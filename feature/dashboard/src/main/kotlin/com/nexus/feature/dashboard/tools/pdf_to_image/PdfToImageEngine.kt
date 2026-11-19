package com.nexus.feature.dashboard.tools.pdf_to_image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object PdfToImageEngine {

    /**
     * Reads metadata and dimensions of the provided PDF.
     */
    suspend fun readDocInfo(context: Context, uri: Uri): PdfDocInfo = withContext(Dispatchers.IO) {
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

        var pageCount = 1
        var baseW = 595
        var baseH = 842

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    if (pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            baseW = page.width
                            baseH = page.height
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to PDFBox if native PdfRenderer encountered an edge case
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        pageCount = doc.numberOfPages
                        if (pageCount > 0) {
                            val page = doc.getPage(0)
                            baseW = page.mediaBox.width.toInt()
                            baseH = page.mediaBox.height.toInt()
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        PdfDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            basePageWidth = baseW,
            basePageHeight = baseH
        )
    }

    /**
     * Renders a specific page (0-indexed) to a high-quality [Bitmap] at the requested scale factor.
     */
    suspend fun renderPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        scale: Float
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@use null
                    renderer.openPage(pageIndex).use { page ->
                        val targetWidth = (page.width * scale).roundToInt().coerceIn(64, 8192)
                        val targetHeight = (page.height * scale).roundToInt().coerceIn(64, 8192)

                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE) // Ensure non-alpha JPEG safety
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Scans and extracts all embedded images from the PDF document.
     * Filters out sub-24px spacer and icon noise.
     */
    suspend fun extractEmbeddedImages(
        context: Context,
        uri: Uri
    ): List<ExtractedImageItem> = withContext(Dispatchers.IO) {
        val extracted = mutableListOf<ExtractedImageItem>()
        var doc: PDDocument? = null
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
            doc = PDDocument.load(stream)

            var idCounter = 1
            for (pageNum in 0 until doc.numberOfPages) {
                val page = doc.getPage(pageNum)
                val resources = page.resources ?: continue
                val seenNames = mutableSetOf<String>()

                for (name in resources.xObjectNames) {
                    val key = name.name
                    if (key in seenNames) continue
                    seenNames.add(key)

                    try {
                        val xObject = resources.getXObject(name)
                        if (xObject is PDImageXObject) {
                            val bmp = xObject.image
                            if (bmp != null && bmp.width >= 24 && bmp.height >= 24) {
                                extracted.add(
                                    ExtractedImageItem(
                                        id = idCounter++,
                                        pageNumber = pageNum + 1,
                                        bitmap = bmp,
                                        width = bmp.width,
                                        height = bmp.height
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Skip corrupted embedded image stream
                    }
                }
            }
        } catch (_: Exception) {
            // Partial results acceptable
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {}
        }
        extracted
    }

    /**
     * Parses page range string (e.g. "1-3, 5, 8") into a sorted list of 1-based page numbers.
     * Returns null if the syntax is invalid or out-of-bounds.
     */
    fun parsePageRange(rangeStr: String, maxPages: Int): List<Int>? {
        val cleaned = rangeStr.trim()
        if (cleaned.isEmpty()) return null
        val set = mutableSetOf<Int>()

        for (part in cleaned.split(",")) {
            val token = part.trim()
            if (token.isEmpty()) continue

            val dashIdx = token.indexOf('-')
            if (dashIdx != -1) {
                val startStr = token.substring(0, dashIdx).trim()
                val endStr = token.substring(dashIdx + 1).trim()
                val start = startStr.toIntOrNull() ?: return null
                val end = endStr.toIntOrNull() ?: return null
                if (start < 1 || end < 1 || start > maxPages || end > maxPages) return null
                val low = minOf(start, end)
                val high = maxOf(start, end)
                for (p in low..high) set.add(p)
            } else {
                val page = token.toIntOrNull() ?: return null
                if (page < 1 || page > maxPages) return null
                set.add(page)
            }
        }

        return if (set.isNotEmpty()) set.sorted() else null
    }

    /**
     * Compresses a [Bitmap] to the requested format and quality into a byte array.
     */
    fun compressBitmap(bitmap: Bitmap, format: ImageFormat, qualityPct: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        val compressFormat = when (format) {
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
        val q = if (format == ImageFormat.PNG) 100 else qualityPct.coerceIn(1, 100)
        bitmap.compress(compressFormat, q, stream)
        return stream.toByteArray()
    }

    /**
     * Exports a single image directly to an [OutputStream].
     */
    suspend fun exportSingleImage(
        bitmap: Bitmap,
        format: ImageFormat,
        qualityPct: Int,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        val bytes = compressBitmap(bitmap, format, qualityPct)
        outputStream.write(bytes)
        outputStream.flush()
    }

    /**
     * Exports multiple images into a standard .ZIP archive stream.
     */
    suspend fun exportImagesToZip(
        images: List<Pair<String, Bitmap>>,
        format: ImageFormat,
        qualityPct: Int,
        outputStream: OutputStream,
        onProgress: (current: Int, total: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        ZipOutputStream(outputStream).use { zipOut ->
            images.forEachIndexed { index, (name, bitmap) ->
                val bytes = compressBitmap(bitmap, format, qualityPct)
                val entry = ZipEntry(name)
                zipOut.putNextEntry(entry)
                zipOut.write(bytes)
                zipOut.closeEntry()
                onProgress(index + 1, images.size)
            }
            zipOut.finish()
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
