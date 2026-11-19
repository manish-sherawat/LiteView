package com.nexus.feature.dashboard.tools.watermark_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
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
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.sin

object WatermarkPdfEngine {

    private const val REFERENCE_PAGE_WIDTH = 595f

    /**
     * Renders a lightweight, high-fidelity preview bitmap of any 0-indexed page with solid white canvas background.
     */
    suspend fun renderPageThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 600
    ): Bitmap? = withContext(Dispatchers.IO) {
        val directResult = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex in 0 until renderer.pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val aspect = page.width.toFloat() / page.height.toFloat()
                            val renderW = targetWidth.coerceIn(300, 1200)
                            val renderH = (renderW / aspect).toInt().coerceAtLeast(100)
                            val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }

        if (directResult != null) return@withContext directResult

        // Fallback for non-seekable file descriptors: buffer into a temporary file
        try {
            val tempFile = File.createTempFile("wm_thumb_", ".pdf", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex in 0 until renderer.pageCount) {
                            renderer.openPage(pageIndex).use { page ->
                                val aspect = page.width.toFloat() / page.height.toFloat()
                                val renderW = targetWidth.coerceIn(300, 1200)
                                val renderH = (renderW / aspect).toInt().coerceAtLeast(100)
                                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            }
                        } else null
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads metadata and page count of a selected PDF.
     * Renders a lightweight preview bitmap of the first page using native [PdfRenderer].
     */
    suspend fun readPdfMetadata(context: Context, uri: Uri): PdfFileInfo = withContext(Dispatchers.IO) {
        var fileName = "Document.pdf"
        var fileSize = 0L

        // Resolve display name and file size
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

        var pageCount = 1

        // Fast metadata detection
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                }
            }
        } catch (_: Exception) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        pageCount = doc.numberOfPages
                    }
                }
            } catch (_: Exception) {}
        }

        // Render high-fidelity first page thumbnail
        val firstPageBitmap = renderPageThumbnail(context, uri, 0)

        PdfFileInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount.coerceAtLeast(1),
            firstPageBitmap = firstPageBitmap
        )
    }

    /**
     * Calculates center placements for watermarks across a page in PDF coordinates.
     */
    fun calculatePlacements(
        pageW: Float,
        pageH: Float,
        tw: Float,
        size: Float,
        isTiled: Boolean
    ): List<PointF> {
        if (!isTiled) {
            return listOf(PointF(pageW / 2f, pageH / 2f))
        }

        val stepX = tw + size * 1.2f
        val stepY = size * 3f
        val points = mutableListOf<PointF>()
        var row = 0
        var y = -stepY
        while (y < pageH + stepY) {
            val offset = if (row % 2 != 0) stepX / 2f else 0f
            var x = -stepX + offset
            while (x < pageW + stepX) {
                points.add(PointF(x, y))
                x += stepX
            }
            y += stepY
            row++
        }
        return points
    }

    /**
     * Sanitizes input text to only allow characters supported by the standard PDF Type 1 font.
     */
    fun sanitizeForPdf(text: String): String {
        return text.map { ch ->
            if (ch.code in 32..126 || ch.code in 160..255) ch else '?'
        }.joinToString("")
    }

    /**
     * Applies watermark to all pages of the PDF and writes the result to [outputStream].
     * Returns total pages watermarked.
     */
    suspend fun applyWatermark(
        context: Context,
        inputUri: Uri,
        outputStream: OutputStream,
        config: WatermarkConfig
    ): Int = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw IllegalStateException("Unable to open input PDF")
            document = PDDocument.load(inputStream)

            val rawText = config.text.trim().ifEmpty { "DRAFT" }
            val sanitizedText = sanitizeForPdf(rawText)
            val font = PDType1Font.HELVETICA_BOLD

            val opacity = (config.opacityPct / 100f).coerceIn(0.01f, 1.0f)
            val grayInt = (config.ink.grayLevel * 255f).toInt().coerceIn(0, 255)
            val rotDeg = config.rotationDeg
            val rad = Math.toRadians(rotDeg.toDouble())
            val cosVal = cos(rad).toFloat()
            val sinVal = sin(rad).toFloat()

            val isTiled = config.layoutMode == WatermarkLayoutMode.TILED

            for (i in 0 until document.numberOfPages) {
                val page = document.getPage(i)
                val mediaBox = page.mediaBox ?: page.cropBox
                val pageW = mediaBox.width
                val pageH = mediaBox.height

                // Scale proportionally relative to reference A4 page width (595pt)
                val k = pageW / REFERENCE_PAGE_WIDTH
                val s = (config.sizePt * k).coerceAtLeast(10f)

                val stringWidthUnits = try {
                    font.getStringWidth(sanitizedText)
                } catch (_: Exception) {
                    sanitizedText.length * 600f
                }
                val tw = (stringWidthUnits / 1000f) * s
                val capH = s * 0.72f

                val placements = calculatePlacements(pageW, pageH, tw, s, isTiled)

                val cs = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                try {
                    // Set opacity via PDExtendedGraphicsState
                    val gState = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity
                        strokingAlphaConstant = opacity
                    }
                    cs.setGraphicsStateParameters(gState)

                    cs.setNonStrokingColor(grayInt, grayInt, grayInt)

                    val vx = -tw / 2f
                    val vy = -capH / 2f

                    for (p in placements) {
                        val anchorX = p.x + vx * cosVal - vy * sinVal
                        val anchorY = p.y + vx * sinVal + vy * cosVal

                        cs.beginText()
                        cs.setFont(font, s)
                        cs.setTextMatrix(Matrix(cosVal, sinVal, -sinVal, cosVal, anchorX, anchorY))
                        cs.showText(sanitizedText)
                        cs.endText()
                    }
                } finally {
                    cs.close()
                }
            }

            document.save(outputStream)
            document.numberOfPages
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
