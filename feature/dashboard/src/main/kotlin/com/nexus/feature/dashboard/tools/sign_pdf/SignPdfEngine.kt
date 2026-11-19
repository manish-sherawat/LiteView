package com.nexus.feature.dashboard.tools.sign_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.sin

object SignPdfEngine {

    /**
     * Reads display name, size, and page count for the selected PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): SignPdfDocInfo = withContext(Dispatchers.IO) {
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

        SignPdfDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            baseName = baseName
        )
    }

    /**
     * Renders a specific 0-indexed page from the PDF into a crisp ARGB_8888 [Bitmap].
     * Returns Pair<Bitmap, Float> where Float is the page aspect ratio (width / height).
     */
    suspend fun renderPdfPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 1200
    ): Pair<Bitmap, Float> = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(400, 2400)
                    val renderH = (renderW / aspect).toInt().coerceAtLeast(400)

                    val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE) // Solid white page background

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    Pair(bitmap, aspect)
                }
            }
        }
    }

    /**
     * Trims transparent border pixels from a signature canvas or image [Bitmap].
     * Adds a tight 6-pixel padding around the visible ink bounding box.
     */
    fun trimBitmap(source: Bitmap): Bitmap? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha > 12) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < 0 || maxY < 0) return null // Nothing visible

        val pad = 6
        minX = (minX - pad).coerceAtLeast(0)
        minY = (minY - pad).coerceAtLeast(0)
        maxX = (maxX + pad).coerceAtMost(width - 1)
        maxY = (maxY + pad).coerceAtMost(height - 1)

        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1
        return Bitmap.createBitmap(source, minX, minY, cropW, cropH)
    }

    /**
     * Converts white/light backgrounds of photo/scanned signatures to transparent.
     * Uses luminance thresholding matching the HTML formula:
     * - lum >= 245: fully transparent (alpha 0)
     * - lum <= 200: fully opaque (original alpha)
     * - 200 < lum < 245: smooth alpha gradient falloff
     */
    fun removeWhiteBackground(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val origA = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (r + g + b) / 3

            val targetA = when {
                lum >= 245 -> 0
                lum <= 200 -> 255
                else -> (((245 - lum) / 45f) * 255f).toInt().coerceIn(0, 255)
            }
            val newA = minOf(origA, targetA)
            pixels[i] = (newA shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Generates a signature [Bitmap] from typed name and chosen script typeface.
     */
    fun generateTypedSignature(text: String, font: ScriptFont): Bitmap {
        val canvasW = 1000
        val canvasH = 300
        val bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val typeface = when (font) {
            ScriptFont.DANCING_SCRIPT -> Typeface.create("cursive", Typeface.ITALIC)
            ScriptFont.GREAT_VIBES -> Typeface.create("cursive", Typeface.NORMAL)
            ScriptFont.ALLURA -> Typeface.create("serif", Typeface.ITALIC)
            ScriptFont.CAVEAT -> Typeface.create("sans-serif", Typeface.BOLD_ITALIC)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }

        var fontSize = 170f
        paint.textSize = fontSize
        while (paint.measureText(text) > 940f && fontSize > 30f) {
            fontSize -= 6f
            paint.textSize = fontSize
        }

        val fontMetrics = paint.fontMetrics
        val centerY = canvasH / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(text, canvasW / 2f, centerY, paint)

        return bitmap
    }

    /**
     * Decodes an image from Uri and limits dimensions to a safe budget.
     */
    suspend fun decodeImageFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val maxDim = 1600
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW > maxDim || origH > maxDim) {
                val halfW = origW / 2
                val halfH = origH / 2
                while ((halfW / inSampleSize) >= maxDim && (halfH / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Embeds placed signature stamps into the PDF pages using PDFBox.
     * Accurately factors page rotation and PDF origin (lower-left).
     * Writes directly to the provided [OutputStream].
     */
    suspend fun embedSignatures(
        context: Context,
        inputUri: Uri,
        stamps: List<PlacedStamp>,
        signatures: Map<Long, Bitmap>,
        outputStream: OutputStream
    ): Int = withContext(Dispatchers.IO) {
        if (stamps.isEmpty()) throw IllegalArgumentException("No stamps placed.")

        var document: PDDocument? = null
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw IllegalStateException("Cannot open input PDF.")
            document = PDDocument.load(inputStream)

            // Cache embedded PNG objects in PDFBox
            val imageCache = mutableMapOf<Long, PDImageXObject>()
            for ((sigId, bmp) in signatures) {
                if (stamps.any { it.sigId == sigId }) {
                    val pdImage = LosslessFactory.createFromImage(document, bmp)
                    imageCache[sigId] = pdImage
                }
            }

            // Group stamps by 1-indexed page
            val stampsByPage = stamps.groupBy { it.page }

            for ((pageNumber, pageStamps) in stampsByPage) {
                val pageIndex = pageNumber - 1
                if (pageIndex !in 0 until document.numberOfPages) continue

                val page = document.getPage(pageIndex)
                val mediaBox = page.mediaBox ?: page.cropBox
                val R = ((page.rotation % 360) + 360) % 360

                val uw = mediaBox.width
                val uh = mediaBox.height
                val dw = if (R % 180 != 0) uh else uw
                val dh = if (R % 180 != 0) uw else uh

                val cs = PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                try {
                    for (stamp in pageStamps) {
                        val pdImage = imageCache[stamp.sigId] ?: continue
                        val Wp = stamp.widthRatio * dw
                        val Hp = Wp / stamp.aspectRatio
                        val L = stamp.x * dw
                        val T = stamp.y * dh

                        val x: Float
                        val y: Float
                        val rot = R

                        when (R) {
                            0 -> {
                                x = L
                                y = dh - T - Hp
                            }
                            90 -> {
                                x = T + Hp
                                y = L
                            }
                            180 -> {
                                x = dw - L
                                y = T + Hp
                            }
                            270 -> {
                                x = dh - T - Hp
                                y = dw - L
                            }
                            else -> {
                                x = L
                                y = dh - T - Hp
                            }
                        }

                        cs.saveGraphicsState()
                        if (rot == 0) {
                            cs.drawImage(pdImage, x, y, Wp, Hp)
                        } else {
                            val rad = Math.toRadians(rot.toDouble())
                            val cosVal = cos(rad).toFloat()
                            val sinVal = sin(rad).toFloat()
                            val matrix = Matrix(
                                cosVal * Wp, sinVal * Wp,
                                -sinVal * Hp, cosVal * Hp,
                                x, y
                            )
                            cs.drawImage(pdImage, matrix)
                        }
                        cs.restoreGraphicsState()
                    }
                } finally {
                    try {
                        cs.close()
                    } catch (_: Exception) {}
                }
            }

            document.save(outputStream)
            stamps.size
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Embeds placed signature stamps into the PDF and saves to a [File].
     */
    suspend fun embedSignatures(
        context: Context,
        inputUri: Uri,
        stamps: List<PlacedStamp>,
        signatures: Map<Long, Bitmap>,
        outputFile: File
    ): SignPdfResult = withContext(Dispatchers.IO) {
        val count = FileOutputStream(outputFile).use { out ->
            embedSignatures(context, inputUri, stamps, signatures, out)
        }
        SignPdfResult(
            outputFile = outputFile,
            outputFileName = outputFile.name,
            stampsCount = count
        )
    }
}
