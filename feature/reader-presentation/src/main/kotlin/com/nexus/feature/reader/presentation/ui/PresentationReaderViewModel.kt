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
import kotlinx.coroutines.isActive
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
        try {
            System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
            System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
            System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")
        } catch (_: Throwable) {}

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

    // Cache rendered slide bitmaps in memory (limit to ~48MB, with a minimum of 16MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxOf(maxMemory / 8, 16 * 1024)
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Dedicated high-performance LRU cache for lightweight slide thumbnails (size capped to ~24MB, min 8MB)
    private val thumbnailCacheSize = maxOf(maxMemory / 12, 8 * 1024)
    private val slideThumbnailCache = object : LruCache<Int, Bitmap>(thumbnailCacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getCachedThumbnail(slideIndex: Int): Bitmap? {
        return slideThumbnailCache.get(slideIndex)
    }

    suspend fun getSlideThumbnail(slideIndex: Int, targetWidth: Int = 320, targetHeight: Int = 180): Bitmap? = withContext(Dispatchers.IO) {
        slideThumbnailCache.get(slideIndex)?.let { return@withContext it }
        val doc = activeDocument ?: return@withContext null
        val bitmap = doc.renderSlide(slideIndex, targetWidth, targetHeight)
        if (bitmap != null) {
            slideThumbnailCache.put(slideIndex, bitmap)
        }
        bitmap
    }

    fun prefetchThumbnails(indices: List<Int>, targetWidth: Int = 320, targetHeight: Int = 180) {
        val doc = activeDocument ?: return
        viewModelScope.launch(Dispatchers.IO) {
            for (idx in indices) {
                if (!isActive) break
                if (idx in 0 until doc.slideCount && slideThumbnailCache.get(idx) == null) {
                    val bitmap = doc.renderSlide(idx, targetWidth, targetHeight)
                    if (bitmap != null) {
                        slideThumbnailCache.put(idx, bitmap)
                    }
                }
            }
        }
    }

    private var isBitmapRenderingSupported: Boolean = false

    private fun safeUrlDecode(input: String): String {
        return try {
            val firstDecode = URLDecoder.decode(input, StandardCharsets.UTF_8.toString())
            // Only decode a second time if the string still contains valid URL-encoded sequences (%[0-9a-fA-F]{2})
            if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(firstDecode)) {
                try {
                    URLDecoder.decode(firstDecode, StandardCharsets.UTF_8.toString())
                } catch (_: IllegalArgumentException) {
                    firstDecode
                }
            } else {
                firstDecode
            }
        } catch (_: Exception) {
            input
        }
    }

    fun loadDocument(encodedUri: String, rawFileName: String) {
        currentEncodedUri = encodedUri
        _currentUriFlow.value = encodedUri

        viewModelScope.launch {
            _uiState.value = PresentationUiState.Loading

            try {
                val decodedFileName = safeUrlDecode(rawFileName)
                val decodedUriString = safeUrlDecode(encodedUri)

                val targetFile = withContext(Dispatchers.IO) {
                    prepareLocalFile(Uri.parse(decodedUriString), decodedFileName)
                }

                if (targetFile == null || !targetFile.exists() || targetFile.length() <= 0L) {
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
                    } catch (_: Throwable) {
                        // Non-fatal if recent history upsert fails
                    }
                }

                val doc: PresentationDocument? = withContext(Dispatchers.IO) {
                    var loadedDoc: PresentationDocument? = null
                    try {
                        // 1. Primary engine: Apache POI with rich native Compose rendering & fast loading
                        loadedDoc = com.nexus.feature.reader.presentation.engine.poi.PoiPresentationParser.parse(targetFile)
                    } catch (t: Throwable) {
                        android.util.Log.w("PresentationVM", "POI presentation parser failed for ${targetFile.name}", t)
                    }
                    if (loadedDoc == null && lokitManager.isAvailable()) {
                        try {
                            // 2. Fallback engine: LibreOfficeKit if bootstrap assets exist
                            loadedDoc = lokitManager.openDocument(targetFile)
                        } catch (t: Throwable) {
                            android.util.Log.e("PresentationVM", "LOKit fallback failed for ${targetFile.name}", t)
                        }
                    }
                    loadedDoc
                }

                if (doc == null || doc.slideCount <= 0) {
                    val msg = if (doc != null && doc.slideCount == 0) {
                        "This presentation contains no slides."
                    } else {
                        "Unable to parse presentation file."
                    }
                    _uiState.value = PresentationUiState.Error(msg)
                    return@launch
                }

                activeDocument = doc
                isBitmapRenderingSupported = try {
                    doc.renderSlide(0, 10, 10) != null
                } catch (t: Throwable) {
                    false
                }

                _uiState.value = PresentationUiState.Success(
                    fileName = decodedFileName,
                    slideCount = doc.slideCount,
                    currentSlideIndex = 0,
                    slides = doc.slides
                )
                triggerPreRenderCache(0)
                prefetchThumbnails((0 until minOf(12, doc.slideCount)).toList())
            } catch (t: Throwable) {
                android.util.Log.e("PresentationVM", "Fatal error opening presentation", t)
                _uiState.value = PresentationUiState.Error(
                    t.localizedMessage?.ifBlank { null } ?: "Failed to open presentation document."
                )
            }
        }
    }

    suspend fun getSlideBitmap(slideIndex: Int, width: Int, height: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!isBitmapRenderingSupported) return@withContext null
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
        val newIndex = (_uiState.value as? PresentationUiState.Success)?.let {
            index.coerceIn(0, it.slideCount - 1)
        } ?: index

        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(currentSlideIndex = newIndex)
            } else current
        }

        triggerPreRenderCache(newIndex)
    }

    private fun triggerPreRenderCache(currentIndex: Int) {
        if (!isBitmapRenderingSupported) return
        val doc = activeDocument ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val adjacentIndices = listOf(currentIndex + 1, currentIndex - 1, currentIndex + 2)
            val targetWidth = 1920
            val aspectRatio = doc.slides.getOrNull(currentIndex)?.aspectRatio ?: (16f / 9f)
            val targetHeight = (targetWidth / aspectRatio).toInt().coerceAtLeast(600)

            for (idx in adjacentIndices) {
                if (idx in 0 until doc.slideCount) {
                    val cacheKey = "${idx}_${targetWidth}_$targetHeight"
                    if (bitmapCache.get(cacheKey) == null) {
                        val preBitmap = doc.renderSlide(idx, targetWidth, targetHeight)
                        if (preBitmap != null) {
                            bitmapCache.put(cacheKey, preBitmap)
                        }
                    }
                }
            }
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

    // ─── 2.1 In-Slide Text Search ──────────────────────────────────────────
    fun toggleSearch() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                val willBeActive = !current.isSearchActive
                if (!willBeActive) {
                    current.copy(
                        isSearchActive = false,
                        searchQuery = "",
                        searchMatches = emptyList(),
                        currentMatchIndex = 0
                    )
                } else {
                    current.copy(isSearchActive = true)
                }
            } else current
        }
    }

    fun search(query: String) {
        val q = query.trim()
        val doc = activeDocument ?: return

        val matches = mutableListOf<com.nexus.feature.reader.presentation.model.SlideSearchMatch>()
        if (q.isNotEmpty()) {
            for (slide in doc.slides) {
                val texts = mutableListOf<String>()
                slide.title?.let { texts.add(it) }
                slide.notes?.let { texts.add(it) }
                for (elem in slide.elements) {
                    when (elem) {
                        is com.nexus.feature.reader.presentation.engine.SlideElement.Text -> {
                            texts.add(elem.text)
                            texts.addAll(elem.bullets)
                            texts.addAll(elem.paragraphs.map { it.rawText })
                        }
                        is com.nexus.feature.reader.presentation.engine.SlideElement.Table -> {
                            texts.addAll(elem.rows.flatten())
                            texts.addAll(elem.tableRows.flatMap { it.cells }.map { it.text })
                        }
                        is com.nexus.feature.reader.presentation.engine.SlideElement.Shape -> {
                            texts.add(elem.shapeName)
                            texts.addAll(elem.textParagraphs.map { it.rawText })
                        }
                        is com.nexus.feature.reader.presentation.engine.SlideElement.Chart -> {
                            elem.title?.let { texts.add(it) }
                            texts.addAll(elem.categories)
                            texts.addAll(elem.series.map { it.name })
                        }
                        is com.nexus.feature.reader.presentation.engine.SlideElement.Media -> {
                            elem.title?.let { texts.add(it) }
                        }
                        is com.nexus.feature.reader.presentation.engine.SlideElement.Image -> {}
                    }
                }

                for (text in texts) {
                    if (text.contains(q, ignoreCase = true)) {
                        matches.add(
                            com.nexus.feature.reader.presentation.model.SlideSearchMatch(
                                slideIndex = slide.index,
                                matchedText = q,
                                snippet = text.take(80)
                            )
                        )
                        break
                    }
                }
            }
        }

        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(
                    searchQuery = query,
                    searchMatches = matches,
                    currentMatchIndex = 0
                )
            } else current
        }

        if (matches.isNotEmpty()) {
            setCurrentSlide(matches[0].slideIndex)
        }
    }

    fun nextSearchMatch() {
        val state = _uiState.value as? PresentationUiState.Success ?: return
        if (state.searchMatches.isEmpty()) return
        val nextIdx = (state.currentMatchIndex + 1) % state.searchMatches.size
        _uiState.update { current ->
            if (current is PresentationUiState.Success) current.copy(currentMatchIndex = nextIdx) else current
        }
        setCurrentSlide(state.searchMatches[nextIdx].slideIndex)
    }

    fun previousSearchMatch() {
        val state = _uiState.value as? PresentationUiState.Success ?: return
        if (state.searchMatches.isEmpty()) return
        val prevIdx = if (state.currentMatchIndex > 0) state.currentMatchIndex - 1 else state.searchMatches.size - 1
        _uiState.update { current ->
            if (current is PresentationUiState.Success) current.copy(currentMatchIndex = prevIdx) else current
        }
        setCurrentSlide(state.searchMatches[prevIdx].slideIndex)
    }

    // ─── 2.2 Speaker & Slide Notes Drawer ──────────────────────────────────
    fun toggleSpeakerNotes() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(showSpeakerNotes = !current.showSpeakerNotes)
            } else current
        }
    }

    fun increaseSpeakerNotesFontSize() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(speakerNotesFontSize = (current.speakerNotesFontSize + 2f).coerceAtMost(28f))
            } else current
        }
    }

    fun decreaseSpeakerNotesFontSize() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(speakerNotesFontSize = (current.speakerNotesFontSize - 2f).coerceAtLeast(12f))
            } else current
        }
    }

    // ─── 2.3 Lightbox / Slide Grid Sorter View ──────────────────────────────
    fun toggleGridSorter() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(isGridSorterVisible = !current.isGridSorterVisible)
            } else current
        }
    }

    // ─── 2.4 Laser Pointer & Pen Inking Annotation Mode ─────────────────────
    fun setInkingTool(tool: com.nexus.feature.reader.presentation.model.InkingTool) {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(activeInkingTool = tool)
            } else current
        }
    }

    fun setInkingColor(colorArgb: Int) {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(inkingColorArgb = colorArgb)
            } else current
        }
    }

    fun addSlideStroke(slideIndex: Int, stroke: com.nexus.feature.reader.presentation.model.InkStroke) {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                val map = current.slideAnnotations.toMutableMap()
                val list = map[slideIndex]?.toMutableList() ?: mutableListOf()
                list.add(stroke)
                map[slideIndex] = list
                current.copy(slideAnnotations = map)
            } else current
        }
    }

    fun clearSlideStrokes(slideIndex: Int) {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                val map = current.slideAnnotations.toMutableMap()
                map.remove(slideIndex)
                current.copy(slideAnnotations = map)
            } else current
        }
    }

    // ─── 2.5 Auto-Play Slideshow ───────────────────────────────────────────
    private var slideshowJob: kotlinx.coroutines.Job? = null

    fun toggleSlideshow() {
        val currentState = _uiState.value as? PresentationUiState.Success ?: return
        val willBeActive = !currentState.isSlideshowActive
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(isSlideshowActive = willBeActive)
            } else current
        }

        slideshowJob?.cancel()
        if (willBeActive) {
            startSlideshowLoop()
        }
    }

    fun setSlideshowInterval(seconds: Int) {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(slideshowIntervalSeconds = seconds.coerceIn(2, 60))
            } else current
        }
    }

    fun toggleSlideshowLoop() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(isSlideshowLoop = !current.isSlideshowLoop)
            } else current
        }
    }

    private fun startSlideshowLoop() {
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value as? PresentationUiState.Success ?: break
                if (!state.isSlideshowActive) break
                kotlinx.coroutines.delay(state.slideshowIntervalSeconds * 1000L)
                val nextIdx = state.currentSlideIndex + 1
                if (nextIdx < state.slideCount) {
                    setCurrentSlide(nextIdx)
                } else if (state.isSlideshowLoop) {
                    setCurrentSlide(0)
                } else {
                    _uiState.update { current ->
                        if (current is PresentationUiState.Success) current.copy(isSlideshowActive = false) else current
                    }
                    break
                }
            }
        }
    }

    // ─── 2.6 Landscape Presentation Lock ───────────────────────────────────
    fun toggleLandscapeLock() {
        _uiState.update { current ->
            if (current is PresentationUiState.Success) {
                current.copy(isLandscapeLocked = !current.isLandscapeLocked)
            } else current
        }
    }

    private fun prepareLocalFile(uri: Uri, fileName: String): File? {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it) }
            } else {
                val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "document.pptx" }
                val tempFile = File(context.cacheDir, "temp_presentation_${System.currentTimeMillis()}_$sanitized")
                val stream = context.contentResolver.openInputStream(uri) ?: return null
                stream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0L) tempFile else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDocument?.close()
        activeDocument = null
        bitmapCache.evictAll()
        slideThumbnailCache.evictAll()
    }
}
