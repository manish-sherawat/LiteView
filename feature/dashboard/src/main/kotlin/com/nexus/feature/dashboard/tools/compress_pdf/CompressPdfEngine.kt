package com.nexus.feature.dashboard.tools.compress_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object CompressPdfEngine {

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
     * Reads metadata and first-page thumbnail of the provided PDF.
     */
    suspend fun readDocInfo(context: Context, uri: Uri): CompressPdfDocInfo = withContext(Dispatchers.IO) {
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
        var thumbBitmap: Bitmap? = null

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    if (pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            baseW = page.width
                            baseH = page.height
                            // Generate high-fidelity thumbnail for the preview card
                            val thumbWidth = 480
                            val thumbHeight = (thumbWidth.toFloat() * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(120, 800)
                            val bmp = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
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
                        if (pageCount > 0) {
                            val page = doc.getPage(0)
                            baseW = page.mediaBox.width.toInt()
                            baseH = page.mediaBox.height.toInt()
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        CompressPdfDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            firstPageBitmap = thumbBitmap,
            basePageWidth = baseW,
            basePageHeight = baseH
        )
    }

    /**
     * Calculates size estimates for Low (20), Medium (50), and High (80) quality settings.
     */
    fun calculateEstimates(docInfo: CompressPdfDocInfo, keepTextSelectable: Boolean): CompressEstimates {
        val orig = docInfo.sizeBytes
        return if (keepTextSelectable) {
            CompressEstimates(
                lowBytes = (orig * 0.42).toLong().coerceIn(1024L, orig),
                medBytes = (orig * 0.62).toLong().coerceIn(2048L, orig),
                highBytes = (orig * 0.82).toLong().coerceIn(4096L, orig)
            )
        } else {
            val pages = docInfo.pageCount.coerceAtLeast(1)
            val lowEst = (pages * 45_000L).coerceAtLeast(10_000L)
            val medEst = (pages * 110_000L).coerceAtLeast(25_000L)
            val highEst = (pages * 240_000L).coerceAtLeast(50_000L)
            CompressEstimates(
                lowBytes = lowEst,
                medBytes = medEst,
                highBytes = highEst
            )
        }
    }

    /**
     * Interpolates smooth estimate value for any slider quality in [10..100].
     */
    fun estimateAtQuality(quality: Int, estimates: CompressEstimates): Long {
        val q = quality.coerceIn(10, 100)
        return when {
            q <= 20 -> {
                val ratio = (q - 10) / 10f
                ((estimates.lowBytes * 0.85f) + ratio * (estimates.lowBytes * 0.15f)).toLong()
            }
            q <= 50 -> {
                val ratio = (q - 20) / 30f
                (estimates.lowBytes + ratio * (estimates.medBytes - estimates.lowBytes)).toLong()
            }
            q <= 80 -> {
                val ratio = (q - 50) / 30f
                (estimates.medBytes + ratio * (estimates.highBytes - estimates.medBytes)).toLong()
            }
            else -> {
                val ratio = (q - 80) / 20f
                (estimates.highBytes + ratio * (estimates.highBytes * 0.25f)).toLong()
            }
        }.coerceAtLeast(500L)
    }

    /**
     * Renders a page preview at the selected compression configuration.
     * Simulates the exact raster quality and resolution if raster mode is selected.
     */
    suspend fun renderPreviewPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        config: CompressPdfConfig
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@use null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = if (config.keepTextSelectable) {
                            1.5f
                        } else {
                            (config.rasterDpi / 72f).coerceIn(0.6f, 2.5f)
                        }

                        val targetW = (page.width * scale).roundToInt().coerceIn(100, 3000)
                        val targetH = (page.height * scale).roundToInt().coerceIn(100, 3000)
                        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // If raster mode, simulate JPEG artifacting at chosen quality
                        if (!config.keepTextSelectable) {
                            val baos = ByteArrayOutputStream()
                            val jpegQuality = (config.jpegQualityFactor * 100).toInt().coerceIn(10, 100)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                            val jpegBytes = baos.toByteArray()
                            bitmap.recycle()
                            android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        } else {
                            bitmap
                        }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes the compression operation on a background thread.
     */
    suspend fun compressPdf(
        context: Context,
        docInfo: CompressPdfDocInfo,
        config: CompressPdfConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit
    ): CompressResult = withContext(Dispatchers.IO) {
        val key = "${config.keepTextSelectable}|${config.quality}"
        if (outputFile.exists()) {
            outputFile.delete()
        }

        if (config.keepTextSelectable) {
            compressWithSelectableText(context, docInfo, config, outputFile, onProgress)
        } else {
            compressRaster(context, docInfo, config, outputFile, onProgress)
        }

        val resultSize = outputFile.length()
        CompressResult(
            key = key,
            file = outputFile,
            sizeBytes = resultSize,
            originalSizeBytes = docInfo.sizeBytes
        )
    }

    /**
     * Mode 1: Keeps text, fonts, vector lines, and links selectable while recompressing
     * all embedded image streams using scaled JPEG compression.
     */
    private suspend fun compressWithSelectableText(
        context: Context,
        docInfo: CompressPdfDocInfo,
        config: CompressPdfConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit
    ) {
        var doc: PDDocument? = null
        try {
            val stream = context.contentResolver.openInputStream(docInfo.uri)
                ?: throw IllegalStateException("Cannot open PDF file stream")
            doc = PDDocument.load(stream)

            val totalPages = doc.numberOfPages
            for (pageNum in 0 until totalPages) {
                val page = doc.getPage(pageNum)
                val resources = page.resources
                if (resources != null) {
                    val xObjects = resources.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary
                    if (xObjects != null) {
                        for (name in resources.xObjectNames) {
                            try {
                                val xObj = resources.getXObject(name)
                                if (xObj is PDImageXObject) {
                                    val origBmp = xObj.image
                                    if (origBmp != null && origBmp.width >= 32 && origBmp.height >= 32) {
                                        val newW = (origBmp.width * config.scaleFactor).roundToInt().coerceAtLeast(16)
                                        val newH = (origBmp.height * config.scaleFactor).roundToInt().coerceAtLeast(16)
                                        val scaledBmp = Bitmap.createScaledBitmap(origBmp, newW, newH, true)

                                        val newImg = JPEGFactory.createFromImage(doc, scaledBmp, config.jpegQualityFactor)
                                        xObjects.setItem(name, newImg.cosObject)

                                        if (scaledBmp != origBmp) {
                                            scaledBmp.recycle()
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                // Skip individual corrupt image stream
                            }
                        }
                    }
                }
                onProgress(pageNum + 1, totalPages)
            }

            FileOutputStream(outputFile).use { fos ->
                doc.save(fos)
            }
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Mode 2: Rasterizes each page into high-efficiency JPEG images to achieve minimal file size.
     */
    private suspend fun compressRaster(
        context: Context,
        docInfo: CompressPdfDocInfo,
        config: CompressPdfConfig,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit
    ) {
        val newDoc = PDDocument()
        try {
            context.contentResolver.openFileDescriptor(docInfo.uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val totalPages = renderer.pageCount
                    val scale = (config.rasterDpi / 72f).coerceIn(0.5f, 3.5f)

                    for (pageNum in 0 until totalPages) {
                        renderer.openPage(pageNum).use { page ->
                            val targetW = (page.width * scale).roundToInt().coerceIn(100, 4000)
                            val targetH = (page.height * scale).roundToInt().coerceIn(100, 4000)

                            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            newDoc.addPage(pdPage)

                            val pdImage = JPEGFactory.createFromImage(newDoc, bitmap, config.jpegQualityFactor)
                            PDPageContentStream(newDoc, pdPage).use { cs ->
                                cs.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                            }

                            bitmap.recycle()
                        }
                        onProgress(pageNum + 1, totalPages)
                    }
                }
            }

            FileOutputStream(outputFile).use { fos ->
                newDoc.save(fos)
            }
        } finally {
            try {
                newDoc.close()
            } catch (_: Exception) {}
        }
    }
}
