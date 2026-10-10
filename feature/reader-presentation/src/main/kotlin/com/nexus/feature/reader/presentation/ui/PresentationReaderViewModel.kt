package com.nexus.feature.reader.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.core.preferences.UserPreferencesRepository
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.feature.reader.presentation.engine.LOKitManager
import com.nexus.feature.reader.presentation.engine.PresentationDocument
import com.nexus.feature.reader.presentation.model.PresentationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class PresentationReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lokitManager: LOKitManager,
    private val recentDocumentDao: RecentDocumentDao,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PresentationUiState>(PresentationUiState.Loading)
    val uiState: StateFlow<PresentationUiState> = _uiState.asStateFlow()

    private var activeDocument: PresentationDocument? = null
    private var currentEncodedUri: String? = null
    private val _currentUriFlow = MutableStateFlow<String?>(null)

    val isStarred: StateFlow<Boolean> = combine(prefsRepository.starredUris, _currentUriFlow) { starredSet, uri ->
        uri?.let { starredSet.contains(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _keepScreenAwake = MutableStateFlow(false)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    init {
        viewModelScope.launch {
            _keepScreenAwake.value = prefsRepository.keepScreenAwake.first()
        }
    }

    fun setKeepScreenAwake(value: Boolean) {
        viewModelScope.launch {
            prefsRepository.setKeepScreenAwake(value)
            _keepScreenAwake.value = value
        }
    }

    fun toggleFavorite(onResult: (Boolean) -> Unit) {
        val uri = currentEncodedUri ?: return
        viewModelScope.launch {
            val isNowStarred = prefsRepository.toggleStarredUri(uri)
            onResult(isNowStarred)
        }
    }

    // Cache rendered slide bitmaps in memory (limit to ~48MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun loadDocument(encodedUri: String, rawFileName: String) {
        currentEncodedUri = encodedUri
        _currentUriFlow.value = encodedUri

        viewModelScope.launch {
            _uiState.value = PresentationUiState.Loading

            val decodedFileName = try {
                URLDecoder.decode(URLDecoder.decode(rawFileName, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                rawFileName
            }

            val decodedUriString = try {
                URLDecoder.decode(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                encodedUri
            }

            val targetFile = withContext(Dispatchers.IO) {
                prepareLocalFile(Uri.parse(decodedUriString), decodedFileName)
            }

            if (targetFile == null || !targetFile.exists()) {
                _uiState.value = PresentationUiState.Error("Unable to access presentation file.")
                return@launch
            }

            // Save to recent documents history
            withContext(Dispatchers.IO) {
                try {
                    val isPpt = decodedFileName.endsWith(".ppt", ignoreCase = true)
                    val mime = if (isPpt) "application/vnd.ms-powerpoint" else "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    recentDocumentDao.upsert(
                        RecentDocument(
                            uri = decodedUriString,
                            fileName = decodedFileName,
                            mimeType = mime,
                            fileSizeBytes = targetFile.length(),
                            lastOpenedAt = System.currentTimeMillis(),
                            documentType = com.nexus.core.navigation.DocumentType.PPTX.name,
                            lastScrollIndex = 0,
                            lastScrollOffset = 0
                        )
                    )
                } catch (_: Exception) {
                    // Non-fatal if recent history upsert fails
                }
            }

            var doc = lokitManager.openDocument(targetFile)
            if (doc == null) {
                // Seamlessly fallback to Apache POI parser (offline native Compose model)
                doc = com.nexus.feature.reader.presentation.engine.poi.PoiPresentationParser.parse(targetFile)
            }

            if (doc == null) {
                _uiState.value = PresentationUiState.Error("Unable to parse presentation file.")
                return@launch
            }

            activeDocument = doc

            _uiState.value = PresentationUiState.Success(
                fileName = decodedFileName,
                slideCount = doc.slideCount,
                currentSlideIndex = 0,
                slides = doc.slides
            )
        }
    }

    suspend fun getSlideBitmap(slideIndex: Int, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${slideIndex}_${width}_$height"
        bitmapCache.get(cacheKey)?.let { return@withContext it }

        val doc = activeDocument ?: return@withContext null
        val bitmap = doc.renderSlide(slideIndex, width, height)
        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    fun setCurrentSlide(index: Int) {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(currentSlideIndex = index.coerceIn(0, current.slideCount - 1))
            } else current
        }
    }

    fun toggleFullscreen() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(isFullscreen = !current.isFullscreen)
            } else current
        }
    }

    fun toggleThumbnails() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(showThumbnails = !current.showThumbnails)
            } else current
        }
    }

    private fun prepareLocalFile(uri: Uri, fileName: String): File? {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it) }
            } else {
                val tempFile = File(context.cacheDir, "temp_presentation_${System.currentTimeMillis()}_$fileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDocument?.close()
        activeDocument = null
        bitmapCache.evictAll()
    }
}
