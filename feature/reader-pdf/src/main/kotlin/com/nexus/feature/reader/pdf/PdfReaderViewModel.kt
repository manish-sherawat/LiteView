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
import kotlinx.coroutines.delay
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
import androidx.compose.ui.graphics.toArgb
import com.artifex.mupdf.fitz.Outline

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// ─── PDF Reader UI State ──────────────────────────────────────────────────────

sealed class PdfReaderUiState {
    data object Loading : PdfReaderUiState()
    data class Success(
        val pageCount: Int,
        val currentPage: Int = 0,
        val fileSize: Long = 0L,
        val lastModified: Long = 0L
    ) : PdfReaderUiState()
    data class PasswordRequired(val encodedUri: String, val encodedFileName: String, val isError: Boolean = false) : PdfReaderUiState()
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

    private val maxCacheSize = 40
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

    fun loadPdf(encodedUri: String, encodedFileName: String, password: String? = null) {
        viewModelScope.launch {
            _uiState.value = PdfReaderUiState.Loading
            
            val decodedName = try {
                safeDecode(encodedFileName)
            } catch (e: Exception) {
                try {
                    val uriStr = safeDecode(encodedUri)
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
            
            val uriStrDecoded = withContext(Dispatchers.IO) {
                safeDecode(encodedUri)
            }
            
            renderedPagesCache.values.forEach { if (!it.isRecycled) it.recycle() }
            currentUri = uriStrDecoded

            viewModelScope.launch {
                documentBookmarkDao.getBookmarksForDocument(uriStrDecoded).collect {
                    _bookmarks.value = it
                }
            }

            renderedPagesCache.clear()
            _renderedPages.value = emptyMap()
            _outline.value = emptyList()
            withContext(Dispatchers.IO) {
                try {
                    val uriStr = uriStrDecoded
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
                    if (doc.needsPassword()) {
                        if (password == null || !doc.authenticatePassword(password)) {
                            withContext(Dispatchers.Main) {
                                _uiState.value = PdfReaderUiState.PasswordRequired(encodedUri, encodedFileName, password != null)
                            }
                            return@withContext
                        }
                    }

                    mupdfDocument = doc
                    val count = doc.countPages()
                    context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
                        .edit()
                        .putInt(uriStr, count)
                        .apply()

                    // Extract Text using MuPDF safely under lock
                    try {
                        val textList = mutableListOf<String>()
                        for (i in 0 until count) {
                            val pageText = renderMutex.withLock {
                                val page = doc.loadPage(i)
                                val htmlBytes = page.textAsHtml()
                                val htmlStr = htmlBytes?.decodeToString() ?: ""
                                val text = htmlStr.replace(Regex("<[^>]*>"), "")
                                page.destroy()
                                text
                            }
                            textList.add(pageText)
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
                        _uiState.value = PdfReaderUiState.Success(
                            pageCount = count,
                            fileSize = fileSize,
                            lastModified = lastModified
                        )
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
                                renderMutex.withLock {
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
                                }
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
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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

    fun renameFile(encodedUri: String, newName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val uriStr = URLDecoder.decode(URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()), StandardCharsets.UTF_8.toString())
                val uri = Uri.parse(uriStr)
                
                var success = false
                var newUri: Uri? = null
                
                withContext(Dispatchers.IO) {
                    if (uri.scheme == "file") {
                        val file = File(uri.path ?: "")
                        val newFile = File(file.parent, newName)
                        success = file.renameTo(newFile)
                        if (success) {
                            newUri = Uri.fromFile(newFile)
                        }
                    } else {
                        newUri = android.provider.DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                        success = newUri != null
                    }
                }
                
                if (success && newUri != null) {
                    _displayName.value = newName
                    withContext(Dispatchers.Main) {
                        onResult(true, newUri.toString())
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Rename failed")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage)
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
                    
                    val scaledWidth = (targetWidth * 2.5f).toInt()
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
                            renderedPagesCache.remove(oldestKey)
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

    private var lastSavedIndex = -1
    private var lastSavedOffset = -1

    fun saveScrollPosition(encodedUri: String, index: Int, offset: Int) {
        if (index == lastSavedIndex && Math.abs(offset - lastSavedOffset) < 50) return
        lastSavedIndex = index
        lastSavedOffset = offset
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rememberPosition = prefsRepository.rememberReadingPosition.first()
                if (rememberPosition) {
                    val uriStr = safeDecode(encodedUri)
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

    fun saveAnnotations(pageIndex: Int, strokes: List<List<android.graphics.PointF>>, color: Int = android.graphics.Color.RED, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(500)
                renderMutex.withLock {
                    renderedPagesCache.remove(pageIndex)
                    withContext(Dispatchers.Main) {
                        _renderedPages.value = renderedPagesCache.toMap()
                    }
                }
                renderPage(pageIndex, 1000)
                withContext(Dispatchers.Main) {
                    onResult(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun saveAnnotationsToFile(
        encodedUri: String,
        drawnStrokesMap: Map<Int, List<PdfAnnotationItem>>,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decodedUri = try { java.net.URLDecoder.decode(encodedUri, "UTF-8") } catch (_: Exception) { encodedUri }
                val uri = android.net.Uri.parse(decodedUri)
                val filePath = if (uri.scheme == "file") uri.path else if (uri.scheme == null) decodedUri else null

                var success = false

                if (filePath != null) {
                    val targetFile = java.io.File(filePath)
                    if (targetFile.exists() && targetFile.canWrite()) {
                        saveAnnotatedPdfInPlace(targetFile, drawnStrokesMap)
                        success = true
                    }
                }

                if (!success && uri.scheme == "content") {
                    context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                        saveAnnotatedPdfToStream(outputStream, drawnStrokesMap)
                        success = true
                    }
                }

                // Invalidate render cache for annotated pages so MuPDF reloads fresh
                renderMutex.withLock {
                    drawnStrokesMap.keys.forEach { pageIdx ->
                        renderedPagesCache.remove(pageIdx)
                    }
                    withContext(Dispatchers.Main) {
                        _renderedPages.value = renderedPagesCache.toMap()
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(true)
                }
            }
        }
    }

    private fun saveAnnotatedPdfInPlace(
        targetFile: java.io.File,
        drawnStrokesMap: Map<Int, List<PdfAnnotationItem>>
    ) {
        val tempFile = java.io.File.createTempFile("temp_annotated_", ".pdf", targetFile.parentFile ?: context.cacheDir)
        try {
            java.io.FileOutputStream(tempFile).use { fos ->
                saveAnnotatedPdfToStream(fos, drawnStrokesMap)
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.copyTo(targetFile, overwrite = true)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun renderPageToBitmapSync(pageIndex: Int, targetWidth: Int): android.graphics.Bitmap? {
        val doc = mupdfDocument ?: return null
        return try {
            val page = doc.loadPage(pageIndex)
            val rect = page.bounds
            val aspectRatio = (rect.y1 - rect.y0) / (rect.x1 - rect.x0)
            val scaledWidth = targetWidth
            val scaledHeight = (scaledWidth * aspectRatio).toInt()
            val bmp = AndroidDrawDevice.drawPageFit(page, scaledWidth, scaledHeight)
            page.destroy()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun saveAnnotatedPdfToStream(
        outputStream: java.io.OutputStream,
        drawnStrokesMap: Map<Int, List<PdfAnnotationItem>>
    ) {
        val pdfDoc = android.graphics.pdf.PdfDocument()
        val totalPages = mupdfDocument?.countPages() ?: 0
        
        for (i in 0 until totalPages) {
            val pageBitmap = renderPageToBitmapSync(i, 1200)
            if (pageBitmap != null) {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageBitmap.width, pageBitmap.height, i + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                
                canvas.drawBitmap(pageBitmap, 0f, 0f, null as android.graphics.Paint?)
                
                val strokes = drawnStrokesMap[i] ?: emptyList()
                strokes.forEach { item ->
                    drawAnnotationItemOnCanvas(canvas, item, pageBitmap.width.toFloat(), pageBitmap.height.toFloat())
                }
                
                pdfDoc.finishPage(page)
                pageBitmap.recycle()
            }
        }
        
        pdfDoc.writeTo(outputStream)
        pdfDoc.close()
    }

    private fun drawAnnotationItemOnCanvas(
        canvas: android.graphics.Canvas,
        item: PdfAnnotationItem,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = item.strokeWidth * (pageWidth / 400f)
            color = item.color.toArgb()
            if (item.tool == com.nexus.feature.reader.pdf.components.AnnotationTool.Highlighter) {
                alpha = 115
            }
        }
        
        if (item.tool == com.nexus.feature.reader.pdf.components.AnnotationTool.Shapes && item.points.size >= 2) {
            val p1 = item.points[0]
            val p2 = item.points[1]
            val x1 = p1.x * pageWidth
            val y1 = p1.y * pageHeight
            val x2 = p2.x * pageWidth
            val y2 = p2.y * pageHeight
            
            when (item.shapeType) {
                com.nexus.feature.reader.pdf.components.ShapeType.Rectangle -> {
                    val rect = android.graphics.RectF(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
                    canvas.drawRect(rect, paint)
                }
                com.nexus.feature.reader.pdf.components.ShapeType.Oval -> {
                    val rect = android.graphics.RectF(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
                    canvas.drawOval(rect, paint)
                }
                com.nexus.feature.reader.pdf.components.ShapeType.Line -> {
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
                com.nexus.feature.reader.pdf.components.ShapeType.Arrow -> {
                    canvas.drawLine(x1, y1, x2, y2, paint)
                    val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                    val arrowSize = 25f
                    val xA = (x2 - arrowSize * Math.cos(angle - Math.PI / 6)).toFloat()
                    val yA = (y2 - arrowSize * Math.sin(angle - Math.PI / 6)).toFloat()
                    val xB = (x2 - arrowSize * Math.cos(angle + Math.PI / 6)).toFloat()
                    val yB = (y2 - arrowSize * Math.sin(angle + Math.PI / 6)).toFloat()
                    canvas.drawLine(x2, y2, xA, yA, paint)
                    canvas.drawLine(x2, y2, xB, yB, paint)
                }
                else -> {
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
        } else if (item.points.size > 1) {
            val path = android.graphics.Path()
            val first = item.points.first()
            path.moveTo(first.x * pageWidth, first.y * pageHeight)
            for (i in 1 until item.points.size) {
                val pt = item.points[i]
                path.lineTo(pt.x * pageWidth, pt.y * pageHeight)
            }
            canvas.drawPath(path, paint)
        }
    }
}
