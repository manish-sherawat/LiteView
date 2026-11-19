package com.nexus.feature.dashboard.tools.word_to_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.*
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max

data class WordPageModel(
    val pageNumber: Int,
    val thumbnailBitmap: Bitmap,
    val fullBitmap: Bitmap
)

data class WordDocumentModel(
    val fileName: String,
    val fileSizeBytes: Long,
    val fileSizeFormatted: String,
    val pages: List<WordPageModel>
)

object WordDocumentRenderer {

    private const val A4_WIDTH_PT = 595f
    private const val A4_HEIGHT_PT = 842f
    private const val MARGIN_X = 44f
    private const val MARGIN_TOP = 48f
    private const val MARGIN_BOTTOM = 48f
    private const val CONTENT_WIDTH = A4_WIDTH_PT - (MARGIN_X * 2) // 507 pt
    private const val CONTENT_HEIGHT = A4_HEIGHT_PT - MARGIN_TOP - MARGIN_BOTTOM // 746 pt

    private sealed class PageElement {
        abstract val height: Float
        abstract val spacingAfter: Float

        data class ParagraphElement(
            val layout: StaticLayout,
            val alignment: Layout.Alignment,
            val isHeading: Boolean = false,
            override val height: Float,
            override val spacingAfter: Float = 10f
        ) : PageElement()

        data class ImageElement(
            val bitmap: Bitmap,
            val drawWidth: Float,
            val drawHeight: Float,
            override val height: Float,
            override val spacingAfter: Float = 12f
        ) : PageElement()

        data class TableRowElement(
            val cellLayouts: List<StaticLayout?>,
            val columnWidths: List<Float>,
            override val height: Float,
            override val spacingAfter: Float = 0f
        ) : PageElement()
    }

    private data class InternalPage(
        val elements: MutableList<Pair<Float, PageElement>> = mutableListOf() // Y-offset to element
    )

    suspend fun parseWordDocument(
        context: Context,
        uri: Uri
    ): Result<WordDocumentModel> = withContext(Dispatchers.IO) {
        try {
            var fileName = "Document.docx"
            var fileSizeBytes = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        fileSizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (fileName.lowercase().endsWith(".doc") && !fileName.lowercase().endsWith(".docx")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Old .doc files aren't supported. Open it in Word and save it as .docx first.")
                )
            }

            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Could not open file stream"))

            val doc = try {
                XWPFDocument(inputStream)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    Exception("Couldn't open that file. It may be damaged or password-protected.")
                )
            } finally {
                try { inputStream.close() } catch (_: Exception) {}
            }

            val pages = layoutDocument(doc)
            doc.close()

            if (pages.isEmpty()) {
                return@withContext Result.failure(Exception("No readable content found in Word document."))
            }

            val renderedPages = mutableListOf<WordPageModel>()
            val totalPages = pages.size

            for ((index, page) in pages.withIndex()) {
                val pageNumber = index + 1
                val thumb = renderPageBitmap(page, pageNumber, totalPages, scale = 0.5f)
                val full = renderPageBitmap(page, pageNumber, totalPages, scale = 1.5f)
                renderedPages.add(WordPageModel(pageNumber, thumb, full))
            }

            val fileSizeFormatted = formatFileSize(fileSizeBytes)

