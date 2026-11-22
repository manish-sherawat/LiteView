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
 * Legacy Apache POI + HTML/CSS layout engine for Word documents.
 * Retained as a reliable fallback when LibreOfficeKit is disabled or unavailable.
 */
@Singleton
class PoiWordEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : WordEngine {

    companion object {
        private const val TAG = "PoiWordEngine"
    }

    override val engineName: String = "Apache POI Legacy Engine"

    override suspend fun loadDocument(
        uri: Uri,
        docType: String,
        password: String?
    ): WordEngineResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext WordEngineResult.Error("Could not open file stream for $uri")

            stream.use { s ->
                if (docType.equals("DOC", ignoreCase = true)) {
                    parseDoc(s)
                } else {
                    parseDocx(s, password)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "POI engine failure", t)
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
                paperSizeName = "A4",
                paperDimensions = "210 × 297 mm",
                orientation = "Portrait",
                marginsDescription = "Normal"
            )
        } catch (e: Exception) {
            WordEngineResult.Error("Failed to parse binary .DOC: ${e.message}", e)
        }
    }

    private fun parseDocx(stream: InputStream, password: String?): WordEngineResult {
        var finalStream = stream
        var poifsToClose: POIFSFileSystem? = null

        if (password != null) {
            val poifs = POIFSFileSystem(stream)
            poifsToClose = poifs
            val info = EncryptionInfo(poifs)
            val decryptor = Decryptor.getInstance(info)
            if (!decryptor.verifyPassword(password)) {
                poifs.close()
                return WordEngineResult.PasswordRequired(isError = true)
            }
            finalStream = decryptor.getDataStream(poifs)
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
                        val blockHtml = WordDiagramParser.renderDiagramBlock(consecutiveDiagrams)
                        currentPage.append(blockHtml)
                        currentHeight += (consecutiveDiagrams.size * 18f) + 36f
                        consecutiveDiagrams.clear()
                    }
                }

                for (element in bodyElements) {
                    when (element.elementType) {
                        BodyElementType.PARAGRAPH -> {
                            val para = element as XWPFParagraph
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

                            appendParagraphHtml(para, doc, currentPage, headings)
                            currentHeight += estimatedHeight
                        }
                        BodyElementType.TABLE -> {
                            flushDiagramParagraphs()
                            val table = element as XWPFTable
                            for (row in table.rows) {
                                for (cell in row.tableCells) {
                                    val txt = cell.text ?: ""
                                    totalWords += txt.split(Regex("\\s+")).count { it.isNotBlank() }
                                    totalChars += txt.length
                                }
                            }

                            val rowSpans = computeRowSpans(table)
                            val tableHeaderHeight = if (table.rows.isNotEmpty()) WordLayoutEngine.estimateTableRowHeight(table.rows[0], printableWidth) else 24f

                            var inTablePage = false
                            fun startTableChunk() {
                                currentPage.append("<div class=\"table-container\"><table class=\"docx-table\">\n")
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
                                        appendTableRowHtml(table.rows[0], 0, rowSpans, currentPage)
                                        currentHeight += tableHeaderHeight
                                    }
                                }
                                appendTableRowHtml(row, rowIdx, rowSpans, currentPage)
                                currentHeight += rowHeight
                            }
                            endTableChunk()
                        }
                        else -> {}
                    }
                }
                flushDiagramParagraphs()
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

        val indentLeft = if (rawIndentLeft > 0) (rawIndentLeft / 20.0).coerceIn(0.0, 32.0) else null
        val indentRight = if (rawIndentRight > 0) (rawIndentRight / 20.0).coerceIn(0.0, 24.0) else null
        val indentFirstLine = if (rawIndentFirstLine > 0) (rawIndentFirstLine / 20.0).coerceIn(0.0, 24.0) else null

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
                    } catch (_: Exception) {}
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
                val escaped = htmlEscape(decodedText).replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")

                val styles = buildString {
                    if (colorHex != null) append("color:$colorHex;")
                    if (fontSize != null) append("font-size:${fontSize}pt;")
                    if (fontFamily != null) append("font-family:'$fontFamily';")
                    if (highlightColor != null) append("background-color:$highlightColor;")
                    if (isCapitalized) append("text-transform:uppercase;")
                    if (isSmallCaps) append("font-variant:small-caps;")
                }

                var span = escaped
                if (isBold)      span = "<strong>$span</strong>"
                if (isItalic)    span = "<em>$span</em>"
                if (isUnderline) span = "<u>$span</u>"
                if (isStrike || isDoubleStrike) span = "<s>$span</s>"
                if (isSubscript) span = "<sub>$span</sub>"
                if (isSuperscript) span = "<sup>$span</sup>"
                if (styles.isNotEmpty()) span = """<span style="$styles">$span</span>"""
                if (url != null) {
                    span = if (url.startsWith("#")) """<a href="$url">$span</a>""" else """<a href="${htmlEscape(url)}">$span</a>"""
                }

                for (fnId in footnoteIds) span += "<sup><a href=\"#footnote_$fnId\">[$fnId]</a></sup>"
                for (enId in endnoteIds) span += "<sup><a href=\"#endnote_$enId\">[$enId]</a></sup>"

                sb.append(span)
            } catch (_: Exception) {}
        }
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
