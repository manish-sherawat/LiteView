package com.nexus.feature.dashboard.tools.merge_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object MergePdfEngine {

    /**
     * Formats bytes into human-readable string matching the HTML spec:
     * - < 1000 B: "N B"
     * - < 1 MB: "N.1 kB"
     * - >= 1 MB: "N.2 MB"
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format(java.util.Locale.US, "%.1f kB", bytes / 1000.0)
            else -> String.format(java.util.Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    /**
     * Reads metadata and page-1 thumbnail of an individual PDF.
     */
    suspend fun readPdfItem(context: Context, uri: Uri, id: Long): MergePdfItem = withContext(Dispatchers.IO) {
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
        var thumbBitmap: Bitmap? = null

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    if (pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val targetW = 140
                            val targetH = (targetW.toFloat() * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(60, 400)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            thumbBitmap = bmp
                        }
                    }
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

        MergePdfItem(
            id = id,
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            thumbnail = thumbBitmap
        )
    }

    /**
     * Merges multiple PDF files into one combined PDF document.
     */
    suspend fun mergePdfs(
        context: Context,
        items: List<MergePdfItem>,
        outputFile: File,
        outputName: String,
        onProgress: (current: Int, total: Int) -> Unit
    ): MergeResult = withContext(Dispatchers.IO) {
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val merger = PDFMergerUtility()
        val streams = mutableListOf<InputStream>()
        val totalFiles = items.size

        try {
            FileOutputStream(outputFile).use { fos ->
                merger.destinationStream = fos

                for ((index, item) in items.withIndex()) {
                    context.contentResolver.openInputStream(item.uri)?.let { stream ->
                        streams.add(stream)
                        merger.addSource(stream)
                    }
                    onProgress(index + 1, totalFiles)
                }

                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            }
        } finally {
            streams.forEach {
                try { it.close() } catch (_: Exception) {}
            }
        }

        val totalPages = items.sumOf { it.pageCount }
        val finalSize = outputFile.length()

        MergeResult(
            file = outputFile,
            sizeBytes = finalSize,
            pageCount = totalPages,
            fileCount = totalFiles,
            outputName = outputName
        )
    }
}
