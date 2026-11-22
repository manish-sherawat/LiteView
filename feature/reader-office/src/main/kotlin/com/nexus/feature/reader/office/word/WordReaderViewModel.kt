package com.nexus.feature.reader.office.word

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.core.preferences.UserPreferencesRepository
import com.nexus.core.utils.ErrorHandler
import com.nexus.feature.dashboard.data.DocumentBookmark
import com.nexus.feature.dashboard.data.DocumentBookmarkDao
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.feature.reader.office.word.model.DocxHeading
import com.nexus.feature.reader.office.word.model.WordReaderUiState
import com.nexus.feature.reader.office.word.parser.DocumentMargins
import com.nexus.feature.reader.office.word.parser.WordCitationParser
import com.nexus.feature.reader.office.word.parser.WordDiagramParser
import com.nexus.feature.reader.office.word.parser.WordLayoutEngine
import com.nexus.feature.reader.office.word.parser.WordMathParser
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import org.w3c.dom.Node
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class WordReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentDocumentDao: RecentDocumentDao,
    private val bookmarkDao: DocumentBookmarkDao,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WordReaderUiState>(WordReaderUiState.Loading())
    val uiState: StateFlow<WordReaderUiState> = _uiState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<DocumentBookmark>>(emptyList())
    val bookmarks: StateFlow<List<DocumentBookmark>> = _bookmarks.asStateFlow()

    private val _docxFontSize = MutableStateFlow(16f)
    val docxFontSize: StateFlow<Float> = _docxFontSize.asStateFlow()

    private val _docxIsSerif = MutableStateFlow(false)
    val docxIsSerif: StateFlow<Boolean> = _docxIsSerif.asStateFlow()

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
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")

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

    fun addBookmark(label: String, scrollY: Int) {
        val uriStr = currentEncodedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val decodedUri = safeDecode(uriStr)
            bookmarkDao.insertBookmark(
                DocumentBookmark(
                    documentUri = decodedUri,
                    pageIndex = scrollY,
                    label = label
                )
            )
        }
    }

    fun deleteBookmark(bookmark: DocumentBookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkDao.deleteBookmark(bookmark)
        }
    }

    fun loadDocument(encodedUri: String, docType: String, password: String? = null) {
        currentEncodedUri = encodedUri
        _currentUriFlow.value = encodedUri
        viewModelScope.launch {
            _uiState.value = WordReaderUiState.Loading()
            withContext(Dispatchers.IO) {
                try {
                    val decodedUri = safeDecode(encodedUri)
                    val uriStr = decodedUri

                    viewModelScope.launch(Dispatchers.IO) {
                        bookmarkDao.getBookmarksForDocument(decodedUri).collect { bks ->
                            _bookmarks.value = bks
                        }
                    }

                    val uri = Uri.parse(uriStr)

                    val fileSize = try {
                        if (uri.scheme == "file") java.io.File(uri.path ?: "").length()
                        else {
                            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (c.moveToFirst() && idx >= 0) c.getLong(idx) else 0L
                            } ?: 0L
                        }
                    } catch (_: Exception) { 0L }

                    val inputStream = openStream(uri)
                        ?: throw IllegalStateException("Cannot open file")

                    val rememberPosition = prefsRepository.rememberReadingPosition.first()
                    if (rememberPosition) {
                        val recentDoc = recentDocumentDao.findByUri(uriStr)
                        if (recentDoc != null &&
                            (recentDoc.lastScrollIndex > 0 || recentDoc.lastScrollOffset > 0)
                        ) {
                            _initialScrollPosition.value =
                                Pair(recentDoc.lastScrollIndex, recentDoc.lastScrollOffset)
                        }
                    }

                    val result = when (docType.uppercase()) {
                        "DOC"  -> parseDoc(inputStream)
                        else   -> parseDocx(inputStream, password)
                    }

                    val count = if (result is WordReaderUiState.Ready) result.pageCount else 0
                    if (count > 0) {
                        context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
                            .edit().putInt(uriStr, count).apply()
                    }

                    if (result is WordReaderUiState.Ready) {
                        try {
                            val name = try {
                                if (uri.scheme == "file") java.io.File(uri.path ?: "").name
                                else {
                                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                                    } ?: uri.lastPathSegment ?: "Word Document"
                                }
                            } catch (_: Exception) { "Word Document" }

                            val recentDoc = recentDocumentDao.findByUri(uriStr)
                            recentDocumentDao.upsert(
                                RecentDocument(
                                    uri = uriStr,
                                    fileName = name,
                                    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    fileSizeBytes = fileSize,
                                    lastOpenedAt = System.currentTimeMillis(),
                                    documentType = docType.uppercase(),
                                    lastScrollIndex = recentDoc?.lastScrollIndex ?: 0,
                                    lastScrollOffset = recentDoc?.lastScrollOffset ?: 0,
                                    pageCount = count
                                )
                            )
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = result
                    }
                } catch (e: org.apache.poi.EncryptedDocumentException) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = WordReaderUiState.PasswordRequired(encodedUri, docType, password != null)
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = WordReaderUiState.Error(
                            ErrorHandler.getUserFriendlyMessage(
                                e as? Exception ?: Exception(e)
                            )
                        )
                    }
                }
            }
        }
    }

    fun saveScrollPosition(encodedUri: String, index: Int, offset: Int) {
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

    fun setDocxFontSize(size: Float) { _docxFontSize.value = size.coerceIn(12f, 28f) }
    fun increaseDocxFontSize() { _docxFontSize.value = (_docxFontSize.value + 2f).coerceAtMost(28f) }
    fun decreaseDocxFontSize() { _docxFontSize.value = (_docxFontSize.value - 2f).coerceAtLeast(12f) }
    fun toggleDocxSerif() { _docxIsSerif.value = !_docxIsSerif.value }

    fun toggleDocxOutline() {
        val current = _uiState.value
        if (current is WordReaderUiState.Ready) {
            _uiState.value = current.copy(showOutline = !current.showOutline)
        }
    }

    private fun openStream(uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: return null))
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private suspend fun parseDoc(stream: java.io.InputStream): WordReaderUiState {
        HWPFDocument(stream).use { doc ->
            val pageSetup = com.nexus.feature.reader.office.word.parser.PageSetup(
                paperSize = com.nexus.feature.reader.office.word.parser.PaperSize.A4,
                widthPt = com.nexus.feature.reader.office.word.parser.PaperSize.A4.widthPt,
                heightPt = com.nexus.feature.reader.office.word.parser.PaperSize.A4.heightPt
            )
            val stylesheet = WordLayoutEngine.generateStylesheet(pageSetup)
            val clientScript = WordLayoutEngine.generateClientScript()

            val range = doc.range
            val pages = mutableListOf<String>()
            var currentPage = StringBuilder()
            var currentHeight = 0f
            val printableHeight = pageSetup.printableHeightPt
            val printableWidth = pageSetup.printableWidthPt
            val consecutiveDiagramLines = mutableListOf<String>()

            fun sealPage() {
                if (currentPage.isNotEmpty()) {
                    pages.add(currentPage.toString())
                    currentPage = StringBuilder()
                    currentHeight = 0f
                }
            }

            fun flushDiagramLines() {
                if (consecutiveDiagramLines.isNotEmpty()) {
                    val diagHeight = WordLayoutEngine.estimateDiagramHeight(consecutiveDiagramLines.size)
                    if (currentHeight + diagHeight > printableHeight && currentPage.isNotEmpty()) {
                        sealPage()
                    }
                    val diagBlock = buildString {
                        append("<div class=\"diagram-visual-block\">\n")
                        append("  <div class=\"diagram-header-bar\"><span class=\"diagram-badge\">Diagram</span></div>\n")
                        append("  <pre class=\"diagram-code-content\"><code>")
                        consecutiveDiagramLines.forEachIndexed { i, line ->
                            append(htmlEscape(line))
                            if (i < consecutiveDiagramLines.size - 1) append("\n")
                        }
                        append("</code></pre></div>\n")
                    }
                    currentPage.append(diagBlock)
                    currentHeight += diagHeight
                    consecutiveDiagramLines.clear()
                }
            }

            var totalWords = 0
            var totalChars = 0

            for (i in 0 until range.numParagraphs()) {
                if (i % 20 == 0) kotlinx.coroutines.yield()
                val para = range.getParagraph(i)
                val rawText = para.text() ?: ""

                val isDiagram = rawText.any { it in '\u2500'..'\u257F' || it in '\u2580'..'\u259F' } ||
                    rawText.contains("├──") || rawText.contains("└──") ||
                    Regex("""\+[-=]{2,}\+|\|[\s\w\W]{2,}\|""").containsMatchIn(rawText)

                if (isDiagram) {
                    consecutiveDiagramLines.add(rawText.trimEnd())
                    continue
                } else {
                    flushDiagramLines()
                }

                totalWords += rawText.split(Regex("\\s+")).count { it.isNotBlank() }
                totalChars += rawText.length

                val alignStr = when(para.justification) {
                    1 -> "center"
                    2 -> "right"
                    3 -> "justify"
                    else -> "left"
                }

                val charsPerLine = (printableWidth / 6.2f).toInt().coerceIn(24, 110)
                val lines = Math.max(1, Math.ceil(rawText.length.toDouble() / charsPerLine).toInt())
                val paraHeight = (lines * 19f) + 8f

                if (currentHeight + paraHeight > printableHeight && currentPage.isNotEmpty()) {
                    sealPage()
                }

                val paraSb = StringBuilder()
                paraSb.append("<p style=\"text-align:$alignStr;min-width:0;width:100%;max-width:100%;word-break:normal;overflow-wrap:break-word;white-space:normal;\">")
                for (j in 0 until para.numCharacterRuns()) {
                    val run = para.getCharacterRun(j)
                    var text = run.text()
                    if (text.isBlank() && text.isEmpty()) continue
                    text = WordMathParser.decodeMathSymbols(text)
                    var span = htmlEscape(text)
                    if (run.isBold) span = "<strong>$span</strong>"
                    if (run.isItalic) span = "<em>$span</em>"
                    if (run.isStrikeThrough) span = "<s>$span</s>"
                    paraSb.append(span)
                }
                paraSb.append("</p>\n")

                val citationFormatted = WordCitationParser.formatCitationTags(paraSb.toString())
                currentPage.append(citationFormatted)
                currentHeight += paraHeight
            }
            flushDiagramLines()
            sealPage()

            if (pages.isEmpty()) {
                pages.add("<p style=\"color: #64748b; font-style: italic;\">Document is empty</p>")
            }

            val totalPages = pages.size
            val sb = StringBuilder(1024 * 64)
            sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=yes\">\n")
            sb.append(stylesheet)
            sb.append("\n</head><body class=\"mode-paged\"><div class=\"docx-paged-container\">\n")

            for ((i, pageContent) in pages.withIndex()) {
                val pageNum = i + 1
                sb.append("<section class=\"docx-page\" data-page-number=\"$pageNum\">\n")
                sb.append("  <div class=\"docx-page-header\">\n")
                sb.append("    <span class=\"docx-header-title\">Document</span>\n")
                sb.append("    <span class=\"docx-header-num\">$pageNum</span>\n")
                sb.append("  </div>\n")
                sb.append("  <div class=\"docx-page-content\">\n")
                sb.append(pageContent)
                sb.append("  </div>\n")
                sb.append("  <div class=\"docx-page-footer\">\n")
                sb.append("    <span class=\"docx-footer-title\"></span>\n")
                sb.append("    <span class=\"docx-footer-num\">Page $pageNum of $totalPages</span>\n")
                sb.append("  </div>\n")
                sb.append("</section>\n")
            }

            sb.append("</div>\n")
            sb.append(clientScript)
            sb.append("\n</body></html>")

            return WordReaderUiState.Ready(
                htmlContent = sb.toString(),
                headings = emptyList(),
                pageCount = totalPages,
                paperSizeName = pageSetup.paperSize.name,
                paperDimensions = "${pageSetup.paperSize.widthMm.toInt()} × ${pageSetup.paperSize.heightMm.toInt()} mm",
                orientation = "Portrait",
                marginsDescription = pageSetup.margins.summary(),
                wordCount = totalWords,
                characterCount = totalChars,
                readingTimeMinutes = Math.max(1, Math.ceil(totalWords / 200.0).toInt())
            )
        }
    }

    private suspend fun parseDocx(stream: java.io.InputStream, password: String? = null): WordReaderUiState {
        var finalStream = stream
        var poifsToClose: POIFSFileSystem? = null

        if (password != null) {
            val poifs = POIFSFileSystem(stream)
            poifsToClose = poifs
            val info = EncryptionInfo(poifs)
            val decryptor = Decryptor.getInstance(info)
            if (!decryptor.verifyPassword(password)) {
                poifs.close()
                return WordReaderUiState.Error("Incorrect password.")
            }
            finalStream = decryptor.getDataStream(poifs)
        }

        try {
            XWPFDocument(finalStream).use { doc ->
                val headings = mutableListOf<DocxHeading>()

                // Layout Engine: extract paper size, orientation and margins
                val pageSetup = WordLayoutEngine.extractPageSetup(doc)
                val stylesheet = WordLayoutEngine.generateStylesheet(pageSetup)
                val clientScript = WordLayoutEngine.generateClientScript()

                val bodyElements = doc.bodyElements
                val total = bodyElements.size

                val author = try { doc.properties.coreProperties.creator } catch (_: Exception) { null }
                val title = try { doc.properties.coreProperties.title } catch (_: Exception) { null }
                val creationDate = try { doc.properties.coreProperties.created?.toString() } catch (_: Exception) { null }
                var totalWords = 0
                var totalChars = 0

                val headers = try { doc.headerList } catch (_: Exception) { emptyList() }
                val footers = try { doc.footerList } catch (_: Exception) { emptyList() }
                val headerText = headers.firstOrNull()?.paragraphs?.firstOrNull()?.text?.trim()
                    ?: title
                    ?: ""
                val footerText = footers.firstOrNull()?.paragraphs?.firstOrNull()?.text?.trim() ?: ""

                val pages = mutableListOf<String>()
                var currentPage = StringBuilder()
                var currentHeight = 0f
                val printableHeight = pageSetup.printableHeightPt
                val printableWidth = pageSetup.printableWidthPt

                fun sealCurrentPage() {
                    if (currentPage.isNotEmpty()) {
                        pages.add(currentPage.toString())
                        currentPage = StringBuilder()
                        currentHeight = 0f
                    }
                }

                // Buffer to group consecutive ASCII / box diagram paragraphs
                val diagramBuffer = mutableListOf<XWPFParagraph>()

                fun flushDiagramBuffer() {
                    if (diagramBuffer.isNotEmpty()) {
                        val diagHeight = WordLayoutEngine.estimateDiagramHeight(diagramBuffer.size)
                        if (currentHeight + diagHeight > printableHeight && currentPage.isNotEmpty()) {
                            sealCurrentPage()
                        }
                        currentPage.append(WordDiagramParser.renderDiagramBlock(diagramBuffer))
                        currentHeight += diagHeight
                        diagramBuffer.clear()
                    }
                }

                for ((idx, element) in bodyElements.withIndex()) {
                    if (idx % 20 == 0) {
                        _uiState.value = WordReaderUiState.Loading(
                            progress = idx.toFloat() / total.coerceAtLeast(1),
                            message = "Calculating layout & page boundaries…"
                        )
                        kotlinx.coroutines.yield()
                    }
                    when (element.elementType) {
                        BodyElementType.PARAGRAPH -> {
                            val para = element as XWPFParagraph
                            if (WordDiagramParser.isDiagramParagraph(para)) {
                                diagramBuffer.add(para)
                                continue
                            } else {
                                flushDiagramBuffer()
                            }

                            if (WordLayoutEngine.hasExplicitPageBreak(para)) {
                                sealCurrentPage()
                            }

                            val txt = para.text ?: ""
                            totalWords += txt.split(Regex("\\s+")).count { it.isNotBlank() }
                            totalChars += txt.length

                            val paraHeight = WordLayoutEngine.estimateParagraphHeight(para, printableWidth)
                            if (currentHeight + paraHeight > printableHeight && currentPage.isNotEmpty()) {
                                sealCurrentPage()
                            }

                            appendParagraphHtml(para, doc, currentPage, headings)
                            currentHeight += paraHeight
                        }
                        BodyElementType.TABLE -> {
                            flushDiagramBuffer()
                            val table = element as XWPFTable
                            for (row in table.rows) {
                                for (cell in row.tableCells) {
                                    val txt = cell.text ?: ""
                                    totalWords += txt.split(Regex("\\s+")).count { it.isNotBlank() }
                                    totalChars += txt.length
                                }
                            }

                            val totalTableHeight = WordLayoutEngine.estimateTableHeight(table, printableWidth)
                            val rowSpans = computeRowSpans(table)

                            if (currentHeight + totalTableHeight <= printableHeight) {
                                currentPage.append("<div class=\"table-container\"><table class=\"docx-table\">\n")
                                table.rows.forEachIndexed { rIdx, row ->
                                    appendTableRowHtml(row, rIdx, rowSpans, currentPage)
                                }
                                currentPage.append("</table></div>\n")
                                currentHeight += totalTableHeight
                            } else {
                                var tableOpen = false
                                val headerRow = table.rows.firstOrNull()

                                for ((rIdx, row) in table.rows.withIndex()) {
                                    val rowHeight = WordLayoutEngine.estimateTableRowHeight(row, printableWidth)

                                    if (!tableOpen) {
                                        if (currentHeight + rowHeight + 24f > printableHeight && currentPage.isNotEmpty()) {
                                            sealCurrentPage()
                                        }
                                        currentPage.append("<div class=\"table-container\"><table class=\"docx-table\">\n")
                                        tableOpen = true
                                        currentHeight += 18f

                                        if (rIdx > 0 && headerRow != null) {
                                            appendTableRowHtml(headerRow, 0, rowSpans, currentPage)
                                            currentHeight += WordLayoutEngine.estimateTableRowHeight(headerRow, printableWidth)
                                        }
                                    } else if (currentHeight + rowHeight > printableHeight && currentPage.isNotEmpty()) {
                                        currentPage.append("</table></div>\n")
                                        tableOpen = false
                                        sealCurrentPage()

                                        currentPage.append("<div class=\"table-container\"><table class=\"docx-table\">\n")
                                        tableOpen = true
                                        currentHeight += 18f

                                        if (headerRow != null) {
                                            appendTableRowHtml(headerRow, 0, rowSpans, currentPage)
                                            currentHeight += WordLayoutEngine.estimateTableRowHeight(headerRow, printableWidth)
                                        }
                                    }

                                    appendTableRowHtml(row, rIdx, rowSpans, currentPage)
                                    currentHeight += rowHeight
                                }

                                if (tableOpen) {
                                    currentPage.append("</table></div>\n")
                                }
                            }
                        }
                        else -> {
                            flushDiagramBuffer()
                        }
                    }
                }
                flushDiagramBuffer()
                sealCurrentPage()

                if (pages.isEmpty()) {
                    pages.add("<p style=\"color: #64748b; font-style: italic;\">Document is empty</p>")
                }

                val totalPages = pages.size
                val sb = StringBuilder(1024 * 64)
                sb.append("<!DOCTYPE html>\n<html>\n<head>\n")
                sb.append("<meta charset=\"UTF-8\">\n")
                sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=yes\">\n")
                sb.append(stylesheet)
                sb.append("\n</head>\n<body class=\"mode-paged\">\n<div class=\"docx-paged-container\">\n")

                for ((i, pageContent) in pages.withIndex()) {
                    val pageNum = i + 1
                    sb.append("<section class=\"docx-page\" data-page-number=\"$pageNum\">\n")
                    sb.append("  <div class=\"docx-page-header\">\n")
                    sb.append("    <span class=\"docx-header-title\">${htmlEscape(headerText)}</span>\n")
                    sb.append("    <span class=\"docx-header-num\">$pageNum</span>\n")
                    sb.append("  </div>\n")
                    sb.append("  <div class=\"docx-page-content\">\n")
                    sb.append(pageContent)
                    sb.append("  </div>\n")
                    sb.append("  <div class=\"docx-page-footer\">\n")
                    sb.append("    <span class=\"docx-footer-title\">${htmlEscape(footerText)}</span>\n")
                    sb.append("    <span class=\"docx-footer-num\">Page $pageNum of $totalPages</span>\n")
                    sb.append("  </div>\n")
                    sb.append("</section>\n")
                }

                val footnotes = try { doc.footnotes } catch (_: Exception) { emptyList() }
                val endnotes = try { doc.endnotes } catch (_: Exception) { emptyList() }
                if (footnotes.isNotEmpty() || endnotes.isNotEmpty()) {
                    sb.append("<section class=\"docx-page\" data-page-number=\"${totalPages + 1}\">\n")
                    sb.append("  <div class=\"docx-page-header\"><span class=\"docx-header-title\">Notes</span></div>\n")
                    sb.append("  <div class=\"docx-page-content\" style=\"font-size: 0.88em;\">\n")
                    sb.append("    <h3>Footnotes & Endnotes</h3>\n")
                    for (footnote in footnotes) {
                        sb.append("<div id=\"footnote_${footnote.id}\"><b>[${footnote.id}]</b> ")
                        for (element in footnote.bodyElements) {
                            if (element is XWPFParagraph) {
                                appendParagraphHtml(element, doc, sb, headings)
                            }
                        }
                        sb.append("</div>\n")
                    }
                    for (endnote in endnotes) {
                        sb.append("<div id=\"endnote_${endnote.id}\"><b>[${endnote.id}]</b> ")
                        for (element in endnote.bodyElements) {
                            if (element is XWPFParagraph) {
                                appendParagraphHtml(element, doc, sb, headings)
                            }
                        }
                        sb.append("</div>\n")
                    }
                    sb.append("  </div>\n")
                    sb.append("  <div class=\"docx-page-footer\"><span class=\"docx-footer-num\">Notes</span></div>\n")
                    sb.append("</section>\n")
                }

                sb.append("</div>\n")
                sb.append(clientScript)
                sb.append("\n</body>\n</html>")

                val finalPageCount = if (footnotes.isNotEmpty() || endnotes.isNotEmpty()) totalPages + 1 else totalPages

                return WordReaderUiState.Ready(
                    htmlContent = sb.toString(),
                    headings = headings,
                    author = author,
                    creationDate = creationDate,
                    wordCount = totalWords,
                    characterCount = totalChars,
                    readingTimeMinutes = Math.max(1, Math.ceil(totalWords / 200.0).toInt()),
                    pageCount = finalPageCount,
                    paperSizeName = pageSetup.paperSize.name,
                    paperDimensions = "${pageSetup.paperSize.widthMm.toInt()} × ${pageSetup.paperSize.heightMm.toInt()} mm",
                    orientation = if (pageSetup.orientation == com.nexus.feature.reader.office.word.parser.PageOrientation.LANDSCAPE) "Landscape" else "Portrait",
                    marginsDescription = pageSetup.margins.summary()
                )
            }
        } finally {
            poifsToClose?.close()
        }
    }

    private fun appendParagraphHtml(
        para: XWPFParagraph,
        doc: XWPFDocument,
        sb: StringBuilder,
        headings: MutableList<DocxHeading>
    ) {
        val styleId = try { para.style ?: "" } catch (_: Exception) { "" }
        val isHeading = styleId.lowercase().startsWith("heading")
        val headingLevel = if (isHeading) {
            styleId.filter { it.isDigit() }.firstOrNull()?.digitToInt()?.coerceIn(1, 6) ?: 1
        } else 0
        val isList = try { para.numID != null } catch (_: Exception) { false }
        val listLevel = try { para.numIlvl?.toInt() ?: 0 } catch (_: Exception) { 0 }
        val alignment = try { para.alignment ?: ParagraphAlignment.LEFT } catch (_: Exception) { ParagraphAlignment.LEFT }
        val alignStr = when (alignment) {
            ParagraphAlignment.CENTER -> "center"
            ParagraphAlignment.RIGHT  -> "right"
            ParagraphAlignment.BOTH   -> "justify"
            else                      -> "left"
        }

        val rawSpacingBefore = try { para.spacingBefore.toDouble() } catch (_: Exception) { -1.0 }
        val rawSpacingAfter = try { para.spacingAfter.toDouble() } catch (_: Exception) { -1.0 }
        val rawSpacingBetween = try { para.spacingBetween.toDouble() } catch (_: Exception) { -1.0 }

        val spacingBefore = if (rawSpacingBefore > 0) (rawSpacingBefore / 20.0).coerceIn(0.0, 36.0) else null
        val spacingAfter = if (rawSpacingAfter > 0) (rawSpacingAfter / 20.0).coerceIn(0.0, 36.0) else null
        val spacingBetween = if (rawSpacingBetween > 0) (rawSpacingBetween / 240.0).coerceIn(1.15, 2.2) else null

        val rawIndentLeft = try { para.indentationLeft } catch (_: Exception) { -1 }
        val rawIndentRight = try { para.indentationRight } catch (_: Exception) { -1 }
        val rawIndentFirstLine = try { para.indentationFirstLine } catch (_: Exception) { -1 }

        // Strict bounds checking on margins and indents: prevents narrow layout collapse (1 letter per line)
        val indentLeft = if (rawIndentLeft > 0) (rawIndentLeft / 20.0).coerceIn(0.0, 32.0) else null
        val indentRight = if (rawIndentRight > 0) (rawIndentRight / 20.0).coerceIn(0.0, 24.0) else null
        val indentFirstLine = if (rawIndentFirstLine > 0) (rawIndentFirstLine / 20.0).coerceIn(0.0, 24.0) else null

        val isPageBreak = WordLayoutEngine.hasExplicitPageBreak(para)
        val bookmarks = try { para.ctp.bookmarkStartList.map { it.name } } catch (_: Exception) { emptyList() }

        val pStyles = buildString {
            append("text-align:$alignStr;")
            val safeLineHeight = spacingBetween ?: 1.3
            append("line-height:$safeLineHeight;")
            append("min-height:1.2em;min-width:0;width:100%;max-width:100%;word-break:normal;overflow-wrap:break-word;white-space:normal;")

            if (spacingBefore != null) append("margin-top:${spacingBefore}pt;")
            if (spacingAfter != null) append("margin-bottom:${spacingAfter}pt;")
            if (indentLeft != null && !isList) append("margin-left:${indentLeft}pt;")
            if (indentRight != null) append("margin-right:${indentRight}pt;")
            if (indentFirstLine != null && !isList) append("text-indent:${indentFirstLine}pt;")
        }

        val rawText = try { para.text ?: "" } catch (_: Exception) { "" }
        val anchorsHtml = bookmarks.joinToString("") { "<a id=\"$it\"></a>" }
        val finalIdAttr = if (isHeading) "id=\"h_${headings.size}\"" else ""

        if (isPageBreak) {
            sb.append("<hr class=\"page-break\">\n")
        }

        val tempSb = StringBuilder()

        if (isHeading) {
            val headingText = rawText.trim()
            if (headingText.isNotEmpty()) {
                headings.add(DocxHeading(text = headingText, level = headingLevel, anchorId = "h_${headings.size}"))
            }
            tempSb.append("""<h$headingLevel $finalIdAttr style="$pStyles;min-width:0;width:100%;max-width:100%;word-break:normal;overflow-wrap:break-word;white-space:normal;">$anchorsHtml""")
            appendParagraphBodyContent(para, doc, tempSb)
            tempSb.append("</h$headingLevel>\n")
        } else if (isList) {
            val indent = (listLevel + 1) * 18
            val bullet = if (listLevel % 2 == 0) "&#8226;" else "&#9702;"
            val listStyles = "$pStyles;margin-left:${indent}px;min-width:0;width:100%;max-width:100%;word-break:normal;overflow-wrap:break-word;"
            tempSb.append("""<p $finalIdAttr style="$listStyles">$anchorsHtml$bullet&nbsp;""")
            appendParagraphBodyContent(para, doc, tempSb)
            tempSb.append("</p>\n")
        } else {
            if (rawText.isBlank() && !hasMathNodes(para) && para.runs.none { it.embeddedPictures.isNotEmpty() }) {
                tempSb.append("<p $finalIdAttr style=\"margin:4px 0;min-height:1em;\">$anchorsHtml&nbsp;</p>\n")
            } else {
                tempSb.append("""<p $finalIdAttr style="$pStyles">$anchorsHtml""")
                appendParagraphBodyContent(para, doc, tempSb)
                tempSb.append("</p>\n")
            }
        }

        val citationFormatted = WordCitationParser.formatCitationTags(tempSb.toString())
        sb.append(citationFormatted)
    }

    private fun computeRowSpans(table: XWPFTable): Map<Pair<Int, Int>, Int> {
        val rowSpans = mutableMapOf<Pair<Int, Int>, Int>()
        for (rIdx in table.rows.indices) {
            val row = table.rows[rIdx]
            for (cIdx in row.tableCells.indices) {
                val cell = row.tableCells[cIdx]
                val tcPr = try { cell.ctTc?.tcPr } catch (_: Exception) { null }
                val vMergeNode = try { tcPr?.vMerge } catch (_: Exception) { null }
                val vMergeVal = try { vMergeNode?.`val` } catch (_: Exception) { null }
                if (vMergeVal == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART) {
                    var span = 1
                    for (nextR in rIdx + 1 until table.rows.size) {
                        val nextRow = table.rows[nextR]
                        val nextCell = if (cIdx < nextRow.tableCells.size) nextRow.tableCells[cIdx] else break
                        val nextTcPr = try { nextCell.ctTc?.tcPr } catch (_: Exception) { null }
                        val nextVMergeNode = try { nextTcPr?.vMerge } catch (_: Exception) { null }
                        val nextVMergeVal = try { nextVMergeNode?.`val` } catch (_: Exception) { null }
                        val isContinue = nextVMergeNode != null && nextVMergeVal != org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART
                        if (isContinue) span++ else break
                    }
                    if (span > 1) rowSpans[Pair(rIdx, cIdx)] = span
                }
            }
        }
        return rowSpans
    }

    private fun appendTableRowHtml(
        row: XWPFTableRow,
        rowIdx: Int,
        rowSpans: Map<Pair<Int, Int>, Int>,
        sb: StringBuilder
    ) {
        sb.append("<tr>")
        row.tableCells.forEachIndexed { cIdx, cell ->
            val tag = if (rowIdx == 0) "th" else "td"
            val tcPr = try { cell.ctTc?.tcPr } catch (_: Exception) { null }

            val colSpan = try {
                tcPr?.gridSpan?.`val`?.toInt()?.coerceAtLeast(1) ?: 1
            } catch (_: Exception) { 1 }

            val vMergeNode = try { tcPr?.vMerge } catch (_: Exception) { null }
            val vMergeVal = try { vMergeNode?.`val` } catch (_: Exception) { null }
            val isVMergeContinue = vMergeNode != null && vMergeVal != org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART
            if (isVMergeContinue) return@forEachIndexed

            val rSpan = rowSpans[Pair(rowIdx, cIdx)] ?: 1

            val bgColor = try { cell.color?.takeIf { it.length == 6 && it != "auto" } } catch (_: Exception) { null }
            val vAlign = try { cell.verticalAlignment } catch (_: Exception) { null }
            val vAlignStr = when (vAlign) {
                org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.CENTER -> "middle"
                org.apache.poi.xwpf.usermodel.XWPFTableCell.XWPFVertAlign.BOTTOM -> "bottom"
                else -> "top"
            }

            val colSpanAttr = if (colSpan > 1) """ colspan="$colSpan"""" else ""
            val rowSpanAttr = if (rSpan > 1) """ rowspan="$rSpan"""" else ""
            val bgAttr = if (bgColor != null) "background-color:#$bgColor;" else ""
            val alignAttr = "vertical-align:$vAlignStr;"

            sb.append("<$tag$colSpanAttr$rowSpanAttr style=\"$bgAttr$alignAttr;min-width:40px;word-break:normal;overflow-wrap:break-word;\">")

            var firstPara = true
            for (para in cell.paragraphs) {
                val cellText = try { para.text ?: "" } catch (_: Exception) { "" }
                if (cellText.isBlank() && firstPara) { firstPara = false; continue }
                if (!firstPara) sb.append("<br>")
                try {
                    appendRunsHtmlSimple(para, sb)
                } catch (_: Exception) {
                    sb.append(htmlEscape(cellText))
                }
                firstPara = false
            }
            sb.append("</$tag>")
        }
        sb.append("</tr>\n")
    }

    private fun hasMathNodes(para: XWPFParagraph): Boolean {
        val domNode = try { para.ctp.domNode } catch (_: Exception) { null } ?: return false
        val childList = domNode.childNodes ?: return false
        for (i in 0 until childList.length) {
            val item = childList.item(i) ?: continue
            if (WordMathParser.isMathNode(item)) return true
        }
        return false
    }

    private fun appendParagraphBodyContent(
        para: XWPFParagraph,
        doc: XWPFDocument,
        sb: StringBuilder
    ) {
        val domNode = try { para.ctp.domNode } catch (_: Exception) { null }
        if (domNode != null) {
            val childNodes = domNode.childNodes
            var hasHandledMath = false
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i) ?: continue
                if (WordMathParser.isMathNode(node)) {
                    WordMathParser.parseOMML(node, sb)
                    hasHandledMath = true
                }
            }
            if (hasHandledMath) return
        }
        appendRunsHtml(para, doc, sb)
    }

    private fun appendRunsHtml(
        para: XWPFParagraph,
        doc: XWPFDocument,
        sb: StringBuilder
    ) {
        for (run in para.runs) {
            try {
                // Embedded images — base64 data URIs
                for (pic in run.embeddedPictures) {
                    try {
                        val data = pic.pictureData.data
                        val ext = pic.pictureData.suggestFileExtension() ?: "png"
                        val mime = when (ext.lowercase()) {
                            "jpg", "jpeg" -> "image/jpeg"
                            "gif"         -> "image/gif"
                            "svg"         -> "image/svg+xml"
                            "bmp"         -> "image/bmp"
                            "webp"        -> "image/webp"
                            else          -> "image/png"
                        }
                        val b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                        sb.append("""<img src="data:$mime;base64,$b64" style="max-width:100%; display:inline-block; overflow:hidden;">""")
                    } catch (_: Exception) { /* skip bad image data */ }
                }

                val text = run.text() ?: ""
                if (text.isEmpty()) continue

                val isBold      = try { run.isBold } catch (_: Exception) { false }
                val isItalic    = try { run.isItalic } catch (_: Exception) { false }
                val underlinePattern = try { run.underline } catch (_: Exception) { null }
                val isUnderline = underlinePattern != null && underlinePattern != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE
                val isStrike    = try { run.isStrikeThrough } catch (_: Exception) { false }
                val isDoubleStrike = try { run.isDoubleStrikeThrough } catch (_: Exception) { false }
                val colorHex    = try { run.color?.let { if (it.length == 6) "#$it" else null } } catch (_: Exception) { null }
                val fontSize    = try { run.fontSizeAsDouble?.toInt()?.takeIf { it > 0 } } catch (_: Exception) { null }
                val fontFamily  = try { run.fontFamily } catch (_: Exception) { null }
                val vAlign      = try { run.verticalAlignment?.toString()?.lowercase() } catch (_: Exception) { null }
                val isSubscript = vAlign == "subscript"
                val isSuperscript = vAlign == "superscript"
                val isCapitalized = try { run.isCapitalized } catch (_: Exception) { false }
                val isSmallCaps   = try { run.isSmallCaps } catch (_: Exception) { false }
                val highlightColor = try { run.textHighlightColor?.toString()?.takeIf { it != "none" && it.isNotBlank() } } catch (_: Exception) { null }

                val isHyperlink = run is XWPFHyperlinkRun
                val url = if (isHyperlink) {
                    try {
                        val hr = run as XWPFHyperlinkRun
                        if (!hr.anchor.isNullOrEmpty()) {
                            "#" + hr.anchor
                        } else {
                            doc.getHyperlinkByID(hr.hyperlinkId)?.url
                        }
                    } catch (_: Exception) { null }
                } else null

                val footnoteIds = try { run.ctr?.footnoteReferenceList?.map { it.id } ?: emptyList() } catch (_: Exception) { emptyList() }
                val endnoteIds = try { run.ctr?.endnoteReferenceList?.map { it.id } ?: emptyList() } catch (_: Exception) { emptyList() }

                val decodedText = WordMathParser.decodeMathSymbols(text)

                // HTML-escape text
                val escaped = htmlEscape(decodedText)
                    .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")

                // Build inline styles
                val styles = buildString {
                    if (colorHex != null) append("color:$colorHex;")
                    if (fontSize != null) append("font-size:${fontSize}pt;")
                    if (fontFamily != null) append("font-family:'$fontFamily';")
                    if (highlightColor != null) append("background-color:$highlightColor;")
                    if (isCapitalized) append("text-transform:uppercase;")
                    if (isSmallCaps) append("font-variant:small-caps;")
                    if (isUnderline) {
                        val cssStyle = when {
                            underlinePattern.name.contains("DOUBLE") -> "double"
                            underlinePattern.name.contains("DASH") -> "dashed"
                            underlinePattern.name.contains("DOT") -> "dotted"
                            underlinePattern.name.contains("WAV") -> "wavy"
                            else -> "solid"
                        }
                        if (cssStyle != "solid") {
                            append("text-decoration:underline;text-decoration-style:$cssStyle;")
                        }
                    }
                }

                var span = escaped
                if (isBold)      span = "<strong>$span</strong>"
                if (isItalic)    span = "<em>$span</em>"
                if (isUnderline && !styles.contains("text-decoration-style")) span = "<u>$span</u>"
                if (isStrike || isDoubleStrike) span = "<s>$span</s>"
                if (isSubscript) span = "<sub>$span</sub>"
                if (isSuperscript) span = "<sup>$span</sup>"
                if (styles.isNotEmpty()) span = """<span style="$styles">$span</span>"""
                if (url != null) {
                    if (url.startsWith("#")) span = """<a href="$url">$span</a>"""
                    else span = """<a href="${htmlEscape(url)}">$span</a>"""
                }

                for (fnId in footnoteIds) span += "<sup><a href=\"#footnote_$fnId\">[$fnId]</a></sup>"
                for (enId in endnoteIds) span += "<sup><a href=\"#endnote_$enId\">[$enId]</a></sup>"

                sb.append(span)
            } catch (_: Exception) { /* skip bad run */ }
        }
    }

    private fun appendTableHtml(
        table: XWPFTable,
        sb: StringBuilder
    ) {
        val rowSpans = computeRowSpans(table)
        sb.append("<div class=\"table-container\"><table class=\"docx-table\">\n")
        table.rows.forEachIndexed { rowIdx, row ->
            appendTableRowHtml(row, rowIdx, rowSpans, sb)
        }
        sb.append("</table></div>\n")
    }

    private fun appendRunsHtmlSimple(
        para: XWPFParagraph,
        sb: StringBuilder
    ) {
        for (run in para.runs) {
            val text = try { run.text() ?: "" } catch (_: Exception) { "" }
            if (text.isEmpty()) continue
            val decoded = WordMathParser.decodeMathSymbols(text)
            val isBold   = try { run.isBold } catch (_: Exception) { false }
            val isItalic = try { run.isItalic } catch (_: Exception) { false }
            var span = htmlEscape(decoded)
            if (isBold)   span = "<strong>$span</strong>"
            if (isItalic) span = "<em>$span</em>"
            sb.append(span)
        }
    }

    private fun htmlEscape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
