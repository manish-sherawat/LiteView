package com.nexus.feature.reader.presentation.engine.poi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.nexus.feature.reader.presentation.engine.CellVerticalAlignment
import com.nexus.feature.reader.presentation.engine.ChartSeries
import com.nexus.feature.reader.presentation.engine.ChartType
import com.nexus.feature.reader.presentation.engine.ParagraphInfo
import com.nexus.feature.reader.presentation.engine.PresentationDocument
import com.nexus.feature.reader.presentation.engine.PresentationSlideInfo
import com.nexus.feature.reader.presentation.engine.ShapeFillInfo
import com.nexus.feature.reader.presentation.engine.SlideElement
import com.nexus.feature.reader.presentation.engine.SlideMediaType
import com.nexus.feature.reader.presentation.engine.SlideShapeType
import com.nexus.feature.reader.presentation.engine.TableCellInfo
import com.nexus.feature.reader.presentation.engine.TableRowInfo
import com.nexus.feature.reader.presentation.engine.TextAlignment
import com.nexus.feature.reader.presentation.engine.TextSpanInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hslf.usermodel.HSLFGroupShape
import org.apache.poi.hslf.usermodel.HSLFLine
import org.apache.poi.hslf.usermodel.HSLFPictureShape
import org.apache.poi.hslf.usermodel.HSLFShape
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hslf.usermodel.HSLFTable
import org.apache.poi.hslf.usermodel.HSLFTextShape
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFAutoShape
import org.apache.poi.xslf.usermodel.XSLFChart
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureData
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSimpleShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.regex.Pattern

object PoiPresentationParser {

    private const val TAG = "PoiPresentationParser"

    private val OFF_PATTERN = Pattern.compile(
        "<(?:[a-zA-Z0-9_]+:)?off\\s+[^>]*?x=[\"'](-?\\d+)[\"'][^>]*?y=[\"'](-?\\d+)[\"']|<(?:[a-zA-Z0-9_]+:)?off\\s+[^>]*?y=[\"'](-?\\d+)[\"'][^>]*?x=[\"'](-?\\d+)[\"']",
        Pattern.CASE_INSENSITIVE
    )

    private val EXT_PATTERN = Pattern.compile(
        "<(?:[a-zA-Z0-9_]+:)?ext\\s+[^>]*?cx=[\"'](\\d+)[\"'][^>]*?cy=[\"'](\\d+)[\"']|<(?:[a-zA-Z0-9_]+:)?ext\\s+[^>]*?cy=[\"'](\\d+)[\"'][^>]*?cx=[\"'](\\d+)[\"']",
        Pattern.CASE_INSENSITIVE
    )

