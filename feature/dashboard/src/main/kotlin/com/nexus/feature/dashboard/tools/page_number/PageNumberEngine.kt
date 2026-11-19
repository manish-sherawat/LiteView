package com.nexus.feature.dashboard.tools.page_number

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin

object PageNumberEngine {

    /**
     * Reads display name, file size, and page count of chosen PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): PageNumberDocInfo = withContext(Dispatchers.IO) {
        var fileName = "Document.pdf"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    cursor.getString(nameIndex)?.let { fileName = it }
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        val baseName = fileName.replace(Regex("(?i)\\.pdf$"), "")

        var pageCount = 1
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                }
            }
        } catch (_: Exception) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        pageCount = doc.numberOfPages.coerceAtLeast(1)
                    }
                }
            } catch (_: Exception) {}
        }

        PageNumberDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            baseName = baseName
        )
    }

    /**
     * Renders a lightweight preview bitmap of a given 0-indexed page using [PdfRenderer].
     */
    suspend fun renderThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 360
    ): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(160, 800)
                    val renderH = (renderW / aspect).toInt().coerceAtLeast(160)

                    val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    /**
     * Converts integer to Roman numeral string.
     */
    fun roman(n: Int, upper: Boolean): String {
        val map = listOf(
            1000 to "m", 900 to "cm", 500 to "d", 400 to "cd",
            100 to "c", 90 to "xc", 50 to "l", 40 to "xl",
            10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i"
        )
        var num = n
        val sb = StringBuilder()
        for ((v, r) in map) {
            while (num >= v) {
                sb.append(r)
                num -= v
            }
        }
        val res = sb.toString()
        return if (upper) res.uppercase() else res
    }

    /**
     * Formats number based on style setting.
     */
    private fun styledNumber(n: Int, style: NumberStyle): String {
        return when (style) {
            NumberStyle.NUM -> n.toString()
            NumberStyle.ROMAN_LOWER -> roman(n, upper = false)
            NumberStyle.ROMAN_UPPER -> roman(n, upper = true)
        }
    }

    /**
     * Generates formatted page number text for a given 1-based page.
     * Returns null if page is outside of [config.fromPage]..[config.toPage].
     */
    fun formatLabel(page: Int, config: PageNumberConfig): String? {
        if (page < config.fromPage || page > config.toPage) return null

        val currentNum = config.startNumber + (page - config.fromPage)
        val lastNum = config.startNumber + (config.toPage - config.fromPage)

        val a = styledNumber(currentNum, config.style)
        val b = styledNumber(lastNum, config.style)

        return when (config.format) {
            NumberFormat.N -> a
            NumberFormat.PAGE -> "Page $a"
            NumberFormat.OF -> "$a / $b"
            NumberFormat.PAGE_OF -> "Page $a of $b"
            NumberFormat.DASH -> "- $a -"
        }
    }

    /**
     * Resolves the appropriate PDFBox [PDType1Font] based on font and fstyle.
     */
    private fun resolveFont(font: NumberFont, fstyle: NumberFontStyle): PDType1Font {
        return when (font) {
            NumberFont.HELVETICA -> when (fstyle) {
                NumberFontStyle.REGULAR -> PDType1Font.HELVETICA
                NumberFontStyle.BOLD -> PDType1Font.HELVETICA_BOLD
                NumberFontStyle.ITALIC -> PDType1Font.HELVETICA_OBLIQUE
                NumberFontStyle.BOLD_ITALIC -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            }
            NumberFont.TIMES -> when (fstyle) {
                NumberFontStyle.REGULAR -> PDType1Font.TIMES_ROMAN
                NumberFontStyle.BOLD -> PDType1Font.TIMES_BOLD
                NumberFontStyle.ITALIC -> PDType1Font.TIMES_ITALIC
                NumberFontStyle.BOLD_ITALIC -> PDType1Font.TIMES_BOLD_ITALIC
            }
            NumberFont.COURIER -> when (fstyle) {
                NumberFontStyle.REGULAR -> PDType1Font.COURIER
                NumberFontStyle.BOLD -> PDType1Font.COURIER_BOLD
                NumberFontStyle.ITALIC -> PDType1Font.COURIER_OBLIQUE
                NumberFontStyle.BOLD_ITALIC -> PDType1Font.COURIER_BOLD_OBLIQUE
            }
        }
    }

    /**
     * Adds page numbers to selected PDF pages and saves output to Downloads.
     */
    suspend fun addPageNumbers(
        context: Context,
        docInfo: PageNumberDocInfo,
        config: PageNumberConfig,
        onProgress: (Float) -> Unit
    ): PageNumberExportResult = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir

        val outputFile = File(downloadsDir, "${docInfo.baseName}-numbered.pdf")
        var document: PDDocument? = null

        try {
            val inputStream = context.contentResolver.openInputStream(docInfo.uri)
                ?: throw IllegalStateException("Cannot open source PDF file.")
            document = PDDocument.load(inputStream)

            val font = resolveFont(config.font, config.fstyle)
            val parsedColor = try {
                AndroidColor.parseColor(config.colorHex)
            } catch (_: Exception) {
                AndroidColor.BLACK
            }
            val red = AndroidColor.red(parsedColor)
            val green = AndroidColor.green(parsedColor)
            val blue = AndroidColor.blue(parsedColor)

            val totalToNumber = (config.toPage - config.fromPage + 1).coerceAtLeast(1)

            for (p in config.fromPage..config.toPage) {
                val pageIndex = p - 1
                if (pageIndex !in 0 until document.numberOfPages) continue

                val text = formatLabel(p, config) ?: continue
                val page = document.getPage(pageIndex)

                val cropBox = page.cropBox ?: page.mediaBox
                val rot = ((page.rotation % 360) + 360) % 360

                val pageW = cropBox.width
                val pageH = cropBox.height
                val vw = if (rot % 180 != 0) pageH else pageW
                val vh = if (rot % 180 != 0) pageW else pageH

                val stringWidthUnits = try {
                    font.getStringWidth(text)
                } catch (_: Exception) {
                    text.length * 600f
                }
                val tw = (stringWidthUnits / 1000f) * config.sizePt
                val m = config.marginPt

                // Calculate baseline start in visible page coordinates (vx, vy)
                val vx: Float
                val vy: Float
                if (config.customPos != null) {
                    vx = (config.customPos.x * vw - tw / 2f).coerceIn(0f, (vw - tw).coerceAtLeast(0f))
                    vy = (vh * (1f - config.customPos.y) - config.sizePt * 0.3f).coerceIn(0f, (vh - config.sizePt).coerceAtLeast(0f))
                } else {
                    vx = when (config.pos.h) {
                        HorizontalPosition.LEFT -> m
                        HorizontalPosition.CENTER -> (vw - tw) / 2f
                        HorizontalPosition.RIGHT -> (vw - m - tw).coerceAtLeast(0f)
                    }
                    vy = when (config.pos.v) {
                        VerticalPosition.TOP -> (vh - m - config.sizePt * 0.8f).coerceAtLeast(0f)
                        VerticalPosition.BOTTOM -> m + config.sizePt * 0.2f
                    }
                }

                // Transform visible coordinates to PDF content space considering page rotation
                val finalX: Float
                val finalY: Float
                val rotationAngle: Float
                when (rot) {
                    0 -> {
                        finalX = cropBox.lowerLeftX + vx
                        finalY = cropBox.lowerLeftY + vy
                        rotationAngle = 0f
                    }
                    90 -> {
                        finalX = cropBox.lowerLeftX + pageW - vy
                        finalY = cropBox.lowerLeftY + vx
                        rotationAngle = 90f
                    }
                    180 -> {
                        finalX = cropBox.lowerLeftX + pageW - vx
                        finalY = cropBox.lowerLeftY + pageH - vy
                        rotationAngle = 180f
                    }
                    else -> { // 270
                        finalX = cropBox.lowerLeftX + vy
                        finalY = cropBox.lowerLeftY + pageH - vx
                        rotationAngle = 270f
                    }
                }

                val cs = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                try {
                    // Set non-stroking opacity using graphics state
                    val gState = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = config.opacity.coerceIn(0.05f, 1.0f)
                    }
                    cs.setGraphicsStateParameters(gState)
                    cs.setNonStrokingColor(red, green, blue)

                    val rad = Math.toRadians(rotationAngle.toDouble())
                    val cosVal = cos(rad).toFloat()
                    val sinVal = sin(rad).toFloat()

                    cs.beginText()
                    cs.setFont(font, config.sizePt)
                    cs.setTextMatrix(Matrix(cosVal, sinVal, -sinVal, cosVal, finalX, finalY))
                    cs.showText(text)
                    cs.endText()
                } finally {
                    cs.close()
                }

                val progress = (p - config.fromPage + 1).toFloat() / totalToNumber.toFloat()
                onProgress(progress.coerceIn(0f, 1f))
            }

            FileOutputStream(outputFile).use { fos ->
                document.save(fos)
            }

            PageNumberExportResult(
                outputFile = outputFile,
                numberedPagesCount = totalToNumber,
                fileSizeBytes = outputFile.length()
            )
        } finally {
            try { document?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Formats bytes into human readable format (B, kB, MB).
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format("%.1f kB", bytes / 1000.0)
            else -> String.format("%.2f MB", bytes / 1_000_000.0)
        }
    }
}

