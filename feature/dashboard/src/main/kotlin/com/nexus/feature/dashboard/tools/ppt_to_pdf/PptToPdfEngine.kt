package com.nexus.feature.dashboard.tools.ppt_to_pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFGroupShape
import org.apache.poi.hslf.usermodel.HSLFPictureShape
import org.apache.poi.hslf.usermodel.HSLFShape
import org.apache.poi.hslf.usermodel.HSLFSlide
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTable
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFAutoShape
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSimpleShape
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt

object PptToPdfEngine {

    private val OFF_PATTERN = Pattern.compile(
        "<(?:[a-zA-Z0-9_]+:)?off\\s+[^>]*?x=[\"'](-?\\d+)[\"'][^>]*?y=[\"'](-?\\d+)[\"']|<(?:[a-zA-Z0-9_]+:)?off\\s+[^>]*?y=[\"'](-?\\d+)[\"'][^>]*?x=[\"'](-?\\d+)[\"']",
        Pattern.CASE_INSENSITIVE
    )

    private val EXT_PATTERN = Pattern.compile(
        "<(?:[a-zA-Z0-9_]+:)?ext\\s+[^>]*?cx=[\"'](\\d+)[\"'][^>]*?cy=[\"'](\\d+)[\"']|<(?:[a-zA-Z0-9_]+:)?ext\\s+[^>]*?cy=[\"'](\\d+)[\"'][^>]*?cx=[\"'](\\d+)[\"']",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Formats bytes into human-readable string matching the HTML spec:
     * - < 1000 B: "N B"
     * - < 1 MB: "N.1 kB"
     * - >= 1 MB: "N.2 MB"
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format(Locale.US, "%.1f kB", bytes / 1000.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    /**
     * Reads display name, file size, and slide count of the selected PowerPoint presentation.
     */
    suspend fun readDocInfo(context: Context, uri: Uri): PptDocInfo = withContext(Dispatchers.IO) {
        var fileName = "Presentation.pptx"
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

        val baseName = fileName.replace(Regex("(?i)\\.(pptx|ppt)$"), "")
        val tempFile = copyUriToTempFile(context, uri, fileName)

        var slideCount = 1
        var slideW = 960f
        var slideH = 540f

        try {
            val isPptx = fileName.lowercase(Locale.US).endsWith(".pptx")
            if (isPptx) {
                FileInputStream(tempFile).use { fis ->
                    val pptx = XMLSlideShow(fis)
                    slideCount = pptx.slides.size.coerceAtLeast(1)
                    val dims = getPresentationPageSize(pptx)
                    slideW = dims.first
                    slideH = dims.second
                }
            } else {
                FileInputStream(tempFile).use { fis ->
                    val ppt = HSLFSlideShow(fis)
                    slideCount = ppt.slides.size.coerceAtLeast(1)
                    val dims = getPresentationPageSize(ppt)
                    slideW = dims.first
                    slideH = dims.second
                }
            }
        } catch (_: Exception) {
            // Keep defaults if failed
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        PptDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            slideCount = slideCount,
            baseName = baseName,
            slideWidth = slideW,
            slideHeight = slideH
        )
    }

    /**
     * Parses the presentation and renders high-fidelity slide items and thumbnails.
     */
    suspend fun loadSlideItems(
        context: Context,
        uri: Uri,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<PptSlideItem> = withContext(Dispatchers.IO) {
        var fileName = "Presentation.pptx"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                cursor.getString(nameIndex)?.let { fileName = it }
            }
        }

        val tempFile = copyUriToTempFile(context, uri, fileName)
        val isPptx = fileName.lowercase(Locale.US).endsWith(".pptx")
        val slideItems = mutableListOf<PptSlideItem>()

        try {
            if (isPptx) {
                FileInputStream(tempFile).use { fis ->
                    val pptx = XMLSlideShow(fis)
                    val total = pptx.slides.size
                    val dims = getPresentationPageSize(pptx)
                    val baseW = dims.first
                    val baseH = dims.second

                    for (i in 0 until total) {
                        val slide = pptx.slides[i]
                        val (fullBmp, thumbBmp) = renderPptxSlide(slide, baseW, baseH)
                        slideItems.add(
                            PptSlideItem(
                                id = i + 1,
                                slideNumber = i + 1,
                                title = slide.title ?: "Slide ${i + 1}",
                                thumbnail = thumbBmp,
                                fullBitmap = fullBmp,
                                width = baseW,
                                height = baseH
                            )
                        )
                        onProgress(i + 1, total)
                    }
                }
            } else {
                FileInputStream(tempFile).use { fis ->
                    val ppt = HSLFSlideShow(fis)
                    val total = ppt.slides.size
                    val dims = getPresentationPageSize(ppt)
                    val baseW = dims.first
                    val baseH = dims.second

                    for (i in 0 until total) {
                        val slide = ppt.slides[i]
                        val (fullBmp, thumbBmp) = renderHslfSlide(slide, baseW, baseH)
                        slideItems.add(
                            PptSlideItem(
                                id = i + 1,
                                slideNumber = i + 1,
                                title = slide.title ?: "Slide ${i + 1}",
                                thumbnail = thumbBmp,
                                fullBitmap = fullBmp,
                                width = baseW,
                                height = baseH
                            )
                        )
                        onProgress(i + 1, total)
                    }
                }
            }
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        slideItems
    }

    /**
     * Retrieves slide width and height in points via reflection to avoid java.awt.Dimension classpath issues.
     */
    private fun getPresentationPageSize(slideShow: Any): Pair<Float, Float> {
        return try {
            val method = slideShow.javaClass.getMethod("getPageSize")
            val dimension = method.invoke(slideShow)
            if (dimension != null) {
                val w = (dimension.javaClass.getMethod("getWidth").invoke(dimension) as Number).toFloat()
                val h = (dimension.javaClass.getMethod("getHeight").invoke(dimension) as Number).toFloat()
                if (w > 0f && h > 0f) Pair(w, h) else Pair(960f, 540f)
            } else {
                Pair(960f, 540f)
            }
        } catch (_: Throwable) {
            Pair(960f, 540f)
        }
    }

    /**
     * Extracts bounding box of a shape safely without direct java.awt.geom.Rectangle2D imports.
     */
    private fun getShapeBoundsSafe(shape: Any, slideW: Float, slideH: Float): RectF? {
        // Try OpenXML EMU extraction for XSLFShape
        if (shape is XSLFShape) {
            try {
                val xml = shape.xmlObject?.toString() ?: ""
                val bounds = extractXfrmBounds(xml, slideW.toDouble(), slideH.toDouble())
                if (bounds != null) {
                    return RectF(
                        bounds.left * slideW,
                        bounds.top * slideH,
                        bounds.right * slideW,
                        bounds.bottom * slideH
                    )
                }
            } catch (_: Throwable) {}
        }

        // Reflection fallback for getAnchor()
        return try {
            val method = shape.javaClass.getMethod("getAnchor")
            val anchor = method.invoke(shape) ?: return null
            val x = (anchor.javaClass.getMethod("getX").invoke(anchor) as Number).toFloat()
            val y = (anchor.javaClass.getMethod("getY").invoke(anchor) as Number).toFloat()
            val w = (anchor.javaClass.getMethod("getWidth").invoke(anchor) as Number).toFloat()
            val h = (anchor.javaClass.getMethod("getHeight").invoke(anchor) as Number).toFloat()
            RectF(x, y, x + w, y + h)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractXfrmBounds(xml: String, slideWPoints: Double, slideHPoints: Double): RectF? {
        if (xml.isEmpty()) return null
        val cleanXml = xml.replace(Regex("<(?:[a-zA-Z0-9_]+:)?grpSpPr(?:/>|>[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?grpSpPr>)"), "")
        val spPrContent = Regex("<(?:[a-zA-Z0-9_]+:)?spPr[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?spPr>").find(cleanXml)?.value
        val targetXml = spPrContent ?: cleanXml

        val offMatcher = OFF_PATTERN.matcher(targetXml)
        val extMatcher = EXT_PATTERN.matcher(targetXml)
        if (offMatcher.find() && extMatcher.find()) {
            val xStr = offMatcher.group(1) ?: offMatcher.group(4)
            val yStr = offMatcher.group(2) ?: offMatcher.group(3)
            val cxStr = extMatcher.group(1) ?: extMatcher.group(4)
            val cyStr = extMatcher.group(2) ?: extMatcher.group(3)

            val x = xStr?.toLongOrNull() ?: 0L
            val y = yStr?.toLongOrNull() ?: 0L
            val cx = cxStr?.toLongOrNull() ?: 0L
            val cy = cyStr?.toLongOrNull() ?: 0L

            if (cx > 0L && cy > 0L) {
                val slideWEmus = slideWPoints.coerceAtLeast(10.0) * 12700.0
                val slideHEmus = slideHPoints.coerceAtLeast(10.0) * 12700.0

                val left = (x.toDouble() / slideWEmus).toFloat().coerceIn(0f, 1f)
                val top = (y.toDouble() / slideHEmus).toFloat().coerceIn(0f, 1f)
                val width = (cx.toDouble() / slideWEmus).toFloat().coerceIn(0.001f, 1f)
                val height = (cy.toDouble() / slideHEmus).toFloat().coerceIn(0.001f, 1f)

                return RectF(left, top, (left + width).coerceAtMost(1f), (top + height).coerceAtMost(1f))
            }
        }
        return null
    }

    /**
     * Renders a PPTX (XSLF) slide into a pair of (Full Resolution Bitmap, 420px Thumbnail Bitmap).
     */
    private fun renderPptxSlide(
        slide: XSLFSlide,
        pageW: Float,
        pageH: Float
    ): Pair<Bitmap, Bitmap> {
        val targetW = 1280
        val targetH = (targetW * (pageH / pageW)).roundToInt().coerceIn(300, 3000)
        val fullBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)

        // 1. Background fill
        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), bgPaint)

        val scaleX = targetW.toFloat() / pageW
        val scaleY = targetH.toFloat() / pageH

        // 2. Render shapes
        val allShapes = mutableListOf<XSLFShape>()
        collectXslfShapes(slide.shapes, allShapes)

        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (shape in allShapes) {
            try {
                val rawBounds = getShapeBoundsSafe(shape, pageW, pageH) ?: continue
                val left = rawBounds.left * scaleX
                val top = rawBounds.top * scaleY
                val right = rawBounds.right * scaleX
                val bottom = rawBounds.bottom * scaleY
                val rect = RectF(left, top, right, bottom)

                when (shape) {
                    is XSLFPictureShape -> {
                        val picData = shape.pictureData
                        if (picData != null) {
                            val bytes = picData.data
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                canvas.drawBitmap(bmp, null, rect, null)
                            }
                        }
                    }
                    is XSLFTable -> {
                        renderXslfTable(shape, rect, canvas)
                    }
                    is XSLFTextShape -> {
                        renderXslfText(shape, rect, canvas, scaleX)
                    }
                    is XSLFAutoShape, is XSLFSimpleShape -> {
                        shapePaint.style = Paint.Style.FILL
                        shapePaint.color = 0xFFF1F5F9.toInt()
                        canvas.drawRoundRect(rect, 8f * scaleX, 8f * scaleY, shapePaint)
                        if (shape is XSLFTextShape) {
                            renderXslfText(shape, rect, canvas, scaleX)
                        }
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }

        // 3. Create 420px width thumbnail
        val thumbW = 420
        val thumbH = (thumbW * (targetH.toFloat() / targetW.toFloat())).roundToInt().coerceAtLeast(100)
        val thumbBitmap = Bitmap.createScaledBitmap(fullBitmap, thumbW, thumbH, true)

        return Pair(fullBitmap, thumbBitmap)
    }

    /**
     * Renders an older PPT (HSLF) slide into a pair of (Full Resolution Bitmap, Thumbnail Bitmap).
     */
    private fun renderHslfSlide(
        slide: HSLFSlide,
        pageW: Float,
        pageH: Float
    ): Pair<Bitmap, Bitmap> {
        val targetW = 1280
        val targetH = (targetW * (pageH / pageW)).roundToInt().coerceIn(300, 3000)
        val fullBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)

        canvas.drawColor(Color.WHITE)
        val scaleX = targetW.toFloat() / pageW
        val scaleY = targetH.toFloat() / pageH

        val allShapes = mutableListOf<HSLFShape>()
        collectHslfShapes(slide.shapes, allShapes)

        for (shape in allShapes) {
            try {
                val rawBounds = getShapeBoundsSafe(shape, pageW, pageH) ?: continue
                val left = rawBounds.left * scaleX
                val top = rawBounds.top * scaleY
                val right = rawBounds.right * scaleX
                val bottom = rawBounds.bottom * scaleY
                val rect = RectF(left, top, right, bottom)

                when (shape) {
                    is HSLFPictureShape -> {
                        val picData = shape.pictureData
                        if (picData != null) {
                            val bytes = picData.data
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                canvas.drawBitmap(bmp, null, rect, null)
                            }
                        }
                    }
                    is HSLFTextShape -> {
                        renderHslfText(shape, rect, canvas, scaleX)
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }

        val thumbW = 420
        val thumbH = (thumbW * (targetH.toFloat() / targetW.toFloat())).roundToInt().coerceAtLeast(100)
        val thumbBitmap = Bitmap.createScaledBitmap(fullBitmap, thumbW, thumbH, true)

        return Pair(fullBitmap, thumbBitmap)
    }

    private fun collectXslfShapes(shapes: List<XSLFShape>, result: MutableList<XSLFShape>) {
        for (shape in shapes) {
            if (shape is XSLFGroupShape) {
                collectXslfShapes(shape.shapes, result)
            } else {
                result.add(shape)
            }
        }
    }

    private fun collectHslfShapes(shapes: List<HSLFShape>, result: MutableList<HSLFShape>) {
        for (shape in shapes) {
            if (shape is HSLFGroupShape) {
                collectHslfShapes(shape.shapes, result)
            } else {
                result.add(shape)
            }
        }
    }

    private fun renderXslfText(shape: XSLFTextShape, bounds: RectF, canvas: Canvas, scale: Float) {
        val text = shape.text
        if (text.isNullOrBlank()) return

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (16f * scale).coerceIn(10f, 60f)
        }

        val availWidth = bounds.width().toInt().coerceAtLeast(20)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, availWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()

        canvas.save()
        canvas.translate(bounds.left + 4f * scale, bounds.top + 4f * scale)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun renderHslfText(shape: HSLFTextShape, bounds: RectF, canvas: Canvas, scale: Float) {
        val text = shape.text
        if (text.isNullOrBlank()) return

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (16f * scale).coerceIn(10f, 60f)
        }

        val availWidth = bounds.width().toInt().coerceAtLeast(20)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, availWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()

        canvas.save()
        canvas.translate(bounds.left + 4f * scale, bounds.top + 4f * scale)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun renderXslfTable(table: XSLFTable, bounds: RectF, canvas: Canvas) {
        val borderPaint = Paint().apply {
            color = 0xFFCBD5E1.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val cellBgPaint = Paint().apply {
            color = 0xFFF8FAFC.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(bounds, cellBgPaint)
        canvas.drawRect(bounds, borderPaint)
    }

    /**
     * Converts selected slides into a PDF file and writes directly to [outputStream].
     */
    suspend fun convertSlidesToPdf(
        context: Context,
        docInfo: PptDocInfo,
        slides: List<PptSlideItem>,
        selectedSlideNumbers: Set<Int>,
        outputStream: OutputStream,
        onProgress: (current: Int, total: Int) -> Unit
    ): PptToPdfResult = withContext(Dispatchers.IO) {
        val selectedSlides = slides
            .filter { it.slideNumber in selectedSlideNumbers }
            .sortedBy { it.slideNumber }

        if (selectedSlides.isEmpty()) {
            throw IllegalArgumentException("No slides selected for conversion.")
        }

        val document = PDDocument()
        val tempExportFile = File(context.cacheDir, "${docInfo.baseName}_converted_${System.currentTimeMillis()}.pdf")

        try {
            val total = selectedSlides.size
            for ((index, item) in selectedSlides.withIndex()) {
                val bmp = item.fullBitmap ?: item.thumbnail
                if (bmp != null) {
                    val wPt = item.width.coerceAtLeast(100f)
                    val hPt = item.height.coerceAtLeast(100f)
                    val pageRect = PDRectangle(wPt, hPt)
                    val page = PDPage(pageRect)
                    document.addPage(page)

                    val pdImage = JPEGFactory.createFromImage(document, bmp, 0.92f)
                    val cs = PDPageContentStream(document, page)
                    cs.drawImage(pdImage, 0f, 0f, wPt, hPt)
                    cs.close()
                }
                onProgress(index + 1, total)
            }

            // Save to outputStream
            document.save(outputStream)

            val exportedSize = tempExportFile.length()
            PptToPdfResult(
                outputFile = tempExportFile,
                convertedSlidesCount = selectedSlides.size,
                fileSizeBytes = exportedSize
            )
        } finally {
            try { document.close() } catch (_: Exception) {}
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri, originalName: String): File {
        val extension = if (originalName.lowercase(Locale.US).endsWith(".ppt")) ".ppt" else ".pptx"
        val tempFile = File(context.cacheDir, "ppt_import_${System.currentTimeMillis()}$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open presentation from Uri: $uri")
        return tempFile
    }
}
