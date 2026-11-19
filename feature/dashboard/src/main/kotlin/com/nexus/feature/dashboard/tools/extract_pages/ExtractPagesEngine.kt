package com.nexus.feature.dashboard.tools.extract_pages

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
import java.io.File
import java.io.FileOutputStream

object ExtractPagesEngine {

    /**
     * Reads display name, file size, and page count of the chosen PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): ExtractPdfDocInfo = withContext(Dispatchers.IO) {
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

        ExtractPdfDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            baseName = baseName
        )
    }

    /**
     * Renders a lightweight thumbnail bitmap of a given 0-indexed page using [PdfRenderer].
     */
    suspend fun renderThumbnail(
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
     * Renders a high-resolution bitmap of a given 0-indexed page for dialog preview.
     */
    suspend fun renderHighRes(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 1000
    ): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(400, 2000)
                    val renderH = (renderW / aspect).toInt().coerceAtLeast(400)

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
     * Parses page numbers and ranges (e.g., "187-230" or "1, 5, 10-20").
     */
    fun parsePageRanges(input: String, totalPages: Int): RangeParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return RangeParseResult.Error("Type some page numbers first.")
        }

        val parts = trimmed.split(Regex("[,\\s;]+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return RangeParseResult.Error("Type some page numbers first.")
        }

        val out = mutableSetOf<Int>()
        val rangeRegex = Regex("^(\\d+)(?:[-–—](\\d+))?$")

        for (part in parts) {
            val match = rangeRegex.matchEntire(part)
            if (match == null) {
                return RangeParseResult.Error("“$part” isn’t a page number or range.")
            }

            var a = match.groupValues[1].toIntOrNull() ?: return RangeParseResult.Error("“$part” is invalid.")
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
                return RangeParseResult.Error("This PDF has $totalPages page${if (totalPages == 1) "" else "s"}. “$part” is out of range.")
            }

            for (i in a..b) {
                out.add(i)
            }
        }

        return RangeParseResult.Success(out.sorted())
    }

    /**
     * Extracts [selectedPages] (1-indexed page numbers) from source PDF and saves as a new PDF to [outputFile].
     */
    suspend fun extractPages(
        context: Context,
        inputUri: Uri,
        selectedPages: List<Int>,
        outputFile: File
    ): ExtractResult = withContext(Dispatchers.IO) {
        if (selectedPages.isEmpty()) throw IllegalArgumentException("No pages selected for extraction.")

        var srcDoc: PDDocument? = null
        var destDoc: PDDocument? = null

        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw IllegalStateException("Cannot open input PDF file.")
            srcDoc = PDDocument.load(inputStream)
            destDoc = PDDocument()

            val sortedPages = selectedPages.distinct().sorted()

            for (p in sortedPages) {
                val pageIndex = p - 1
                if (pageIndex in 0 until srcDoc.numberOfPages) {
                    val page = srcDoc.getPage(pageIndex)
                    destDoc.importPage(page)
                }
            }

            FileOutputStream(outputFile).use { out ->
                destDoc.save(out)
            }

            ExtractResult(
                outputFile = outputFile,
                extractedPagesCount = sortedPages.size,
                fileSizeBytes = outputFile.length()
            )
        } finally {
            try {
                destDoc?.close()
            } catch (_: Exception) {}
            try {
                srcDoc?.close()
            } catch (_: Exception) {}
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