            Result.success(
                WordDocumentModel(
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    fileSizeFormatted = fileSizeFormatted,
                    pages = renderedPages
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun layoutDocument(doc: XWPFDocument): List<InternalPage> {
        val pages = mutableListOf<InternalPage>()
        var currentPage = InternalPage()
        pages.add(currentPage)
        var currentY = MARGIN_TOP

        fun newPage() {
            currentPage = InternalPage()
            pages.add(currentPage)
            currentY = MARGIN_TOP
        }

        val bodyElements = doc.bodyElements

        for (element in bodyElements) {
            when (element) {
                is XWPFParagraph -> {
                    // Check for explicit page break
                    val hasPageBreak = element.runs.any { run ->
                        try { run.text()?.contains("\u000c") == true } catch (_: Exception) { false }
                    }

                    if (hasPageBreak && currentPage.elements.isNotEmpty()) {
                        newPage()
                    }

                    // Check for embedded images in this paragraph
                    for (run in element.runs) {
                        for (pic in run.embeddedPictures) {
                            try {
                                val picData = pic.pictureData.data
                                val bmp = BitmapFactory.decodeByteArray(picData, 0, picData.size)
                                if (bmp != null) {
                                    val maxW = CONTENT_WIDTH
                                    val maxH = 340f
                                    val aspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                                    var drawW = maxW.coerceAtMost(bmp.width.toFloat())
                                    var drawH = drawW / aspect
                                    if (drawH > maxH) {
                                        drawH = maxH
                                        drawW = drawH * aspect
                                    }

                                    val imgElem = PageElement.ImageElement(
                                        bitmap = bmp,
                                        drawWidth = drawW,
                                        drawHeight = drawH,
                                        height = drawH,
                                        spacingAfter = 12f
                                    )

                                    if (currentY + imgElem.height > A4_HEIGHT_PT - MARGIN_BOTTOM && currentPage.elements.isNotEmpty()) {
                                        newPage()
                                    }

                                    currentPage.elements.add(currentY to imgElem)
                                    currentY += imgElem.height + imgElem.spacingAfter
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    // Now process text content
                    val text = element.text ?: ""
                    if (text.isNotBlank()) {
                        val isHeading = element.style?.lowercase()?.contains("heading") == true ||
                                element.style?.lowercase()?.contains("title") == true

                        val align = when (element.alignment) {
                            ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                            ParagraphAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                            else -> Layout.Alignment.ALIGN_NORMAL
                        }

                        val spannable = SpannableStringBuilder()
                        for (run in element.runs) {
                            val rText = try { run.text() ?: "" } catch (_: Exception) { "" }
                            if (rText.isEmpty()) continue
                            val start = spannable.length
                            spannable.append(rText)
                            val end = spannable.length

                            // Font size
                            val ptSize = if (run.fontSize > 0) run.fontSize.toFloat() else if (isHeading) 16f else 11.5f
                            val pxSize = ptSize * 1.33f
                            spannable.setSpan(AbsoluteSizeSpan(pxSize.toInt()), start, end, 0)

                            // Bold / Italic
                            val isBold = run.isBold || isHeading
                            val isItalic = run.isItalic
                            val style = when {
                                isBold && isItalic -> Typeface.BOLD_ITALIC
                                isBold -> Typeface.BOLD
                                isItalic -> Typeface.ITALIC
                                else -> Typeface.NORMAL
                            }
                            if (style != Typeface.NORMAL) {
                                spannable.setSpan(StyleSpan(style), start, end, 0)
                            }

                            // Color
                            val hexColor = run.color
                            if (!hexColor.isNullOrBlank()) {
                                try {
                                    val parsedColor = Color.parseColor("#$hexColor")
                                    spannable.setSpan(ForegroundColorSpan(parsedColor), start, end, 0)
                                } catch (_: Exception) {}
                            } else {
                                val defaultColor = if (isHeading) Color.rgb(15, 23, 42) else Color.rgb(30, 41, 59)
                                spannable.setSpan(ForegroundColorSpan(defaultColor), start, end, 0)
                            }
                        }

                        if (spannable.isNotEmpty()) {
                            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (isHeading) Color.rgb(15, 23, 42) else Color.rgb(30, 41, 59)
                                textSize = (if (isHeading) 16f else 11.5f) * 1.33f
                            }

                            val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                StaticLayout.Builder.obtain(spannable, 0, spannable.length, textPaint, CONTENT_WIDTH.toInt())
                                    .setAlignment(align)
                                    .setLineSpacing(0f, 1.2f)
                                    .setIncludePad(false)
                                    .build()
                            } else {
                                @Suppress("DEPRECATION")
                                StaticLayout(spannable, textPaint, CONTENT_WIDTH.toInt(), align, 1.2f, 0f, false)
                            }

                            val pElem = PageElement.ParagraphElement(
                                layout = staticLayout,
                                alignment = align,
                                isHeading = isHeading,
                                height = staticLayout.height.toFloat(),
                                spacingAfter = if (isHeading) 14f else 8f
                            )

                            if (currentY + pElem.height > A4_HEIGHT_PT - MARGIN_BOTTOM && currentPage.elements.isNotEmpty()) {
                                newPage()
                            }

                            currentPage.elements.add(currentY to pElem)
                            currentY += pElem.height + pElem.spacingAfter
                        }
                    }
                }
                is XWPFTable -> {
                    val rows = element.rows
                    if (rows.isNotEmpty()) {
                        val numCols = rows.maxOfOrNull { it.tableCells.size } ?: 1
                        val colWidth = CONTENT_WIDTH / numCols.coerceAtLeast(1)
                        val colWidths = List(numCols) { colWidth }

                        for (row in rows) {
                            val cellLayouts = mutableListOf<StaticLayout?>()
                            var maxCellH = 24f

                            for (cell in row.tableCells) {
                                val cellText = cell.text ?: ""
                                if (cellText.isNotBlank()) {
                                    val cellSpannable = SpannableStringBuilder(cellText)
                                    val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = Color.rgb(30, 41, 59)
                                        textSize = 10f * 1.33f
                                    }
                                    val usableCellWidth = (colWidth - 12f).coerceAtLeast(20f).toInt()
                                    val cellLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        StaticLayout.Builder.obtain(cellSpannable, 0, cellSpannable.length, cellPaint, usableCellWidth)
                                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                            .setLineSpacing(0f, 1.15f)
                                            .setIncludePad(false)
                                            .build()
                                    } else {
                                        @Suppress("DEPRECATION")
                                        StaticLayout(cellSpannable, cellPaint, usableCellWidth, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
                                    }
                                    cellLayouts.add(cellLayout)
                                    maxCellH = max(maxCellH, cellLayout.height.toFloat() + 12f)
                                } else {
                                    cellLayouts.add(null)
                                }
                            }

                            val rowElem = PageElement.TableRowElement(
                                cellLayouts = cellLayouts,
                                columnWidths = colWidths,
                                height = maxCellH,
                                spacingAfter = 0f
                            )

                            if (currentY + rowElem.height > A4_HEIGHT_PT - MARGIN_BOTTOM && currentPage.elements.isNotEmpty()) {
                                newPage()
                            }

                            currentPage.elements.add(currentY to rowElem)
                            currentY += rowElem.height + rowElem.spacingAfter
                        }
                        currentY += 8f
                    }
                }
            }
        }

        // Remove trailing empty pages if any
        val validPages = pages.filter { it.elements.isNotEmpty() }
        return if (validPages.isNotEmpty()) validPages else listOf(InternalPage())
    }

    private fun renderPageBitmap(
        page: InternalPage,
        pageNumber: Int,
        totalPages: Int,
        scale: Float
    ): Bitmap {
        val widthPx = (A4_WIDTH_PT * scale).toInt()
        val heightPx = (A4_HEIGHT_PT * scale).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPageContent(canvas, page, pageNumber, totalPages, scale)

        return bitmap
    }

    private fun drawPageContent(
        canvas: Canvas,
        page: InternalPage,
        pageNumber: Int,
        totalPages: Int,
        scale: Float
    ) {
        // Draw crisp white background
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, A4_WIDTH_PT * scale, A4_HEIGHT_PT * scale, bgPaint)

        canvas.save()
        canvas.scale(scale, scale)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            style = Paint.Style.FILL
        }

        for ((elemY, element) in page.elements) {
            when (element) {
                is PageElement.ParagraphElement -> {
                    canvas.save()
                    if (element.isHeading) {
                        // Draw small accent bar for heading
                        canvas.drawRect(
                            MARGIN_X - 10f,
                            elemY + 2f,
                            MARGIN_X - 6f,
                            elemY + element.height - 2f,
                            headerBarPaint
                        )
                    }
                    val x = when (element.alignment) {
                        Layout.Alignment.ALIGN_CENTER -> MARGIN_X
                        Layout.Alignment.ALIGN_OPPOSITE -> MARGIN_X
                        else -> MARGIN_X
                    }
                    canvas.translate(x, elemY)
                    element.layout.draw(canvas)
                    canvas.restore()
                }
                is PageElement.ImageElement -> {
                    val destRect = RectF(
                        MARGIN_X + (CONTENT_WIDTH - element.drawWidth) / 2f,
                        elemY,
                        MARGIN_X + (CONTENT_WIDTH - element.drawWidth) / 2f + element.drawWidth,
                        elemY + element.drawHeight
                    )
                    canvas.drawBitmap(element.bitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                }
                is PageElement.TableRowElement -> {
                    var currentX = MARGIN_X
                    for ((colIdx, colW) in element.columnWidths.withIndex()) {
                        val cellRect = RectF(currentX, elemY, currentX + colW, elemY + element.height)
                        canvas.drawRect(cellRect, borderPaint)

                        val cellLayout = element.cellLayouts.getOrNull(colIdx)
                        if (cellLayout != null) {
                            canvas.save()
                            canvas.translate(currentX + 6f, elemY + 6f)
                            cellLayout.draw(canvas)
                            canvas.restore()
                        }
                        currentX += colW
                    }
                }
            }
        }

        // Draw page number footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Page $pageNumber of $totalPages",
            A4_WIDTH_PT / 2f,
            A4_HEIGHT_PT - 24f,
            footerPaint
        )

        canvas.restore()
    }

    suspend fun exportToPdf(
        pages: List<WordPageModel>,
        selectedPageNumbers: Set<Int>,
        quality: Float, // 50f to 100f
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val sortedPages = pages.filter { selectedPageNumbers.contains(it.pageNumber) }
            .sortedBy { it.pageNumber }

        val totalToExport = sortedPages.size.coerceAtLeast(1)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for ((idx, pageModel) in sortedPages.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT.toInt(), A4_HEIGHT_PT.toInt(), idx + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Render high-quality bitmap scaled to fit A4 page
            val bmp = pageModel.fullBitmap
            val destRect = Rect(0, 0, A4_WIDTH_PT.toInt(), A4_HEIGHT_PT.toInt())
            canvas.drawBitmap(bmp, null, destRect, bitmapPaint)

            pdfDocument.finishPage(page)
            onProgress((idx + 1).toFloat() / totalToExport.toFloat())
        }

        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f kB", bytes / 1024f)
            else -> String.format("%.2f MB", bytes / (1024f * 1024f))
        }
    }
}
