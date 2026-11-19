package com.nexus.feature.dashboard.tools.rotate_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object RotatePdfEngine {

    /**
     * Formats bytes into a human-readable string:
     * - < 1000 B: "N B"
     * - < 1 MB: "N.1 kB"
     * - >= 1 MB: "N.2 MB"
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format(Locale.US, "%.1f kB", bytes / 1000.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    /**
     * Reads document metadata and initial page rotations from the given PDF Uri.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): Pair<RotatePdfDocInfo, List<Int>> =
        withContext(Dispatchers.IO) {
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

            val baseName = fileName.replace(Regex("(?i)\\.pdf$"), "").ifBlank { "document" }

            val initialRotations = mutableListOf<Int>()
            var pageCount = 0

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Could not open input PDF.")

            PDDocument.load(inputStream).use { doc ->
                pageCount = doc.numberOfPages
                for (i in 0 until pageCount) {
                    val page = doc.getPage(i)
                    initialRotations.add(page.rotation)
                }
            }

            if (pageCount == 0) {
                throw IllegalStateException("PDF contains no pages.")
            }

            val docInfo = RotatePdfDocInfo(
                uri = uri,
                name = fileName,
                sizeBytes = fileSize,
                pageCount = pageCount,
                baseName = baseName
            )

            Pair(docInfo, initialRotations)
        }

    /**
     * Renders a page thumbnail bitmap of a given 0-indexed page using Android's native [PdfRenderer].
     */
    suspend fun renderPageThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 260
    ): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(120, 500)
                    val renderH = (renderW / aspect).toInt().coerceAtLeast(100)

                    val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    /**
     * Parses a page range string (e.g. "2-5, 8") matching the specification.
     * Returns a sorted, distinct list of 1-indexed page numbers or an error message.
     */
    fun parsePageRange(rangeStr: String, maxPage: Int): Result<List<Int>> {
        val parts = rangeStr.split(Regex("[,\\s;]+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return Result.failure(IllegalArgumentException("Type some page numbers first."))
        }

        val out = mutableSetOf<Int>()
        for (part in parts) {
            val match = Regex("^(\\d+)(?:[-–—](\\d+))?$").matchEntire(part)
                ?: return Result.failure(IllegalArgumentException("“$part” isn’t a page number or range."))

            val a = match.groupValues[1].toIntOrNull() ?: 1
            val b = if (match.groupValues[2].isNotEmpty()) match.groupValues[2].toIntOrNull() ?: a else a
            val start = min(a, b)
            val end = max(a, b)

            if (start < 1 || end > maxPage) {
                val pageWord = if (maxPage == 1) "page" else "pages"
                return Result.failure(IllegalArgumentException("This PDF has $maxPage $pageWord. “$part” is out of range."))
            }

            for (p in start..end) {
                out.add(p)
            }
        }

        return Result.success(out.sorted())
    }

    /**
     * Applies rotation deltas losslessly to the PDF and writes to the destination stream.
     * [rotations] maps 1-indexed page number -> relative rotation delta (e.g. 90, 180, 270).
     */
    suspend fun applyRotationsAndSave(
        context: Context,
        inputUri: Uri,
        rotations: Map<Int, Int>,
        outputStream: OutputStream,
        onProgress: (current: Int, total: Int) -> Unit
    ): RotatePdfResult = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(inputUri)
            ?: throw IllegalStateException("Could not open input PDF.")

        PDDocument.load(inputStream).use { doc ->
            val totalPages = doc.numberOfPages
            var turnedCount = 0

            for (i in 0 until totalPages) {
                val pageNumber = i + 1
                val delta = rotations[pageNumber] ?: 0
                if (delta != 0) {
                    val page = doc.getPage(i)
                    val currentRot = page.rotation
                    val newRot = ((currentRot + delta) % 360 + 360) % 360
                    page.rotation = newRot
                    turnedCount++
                }
                onProgress(pageNumber, totalPages)
            }

            doc.save(outputStream)

            RotatePdfResult(
                totalRotatedPages = turnedCount,
                pageCount = totalPages,
                fileSizeBytes = 0L
            )
        }
    }
}

