package com.nexus.feature.dashboard.tools.delete_pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DeletePagesEngine {

    /**
     * Reads display name, file size, and page count of chosen PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): DeleteDocInfo = withContext(Dispatchers.IO) {
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

        DeleteDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            baseName = baseName
        )
    }

    /**
     * Renders a lightweight, high-fidelity thumbnail bitmap of a given 0-indexed page using [PdfRenderer].
     */
    suspend fun renderThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 300
    ): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(120, 800)
                    val renderH = (renderW / aspect).toInt().coerceAtLeast(120)

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
     * Parses page numbers and ranges (e.g. "2-5, 8, 11-13").
     * Supports en-dash, em-dash, commas, and spaces.
     * Validates bounds [1..totalPages].
     */
    fun parseRanges(input: String, totalPages: Int): Result<List<Int>> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Type some page numbers or ranges first."))
        }

        val parts = trimmed.split(Regex("[,\\s;]+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return Result.failure(IllegalArgumentException("Type some page numbers or ranges first."))
        }

        val parsedPages = mutableSetOf<Int>()
        val rangeRegex = Regex("^(\\d+)(?:[-–—](\\d+))?$")

        for (part in parts) {
            val match = rangeRegex.matchEntire(part)
                ?: return Result.failure(IllegalArgumentException("“$part” isn’t a valid page number or range."))

            val start = match.groupValues[1].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("“$part” isn’t a valid page number or range."))
            val end = if (match.groupValues[2].isNotBlank()) {
                match.groupValues[2].toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("“$part” isn’t a valid page number or range."))
            } else {
                start
            }

            val (a, b) = if (start <= end) start to end else end to start

            if (a < 1 || b > totalPages) {
                return Result.failure(
                    IllegalArgumentException(
                        "This PDF has $totalPages page${if (totalPages == 1) "" else "s"}. “$part” is out of range."
                    )
                )
            }

            for (p in a..b) {
                parsedPages.add(p)
            }
        }

        return Result.success(parsedPages.sorted())
    }

    /**
     * Deletes marked pages from the source PDF and writes the cleaned PDF
     * to the user's Downloads directory (${baseName}-cleaned.pdf).
     */
    suspend fun deletePages(
        context: Context,
        docInfo: DeleteDocInfo,
        markedPages: Set<Int>,
        onProgress: (Float) -> Unit
    ): DeleteExportResult = withContext(Dispatchers.IO) {
        val keepIndices = (1..docInfo.pageCount).filter { it !in markedPages }.map { it - 1 }
        if (keepIndices.isEmpty()) {
            throw IllegalStateException("Cannot delete all pages in the PDF. Keep at least 1 page.")
        }

        onProgress(0.1f)

        var srcDoc: PDDocument? = null
        val outDoc = PDDocument()
        try {
            val stream = context.contentResolver.openInputStream(docInfo.uri)
                ?: throw IllegalStateException("Failed to open source PDF.")

            srcDoc = PDDocument.load(stream)
            onProgress(0.25f)

            val totalKeep = keepIndices.size
            for ((idx, pageIdx) in keepIndices.withIndex()) {
                outDoc.importPage(srcDoc.getPage(pageIdx))
                val p = 0.25f + 0.65f * ((idx + 1).toFloat() / totalKeep)
                onProgress(p.coerceIn(0.25f, 0.90f))
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            var outFile = File(downloadsDir, "${docInfo.baseName}-cleaned.pdf")
            var counter = 1
            while (outFile.exists() && outFile.length() > 0) {
                // If a cleaned file exists and might be open or existing, find unique if locked
                val candidate = File(downloadsDir, "${docInfo.baseName}-cleaned($counter).pdf")
                if (!candidate.exists()) {
                    outFile = candidate
                    break
                }
                counter++
            }

            FileOutputStream(outFile).use { fos ->
                outDoc.save(fos)
            }
            onProgress(1f)

            DeleteExportResult(
                outputFile = outFile,
                deletedPagesCount = markedPages.size,
                remainingPagesCount = keepIndices.size,
                fileSizeBytes = outFile.length()
            )
        } finally {
            try { outDoc.close() } catch (_: Exception) {}
            try { srcDoc?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Formats bytes into human readable format (kB, MB).
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format("%.1f kB", bytes / 1000.0)
            else -> String.format("%.2f MB", bytes / 1_000_000.0)
        }
    }
}