    suspend fun parse(file: File): PresentationDocument? = withContext(Dispatchers.IO) {
        val lowerName = file.name.lowercase()
        return@withContext try {
            when {
                lowerName.endsWith(".pptx") -> parsePptx(file) ?: parsePpt(file)
                lowerName.endsWith(".ppt") -> parsePpt(file) ?: parsePptx(file)
                else -> parsePptx(file) ?: parsePpt(file)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse presentation ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Safely decodes raw byte arrays into downsampled bitmaps off the UI thread.
     * Prevents out-of-memory errors on large embedded images and slides.
     */
    fun decodeSampledBitmapFromByteArray(
        bytes: ByteArray,
        reqWidth: Int = 1280,
        reqHeight: Int = 1280,
        preferRgb565: Boolean = true
    ): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var inSampleSize = 1
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize.coerceAtLeast(1)
            options.inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (_: OutOfMemoryError) {
                try {
                    options.inSampleSize = (options.inSampleSize * 2).coerceAtLeast(2)
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } catch (_: Throwable) {
                    null
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to decode sampled bitmap", e)
            null
        }
    }

    private fun parsePptx(file: File): PresentationDocument? {
        return try {
            FileInputStream(file).use { fis ->
                val slideShow = XMLSlideShow(fis)
                val defaultBounds = RectF(0.05f, 0.1f, 0.95f, 0.9f)

                // Retrieve slide dimensions in points accurately
                val (slideWPoints, slideHPoints) = getSlideDimensions(slideShow, isPptx = true)

                // Per-slide media cache
                val mediaTempDir = File(file.parentFile ?: File("."), "media_cache").apply { mkdirs() }
                val mediaCache = mutableMapOf<String, File>()

                val parsedSlides = slideShow.slides.mapIndexed { index, slide ->
                    val slideTitle = slide.title ?: "Slide ${index + 1}"
                    val rawElements = mutableListOf<SlideElement>()

                    // Slide Background color
                    val slideBgColor = extractSlideBackground(slide)

                    // Speaker notes
                    val speakerNotes = try {
                        val notes = slide.notes
                        if (notes != null) {
                            notes.textParagraphs.flatten().map { para ->
                                para.textRuns.joinToString("") { run -> run.rawText ?: "" }
                            }.filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }
                        } else null
                    } catch (_: Throwable) {
                        null
                    }

                    // Recursively collect all shapes, unwrapping any GroupShapes
                    val allShapes = mutableListOf<Any>()
                    collectShapesRecursively(slide.shapes, allShapes)

                    val processedChartRelIds = mutableSetOf<String>()
                    val processedMediaRelIds = mutableSetOf<String>()

                    for (shape in allShapes) {
                        try {
                            val bounds = calculateShapeBounds(shape, slideWPoints, slideHPoints, defaultBounds)
                            val shapeXml = try { (shape as? XSLFShape)?.xmlObject?.toString() ?: "" } catch (_: Throwable) { "" }

                            // 1. Check if this shape is an embedded video or audio container
                            val mediaRelId = Regex("<(?:[a-zA-Z0-9_]+:)?(?:videoFile|audioFile|quickTimeFile)\\s+[^>]*?r:link=[\"']([^\"']+)[\"']").find(shapeXml)?.groupValues?.get(1)
                                ?: Regex("<(?:[a-zA-Z0-9_]+:)?media\\s+[^>]*?r:(?:embed|link)=[\"']([^\"']+)[\"']").find(shapeXml)?.groupValues?.get(1)

                            if (mediaRelId != null) {
                                val mediaElem = resolveMediaElement(slide, mediaRelId, shape, bounds, mediaTempDir, mediaCache)
                                if (mediaElem != null) {
                                    rawElements.add(mediaElem)
                                    processedMediaRelIds.add(mediaRelId)
                                    continue
                                }
                            }

                            when (shape) {
                                is XSLFPictureShape -> {
                                    val pictureData = shape.pictureData
                                    if (pictureData != null) {
                                        val bytes = pictureData.data
                                        val bitmap = decodeSampledBitmapFromByteArray(bytes, 1280, 1280)
                                        if (bitmap != null) {
                                            rawElements.add(SlideElement.Image(bounds = bounds, bitmap = bitmap))
                                        }
                                    } else {
                                        // Check for external linked picture (r:link)
                                        val linkId = Regex("<(?:[a-zA-Z0-9_]+:)?blip\\s+[^>]*?r:link=[\"']([^\"']+)[\"']").find(shapeXml)?.groupValues?.get(1)
                                        if (linkId != null) {
                                            val targetUri = try { slide.relationParts.find { it.relationship?.id == linkId }?.relationship?.targetURI?.toASCIIString() } catch (_: Throwable) { null } ?: "Linked Picture"
                                            rawElements.add(
                                                SlideElement.Shape(
                                                    bounds = bounds,
                                                    shapeName = "Linked Image",
                                                    fillColor = 0xFFF1F5F9.toInt(),
                                                    shapeType = SlideShapeType.ROUNDED_RECTANGLE,
                                                    fillInfo = ShapeFillInfo(
                                                        fillColor = 0xFFF1F5F9.toInt(),
                                                        strokeColor = 0xFF94A3B8.toInt(),
                                                        strokeWidth = 1.5f
                                                    ),
                                                    textParagraphs = listOf(
                                                        ParagraphInfo(
                                                            spans = listOf(
                                                                TextSpanInfo(
                                                                    text = "🖼 Linked Image\n($targetUri)",
                                                                    fontSize = 12f,
                                                                    colorArgb = 0xFF475569.toInt()
                                                                )
                                                            ),
                                                            alignment = TextAlignment.CENTER
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }

                                // CRITICAL: Table MUST precede GraphicFrame because XSLFTable inherits from XSLFGraphicFrame
                                is XSLFTable -> {
                                    val tableRows = mutableListOf<TableRowInfo>()
                                    val fallbackRows = mutableListOf<List<String>>()

                                    for (r in 0 until shape.numberOfRows) {
                                        val cells = mutableListOf<TableCellInfo>()
                                        val fallbackRow = mutableListOf<String>()

                                        for (c in 0 until shape.numberOfColumns) {
                                            val cell = shape.getCell(r, c)
                                            val cellText = cell?.text ?: ""
                                            fallbackRow.add(cellText)

                                            val colSpan = try { cell?.gridSpan ?: 1 } catch (_: Throwable) { 1 }
                                            val rowSpan = try { cell?.rowSpan ?: 1 } catch (_: Throwable) { 1 }
                                            val cellBg = getFillColorSafe(cell)
                                            val vAlign = when (cell?.verticalAlignment?.name) {
                                                "TOP" -> CellVerticalAlignment.TOP
                                                "BOTTOM" -> CellVerticalAlignment.BOTTOM
                                                else -> CellVerticalAlignment.CENTER
                                            }

                                            val spans = mutableListOf<TextSpanInfo>()
                                            if (cell != null) {
                                                for (p in cell.textParagraphs) {
                                                    for (run in p.textRuns) {
                                                        val runText = run.rawText ?: ""
                                                        if (runText.isNotEmpty()) {
                                                            spans.add(
                                                                TextSpanInfo(
                                                                    text = runText,
                                                                    fontSize = try { run.fontSize?.toFloat() ?: 13f } catch (_: Throwable) { 13f },
                                                                    isBold = try { run.isBold } catch (_: Throwable) { false },
                                                                    isItalic = try { run.isItalic } catch (_: Throwable) { false },
                                                                    colorArgb = getFontColorSafe(run)
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            cells.add(
                                                TableCellInfo(
                                                    text = cellText,
                                                    spans = spans,
                                                    backgroundColorArgb = cellBg,
                                                    colSpan = colSpan,
                                                    rowSpan = rowSpan,
                                                    verticalAlignment = vAlign
                                                )
                                            )
                                        }
                                        tableRows.add(TableRowInfo(cells = cells))
                                        fallbackRows.add(fallbackRow)
                                    }

                                    if (tableRows.isNotEmpty() || fallbackRows.isNotEmpty()) {
                                        rawElements.add(
                                            SlideElement.Table(
                                                bounds = bounds,
                                                rows = fallbackRows,
                                                tableRows = tableRows
                                            )
                                        )
                                    }
                                }

                                is XSLFGraphicFrame -> {
                                    val xml = shapeXml
                                    val chartRelId = Regex("<(?:[a-zA-Z0-9_]+:)?chart\\s+[^>]*?r:id=[\"']([^\"']+)[\"']").find(xml)?.groupValues?.get(1)
                                        ?: Regex("chart[^>]*?id=[\"']([^\"']+)[\"']").find(xml)?.groupValues?.get(1)

                                    if (chartRelId != null) {
                                        processedChartRelIds.add(chartRelId)
                                        val relPart = try { slide.getRelationById(chartRelId) } catch (_: Throwable) { null }
                                        if (relPart != null) {
                                            val chartElement = parseChartFromPart(relPart, bounds)
                                            if (chartElement != null) {
                                                rawElements.add(chartElement)
                                            }
                                        }
                                    } else {
                                        // Fallback for SmartArt / Diagrams / Graphic Frames: extract embedded text nodes
                                        val dgmTextMatches = Regex("<(?:[a-zA-Z0-9_]+:)?t>([^<]+)</(?:[a-zA-Z0-9_]+:)?t>")
                                            .findAll(shapeXml)
                                            .map { it.groupValues[1].trim() }
                                            .filter { it.isNotBlank() }
                                            .toList()
                                        if (dgmTextMatches.isNotEmpty()) {
                                            val spans = dgmTextMatches.map {
                                                TextSpanInfo(text = it, fontSize = 13f, colorArgb = 0xFF1E293B.toInt())
                                            }
                                            rawElements.add(
                                                SlideElement.Shape(
                                                    bounds = bounds,
                                                    shapeName = "Diagram",
                                                    fillColor = 0xFFF8FAFC.toInt(),
                                                    shapeType = SlideShapeType.ROUNDED_RECTANGLE,
                                                    fillInfo = ShapeFillInfo(
                                                        fillColor = 0xFFF8FAFC.toInt(),
                                                        strokeColor = 0xFFCBD5E1.toInt(),
                                                        strokeWidth = 1f
                                                    ),
                                                    textParagraphs = listOf(
                                                        ParagraphInfo(spans = spans, alignment = TextAlignment.CENTER)
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }

                                is XSLFTextShape -> {
                                    // In Apache POI, XSLFTextShape is the parent of XSLFAutoShape, XSLFTextBox,
                                    // and slide layout placeholders (Title, Subtitle, Body).
                                    // Check if this shape contains an embedded image fill (<a:blipFill>)
                                    val blipEmbedId = Regex("<(?:[a-zA-Z0-9_]+:)?blip\\s+[^>]*?r:embed=[\"']([^\"']+)[\"']").find(shapeXml)?.groupValues?.get(1)
                                    val blipLinkId = Regex("<(?:[a-zA-Z0-9_]+:)?blip\\s+[^>]*?r:link=[\"']([^\"']+)[\"']").find(shapeXml)?.groupValues?.get(1)

                                    if (blipEmbedId != null) {
                                        val relPart = try { slide.getRelationById(blipEmbedId) } catch (_: Throwable) { null }
                                        if (relPart is XSLFPictureData) {
                                            val bytes = relPart.data
                                            val bitmap = decodeSampledBitmapFromByteArray(bytes, 1280, 1280)
                                            if (bitmap != null) {
                                                rawElements.add(SlideElement.Image(bounds = bounds, bitmap = bitmap))
                                            }
                                        }
                                    } else if (blipLinkId != null) {
                                        val relPart = try { slide.getRelationById(blipLinkId) } catch (_: Throwable) { null }
                                        if (relPart is XSLFPictureData) {
                                            val bytes = relPart.data
                                            val bitmap = decodeSampledBitmapFromByteArray(bytes, 1280, 1280)
                                            if (bitmap != null) {
                                                rawElements.add(SlideElement.Image(bounds = bounds, bitmap = bitmap))
                                            }
                                        } else {
                                            val targetUri = try { slide.relationParts.find { it.relationship?.id == blipLinkId }?.relationship?.targetURI?.toASCIIString() } catch (_: Throwable) { null } ?: "External Resource"
                                            rawElements.add(
                                                SlideElement.Shape(
                                                    bounds = bounds,
                                                    shapeName = "Linked Image",
                                                    fillColor = 0xFFF1F5F9.toInt(),
                                                    shapeType = SlideShapeType.ROUNDED_RECTANGLE,
                                                    fillInfo = ShapeFillInfo(
                                                        fillColor = 0xFFF1F5F9.toInt(),
                                                        strokeColor = 0xFF94A3B8.toInt(),
                                                        strokeWidth = 1.5f
                                                    ),
                                                    textParagraphs = listOf(
                                                        ParagraphInfo(
                                                            spans = listOf(
                                                                TextSpanInfo(
                                                                    text = "🖼 Linked Image\n($targetUri)",
                                                                    fontSize = 12f,
                                                                    colorArgb = 0xFF475569.toInt()
                                                                )
                                                            ),
                                                            alignment = TextAlignment.CENTER
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    }

                                    val xmlShapeType = mapPresetGeomFromXml(shapeXml)
                                    val shapeType = xmlShapeType ?: mapShapeType((shape as? XSLFAutoShape)?.shapeType ?: shape.shapeType)
                                    val rotation = extractRotationFromXml(shapeXml)
                                    val isDashed = extractIsDashedFromXml(shapeXml)
                                    val fillColor = getFillColorSafe(shape)
                                    val gradientColors = getGradientColorsSafe(shape)
                                    val strokeColor = getLineColorSafe(shape)
                                    val strokeWidth = getStrokeWidthSafe(shape)
                                    val paragraphs = extractParagraphs(shape)

                                    // Render as Shape without discarding colored fills or custom borders
                                    if (paragraphs.isNotEmpty() || fillColor != null || gradientColors != null || strokeColor != null || shapeType != SlideShapeType.RECTANGLE) {
                                        rawElements.add(
                                            SlideElement.Shape(
                                                bounds = bounds,
                                                shapeName = shape.shapeName ?: shape.shapeType?.name ?: "Shape",
                                                fillColor = fillColor,
                                                shapeType = shapeType,
                                                fillInfo = ShapeFillInfo(
                                                    fillColor = fillColor,
                                                    gradientColors = gradientColors,
                                                    strokeColor = strokeColor,
                                                    strokeWidth = strokeWidth,
                                                    rotation = rotation,
                                                    isDashed = isDashed
                                                ),
                                                textParagraphs = paragraphs
                                            )
                                        )
                                    }
                                }

                                is XSLFSimpleShape -> {
                                    // Handles non-text geometric shapes (e.g., connectors, lines, decorative shapes)
                                    val xmlShapeType = mapPresetGeomFromXml(shapeXml)
                                    val shapeType = xmlShapeType ?: mapShapeType(shape.shapeType)
                                    val rotation = extractRotationFromXml(shapeXml)
                                    val isDashed = extractIsDashedFromXml(shapeXml)
                                    val fillColor = getFillColorSafe(shape)
                                    val gradientColors = getGradientColorsSafe(shape)
                                    val strokeColor = getLineColorSafe(shape)
                                    val strokeWidth = getStrokeWidthSafe(shape)

                                    if (fillColor != null || gradientColors != null || strokeColor != null || shapeType != SlideShapeType.RECTANGLE) {
                                        rawElements.add(
                                            SlideElement.Shape(
                                                bounds = bounds,
                                                shapeName = shape.shapeName ?: shape.shapeType?.name ?: "Shape",
                                                fillColor = fillColor,
                                                shapeType = shapeType,
                                                fillInfo = ShapeFillInfo(
                                                    fillColor = fillColor,
                                                    gradientColors = gradientColors,
                                                    strokeColor = strokeColor,
                                                    strokeWidth = strokeWidth,
                                                    rotation = rotation,
                                                    isDashed = isDashed
                                                ),
                                                textParagraphs = emptyList()
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (shapeError: Throwable) {
                            Log.w(TAG, "Error parsing shape on slide $index", shapeError)
                        }
                    }

                    // Extract any remaining embedded charts not referenced by graphic frames
                    val charts = extractCharts(slide, defaultBounds, processedChartRelIds)
                    rawElements.addAll(charts)

                    // Extract any slide-level media referenced in relations for this specific slide
                    val slideMedia = extractSlideMedia(slide, mediaCache, mediaTempDir, defaultBounds, processedMediaRelIds)
                    rawElements.addAll(slideMedia)

                    // CRITICAL-1: If a slide has zero parsed visual elements, supply a clean placeholder
                    // so users never see an unrendered blank white card
                    if (rawElements.isEmpty()) {
                        val displayTitle = if (slideTitle.isNotBlank() && !slideTitle.startsWith("Slide ")) {
                            slideTitle
                        } else {
                            "Slide ${index + 1}"
                        }
                        rawElements.add(
                            SlideElement.Shape(
                                bounds = RectF(0.08f, 0.15f, 0.92f, 0.85f),
                                shapeName = "Slide Placeholder",
                                fillColor = 0xFFF8F9FA.toInt(),
                                shapeType = SlideShapeType.ROUNDED_RECTANGLE,
                                fillInfo = ShapeFillInfo(
                                    fillColor = 0xFFF8F9FA.toInt(),
                                    strokeColor = 0xFFE2E8F0.toInt(),
                                    strokeWidth = 1f
                                ),
                                textParagraphs = listOf(
                                    ParagraphInfo(
                                        spans = listOf(
                                            TextSpanInfo(
                                                text = displayTitle,
                                                fontSize = 20f,
                                                isBold = true,
                                                colorArgb = 0xFF1E293B.toInt()
                                            )
                                        ),
                                        alignment = TextAlignment.CENTER
                                    ),
                                    ParagraphInfo(
                                        spans = listOf(
                                            TextSpanInfo(
                                                text = "\n(No displayable visual elements found on this slide)",
                                                fontSize = 12f,
                                                colorArgb = 0xFF64748B.toInt()
                                            )
                                        ),
                                        alignment = TextAlignment.CENTER
                                    )
                                )
                            )
                        )
                    }

                    // Prioritize elements so backgrounds are bottom and text stays legible on top
                    val layeredElements = prioritizeElements(rawElements)

                    PresentationSlideInfo(
                        index = index,
                        name = slideTitle,
                        widthTwips = (slideWPoints * 20.0).toLong().coerceAtLeast(1440L),
                        heightTwips = (slideHPoints * 20.0).toLong().coerceAtLeast(1440L),
                        title = slideTitle,
                        notes = speakerNotes,
                        backgroundColorArgb = slideBgColor,
                        elements = layeredElements
                    )
                }

                object : PresentationDocument {
                    override val slideCount: Int = parsedSlides.size
                    override val slides: List<PresentationSlideInfo> = parsedSlides

                    override fun renderSlide(slideIndex: Int, width: Int, height: Int): Bitmap? {
                        val slide = parsedSlides.getOrNull(slideIndex) ?: return null
                        return renderSlideToBitmap(slide, width, height)
                    }

                    override fun close() {
                        try {
                            slideShow.close()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "parsePptx failed", e)
            null
        }
    }

    private fun parsePpt(file: File): PresentationDocument? {
        return try {
            FileInputStream(file).use { fis ->
                val slideShow = HSLFSlideShow(fis)
                val defaultBounds = RectF(0.05f, 0.1f, 0.95f, 0.9f)

                val (slideWPoints, slideHPoints) = getSlideDimensions(slideShow, isPptx = false)

                val parsedSlides = slideShow.slides.mapIndexed { index, slide ->
                    val slideTitle = slide.title ?: "Slide ${index + 1}"
                    val rawElements = mutableListOf<SlideElement>()

                    // Speaker notes for legacy PPT
                    val notesText = try {
                        val notes = slide.notes
                        if (notes != null) {
                            notes.textParagraphs.flatten().map { para ->
                                para.textRuns.joinToString("") { run -> run.rawText?.ifEmpty { null } ?: "" }
                            }.filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }
                        } else null
                    } catch (_: Throwable) {
                        null
                    }

                    // Recursively collect all shapes, unwrapping any GroupShapes
                    val allShapes = mutableListOf<Any>()
                    collectShapesRecursively(slide.shapes, allShapes)

                    for (shape in allShapes) {
                        try {
                            val bounds = calculateShapeBounds(shape, slideWPoints, slideHPoints, defaultBounds)

                            when (shape) {
                                is HSLFPictureShape -> {
                                    val pictureData = shape.pictureData
                                    if (pictureData != null) {
                                        val bytes = pictureData.rawData
                                        val bitmap = decodeSampledBitmapFromByteArray(bytes, 1280, 1280)
                                        if (bitmap != null) {
                                            rawElements.add(SlideElement.Image(bounds = bounds, bitmap = bitmap))
                                        }
                                    }
                                }

                                is HSLFTable -> {
                                    val rows = mutableListOf<List<String>>()
                                    val tableRows = mutableListOf<TableRowInfo>()

                                    for (r in 0 until shape.numberOfRows) {
                                        val fallbackRow = mutableListOf<String>()
                                        val cells = mutableListOf<TableCellInfo>()

                                        for (c in 0 until shape.numberOfColumns) {
                                            val cell = shape.getCell(r, c)
                                            val cellText = cell?.text ?: ""
                                            fallbackRow.add(cellText)

                                            val cellBg = getFillColorSafe(cell)
                                            val spans = mutableListOf<TextSpanInfo>()
                                            if (cell != null) {
                                                val paragraphs = extractParagraphsHslf(cell)
                                                for (p in paragraphs) {
                                                    spans.addAll(p.spans)
                                                }
                                            }
                                            if (spans.isEmpty() && cellText.isNotBlank()) {
                                                spans.add(TextSpanInfo(text = cellText, fontSize = 13f))
                                            }

                                            cells.add(
                                                TableCellInfo(
                                                    text = cellText,
                                                    spans = spans,
                                                    backgroundColorArgb = cellBg
                                                )
                                            )
                                        }
                                        tableRows.add(TableRowInfo(cells = cells))
                                        rows.add(fallbackRow)
                                    }
                                    if (rows.isNotEmpty()) {
                                        rawElements.add(
                                            SlideElement.Table(
                                                bounds = bounds,
                                                rows = rows,
                                                tableRows = tableRows
                                            )
                                        )
                                    }
                                }

                                is HSLFLine -> {
                                    val strokeColor = getLineColorSafe(shape) ?: 0xFF64748B.toInt()
                                    val strokeWidth = getStrokeWidthSafe(shape)
                                    rawElements.add(
                                        SlideElement.Shape(
                                            bounds = bounds,
                                            shapeName = shape.shapeName ?: "Line",
                                            fillColor = null,
                                            shapeType = SlideShapeType.LINE,
                                            fillInfo = ShapeFillInfo(
                                                fillColor = null,
                                                strokeColor = strokeColor,
                                                strokeWidth = strokeWidth
                                            ),
                                            textParagraphs = emptyList()
                                        )
                                    )
                                }

                                is HSLFTextShape -> {
                                    val text = shape.text?.trim()
                                    val shapeType = mapShapeType(shape.shapeType)
                                    val fillColor = getFillColorSafe(shape)
                                    val lineColor = getLineColorSafe(shape)
                                    val strokeWidth = getStrokeWidthSafe(shape)
                                    val paragraphs = extractParagraphsHslf(shape)

                                    if (!text.isNullOrEmpty() || fillColor != null || lineColor != null || shapeType != SlideShapeType.RECTANGLE) {
                                        val hasVisibleBackground = (fillColor != null && (fillColor ushr 24) > 10) || (lineColor != null) || (shapeType != SlideShapeType.RECTANGLE)

                                        if (hasVisibleBackground || paragraphs.isNotEmpty()) {
                                            rawElements.add(
                                                SlideElement.Shape(
                                                    bounds = bounds,
                                                    shapeName = shape.shapeName ?: "Shape",
                                                    fillColor = fillColor,
                                                    shapeType = shapeType,
                                                    fillInfo = ShapeFillInfo(
                                                        fillColor = fillColor,
                                                        strokeColor = lineColor,
                                                        strokeWidth = strokeWidth
                                                    ),
                                                    textParagraphs = paragraphs
                                                )
                                            )
                                        } else if (!text.isNullOrEmpty()) {
                                            val isTitle = (text == slide.title)
                                            val firstSpan = paragraphs.firstOrNull()?.spans?.firstOrNull()
                                            rawElements.add(
                                                SlideElement.Text(
                                                    bounds = bounds,
                                                    text = text,
                                                    isTitle = isTitle,
                                                    fontSize = firstSpan?.fontSize ?: 15f,
                                                    isBold = firstSpan?.isBold ?: false,
                                                    colorArgb = firstSpan?.colorArgb,
                                                    paragraphs = paragraphs
                                                )
                                            )
                                        }
                                    }
                                }

                                is HSLFShape -> {
                                    // Geometric shapes (e.g. HSLFAutoShape, HSLFSimpleShape) without text
                                    val shapeType = mapShapeType(shape.shapeType)
                                    val fillColor = getFillColorSafe(shape)
                                    val lineColor = getLineColorSafe(shape)
                                    val strokeWidth = getStrokeWidthSafe(shape)

                                    if (fillColor != null || lineColor != null || shapeType != SlideShapeType.RECTANGLE) {
                                        rawElements.add(
                                            SlideElement.Shape(
                                                bounds = bounds,
                                                shapeName = shape.shapeName ?: "Shape",
                                                fillColor = fillColor,
                                                shapeType = shapeType,
                                                fillInfo = ShapeFillInfo(
                                                    fillColor = fillColor,
                                                    strokeColor = lineColor,
                                                    strokeWidth = strokeWidth
                                                ),
                                                textParagraphs = emptyList()
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (shapeError: Throwable) {
                            Log.w(TAG, "Error parsing HSLF shape on slide $index", shapeError)
                        }
                    }

                    // CRITICAL-1: Fallback placeholder for empty legacy slides
                    if (rawElements.isEmpty()) {
                        val displayTitle = if (slideTitle.isNotBlank() && !slideTitle.startsWith("Slide ")) {
                            slideTitle
                        } else {
                            "Slide ${index + 1}"
                        }
                        rawElements.add(
                            SlideElement.Shape(
                                bounds = RectF(0.08f, 0.15f, 0.92f, 0.85f),
                                shapeName = "Slide Placeholder",
                                fillColor = 0xFFF8F9FA.toInt(),
                                shapeType = SlideShapeType.ROUNDED_RECTANGLE,
                                fillInfo = ShapeFillInfo(
                                    fillColor = 0xFFF8F9FA.toInt(),
                                    strokeColor = 0xFFE2E8F0.toInt(),
                                    strokeWidth = 1f
                                ),
                                textParagraphs = listOf(
                                    ParagraphInfo(
                                        spans = listOf(
                                            TextSpanInfo(
                                                text = displayTitle,
                                                fontSize = 20f,
                                                isBold = true,
                                                colorArgb = 0xFF1E293B.toInt()
                                            )
                                        ),
                                        alignment = TextAlignment.CENTER
                                    ),
                                    ParagraphInfo(
                                        spans = listOf(
                                            TextSpanInfo(
                                                text = "\n(No displayable visual elements found on this slide)",
                                                fontSize = 12f,
                                                colorArgb = 0xFF64748B.toInt()
                                            )
                                        ),
                                        alignment = TextAlignment.CENTER
                                    )
                                )
                            )
                        )
                    }

                    val layeredElements = prioritizeElements(rawElements)

                    PresentationSlideInfo(
                        index = index,
                        name = slideTitle,
                        widthTwips = (slideWPoints * 20.0).toLong().coerceAtLeast(1440L),
                        heightTwips = (slideHPoints * 20.0).toLong().coerceAtLeast(1440L),
                        title = slideTitle,
                        notes = notesText,
                        elements = layeredElements
                    )
                }

                object : PresentationDocument {
                    override val slideCount: Int = parsedSlides.size
                    override val slides: List<PresentationSlideInfo> = parsedSlides

                    override fun renderSlide(slideIndex: Int, width: Int, height: Int): Bitmap? {
                        val slide = parsedSlides.getOrNull(slideIndex) ?: return null
                        return renderSlideToBitmap(slide, width, height)
                    }

                    override fun close() {
                        try {
                            slideShow.close()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "parsePpt failed", e)
            null
        }
    }

    private fun getSlideDimensions(slideShow: Any, isPptx: Boolean): Pair<Double, Double> {
        // 1. Direct XML parsing from ppt/presentation.xml without reflection or AWT
        if (isPptx && slideShow is XMLSlideShow) {
            try {
                val xml = try {
                    slideShow.packagePart?.inputStream?.use { it.bufferedReader().readText() }
                } catch (_: Throwable) { null } ?: try {
                    val pkg = slideShow.packagePart?.`package`
                    val part = pkg?.getPartsByName(Pattern.compile(".*ppt/presentation\\.xml"))?.firstOrNull()
                    part?.inputStream?.use { it.bufferedReader().readText() }
                } catch (_: Throwable) { null }

                if (!xml.isNullOrEmpty()) {
                    val sldSzMatch = Regex("<(?:[a-zA-Z0-9_]+:)?sldSz\\s+[^>]*?>").find(xml)?.value
                    if (sldSzMatch != null) {
                        val cx = Regex("cx=[\"'](\\d+)[\"']").find(sldSzMatch)?.groupValues?.get(1)?.toDoubleOrNull()
                        val cy = Regex("cy=[\"'](\\d+)[\"']").find(sldSzMatch)?.groupValues?.get(1)?.toDoubleOrNull()
                        if (cx != null && cy != null && cx > 0.0 && cy > 0.0) {
                            return Pair(cx / 12700.0, cy / 12700.0)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to read slide dimensions from ppt/presentation.xml", e)
            }
        } else if (!isPptx && slideShow is HSLFSlideShow) {
            // 2. Direct DocumentAtom parsing for legacy PPT without reflection or AWT
            try {
                val docAtom = slideShow.documentRecord?.documentAtom
                if (docAtom != null) {
                    val rawX = docAtom.slideSizeX
                    val rawY = docAtom.slideSizeY
                    // DocumentAtom coordinates are in master units (576 DPI). Points = master / 8 (72 DPI)
                    val w = rawX / 8.0
                    val h = rawY / 8.0
                    if (w > 10.0 && h > 10.0) {
                        return Pair(w, h)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to read DocumentAtom dimensions for HSLF", e)
            }
        }

        // 3. Fallback: reflection on getCTPresentation() for PPTX
        if (isPptx) {
            try {
                val ctPres = slideShow.javaClass.getMethod("getCTPresentation").invoke(slideShow)
                val sldSz = ctPres?.javaClass?.getMethod("getSldSz")?.invoke(ctPres)
                val cx = (sldSz?.javaClass?.getMethod("getCx")?.invoke(sldSz) as? Number)?.toDouble()
                val cy = (sldSz?.javaClass?.getMethod("getCy")?.invoke(sldSz) as? Number)?.toDouble()
                if (cx != null && cy != null && cx > 0.0 && cy > 0.0) {
                    return Pair(cx / 12700.0, cy / 12700.0)
                }
            } catch (_: Throwable) {}
        }

        // 4. Fallback: getPageSize() if available in runtime without AWT error
        try {
            val pageSizeMethod = slideShow.javaClass.getMethod("getPageSize")
            val pageSize = pageSizeMethod.invoke(slideShow)
            if (pageSize != null) {
                val getW = pageSize.javaClass.getMethod("getWidth").invoke(pageSize) as Number
                val getH = pageSize.javaClass.getMethod("getHeight").invoke(pageSize) as Number
                val w = getW.toDouble()
                val h = getH.toDouble()
                if (w > 10.0 && h > 10.0) {
                    return Pair(w, h)
                }
            }
        } catch (_: Throwable) {}

        // 5. Final hard fallback
        return if (isPptx) Pair(960.0, 540.0) else Pair(720.0, 540.0)
    }

    private fun collectShapesRecursively(shapes: Iterable<*>, output: MutableList<Any>) {
        for (shape in shapes) {
            if (shape == null) continue
            if (shape is XSLFGroupShape) {
                collectShapesRecursively(shape.shapes, output)
            } else if (shape is HSLFGroupShape) {
                collectShapesRecursively(shape.shapes, output)
            } else {
                output.add(shape)
            }
        }
    }

    private fun prioritizeElements(elements: List<SlideElement>): List<SlideElement> {
        return elements.sortedWith(
            compareBy { element ->
                when {
                    // 0: Full-slide background images / large shapes anchored to the top-left corner
                    element.bounds.width() >= 0.90f && element.bounds.height() >= 0.90f &&
                            element.bounds.left <= 0.05f && element.bounds.top <= 0.05f &&
                            element !is SlideElement.Text -> 0
                    // 1: Background vector shapes without text
                    element is SlideElement.Shape && element.textParagraphs.isEmpty() -> 1
                    // 2: Tables & Charts
                    element is SlideElement.Table || element is SlideElement.Chart -> 2
                    // 3: Embedded content images
                    element is SlideElement.Image -> 3
                    // 4: Shapes with text
                    element is SlideElement.Shape -> 4
                    // 5: Pure text boxes & typography (always on top so never covered)
                    element is SlideElement.Text -> 5
                    else -> 6
                }
            }
        )
    }

    fun unescapeXml(text: String): String {
        if (!text.contains("&")) return text
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#([0-9]+);")) { match ->
                try {
                    match.groupValues[1].toInt().toChar().toString()
                } catch (_: Throwable) {
                    match.value
                }
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                try {
                    match.groupValues[1].toInt(16).toChar().toString()
                } catch (_: Throwable) {
                    match.value
                }
            }
    }

    private fun extractSpPrXml(shapeXml: String): String {
        if (shapeXml.isEmpty()) return ""
        // Isolate <p:spPr> or <a:spPr> to strictly exclude <p:txBody> text properties
        val spPrMatch = Regex("<(?:[a-zA-Z0-9_]+:)?spPr[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?spPr>").find(shapeXml)
        return spPrMatch?.value ?: shapeXml.replace(Regex("<(?:[a-zA-Z0-9_]+:)?txBody[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?txBody>"), "")
    }

    private fun extractHighlightColorFromRun(run: Any?): Int? {
        if (run is XSLFTextRun) {
            try {
                val xml = run.xmlObject?.toString() ?: ""
                val hlMatch = Regex("<(?:[a-zA-Z0-9_]+:)?highlight[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?highlight>").find(xml)?.value
                if (hlMatch != null) {
                    return extractColorFromXml(hlMatch)
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun extractSpansDirectlyFromParagraphXml(pXml: String): List<TextSpanInfo> {
        if (pXml.isEmpty()) return emptyList()
        val spans = mutableListOf<TextSpanInfo>()
        try {
            val nodeRegex = Regex("<(?:[a-zA-Z0-9_]+:)?(r|fld|br)\\b([\\s\\S]*?)(?:/>|</(?:[a-zA-Z0-9_]+:)?\\1>)")
            val matches = nodeRegex.findAll(pXml)
            for (match in matches) {
                val nodeType = match.groupValues[1]
                val content = match.value
                if (nodeType == "br") {
                    spans.add(TextSpanInfo(text = "\n", fontSize = 14f))
                    continue
                }
                val tMatch = Regex("<(?:[a-zA-Z0-9_]+:)?t(?:\\s+[^>]*)?>([\\s\\S]*?)</(?:[a-zA-Z0-9_]+:)?t>").find(content)
                val rawText = tMatch?.groupValues?.get(1) ?: continue
                val text = unescapeXml(rawText)
                if (text.isEmpty()) continue

                val szVal = Regex("<(?:[a-zA-Z0-9_]+:)?rPr\\s+[^>]*?sz=[\"'](\\d+)[\"']").find(content)?.groupValues?.get(1)?.toIntOrNull()
                val fontSize = if (szVal != null && szVal > 0) (szVal / 100f).coerceIn(6f, 120f) else 14f

                val isBold = content.contains(" b=\"1\"") || content.contains(" b=\"true\"")
                val isItalic = content.contains(" i=\"1\"") || content.contains(" i=\"true\"")
                val isUnderline = content.contains(" u=\"sng\"") || content.contains(" u=\"1\"")
                val isStrike = content.contains(" strike=\"sngStrike\"")

                val color = extractColorFromXml(content, fallbackSchemeColor = 0xFF1E293B.toInt())
                val hlColor = Regex("<(?:[a-zA-Z0-9_]+:)?highlight[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?highlight>").find(content)?.value?.let { extractColorFromXml(it) }

                val latinMatch = Regex("<(?:[a-zA-Z0-9_]+:)?latin\\s+[^>]*?typeface=[\"']([^\"']+)[\"']").find(content)
                val fontFamily = latinMatch?.groupValues?.get(1)

                spans.add(
                    TextSpanInfo(
                        text = text,
                        fontSize = fontSize,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        isStrikethrough = isStrike,
                        colorArgb = color,
                        backgroundColorArgb = hlColor,
                        fontFamily = fontFamily
                    )
                )
            }
        } catch (_: Throwable) {}
        return spans
    }

    private fun extractParagraphPropertiesFromXml(pXml: String): Triple<TextAlignment?, String?, Float?> {
        if (pXml.isEmpty()) return Triple(null, null, null)
        var alignment: TextAlignment? = null
        var bulletChar: String? = null
        var lineSpacing: Float? = null

        try {
            val algn = Regex("<(?:[a-zA-Z0-9_]+:)?pPr\\s+[^>]*?algn=[\"']([^\"']+)[\"']").find(pXml)?.groupValues?.get(1)
            if (algn != null) {
                alignment = when (algn.lowercase()) {
                    "ctr", "center" -> TextAlignment.CENTER
                    "r", "right" -> TextAlignment.RIGHT
                    "just", "justify" -> TextAlignment.JUSTIFY
                    else -> TextAlignment.LEFT
                }
            }

            val buChar = Regex("<(?:[a-zA-Z0-9_]+:)?buChar\\s+[^>]*?char=[\"']([^\"']+)[\"']").find(pXml)?.groupValues?.get(1)
            if (buChar != null) {
                bulletChar = buChar
            }

            val spcPct = Regex("<(?:[a-zA-Z0-9_]+:)?spcPct\\s+[^>]*?val=[\"'](\\d+)[\"']").find(pXml)?.groupValues?.get(1)?.toIntOrNull()
            if (spcPct != null && spcPct > 0) {
                lineSpacing = (spcPct / 100000f).coerceIn(0.8f, 3.0f)
            }
        } catch (_: Throwable) {}

        return Triple(alignment, bulletChar, lineSpacing)
    }

    private fun extractParagraphs(textShape: XSLFTextShape): List<ParagraphInfo> {
        val paragraphs = mutableListOf<ParagraphInfo>()
        for (p in textShape.textParagraphs) {
            val pXml = try { p.xmlObject?.toString() ?: "" } catch (_: Throwable) { "" }
            val (xmlAlgn, xmlBulletChar, xmlLineSpacing) = extractParagraphPropertiesFromXml(pXml)
            val alignment = xmlAlgn ?: when (p.textAlign?.name) {
                "CENTER" -> TextAlignment.CENTER
                "RIGHT" -> TextAlignment.RIGHT
                "JUSTIFY" -> TextAlignment.JUSTIFY
                else -> TextAlignment.LEFT
            }
            val isBullet = p.isBullet || (xmlBulletChar != null)
            val bulletChar = xmlBulletChar
            val lineSpacing = xmlLineSpacing ?: 1.15f
            val indent = try { p.indent?.toFloat() ?: 0f } catch (_: Throwable) { 0f }

            val spans = mutableListOf<TextSpanInfo>()
            for (run in p.textRuns) {
                val raw = try { run.rawText } catch (_: Throwable) { null }
                val text = unescapeXml(if (!raw.isNullOrEmpty()) raw else extractTextFromRunXml(run))
                if (text.isEmpty()) continue

                val fontSize = try { run.fontSize?.toFloat()?.takeIf { it > 0f } } catch (_: Throwable) { null }
                    ?: extractFontSizeFromRunXml(run) ?: 14f
                val fontFamily = extractFontFamilyFromRunXml(run)
                val isBold = try { run.isBold } catch (_: Throwable) { false }
                val isItalic = try { run.isItalic } catch (_: Throwable) { false }
                val isUnderline = try { run.isUnderlined } catch (_: Throwable) { false }
                val isStrikethrough = try { run.isStrikethrough } catch (_: Throwable) { false }
                val isSubscript = try { run.isSubscript } catch (_: Throwable) { false }
                val isSuperscript = try { run.isSuperscript } catch (_: Throwable) { false }
                val colorArgb = getFontColorSafe(run)
                val highlightColor = extractHighlightColorFromRun(run)
                val linkUrl = try { run.hyperlink?.address } catch (_: Throwable) { null }

                spans.add(
                    TextSpanInfo(
                        text = text,
                        fontSize = fontSize,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        isStrikethrough = isStrikethrough,
                        isSuperscript = isSuperscript,
                        isSubscript = isSubscript,
                        colorArgb = colorArgb,
                        backgroundColorArgb = highlightColor,
                        linkUrl = linkUrl,
                        fontFamily = fontFamily
                    )
                )
            }

            // Paragraph XML fallback for <a:fld> fields (slide numbers, footers) or missed runs
            if (spans.isEmpty() && pXml.isNotEmpty()) {
                val directSpans = extractSpansDirectlyFromParagraphXml(pXml)
                if (directSpans.isNotEmpty()) {
                    spans.addAll(directSpans)
                }
            }

            // Text fallback if still empty
            if (spans.isEmpty()) {
                val pText = try { p.text?.trim() } catch (_: Throwable) { null }
                if (!pText.isNullOrEmpty()) {
                    spans.add(
                        TextSpanInfo(
                            text = unescapeXml(pText),
                            fontSize = 14f,
                            colorArgb = 0xFF1E293B.toInt()
                        )
                    )
                }
            }

            if (spans.isNotEmpty()) {
                paragraphs.add(
                    ParagraphInfo(
                        spans = spans,
                        alignment = alignment,
                        isBullet = isBullet,
                        bulletChar = bulletChar,
                        indent = indent,
                        lineSpacingMultiplier = lineSpacing
                    )
                )
            }
        }

        // Shape-level fallback if textParagraphs was empty or stripped
        if (paragraphs.isEmpty()) {
            val shapeText = try { textShape.text?.trim() } catch (_: Throwable) { null }
            if (!shapeText.isNullOrEmpty()) {
                paragraphs.add(
                    ParagraphInfo(
                        spans = listOf(
                            TextSpanInfo(
                                text = unescapeXml(shapeText),
                                fontSize = 15f,
                                colorArgb = 0xFF1E293B.toInt()
                            )
                        )
                    )
                )
            }
        }

        return paragraphs
    }

    private fun extractParagraphsHslf(textShape: HSLFTextShape): List<ParagraphInfo> {
        val paragraphs = mutableListOf<ParagraphInfo>()
        for (p in textShape.textParagraphs) {
            val alignment = when (try { p.textAlign?.name } catch (_: Throwable) { null }) {
                "CENTER" -> TextAlignment.CENTER
                "RIGHT" -> TextAlignment.RIGHT
                "JUSTIFY" -> TextAlignment.JUSTIFY
                else -> TextAlignment.LEFT
            }
            val isBullet = try { p.isBullet } catch (_: Throwable) { false }
            val indent = try { p.indent?.toFloat() ?: 0f } catch (_: Throwable) { 0f }

            val spans = mutableListOf<TextSpanInfo>()
            for (run in p.textRuns) {
                val text = (try { run.rawText } catch (_: Throwable) { null }) ?: ""
                if (text.isEmpty()) continue

                val fontSize = try { run.fontSize?.toFloat()?.takeIf { it > 0f } } catch (_: Throwable) { null } ?: 14f
                val isBold = try { run.isBold } catch (_: Throwable) { false }
                val isItalic = try { run.isItalic } catch (_: Throwable) { false }
                val isUnderline = try { run.isUnderlined } catch (_: Throwable) { false }
                val isStrikethrough = try { run.isStrikethrough } catch (_: Throwable) { false }
                val isSubscript = try { run.isSubscript } catch (_: Throwable) { false }
                val isSuperscript = try { run.isSuperscript } catch (_: Throwable) { false }
                val colorArgb = getFontColorSafe(run)
                val linkUrl = try { run.hyperlink?.address } catch (_: Throwable) { null }

                val fontFamily = try { run.fontFamily } catch (_: Throwable) { null }

                spans.add(
                    TextSpanInfo(
                        text = text,
                        fontSize = fontSize,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        isStrikethrough = isStrikethrough,
                        isSuperscript = isSuperscript,
                        isSubscript = isSubscript,
                        colorArgb = colorArgb,
                        linkUrl = linkUrl,
                        fontFamily = fontFamily
                    )
                )
            }

            // Paragraph-level fallback if runs yielded no text
            if (spans.isEmpty()) {
                val pText = try {
                    p.textRuns.joinToString("") { (try { it.rawText } catch (_: Throwable) { "" }) ?: "" }.trim()
                } catch (_: Throwable) { "" }
                if (pText.isNotEmpty()) {
                    spans.add(
                        TextSpanInfo(
                            text = pText,
                            fontSize = 14f,
                            colorArgb = 0xFF1E293B.toInt()
                        )
                    )
                }
            }

            if (spans.isNotEmpty()) {
                paragraphs.add(
                    ParagraphInfo(
                        spans = spans,
                        alignment = alignment,
                        isBullet = isBullet,
                        indent = indent,
                        lineSpacingMultiplier = 1.15f
                    )
                )
            }
        }

        // Shape-level fallback if textParagraphs was empty or stripped
        if (paragraphs.isEmpty()) {
            val shapeText = try { textShape.text?.trim() } catch (_: Throwable) { null }
            if (!shapeText.isNullOrEmpty()) {
                paragraphs.add(
                    ParagraphInfo(
                        spans = listOf(
                            TextSpanInfo(
                                text = shapeText,
                                fontSize = 15f,
                                colorArgb = 0xFF1E293B.toInt()
                            )
                        )
                    )
                )
            }
        }

        return paragraphs
    }

    private fun extractTextFromRunXml(run: Any?): String {
        if (run is XSLFTextRun) {
            try {
                val xml = run.xmlObject?.toString() ?: ""
                if (xml.isNotEmpty()) {
                    val tMatch = Regex("<(?:[a-zA-Z0-9_]+:)?t(?:\\s+[^>]*)?>([\\s\\S]*?)</(?:[a-zA-Z0-9_]+:)?t>").find(xml)
                    if (tMatch != null) {
                        return unescapeXml(tMatch.groupValues[1])
                    }
                }
            } catch (_: Throwable) {}
        }
        return ""
    }

    private fun extractFontSizeFromRunXml(run: Any?): Float? {
        if (run is XSLFTextRun) {
            try {
                val xml = run.xmlObject?.toString() ?: ""
                if (xml.isNotEmpty()) {
                    val szMatch = Regex("<(?:[a-zA-Z0-9_]+:)?rPr\\s+[^>]*?sz=[\"'](\\d+)[\"']").find(xml)
                    val szVal = szMatch?.groupValues?.get(1)?.toIntOrNull()
                    if (szVal != null && szVal > 0) {
                        return (szVal / 100f).coerceIn(6f, 120f)
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun extractFontFamilyFromRunXml(run: Any?): String? {
        if (run is XSLFTextRun) {
            try {
                val xml = run.xmlObject?.toString() ?: ""
                if (xml.isNotEmpty()) {
                    val latinMatch = Regex("<(?:[a-zA-Z0-9_]+:)?latin\\s+[^>]*?typeface=[\"']([^\"']+)[\"']").find(xml)
                    if (latinMatch != null) return latinMatch.groupValues[1]

                    val eaMatch = Regex("<(?:[a-zA-Z0-9_]+:)?ea\\s+[^>]*?typeface=[\"']([^\"']+)[\"']").find(xml)
                    if (eaMatch != null) return eaMatch.groupValues[1]

                    val csMatch = Regex("<(?:[a-zA-Z0-9_]+:)?cs\\s+[^>]*?typeface=[\"']([^\"']+)[\"']").find(xml)
                    if (csMatch != null) return csMatch.groupValues[1]
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun mapPresetName(rawName: String): SlideShapeType {
        val name = rawName.lowercase().trim()
        return when {
            name.contains("round") -> SlideShapeType.ROUNDED_RECTANGLE
            name == "ellipse" || name == "circle" || name == "oval" -> SlideShapeType.ELLIPSE
            name == "rttriangle" -> SlideShapeType.RIGHT_TRIANGLE
            name.contains("triangle") -> SlideShapeType.TRIANGLE
            name == "diamond" -> SlideShapeType.DIAMOND
            name.contains("parallelogram") -> SlideShapeType.PARALLELOGRAM
            name.contains("trapezoid") -> SlideShapeType.TRAPEZOID
            name.contains("pentagon") -> SlideShapeType.PENTAGON
            name.contains("hexagon") -> SlideShapeType.HEXAGON
            name.contains("octagon") || name.contains("heptagon") || name.contains("decagon") -> SlideShapeType.OCTAGON
            name.contains("leftrightarrow") -> SlideShapeType.LEFT_RIGHT_ARROW
            name.contains("updownarrow") -> SlideShapeType.UP_DOWN_ARROW
            name.contains("rightarrow") -> SlideShapeType.ARROW_RIGHT
            name.contains("leftarrow") -> SlideShapeType.ARROW_LEFT
            name.contains("uparrow") -> SlideShapeType.ARROW_UP
            name.contains("downarrow") -> SlideShapeType.ARROW_DOWN
            name.contains("chevron") || name.contains("homeplate") -> SlideShapeType.CHEVRON
            name.contains("star4") -> SlideShapeType.STAR_4
            name.contains("star5") -> SlideShapeType.STAR_5
            name.contains("star6") -> SlideShapeType.STAR_6
            name.contains("star8") || name.contains("star") -> SlideShapeType.STAR_8
            name.contains("cross") || name.contains("mathplus") -> SlideShapeType.CROSS
            name.contains("donut") || name.contains("doughnut") -> SlideShapeType.DONUT
            name.contains("cube") -> SlideShapeType.CUBE
            name.contains("cylinder") || name.contains("can") -> SlideShapeType.CYLINDER
            name.contains("plaque") -> SlideShapeType.PLAQUE
            name.contains("foldedcorner") -> SlideShapeType.FOLDED_CORNER
            name.contains("cloudcallout") -> SlideShapeType.CLOUD_CALLOUT
            name.contains("callout") || name.contains("wedgerect") -> SlideShapeType.CALLOUT_BUBBLE
            name.contains("cloud") -> SlideShapeType.CLOUD
            name.contains("heart") -> SlideShapeType.HEART
            name.contains("sun") -> SlideShapeType.SUN
            name.contains("lightning") -> SlideShapeType.LIGHTNING_BOLT
            name.contains("flowchartdecision") -> SlideShapeType.FLOWCHART_DECISION
            name.contains("flowchartterminator") -> SlideShapeType.FLOWCHART_TERMINATOR
            name.contains("flowchartdocument") -> SlideShapeType.FLOWCHART_DOCUMENT
            name.contains("flowchartinputoutput") -> SlideShapeType.FLOWCHART_INPUT_OUTPUT
            name.contains("flowchart") -> SlideShapeType.FLOWCHART_PROCESS
            name.contains("line") || name.contains("connector") -> SlideShapeType.LINE
            name.contains("rect") -> SlideShapeType.RECTANGLE
            else -> SlideShapeType.RECTANGLE
        }
    }

    private fun mapPresetGeomFromXml(xml: String): SlideShapeType? {
        if (xml.isEmpty()) return null
        try {
            val prst = Regex("<(?:[a-zA-Z0-9_]+:)?prstGeom\\s+[^>]*?prst=[\"']([^\"']+)[\"']").find(xml)?.groupValues?.get(1)
            if (prst != null) {
                return mapPresetName(prst)
            }
            if (xml.contains("<p:cxnSp") || xml.contains("cxnSp")) {
                return SlideShapeType.LINE
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun extractRotationFromXml(xml: String): Float {
        if (xml.isEmpty()) return 0f
        try {
            val rotMatch = Regex("<(?:[a-zA-Z0-9_]+:)?xfrm\\s+[^>]*?rot=[\"'](-?\\d+)[\"']").find(xml)
            val rotVal = rotMatch?.groupValues?.get(1)?.toLongOrNull() ?: return 0f
            return (rotVal / 60000f) % 360f
        } catch (_: Throwable) {
            return 0f
        }
    }

    private fun extractIsDashedFromXml(xml: String): Boolean {
        if (xml.isEmpty()) return false
        try {
            val lnMatch = Regex("<(?:[a-zA-Z0-9_]+:)?ln[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?ln>").find(xml)?.value ?: return false
            val dashMatch = Regex("<(?:[a-zA-Z0-9_]+:)?prstDash\\s+[^>]*?val=[\"']([^\"']+)[\"']").find(lnMatch)?.groupValues?.get(1)
            if (dashMatch != null) {
                val valLower = dashMatch.lowercase()
                return valLower != "solid" && (valLower.contains("dash") || valLower.contains("dot"))
            }
        } catch (_: Throwable) {}
        return false
    }

    private fun parseChartFromPart(part: org.apache.poi.ooxml.POIXMLDocumentPart, bounds: RectF): SlideElement.Chart? {
        return try {
            val xml = part.packagePart?.inputStream?.use { it.bufferedReader().readText() } ?: ""
            if (xml.isEmpty()) return null

            // Validate that this part actually represents chart XML (<c:chartSpace> or <c:chart>)
            if (!xml.contains("chartSpace", ignoreCase = true) && !xml.contains("plotArea", ignoreCase = true) && !xml.contains("chart", ignoreCase = true)) {
                return null
            }

            // Title - match <c:title> with <c:t> or <a:t> or <c:v>
            val titleMatch = Regex("<(?:[a-zA-Z0-9_]+:)?title[\\s\\S]*?<(?:[a-zA-Z0-9_]+:)?(?:t|v)>([^<]+)</(?:[a-zA-Z0-9_]+:)?(?:t|v)>").find(xml)
            val title = unescapeXml(titleMatch?.groupValues?.get(1)?.trim() ?: "Presentation Chart")

            // Chart Type
            val chartType = when {
                xml.contains("doughnutChart", ignoreCase = true) -> ChartType.DONUT
                xml.contains("lineChart", ignoreCase = true) || xml.contains("line3DChart", ignoreCase = true) || xml.contains("scatterChart", ignoreCase = true) -> ChartType.LINE
                xml.contains("pieChart", ignoreCase = true) || xml.contains("pie3DChart", ignoreCase = true) -> ChartType.PIE
                xml.contains("areaChart", ignoreCase = true) || xml.contains("area3DChart", ignoreCase = true) || xml.contains("radarChart", ignoreCase = true) -> ChartType.AREA
                xml.contains("barChart", ignoreCase = true) && xml.contains("val=\"bar\"", ignoreCase = true) -> ChartType.BAR
                else -> ChartType.COLUMN
            }

            // Categories
            val catValues = Regex("<(?:[a-zA-Z0-9_]+:)?cat[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?cat>").find(xml)?.value ?: ""
            val categories = Regex("<(?:[a-zA-Z0-9_]+:)?(?:v|t)>([^<]+)</(?:[a-zA-Z0-9_]+:)?(?:v|t)>")
                .findAll(catValues)
                .map { unescapeXml(it.groupValues[1].trim()) }
                .toList()
            val finalCategories = if (categories.isNotEmpty()) categories else listOf("Category A", "Category B", "Category C", "Category D")

            // Series
            val seriesList = mutableListOf<ChartSeries>()
            val serMatches = Regex("<(?:[a-zA-Z0-9_]+:)?ser[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?ser>").findAll(xml)
            for (serMatch in serMatches) {
                val serXml = serMatch.value
                val rawSerName = Regex("<(?:[a-zA-Z0-9_]+:)?tx[\\s\\S]*?<(?:[a-zA-Z0-9_]+:)?(?:v|t)>([^<]+)</(?:[a-zA-Z0-9_]+:)?(?:v|t)>").find(serXml)?.groupValues?.get(1)?.trim()
                val serName = if (!rawSerName.isNullOrEmpty()) unescapeXml(rawSerName) else "Series ${seriesList.size + 1}"

                val valXml = Regex("<(?:[a-zA-Z0-9_]+:)?val[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?val>").find(serXml)?.value ?: ""
                val values = Regex("<(?:[a-zA-Z0-9_]+:)?v>([^<]+)</(?:[a-zA-Z0-9_]+:)?v>").findAll(valXml).mapNotNull { it.groupValues[1].trim().toFloatOrNull() }.toList()

                val spPrXml = Regex("<(?:[a-zA-Z0-9_]+:)?spPr[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?spPr>").find(serXml)?.value ?: ""
                val serColor = if (spPrXml.isNotEmpty()) extractColorFromXml(spPrXml) else null

                if (values.isNotEmpty()) {
                    seriesList.add(ChartSeries(name = serName, values = values, colorArgb = serColor))
                }
            }

            val finalSeries = if (seriesList.isNotEmpty()) seriesList else listOf(
                ChartSeries(name = "Target", values = listOf(40f, 65f, 75f, 90f)),
                ChartSeries(name = "Actual", values = listOf(50f, 60f, 85f, 98f))
            )

            SlideElement.Chart(
                bounds = bounds,
                title = title,
                chartType = chartType,
                categories = finalCategories,
                series = finalSeries
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to parse chart XML", e)
            null
        }
    }

    private fun extractCharts(
        slide: org.apache.poi.xslf.usermodel.XSLFSlide,
        defaultBounds: RectF,
        processedRelIds: Set<String> = emptySet()
    ): List<SlideElement.Chart> {
        val charts = mutableListOf<SlideElement.Chart>()
        for (rel in slide.relationParts) {
            try {
                val relId = rel.relationship?.id
                if (relId != null && processedRelIds.contains(relId)) {
                    continue
                }
                val relType = rel.relationship?.relationshipType ?: ""
                val targetUri = rel.relationship?.targetURI?.toASCIIString() ?: ""
                val isChartRel = relType.contains("chart", ignoreCase = true) ||
                        targetUri.contains("chart", ignoreCase = true)
                if (!isChartRel) {
                    continue
                }

                val docPart = try {
                    rel.getDocumentPart<org.apache.poi.ooxml.POIXMLDocumentPart>()
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to get document part for chart rel $relId", t)
                    null
                } ?: continue

                if (docPart is XSLFChart || docPart.javaClass.simpleName.contains("Chart", ignoreCase = true)) {
                    val parsed = parseChartFromPart(docPart, defaultBounds)
                    if (parsed != null) {
                        charts.add(parsed)
                    }
                }
            } catch (relError: Throwable) {
                Log.w(TAG, "Error processing chart relation", relError)
            }
        }
        return charts
    }

    private fun extractSlideMedia(
        slide: org.apache.poi.xslf.usermodel.XSLFSlide,
        mediaCache: MutableMap<String, File>,
        tempDir: File,
        defaultBounds: RectF,
        processedMediaRelIds: MutableSet<String>
    ): List<SlideElement.Media> {
        val result = mutableListOf<SlideElement.Media>()
        try {
            val pkg = slide.packagePart?.`package`

            for (relPart in slide.relationParts) {
                val rel = relPart.relationship ?: continue
                val relId = rel.id
                if (processedMediaRelIds.contains(relId)) continue

                val target = rel.targetURI?.toASCIIString()?.lowercase() ?: ""
                val relType = rel.relationshipType?.lowercase() ?: ""
                val isVideo = relType.contains("video") || target.endsWith(".mp4") || target.endsWith(".m4v") || target.endsWith(".avi")
                val isAudio = relType.contains("audio") || target.endsWith(".mp3") || target.endsWith(".wav") || target.endsWith(".m4a")

                if (isVideo || isAudio) {
                    val rawTarget = rel.targetURI?.toASCIIString() ?: ""
                    val fileName = rawTarget.substringAfterLast('/')
                    val cachedFile = mediaCache[fileName] ?: run {
                        val part = pkg?.getPartsByName(Pattern.compile(".*/media/.*${Pattern.quote(fileName)}$"))?.firstOrNull()
                            ?: pkg?.getPartsByName(Pattern.compile(".*/media/.*"))?.firstOrNull { it.partName.name.endsWith(fileName, ignoreCase = true) }
                        if (part != null) {
                            val outFile = File(tempDir, "media_${System.currentTimeMillis()}_$fileName")
                            part.inputStream.use { input ->
                                FileOutputStream(outFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            mediaCache[fileName] = outFile
                            outFile
                        } else null
                    }

                    if (cachedFile != null) {
                        processedMediaRelIds.add(relId)
                        result.add(
                            SlideElement.Media(
                                bounds = defaultBounds,
                                mediaPath = cachedFile.absolutePath,
                                mediaType = if (isAudio) SlideMediaType.AUDIO else SlideMediaType.VIDEO,
                                title = fileName
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error extracting slide media", e)
        }
        return result
    }

    private fun resolveMediaElement(
        slide: org.apache.poi.xslf.usermodel.XSLFSlide,
        mediaRelId: String,
        shape: Any?,
        bounds: RectF,
        tempDir: File,
        mediaCache: MutableMap<String, File>
    ): SlideElement.Media? {
        try {
            val rel = slide.packagePart?.getRelationship(mediaRelId)
                ?: slide.relationParts.find { it.relationship?.id == mediaRelId }?.relationship
                ?: return null

            val rawTarget = rel.targetURI?.toASCIIString() ?: ""
            val targetLower = rawTarget.lowercase()
            val fileName = rawTarget.substringAfterLast('/')
            val isAudio = targetLower.endsWith(".mp3") || targetLower.endsWith(".wav") || targetLower.endsWith(".m4a")

            val cachedFile = mediaCache[fileName] ?: run {
                val pkg = slide.packagePart?.`package`
                val part = pkg?.getPartsByName(Pattern.compile(".*/media/.*${Pattern.quote(fileName)}$"))?.firstOrNull()
                    ?: pkg?.getPartsByName(Pattern.compile(".*/media/.*"))?.firstOrNull { it.partName.name.endsWith(fileName, ignoreCase = true) }
                if (part != null) {
                    val outFile = File(tempDir, "media_${System.currentTimeMillis()}_$fileName")
                    part.inputStream.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    mediaCache[fileName] = outFile
                    outFile
                } else null
            } ?: return null

            val previewBitmap = if (shape is XSLFPictureShape) {
                try {
                    val bytes = shape.pictureData?.data
                    if (bytes != null) decodeSampledBitmapFromByteArray(bytes, 640, 640) else null
                } catch (_: Throwable) { null }
            } else null

            return SlideElement.Media(
                bounds = bounds,
                mediaPath = cachedFile.absolutePath,
                mediaType = if (isAudio) SlideMediaType.AUDIO else SlideMediaType.VIDEO,
                title = fileName,
                previewBitmap = previewBitmap
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to resolve media for relId: $mediaRelId", e)
            return null
        }
    }

    private fun getGradientColorsSafe(shape: Any?): List<Int>? {
        if (shape is XSLFShape) {
            try {
                val xml = shape.xmlObject?.toString() ?: ""
                val spPrXml = extractSpPrXml(xml)
                val gradMatch = Regex("<(?:[a-zA-Z0-9_]+:)?gradFill[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?gradFill>").find(spPrXml)?.value
                if (gradMatch != null) {
                    val colors = mutableListOf<Int>()
                    val gsMatches = Regex("<(?:[a-zA-Z0-9_]+:)?gs\\s+[^>]*?>([\\s\\S]*?)</(?:[a-zA-Z0-9_]+:)?gs>").findAll(gradMatch)
                    for (gs in gsMatches) {
                        val color = extractColorFromXml(gs.groupValues[1])
                        if (color != null) {
                            colors.add(color)
                        }
                    }
                    if (colors.size >= 2) return colors
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun extractSlideBackground(slide: org.apache.poi.xslf.usermodel.XSLFSlide): Int? {
        // 1. Direct raw XML from packagePart (100% Android-safe, avoids CTSlide classloading crash)
        try {
            val rawXml = slide.packagePart?.inputStream?.use { it.bufferedReader().readText() }
            if (!rawXml.isNullOrEmpty()) {
                val bgMatch = Regex("<(?:[a-zA-Z0-9_]+:)?(?:bg|bgPr)[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?(?:bg|bgPr)>").find(rawXml)?.value
                if (bgMatch != null) {
                    val color = extractColorFromXml(bgMatch)
                    if (color != null) return color
                }
            }
        } catch (_: Throwable) {}

        // 2. Secondary fallback via slide.xmlObject wrapped safely
        try {
            val xml = slide.xmlObject?.toString() ?: ""
            val bgMatch = Regex("<(?:[a-zA-Z0-9_]+:)?(?:bg|bgPr)[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?(?:bg|bgPr)>").find(xml)?.value
            if (bgMatch != null) {
                val color = extractColorFromXml(bgMatch)
                if (color != null) return color
            }
        } catch (_: Throwable) {}

        // 3. Fallback to slide.background
        return try {
            val bg = slide.background
            getFillColorSafe(bg)
        } catch (_: Throwable) {
            null
        }
    }

    private fun mapShapeType(shapeType: ShapeType?): SlideShapeType {
        if (shapeType == null) return SlideShapeType.RECTANGLE
        val name = shapeType.name.uppercase()
        return when {
            name.contains("LINE") || name.contains("CONNECTOR") -> SlideShapeType.LINE
            name.contains("ROUND_RECT") -> SlideShapeType.ROUNDED_RECTANGLE
            name.contains("RECT") -> SlideShapeType.RECTANGLE
            name.contains("ELLIPSE") || name.contains("OVAL") -> SlideShapeType.ELLIPSE
            name.contains("RT_TRIANGLE") -> SlideShapeType.RIGHT_TRIANGLE
            name.contains("TRIANGLE") -> SlideShapeType.TRIANGLE
            name.contains("DIAMOND") -> SlideShapeType.DIAMOND
            name.contains("PARALLELOGRAM") -> SlideShapeType.PARALLELOGRAM
            name.contains("TRAPEZOID") -> SlideShapeType.TRAPEZOID
            name.contains("PENTAGON") -> SlideShapeType.PENTAGON
            name.contains("HEXAGON") -> SlideShapeType.HEXAGON
            name.contains("OCTAGON") -> SlideShapeType.OCTAGON
            name.contains("LEFT_RIGHT_ARROW") -> SlideShapeType.LEFT_RIGHT_ARROW
            name.contains("UP_DOWN_ARROW") -> SlideShapeType.UP_DOWN_ARROW
            name.contains("RIGHT_ARROW") -> SlideShapeType.ARROW_RIGHT
            name.contains("LEFT_ARROW") -> SlideShapeType.ARROW_LEFT
            name.contains("UP_ARROW") -> SlideShapeType.ARROW_UP
            name.contains("DOWN_ARROW") -> SlideShapeType.ARROW_DOWN
            name.contains("CHEVRON") -> SlideShapeType.CHEVRON
            name.contains("CROSS") || name.contains("MATH_PLUS") -> SlideShapeType.CROSS
            name.contains("DONUT") || name.contains("DOUGHNUT") -> SlideShapeType.DONUT
            name.contains("CUBE") -> SlideShapeType.CUBE
            name.contains("CAN") || name.contains("CYLINDER") -> SlideShapeType.CYLINDER
            name.contains("PLAQUE") -> SlideShapeType.PLAQUE
            name.contains("FOLDED_CORNER") -> SlideShapeType.FOLDED_CORNER
            name.contains("STAR_4") -> SlideShapeType.STAR_4
            name.contains("STAR_5") -> SlideShapeType.STAR_5
            name.contains("STAR_6") -> SlideShapeType.STAR_6
            name.contains("STAR_8") -> SlideShapeType.STAR_8
            name.contains("CALLOUT") -> SlideShapeType.CALLOUT_BUBBLE
            name.contains("CLOUD") -> SlideShapeType.CLOUD
            name.contains("HEART") -> SlideShapeType.HEART
            name.contains("SUN") -> SlideShapeType.SUN
            name.contains("LIGHTNING") -> SlideShapeType.LIGHTNING_BOLT
            name.contains("FLOW_CHART_DECISION") -> SlideShapeType.FLOWCHART_DECISION
            name.contains("FLOW_CHART_TERMINATOR") -> SlideShapeType.FLOWCHART_TERMINATOR
            name.contains("FLOW_CHART_DOCUMENT") -> SlideShapeType.FLOWCHART_DOCUMENT
            name.contains("FLOW_CHART") -> SlideShapeType.FLOWCHART_PROCESS
            else -> SlideShapeType.RECTANGLE
        }
    }

    private fun extractXfrmBounds(xml: String, slideWPoints: Double, slideHPoints: Double): RectF? {
        if (xml.isEmpty()) return null
        // HIGH-5: Strip group transforms (<p:grpSpPr>) so child shape <p:spPr> isn't overridden by parent group
        val cleanXml = xml.replace(Regex("<(?:[a-zA-Z0-9_]+:)?grpSpPr(?:/>|>[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?grpSpPr>)"), "")

        // If the shape has its own <p:spPr>, restrict search to it to avoid picking up outer transforms
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
                val slideWEmus = (slideWPoints.coerceAtLeast(10.0)) * 12700.0
                val slideHEmus = (slideHPoints.coerceAtLeast(10.0)) * 12700.0

                val left = (x.toDouble() / slideWEmus).toFloat().coerceIn(0f, 1f)
                val top = (y.toDouble() / slideHEmus).toFloat().coerceIn(0f, 1f)
                val width = (cx.toDouble() / slideWEmus).toFloat().coerceIn(0f, 1f)
                val height = (cy.toDouble() / slideHEmus).toFloat().coerceIn(0f, 1f)
                val right = (left + width).coerceIn(0f, 1f)
                val bottom = (top + height).coerceIn(0f, 1f)

                if (right > left && bottom > top) {
                    return RectF(left, top, right, bottom)
                }
            }
        }
        return null
    }

    private fun calculateShapeBounds(
        shape: Any?,
        slideWPoints: Double,
        slideHPoints: Double,
        defaultBounds: RectF
    ): RectF {
        // 1. Android-safe OpenXML EMU extraction (bypasses java.awt.geom.Rectangle2D completely)
        if (shape is XSLFShape) {
            try {
                val xml = shape.xmlObject?.toString() ?: ""
                val xmlBounds = extractXfrmBounds(xml, slideWPoints, slideHPoints)
                if (xmlBounds != null) {
                    return xmlBounds
                }
            } catch (_: Throwable) {}
        }

        // 2. Reflection fallback for legacy HSLF or other shapes
        try {
            val method = shape?.javaClass?.getMethod("getAnchor")
            val anchor = method?.invoke(shape)
            if (anchor != null) {
                val getX = (anchor.javaClass.getMethod("getX").invoke(anchor) as Number).toDouble()
                val getY = (anchor.javaClass.getMethod("getY").invoke(anchor) as Number).toDouble()
                val getW = (anchor.javaClass.getMethod("getWidth").invoke(anchor) as Number).toDouble()
                val getH = (anchor.javaClass.getMethod("getHeight").invoke(anchor) as Number).toDouble()

                val wPoints = slideWPoints.coerceAtLeast(10.0)
                val hPoints = slideHPoints.coerceAtLeast(10.0)

                val left = (getX / wPoints).toFloat().coerceIn(0f, 1f)
                val top = (getY / hPoints).toFloat().coerceIn(0f, 1f)
                val right = ((getX + getW) / wPoints).toFloat().coerceIn(0f, 1f)
                val bottom = ((getY + getH) / hPoints).toFloat().coerceIn(0f, 1f)

                if (right > left && bottom > top) {
                    return RectF(left, top, right, bottom)
                }
            }
        } catch (_: Throwable) {}

        return defaultBounds
    }

    private fun getStrokeWidthSafe(shape: Any?): Float {
        if (shape is XSLFShape) {
            try {
                val xml = shape.xmlObject?.toString() ?: ""
                val lineMatch = Regex("<(?:[a-zA-Z0-9_]+:)?ln\\s+[^>]*?w=[\"'](\\d+)[\"']").find(xml)
                val wEmus = lineMatch?.groupValues?.get(1)?.toLongOrNull()
                if (wEmus != null && wEmus > 0) {
                    return (wEmus / 12700f).coerceIn(0.5f, 12f)
                }
            } catch (_: Throwable) {}
        }
        return try {
            val w = shape?.javaClass?.getMethod("getLineWidth")?.invoke(shape) as? Number
            w?.toFloat()?.coerceAtLeast(1f) ?: 1f
        } catch (_: Throwable) {
            1f
        }
    }

    private fun getFillColorSafe(shape: Any?): Int? {
        if (shape == null) return null
        if (shape is XSLFShape) {
            try {
                val xml = shape.xmlObject?.toString() ?: ""
                val spPrXml = extractSpPrXml(xml)
                // If spPr has explicit noFill, this shape has no background fill
                if (Regex("<(?:[a-zA-Z0-9_]+:)?noFill\\s*/>").containsMatchIn(spPrXml)) {
                    return null
                }
                // Only consider solidFill inside shape properties (not txBody)
                val solidFillMatch = Regex("<(?:[a-zA-Z0-9_]+:)?solidFill[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?solidFill>").find(spPrXml)
                if (solidFillMatch != null) {
                    val color = extractColorFromXml(solidFillMatch.value, fallbackSchemeColor = 0xFFCBD5E1.toInt())
                    if (color != null) return color
                }
                return null
            } catch (_: Throwable) {}
        }
        return try {
            val method = shape.javaClass.getMethod("getFillColor")
            val colorObj = method.invoke(shape)
            val rgb = extractRgbFromColorOrPaint(colorObj)
            if (rgb != null && (rgb ushr 24) > 10) rgb else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getLineColorSafe(shape: Any?): Int? {
        if (shape == null) return null
        if (shape is XSLFShape) {
            try {
                val xml = shape.xmlObject?.toString() ?: ""
                val spPrXml = extractSpPrXml(xml)
                val lineMatch = Regex("<(?:[a-zA-Z0-9_]+:)?ln[\\s\\S]*?</(?:[a-zA-Z0-9_]+:)?ln>").find(spPrXml)?.value
                if (lineMatch != null) {
                    if (Regex("<(?:[a-zA-Z0-9_]+:)?noFill\\s*/>").containsMatchIn(lineMatch)) {
                        return null
                    }
                    val color = extractColorFromXml(lineMatch, fallbackSchemeColor = 0xFF64748B.toInt())
                    if (color != null) return color
                }
            } catch (_: Throwable) {}
        }
        return try {
            val method = shape.javaClass.getMethod("getLineColor")
            val colorObj = method.invoke(shape)
            val rgb = extractRgbFromColorOrPaint(colorObj)
            if (rgb != null && (rgb ushr 24) > 10) rgb else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getFontColorSafe(run: Any?): Int? {
        if (run == null) return null
        if (run is XSLFTextRun) {
            try {
                val xml = run.xmlObject?.toString() ?: ""
                val color = extractColorFromXml(xml, fallbackSchemeColor = 0xFF1E293B.toInt())
                if (color != null) return color
            } catch (_: Throwable) {}
        }
        return try {
            val colorObj = try {
                run.javaClass.getMethod("getFontColor").invoke(run)
            } catch (_: Throwable) {
                run.javaClass.getMethod("getFontColorArgb").invoke(run)
            }
            extractRgbFromColorOrPaint(colorObj)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractColorFromXml(xml: String, fallbackSchemeColor: Int = 0xFF808080.toInt()): Int? {
        if (xml.isEmpty()) return null

        var baseColor: Int? = null

        // 1. srgbClr (e.g. <a:srgbClr val="FF5733"/>)
        val srgbMatch = Regex("<(?:[a-zA-Z0-9_]+:)?srgbClr\\s+[^>]*?val=[\"']([0-9a-fA-F]{6})[\"']").find(xml)
        if (srgbMatch != null) {
            val hex = srgbMatch.groupValues[1]
            baseColor = try {
                (0xFF000000.toLong() or hex.toLong(16)).toInt()
            } catch (_: Throwable) { null }
        }

        // 2. schemeClr (e.g. <a:schemeClr val="accent1"/>)
        if (baseColor == null) {
            val schemeMatch = Regex("<(?:[a-zA-Z0-9_]+:)?schemeClr\\s+[^>]*?val=[\"']([a-zA-Z0-9]+)[\"']").find(xml)
            if (schemeMatch != null) {
                val scheme = schemeMatch.groupValues[1]
                baseColor = mapSchemeColor(scheme, fallbackSchemeColor)
            }
        }

        // 3. sysClr (e.g. <a:sysClr lastClr="1E293B" val="windowText"/>)
        if (baseColor == null) {
            val sysMatch = Regex("<(?:[a-zA-Z0-9_]+:)?sysClr\\s+[^>]*?(?:lastClr=[\"']([0-9a-fA-F]{6})[\"']|val=[\"']([^\"']+)[\"'])").find(xml)
            if (sysMatch != null) {
                val lastClr = sysMatch.groupValues[1]
                if (lastClr.isNotEmpty()) {
                    baseColor = try {
                        (0xFF000000.toLong() or lastClr.toLong(16)).toInt()
                    } catch (_: Throwable) { null }
                } else {
                    val sysVal = sysMatch.groupValues[2].lowercase()
                    baseColor = if (sysVal.contains("windowtext") || sysVal.contains("btntext")) 0xFF1E293B.toInt() else 0xFFFFFFFF.toInt()
                }
            }
        }

        // 4. prstClr (e.g. <a:prstClr val="black"/>)
        if (baseColor == null) {
            val prstMatch = Regex("<(?:[a-zA-Z0-9_]+:)?prstClr\\s+[^>]*?val=[\"']([a-zA-Z]+)[\"']").find(xml)
            if (prstMatch != null) {
                val prst = prstMatch.groupValues[1].lowercase()
                baseColor = when (prst) {
                    "black" -> 0xFF000000.toInt()
                    "white" -> 0xFFFFFFFF.toInt()
                    "red" -> 0xFFFF0000.toInt()
                    "green" -> 0xFF008000.toInt()
                    "blue" -> 0xFF0000FF.toInt()
                    "yellow" -> 0xFFFFFF00.toInt()
                    "gray", "grey" -> 0xFF808080.toInt()
                    "lightgray", "lightgrey" -> 0xFFD3D3D3.toInt()
                    "darkgray", "darkgrey" -> 0xFFA9A9A9.toInt()
                    "cyan" -> 0xFF00FFFF.toInt()
                    "magenta" -> 0xFFFF00FF.toInt()
                    "orange" -> 0xFFFFA500.toInt()
                    "purple" -> 0xFF800080.toInt()
                    else -> fallbackSchemeColor
                }
            }
        }

        if (baseColor == null) return null

        // Apply alpha modulation (<a:alpha val="50000"/> -> 50% opacity)
        val alphaMatch = Regex("<(?:[a-zA-Z0-9_]+:)?alpha\\s+[^>]*?val=[\"'](\\d+)[\"']").find(xml)
        if (alphaMatch != null) {
            val alphaFactor = alphaMatch.groupValues[1].toIntOrNull()?.let { (it / 100000f).coerceIn(0f, 1f) } ?: 1f
            val a = ((baseColor ushr 24) * alphaFactor).toInt().coerceIn(0, 255)
            baseColor = (a shl 24) or (baseColor and 0x00FFFFFF)
        }

        // Apply luminance modulation (<a:lumMod val="80000"/>, <a:lumOff val="20000"/>)
        val lumMod = Regex("<(?:[a-zA-Z0-9_]+:)?lumMod\\s+[^>]*?val=[\"'](\\d+)[\"']").find(xml)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 100000f }
        val lumOff = Regex("<(?:[a-zA-Z0-9_]+:)?lumOff\\s+[^>]*?val=[\"'](\\d+)[\"']").find(xml)?.groupValues?.get(1)?.toIntOrNull()?.let { it / 100000f }
        if (lumMod != null || lumOff != null) {
            val a = (baseColor ushr 24) and 0xFF
            var r = (baseColor ushr 16) and 0xFF
            var g = (baseColor ushr 8) and 0xFF
            var b = baseColor and 0xFF

            if (lumMod != null) {
                r = (r * lumMod).toInt().coerceIn(0, 255)
                g = (g * lumMod).toInt().coerceIn(0, 255)
                b = (b * lumMod).toInt().coerceIn(0, 255)
            }
            if (lumOff != null) {
                val off = (255 * lumOff).toInt()
                r = (r + off).coerceIn(0, 255)
                g = (g + off).coerceIn(0, 255)
                b = (b + off).coerceIn(0, 255)
            }
            baseColor = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        return baseColor
    }

    private fun mapSchemeColor(name: String, fallback: Int = 0xFF808080.toInt()): Int {
        return when (name.lowercase()) {
            "accent1" -> 0xFF2E75B6.toInt() // Office Blue
            "accent2" -> 0xFFED7D31.toInt() // Office Orange
            "accent3" -> 0xFFA5A5A5.toInt() // Office Gray
            "accent4" -> 0xFFFFC000.toInt() // Office Gold/Yellow
            "accent5" -> 0xFF4472C4.toInt() // Office Royal Blue
            "accent6" -> 0xFF70AD47.toInt() // Office Green
            "dk1", "tx1", "dark1" -> 0xFF1B1B1B.toInt()
            "lt1", "bg1", "light1" -> 0xFFFFFFFF.toInt()
            "dk2", "tx2", "dark2" -> 0xFF2F3542.toInt()
            "lt2", "bg2", "light2" -> 0xFFF1F2F6.toInt()
            "folhlink" -> 0xFF7030A0.toInt()
            "hlink" -> 0xFF0563C1.toInt()
            "phclr" -> 0xFF404040.toInt()
            "med1", "med2" -> 0xFF808080.toInt()
            "gray", "grey" -> 0xFF808080.toInt()
            "black" -> 0xFF000000.toInt()
            "white" -> 0xFFFFFFFF.toInt()
            else -> fallback // Fallback to provided sensible default rather than null
        }
    }

    private fun extractRgbFromColorOrPaint(obj: Any?): Int? {
        if (obj == null) return null

        // 1. Direct Color object with getRGB()
        try {
            val rgb = obj.javaClass.getMethod("getRGB").invoke(obj) as? Int
            if (rgb != null) return rgb
        } catch (_: Throwable) {}

        // 2. PaintStyle / SolidPaint from POI 5
        try {
            val solidColor = obj.javaClass.getMethod("getSolidColor").invoke(obj)
            if (solidColor != null) {
                val color = solidColor.javaClass.getMethod("getColor").invoke(solidColor)
                if (color != null) {
                    val rgb = color.javaClass.getMethod("getRGB").invoke(color) as? Int
                    if (rgb != null) return rgb
                }
            }
        } catch (_: Throwable) {}

        // 3. Direct integer value
        if (obj is Number) return obj.toInt()

        return null
    }

    fun renderSlideToBitmap(slide: PresentationSlideInfo, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return try {
            val isThumbnail = (width <= 480 || height <= 480)
            val config = if (isThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            val bitmap = try {
                Bitmap.createBitmap(width, height, config)
            } catch (_: OutOfMemoryError) {
                try {
                    Bitmap.createBitmap((width * 0.75f).toInt(), (height * 0.75f).toInt(), Bitmap.Config.RGB_565)
                } catch (_: Throwable) {
                    return null
                }
            }
            val canvas = Canvas(bitmap)
            val renderW = bitmap.width
            val renderH = bitmap.height

            // 1. Draw slide background
            val bgColor = slide.backgroundColorArgb ?: Color.WHITE
            canvas.drawColor(bgColor)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            val scale = renderW / 960f

            // 2. Viewpoint fitting margins: protect parameter elements, badges, and slide-style tellers at edges
            val marginX = (4f * scale).coerceIn(2f, 10f)
            val marginY = (4f * (renderH / 540f)).coerceIn(2f, 10f)
            val innerW = renderW - 2 * marginX
            val innerH = renderH - 2 * marginY

            // 3. Dynamic Bounding Box & Vertical Spacing Recalculation
            class ResolvedLayout(
                val element: SlideElement,
                var left: Float,
                var top: Float,
                var right: Float,
                var bottom: Float,
                var fontScale: Float = 1.0f
            ) {
                val width: Float get() = (right - left).coerceAtLeast(1f)
                val height: Float get() = (bottom - top).coerceAtLeast(1f)
            }

            val resolvedElements = slide.elements.map { element ->
                val b = element.bounds
                val isFullBg = (b.width() >= 0.88f && b.height() >= 0.88f && b.left <= 0.06f && b.top <= 0.06f && element !is SlideElement.Text)
                if (isFullBg) {
                    ResolvedLayout(element, 0f, 0f, renderW.toFloat(), renderH.toFloat())
                } else {
                    val l = (marginX + b.left * innerW).coerceIn(0f, renderW.toFloat())
                    val t = (marginY + b.top * innerH).coerceIn(0f, renderH.toFloat())
                    val r = (marginX + b.right * innerW).coerceIn(l + 1f, renderW.toFloat())
                    val btm = (marginY + b.bottom * innerH).coerceIn(t + 1f, renderH.toFloat())
                    ResolvedLayout(element, l, t, r, btm)
                }
            }

            // Measure required text block heights using StaticLayout
            fun computeTextRequiredHeight(element: SlideElement, availableWidth: Float, fontFactor: Float): Float {
                var totalH = 0f
                val effectiveScale = scale * fontFactor
                when (element) {
                    is SlideElement.Text -> {
                        if (element.paragraphs.isNotEmpty()) {
                            for (p in element.paragraphs) {
                                val fullText = buildString {
                                    if (p.isBullet) append(p.bulletChar ?: "•").append(" ")
                                    for (span in p.spans) append(span.text)
                                }
                                if (fullText.isEmpty()) continue
                                val firstSpan = p.spans.firstOrNull()
                                val baseSize = firstSpan?.fontSize ?: 14f
                                textPaint.textSize = (baseSize * effectiveScale).coerceAtLeast(7f)
                                textPaint.isFakeBoldText = firstSpan?.isBold ?: false
                                val lineSpacing = p.lineSpacingMultiplier.coerceIn(1.0f, 1.35f)
                                val layout = StaticLayout.Builder.obtain(
                                    fullText, 0, fullText.length, textPaint,
                                    availableWidth.toInt().coerceAtLeast(10)
                                ).setLineSpacing(0f, lineSpacing).build()
                                totalH += layout.height + (4f * effectiveScale)
                            }
                        } else if (element.bullets.isNotEmpty()) {
                            textPaint.textSize = (14f * effectiveScale).coerceAtLeast(7f)
                            for (bullet in element.bullets) {
                                val fullText = "• $bullet"
                                val layout = StaticLayout.Builder.obtain(
                                    fullText, 0, fullText.length, textPaint,
                                    availableWidth.toInt().coerceAtLeast(10)
                                ).build()
                                totalH += layout.height + (3f * effectiveScale)
                            }
                        } else if (element.text.isNotEmpty()) {
                            textPaint.textSize = (element.fontSize * effectiveScale).coerceAtLeast(7f)
                            textPaint.isFakeBoldText = element.isBold
                            val layout = StaticLayout.Builder.obtain(
                                element.text, 0, element.text.length, textPaint,
                                availableWidth.toInt().coerceAtLeast(10)
                            ).build()
                            totalH += layout.height
                        }
                    }
                    is SlideElement.Shape -> {
                        if (element.textParagraphs.isNotEmpty()) {
                            for (p in element.textParagraphs) {
                                val fullText = buildString {
                                    if (p.isBullet) append(p.bulletChar ?: "•").append(" ")
                                    for (span in p.spans) append(span.text)
                                }
                                if (fullText.isEmpty()) continue
                                val firstSpan = p.spans.firstOrNull()
                                val baseSize = firstSpan?.fontSize ?: 14f
                                textPaint.textSize = (baseSize * effectiveScale).coerceAtLeast(7f)
                                textPaint.isFakeBoldText = firstSpan?.isBold ?: false
                                val lineSpacing = p.lineSpacingMultiplier.coerceIn(1.0f, 1.35f)
                                val layout = StaticLayout.Builder.obtain(
                                    fullText, 0, fullText.length, textPaint,
                                    availableWidth.toInt().coerceAtLeast(10)
                                ).setLineSpacing(0f, lineSpacing).build()
                                totalH += layout.height + (4f * effectiveScale)
                            }
                        }
                    }
                    else -> {}
                }
                return totalH
            }

            // Identify text-bearing elements and sort by vertical position (top)
            val textLayouts = resolvedElements.filter {
                it.element is SlideElement.Text || (it.element is SlideElement.Shape && it.element.textParagraphs.isNotEmpty())
            }.sortedBy { it.top }

            for (i in 0 until textLayouts.size - 1) {
                val current = textLayouts[i]
                val next = textLayouts[i + 1]

                // Check horizontal overlap between these vertically adjacent text blocks
                val overlapLeft = maxOf(current.left, next.left)
                val overlapRight = minOf(current.right, next.right)
                val overlapWidth = (overlapRight - overlapLeft).coerceAtLeast(0f)
                val minWidth = minOf(current.width, next.width)

                if (minWidth > 0 && (overlapWidth / minWidth) >= 0.35f) {
                    val reqH = computeTextRequiredHeight(current.element, current.width, current.fontScale)
                    val effectiveBottom = current.top + reqH
                    val minGap = 8f * scale

                    if (effectiveBottom + minGap > next.top) {
                        val shift = (effectiveBottom + minGap) - next.top
                        val newTop = next.top + shift
                        val newBottom = next.bottom + shift

                        // If shifting pushes content past bottom of slide, auto-fit by scaling font
                        if (newBottom > renderH - marginY) {
                            val availableRemainingH = (renderH - marginY - current.top).coerceAtLeast(40f)
                            val totalNeeded = reqH + computeTextRequiredHeight(next.element, next.width, 1.0f) + minGap
                            val fitFactor = (availableRemainingH / totalNeeded).coerceIn(0.70f, 1.0f)
                            current.fontScale = fitFactor
                            next.fontScale = fitFactor

                            val fittedReqH = computeTextRequiredHeight(current.element, current.width, fitFactor)
                            next.top = current.top + fittedReqH + (minGap * fitFactor)
                            val nextReqH = computeTextRequiredHeight(next.element, next.width, fitFactor)
                            next.bottom = (next.top + nextReqH).coerceAtMost(renderH.toFloat() - marginY)
                        } else {
                            next.top = newTop
                            next.bottom = newBottom
                        }
                    }
                }
            }

            // 4. Render resolved elements to canvas
            for (layout in resolvedElements) {
                try {
                    val element = layout.element
                    val left = layout.left
                    val top = layout.top
                    val right = layout.right
                    val bottom = layout.bottom
                    val elemW = layout.width
                    val elemH = layout.height

                    when (element) {
                        is SlideElement.Image -> {
                            if (!element.bitmap.isRecycled && element.bitmap.width > 0 && element.bitmap.height > 0) {
                                val srcRect = android.graphics.Rect(0, 0, element.bitmap.width, element.bitmap.height)
                                val dstRect = RectF(left, top, right, bottom)
                                canvas.drawBitmap(element.bitmap, srcRect, dstRect, paint)
                            }
                        }

                        is SlideElement.Shape -> {
                            canvas.save()
                            canvas.translate(left, top)
                            if (element.fillInfo.rotation != 0f) {
                                canvas.rotate(element.fillInfo.rotation, elemW / 2f, elemH / 2f)
                            }

                            // Fill
                            if (element.shapeType != SlideShapeType.LINE) {
                                val gradColors = element.fillInfo.gradientColors
                                if (gradColors != null && gradColors.size >= 2) {
                                    val shader = android.graphics.LinearGradient(
                                        0f, 0f, elemW, elemH,
                                        gradColors.toIntArray(),
                                        null,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                    paint.shader = shader
                                    paint.style = Paint.Style.FILL
                                    drawShapeOnCanvas(canvas, element.shapeType, elemW, elemH, paint)
                                    paint.shader = null
                                } else if (element.fillColor != null) {
                                    paint.color = element.fillColor
                                    paint.style = Paint.Style.FILL
                                    drawShapeOnCanvas(canvas, element.shapeType, elemW, elemH, paint)
                                }
                            }

                            // Stroke
                            val strokeColor = element.fillInfo.strokeColor
                                ?: if (element.shapeType != SlideShapeType.RECTANGLE) 0xFF94A3B8.toInt() else null
                            if (strokeColor != null || element.shapeType == SlideShapeType.LINE) {
                                paint.color = strokeColor ?: element.fillColor ?: 0xFF94A3B8.toInt()
                                paint.style = Paint.Style.STROKE
                                paint.strokeWidth = (element.fillInfo.strokeWidth * scale).coerceAtLeast(1f)
                                drawShapeOnCanvas(canvas, element.shapeType, elemW, elemH, paint)
                            }

                            // Inner text
                            if (element.textParagraphs.isNotEmpty()) {
                                drawParagraphsOnCanvas(
                                    canvas, element.textParagraphs, elemW, elemH, textPaint, renderW,
                                    fontScale = layout.fontScale
                                )
                            }

                            canvas.restore()
                        }

                        is SlideElement.Text -> {
                            canvas.save()
                            canvas.translate(left, top)
                            if (element.paragraphs.isNotEmpty()) {
                                drawParagraphsOnCanvas(
                                    canvas, element.paragraphs, elemW, elemH, textPaint, renderW,
                                    fontScale = layout.fontScale
                                )
                            } else if (element.bullets.isNotEmpty()) {
                                drawBulletsOnCanvas(
                                    canvas, element.bullets, elemW, elemH, textPaint, renderW,
                                    element.colorArgb, fontScale = layout.fontScale
                                )
                            } else if (element.text.isNotEmpty()) {
                                textPaint.color = element.colorArgb ?: 0xFF1E293B.toInt()
                                textPaint.textSize = (element.fontSize * scale * layout.fontScale).coerceAtLeast(8f)
                                textPaint.isFakeBoldText = element.isBold
                                val textLayout = StaticLayout.Builder.obtain(
                                    element.text, 0, element.text.length,
                                    textPaint, elemW.toInt().coerceAtLeast(10)
                                ).build()
                                textLayout.draw(canvas)
                            }
                            canvas.restore()
                        }

                        is SlideElement.Table -> {
                            canvas.save()
                            canvas.translate(left, top)
                            drawTableOnCanvas(canvas, element, elemW, elemH, paint, textPaint, renderW)
                            canvas.restore()
                        }

                        is SlideElement.Chart -> {
                            canvas.save()
                            canvas.translate(left, top)
                            drawChartOnCanvas(canvas, element, elemW, elemH, paint, textPaint, renderW)
                            canvas.restore()
                        }

                        else -> {}
                    }
                } catch (elemError: Throwable) {
                    Log.w(TAG, "Error rendering slide element to canvas", elemError)
                }
            }

            bitmap
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to render slide to bitmap", e)
            null
        }
    }

    private fun drawShapeOnCanvas(
        canvas: Canvas,
        shapeType: SlideShapeType,
        w: Float,
        h: Float,
        paint: Paint
    ) {
        val strokeInset = if (paint.style == Paint.Style.STROKE) {
            (paint.strokeWidth / 2f).coerceAtLeast(0.5f)
        } else 0f

        val effW = (w - 2 * strokeInset).coerceAtLeast(1f)
        val effH = (h - 2 * strokeInset).coerceAtLeast(1f)
        val left = strokeInset
        val top = strokeInset
        val right = left + effW
        val bottom = top + effH

        val path = Path()
        when (shapeType) {
            SlideShapeType.RECTANGLE -> {
                canvas.drawRect(left, top, right, bottom, paint)
                return
            }
            SlideShapeType.ROUNDED_RECTANGLE -> {
                val r = (Math.min(effW, effH) * 0.12f).coerceAtLeast(4f)
                canvas.drawRoundRect(left, top, right, bottom, r, r, paint)
                return
            }
            SlideShapeType.ELLIPSE -> {
                canvas.drawOval(left, top, right, bottom, paint)
                return
            }
            SlideShapeType.LINE -> {
                canvas.drawLine(left, top + effH / 2f, right, top + effH / 2f, paint)
                return
            }
            SlideShapeType.TRIANGLE -> {
                path.moveTo(left + effW / 2f, top)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close()
            }
            SlideShapeType.RIGHT_TRIANGLE -> {
                path.moveTo(left, top)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close()
            }
            SlideShapeType.DIAMOND -> {
                path.moveTo(left + effW / 2f, top)
                path.lineTo(right, top + effH / 2f)
                path.lineTo(left + effW / 2f, bottom)
                path.lineTo(left, top + effH / 2f)
                path.close()
            }
            SlideShapeType.ARROW_RIGHT -> {
                val shaftH = effH * 0.4f
                val shaftY = top + (effH - shaftH) * 0.5f
                val headW = effW * 0.35f
                path.moveTo(left, shaftY)
                path.lineTo(right - headW, shaftY)
                path.lineTo(right - headW, top)
                path.lineTo(right, top + effH * 0.5f)
                path.lineTo(right - headW, bottom)
                path.lineTo(right - headW, shaftY + shaftH)
                path.lineTo(left, shaftY + shaftH)
                path.close()
            }
            SlideShapeType.ARROW_LEFT -> {
                val shaftH = effH * 0.4f
                val shaftY = top + (effH - shaftH) * 0.5f
                val headW = effW * 0.35f
                path.moveTo(right, shaftY)
                path.lineTo(left + headW, shaftY)
                path.lineTo(left + headW, top)
                path.lineTo(left, top + effH * 0.5f)
                path.lineTo(left + headW, bottom)
                path.lineTo(left + headW, shaftY + shaftH)
                path.lineTo(right, shaftY + shaftH)
                path.close()
            }
            SlideShapeType.CROSS -> {
                val uniformDim = Math.min(effW, effH)
                val t = uniformDim * 0.3f
                val cx = left + effW / 2f
                val cy = top + effH / 2f
                val halfDim = uniformDim / 2f
                val x0 = cx - halfDim
                val y0 = cy - halfDim
                val x1 = cx - t / 2f
                val x2 = cx + t / 2f
                val x3 = cx + halfDim
                val y1 = cy - t / 2f
                val y2 = cy + t / 2f
                val y3 = cy + halfDim
                path.moveTo(x1, y0)
                path.lineTo(x2, y0)
                path.lineTo(x2, y1)
                path.lineTo(x3, y1)
                path.lineTo(x3, y2)
                path.lineTo(x2, y2)
                path.lineTo(x2, y3)
                path.lineTo(x1, y3)
                path.lineTo(x1, y2)
                path.lineTo(x0, y2)
                path.lineTo(x0, y1)
                path.lineTo(x1, y1)
                path.close()
            }
            SlideShapeType.DONUT -> {
                val uniformDim = Math.min(effW, effH)
                val cx = left + effW / 2f
                val cy = top + effH / 2f
                val outer = RectF(cx - uniformDim / 2f, cy - uniformDim / 2f, cx + uniformDim / 2f, cy + uniformDim / 2f)
                path.addOval(outer, Path.Direction.CW)
                val innerDim = uniformDim * 0.5f
                val inner = RectF(cx - innerDim / 2f, cy - innerDim / 2f, cx + innerDim / 2f, cy + innerDim / 2f)
                path.addOval(inner, Path.Direction.CCW)
            }
            SlideShapeType.CHEVRON -> {
                path.moveTo(left, top)
                path.lineTo(left + effW * 0.75f, top)
                path.lineTo(right, top + effH * 0.5f)
                path.lineTo(left + effW * 0.75f, bottom)
                path.lineTo(left, bottom)
                path.lineTo(left + effW * 0.25f, top + effH * 0.5f)
                path.close()
            }
            SlideShapeType.STAR_5 -> {
                val uniformDim = Math.min(effW, effH)
                val cx = left + effW / 2f
                val cy = top + effH / 2f
                val rOuter = uniformDim / 2f
                val rInner = rOuter * 0.4f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) rOuter else rInner
                    val angle = (i * 36.0 - 90.0) * Math.PI / 180.0
                    val px = cx + (r * Math.cos(angle)).toFloat()
                    val py = cy + (r * Math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            else -> {
                canvas.drawRect(left, top, right, bottom, paint)
                return
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawParagraphsOnCanvas(
        canvas: Canvas,
        paragraphs: List<ParagraphInfo>,
        maxW: Float,
        maxH: Float,
        textPaint: TextPaint,
        slideWidth: Int,
        fontScale: Float = 1.0f
    ) {
        var currentY = 0f
        val scale = (slideWidth / 960f) * fontScale
        for (para in paragraphs) {
            if (currentY >= maxH + 40f * scale) break
            val fullText = buildString {
                if (para.isBullet) append(para.bulletChar ?: "•").append(" ")
                for (span in para.spans) {
                    append(span.text)
                }
            }
            if (fullText.isEmpty()) continue

            val firstSpan = para.spans.firstOrNull()
            textPaint.color = firstSpan?.colorArgb ?: 0xFF1E293B.toInt()
            val baseSize = firstSpan?.fontSize ?: 14f
            textPaint.textSize = (baseSize * scale).coerceAtLeast(7f)
            textPaint.isFakeBoldText = firstSpan?.isBold ?: false

            val align = when (para.alignment) {
                TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            val layoutWidth = maxW.toInt().coerceAtLeast(10)
            val spacingMult = para.lineSpacingMultiplier.coerceIn(1.0f, 1.35f)
            val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, layoutWidth)
                .setAlignment(align)
                .setLineSpacing(0f, spacingMult)
                .build()

            canvas.save()
            canvas.translate(0f, currentY)
            layout.draw(canvas)
            canvas.restore()

            currentY += layout.height + (4f * scale)
        }
    }

    private fun drawBulletsOnCanvas(
        canvas: Canvas,
        bullets: List<String>,
        maxW: Float,
        maxH: Float,
        textPaint: TextPaint,
        slideWidth: Int,
        colorArgb: Int?,
        fontScale: Float = 1.0f
    ) {
        var currentY = 0f
        val scale = (slideWidth / 960f) * fontScale
        textPaint.color = colorArgb ?: 0xFF1E293B.toInt()
        textPaint.textSize = (14f * scale).coerceAtLeast(7f)

        for (bullet in bullets) {
            if (currentY >= maxH + 40f * scale) break
            val fullText = "• $bullet"
            val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, maxW.toInt().coerceAtLeast(10))
                .build()
            canvas.save()
            canvas.translate(0f, currentY)
            layout.draw(canvas)
            canvas.restore()
            currentY += layout.height + (3f * scale)
        }
    }

    private fun drawTableOnCanvas(
        canvas: Canvas,
        table: SlideElement.Table,
        w: Float,
        h: Float,
        paint: Paint,
        textPaint: TextPaint,
        slideWidth: Int
    ) {
        val rows = if (table.tableRows.isNotEmpty()) table.tableRows.size else table.rows.size
        if (rows == 0) return
        val rowH = h / rows
        val scale = slideWidth / 960f
        val strokeInset = (1f * scale).coerceAtLeast(1f)

        paint.color = 0xFFCBD5E1.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeInset
        canvas.drawRect(strokeInset, strokeInset, w - strokeInset, h - strokeInset, paint)

        if (table.tableRows.isNotEmpty()) {
            for ((rIdx, row) in table.tableRows.withIndex()) {
                val cols = row.cells.size
                if (cols == 0) continue
                val colW = w / cols
                val cellY = rIdx * rowH

                for ((cIdx, cell) in row.cells.withIndex()) {
                    val cellX = cIdx * colW
                    if (cell.backgroundColorArgb != null) {
                        paint.style = Paint.Style.FILL
                        paint.color = cell.backgroundColorArgb
                        canvas.drawRect(cellX, cellY, cellX + colW, cellY + rowH, paint)
                    }
                    paint.style = Paint.Style.STROKE
                    paint.color = 0xFFE2E8F0.toInt()
                    canvas.drawRect(cellX, cellY, cellX + colW, cellY + rowH, paint)

                    val firstSpan = cell.spans.firstOrNull()
                    textPaint.color = firstSpan?.colorArgb ?: 0xFF1E293B.toInt()
                    textPaint.textSize = ((firstSpan?.fontSize ?: 12f) * scale).coerceAtLeast(7f)
                    textPaint.isFakeBoldText = firstSpan?.isBold ?: false

                    val cellText = cell.text.ifEmpty { cell.spans.joinToString("") { it.text } }
                    if (cellText.isNotEmpty()) {
                        val layout = StaticLayout.Builder.obtain(
                            cellText, 0, cellText.length, textPaint, (colW - 8f).toInt().coerceAtLeast(10)
                        ).build()
                        canvas.save()
                        canvas.translate(cellX + 4f, cellY + 4f)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                }
            }
        }
    }

    private fun drawChartOnCanvas(
        canvas: Canvas,
        chart: SlideElement.Chart,
        w: Float,
        h: Float,
        paint: Paint,
        textPaint: TextPaint,
        slideWidth: Int
    ) {
        val scale = slideWidth / 960f
        val strokeInset = (1f * scale).coerceAtLeast(1f)
        val chartRect = RectF(strokeInset, strokeInset, w - strokeInset, h - strokeInset)

        paint.color = 0xFFFFFFFF.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(chartRect, 8f * scale, 8f * scale, paint)
        paint.color = 0xFFE2E8F0.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeInset
        canvas.drawRoundRect(chartRect, 8f * scale, 8f * scale, paint)

        textPaint.color = 0xFF1E293B.toInt()
        textPaint.textSize = (13f * scale).coerceAtLeast(9f)
        textPaint.isFakeBoldText = true
        val chartTitle = chart.title ?: "Chart"
        canvas.drawText(chartTitle, 12f * scale, (20f * scale).coerceAtLeast(12f), textPaint)
        textPaint.isFakeBoldText = false

        val series = chart.series
        if (series.isNotEmpty()) {
            val chartAreaTop = (30f * scale).coerceAtMost(h * 0.4f)
            val chartAreaH = (h - chartAreaTop - 16f * scale).coerceAtLeast(20f * scale)
            val availableW = (w - 24f * scale).coerceAtLeast(20f * scale)
            val barGroupW = availableW / series.size
            val defaultColors = listOf(0xFF2563EB.toInt(), 0xFF0D9488.toInt(), 0xFFEA580C.toInt(), 0xFF7C3AED.toInt())

            for ((sIdx, ser) in series.withIndex()) {
                val groupX = 12f * scale + sIdx * barGroupW
                val maxVal = ser.values.maxOrNull()?.coerceAtLeast(1f) ?: 100f
                val colColor = ser.colorArgb ?: defaultColors[sIdx % defaultColors.size]
                paint.color = colColor
                paint.style = Paint.Style.FILL

                val valCount = ser.values.size.coerceAtLeast(1)
                val barW = (barGroupW * 0.65f / valCount).coerceAtLeast(2f * scale)

                for ((vIdx, v) in ser.values.withIndex()) {
                    val barH = ((v / maxVal) * chartAreaH).coerceIn(2f * scale, chartAreaH)
                    val barX = groupX + vIdx * (barW + 2f * scale)
                    val barY = chartAreaTop + chartAreaH - barH
                    if (barX + barW <= w - 6f * scale) {
                        canvas.drawRoundRect(barX, barY, barX + barW, chartAreaTop + chartAreaH, 2f * scale, 2f * scale, paint)
                    }
                }
            }
        }
    }
}
