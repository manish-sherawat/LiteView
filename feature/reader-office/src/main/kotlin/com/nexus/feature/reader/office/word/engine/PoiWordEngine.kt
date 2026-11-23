package com.nexus.feature.reader.office.word.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexus.feature.reader.office.word.model.DocxHeading
import com.nexus.feature.reader.office.word.parser.DocumentMargins
import com.nexus.feature.reader.office.word.parser.PageOrientation
import com.nexus.feature.reader.office.word.parser.PageSetup
import com.nexus.feature.reader.office.word.parser.PaperSize
import com.nexus.feature.reader.office.word.parser.WordCitationParser
import com.nexus.feature.reader.office.word.parser.WordDiagramParser
import com.nexus.feature.reader.office.word.parser.WordLayoutEngine
import com.nexus.feature.reader.office.word.parser.WordMathParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust, production-grade Apache POI + HTML/CSS layout engine for Word documents.
 * Produces clean, desktop-grade paginated HTML for the WebView reader.
 *
 * Key Capabilities:
 * 1. Strict horizontal boundary constraints: all paragraph shading, borders, and containers
 *    are constrained strictly within (page width - left margin - right margin).
 * 2. Indentation math: line wrapping width for bulleted and indented paragraphs subtracts left
 *    indent and hanging indent so justified text never clips on the right edge.
 * 3. Style inheritance: hyperlinks and nested runs inherit base paragraph font size and spacing
 *    without scaling up or applying unintended uppercase transforms; list items enforce proper
 *    spacing after.
 * 4. Granular error resilience: every XML node in the OOXML parser and pagination loop is wrapped
 *    in isolated try-catch blocks with node-level logging and fallback recovery.
 */
@Singleton
class PoiWordEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : WordEngine {

    companion object {
        private const val TAG = "PoiWordEngine"
    }

    override val engineName: String = "Apache POI Paginated Engine"

    override suspend fun loadDocument(
        uri: Uri,
        docType: String,
        password: String?
    ): WordEngineResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext WordEngineResult.Error("Cannot open file stream for $uri")

