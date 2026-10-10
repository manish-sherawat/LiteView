package com.nexus.feature.reader.presentation.engine

import android.graphics.Bitmap
import android.graphics.RectF
import java.io.Closeable

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
        val colorArgb: Int? = null
    ) : SlideElement

    data class Image(
        override val bounds: RectF,
        val bitmap: Bitmap
    ) : SlideElement

    data class Table(
        override val bounds: RectF,
        val rows: List<List<String>>
    ) : SlideElement

    data class Shape(
        override val bounds: RectF,
        val shapeName: String,
        val fillColor: Int? = null
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
        get() = if (heightTwips > 0) widthTwips.toFloat() / heightTwips.toFloat() else 16f / 9f
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
