package com.nexus.feature.dashboard.tools.repair_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.max

/**
 * 100% offline, on-device engine for repairing corrupted and damaged PDF files.
 * Rebuilds cross-reference tables, corrects damaged headers/trailers, and recovers
 * unreadable pages via rendering fallback.
 */
object RepairPdfEngine {

    /**
     * Formats byte size into human-readable string matching reference specification.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format(Locale.US, "%.1f kB", bytes / 1000.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    /**
     * Step 1: Cleans raw bytes by stripping stray leading bytes before %PDF-
     * and ensuring a terminating %%EOF marker exists.
     */
    fun cleanRawBytes(bytes: ByteArray): Pair<ByteArray, List<String>> {
        val notes = mutableListOf<String>()
        var b = bytes

        // Check for stray bytes before %PDF- in the first 4096 bytes
        val headSearchLimit = b.size.coerceAtMost(4096)
        val headString = String(b, 0, headSearchLimit, Charsets.ISO_8859_1)
        val pdfHeaderIndex = headString.indexOf("%PDF-")

        if (pdfHeaderIndex > 0) {
            b = b.copyOfRange(pdfHeaderIndex, b.size)
            notes.add("Removed $pdfHeaderIndex stray byte${if (pdfHeaderIndex == 1) "" else "s"} before the PDF header.")
        } else if (pdfHeaderIndex < 0) {
            notes.add("The PDF header was missing; the file was read in recovery mode.")
        }

        // Check for %%EOF in the last 1024 bytes
        val tailSearchStart = (b.size - 1024).coerceAtLeast(0)
        val tailString = String(b, tailSearchStart, b.size - tailSearchStart, Charsets.ISO_8859_1)

        if (!tailString.contains("%%EOF")) {
            val eofBytes = "\n%%EOF\n".toByteArray(Charsets.ISO_8859_1)
            val combined = ByteArray(b.size + eofBytes.size)
            System.arraycopy(b, 0, combined, 0, b.size)
            System.arraycopy(eofBytes, 0, combined, b.size, eofBytes.size)
            b = combined
            notes.add("Added a missing end-of-file marker (the file looked cut off).")
        }

        return Pair(b, notes)
    }

