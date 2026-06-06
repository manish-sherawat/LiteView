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
        val showOutline: Boolean = false
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

    fun loadDocument(encodedUri: String, docType: String) {
        viewModelScope.launch {
            _uiState.value = OfficeReaderUiState.Loading()
            withContext(Dispatchers.IO) {
                try {
                    val uriStr = URLDecoder.decode(
                        URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.toString()),
                        StandardCharsets.UTF_8.toString()
                    )
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
                        "DOCX" -> parseDocx(inputStream)
                        "XLSX" -> parseXlsx(inputStream)
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

                    withContext(Dispatchers.Main) { _uiState.value = result }
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

    // ── DOCX → HTML Parser ────────────────────────────────────────────────────
    // Converts the Word document to a self-contained HTML string.
    // Images are embedded as base64 data URIs (no temp files needed).

    private suspend fun parseDocx(stream: java.io.InputStream): OfficeReaderUiState {
        XWPFDocument(stream).use { doc ->
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
  img { max-width: 100%; height: auto; border-radius: 8px; margin: 10px 0; display: block; }
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
  .page-break { border-top: 2px dashed rgba(128,128,128,0.3); margin: 20px 0; }
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
                        appendParagraphHtml(para, doc, sb, headings)
                    }
                    org.apache.poi.xwpf.usermodel.BodyElementType.TABLE -> {
                        val table = element as org.apache.poi.xwpf.usermodel.XWPFTable
                        appendTableHtml(table, sb)
                    }
                    else -> {}
                }
            }

            sb.append("\n</div></body></html>")

            return OfficeReaderUiState.DocxReady(
                htmlContent = sb.toString(),
                headings = headings
            )
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

        // Check if paragraph text is blank (skip empty paragraphs unless heading)
        val rawText = try { para.text ?: "" } catch (_: Exception) { "" }

        if (isHeading) {
            val anchorId = "h_${headings.size}"
            val headingText = rawText.trim()
            if (headingText.isNotEmpty()) {
                headings.add(DocxHeading(text = headingText, level = headingLevel, anchorId = anchorId))
            }
            sb.append("""<h$headingLevel id="$anchorId" style="text-align:$alignStr">""")
            appendRunsHtml(para, doc, sb)
            sb.append("</h$headingLevel>\n")
        } else if (isList) {
            val indent = (listLevel + 1) * 20
            val bullet = if (listLevel % 2 == 0) "&#8226;" else "&#9702;"
            sb.append("""<p style="text-align:$alignStr;margin-left:${indent}px">$bullet&nbsp;""")
            appendRunsHtml(para, doc, sb)
            sb.append("</p>\n")
        } else {
            if (rawText.isBlank()) {
                // Small vertical gap for blank lines
                sb.append("<p style=\"margin:4px 0\">&nbsp;</p>\n")
            } else {
                sb.append("""<p style="text-align:$alignStr">""")
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
                        sb.append("""<img src="data:$mime;base64,$b64" style="max-width:100%">""")
                    } catch (_: Exception) { /* skip bad image data */ }
                }

                val text = run.text() ?: ""
                if (text.isEmpty()) continue

                val isBold      = try { run.isBold } catch (_: Exception) { false }
                val isItalic    = try { run.isItalic } catch (_: Exception) { false }
                val isUnderline = try { run.underline != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE } catch (_: Exception) { false }
                val isStrike    = try { run.isStrikeThrough } catch (_: Exception) { false }
                val colorHex    = try { run.color?.let { if (it.length == 6) "#$it" else null } } catch (_: Exception) { null }
                val fontSize    = try { run.fontSizeAsDouble?.toInt()?.takeIf { it > 0 } } catch (_: Exception) { null }

                val isHyperlink = run is org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
                val url = if (isHyperlink) {
                    try {
                        val id = (run as org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun).hyperlinkId
                        doc.getHyperlinkByID(id)?.url
                    } catch (_: Exception) { null }
                } else null

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
                }

                var span = escaped
                if (isBold)      span = "<strong>$span</strong>"
                if (isItalic)    span = "<em>$span</em>"
                if (isUnderline) span = "<u>$span</u>"
                if (isStrike)    span = "<s>$span</s>"
                if (styles.isNotEmpty()) span = """<span style="$styles">$span</span>"""
                if (url != null) span = """<a href="${htmlEscape(url)}">$span</a>"""

                sb.append(span)
            } catch (_: Exception) { /* skip bad run */ }
        }
    }

    /** Converts a table element to an HTML <table>. */
    private fun appendTableHtml(
        table: org.apache.poi.xwpf.usermodel.XWPFTable,
        sb: StringBuilder
    ) {
        sb.append("<table>\n")
        table.rows.forEachIndexed { rowIdx, row ->
            sb.append("<tr>")
            for (cell in row.tableCells) {
                val tag = if (rowIdx == 0) "th" else "td"
                val tcPr = try { cell.ctTc?.tcPr } catch (_: Exception) { null }

                // Compute accurate colSpan from w:gridSpan
                val colSpan = try {
                    tcPr?.gridSpan?.`val`?.toInt()?.coerceAtLeast(1) ?: 1
                } catch (_: Exception) { 1 }

                // Skip cells that are vertical merge continuations
                val vMergeVal = try { tcPr?.vMerge?.`val` } catch (_: Exception) { null }
                val isVMergeContinue = vMergeVal == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE
                if (isVMergeContinue) continue

                val colSpanAttr = if (colSpan > 1) """ colspan="$colSpan"""" else ""
                sb.append("<$tag$colSpanAttr>")

                // Render cell content as paragraphs
                var firstPara = true
                for (para in cell.paragraphs) {
                    val cellText = try { para.text ?: "" } catch (_: Exception) { "" }
                    if (cellText.isBlank() && firstPara) { firstPara = false; continue }
                    if (!firstPara) sb.append("<br>")
                    // Inline runs for formatting
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

    private suspend fun parseXlsx(stream: java.io.InputStream): OfficeReaderUiState {
        WorkbookFactory.create(stream).use { workbook ->
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
    }
}
