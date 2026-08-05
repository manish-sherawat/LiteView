package com.nexus.feature.reader.office

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
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.core.preferences.UserPreferencesRepository
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.crypt.Decryptor

// ─── Office Content Models ────────────────────────────────────────────────────

/**
 * A heading extracted from a DOCX document, used to populate the outline drawer.
 * @param text    The visible heading text.
 * @param level   Heading level 1–6 (matches Word heading styles).
 * @param anchorId The id attribute set on the <h> element in the generated HTML.
 */
data class DocxHeading(
    val text: String,
    val level: Int,
    val anchorId: String
)

/** A spreadsheet cell with display text and optional styling. */
data class XlsxCell(
    val text: String,
    val rawFormula: String? = null,
    val isBold: Boolean,
    val backgroundColorHex: String? = null,
    val textColorHex: String? = null,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val isHidden: Boolean = false
)

/** A spreadsheet table row. */
data class XlsxRow(val cells: List<XlsxCell>)

// ─── UI State ─────────────────────────────────────────────────────────────────

sealed class OfficeReaderUiState {
    data class Loading(val progress: Float? = null, val message: String? = null) : OfficeReaderUiState()

    /**
     * DOCX document loaded. The content is pre-rendered as an HTML string
     * (produced by the ViewModel) so the UI layer only needs to hand it to a WebView.
     */
    data class DocxReady(
        val htmlContent: String,
        val headings: List<DocxHeading> = emptyList(),
        val showOutline: Boolean = false,
        val author: String? = null,
        val creationDate: String? = null,
        val wordCount: Int? = null
    ) : OfficeReaderUiState()

    data class XlsxReady(
        val sheetNames: List<String>,
        val sheetsRows: List<List<XlsxRow>>,
        val sheetsColumnWidths: List<List<Int>>,
        val currentSheet: Int = 0,
        val showGridlines: Boolean = true,
        val selectedCell: Pair<Int, Int>? = null,
        val searchQuery: String = "",
        val searchMatches: List<Pair<Int, Int>> = emptyList(),
        val currentMatchIndex: Int = 0
    ) : OfficeReaderUiState()

    data class PasswordRequired(val encodedUri: String, val docType: String, val isError: Boolean = false) : OfficeReaderUiState()

    data class Error(val message: String) : OfficeReaderUiState()
}

// ─── Office Reader ViewModel ──────────────────────────────────────────────────
// Parses DOCX (→ HTML) and XLSX files using Apache POI on the IO dispatcher.

