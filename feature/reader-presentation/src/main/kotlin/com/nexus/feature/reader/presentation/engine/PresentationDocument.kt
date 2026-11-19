package com.nexus.feature.reader.presentation.engine

import android.graphics.Bitmap
import android.graphics.RectF
import java.io.Closeable

/**
 * Common shape geometry categories for vector rendering.
 */
enum class SlideShapeType {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    ELLIPSE,
    TRIANGLE,
    RIGHT_TRIANGLE,
    DIAMOND,
    PARALLELOGRAM,
    TRAPEZOID,
    PENTAGON,
    HEXAGON,
    OCTAGON,
    ARROW_RIGHT,
    ARROW_LEFT,
    ARROW_UP,
    ARROW_DOWN,
    CHEVRON,
    STAR_4,
    STAR_5,
    STAR_6,
    STAR_8,
    CALLOUT_BUBBLE,
    CLOUD_CALLOUT,
    FLOWCHART_PROCESS,
    FLOWCHART_DECISION,
    FLOWCHART_TERMINATOR,
    FLOWCHART_DOCUMENT,
    FLOWCHART_INPUT_OUTPUT,
    CLOUD,
    HEART,
    SUN,
    LIGHTNING_BOLT,
    LINE,
    CROSS,
    DONUT,
    CUBE,
    CYLINDER,
    PLAQUE,
    FOLDED_CORNER,
    LEFT_RIGHT_ARROW,
    UP_DOWN_ARROW,
    UNKNOWN
}

data class ShapeFillInfo(
    val fillColor: Int? = null,
    val gradientColors: List<Int>? = null,
    val strokeColor: Int? = null,
    val strokeWidth: Float = 1f,
    val rotation: Float = 0f,
    val isDashed: Boolean = false,
    val customPathData: String? = null
)

data class TextSpanInfo(
    val text: String,
    val fontSize: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false,
    val colorArgb: Int? = null,
    val backgroundColorArgb: Int? = null,
    val linkUrl: String? = null,
    val fontFamily: String? = null
)

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

data class ParagraphInfo(
    val spans: List<TextSpanInfo> = emptyList(),
    val alignment: TextAlignment = TextAlignment.LEFT,
    val isBullet: Boolean = false,
    val bulletChar: String? = null,
    val indent: Float = 0f,
    val lineSpacingMultiplier: Float = 1.15f
) {
    val rawText: String
        get() = spans.joinToString("") { it.text }
}

enum class CellVerticalAlignment {
    TOP,
    CENTER,
    BOTTOM
}

data class TableCellInfo(
    val text: String,
    val spans: List<TextSpanInfo> = emptyList(),
    val backgroundColorArgb: Int? = null,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val verticalAlignment: CellVerticalAlignment = CellVerticalAlignment.CENTER,
    val borderColorArgb: Int? = null,
    val borderWidth: Float = 1f
)

data class TableRowInfo(
    val cells: List<TableCellInfo> = emptyList()
)

enum class ChartType {
    BAR,
    COLUMN,
    LINE,
    PIE,
    DONUT,
    AREA
}

data class ChartSeries(
    val name: String,
    val values: List<Float>,
    val colorArgb: Int? = null
)

enum class SlideMediaType {
    VIDEO,
    AUDIO
}

/**
 * Slide visual and textual elements for native presentation rendering.
 */
sealed interface SlideElement {
    val bounds: RectF // Relative coordinate ratios: 0.0f..1.0f

    data class Text(
        override val bounds: RectF,
        val text: String,
        val isTitle: Boolean = false,
        val bullets: List<String> = emptyList(),
        val fontSize: Float = 16f,
        val isBold: Boolean = false,
        val colorArgb: Int? = null,
        val paragraphs: List<ParagraphInfo> = emptyList()
    ) : SlideElement

    data class Image(
        override val bounds: RectF,
        val bitmap: Bitmap
    ) : SlideElement

    data class Table(
        override val bounds: RectF,
        val rows: List<List<String>> = emptyList(),
        val tableRows: List<TableRowInfo> = emptyList()
    ) : SlideElement

    data class Shape(
        override val bounds: RectF,
        val shapeName: String,
        val fillColor: Int? = null,
        val shapeType: SlideShapeType = SlideShapeType.RECTANGLE,
        val fillInfo: ShapeFillInfo = ShapeFillInfo(fillColor = fillColor),
        val textParagraphs: List<ParagraphInfo> = emptyList()
    ) : SlideElement

    data class Chart(
        override val bounds: RectF,
        val title: String? = null,
        val chartType: ChartType = ChartType.COLUMN,
        val categories: List<String> = emptyList(),
        val series: List<ChartSeries> = emptyList()
    ) : SlideElement

    data class Media(
        override val bounds: RectF,
        val mediaPath: String,
        val mediaType: SlideMediaType = SlideMediaType.VIDEO,
        val title: String? = null,
        val previewBitmap: Bitmap? = null
    ) : SlideElement
}

/**
 * Represents an individual slide metadata and content.
 */
data class PresentationSlideInfo(
    val index: Int,
    val name: String,
    val widthTwips: Long = 28800L,
    val heightTwips: Long = 16200L,
    val title: String? = null,
    val notes: String? = null,
    val backgroundColorArgb: Int? = null,
    val elements: List<SlideElement> = emptyList()
) {
    val aspectRatio: Float
        get() {
            val calculated = if (heightTwips > 0L && widthTwips > 0L) {
                widthTwips.toFloat() / heightTwips.toFloat()
            } else {
                16f / 9f
            }
            return if (calculated.isNaN() || calculated.isInfinite() || calculated !in 0.3f..4.0f) {
                16f / 9f
            } else {
                calculated
            }
        }
}

interface PresentationDocument : Closeable {
    val slideCount: Int
    val slides: List<PresentationSlideInfo>

    /**
     * Renders a slide into an existing or newly allocated Bitmap.
     * Returns null if using native Compose element rendering.
     */
    fun renderSlide(slideIndex: Int, width: Int, height: Int): Bitmap?

    override fun close()
}
