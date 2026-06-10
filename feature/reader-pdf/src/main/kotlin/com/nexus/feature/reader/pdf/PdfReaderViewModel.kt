package com.nexus.feature.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.feature.dashboard.data.DocumentBookmarkDao
import com.nexus.feature.dashboard.data.DocumentBookmark
import com.nexus.core.preferences.UserPreferencesRepository

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.artifex.mupdf.fitz.Outline

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// ─── PDF Reader UI State ──────────────────────────────────────────────────────

sealed class PdfReaderUiState {
    data object Loading : PdfReaderUiState()
    data class Success(
        val pageCount: Int,
        val currentPage: Int = 0
    ) : PdfReaderUiState()
    data class Error(val message: String) : PdfReaderUiState()
}

data class PdfOutlineItem(
    val title: String,
    val pageIndex: Int,
    val children: List<PdfOutlineItem>
)

// ─── PDF Reader ViewModel ─────────────────────────────────────────────────────

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentDocumentDao: RecentDocumentDao,
    private val documentBookmarkDao: DocumentBookmarkDao,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfReaderUiState>(PdfReaderUiState.Loading)
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private val maxCacheSize = 24
    private val renderedPagesCache = linkedMapOf<Int, Bitmap>()

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

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

    var pdfTextByPage: List<String> = emptyList()
        private set

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _searchHighlights = MutableStateFlow<Map<Int, List<android.graphics.RectF>>>(emptyMap())
    val searchHighlights: StateFlow<Map<Int, List<android.graphics.RectF>>> = _searchHighlights.asStateFlow()

    private val _currentSearchMatchIndex = MutableStateFlow(-1)
    val currentSearchMatchIndex: StateFlow<Int> = _currentSearchMatchIndex.asStateFlow()
    
    private val _bookmarks = MutableStateFlow<List<DocumentBookmark>>(emptyList())
    val bookmarks: StateFlow<List<DocumentBookmark>> = _bookmarks.asStateFlow()
    
    private val _outline = MutableStateFlow<List<PdfOutlineItem>>(emptyList())
    val outline: StateFlow<List<PdfOutlineItem>> = _outline.asStateFlow()

    private var currentUri: String? = null

    private val _isHorizontalLayout = MutableStateFlow(false)
    val isHorizontalLayout: StateFlow<Boolean> = _isHorizontalLayout.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private var mupdfDocument: Document? = null
    private val renderMutex = Mutex()
    private var tempFile: File? = null

    init {
        viewModelScope.launch {
            _keepScreenAwake.value = prefsRepository.keepScreenAwake.first()
        }
    }



    fun loadPdf(encodedUri: String, encodedFileName: String) {
        viewModelScope.launch {
            _uiState.value = PdfReaderUiState.Loading
            
            val decodedName = try {
                URLDecoder.decode(URLDecoder.decode(encodedFileName, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
            } catch (e: Exception) {
                try {
                    val uriStr = URLDecoder.decode(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
                    Uri.parse(uriStr).lastPathSegment ?: "Document"
                } catch (_: Exception) {
                    encodedFileName
                }
            }
            _displayName.value = decodedName
            
            withContext(Dispatchers.IO) {
                renderMutex.withLock {
                    mupdfDocument?.destroy()
                    mupdfDocument = null
                    tempFile?.delete()
                    tempFile = null
                }
            }
            
            withContext(Dispatchers.Main) {
                renderedPagesCache.values.forEach { it.recycle() }
                val uriStr = URLDecoder.decode(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
                currentUri = uriStr

                viewModelScope.launch {
                    documentBookmarkDao.getBookmarksForDocument(uriStr).collect {
                        _bookmarks.value = it
                    }
                }

                renderedPagesCache.clear()
                _renderedPages.value = emptyMap()
                _outline.value = emptyList()
            }

            withContext(Dispatchers.IO) {
                try {
                    val uriStr = URLDecoder.decode(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
                    val uri = Uri.parse(uriStr)
                    
                    var fileSize = 0L
                    var lastModified = 0L
                    if (uri.scheme == "file") {
                        val file = File(uri.path ?: "")
                        if (file.exists()) {
                            fileSize = file.length()
                            lastModified = file.lastModified()
                        }
                    } else {
                        try {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                val dateIndex = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                                if (cursor.moveToFirst()) {
                                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                                    if (dateIndex >= 0) lastModified = cursor.getLong(dateIndex)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    
                    if (fileSize > 100L * 1024 * 1024) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = PdfReaderUiState.Error("File exceeds the 100MB limit for PDF documents. Opening it might cause the app to run out of memory.")
                        }
                        return@withContext
                    }
                    
                    var filePathToOpen: String? = null
                    if (uri.scheme == "file") {
                        filePathToOpen = uri.path
                    } else {
                        // Copy to temp file
                        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val temp = File.createTempFile("mupdf_", ".pdf", context.cacheDir)
                            val out = FileOutputStream(temp)
                            inputStream.copyTo(out)
                            out.close()
                            inputStream.close()
                            tempFile = temp
                            filePathToOpen = temp.absolutePath
                        }
                    }

                    if (filePathToOpen == null) throw IllegalStateException("Cannot access file")

                    val doc = Document.openDocument(filePathToOpen)
                    mupdfDocument = doc
                    val count = doc.countPages()
                    context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
                        .edit()
                        .putInt(uriStr, count)
                        .apply()

                    // Extract Text using MuPDF
                    try {
                        val textList = mutableListOf<String>()
                        for (i in 0 until count) {
                            val page = doc.loadPage(i)
                            val htmlBytes = page.textAsHtml()
                            val htmlStr = htmlBytes?.decodeToString() ?: ""
                            // Strip HTML for basic text searching
                            val text = htmlStr.replace(Regex("<[^>]*>"), "")
                            textList.add(text)
                            page.destroy()
                        }
                        pdfTextByPage = textList
                    } catch (_: Exception) {
                        pdfTextByPage = emptyList()
                    }

                    val rememberPosition = prefsRepository.rememberReadingPosition.first()
                    if (rememberPosition) {
                        val recentDoc = recentDocumentDao.findByUri(uriStr)
                        if (recentDoc != null && (recentDoc.lastScrollIndex > 0 || recentDoc.lastScrollOffset > 0)) {
                            _initialScrollPosition.value = Pair(recentDoc.lastScrollIndex, recentDoc.lastScrollOffset)
                        }
                    }

                    // Extract outline
                    try {
                        val out = doc.loadOutline()
                        _outline.value = extractOutline(out)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = PdfReaderUiState.Success(pageCount = count)
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = PdfReaderUiState.Error(
                            message = com.nexus.core.utils.ErrorHandler.getUserFriendlyMessage(e as? Exception ?: Exception(e))
                        )
                    }
                }
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchHighlights.value = emptyMap()
            _currentSearchMatchIndex.value = -1
        } else {
            searchJob = viewModelScope.launch(Dispatchers.Default) {
                val matches = mutableListOf<Int>()
                val highlightsMap = mutableMapOf<Int, List<android.graphics.RectF>>()
                val doc = mupdfDocument
                
                pdfTextByPage.forEachIndexed { index, text ->
                    kotlinx.coroutines.yield()
                    if (text.contains(query, ignoreCase = true)) {
                        matches.add(index)
                        
                        if (doc != null) {
                            try {
                                val page = doc.loadPage(index)
                                val rect = page.bounds
                                val width = rect.x1 - rect.x0
                                val height = rect.y1 - rect.y0
                                
                                val quads = page.search(query)
                                val rectList = mutableListOf<android.graphics.RectF>()
                                if (quads != null) {
                                    for (qArray in quads) {
                                        for (q in qArray) {
                                            val left = minOf(q.ul_x, q.ll_x) / width
                                            val top = minOf(q.ul_y, q.ur_y) / height
                                            val right = maxOf(q.ur_x, q.lr_x) / width
                                            val bottom = maxOf(q.ll_y, q.lr_y) / height
                                            rectList.add(android.graphics.RectF(left, top, right, bottom))
                                        }
                                    }
                                }
                                highlightsMap[index] = rectList
                                page.destroy()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _searchResults.value = matches
                    _searchHighlights.value = highlightsMap
                    _currentSearchMatchIndex.value = if (matches.isNotEmpty()) 0 else -1
                }
            }
        }
    }

    fun nextSearchMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        val current = _currentSearchMatchIndex.value
        _currentSearchMatchIndex.value = (current + 1) % matches.size
    }

    fun previousSearchMatch() {
        val matches = _searchResults.value
        if (matches.isEmpty()) return
        val current = _currentSearchMatchIndex.value
        _currentSearchMatchIndex.value = (current - 1 + matches.size) % matches.size
    }

    fun copyPageTextToClipboard(pageIndex: Int) {
        if (pageIndex in pdfTextByPage.indices) {
            val text = pdfTextByPage[pageIndex]
            if (text.isNotBlank()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PDF Page Text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Page has no selectable text", Toast.LENGTH_SHORT).show()
            }
        }
    }



    fun toggleHorizontalLayout() {
        _isHorizontalLayout.value = !_isHorizontalLayout.value
    }



    fun sharePdf(context: Context, encodedUri: String) {
        viewModelScope.launch {
            try {
                val uriStr = URLDecoder.decode(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
                val rawUri = Uri.parse(uriStr)
                val shareUri = if (rawUri.scheme == "file") {
                    val file = File(rawUri.path ?: "")
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    rawUri
                }
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Share PDF").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun printPdf(context: Context, displayName: String) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
            
            val printAdapter = object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: android.print.PrintAttributes?,
                    newAttributes: android.print.PrintAttributes,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(displayName)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    var inStream: InputStream? = null
                    var outStream: FileOutputStream? = null
                    try {
                        val uriStr = currentUri ?: return
                        val uri = Uri.parse(uriStr)
                        inStream = if (tempFile != null && tempFile!!.exists()) {
                            java.io.FileInputStream(tempFile!!)
                        } else if (uri.scheme == "file") {
                            java.io.FileInputStream(File(uri.path ?: ""))
                        } else {
                            context.contentResolver.openInputStream(uri)
                        }
                        outStream = FileOutputStream(destination.fileDescriptor)
                        
                        inStream?.copyTo(outStream)
                        
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    } finally {
                        inStream?.close()
                        outStream?.close()
                    }
                }
            }
            
            printManager.print(displayName, printAdapter, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Print failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int) {
        val doc = mupdfDocument ?: return
        if (_renderedPages.value.containsKey(pageIndex)) return

        withContext(Dispatchers.IO) {
            try {
                val bitmap = renderMutex.withLock {
                    if (!isActive) return@withLock null
                    val page = doc.loadPage(pageIndex)
                    val rect = page.bounds
                    val aspectRatio = (rect.y1 - rect.y0) / (rect.x1 - rect.x0)
                    
                    val scaledWidth = (targetWidth * 1.5f).toInt()
                    val scaledHeight = (scaledWidth * aspectRatio).toInt()

                    val bmp = AndroidDrawDevice.drawPageFit(page, scaledWidth, scaledHeight)
                    
                    page.destroy()
                    bmp
                }

                if (bitmap != null && isActive) {
                    withContext(Dispatchers.Main) {
                        renderedPagesCache[pageIndex] = bitmap
                        if (renderedPagesCache.size > maxCacheSize) {
                            val oldestKey = renderedPagesCache.keys.first()
                            val evicted = renderedPagesCache.remove(oldestKey)
                            evicted?.recycle()
                        }
                        _renderedPages.value = renderedPagesCache.toMap()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun setCurrentPage(index: Int) {
        _currentPage.value = index
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
    
    fun toggleBookmark(pageIndex: Int, label: String = "Page ${pageIndex + 1}", note: String? = null) {
        val uri = currentUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _bookmarks.value.find { it.pageIndex == pageIndex }
            if (existing != null) {
                documentBookmarkDao.deleteBookmarkByPage(uri, pageIndex)
            } else {
                documentBookmarkDao.insertBookmark(
                    DocumentBookmark(
                        documentUri = uri,
                        pageIndex = pageIndex,
                        label = label,
                        note = note
                    )
                )
            }
        }
    }

    private fun extractOutline(outlines: Array<Outline>?): List<PdfOutlineItem> {
        if (outlines == null) return emptyList()
        val result = mutableListOf<PdfOutlineItem>()
        for (current in outlines) {
            var pageIdx = -1
            if (current.uri != null && current.uri.startsWith("#")) {
                pageIdx = (current.uri.substring(1).toIntOrNull() ?: 1) - 1
            }
            result.add(
                PdfOutlineItem(
                    title = current.title ?: "Untitled",
                    pageIndex = pageIdx,
                    children = extractOutline(current.down)
                )
            )
        }
        return result
    }



    override fun onCleared() {
        super.onCleared()
        renderedPagesCache.values.forEach { it.recycle() }
        renderedPagesCache.clear()
        _renderedPages.value = emptyMap()
        mupdfDocument?.destroy()
        tempFile?.delete()
    }
}
