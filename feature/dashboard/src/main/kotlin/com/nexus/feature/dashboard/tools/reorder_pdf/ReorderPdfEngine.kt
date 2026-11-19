package com.nexus.feature.dashboard.tools.reorder_pdf

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

object ReorderPdfEngine {

    /**
     * Reads metadata and page count of the selected PDF file.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): ReorderPdfDocInfo = withContext(Dispatchers.IO) {
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

        ReorderPdfDocInfo(
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
    suspend fun renderPageThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 180
    ): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(100, 400)
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
     * Renders a high-resolution bitmap of a given 0-indexed page for dialog preview.
     */
    suspend fun renderPageHighRes(
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
     * Reorders pages of the source PDF into [newOrder] (1-indexed page list) and writes to [outputFile].
     */
    suspend fun reorderPdf(
        context: Context,
        inputUri: Uri,
        newOrder: List<Int>,
        outputFile: File
    ): ReorderPdfResult = withContext(Dispatchers.IO) {
        if (newOrder.isEmpty()) throw IllegalArgumentException("Page order cannot be empty.")

        var srcDoc: PDDocument? = null
        var destDoc: PDDocument? = null

        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw IllegalStateException("Cannot open input PDF file.")
            srcDoc = PDDocument.load(inputStream)
            destDoc = PDDocument()

            for (p in newOrder) {
                val pageIndex = p - 1
                if (pageIndex in 0 until srcDoc.numberOfPages) {
                    val page = srcDoc.getPage(pageIndex)
                    destDoc.importPage(page)
                }
            }

            FileOutputStream(outputFile).use { out ->
                destDoc.save(out)
            }

            ReorderPdfResult(
                outputFile = outputFile,
                pageCount = destDoc.numberOfPages
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
     * Formats bytes into a human readable string (B, kB, MB).
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format("%.1f kB", bytes / 1000.0)
            else -> String.format("%.2f MB", bytes / 1_000_000.0)
        }
    }
}
