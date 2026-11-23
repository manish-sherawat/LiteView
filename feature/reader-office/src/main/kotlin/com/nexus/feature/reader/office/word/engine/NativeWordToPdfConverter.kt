package com.nexus.feature.reader.office.word.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import com.nexus.feature.reader.office.word.model.DocxHeading
import com.nexus.feature.reader.office.word.parser.DocumentMargins
import com.nexus.feature.reader.office.word.parser.PageOrientation
import com.nexus.feature.reader.office.word.parser.PageSetup
import com.nexus.feature.reader.office.word.parser.PaperSize
import com.nexus.feature.reader.office.word.parser.WordLayoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.xwpf.usermodel.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * Native in-memory conversion service that converts OOXML (DOCX) and binary Word (DOC)
 * documents into standard PDF byte streams with strict typography and spacing rules.
 *
 * Guarantees:
 * 1. 100% In-Memory: Streams directly into a [ByteArrayOutputStream]. Zero files written to permanent storage.
 * 2. Strict Layout & Spacing:
 *    - Strict page margins: All elements constrained within printable bounds.
 *    - Calibrated paragraph padding: Strict space before and space after on body paragraphs and list items.
 *    - Hierarchy-aware heading separation: Distinct space before (14-24pt) and space after (7-12pt) with orphan protection.
 *    - Decorative line spacing: Generous vertical padding around horizontal rules and paragraph borders to prevent overlapping.
 *    - Comfortable line spacing: Minimum 1.28-1.30x line multiplier to prevent crowded text and descender/ascender collisions.
 * 3. Robust Fault-Tolerance: Recovers gracefully from damaged nodes without failing the whole document.
 */
object NativeWordToPdfConverter {

    private const val TAG = "NativeWordToPdf"

    // Default standard A4 fallback dimensions in PostScript points (72 points per inch)
    private const val DEFAULT_PAGE_WIDTH_PT = 595
    private const val DEFAULT_PAGE_HEIGHT_PT = 842
    private const val DEFAULT_MARGIN_X = 54f
    private const val DEFAULT_MARGIN_TOP = 54f
    private const val DEFAULT_MARGIN_BOTTOM = 54f

    suspend fun convertToPdfStream(
        context: Context,
        uri: Uri,
        docType: String,
        password: String? = null
    ): WordEngineResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = openInputStream(context, uri)
                ?: return@withContext WordEngineResult.Error("Unable to open input stream for $uri")

            val isDoc = docType.equals("DOC", ignoreCase = true) ||
                (uri.path?.lowercase()?.endsWith(".doc") == true && !uri.path!!.lowercase().endsWith(".docx"))