            stream.use { s ->
                val isDoc = docType.equals("DOC", ignoreCase = true) ||
                    (uri.lastPathSegment ?: "").endsWith(".doc", ignoreCase = true)

                if (isDoc) {
                    parseDoc(s)
                } else {
                    parseDocx(s, password)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "POI engine failure for $uri: ${t.message}", t)
            WordEngineResult.Error("POI engine error: ${t.localizedMessage ?: "Parsing failure"}", t)
        }
    }

    private fun parseDoc(stream: InputStream): WordEngineResult {
        return try {
            val hwpf = HWPFDocument(stream)
            val range = hwpf.range
            val numParas = range.numParagraphs()

            var totalWords = 0
            var totalChars = 0

            val pageSetup = PageSetup(
                paperSize = PaperSize.A4,
                widthPt = PaperSize.A4.widthPt,
                heightPt = PaperSize.A4.heightPt,
                orientation = PageOrientation.PORTRAIT,
                margins = DocumentMargins()
            )

            val stylesheet = WordLayoutEngine.generateStylesheet(pageSetup)
            val clientScript = WordLayoutEngine.generateClientScript()

            val pages = mutableListOf<String>()
            val currentPage = StringBuilder(4096)
            var currentHeight = 0f
            val printableHeight = pageSetup.printableHeightPt
            val printableWidth = pageSetup.printableWidthPt

            fun sealPage() {
                if (currentPage.isNotEmpty()) {
                    pages.add(currentPage.toString())
                    currentPage.clear()
                    currentHeight = 0f
                }
            }

            val consecutiveDiagramLines = mutableListOf<String>()
            fun flushDiagramLines() {
                if (consecutiveDiagramLines.isNotEmpty()) {
                    val blockHtml = WordDiagramParser.renderDiagramBlock(emptyList())
                        .replace("<code></code>", "<code>" + consecutiveDiagramLines.joinToString("\n") { htmlEscape(it) } + "</code>")
                    currentPage.append(blockHtml)
                    currentHeight += (consecutiveDiagramLines.size * 18f) + 36f
                    consecutiveDiagramLines.clear()
                }
            }

            for (i in 0 until numParas) {
                try {
                    val para = range.getParagraph(i)
                    val rawText = para.text() ?: ""
                    val isDiagram = rawText.contains("┌") || rawText.contains("└") ||
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
                    paraSb.append("<p style=\"text-align:$alignStr;min-width:0;max-width:100%;box-sizing:border-box;margin-bottom:6pt;word-break:normal;overflow-wrap:break-word;white-space:normal;\">")
                    for (j in 0 until para.numCharacterRuns()) {
                        try {
                            val run = para.getCharacterRun(j)
                            var text = run.text()
                            if (text.isBlank() && text.isEmpty()) continue
                            text = WordMathParser.decodeMathSymbols(text)
                            var span = htmlEscape(text)
                            if (run.isBold) span = "<strong>$span</strong>"
                            if (run.isItalic) span = "<em>$span</em>"
                            if (run.isStrikeThrough) span = "<s>$span</s>"
                            paraSb.append(span)
                        } catch (_: Exception) {}
                    }
                    paraSb.append("</p>\n")

                    val citationFormatted = WordCitationParser.formatCitationTags(paraSb.toString())
                    currentPage.append(citationFormatted)
                    currentHeight += paraHeight
                } catch (t: Throwable) {
                    Log.w(TAG, "Error parsing .doc paragraph at index $i: ${t.message}", t)
                }
            }
            flushDiagramLines()
            sealPage()

            if (pages.isEmpty()) {
                pages.add("<p style=\"color: #64748b; font-style: italic;\">Document is empty</p>")
            }

            val totalPages = pages.size
            val sb = StringBuilder(1024 * 64)
            sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">\n")
            sb.append(stylesheet)
            sb.append("\n</head><body class=\"mode-paged\"><div class=\"docx-paged-container\">\n")

            for ((i, pageContent) in pages.withIndex()) {
                val pageNum = i + 1
                sb.append("<section class=\"docx-page\" data-page-number=\"$pageNum\">\n")
                sb.append("  <div class=\"docx-page-header\"><span class=\"docx-header-title\">Document</span><span class=\"docx-header-num\">$pageNum</span></div>\n")
                sb.append("  <div class=\"docx-page-content\">\n$pageContent\n  </div>\n")
                sb.append("  <div class=\"docx-page-footer\"><span class=\"docx-footer-title\"></span><span class=\"docx-footer-num\">Page $pageNum of $totalPages</span></div>\n")
                sb.append("</section>\n")
            }

            sb.append("</div>\n")
            sb.append(clientScript)
            sb.append("\n</body></html>")

            WordEngineResult.Html(
                htmlContent = sb.toString(),
                headings = emptyList(),
                pageCount = totalPages,
                wordCount = totalWords,
                characterCount = totalChars,
                readingTimeMinutes = Math.max(1, Math.ceil(totalWords / 200.0).toInt()),
                author = null,
                creationDate = null,
                paperSizeName = pageSetup.paperSize.name,
                paperDimensions = "${pageSetup.paperSize.widthMm.toInt()} × ${pageSetup.paperSize.heightMm.toInt()} mm",
                orientation = if (pageSetup.orientation == PageOrientation.LANDSCAPE) "Landscape" else "Portrait",
                marginsDescription = pageSetup.margins.summary()
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error in parseDoc: ${t.message}", t)
            WordEngineResult.Error("Failed to parse Word (.doc) document: ${t.localizedMessage ?: "Corrupt or encrypted file"}", t)
        }
    }

    private fun parseDocx(stream: InputStream, password: String?): WordEngineResult {
        var poifsToClose: POIFSFileSystem? = null
        var finalStream: InputStream = stream

        val pushback = java.io.PushbackInputStream(stream, 8)
        val header = ByteArray(8)
        val n = pushback.read(header)
        if (n > 0) pushback.unread(header, 0, n)

        val isOleo = n >= 8 &&
            header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte() &&
            header[4] == 0xA1.toByte() && header[5] == 0xB1.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0xE1.toByte()

        if (isOleo) {
            val poifs = POIFSFileSystem(pushback)
            poifsToClose = poifs
            val info = EncryptionInfo(poifs)
            val decryptor = Decryptor.getInstance(info)

            val pwd = password ?: "VelvetSweatshop"
            val verified = try {
                decryptor.verifyPassword(pwd)
            } catch (_: Exception) {
                false
            }

            if (!verified) {
                if (password == null) {
                    return WordEngineResult.PasswordRequired(isError = false)
                }
                return WordEngineResult.PasswordRequired(isError = true)
            }
            finalStream = decryptor.getDataStream(poifs)
        } else {
            finalStream = pushback
        }

        return try {
            XWPFDocument(finalStream).use { doc ->
                val bodyElements = doc.bodyElements
                val headings = mutableListOf<DocxHeading>()

                val author = try { doc.properties.coreProperties.creator } catch (_: Exception) { null }
                val creationDate = try { doc.properties.coreProperties.created?.toString() } catch (_: Exception) { null }
                var totalWords = 0
                var totalChars = 0

                val pageSetup = WordLayoutEngine.extractPageSetup(doc)
                val stylesheet = WordLayoutEngine.generateStylesheet(pageSetup)
                val clientScript = WordLayoutEngine.generateClientScript()

                // Extract all native document footnotes
                val footnotesMap = mutableMapOf<String, String>()
                try {
                    for (fn in doc.footnotes) {
                        val idStr = fn.id?.toString() ?: continue
                        val fnType = (try { fn.ctFtnEdn?.type?.toString()?.lowercase() } catch (_: Exception) { null }) ?: ""
                        if (fnType.contains("separator")) continue
                        val fnTextSb = StringBuilder()
                        for (p in fn.paragraphs) {
                            val pTxt = p.text ?: ""
                            if (pTxt.isNotBlank()) {
                                if (fnTextSb.isNotEmpty()) fnTextSb.append(" ")
                                appendRunsHtmlSimple(p, fnTextSb)
                            }
                        }
                        if (fnTextSb.isNotBlank()) {
                            footnotesMap[idStr] = fnTextSb.toString()
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error extracting footnotes: ${t.message}", t)
                }

                val pages = mutableListOf<String>()
                val currentPage = StringBuilder(4096)
                var currentHeight = 0f
                val printableHeight = pageSetup.printableHeightPt
                val printableWidth = pageSetup.printableWidthPt

                fun sealPage() {
                    if (currentPage.isNotEmpty()) {
                        pages.add(currentPage.toString())
                        currentPage.clear()
                        currentHeight = 0f
                    }
                }

                val consecutiveDiagrams = mutableListOf<XWPFParagraph>()
                fun flushDiagramParagraphs() {
                    if (consecutiveDiagrams.isNotEmpty()) {
                        try {
                            val blockHtml = WordDiagramParser.renderDiagramBlock(consecutiveDiagrams)
                            currentPage.append(blockHtml)
                            currentHeight += (consecutiveDiagrams.size * 18f) + 36f
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error rendering diagram block: ${t.message}", t)
                        } finally {
                            consecutiveDiagrams.clear()
                        }
                    }
                }

                // Granular per-XML-node iteration with safe fallback
                for ((elemIdx, element) in bodyElements.withIndex()) {
                    val elemTag = try {
                        when (element.elementType) {
                            BodyElementType.PARAGRAPH -> (element as? XWPFParagraph)?.ctp?.domNode?.nodeName ?: "w:p"
                            BodyElementType.TABLE -> (element as? XWPFTable)?.ctTbl?.domNode?.nodeName ?: "w:tbl"
                            else -> element.elementType?.name ?: "w:bodyElement"
                        }
                    } catch (_: Exception) { "w:node" }

                    try {
                        when (element.elementType) {
                            BodyElementType.PARAGRAPH -> {
                                val para = element as XWPFParagraph
                                try {
                                    if (WordDiagramParser.isDiagramParagraph(para)) {
                                        consecutiveDiagrams.add(para)
                                        continue
                                    } else {
                                        flushDiagramParagraphs()
                                    }

                                    val txt = para.text ?: ""
                                    totalWords += txt.split(Regex("\\s+")).count { it.isNotBlank() }
                                    totalChars += txt.length

                                    val isBreak = WordLayoutEngine.hasExplicitPageBreak(para)
                                    val estimatedHeight = WordLayoutEngine.estimateParagraphHeight(para, printableWidth)

                                    if (isBreak && currentPage.isNotEmpty()) {
                                        sealPage()
                                    } else if (currentHeight + estimatedHeight > printableHeight && currentPage.isNotEmpty()) {
                                        sealPage()
                                    }

                                    appendParagraphHtml(para, doc, currentPage, headings, footnotesMap)
                                    currentHeight += estimatedHeight
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Error parsing paragraph node <$elemTag> at index $elemIdx: ${t.message}", t)
                                    // Safe fallback: extract raw text in bounded paragraph
                                    try {
                                        val raw = para.text ?: ""
                                        if (raw.isNotBlank()) {
                                            currentPage.append("<p style=\"margin-bottom:6pt;max-width:100%;box-sizing:border-box;word-break:normal;overflow-wrap:break-word;\">${htmlEscape(raw)}</p>\n")
                                            currentHeight += 18f
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                            BodyElementType.TABLE -> {
                                flushDiagramParagraphs()
                                val table = element as XWPFTable
                                try {
                                    for (row in table.rows) {
                                        for (cell in row.tableCells) {
                                            val txt = cell.text ?: ""
                                            totalWords += txt.split(Regex("\\s+")).count { it.isNotBlank() }
                                            totalChars += txt.length
                                        }
                                    }

                                    val rowSpans = computeRowSpans(table)
                                    val tableHeaderHeight = if (table.rows.isNotEmpty()) WordLayoutEngine.estimateTableRowHeight(table.rows[0], printableWidth) else 24f

                                    val tblAlignment = try {
                                        table.tableAlignment?.toString()?.lowercase() ?:
                                        table.ctTbl?.tblPr?.jc?.`val`?.toString()?.lowercase() ?: ""
                                    } catch (_: Exception) { "" }
                                    val tblAlignStyle = when {
                                        tblAlignment.contains("center") -> "margin:14px auto;"
                                        tblAlignment.contains("right")  -> "margin:14px 0 14px auto;"
                                        else                            -> "margin:14px 0;"
                                    }

                                    var inTablePage = false
                                    fun startTableChunk() {
                                        currentPage.append("<div class=\"table-container\" style=\"max-width:100%;box-sizing:border-box;overflow-x:auto;\"><table class=\"docx-table\" style=\"min-width:100%;box-sizing:border-box;$tblAlignStyle\">\n")
                                        inTablePage = true
                                    }
                                    fun endTableChunk() {
                                        if (inTablePage) {
                                            currentPage.append("</table></div>\n")
                                            inTablePage = false
                                        }
                                    }

                                    startTableChunk()
                                    table.rows.forEachIndexed { rowIdx, row ->
                                        val rowHeight = WordLayoutEngine.estimateTableRowHeight(row, printableWidth)
                                        if (currentHeight + rowHeight > printableHeight && currentHeight > 0f) {
                                            endTableChunk()
                                            sealPage()
                                            startTableChunk()
                                            if (rowIdx > 0 && table.rows.isNotEmpty()) {
                                                appendTableRowHtml(table.rows[0], 0, rowSpans, currentPage, doc, footnotesMap)
                                                currentHeight += tableHeaderHeight
                                            }
                                        }
                                        appendTableRowHtml(row, rowIdx, rowSpans, currentPage, doc, footnotesMap)
                                        currentHeight += rowHeight
                                    }
                                    endTableChunk()
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Error parsing table node <$elemTag> at index $elemIdx: ${t.message}", t)
                                    // Safe fallback: render simple table structure
                                    try {
                                        renderFallbackTable(table, currentPage)
                                        currentHeight += 40f
                                    } catch (_: Exception) {}
                                }
                            }
                            else -> {
                                Log.d(TAG, "Skipping unrecognized XML body node <$elemTag> at index $elemIdx")
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Unhandled error in body node <$elemTag> at index $elemIdx: ${t.message}", t)
                    }
                }
                flushDiagramParagraphs()
                sealPage()

                // Append footnotes section if footnotes or references exist
                val footnotesHtml = WordCitationParser.renderFootnotesSection(footnotesMap)
                if (footnotesHtml.isNotBlank()) {
                    if (pages.isNotEmpty()) {
                        val lastIdx = pages.size - 1
                        pages[lastIdx] = pages[lastIdx] + "\n" + footnotesHtml
                    } else {
                        pages.add(footnotesHtml)
                    }
                }

                if (pages.isEmpty()) {
                    pages.add("<p style=\"color: #64748b; font-style: italic;\">Document is empty</p>")
                }

                // Extract custom headers/footers safely
                val headerTitle = try {
                    doc.headerList?.firstOrNull { it.text.isNotBlank() }?.text?.trim()?.take(50) ?: "Document"
                } catch (t: Throwable) {
                    Log.w(TAG, "Error extracting custom header: ${t.message}", t)
                    "Document"
                }
                val footerTitle = try {
                    doc.footerList?.firstOrNull { it.text.isNotBlank() }?.text?.trim()?.take(50) ?: ""
                } catch (t: Throwable) {
                    Log.w(TAG, "Error extracting custom footer: ${t.message}", t)
                    ""
                }

                val totalPages = pages.size
                val sb = StringBuilder(1024 * 64)
                sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes\">\n")
                sb.append(stylesheet)
                sb.append("\n</head><body class=\"mode-paged\"><div class=\"docx-paged-container\">\n")

                for ((i, pageContent) in pages.withIndex()) {
                    val pageNum = i + 1
                    sb.append("<section class=\"docx-page\" data-page-number=\"$pageNum\">\n")
                    sb.append("  <div class=\"docx-page-header\"><span class=\"docx-header-title\">${htmlEscape(headerTitle)}</span><span class=\"docx-header-num\">$pageNum</span></div>\n")
                    sb.append("  <div class=\"docx-page-content\">\n$pageContent\n  </div>\n")
                    sb.append("  <div class=\"docx-page-footer\"><span class=\"docx-footer-title\">${htmlEscape(footerTitle)}</span><span class=\"docx-footer-num\">Page $pageNum of $totalPages</span></div>\n")
                    sb.append("</section>\n")
                }

                sb.append("</div>\n")
                sb.append(clientScript)
                sb.append("\n</body></html>")

                WordEngineResult.Html(
                    htmlContent = sb.toString(),
                    headings = headings,
                    pageCount = totalPages,
                    wordCount = totalWords,
                    characterCount = totalChars,
                    readingTimeMinutes = Math.max(1, Math.ceil(totalWords / 200.0).toInt()),
                    author = author,
                    creationDate = creationDate,
                    paperSizeName = pageSetup.paperSize.name,
                    paperDimensions = "${pageSetup.paperSize.widthMm.toInt()} × ${pageSetup.paperSize.heightMm.toInt()} mm",
                    orientation = if (pageSetup.orientation == PageOrientation.LANDSCAPE) "Landscape" else "Portrait",
                    marginsDescription = pageSetup.margins.summary()
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "DOCX OOXML stream parser error: ${t.message}", t)
            // Attempt emergency text extractor recovery so the file never fails to open
            attemptEmergencyRecovery(finalStream, t)
        } finally {
            try { poifsToClose?.close() } catch (_: Exception) {}
        }
    }

    private fun attemptEmergencyRecovery(stream: InputStream, originalError: Throwable): WordEngineResult {
        return try {
            val text = stream.use { s ->
                val pushback = java.io.PushbackInputStream(s, 4)
                val h = ByteArray(4)
                pushback.read(h)
                pushback.unread(h)
                try {
                    val doc = XWPFDocument(pushback)
                    val sb = StringBuilder()
                    for (p in doc.paragraphs) {
                        val t = p.text
                        if (!t.isNullOrBlank()) sb.append(t).append("\n")
                    }
                    for (tbl in doc.tables) {
                        for (row in tbl.rows) {
                            for (cell in row.tableCells) {
                                val t = cell.text
                                if (!t.isNullOrBlank()) sb.append(t).append("\t")
                            }
                            sb.append("\n")
                        }
                    }
                    doc.close()
                    sb.toString()
                } catch (_: Exception) {
                    null
                }
            }

            if (!text.isNullOrBlank()) {
                val lines = text.lines()
                val paragraphsHtml = lines.filter { it.isNotBlank() }
                    .joinToString("\n") { "<p style=\"margin-bottom:6pt;max-width:100%;box-sizing:border-box;\">${htmlEscape(it)}</p>" }

                val pageSetup = PageSetup(
                    paperSize = PaperSize.A4,
                    widthPt = PaperSize.A4.widthPt,
                    heightPt = PaperSize.A4.heightPt,
                    orientation = PageOrientation.PORTRAIT,
                    margins = DocumentMargins()
                )
                val stylesheet = WordLayoutEngine.generateStylesheet(pageSetup)
                val clientScript = WordLayoutEngine.generateClientScript()

                val html = """
                    <!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                    $stylesheet
                    </head><body class="mode-paged"><div class="docx-paged-container">
                    <section class="docx-page" data-page-number="1">
                      <div class="docx-page-header"><span class="docx-header-title">Recovered Document</span><span class="docx-header-num">1</span></div>
                      <div class="docx-page-content">
                        <div style="padding:8px 12px;margin-bottom:14px;background:#fef3c7;border-left:4px solid #f59e0b;border-radius:4px;font-size:12px;color:#92400e;">
                          Notice: Document layout recovered in safe mode due to complex or non-standard formatting.
                        </div>
                        $paragraphsHtml
                      </div>
                      <div class="docx-page-footer"><span class="docx-footer-title"></span><span class="docx-footer-num">Page 1 of 1</span></div>
                    </section>
                    </div>$clientScript</body></html>
                """.trimIndent()

                WordEngineResult.Html(
                    htmlContent = html,
                    headings = emptyList(),
                    pageCount = 1,
                    wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() },
                    characterCount = text.length,
                    readingTimeMinutes = 1,
                    author = null,
                    creationDate = null,
                    paperSizeName = "A4",
                    paperDimensions = "210 × 297 mm",
                    orientation = "Portrait",
                    marginsDescription = "Standard Margins"
                )
            } else {
                WordEngineResult.Error("Failed to parse Word document: ${originalError.localizedMessage ?: "Unknown format"}", originalError)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency recovery failed: ${e.message}", e)
            WordEngineResult.Error("Failed to parse Word document: ${originalError.localizedMessage ?: "Unrecognized or corrupted document"}", originalError)
        }
    }

    private fun appendParagraphHtml(
        para: XWPFParagraph,
        doc: XWPFDocument,
        sb: StringBuilder,
        headings: MutableList<DocxHeading>,
        footnotesMap: Map<String, String> = emptyMap()
    ) {
        val styleId = try { para.style ?: "" } catch (_: Exception) { "" }
        val cleanStyleId = styleId.lowercase()
        val isTOC = cleanStyleId.contains("toc")
        val isHeading = !isTOC && cleanStyleId.startsWith("heading")
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

        // Enforce proper paragraph spacing (spacing after) on list items
        val effectiveSpacingAfter = if (isList) {
            if (spacingAfter != null) spacingAfter.coerceAtLeast(4.0) else 4.0
        } else {
            spacingAfter
        }

        val rawIndentLeft = try { para.indentationLeft } catch (_: Exception) { -1 }
        val rawIndentRight = try { para.indentationRight } catch (_: Exception) { -1 }
        val rawIndentFirstLine = try { para.indentationFirstLine } catch (_: Exception) { -1 }
        val rawIndentHanging = try { para.indentationHanging } catch (_: Exception) { -1 }

        val indentLeftPt = if (rawIndentLeft > 0) (rawIndentLeft / 20.0).coerceIn(0.0, 180.0) else null
        val indentRightPt = if (rawIndentRight > 0) (rawIndentRight / 20.0).coerceIn(0.0, 120.0) else null
        val indentFirstLinePt = if (rawIndentFirstLine > 0) (rawIndentFirstLine / 20.0).coerceIn(0.0, 100.0) else null
        val indentHangingPt = if (rawIndentHanging > 0) (rawIndentHanging / 20.0).coerceIn(0.0, 100.0) else null

        // Paragraph shading & border constraints within page boundaries
        val pPr = try { para.ctp.pPr } catch (_: Exception) { null }
        val isBidi = try {
            val dom = pPr?.domNode
            if (dom != null) {
                val nl = dom.childNodes
                (0 until nl.length).any { i ->
                    val item = nl.item(i)
                    val name = item.localName ?: item.nodeName
                    name.equals("bidi", ignoreCase = true)
                }
            } else false
        } catch (_: Exception) { false }

        val dirStyle = if (isBidi) "direction:rtl;" else "direction:ltr;"

        // Vertical text direction handling
        val textDirection = try {
            val dom = pPr?.domNode
            var dir: String? = null
            if (dom != null) {
                val nl = dom.childNodes
                for (i in 0 until nl.length) {
                    val item = nl.item(i) ?: continue
                    val name = item.localName ?: item.nodeName
                    if (name.equals("textDirection", ignoreCase = true)) {
                        dir = item.attributes?.getNamedItem("w:val")?.nodeValue
                            ?: item.attributes?.getNamedItem("val")?.nodeValue
                        break
                    }
                }
            }
            dir
        } catch (_: Exception) { null }

        val writingModeStyle = when (textDirection?.lowercase()) {
            "tbrl", "tbrlv" -> "writing-mode:vertical-rl;"
            "btlr" -> "writing-mode:vertical-lr;"
            else -> "writing-mode:horizontal-tb;"
        }

        val shdFill = try {
            val fill = pPr?.shd?.fill?.toString()
            if (!fill.isNullOrBlank() && fill != "auto" && fill != "none" && fill != "clear") {
                if (fill.length == 6) "#$fill" else null
            } else null
        } catch (_: Exception) { null }

        val hasBdr = try {
            val bdr = pPr?.pBdr
            bdr != null && (bdr.isSetLeft || bdr.isSetBottom || bdr.isSetTop || bdr.isSetRight)
        } catch (_: Exception) { false }

        val bookmarks = try { para.ctp.bookmarkStartList.map { it.name } } catch (_: Exception) { emptyList() }

        val pStyles = buildString {
            append("text-align:$alignStr;")
            append("$dirStyle;$writingModeStyle")
            val safeLineHeight = spacingBetween ?: 1.35
            append("line-height:$safeLineHeight;")
            // Crucial: Use max-width: 100% and box-sizing: border-box WITHOUT width: 100% to prevent horizontal overflow
            append("min-height:1.2em;min-width:0;max-width:100%;box-sizing:border-box;word-break:normal;overflow-wrap:break-word;white-space:normal;")

            if (spacingBefore != null) append("margin-top:${spacingBefore}pt;")
            if (effectiveSpacingAfter != null) append("margin-bottom:${effectiveSpacingAfter}pt;")

            if (!isList) {
                if (indentLeftPt != null) append("margin-left:${indentLeftPt}pt;")
                if (indentFirstLinePt != null) append("text-indent:${indentFirstLinePt}pt;")
                else if (indentHangingPt != null) append("text-indent:-${indentHangingPt}pt;padding-left:${indentHangingPt}pt;")
            }
            if (indentRightPt != null) append("margin-right:${indentRightPt}pt;")

            if (shdFill != null) {
                append("background-color:$shdFill;padding:6px 10px;border-radius:4px;")
            }
            if (hasBdr) {
                append("border-left:3px solid var(--border-color);padding-left:8px;")
            }
        }

        val rawText = try { para.text ?: "" } catch (_: Exception) { "" }
        val cleanHeadingText = rawText.replace(Regex("[\\u00A0\\u200B\\uFEFF]"), " ").replace(Regex("\\s+"), " ").trim()
        val isValidHeading = isHeading && cleanHeadingText.isNotBlank() && cleanHeadingText.length >= 2 && !cleanHeadingText.all { it.isDigit() || it in ".-_•* \t" }
        val hasPictures = para.runs.any { it.embeddedPictures.isNotEmpty() }
        val isStandalonePicture = hasPictures && rawText.isBlank() && !hasMathNodes(para)
        val anchorsHtml = bookmarks.joinToString("") { "<a id=\"$it\"></a>" }
        val finalIdAttr = if (isValidHeading) "id=\"h_${headings.size}\"" else ""

        val tempSb = StringBuilder()
        if (isStandalonePicture) {
            tempSb.append("""<div class="docx-figure-container" $finalIdAttr>$anchorsHtml""")
            appendParagraphBodyContent(para, doc, tempSb, footnotesMap)
            tempSb.append("</div>\n")
        } else if (isValidHeading) {
            headings.add(DocxHeading(text = cleanHeadingText, level = headingLevel, anchorId = "h_${headings.size}"))
            tempSb.append("""<h$headingLevel $finalIdAttr style="$pStyles;min-width:0;max-width:100%;box-sizing:border-box;word-break:normal;overflow-wrap:break-word;white-space:normal;">$anchorsHtml""")
            appendParagraphBodyContent(para, doc, tempSb, footnotesMap)
            tempSb.append("</h$headingLevel>\n")
        } else if (isList) {
            val baseIndentPt = ((listLevel + 1) * 16.0).coerceIn(16.0, 140.0)
            val totalLeftIndentPt = (indentLeftPt ?: baseIndentPt).coerceAtLeast(baseIndentPt)
            val bullet = if (listLevel % 2 == 0) "&#8226;" else "&#9702;"
            val bulletWidthPt = 14.0

            // Subtract left indent and hanging indent from available width using padding-left with negative text-indent
            val listStyles = buildString {
                append(pStyles)
                append("padding-left:${totalLeftIndentPt}pt;")
                append("text-indent:-${bulletWidthPt}pt;")
                append("margin-left:0;")
                append("max-width:100%;box-sizing:border-box;")
            }
            tempSb.append("""<p class="docx-list-item" $finalIdAttr style="$listStyles">$anchorsHtml<span style="display:inline-block;width:${bulletWidthPt}pt;text-indent:0;">$bullet</span>""")
            appendParagraphBodyContent(para, doc, tempSb, footnotesMap)
            tempSb.append("</p>\n")
        } else {
            if (rawText.isBlank() && !hasMathNodes(para) && !hasPictures) {
                tempSb.append("<p $finalIdAttr style=\"margin:4px 0;min-height:1em;max-width:100%;box-sizing:border-box;\">$anchorsHtml&nbsp;</p>\n")
            } else {
                tempSb.append("""<p class="docx-paragraph" $finalIdAttr style="$pStyles">$anchorsHtml""")
                appendParagraphBodyContent(para, doc, tempSb, footnotesMap)
                tempSb.append("</p>\n")
            }
        }

        val citationFormatted = WordCitationParser.formatCitationTags(tempSb.toString(), footnotesMap.keys)
        sb.append(citationFormatted)
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
        sb: StringBuilder,
        footnotesMap: Map<String, String> = emptyMap()
    ) {
        val domNode = try { para.ctp.domNode } catch (_: Exception) { null }
        if (domNode != null) {
            val childNodes = domNode.childNodes
            var hasHandledMath = false
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i) ?: continue
                if (WordMathParser.isMathNode(node)) {
                    try {
                        WordMathParser.parseOMML(node, sb)
                        hasHandledMath = true
                    } catch (t: Throwable) {
                        val nodeName = node.nodeName ?: "m:oMath"
                        Log.w(TAG, "Error parsing OMML math node <$nodeName>: ${t.message}", t)
                    }
                }
            }
            if (hasHandledMath) return
        }
        appendRunsHtml(para, doc, sb, footnotesMap)
    }

    private fun appendRunsHtml(
        para: XWPFParagraph,
        doc: XWPFDocument,
        sb: StringBuilder,
        footnotesMap: Map<String, String> = emptyMap()
    ) {
        for (run in para.runs) {
            val runNodeName = try { run.ctr?.domNode?.nodeName ?: "w:r" } catch (_: Exception) { "w:r" }
            try {
                // Embedded pictures
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
                        sb.append("""<span class="docx-image-frame"><img class="docx-inline-image" src="data:$mime;base64,$b64" alt="Image" style="max-width:100%;height:auto;object-fit:contain;vertical-align:middle;border-radius:4px;overflow:hidden;"></span>""")
                    } catch (t: Throwable) {
                        Log.w(TAG, "Error decoding embedded picture: ${t.message}", t)
                    }
                }

                val text = run.text() ?: ""
                if (text.isEmpty()) continue

                val isHyperlink = run is XWPFHyperlinkRun ||
                    try { run.ctr?.domNode?.parentNode?.nodeName?.contains("hyperlink", ignoreCase = true) == true } catch (_: Exception) { false }

                val isBold = try { run.isBold } catch (_: Exception) { false }
                val isItalic = try { run.isItalic } catch (_: Exception) { false }
                val underlinePattern = try { run.underline } catch (_: Exception) { null }
                val isUnderline = isHyperlink || (underlinePattern != null && underlinePattern != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE)
                val isStrike = try { run.isStrikeThrough } catch (_: Exception) { false }
                val isDoubleStrike = try { run.isDoubleStrikeThrough } catch (_: Exception) { false }
                val colorHex = try { run.color?.let { if (it.length == 6 && it != "auto") "#$it" else null } } catch (_: Exception) { null }

                // Style inheritance: Hyperlinks and nested runs inherit base paragraph font size and spacing
                val fontSize = if (!isHyperlink) {
                    try { run.fontSizeAsDouble?.toInt()?.takeIf { it > 0 } } catch (_: Exception) { null }
                } else {
                    // Hyperlinks inherit base paragraph font size and line spacing to avoid scaling up
                    null
                }

                val fontFamily = try { run.fontFamily } catch (_: Exception) { null }
                val vAlign = try { run.verticalAlignment?.toString()?.lowercase() } catch (_: Exception) { null }
                val isSubscript = vAlign == "subscript"
                val isSuperscript = vAlign == "superscript"

                // Unintended casing transforms: ONLY apply if explicitly specified on run's own rPr DOM and never on hyperlinks
                val isExplicitCaps = if (!isHyperlink) {
                    try {
                        val dom = run.ctr?.rPr?.domNode
                        if (dom != null) {
                            val nl = dom.childNodes
                            (0 until nl.length).any { i ->
                                val item = nl.item(i)
                                (item.localName ?: item.nodeName).equals("caps", ignoreCase = true)
                            }
                        } else false
                    } catch (_: Exception) { false }
                } else false

                val isExplicitSmallCaps = if (!isHyperlink) {
                    try {
                        val dom = run.ctr?.rPr?.domNode
                        if (dom != null) {
                            val nl = dom.childNodes
                            (0 until nl.length).any { i ->
                                val item = nl.item(i)
                                (item.localName ?: item.nodeName).equals("smallcaps", ignoreCase = true)
                            }
                        } else false
                    } catch (_: Exception) { false }
                } else false

                val highlightColor = try { run.textHighlightColor?.toString()?.takeIf { it != "none" && it.isNotBlank() } } catch (_: Exception) { null }

                val url = if (isHyperlink) {
                    try {
                        val hr = run as? XWPFHyperlinkRun
                        if (hr != null && !hr.anchor.isNullOrEmpty()) {
                            "#" + hr.anchor
                        } else if (hr != null) {
                            doc.getHyperlinkByID(hr.hyperlinkId)?.url
                        } else {
                            val parent = run.ctr?.domNode?.parentNode
                            val rId = parent?.attributes?.getNamedItem("r:id")?.nodeValue
                                ?: parent?.attributes?.getNamedItem("id")?.nodeValue
                            if (rId != null) doc.getHyperlinkByID(rId)?.url else null
                        }
                    } catch (_: Exception) { null }
                } else null

                val footnoteIds = try { run.ctr?.footnoteReferenceList?.map { it.id?.toString() ?: "" }?.filter { it.isNotBlank() } ?: emptyList() } catch (_: Exception) { emptyList() }
                val endnoteIds = try { run.ctr?.endnoteReferenceList?.map { it.id?.toString() ?: "" }?.filter { it.isNotBlank() } ?: emptyList() } catch (_: Exception) { emptyList() }

                val decodedText = WordMathParser.decodeMathSymbols(text)
                val escaped = htmlEscape(decodedText).replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")

                val styles = buildString {
                    append("writing-mode:horizontal-tb;display:inline;")
                    if (colorHex != null && !isHyperlink) append("color:$colorHex;")
                    if (fontSize != null) append("font-size:${fontSize}pt;")
                    if (fontFamily != null) append("font-family:'$fontFamily';")
                    if (highlightColor != null) append("background-color:$highlightColor;")
                    if (isExplicitCaps) append("text-transform:uppercase;")
                    if (isExplicitSmallCaps) append("font-variant:small-caps;")
                }

                var span = escaped
                if (isBold) span = "<strong>$span</strong>"
                if (isItalic) span = "<em>$span</em>"
                if (isUnderline) span = "<u>$span</u>"
                if (isStrike || isDoubleStrike) span = "<s>$span</s>"
                if (isSubscript) span = "<sub>$span</sub>"
                if (isSuperscript) span = "<sup>$span</sup>"
                if (styles.isNotEmpty()) span = """<span class="docx-run" style="$styles">$span</span>"""
                if (isHyperlink || url != null) {
                    val href = url ?: "#"
                    span = """<a class="docx-hyperlink" href="${htmlEscape(href)}">$span</a>"""
                }

                for (fnId in footnoteIds) {
                    val preview = footnotesMap[fnId]?.let { " title=\"${htmlEscape(it)}\"" } ?: ""
                    span += "<sup class=\"footnote-ref\"><a href=\"#footnote_$fnId\"$preview>[$fnId]</a></sup>"
                }
                for (enId in endnoteIds) {
                    span += "<sup class=\"endnote-ref\"><a href=\"#endnote_$enId\">[$enId]</a></sup>"
                }

                sb.append(span)
            } catch (t: Throwable) {
                Log.w(TAG, "Error parsing run node <$runNodeName>: ${t.message}", t)
                try {
                    val txt = run.text() ?: ""
                    if (txt.isNotEmpty()) sb.append(htmlEscape(txt))
                } catch (_: Exception) {}
            }
        }
    }

    private fun computeRowSpans(table: XWPFTable): Map<Pair<Int, Int>, Int> {
        val rowSpans = mutableMapOf<Pair<Int, Int>, Int>()
        try {
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
        } catch (t: Throwable) {
            Log.w(TAG, "Error computing table row spans: ${t.message}", t)
        }
        return rowSpans
    }

    private fun extractCellBordersCss(cell: XWPFTableCell): String {
        val tcPr = try { cell.ctTc?.tcPr } catch (_: Exception) { null } ?: return "border:1px solid var(--border-color);"
        val dom = try { tcPr.domNode } catch (_: Exception) { null } ?: return "border:1px solid var(--border-color);"
        val childNodes = dom.childNodes ?: return "border:1px solid var(--border-color);"

        var tcBordersNode: org.w3c.dom.Node? = null
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i) ?: continue
            val name = node.localName ?: node.nodeName
            if (name.equals("tcBorders", ignoreCase = true)) {
                tcBordersNode = node
                break
            }
        }

        if (tcBordersNode == null) {
            return "border:1px solid var(--border-color);"
        }

        val bordersList = tcBordersNode.childNodes ?: return "border:1px solid var(--border-color);"
        val borderSides = mutableMapOf<String, String>()

        for (i in 0 until bordersList.length) {
            val bNode = bordersList.item(i) ?: continue
            val side = (bNode.localName ?: bNode.nodeName).lowercase()
            if (side !in listOf("top", "left", "bottom", "right")) continue

            val attrs = bNode.attributes
            val bVal = attrs?.getNamedItem("w:val")?.nodeValue ?: attrs?.getNamedItem("val")?.nodeValue ?: "single"
            if (bVal == "none" || bVal == "nil") {
                borderSides[side] = "border-$side:none !important;"
                continue
            }
            val rawSz = attrs?.getNamedItem("w:sz")?.nodeValue ?: attrs?.getNamedItem("sz")?.nodeValue
            val szPt = (rawSz?.toFloatOrNull() ?: 4f) / 8f
            val borderPx = (szPt * 1.33f).coerceIn(1f, 8f)

            val rawColor = attrs?.getNamedItem("w:color")?.nodeValue ?: attrs?.getNamedItem("color")?.nodeValue
            val color = if (!rawColor.isNullOrBlank() && rawColor != "auto" && rawColor.length == 6) "#$rawColor" else "var(--border-color)"

            val borderStyle = when (bVal.lowercase()) {
                "double" -> "double"
                "dashed", "dashdotstroked" -> "dashed"
                "dotted" -> "dotted"
                else -> "solid"
            }
            borderSides[side] = "border-$side:${String.format(java.util.Locale.US, "%.1f", borderPx)}px $borderStyle $color !important;"
        }

        return if (borderSides.isEmpty()) {
            "border:1px solid var(--border-color);"
        } else {
            listOf("top", "right", "bottom", "left").joinToString(" ") { side ->
                borderSides[side] ?: "border-$side:1px solid var(--border-color);"
            }
        }
    }

    private fun appendTableRowHtml(
        row: XWPFTableRow,
        rowIdx: Int,
        rowSpans: Map<Pair<Int, Int>, Int>,
        sb: StringBuilder,
        doc: XWPFDocument? = null,
        footnotesMap: Map<String, String> = emptyMap()
    ) {
        val rowNodeName = try { row.ctRow?.domNode?.nodeName ?: "w:tr" } catch (_: Exception) { "w:tr" }
        try {
            sb.append("<tr>")
            row.tableCells.forEachIndexed { cIdx, cell ->
                val cellNodeName = try { cell.ctTc?.domNode?.nodeName ?: "w:tc" } catch (_: Exception) { "w:tc" }
                try {
                    val tag = if (rowIdx == 0) "th" else "td"
                    val tcPr = try { cell.ctTc?.tcPr } catch (_: Exception) { null }
                    val colSpan = try { tcPr?.gridSpan?.`val`?.toInt()?.coerceAtLeast(1) ?: 1 } catch (_: Exception) { 1 }

                    val vMergeNode = try { tcPr?.vMerge } catch (_: Exception) { null }
                    val vMergeVal = try { vMergeNode?.`val` } catch (_: Exception) { null }
                    val isVMergeContinue = vMergeNode != null && vMergeVal != org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART
                    if (isVMergeContinue) return@forEachIndexed

                    val rSpan = rowSpans[Pair(rowIdx, cIdx)] ?: 1
                    val bgColor = try { cell.color?.takeIf { it.length == 6 && it != "auto" } } catch (_: Exception) { null }
                    val vAlign = try { cell.verticalAlignment } catch (_: Exception) { null }
                    val vAlignStr = when (vAlign) {
                        XWPFTableCell.XWPFVertAlign.CENTER -> "middle"
                        XWPFTableCell.XWPFVertAlign.BOTTOM -> "bottom"
                        else -> "top"
                    }

                    val tcW = try { tcPr?.tcW } catch (_: Exception) { null }
                    val rawW = try { tcW?.w?.toString()?.toFloatOrNull() } catch (_: Exception) { null }
                    val wType = (try { tcW?.type?.toString()?.lowercase() } catch (_: Exception) { null }) ?: "dxa"
                    val widthStyle = if (rawW != null && rawW > 0) {
                        if (wType.contains("pct")) {
                            val pct = (rawW / 50f).coerceIn(5f, 100f)
                            "width:${pct}%;min-width:64px;"
                        } else {
                            val pt = (rawW / 20f).coerceIn(36f, 800f)
                            "width:${pt}pt;min-width:${minOf(pt, 64f)}pt;"
                        }
                    } else {
                        "min-width:64px;"
                    }

                    val cellParaAlign = cell.paragraphs.firstOrNull()?.let { p ->
                        try { p.alignment } catch (_: Exception) { null }
                    }
                    val cellHAlignStr = when (cellParaAlign) {
                        ParagraphAlignment.CENTER -> "text-align:center;"
                        ParagraphAlignment.RIGHT  -> "text-align:right;"
                        ParagraphAlignment.BOTH   -> "text-align:justify;"
                        else                      -> "text-align:left;"
                    }

                    val cellTextDirection = try {
                        val dom = tcPr?.domNode
                        var dir: String? = null
                        if (dom != null) {
                            val nl = dom.childNodes
                            for (i in 0 until nl.length) {
                                val item = nl.item(i) ?: continue
                                val name = item.localName ?: item.nodeName
                                if (name.equals("textDirection", ignoreCase = true)) {
                                    dir = item.attributes?.getNamedItem("w:val")?.nodeValue
                                        ?: item.attributes?.getNamedItem("val")?.nodeValue
                                    break
                                }
                            }
                        }
                        dir
                    } catch (_: Exception) { null }

                    val cellWritingMode = when (cellTextDirection?.lowercase()) {
                        "tbrl", "tbrlv" -> "writing-mode:vertical-rl;"
                        "btlr" -> "writing-mode:vertical-lr;"
                        else -> "writing-mode:horizontal-tb;"
                    }

                    val cellBordersCss = extractCellBordersCss(cell)
                    val colSpanAttr = if (colSpan > 1) """ colspan="$colSpan"""" else ""
                    val rowSpanAttr = if (rSpan > 1) """ rowspan="$rSpan"""" else ""
                    val bgAttr = if (bgColor != null) "background-color:#$bgColor;" else ""
                    val alignAttr = "vertical-align:$vAlignStr;"

                    sb.append("<$tag$colSpanAttr$rowSpanAttr style=\"$bgAttr$alignAttr$cellHAlignStr$widthStyle$cellWritingMode$cellBordersCss;padding:8px 10px;box-sizing:border-box;word-break:normal;overflow-wrap:normal;white-space:normal;\">")

                    var firstPara = true
                    for (para in cell.paragraphs) {
                        val cellText = try { para.text ?: "" } catch (_: Exception) { "" }
                        val hasPictures = para.runs.any { it.embeddedPictures.isNotEmpty() }
                        if (cellText.isBlank() && firstPara && !hasPictures) { firstPara = false; continue }
                        if (!firstPara) sb.append("<br>")
                        try {
                            if (doc != null) {
                                appendParagraphBodyContent(para, doc, sb, footnotesMap)
                            } else {
                                appendRunsHtmlSimple(para, sb)
                            }
                        } catch (_: Exception) {
                            sb.append(htmlEscape(cellText))
                        }
                        firstPara = false
                    }
                    sb.append("</$tag>")
                } catch (t: Throwable) {
                    Log.w(TAG, "Error parsing table cell <$cellNodeName> at ($rowIdx, $cIdx): ${t.message}", t)
                    sb.append("<td style=\"padding:6px;border:1px solid var(--border-color);min-width:64px;word-break:normal;overflow-wrap:normal;white-space:normal;writing-mode:horizontal-tb;\">${htmlEscape(try { cell.text ?: "" } catch (_: Exception) { "" })}</td>")
                }
            }
            sb.append("</tr>\n")
        } catch (t: Throwable) {
            Log.w(TAG, "Error parsing table row <$rowNodeName> at index $rowIdx: ${t.message}", t)
        }
    }

    private fun renderFallbackTable(table: XWPFTable, sb: StringBuilder) {
        sb.append("<div class=\"table-container\" style=\"max-width:100%;box-sizing:border-box;overflow-x:auto;\"><table class=\"docx-table\" style=\"min-width:100%;box-sizing:border-box;\">\n")
        for ((rIdx, row) in table.rows.withIndex()) {
            sb.append("<tr>")
            val tag = if (rIdx == 0) "th" else "td"
            for (cell in row.tableCells) {
                val txt = try { cell.text ?: "" } catch (_: Exception) { "" }
                sb.append("<$tag style=\"padding:6px;border:1px solid var(--border-color);box-sizing:border-box;min-width:64px;word-break:normal;overflow-wrap:normal;white-space:normal;writing-mode:horizontal-tb;\">${htmlEscape(txt)}</$tag>")
            }
            sb.append("</tr>\n")
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
            val isBold = try { run.isBold } catch (_: Exception) { false }
            val isItalic = try { run.isItalic } catch (_: Exception) { false }
            var span = htmlEscape(decoded)
            if (isBold) span = "<strong>$span</strong>"
            if (isItalic) span = "<em>$span</em>"
            sb.append("""<span class="docx-run" style="writing-mode:horizontal-tb;display:inline;">$span</span>""")
        }
    }

    private fun htmlEscape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