    /**
     * Inspects the input PDF file, determines metadata, checks password protection,
     * and attempts to render the first page for health diagnostics.
     */
    suspend fun readPdfDocInfo(
        context: Context,
        uri: Uri
    ): Triple<RepairPdfDocInfo, Bitmap?, ByteArray?> = withContext(Dispatchers.IO) {
        var fileName = "DamagedDocument.pdf"
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

        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read document contents.")

        if (rawBytes.size < 20) {
            throw IllegalStateException("The selected file is empty or too short.")
        }

        val (cleanedBytes, _) = cleanRawBytes(rawBytes)

        var pageCount: Int? = null
        var readable = false
        var isEncrypted = false

        // Check with PDFBox
        try {
            PDDocument.load(ByteArrayInputStream(cleanedBytes)).use { doc ->
                if (doc.isEncrypted) {
                    isEncrypted = true
                } else {
                    readable = true
                    pageCount = doc.numberOfPages
                }
            }
        } catch (_: Exception) {}

        if (isEncrypted) {
            throw IllegalStateException("This PDF is password-protected. Remove the password first, then repair it.")
        }

        // Try rendering first page thumbnail
        var thumbnail: Bitmap? = null
        var thumbOk = false
        val tempThumbFile = File(context.cacheDir, "repair_thumb_${System.currentTimeMillis()}.pdf")
        try {
            tempThumbFile.writeBytes(cleanedBytes)
            ParcelFileDescriptor.open(tempThumbFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageCount == null || pageCount == 0) {
                        pageCount = renderer.pageCount
                    }
                    if (renderer.pageCount > 0) {
                        readable = true
                        renderer.openPage(0).use { page ->
                            val renderW = 360
                            val aspect = page.width.toFloat() / page.height.toFloat()
                            val renderH = (renderW / aspect).toInt().coerceAtLeast(100)

                            val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            thumbnail = bitmap
                            thumbOk = true
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Rendering failed, keep thumbnail null
        } finally {
            if (tempThumbFile.exists()) tempThumbFile.delete()
        }

        val healthStatus = when {
            readable && thumbOk -> PdfHealthStatus.HEALTHY
            readable -> PdfHealthStatus.REPAIR_RECOMMENDED
            else -> PdfHealthStatus.REPAIR_NEEDED
        }

        val docInfo = RepairPdfDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = if (fileSize > 0) fileSize else rawBytes.size.toLong(),
            pageCount = pageCount,
            baseName = baseName,
            healthStatus = healthStatus
        )

        Triple(docInfo, thumbnail, cleanedBytes)
    }

    /**
     * Executes the comprehensive two-pass repair process.
     * Rebuilds cross-references, extracts undamaged pages, rasters damaged pages as
     * high-fidelity images if enabled, or falls back to blank spacers to preserve page numbers.
     */
    suspend fun repairPdf(
        context: Context,
        cleanedBytes: ByteArray,
        options: RepairPdfOptions,
        onProgress: (Float) -> Unit,
        outputStream: OutputStream
    ): RepairPdfResult = withContext(Dispatchers.IO) {
        val notes = mutableListOf<String>()

        // 1. Initial byte cleaning notes
        val (_, cleanNotes) = cleanRawBytes(cleanedBytes)
        notes.addAll(cleanNotes)

        var srcDoc: PDDocument? = null
        try {
            srcDoc = PDDocument.load(ByteArrayInputStream(cleanedBytes))
        } catch (_: Exception) {
            srcDoc = null
        }

        val tempRenderFile = File(context.cacheDir, "repair_render_${System.currentTimeMillis()}.pdf")
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            tempRenderFile.writeBytes(cleanedBytes)
            pfd = ParcelFileDescriptor.open(tempRenderFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
        } catch (_: Exception) {
            renderer = null
        }

        if (srcDoc == null && renderer == null) {
            pfd?.close()
            if (tempRenderFile.exists()) tempRenderFile.delete()
            throw IllegalStateException("Sorry, this file is too damaged to recover.")
        }

        if (srcDoc == null) {
            notes.add("The structure couldn’t be parsed directly, so pages were recovered by rendering.")
        } else {
            notes.add("Rebuilt the cross-reference table and page tree.")
        }

        val total = max(renderer?.pageCount ?: 0, srcDoc?.numberOfPages ?: 0)
        if (total <= 0) {
            srcDoc?.close()
            renderer?.close()
            pfd?.close()
            if (tempRenderFile.exists()) tempRenderFile.delete()
            throw IllegalStateException("No pages could be found in the document.")
        }

        val outDoc = PDDocument()
        var ok = 0
        var img = 0
        var lost = 0

        try {
            for (i in 0 until total) {
                var importedOk = false
                var rendersOk = false

                // Test if PdfRenderer can render this page
                if (renderer != null && i < renderer.pageCount) {
                    try {
                        renderer.openPage(i).use { _ ->
                            rendersOk = true
                        }
                    } catch (_: Exception) {
                        rendersOk = false
                    }
                }

                // Attempt to copy the page structure directly from PDFBox
                if (srcDoc != null && i < srcDoc.numberOfPages) {
                    try {
                        val srcPage = srcDoc.getPage(i)
                        outDoc.importPage(srcPage)
                        importedOk = true
                    } catch (_: Exception) {
                        importedOk = false
                    }
                }

                if (importedOk && (rendersOk || renderer == null)) {
                    ok++
                } else if (options.rebuildAsImages && rendersOk && renderer != null) {
                    // Page structure was damaged or unreadable as text, rebuild as high-res image
                    try {
                        renderer.openPage(i).use { page ->
                            val scale = (3200f / max(page.width, page.height).toFloat()).coerceAtMost(2.0f)
                            val targetW = max(1, (page.width * scale).toInt())
                            val targetH = max(1, (page.height * scale).toInt())

                            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            outDoc.addPage(pdPage)

                            val pdImage = JPEGFactory.createFromImage(outDoc, bitmap, 0.9f)
                            PDPageContentStream(outDoc, pdPage).use { cs ->
                                cs.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                            }
                            bitmap.recycle()
                            img++
                        }
                    } catch (_: Exception) {
                        if (importedOk) {
                            ok++
                        } else {
                            outDoc.addPage(PDPage(PDRectangle.A4))
                            lost++
                        }
                    }
                } else if (importedOk) {
                    ok++
                } else {
                    outDoc.addPage(PDPage(PDRectangle.A4))
                    lost++
                }

                onProgress((i + 1).toFloat() / total)
            }

            if (ok + img == 0) {
                throw IllegalStateException("Nothing could be recovered from this document.")
            }

            if (img > 0) {
                notes.add("$img page${if (img == 1) " was" else "s were"} unreadable as text and ${if (img == 1) "was" else "were"} rebuilt as image${if (img == 1) "" else "s"}.")
            }
            if (lost > 0) {
                notes.add("$lost page${if (lost == 1) " couldn’t be recovered and was" else " couldn’t be recovered and were"} replaced with a blank page so numbering stays correct.")
            }
            if (img == 0 && lost == 0 && notes.size <= 1) {
                notes.add("No page damage was found. A clean copy has been saved.")
            }

            // Save to intermediate buffer to calculate output size
            val tempOutputFile = File(context.cacheDir, "repair_out_${System.currentTimeMillis()}.pdf")
            try {
                FileOutputStream(tempOutputFile).use { fos ->
                    outDoc.save(fos)
                }
                val outSizeBytes = tempOutputFile.length()
                tempOutputFile.inputStream().use { fis ->
                    fis.copyTo(outputStream)
                }

                RepairPdfResult(
                    totalFound = total,
                    recoveredIntact = ok,
                    rebuiltAsImages = img,
                    unrecoverable = lost,
                    notes = notes,
                    outputFileSizeBytes = outSizeBytes
                )
            } finally {
                if (tempOutputFile.exists()) tempOutputFile.delete()
            }
        } finally {
            try { outDoc.close() } catch (_: Exception) {}
            try { srcDoc?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            if (tempRenderFile.exists()) tempRenderFile.delete()
        }
    }
}