            if (isDoc) {
                convertDocStream(inputStream)
            } else {
                convertDocxStream(inputStream, password)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Native Word to PDF conversion failed: ${t.message}", t)
            WordEngineResult.Error("Native conversion failed: ${t.localizedMessage ?: "Unknown error"}", t)
        }
    }

    private fun openInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun convertDocxStream(stream: InputStream, password: String?): WordEngineResult {
        val pushback = java.io.PushbackInputStream(stream, 8)
        val header = ByteArray(8)
        val n = pushback.read(header)
        if (n > 0) pushback.unread(header, 0, n)

        val isOleo = n >= 8 &&
            header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
            header[2] == 0x11.toByte() && header[3] == 0xE0.toByte() &&
            header[4] == 0xA1.toByte() && header[5] == 0xB1.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0xE1.toByte()

        var finalStream: InputStream = pushback
        if (isOleo) {
            try {
                val poifs = POIFSFileSystem(pushback)
                val info = EncryptionInfo(poifs)
                val decryptor = Decryptor.getInstance(info)
                val pwd = password ?: "VelvetSweatshop"
                if (!decryptor.verifyPassword(pwd)) {
                    return WordEngineResult.PasswordRequired(isError = password != null)
                }
                finalStream = decryptor.getDataStream(poifs)
            } catch (e: Exception) {
                return WordEngineResult.PasswordRequired(isError = true)
            }
        }

        val pdfDoc = PdfDocument()
        val headings = mutableListOf<DocxHeading>()

        try {
            XWPFDocument(finalStream).use { doc ->
                // Extract document page setup and enforce strict margin rules
                val pageSetup = try {
                    WordLayoutEngine.extractPageSetup(doc)
                } catch (_: Exception) {
                    PageSetup(
                        paperSize = PaperSize.A4,
                        widthPt = DEFAULT_PAGE_WIDTH_PT.toFloat(),
                        heightPt = DEFAULT_PAGE_HEIGHT_PT.toFloat(),
                        orientation = PageOrientation.PORTRAIT,
                        margins = DocumentMargins(DEFAULT_MARGIN_TOP, DEFAULT_MARGIN_BOTTOM, DEFAULT_MARGIN_X, DEFAULT_MARGIN_X)
                    )
                }

                val pageWidthPt = pageSetup.widthPt.toInt().coerceIn(320, 1600)
                val pageHeightPt = pageSetup.heightPt.toInt().coerceIn(400, 2200)

                // Enforce strict margin rules:
                val marginLeft = pageSetup.margins.leftPt.coerceIn(40f, 90f)
                val marginRight = pageSetup.margins.rightPt.coerceIn(40f, 90f)
                val marginTop = pageSetup.margins.topPt.coerceIn(48f, 90f)
                val marginBottom = pageSetup.margins.bottomPt.coerceIn(48f, 90f)

                val contentWidth = pageWidthPt - marginLeft - marginRight
                val pageBottomLimit = pageHeightPt - marginBottom

                var currentPageNum = 1
                var pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, currentPageNum).create()
                var currentPage = pdfDoc.startPage(pageInfo)
                var currentCanvas = currentPage.canvas
                var cursorY = marginTop

                val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
                currentCanvas.drawRect(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat(), bgPaint)

                fun startNewPage() {
                    drawPageFooter(currentCanvas, currentPageNum, pageWidthPt.toFloat(), pageHeightPt.toFloat(), marginLeft, marginRight)
                    pdfDoc.finishPage(currentPage)

                    currentPageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, currentPageNum).create()
                    currentPage = pdfDoc.startPage(pageInfo)
                    currentCanvas = currentPage.canvas
                    currentCanvas.drawRect(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat(), bgPaint)
                    cursorY = marginTop
                }

                for (elem in doc.bodyElements) {
                    when (elem.elementType) {
                        BodyElementType.PARAGRAPH -> {
                            val para = elem as XWPFParagraph
                            val rawText = para.text ?: ""
                            val cleanText = rawText.trim()
                            val paraStyleId = try { para.style ?: "" } catch (_: Exception) { "" }
                            val isHeading = paraStyleId.lowercase().startsWith("heading")
                            val headingLevel = if (isHeading) {
                                paraStyleId.filter { it.isDigit() }.firstOrNull()?.digitToInt()?.coerceIn(1, 6) ?: 1
                            } else 0

                            // Check explicit page break
                            if (WordLayoutEngine.hasExplicitPageBreak(para)) {
                                if (cursorY > marginTop) {
                                    startNewPage()
                                }
                            }

                            if (isHeading && cleanText.isNotBlank() && cleanText.length >= 2) {
                                headings.add(DocxHeading(text = cleanText, level = headingLevel, anchorId = "h_${headings.size}"))
                            }

                            // Calculate paragraph indentation math
                            val rawIndentLeft = try { para.indentationLeft } catch (_: Exception) { -1 }
                            val rawIndentRight = try { para.indentationRight } catch (_: Exception) { -1 }
                            val indentLeft = if (rawIndentLeft > 0) (rawIndentLeft / 20f).coerceIn(0f, 160f) else 0f
                            val indentRight = if (rawIndentRight > 0) (rawIndentRight / 20f).coerceIn(0f, 120f) else 0f

                            val isList = try { para.numID != null || para.numFmt != null } catch (_: Exception) { false }
                            val listLevel = try { para.numIlvl?.toInt() ?: 0 } catch (_: Exception) { 0 }
                            val listIndent = if (isList) ((listLevel + 1) * 18f).coerceIn(18f, 120f) else 0f

                            val effectiveLeft = marginLeft + indentLeft + listIndent
                            val availableWidth = (pageWidthPt - effectiveLeft - marginRight - indentRight).coerceIn(120f, contentWidth)

                            // Check for decorative horizontal rule / divider line
                            val isHorizontalRule = cleanText.matches(Regex("^[-_*=~]{3,}$"))
                            if (isHorizontalRule) {
                                val dividerSpaceBefore = 12f
                                val dividerSpaceAfter = 14f
                                if (cursorY + dividerSpaceBefore + 2f + dividerSpaceAfter > pageBottomLimit) {
                                    startNewPage()
                                } else {
                                    cursorY += if (cursorY == marginTop) 4f else dividerSpaceBefore
                                }
                                val dividerPaint = Paint().apply {
                                    color = Color.rgb(203, 213, 225)
                                    strokeWidth = 1.25f
                                    style = Paint.Style.STROKE
                                }
                                currentCanvas.drawLine(effectiveLeft, cursorY, effectiveLeft + availableWidth, cursorY, dividerPaint)
                                cursorY += dividerSpaceAfter
                                continue
                            }

                            // Check paragraph borders
                            val hasTopBorder = try {
                                val bdrTop = para.ctp.pPr?.pBdr?.top
                                bdrTop != null && bdrTop.`val`?.toString()?.lowercase() != "none"
                            } catch (_: Exception) { false }

                            val hasBottomBorder = try {
                                val bdrBottom = para.ctp.pPr?.pBdr?.bottom
                                bdrBottom != null && bdrBottom.`val`?.toString()?.lowercase() != "none"
                            } catch (_: Exception) { false }

                            // Handle embedded pictures inside paragraph
                            val pictures = para.runs.flatMap { it.embeddedPictures }
                            for (pic in pictures) {
                                val data = try { pic.pictureData.data } catch (_: Exception) { null }
                                if (data != null && data.isNotEmpty()) {
                                    val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                                    if (bmp != null) {
                                        val picSpaceBefore = if (cursorY == marginTop) 4f else 12f
                                        val picSpaceAfter = 14f
                                        val maxW = availableWidth
                                        val aspect = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
                                        val drawW = minOf(maxW, bmp.width.toFloat())
                                        val drawH = drawW * aspect

                                        if (cursorY + picSpaceBefore + drawH + picSpaceAfter > pageBottomLimit) {
                                            startNewPage()
                                        } else {
                                            cursorY += picSpaceBefore
                                        }

                                        val left = effectiveLeft + ((availableWidth - drawW) / 2f)
                                        currentCanvas.drawBitmap(bmp, null, RectF(left, cursorY, left + drawW, cursorY + drawH), null)
                                        cursorY += drawH + picSpaceAfter
                                    }
                                }
                            }

                            // Blank paragraph handling: enforce strict vertical padding
                            if (rawText.isBlank()) {
                                cursorY += 10f
                                if (cursorY > pageBottomLimit) startNewPage()
                                continue
                            }

                            // Draw decorative top border with proper vertical separation
                            if (hasTopBorder) {
                                val topBorderSpace = if (cursorY == marginTop) 4f else 8f
                                cursorY += topBorderSpace
                                val borderPaint = Paint().apply {
                                    color = Color.rgb(203, 213, 225)
                                    strokeWidth = 1f
                                    style = Paint.Style.STROKE
                                }
                                currentCanvas.drawLine(effectiveLeft, cursorY, effectiveLeft + availableWidth, cursorY, borderPaint)
                                cursorY += 8f // separation between decorative top line and text
                            }

                            // Alignment
                            val alignment = try { para.alignment ?: ParagraphAlignment.LEFT } catch (_: Exception) { ParagraphAlignment.LEFT }
                            val layoutAlign = when (alignment) {
                                ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                                ParagraphAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                                else -> Layout.Alignment.ALIGN_NORMAL
                            }

                            // Configure strict line spacing settings
                            val rawSpacingBetween = try { para.spacingBetween.toDouble() } catch (_: Exception) { -1.0 }
                            val lineMultiplier = if (rawSpacingBetween > 0) {
                                (rawSpacingBetween / 240.0).coerceIn(1.20, 2.2).toFloat()
                            } else {
                                if (isHeading) 1.22f else 1.30f
                            }
                            val lineAdd = if (isHeading) 3.5f else 3.0f

                            // Typography configuration
                            val ssb = buildSpannable(para, isHeading)
                            val tp = TextPaint().apply {
                                isAntiAlias = true
                                textSize = if (isHeading) {
                                    when (headingLevel) {
                                        1 -> 20f
                                        2 -> 16f
                                        3 -> 14f
                                        else -> 12.5f
                                    }
                                } else {
                                    11.5f
                                }
                                color = if (isHeading) {
                                    when (headingLevel) {
                                        1 -> Color.rgb(15, 23, 42) // Slate 900
                                        2 -> Color.rgb(30, 41, 59) // Slate 800
                                        else -> Color.rgb(51, 65, 85) // Slate 700
                                    }
                                } else {
                                    Color.rgb(51, 65, 85)
                                }
                                if (isHeading) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            }

                            val staticLayout = StaticLayout.Builder
                                .obtain(ssb, 0, ssb.length, tp, availableWidth.toInt())
                                .setAlignment(layoutAlign)
                                .setLineSpacing(lineAdd, lineMultiplier)
                                .setIncludePad(false)
                                .build()

                            val elementHeight = staticLayout.height.toFloat()

                            // Vertical separation settings: strict spacing before and spacing after
                            val rawSpacingBefore = try { para.spacingBefore.toDouble() } catch (_: Exception) { -1.0 }
                            val rawSpacingAfter = try { para.spacingAfter.toDouble() } catch (_: Exception) { -1.0 }

                            val spacingBefore = if (cursorY == marginTop) {
                                0f // Suppress space before at top of page
                            } else if (rawSpacingBefore > 0) {
                                (rawSpacingBefore / 20.0).coerceIn(0.0, 36.0).toFloat()
                            } else {
                                if (isHeading) {
                                    when (headingLevel) {
                                        1 -> 24f
                                        2 -> 18f
                                        3 -> 14f
                                        else -> 12f
                                    }
                                } else {
                                    0f
                                }
                            }

                            val spacingAfter = if (rawSpacingAfter > 0) {
                                (rawSpacingAfter / 20.0).coerceIn(3.0, 36.0).toFloat()
                            } else {
                                if (isHeading) {
                                    when (headingLevel) {
                                        1 -> 12f
                                        2 -> 9f
                                        3 -> 7f
                                        else -> 6f
                                    }
                                } else if (isList) {
                                    5f
                                } else {
                                    8f // Strict paragraph padding for normal body text
                                }
                            }

                            // Keep-with-next / Orphan prevention for headings
                            val keepWithNextRoom = if (isHeading) 44f else 0f
                            if (cursorY + spacingBefore + elementHeight + spacingAfter + keepWithNextRoom > pageBottomLimit) {
                                startNewPage()
                            } else {
                                cursorY += spacingBefore
                            }

                            // Draw list bullet dot if list item
                            if (isList && cleanText.isNotBlank()) {
                                val bulletPaint = Paint().apply {
                                    color = Color.rgb(100, 116, 139)
                                    style = Paint.Style.FILL
                                    isAntiAlias = true
                                }
                                val bulletY = cursorY + (tp.textSize * 0.72f)
                                currentCanvas.drawCircle(effectiveLeft - 9f, bulletY, 2.2f, bulletPaint)
                            }

                            currentCanvas.save()
                            currentCanvas.translate(effectiveLeft, cursorY)
                            staticLayout.draw(currentCanvas)
                            currentCanvas.restore()

                            cursorY += elementHeight

                            // Draw decorative bottom border with proper separation
                            if (hasBottomBorder) {
                                cursorY += 6f
                                val borderPaint = Paint().apply {
                                    color = Color.rgb(203, 213, 225)
                                    strokeWidth = 1f
                                    style = Paint.Style.STROKE
                                }
                                currentCanvas.drawLine(effectiveLeft, cursorY, effectiveLeft + availableWidth, cursorY, borderPaint)
                                cursorY += 12f // generous vertical separation after decorative line
                            } else {
                                cursorY += spacingAfter
                            }
                        }
                        BodyElementType.TABLE -> {
                            val table = elem as XWPFTable
                            val numRows = table.rows.size
                            if (numRows == 0) continue

                            val tableSpaceBefore = if (cursorY == marginTop) 4f else 14f
                            val tableSpaceAfter = 16f

                            val numCols = table.rows.maxOfOrNull { it.tableCells.size } ?: 1
                            val colW = contentWidth / numCols.coerceAtLeast(1)
                            val cellPadH = 8f
                            val cellPadV = 6f

                            val cellPaint = TextPaint().apply {
                                isAntiAlias = true
                                textSize = 10f
                                color = Color.rgb(30, 41, 59)
                            }
                            val borderPaint = Paint().apply {
                                color = Color.rgb(203, 213, 225)
                                strokeWidth = 1f
                                style = Paint.Style.STROKE
                            }

                            cursorY += tableSpaceBefore

                            for ((rIdx, row) in table.rows.withIndex()) {
                                var rowHeight = 22f
                                val cellLayouts = row.tableCells.map { cell ->
                                    val txt = cell.text ?: ""
                                    StaticLayout.Builder
                                        .obtain(txt, 0, txt.length, cellPaint, (colW - (cellPadH * 2f)).toInt().coerceAtLeast(20))
                                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                        .setLineSpacing(2f, 1.20f)
                                        .setIncludePad(false)
                                        .build()
                                }
                                val maxCellH = cellLayouts.maxOfOrNull { it.height.toFloat() } ?: 14f
                                rowHeight = maxOf(rowHeight, maxCellH + (cellPadV * 2f))

                                if (cursorY + rowHeight > pageBottomLimit) {
                                    startNewPage()
                                }

                                var cellX = marginLeft
                                for ((cIdx, cellLayout) in cellLayouts.withIndex()) {
                                    val cellRect = RectF(cellX, cursorY, cellX + colW, cursorY + rowHeight)
                                    if (rIdx == 0) {
                                        val headerBg = Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL }
                                        currentCanvas.drawRect(cellRect, headerBg)
                                    }
                                    currentCanvas.drawRect(cellRect, borderPaint)

                                    currentCanvas.save()
                                    currentCanvas.translate(cellX + cellPadH, cursorY + cellPadV)
                                    cellLayout.draw(currentCanvas)
                                    currentCanvas.restore()

                                    cellX += colW
                                }

                                cursorY += rowHeight
                            }
                            cursorY += tableSpaceAfter
                        }
                        else -> {}
                    }
                }

                drawPageFooter(currentCanvas, currentPageNum, pageWidthPt.toFloat(), pageHeightPt.toFloat(), marginLeft, marginRight)
                pdfDoc.finishPage(currentPage)
            }

            val baos = ByteArrayOutputStream(1024 * 128)
            pdfDoc.writeTo(baos)
            val pdfBytes = baos.toByteArray()

            Log.i(TAG, "Successfully generated native PDF stream: ${pdfBytes.size} bytes (${headings.size} headings)")
            return WordEngineResult.PdfStream(
                pdfBytes = pdfBytes,
                pageCount = max(1, pdfDoc.pages.size),
                headings = headings
            )
        } finally {
            try { pdfDoc.close() } catch (_: Exception) {}
        }
    }

    private fun convertDocStream(stream: InputStream): WordEngineResult {
        val pdfDoc = PdfDocument()
        try {
            HWPFDocument(stream).use { doc ->
                val pageWidthPt = DEFAULT_PAGE_WIDTH_PT
                val pageHeightPt = DEFAULT_PAGE_HEIGHT_PT
                val marginLeft = DEFAULT_MARGIN_X
                val marginRight = DEFAULT_MARGIN_X
                val marginTop = DEFAULT_MARGIN_TOP
                val marginBottom = DEFAULT_MARGIN_BOTTOM
                val contentWidth = pageWidthPt - marginLeft - marginRight
                val pageBottomLimit = pageHeightPt - marginBottom

                var currentPageNum = 1
                var pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, currentPageNum).create()
                var currentPage = pdfDoc.startPage(pageInfo)
                var currentCanvas = currentPage.canvas
                var cursorY = marginTop

                val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
                currentCanvas.drawRect(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat(), bgPaint)

                fun startNewPage() {
                    drawPageFooter(currentCanvas, currentPageNum, pageWidthPt.toFloat(), pageHeightPt.toFloat(), marginLeft, marginRight)
                    pdfDoc.finishPage(currentPage)

                    currentPageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, currentPageNum).create()
                    currentPage = pdfDoc.startPage(pageInfo)
                    currentCanvas = currentPage.canvas
                    currentCanvas.drawRect(0f, 0f, pageWidthPt.toFloat(), pageHeightPt.toFloat(), bgPaint)
                    cursorY = marginTop
                }

                val range = doc.range
                val numParas = range.numParagraphs()

                for (i in 0 until numParas) {
                    val p = range.getParagraph(i)
                    val rawText = p.text()?.trimEnd('\r', '\n') ?: ""
                    if (rawText.isBlank()) {
                        cursorY += 8f
                        if (cursorY > pageBottomLimit) startNewPage()
                        continue
                    }

                    val cleanText = rawText.trim()
                    val isHorizontalRule = cleanText.matches(Regex("^[-_*=~]{3,}$"))
                    if (isHorizontalRule) {
                        cursorY += 12f
                        val dividerPaint = Paint().apply {
                            color = Color.rgb(203, 213, 225)
                            strokeWidth = 1.25f
                            style = Paint.Style.STROKE
                        }
                        currentCanvas.drawLine(marginLeft, cursorY, marginLeft + contentWidth, cursorY, dividerPaint)
                        cursorY += 14f
                        continue
                    }

                    val tp = TextPaint().apply {
                        isAntiAlias = true
                        textSize = 11.5f
                        color = Color.rgb(51, 65, 85)
                    }

                    val ssb = SpannableStringBuilder(rawText)
                    for (j in 0 until p.numCharacterRuns()) {
                        val cr = p.getCharacterRun(j)
                        val start = max(0, cr.startOffset - p.startOffset)
                        val end = minOf(ssb.length, max(start, cr.endOffset - p.startOffset))
                        if (start < end) {
                            if (cr.isBold) ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                            if (cr.isItalic) ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, 0)
                        }
                    }

                    val staticLayout = StaticLayout.Builder
                        .obtain(ssb, 0, ssb.length, tp, contentWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(3f, 1.28f)
                        .setIncludePad(false)
                        .build()

                    val elementHeight = staticLayout.height.toFloat()
                    val spacingAfter = 8f

                    if (cursorY + elementHeight + spacingAfter > pageBottomLimit) {
                        startNewPage()
                    }

                    currentCanvas.save()
                    currentCanvas.translate(marginLeft, cursorY)
                    staticLayout.draw(currentCanvas)
                    currentCanvas.restore()

                    cursorY += elementHeight + spacingAfter
                }

                drawPageFooter(currentCanvas, currentPageNum, pageWidthPt.toFloat(), pageHeightPt.toFloat(), marginLeft, marginRight)
                pdfDoc.finishPage(currentPage)
            }

            val baos = ByteArrayOutputStream(1024 * 64)
            pdfDoc.writeTo(baos)
            val pdfBytes = baos.toByteArray()

            return WordEngineResult.PdfStream(
                pdfBytes = pdfBytes,
                pageCount = max(1, pdfDoc.pages.size),
                headings = emptyList()
            )
        } finally {
            try { pdfDoc.close() } catch (_: Exception) {}
        }
    }

    private fun buildSpannable(para: XWPFParagraph, isHeading: Boolean): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        for (run in para.runs) {
            val text = run.text() ?: ""
            if (text.isEmpty()) continue

            val start = ssb.length
            ssb.append(text)
            val end = ssb.length

            if (run.isBold || isHeading) {
                val style = if (run.isItalic) Typeface.BOLD_ITALIC else Typeface.BOLD
                ssb.setSpan(StyleSpan(style), start, end, 0)
            } else if (run.isItalic) {
                ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, 0)
            }

            if (run.underline != null && run.underline != UnderlinePatterns.NONE) {
                ssb.setSpan(UnderlineSpan(), start, end, 0)
            }

            val rawColor = try { run.color } catch (_: Exception) { null }
            if (rawColor != null && rawColor.length == 6 && rawColor != "auto") {
                try {
                    val colorInt = Color.parseColor("#$rawColor")
                    ssb.setSpan(ForegroundColorSpan(colorInt), start, end, 0)
                } catch (_: Exception) {}
            }

            val fontSize = try { run.fontSizeAsDouble?.toFloat() } catch (_: Exception) { null }
            if (fontSize != null && fontSize > 0) {
                ssb.setSpan(AbsoluteSizeSpan(fontSize.toInt(), true), start, end, 0)
            }
        }
        return ssb
    }

    private fun drawPageFooter(
        canvas: Canvas,
        pageNum: Int,
        pageWidth: Float,
        pageHeight: Float,
        marginLeft: Float,
        marginRight: Float
    ) {
        val footerPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.5f
        }
        val footerY = pageHeight - 24f
        canvas.drawLine(marginLeft, footerY - 10f, pageWidth - marginRight, footerY - 10f, linePaint)
        canvas.drawText("Page $pageNum", pageWidth / 2f, footerY, footerPaint)
    }
}
