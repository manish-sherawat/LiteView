package com.nexus.feature.dashboard.tools.image_to_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

object ImageToPdfEngine {

    /**
     * Decodes a memory-safe [Bitmap] from an image [Uri].
     */
    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 3000): Pair<Bitmap?, String> = withContext(Dispatchers.IO) {
        var fileName = "image.jpg"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                cursor.getString(nameIndex)?.let { fileName = it }
            }
        }

        try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            val origW = options.outWidth
            val origH = options.outHeight

            if (origW > maxDimension || origH > maxDimension) {
                val halfW = origW / 2
                val halfH = origH / 2
                while ((halfW / inSampleSize) >= maxDimension && (halfH / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            bitmap to fileName
        } catch (_: Exception) {
            null to fileName
        }
    }

    /**
     * Rotates a bitmap by [degrees].
     */
    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Calculates page boundaries and image placement in PDF points (72 pt per inch).
     */
    fun calculateGeometry(page: ImagePageItem, pageSize: PageSize, marginPt: Int): PageGeometry {
        val m = marginPt.toFloat()
        val effW = if (page.rotationDeg % 180 != 0) page.height.toFloat() else page.width.toFloat()
        val effH = if (page.rotationDeg % 180 != 0) page.width.toFloat() else page.height.toFloat()

        if (pageSize == PageSize.FIT_IMAGE && page.bitmap != null && effW > 0 && effH > 0) {
            var k = 0.75f
            val mx = max(effW, effH) * k
            if (mx > 1200f) {
                k *= 1200f / mx
            }
            val iw = effW * k
            val ih = effH * k
            return PageGeometry(
                pageWidthPt = iw + 2f * m,
                pageHeightPt = ih + 2f * m,
                imageXPt = m,
                imageYPt = m,
                imageWidthPt = iw,
                imageHeightPt = ih
            )
        }

        var W = if (pageSize == PageSize.LETTER) PageSize.LETTER.widthPt else PageSize.A4.widthPt
        var H = if (pageSize == PageSize.LETTER) PageSize.LETTER.heightPt else PageSize.A4.heightPt

        // Auto-orient page to match landscape images
        if (page.bitmap != null && effW > effH) {
            val temp = W
            W = H
            H = temp
        }

        if (page.bitmap == null || effW <= 0 || effH <= 0) {
            return PageGeometry(W, H, 0f, 0f, 0f, 0f)
        }

        val availableW = max(10f, W - 2f * m)
        val availableH = max(10f, H - 2f * m)
        val s = min(availableW / effW, availableH / effH)
        val iw = effW * s
        val ih = effH * s

        return PageGeometry(
            pageWidthPt = W,
            pageHeightPt = H,
            imageXPt = (W - iw) / 2f,
            imageYPt = (H - ih) / 2f,
            imageWidthPt = iw,
            imageHeightPt = ih
        )
    }

    /**
     * Generates a multi-page PDF document offline and writes directly to [outputStream].
     */
    suspend fun generatePdf(
        pages: List<ImagePageItem>,
        config: ImageToPdfConfig,
        outputStream: OutputStream,
        onProgress: (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val document = PDDocument()
        try {
            for (i in pages.indices) {
                val page = pages[i]
                val geom = calculateGeometry(page, config.pageSize, config.marginPt)
                val pdPage = PDPage(PDRectangle(geom.pageWidthPt, geom.pageHeightPt))
                document.addPage(pdPage)

                if (page.bitmap != null) {
                    val rotatedBmp = rotateBitmap(page.bitmap, page.rotationDeg)
                    val pdImage = if (rotatedBmp.hasAlpha()) {
                        LosslessFactory.createFromImage(document, rotatedBmp)
                    } else {
                        JPEGFactory.createFromImage(document, rotatedBmp, 0.92f)
                    }

                    PDPageContentStream(document, pdPage).use { cs ->
                        // PDF coordinate system starts at bottom-left:
                        val pdfY = geom.pageHeightPt - geom.imageYPt - geom.imageHeightPt
                        cs.drawImage(
                            pdImage,
                            geom.imageXPt,
                            pdfY,
                            geom.imageWidthPt,
                            geom.imageHeightPt
                        )
                    }
                }

                onProgress(i + 1, pages.size)
            }

            document.save(outputStream)
            pages.size
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }
    }
}
