package com.nexus.feature.dashboard.tools.split_pdf

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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SplitPdfEngine {

    private const val MAX_FILES = 500

    /**
     * Reads display name, file size, and page count of chosen PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): SplitDocInfo = withContext(Dispatchers.IO) {
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

        SplitDocInfo(
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
     * Computes parts when splitting every [n] pages.
     */
    fun computeEveryParts(totalPages: Int, n: Int): List<SplitPart> {
        val step = n.coerceIn(1, totalPages)
        val parts = mutableListOf<SplitPart>()
        var idx = 1
        for (i in 1..totalPages step step) {
            val end = (i + step - 1).coerceAtMost(totalPages)
            parts.add(SplitPart(index = idx++, startPage = i, endPage = end))
        }
        return parts
    }

    /**
     * Parses custom page range string (e.g. "1-3, 4-6, 7") matching HTML logic.
     */
    fun parseRangeParts(input: String, totalPages: Int): SplitRangeResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return SplitRangeResult.Error("Type page ranges to see the files that will be created.")
        }

        val items = trimmed.split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) {
            return SplitRangeResult.Error("Type page ranges to see the files that will be created.")
        }

        val parts = mutableListOf<SplitPart>()
        val rangeRegex = Regex("^(\\d+)\\s*(?:[-–—]\\s*(\\d+))?$")
        var idx = 1

        for (item in items) {
            val match = rangeRegex.matchEntire(item)
            if (match == null) {
                return SplitRangeResult.Error("“$item” isn’t a page number or range.")
            }

            var a = match.groupValues[1].toIntOrNull()
                ?: return SplitRangeResult.Error("“$item” is invalid.")
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
                return SplitRangeResult.Error(
                    "This PDF has $totalPages page${if (totalPages == 1) "" else "s"}. “$item” is out of range."
                )
            }

            parts.add(SplitPart(index = idx++, startPage = a, endPage = b))
        }

        if (parts.size > MAX_FILES) {
            return SplitRangeResult.Error("That would create ${parts.size} files. The limit is $MAX_FILES.")
        }

        return SplitRangeResult.Success(parts)
    }

    /**
     * Splits source PDF into multiple PDF files. If more than one file is generated,
     * packages them into a clean ZIP archive in the Downloads directory.
     */
    suspend fun splitPdf(
        context: Context,
        docInfo: SplitDocInfo,
        parts: List<SplitPart>,
        onProgress: (Float) -> Unit
    ): SplitExportResult = withContext(Dispatchers.IO) {
        if (parts.isEmpty()) throw IllegalArgumentException("No parts defined for splitting.")

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir

        val tempDir = File(context.cacheDir, "split_${System.currentTimeMillis()}").apply { mkdirs() }
        val generatedFiles = mutableListOf<File>()

        var srcDoc: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(docInfo.uri)
                ?: throw IllegalStateException("Cannot open source PDF file.")
            srcDoc = PDDocument.load(inputStream)
            val totalParts = parts.size

            for ((i, part) in parts.withIndex()) {
                val outDoc = PDDocument()
                try {
                    for (p in part.startPage..part.endPage) {
                        val pageIdx = p - 1
                        if (pageIdx in 0 until srcDoc.numberOfPages) {
                            outDoc.importPage(srcDoc.getPage(pageIdx))
                        }
                    }

                    val name = if (part.startPage == part.endPage) {
                        "${docInfo.baseName}_page-${part.startPage}.pdf"
                    } else {
                        "${docInfo.baseName}_pages-${part.startPage}-${part.endPage}.pdf"
                    }

                    val partFile = File(tempDir, name)
                    FileOutputStream(partFile).use { fos ->
                        outDoc.save(fos)
                    }
                    generatedFiles.add(partFile)
                } finally {
                    try { outDoc.close() } catch (_: Exception) {}
                }

                val progress = ((i + 1).toFloat() / totalParts.toFloat()) * (if (totalParts > 1) 0.85f else 1.0f)
                onProgress(progress)
            }

            if (generatedFiles.size == 1) {
                // Single file: copy directly to Downloads
                val singleOut = File(downloadsDir, "${docInfo.baseName}-split.pdf")
                generatedFiles[0].copyTo(singleOut, overwrite = true)
                onProgress(1f)
                SplitExportResult(
                    outputFiles = listOf(singleOut),
                    zipFile = null,
                    totalParts = 1,
                    totalBytes = singleOut.length()
                )
            } else {
                // Multiple files: create ZIP file
                val zipOut = File(downloadsDir, "${docInfo.baseName}-split.zip")
                val buffer = ByteArray(8192)

                ZipOutputStream(FileOutputStream(zipOut)).use { zos ->
                    for ((idx, file) in generatedFiles.withIndex()) {
                        FileInputStream(file).use { fis ->
                            val zipEntry = ZipEntry(file.name)
                            zos.putNextEntry(zipEntry)
                            var len: Int
                            while (fis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                            zos.closeEntry()
                        }
                        val zipProgress = 0.85f + (0.15f * ((idx + 1).toFloat() / generatedFiles.size.toFloat()))
                        onProgress(zipProgress.coerceAtMost(1f))
                    }
                }

                // Also copy the individual files to a dedicated folder in Downloads so the user has direct access
                val partsFolder = File(downloadsDir, "${docInfo.baseName}-split-files").apply { mkdirs() }
                val persistentFiles = generatedFiles.map { tempFile ->
                    val dest = File(partsFolder, tempFile.name)
                    tempFile.copyTo(dest, overwrite = true)
                    dest
                }

                onProgress(1f)
                SplitExportResult(
                    outputFiles = persistentFiles,
                    zipFile = zipOut,
                    totalParts = generatedFiles.size,
                    totalBytes = zipOut.length()
                )
            }
        } finally {
            try { srcDoc?.close() } catch (_: Exception) {}
            // Clean up temporary files
            tempDir.deleteRecursively()
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

