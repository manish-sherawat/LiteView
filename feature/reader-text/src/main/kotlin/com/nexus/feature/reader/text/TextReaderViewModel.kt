package com.nexus.feature.reader.text

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.core.preferences.UserPreferencesRepository

// ─── Text Reader UI State ──────────────────────────────────────────────────────

sealed class TextReaderUiState {
    data object Loading : TextReaderUiState()
    data class Success(
        val lines: List<String>,
        val charset: String,
        val totalLineCount: Int,
        val isTruncated: Boolean = false,
        val isCodeFile: Boolean = false
    ) : TextReaderUiState()
    data class Error(val message: String) : TextReaderUiState()
}

// ─── Text Reader ViewModel ────────────────────────────────────────────────────
// Reads plain text files via ContentResolver on the IO dispatcher.
// Handles large files by capping initial load at 5000 lines.
// Exposes font size state for the UI to control rendering.

@HiltViewModel
class TextReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentDocumentDao: RecentDocumentDao,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TextReaderUiState>(TextReaderUiState.Loading)
    val uiState: StateFlow<TextReaderUiState> = _uiState.asStateFlow()

    // Font size in sp — user-adjustable from the top bar
    private val _fontSize = MutableStateFlow(14f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    // UX Enhancements States
    private val _isWordWrapEnabled = MutableStateFlow(true)
    val isWordWrapEnabled: StateFlow<Boolean> = _isWordWrapEnabled.asStateFlow()

    private val _readerTheme = MutableStateFlow("LIGHT") // LIGHT, DARK, SEPIA
    val readerTheme: StateFlow<String> = _readerTheme.asStateFlow()
    
    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _currentSearchIndex = MutableStateFlow(-1)
    val currentSearchIndex: StateFlow<Int> = _currentSearchIndex.asStateFlow()

    private var allLines: List<String> = emptyList()
    private var fileCharsetName: String = "UTF-8"
    private var totalLinesCount: Int = 0
    private val _visibleLinesCount = MutableStateFlow(1000)

    private val _initialScrollPosition = MutableStateFlow<Pair<Int, Int>?>(null)
    val initialScrollPosition: StateFlow<Pair<Int, Int>?> = _initialScrollPosition.asStateFlow()

    private val _keepScreenAwake = MutableStateFlow(false)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    fun setKeepScreenAwake(value: Boolean) {
        viewModelScope.launch {
            prefsRepository.setKeepScreenAwake(value)
            _keepScreenAwake.value = value
        }
    }

    init {
        viewModelScope.launch {
            _keepScreenAwake.value = prefsRepository.keepScreenAwake.first()
            _fontSize.value = 16f // Increased default font size
            _readerTheme.value = prefsRepository.readerTheme.first()
            _isWordWrapEnabled.value = prefsRepository.wordWrapEnabled.first()
        }
    }

    companion object {
        private const val MAX_LINES = 25_000
        private const val MIN_FONT_SIZE = 10f
        private const val MAX_FONT_SIZE = 28f
        private const val FONT_STEP = 2f
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentSearchIndex.value = -1
        } else {
            searchJob = viewModelScope.launch(Dispatchers.Default) {
                val matches = mutableListOf<Int>()
                val ignoreCase = !_matchCase.value
                allLines.forEachIndexed { index, text ->
                    if (index % 1000 == 0) kotlinx.coroutines.yield() // Allow cooperative cancellation
                    if (text.contains(query, ignoreCase = ignoreCase)) {
                        matches.add(index)
                    }
                }
                withContext(Dispatchers.Main) {
                    _searchResults.value = matches
                    _currentSearchIndex.value = if (matches.isNotEmpty()) 0 else -1
                }
            }
        }
    }
    
    fun toggleMatchCase() {
        _matchCase.value = !_matchCase.value
        setSearchQuery(_searchQuery.value)
    }

    fun nextSearchMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        val current = _currentSearchIndex.value
        _currentSearchIndex.value = (current + 1) % matches.size
        ensureSearchMatchVisible()
    }

    fun previousSearchMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        val current = _currentSearchIndex.value
        _currentSearchIndex.value = (current - 1 + matches.size) % matches.size
        
        ensureSearchMatchVisible()
    }

    private fun ensureSearchMatchVisible() {
        val matches = _searchResults.value
        val currentIndex = _currentSearchIndex.value
        if (matches.isEmpty() || currentIndex < 0) return
        
        val targetLineIndex = matches[currentIndex]
        if (targetLineIndex >= _visibleLinesCount.value) {
            _visibleLinesCount.value = (targetLineIndex + 1000).coerceAtMost(allLines.size)
            updateUiStateSuccess()
        }
    }

    private fun safeDecode(str: String): String {
        return try {
            val d1 = URLDecoder.decode(str, StandardCharsets.UTF_8.toString())
            if (d1.contains("%")) {
                try { URLDecoder.decode(d1, StandardCharsets.UTF_8.toString()) } catch (_: Exception) { d1 }
            } else d1
        } catch (_: Exception) {
            str
        }
    }

    fun toggleWordWrap() {
        val newValue = !_isWordWrapEnabled.value
        _isWordWrapEnabled.value = newValue
        viewModelScope.launch { prefsRepository.setWordWrapEnabled(newValue) }
    }

    fun setReaderTheme(theme: String) {
        _readerTheme.value = theme
        viewModelScope.launch { prefsRepository.setReaderTheme(theme) }
    }
    
    private var currentEncodedUri: String? = null
    private val _currentUriFlow = MutableStateFlow<String?>(null)

    val isStarred: StateFlow<Boolean> = combine(prefsRepository.starredUris, _currentUriFlow) { starredSet, uri ->
        uri?.let { starredSet.contains(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleFavorite(onResult: (Boolean) -> Unit) {
        val uri = currentEncodedUri ?: return
        viewModelScope.launch {
            val isNowStarred = prefsRepository.toggleStarredUri(uri)
            onResult(isNowStarred)
        }
    }

    fun loadFile(encodedUri: String, overrideCharset: Charset? = null) {
        currentEncodedUri = encodedUri
        _currentUriFlow.value = encodedUri
        viewModelScope.launch {
            _uiState.value = TextReaderUiState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val uriStr = safeDecode(encodedUri)
                    val uri = Uri.parse(uriStr)
                    val inputStream = if (uri.scheme == "file") {
                        java.io.FileInputStream(java.io.File(uri.path ?: throw IllegalArgumentException("Invalid file path")))
                    } else {
                        context.contentResolver.openInputStream(uri)
                    } ?: throw IllegalStateException("Cannot open file")

                    // Detect charset — try UTF-8, fall back to ISO-8859-1
                    val charset = overrideCharset ?: detectCharset(inputStream)

                    // Re-open the stream (InputStreams cannot be reset after reading)
                    val freshStream = if (uri.scheme == "file") {
                        java.io.FileInputStream(java.io.File(uri.path ?: throw IllegalArgumentException("Invalid file path")))
                    } else {
                        context.contentResolver.openInputStream(uri)
                    } ?: throw IllegalStateException("Cannot re-open file")

                    val reader = BufferedReader(InputStreamReader(freshStream, charset))
                    val lines = mutableListOf<String>()
                    var totalLines = 0
                    var line: String?
                    var truncated = false
                    while (reader.readLine().also { line = it } != null) {
                        if (totalLines % 1000 == 0) kotlinx.coroutines.yield() // Prevent blocking IO thread completely
                        totalLines++
                        if (totalLines <= MAX_LINES) {
                            // Truncate excessively long lines to prevent Compose layout engine from hanging
                            val safeLine = if ((line?.length ?: 0) > 5000) {
                                line?.substring(0, 5000) + "… [Line Truncated]"
                            } else {
                                line ?: ""
                            }
                            lines.add(safeLine)
                        } else {
                            truncated = true
                            break
                        }
                    }
                    reader.close()
                    
                    val extension = uriStr.substringAfterLast('.', "").lowercase()
                    val codeExtensions = setOf("kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "c", "cpp", "h", "cs", "rb", "sh", "yml", "yaml", "md")
                    val isCode = codeExtensions.contains(extension)

                    val rememberPosition = prefsRepository.rememberReadingPosition.first()
                    if (rememberPosition) {
                        val recentDoc = recentDocumentDao.findByUri(uriStr)
                        if (recentDoc != null && (recentDoc.lastScrollIndex > 0 || recentDoc.lastScrollOffset > 0)) {
                            _initialScrollPosition.value = Pair(recentDoc.lastScrollIndex, recentDoc.lastScrollOffset)
                        }
                    }

                    try {
                        val name = try {
                            if (uri.scheme == "file") java.io.File(uri.path ?: "").name
                            else {
                                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                                } ?: uri.lastPathSegment ?: "Text Document"
                            }
                        } catch (_: Exception) { "Text Document" }

                        val size = try {
                            if (uri.scheme == "file") java.io.File(uri.path ?: "").length()
                            else {
                                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                    val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                    if (c.moveToFirst() && idx >= 0) c.getLong(idx) else 0L
                                } ?: 0L
                            }
                        } catch (_: Exception) { 0L }

                        val recentDoc = recentDocumentDao.findByUri(uriStr)
                        recentDocumentDao.upsert(
                            com.nexus.feature.dashboard.data.RecentDocument(
                                uri = uriStr,
                                fileName = name,
                                mimeType = "text/plain",
                                fileSizeBytes = size,
                                lastOpenedAt = System.currentTimeMillis(),
                                documentType = "TEXT",
                                lastScrollIndex = recentDoc?.lastScrollIndex ?: 0,
                                lastScrollOffset = recentDoc?.lastScrollOffset ?: 0
                            )
                        )
                    } catch (_: Exception) {}

                    withContext(Dispatchers.Main) {
                        allLines = lines
                        fileCharsetName = charset.name()
                        totalLinesCount = totalLines
                        _isTruncatedFlag = truncated
                        _isCodeFileFlag = isCode
                        _visibleLinesCount.value = 1000.coerceAtMost(allLines.size)
                        context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
                            .edit()
                            .putInt(uriStr, totalLines)
                            .apply()
                        updateUiStateSuccess()
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = TextReaderUiState.Error(
                            com.nexus.core.utils.ErrorHandler.getUserFriendlyMessage(e as? Exception ?: Exception(e))
                        )
                    }
                }
            }
        }
    }
    
    fun reloadWithCharset(charsetName: String) {
        currentEncodedUri?.let { uri ->
            try {
                val charset = Charset.forName(charsetName)
                loadFile(uri, charset)
            } catch (e: Exception) {
                // Invalid charset name
            }
        }
    }

    private var _isTruncatedFlag = false
    private var _isCodeFileFlag = false

    private fun updateUiStateSuccess() {
        val count = _visibleLinesCount.value
        val visible = allLines.take(count)
        _uiState.value = TextReaderUiState.Success(
            lines = visible,
            charset = fileCharsetName,
            totalLineCount = totalLinesCount,
            isTruncated = _isTruncatedFlag,
            isCodeFile = _isCodeFileFlag
        )
    }

    fun loadMore() {
        if (_uiState.value !is TextReaderUiState.Success) return
        val currentCount = _visibleLinesCount.value
        if (currentCount >= allLines.size) return
        _visibleLinesCount.value = (currentCount + 1000).coerceAtMost(allLines.size)
        updateUiStateSuccess()
    }

    fun increaseFontSize() {
        val newSize = (_fontSize.value + FONT_STEP).coerceAtMost(MAX_FONT_SIZE)
        _fontSize.value = newSize
        viewModelScope.launch { prefsRepository.setDefaultFontSize(newSize) }
    }

    fun saveScrollPosition(encodedUri: String, index: Int, offset: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rememberPosition = prefsRepository.rememberReadingPosition.first()
                if (rememberPosition) {
                    val uriStr = URLDecoder.decode(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
                    recentDocumentDao.updateScrollPosition(uriStr, index, offset)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setFontSize(size: Float) {
        val targetSize = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        _fontSize.value = targetSize
        viewModelScope.launch { prefsRepository.setDefaultFontSize(targetSize) }
    }

    fun decreaseFontSize() {
        val newSize = (_fontSize.value - FONT_STEP).coerceAtLeast(MIN_FONT_SIZE)
        _fontSize.value = newSize
        viewModelScope.launch { prefsRepository.setDefaultFontSize(newSize) }
    }

    /** Heuristic charset detection — reads first 4KB and checks for BOM or non-UTF-8 sequences. */
    private fun detectCharset(stream: java.io.InputStream): Charset {
        return try {
            val buf = ByteArray(4096)
            val read = stream.read(buf)
            stream.close()
            if (read < 0) return StandardCharsets.UTF_8

            // Check for UTF-8 BOM (EF BB BF)
            if (read >= 3 && buf[0] == 0xEF.toByte() && buf[1] == 0xBB.toByte() && buf[2] == 0xBF.toByte()) {
                return StandardCharsets.UTF_8
            }
            // Check for UTF-16 LE BOM (FF FE)
            if (read >= 2 && buf[0] == 0xFF.toByte() && buf[1] == 0xFE.toByte()) {
                return StandardCharsets.UTF_16LE
            }
            // Check for UTF-16 BE BOM (FE FF)
            if (read >= 2 && buf[0] == 0xFE.toByte() && buf[1] == 0xFF.toByte()) {
                return StandardCharsets.UTF_16BE
            }
            // Try to validate as UTF-8 — if any bytes are invalid, fall back to ISO-8859-1
            val testString = String(buf, 0, read, StandardCharsets.UTF_8)
            if (testString.contains('\uFFFD')) StandardCharsets.ISO_8859_1
            else StandardCharsets.UTF_8
        } catch (_: Exception) {
            StandardCharsets.UTF_8
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Release the massive string list back to garbage collector
        allLines = emptyList()
    }

    fun shareText(context: android.content.Context, encodedUri: String) {
        viewModelScope.launch {
            try {
                val uriStr = java.net.URLDecoder.decode(java.net.URLDecoder.decode(encodedUri, "UTF-8"), "UTF-8")
                val rawUri = android.net.Uri.parse(uriStr)
                val shareUri = if (rawUri.scheme == "file") {
                    val file = java.io.File(rawUri.path ?: "")
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    rawUri
                }
                
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, shareUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = android.content.Intent.createChooser(intent, "Share Document").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to share: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