@HiltViewModel
class OfficeReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentDocumentDao: RecentDocumentDao,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OfficeReaderUiState>(OfficeReaderUiState.Loading())
    val uiState: StateFlow<OfficeReaderUiState> = _uiState.asStateFlow()

    // Typography preferences for DOCX
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
        // Required for Apache POI 5.x on Android
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

    fun loadDocument(encodedUri: String, docType: String, password: String? = null) {
        viewModelScope.launch {
            _uiState.value = OfficeReaderUiState.Loading()
            withContext(Dispatchers.IO) {
                try {
                    val uriStr = safeDecode(encodedUri)
                    val uri = Uri.parse(uriStr)

                    // Enforce 50 MB file size limit
                    val fileSize = getFileSize(uri)
                    if (fileSize > 50L * 1024 * 1024) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = OfficeReaderUiState.Error(
                                "File exceeds the 50 MB limit for Office documents."
                            )
                        }
                        return@withContext
                    }

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
                        "DOCX" -> parseDocx(inputStream, password)
                        "DOC"  -> parseDoc(inputStream)
                        "XLSX" -> parseXlsx(inputStream, password)
                        else   -> throw UnsupportedOperationException("Unsupported type: $docType")
                    }

                    // Persist element count for progress display
                    val count = when (result) {
                        is OfficeReaderUiState.DocxReady -> result.headings.size
                        is OfficeReaderUiState.XlsxReady -> result.sheetsRows.getOrNull(0)?.size ?: 0
                        else -> 0
                    }
                    if (count > 0) {
                        context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
                            .edit().putInt(uriStr, count).apply()
                    }

                    withContext(Dispatchers.Main) { 
                        if (result is OfficeReaderUiState.PasswordRequired) {
                            // This logic is specifically for if the parse helper returned this status
                            _uiState.value = result
                        } else {
                            _uiState.value = result 
                        }
                    }
                } catch (e: org.apache.poi.EncryptedDocumentException) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = OfficeReaderUiState.PasswordRequired(encodedUri, docType, password != null)
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = OfficeReaderUiState.Error(
                            com.nexus.core.utils.ErrorHandler.getUserFriendlyMessage(
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
                    val uriStr = URLDecoder.decode(
                        URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()),
                        StandardCharsets.UTF_8.toString()
                    )
                    recentDocumentDao.updateScrollPosition(uriStr, index, offset)
                }
            } catch (_: Throwable) {}
        }
    }

    // ── DOCX typography preferences ───────────────────────────────────────────

    fun setDocxFontSize(size: Float) { _docxFontSize.value = size.coerceIn(12f, 28f) }
    fun increaseDocxFontSize() { _docxFontSize.value = (_docxFontSize.value + 2f).coerceAtMost(28f) }
    fun decreaseDocxFontSize() { _docxFontSize.value = (_docxFontSize.value - 2f).coerceAtLeast(12f) }
    fun toggleDocxSerif() { _docxIsSerif.value = !_docxIsSerif.value }

    fun toggleDocxOutline() {
        val current = _uiState.value
        if (current is OfficeReaderUiState.DocxReady) {
            _uiState.value = current.copy(showOutline = !current.showOutline)
        }
    }

    // ── XLSX helpers ──────────────────────────────────────────────────────────

    fun switchSheet(sheetIndex: Int) {
        val current = _uiState.value
        if (current is OfficeReaderUiState.XlsxReady) {
            _uiState.value = current.copy(currentSheet = sheetIndex)
        }
    }

    fun toggleGridlines() {
        val current = _uiState.value
        if (current is OfficeReaderUiState.XlsxReady) {
            _uiState.value = current.copy(showGridlines = !current.showGridlines)
        }
    }

    fun selectCell(row: Int, col: Int) {
        val current = _uiState.value
        if (current is OfficeReaderUiState.XlsxReady) {
            _uiState.value = current.copy(selectedCell = Pair(row, col))
        }
    }

    fun setSearchQuery(query: String) {
        val current = _uiState.value
        if (current is OfficeReaderUiState.XlsxReady) {
            if (query.isEmpty()) {
                _uiState.value = current.copy(searchQuery = query, searchMatches = emptyList(), currentMatchIndex = 0)
                return
            }
            val matches = mutableListOf<Pair<Int, Int>>()
            val rows = current.sheetsRows.getOrNull(current.currentSheet) ?: emptyList()
            for ((rIdx, row) in rows.withIndex()) {
                for ((cIdx, cell) in row.cells.withIndex()) {
                    if (cell.text.contains(query, ignoreCase = true) && !cell.isHidden) {
                        matches.add(Pair(rIdx, cIdx))
                    }
                }
            }
            _uiState.value = current.copy(
                searchQuery = query,
                searchMatches = matches,
                currentMatchIndex = 0,
                selectedCell = matches.firstOrNull()
            )
        }
    }

    fun nextSearchMatch() {
        val current = _uiState.value
        if (current is OfficeReaderUiState.XlsxReady && current.searchMatches.isNotEmpty()) {
            val nextIdx = (current.currentMatchIndex + 1) % current.searchMatches.size
            _uiState.value = current.copy(
                currentMatchIndex = nextIdx,
                selectedCell = current.searchMatches[nextIdx]
            )
        }
    }

    fun previousSearchMatch() {
        val current = _uiState.value
        if (current is OfficeReaderUiState.XlsxReady && current.searchMatches.isNotEmpty()) {
            val prevIdx = if (current.currentMatchIndex - 1 < 0)
                current.searchMatches.size - 1
            else
                current.currentMatchIndex - 1
            _uiState.value = current.copy(
                currentMatchIndex = prevIdx,
                selectedCell = current.searchMatches[prevIdx]
            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun getFileSize(uri: Uri): Long {
        return try {
            if (uri.scheme == "file") {
                java.io.File(uri.path ?: "").length()
            } else {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                } ?: 0L
            }
        } catch (_: Exception) { 0L }
    }

    private fun openStream(uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: return null))
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private suspend fun parseDoc(stream: java.io.InputStream): OfficeReaderUiState {
        HWPFDocument(stream).use { doc ->
            val sb = StringBuilder(1024 * 64)
            sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>body { margin: 0; padding: 16px; padding-top: 72px; padding-bottom: 80px; font-family: sans-serif; font-size: 16px; line-height: 1.7; color: #1a1a1a; word-wrap: break-word; } p { margin: 0.4em 0; } .dark-mode { color: #e8e8e8; } strong { font-weight: bold; } em { font-style: italic; } s { text-decoration: line-through; }</style></head><body><div id=\"content\">\n")

            val range = doc.range
            for (i in 0 until range.numParagraphs()) {
                if (i % 20 == 0) kotlinx.coroutines.yield()
                val para = range.getParagraph(i)
                val alignStr = when(para.justification) {
                    1 -> "center"
                    2 -> "right"
                    3 -> "justify"
                    else -> "left"
                }
                sb.append("<p style=\"text-align:$alignStr\">")
                for (j in 0 until para.numCharacterRuns()) {
                    val run = para.getCharacterRun(j)
                    val text = run.text().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    if (text.isBlank() && text.isEmpty()) continue
                    var span = text
                    if (run.isBold) span = "<strong>$span</strong>"
                    if (run.isItalic) span = "<em>$span</em>"
                    if (run.isStrikeThrough) span = "<s>$span</s>"
                    sb.append(span)
                }
                sb.append("</p>\n")
            }
            sb.append("\n</div></body></html>")
            
            return OfficeReaderUiState.DocxReady(
                htmlContent = sb.toString(),
                headings = emptyList()
            )
        }
    }

    // ── DOCX → HTML Parser ────────────────────────────────────────────────────
    // Converts the Word document to a self-contained HTML string.
    // Images are embedded as base64 data URIs (no temp files needed).

    private suspend fun parseDocx(stream: java.io.InputStream, password: String? = null): OfficeReaderUiState {
        var finalStream = stream
        var poifsToClose: POIFSFileSystem? = null

        if (password != null) {
            val poifs = POIFSFileSystem(stream)
            poifsToClose = poifs
            val info = EncryptionInfo(poifs)
            val decryptor = Decryptor.getInstance(info)
            if (!decryptor.verifyPassword(password)) {
                poifs.close()
                return OfficeReaderUiState.Error("Incorrect password.")
            }
            finalStream = decryptor.getDataStream(poifs)
        }

        try {
            XWPFDocument(finalStream).use { doc ->
            val headings = mutableListOf<DocxHeading>()
            val sb = StringBuilder(1024 * 64) // preallocate 64 KB

            // ── CSS ──────────────────────────────────────────────────────────
            sb.append(
                """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 16px; padding-top: 72px; padding-bottom: 80px;
    font-family: sans-serif; font-size: 16px; line-height: 1.7;
    color: #1a1a1a; background: transparent;
    word-wrap: break-word; overflow-wrap: break-word;
  }
  h1 {
    font-size: 1.8em; font-weight: 700; margin: 1.2em 0 0.4em;
    padding: 10px 14px; border-left: 5px solid #3B7DD8;
    background: rgba(59,125,216,0.10); border-radius: 6px;
  }
  h2 {
    font-size: 1.45em; font-weight: 600; margin: 1em 0 0.35em;
    padding: 8px 12px; border-left: 4px solid rgba(59,125,216,0.7);
    background: rgba(59,125,216,0.07); border-radius: 5px;
  }
  h3 { font-size: 1.2em; font-weight: 600; margin: 0.9em 0 0.3em; color: #1a3a6b; }
  h4 { font-size: 1.05em; font-weight: 600; margin: 0.8em 0 0.25em; color: #2a4a7f; }
  h5, h6 { font-size: 0.95em; font-weight: 600; margin: 0.7em 0 0.2em; }
  p { margin: 0.4em 0; }
  img { max-width: 100%; height: auto; object-fit: contain; border-radius: 8px; margin: 10px 0; display: block; }
  table {
    width: 100%; border-collapse: collapse; margin: 14px 0;
    overflow-x: auto; display: block; border-radius: 8px;
    border: 1px solid rgba(59,125,216,0.25);
  }
  thead tr { background: rgba(59,125,216,0.14); }
  th {
    padding: 9px 12px; text-align: left; font-weight: 600; font-size: 0.9em;
    border: 1px solid rgba(59,125,216,0.2); color: #1a3a6b;
  }
  td {
    padding: 8px 12px; border: 1px solid rgba(128,128,128,0.2);
    font-size: 0.9em; vertical-align: top;
  }
  tr:nth-child(even) td { background: rgba(128,128,128,0.04); }
  tr:hover td { background: rgba(59,125,216,0.06); }
  a { color: #3B7DD8; text-decoration: underline; }
  hr { border: none; border-top: 1px solid rgba(128,128,128,0.25); margin: 16px 0; }
  .list-bullet { margin-left: 1.5em; }
  .page-break { border-top: 2px dashed #888; margin: 40px 0; page-break-after: always; }
  .dark-mode { color: #e8e8e8; }
  .dark-mode h3, .dark-mode h4 { color: #90b8f8; }
  .dark-mode h5, .dark-mode h6 { color: #8ab4f8; }
  .dark-mode h1 { background: rgba(59,125,216,0.18); border-left-color: #6ba3e8; }
  .dark-mode h2 { background: rgba(59,125,216,0.14); border-left-color: rgba(107,163,232,0.8); }
  .dark-mode thead tr { background: rgba(59,125,216,0.22); }
  .dark-mode th { color: #90b8f8; border-color: rgba(59,125,216,0.3); }
  .dark-mode td { border-color: rgba(180,180,180,0.15); }
  .dark-mode tr:nth-child(even) td { background: rgba(255,255,255,0.04); }
  .dark-mode tr:hover td { background: rgba(59,125,216,0.1); }
  .dark-mode table { border-color: rgba(59,125,216,0.35); }
</style>
</head>
<body>
<div id="content">
"""
            )

            // ── Parse body elements ───────────────────────────────────────────
            val bodyElements = doc.bodyElements
            val total = bodyElements.size

            val author = try { doc.properties.coreProperties.creator } catch (_: Exception) { null }
            val creationDate = try { doc.properties.coreProperties.created?.toString() } catch (_: Exception) { null }
            var totalWords = 0

            val headers = try { doc.headerList } catch (_: Exception) { emptyList() }
            if (headers.isNotEmpty()) {
                sb.append("<div style=\"opacity: 0.6; border-bottom: 1px solid #ccc; padding-bottom: 8px; margin-bottom: 16px;\">\n")
                for (header in headers) {
                    for (element in header.bodyElements) {
                        if (element is org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                            appendParagraphHtml(element, doc, sb, headings)
                        } else if (element is org.apache.poi.xwpf.usermodel.XWPFTable) {
                            appendTableHtml(element, sb)
                        }
                    }
                }
                sb.append("</div>\n")
            }

            for ((idx, element) in bodyElements.withIndex()) {
                if (idx % 20 == 0) {
                    _uiState.value = OfficeReaderUiState.Loading(
                        progress = idx.toFloat() / total.coerceAtLeast(1),
                        message = "Loading document…"
                    )
                    kotlinx.coroutines.yield()
                }
                when (element.elementType) {
                    org.apache.poi.xwpf.usermodel.BodyElementType.PARAGRAPH -> {
                        val para = element as org.apache.poi.xwpf.usermodel.XWPFParagraph
                        totalWords += (para.text ?: "").split(Regex("\\s+")).count { it.isNotBlank() }
                        appendParagraphHtml(para, doc, sb, headings)
                    }
                    org.apache.poi.xwpf.usermodel.BodyElementType.TABLE -> {
                        val table = element as org.apache.poi.xwpf.usermodel.XWPFTable
                        for (row in table.rows) {
                            for (cell in row.tableCells) {
                                totalWords += (cell.text ?: "").split(Regex("\\s+")).count { it.isNotBlank() }
                            }
                        }
                        appendTableHtml(table, sb)
                    }
                    else -> {}
                }
            }

            val footers = try { doc.footerList } catch (_: Exception) { emptyList() }
            if (footers.isNotEmpty()) {
                sb.append("<div style=\"opacity: 0.6; border-top: 1px solid #ccc; padding-top: 8px; margin-top: 16px;\">\n")
                for (footer in footers) {
                    for (element in footer.bodyElements) {
                        if (element is org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                            appendParagraphHtml(element, doc, sb, headings)
                        } else if (element is org.apache.poi.xwpf.usermodel.XWPFTable) {
                            appendTableHtml(element, sb)
                        }
                    }
                }
                sb.append("</div>\n")
            }

            val footnotes = try { doc.footnotes } catch (_: Exception) { emptyList() }
            val endnotes = try { doc.endnotes } catch (_: Exception) { emptyList() }
            if (footnotes.isNotEmpty() || endnotes.isNotEmpty()) {
                sb.append("<hr style=\"margin-top: 40px;\">\n<div style=\"font-size: 0.85em; opacity: 0.8;\">\n")
                for (footnote in footnotes) {
                    sb.append("<div id=\"footnote_${footnote.id}\"><b><a href=\"#\">[${footnote.id}]</a></b> ")
                    for (element in footnote.bodyElements) {
                        if (element is org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                            appendParagraphHtml(element, doc, sb, headings)
                        }
                    }
                    sb.append("</div>\n")
                }
                for (endnote in endnotes) {
                    sb.append("<div id=\"endnote_${endnote.id}\"><b><a href=\"#\">[${endnote.id}]</a></b> ")
                    for (element in endnote.bodyElements) {
                        if (element is org.apache.poi.xwpf.usermodel.XWPFParagraph) {
                            appendParagraphHtml(element, doc, sb, headings)
                        }
                    }
                    sb.append("</div>\n")
                }
                sb.append("</div>\n")
            }

            sb.append("\n</div></body></html>")

            return OfficeReaderUiState.DocxReady(
                htmlContent = sb.toString(),
                headings = headings,
                author = author,
                creationDate = creationDate,
                wordCount = totalWords
            )
            }
        } finally {
            poifsToClose?.close()
        }
    }

    /** Converts a single paragraph to its HTML representation. */
    private fun appendParagraphHtml(
        para: org.apache.poi.xwpf.usermodel.XWPFParagraph,
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

        val spacingBefore = try { para.spacingBefore.toDouble() / 20.0 } catch (_: Exception) { null }
        val spacingAfter = try { para.spacingAfter.toDouble() / 20.0 } catch (_: Exception) { null }
        val spacingBetween = try { para.spacingBetween.toDouble() / 240.0 } catch (_: Exception) { null }
        val indentLeft = try { (para.indentationLeft.toDouble() / 20.0).coerceAtLeast(0.0) } catch (_: Exception) { null }
        val indentRight = try { (para.indentationRight.toDouble() / 20.0).coerceAtLeast(0.0) } catch (_: Exception) { null }
        val indentFirstLine = try { para.indentationFirstLine.toDouble() / 20.0 } catch (_: Exception) { null }
        val isPageBreak = try { para.isPageBreak } catch (_: Exception) { false }
        val bookmarks = try { para.ctp.bookmarkStartList.map { it.name } } catch (_: Exception) { emptyList() }
        val pStyles = buildString {
            append("text-align:$alignStr;")
            // Enforce minimum line-height to prevent overlapping text (min 1.1x)
            val safeLineHeight = spacingBetween?.coerceAtLeast(1.1) ?: 1.3
            append("line-height:$safeLineHeight;")
            append("min-height:1.2em;overflow:visible;") // dynamic height for text blocks
            
            if (spacingBefore != null && spacingBefore > 0) append("margin-top:${spacingBefore}pt;")
            if (spacingAfter != null && spacingAfter > 0) append("margin-bottom:${spacingAfter}pt;")
            if (indentLeft != null && indentLeft > 0 && !isList) append("margin-left:${indentLeft}pt;")
            if (indentRight != null && indentRight > 0) append("margin-right:${indentRight}pt;")
            if (indentFirstLine != null && indentFirstLine > 0 && !isList) append("text-indent:${indentFirstLine}pt;")
        }

        // Check if paragraph text is blank (skip empty paragraphs unless heading)
        val rawText = try { para.text ?: "" } catch (_: Exception) { "" }

        val anchorsHtml = bookmarks.joinToString("") { "<a id=\"$it\"></a>" }
        val finalIdAttr = if (isHeading) "id=\"h_${headings.size}\"" else ""

        if (isPageBreak) {
            sb.append("<hr class=\"page-break\">\n")
        }

        if (isHeading) {
            val headingText = rawText.trim()
            if (headingText.isNotEmpty()) {
                headings.add(DocxHeading(text = headingText, level = headingLevel, anchorId = "h_${headings.size}"))
            }
            sb.append("""<h$headingLevel $finalIdAttr style="$pStyles">$anchorsHtml""")
            appendRunsHtml(para, doc, sb)
            sb.append("</h$headingLevel>\n")
        } else if (isList) {
            val indent = (listLevel + 1) * 20
            val bullet = if (listLevel % 2 == 0) "&#8226;" else "&#9702;"
            val listStyles = "$pStyles;margin-left:${indent}px"
            sb.append("""<p $finalIdAttr style="$listStyles">$anchorsHtml$bullet&nbsp;""")
            appendRunsHtml(para, doc, sb)
            sb.append("</p>\n")
        } else {
            if (rawText.isBlank()) {
                // Small vertical gap for blank lines
                sb.append("<p $finalIdAttr style=\"margin:4px 0\">$anchorsHtml&nbsp;</p>\n")
            } else {
                sb.append("""<p $finalIdAttr style="$pStyles">$anchorsHtml""")
                appendRunsHtml(para, doc, sb)
                sb.append("</p>\n")
            }
        }
    }

    /** Converts all runs in a paragraph to inline HTML spans. */
    private fun appendRunsHtml(
        para: org.apache.poi.xwpf.usermodel.XWPFParagraph,
        doc: XWPFDocument,
        sb: StringBuilder
    ) {
        for (run in para.runs) {
            try {
                // Embedded images — base64 data URIs (no temp files)
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

                val isHyperlink = run is org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
                val url = if (isHyperlink) {
                    try {
                        val hr = run as org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
                        if (!hr.anchor.isNullOrEmpty()) {
                            "#" + hr.anchor
                        } else {
                            doc.getHyperlinkByID(hr.hyperlinkId)?.url
                        }
                    } catch (_: Exception) { null }
                } else null

                val footnoteIds = try { run.ctr?.footnoteReferenceList?.map { it.id } ?: emptyList() } catch (_: Exception) { emptyList() }
                val endnoteIds = try { run.ctr?.endnoteReferenceList?.map { it.id } ?: emptyList() } catch (_: Exception) { emptyList() }

                // HTML-escape text
                val escaped = text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
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

    /** Converts a table element to an HTML <table>. */
    private fun appendTableHtml(
        table: org.apache.poi.xwpf.usermodel.XWPFTable,
        sb: StringBuilder
    ) {
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

        sb.append("<table>\n")
        table.rows.forEachIndexed { rowIdx, row ->
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

                sb.append("<$tag$colSpanAttr$rowSpanAttr style=\"$bgAttr$alignAttr\">")

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
        sb.append("</table>\n")
    }

    /**
     * Simplified run renderer for table cells — no image extraction,
     * just inline bold/italic/color.
     */
    private fun appendRunsHtmlSimple(
        para: org.apache.poi.xwpf.usermodel.XWPFParagraph,
        sb: StringBuilder
    ) {
        for (run in para.runs) {
            val text = try { run.text() ?: "" } catch (_: Exception) { "" }
            if (text.isEmpty()) continue
            val isBold   = try { run.isBold } catch (_: Exception) { false }
            val isItalic = try { run.isItalic } catch (_: Exception) { false }
            var span = htmlEscape(text)
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

    // ── XLSX Parser ───────────────────────────────────────────────────────────

    private suspend fun parseXlsx(stream: java.io.InputStream, password: String? = null): OfficeReaderUiState {
        var finalStream = stream
        var poifsToClose: POIFSFileSystem? = null

        if (password != null) {
            val poifs = POIFSFileSystem(stream)
            poifsToClose = poifs
            val info = EncryptionInfo(poifs)
            val decryptor = Decryptor.getInstance(info)
            if (!decryptor.verifyPassword(password)) {
                poifs.close()
                return OfficeReaderUiState.Error("Incorrect password.")
            }
            finalStream = decryptor.getDataStream(poifs)
        }

        try {
            WorkbookFactory.create(finalStream).use { workbook ->
            val sheetNames = (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) }
            val sheetsRows = mutableListOf<List<XlsxRow>>()
            val sheetsColumnWidths = mutableListOf<List<Int>>()
            val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
            val evaluator = workbook.creationHelper.createFormulaEvaluator()

            for (sheetIdx in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIdx)
                val rows = mutableListOf<XlsxRow>()

                val mergedRegions = (0 until sheet.numMergedRegions).map { sheet.getMergedRegion(it) }
                val maxCol = (sheet.maxOfOrNull { it.lastCellNum.toInt() } ?: 0).coerceAtMost(200)
                val colMaxChars = IntArray(maxCol) { 0 }

                val maxRow = sheet.lastRowNum.coerceAtMost(1000)
                for (rowIdx in 0..maxRow) {
                    if (rowIdx % 50 == 0) kotlinx.coroutines.yield()
                    val row = sheet.getRow(rowIdx)
                    val cells = (0 until maxCol).map { colIdx ->
                        val cell = row?.getCell(colIdx)
                        val text = if (cell != null) dataFormatter.formatCellValue(cell, evaluator) else ""
                        val rawFormula = if (cell?.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                            try { cell.cellFormula } catch (e: Exception) { null }
                        } else null

                        var colSpan = 1; var rowSpan = 1; var isHidden = false
                        for (region in mergedRegions) {
                            if (region.isInRange(rowIdx, colIdx)) {
                                if (region.firstRow == rowIdx && region.firstColumn == colIdx) {
                                    colSpan = region.lastColumn - region.firstColumn + 1
                                    rowSpan = region.lastRow - region.firstRow + 1
                                } else { isHidden = true }
                                break
                            }
                        }
                        if (!isHidden) colMaxChars[colIdx] = maxOf(colMaxChars[colIdx], text.length)

                        var isBold = false; var bgColor: String? = null; var textColor: String? = null
                        val style = cell?.cellStyle
                        if (style != null) {
                            try {
                                val font = workbook.getFontAt(style.fontIndex)
                                isBold = font?.bold == true
                                if (font is XSSFFont) {
                                    font.xssfColor?.rgb?.let { rgb ->
                                        if (rgb.size >= 3) {
                                            val o = if (rgb.size == 4) 1 else 0
                                            textColor = "#%02X%02X%02X".format(rgb[o].toInt() and 0xFF, rgb[o+1].toInt() and 0xFF, rgb[o+2].toInt() and 0xFF)
                                        }
                                    }
                                }
                                style.fillForegroundColorColor?.let { color ->
                                    if (color is XSSFColor) {
                                        color.rgb?.let { rgb ->
                                            if (rgb.size >= 3) {
                                                val o = if (rgb.size == 4) 1 else 0
                                                bgColor = "#%02X%02X%02X".format(rgb[o].toInt() and 0xFF, rgb[o+1].toInt() and 0xFF, rgb[o+2].toInt() and 0xFF)
                                            }
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                        XlsxCell(text, rawFormula, isBold, bgColor, textColor, colSpan, rowSpan, isHidden)
                    }
                    rows.add(XlsxRow(cells))
                }
                sheetsRows.add(rows)
                sheetsColumnWidths.add(colMaxChars.map { (it * 8 + 32).coerceIn(80, 260) })
            }

            return OfficeReaderUiState.XlsxReady(
                sheetNames = sheetNames,
                sheetsRows = sheetsRows,
                sheetsColumnWidths = sheetsColumnWidths
            )
        }
        } finally {
            poifsToClose?.close()
        }
    }
}
