package com.nexus.feature.dashboard.tools.crop_pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object CropPagesEngine {

    /**
     * Reads display name, file size, and page count of chosen PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): CropDocInfo = withContext(Dispatchers.IO) {
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

        CropDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            baseName = baseName
        )
    }

    /**
     * Renders a preview bitmap of a given 0-indexed page using [PdfRenderer].
     */
    suspend fun renderThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 400
    ): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(140, 1000)
                    val renderH = (renderW / aspect).toInt().coerceAtLeast(140)

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
     * Analyzes pixels of the rendered bitmap to detect non-white content margins.
     */
    fun autoTrim(bitmap: Bitmap): CropRect {
        val w = bitmap.width
        val h = bitmap.height
        var x0 = w
        var y0 = h
        var x1 = -1
        var y1 = -1

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val threshold = 244

        for (y in 0 until h step 2) {
            val rowOffset = y * w
            for (x in 0 until w step 2) {
                val pixel = pixels[rowOffset + x]
                val r = AndroidColor.red(pixel)
                val g = AndroidColor.green(pixel)
                val b = AndroidColor.blue(pixel)

                if (r < threshold || g < threshold || b < threshold) {
                    if (x < x0) x0 = x
                    if (x > x1) x1 = x
                    if (y < y0) y0 = y
                    if (y > y1) y1 = y
                }
            }
        }

        if (x1 < 0) {
            return CropRect.FULL
        }

        val pad = 0.015f
        val normX0 = (x0.toFloat() / w.toFloat() - pad).coerceIn(0f, 1f)
        val normY0 = (y0.toFloat() / h.toFloat() - pad).coerceIn(0f, 1f)
        val normX1 = ((x1 + 2).toFloat() / w.toFloat() + pad).coerceIn(0f, 1f)
        val normY1 = ((y1 + 2).toFloat() / h.toFloat() + pad).coerceIn(0f, 1f)

        return CropRect(normX0, normY0, normX1, normY1)
    }

    /**
     * Parses page numbers and ranges (e.g. "2-5, 8") to copy crop settings.
     */
    fun parseRangePages(input: String, totalPages: Int): Result<List<Int>> {
        val out = mutableSetOf<Int>()
        val parts = input.split(Regex("[,\\s;]+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return Result.failure(IllegalArgumentException("Type some page numbers first."))
        }

        val rangeRegex = Regex("^(\\d+)(?:[-–—](\\d+))?$")
        for (part in parts) {
            val match = rangeRegex.matchEntire(part)
                ?: return Result.failure(IllegalArgumentException("“$part” isn’t a page number or range."))

            var a = match.groupValues[1].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("“$part” is invalid."))
            var b = if (match.groupValues.size > 2 && match.groupValues[2].isNotEmpty()) {
                match.groupValues[2].toIntOrNull() ?: a
            } else {
                a
            }

            if (a > b) {
                val temp = a
                a = b
                b = temp
            }

            if (a < 1 || b > totalPages) {
                return Result.failure(
                    IllegalArgumentException("This PDF has $totalPages page${if (totalPages == 1) "" else "s"}. “$part” is out of range.")
                )
            }

            for (i in a..b) {
                out.add(i)
            }
        }

        return Result.success(out.sorted())
    }

    /**
     * Applies crop rectangles to pages and saves output to Downloads.
     */
    suspend fun cropPdf(
        context: Context,
        docInfo: CropDocInfo,
        crops: Map<Int, CropRect>, // 1-indexed page -> CropRect
        onProgress: (Float) -> Unit
    ): CropExportResult = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir

        val outputFile = File(downloadsDir, "${docInfo.baseName}-cropped.pdf")
        var document: PDDocument? = null

        try {
            val inputStream = context.contentResolver.openInputStream(docInfo.uri)
                ?: throw IllegalStateException("Cannot open source PDF file.")
            document = PDDocument.load(inputStream)

            val sortedPages = crops.keys.sorted()
            val total = sortedPages.size

            for ((idx, pageNum) in sortedPages.withIndex()) {
                val pageIndex = pageNum - 1
                if (pageIndex !in 0 until document.numberOfPages) continue

                val rect = crops[pageNum] ?: continue
                if (rect.isFull) continue

                val page = document.getPage(pageIndex)
                val cb = page.cropBox ?: page.mediaBox
                val rot = ((page.rotation % 360) + 360) % 360

                val pageW = cb.width
                val pageH = cb.height
                val vw = if (rot % 180 != 0) pageH else pageW
                val vh = if (rot % 180 != 0) pageW else pageH

                // Visible coordinate bounds (origin bottom-left in visible space)
                val ax = rect.x0 * vw
                val bx = rect.x1 * vw
                val ay = vh * (1f - rect.y1)
                val by = vh * (1f - rect.y0)

                // Map back to PDF coordinate space based on rotation
                val (x1, y1) = when (rot) {
                    0 -> Pair(ax, ay)
                    90 -> Pair(pageW - by, ax)
                    180 -> Pair(pageW - bx, pageH - by)
                    else -> Pair(ay, pageH - bx)
                }
                val (x2, y2) = when (rot) {
                    0 -> Pair(bx, by)
                    90 -> Pair(pageW - ay, bx)
                    180 -> Pair(pageW - ax, pageH - ay)
                    else -> Pair(by, pageH - ax)
                }

                val finalX = cb.lowerLeftX + min(x1, x2)
                val finalY = cb.lowerLeftY + min(y1, y2)
                val finalW = abs(x2 - x1)
                val finalH = abs(y2 - y1)

                val newBox = PDRectangle(finalX, finalY, finalW, finalH)
                page.cropBox = newBox
                page.mediaBox = newBox

                onProgress((idx + 1).toFloat() / total.toFloat())
            }

            FileOutputStream(outputFile).use { fos ->
                document.save(fos)
            }

            CropExportResult(
                outputFile = outputFile,
                croppedPagesCount = total,
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

