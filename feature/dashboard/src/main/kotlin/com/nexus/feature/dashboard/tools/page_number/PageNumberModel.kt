package com.nexus.feature.dashboard.tools.page_number

import android.net.Uri
import java.io.File

/**
 * Page number text format.
 */
enum class NumberFormat(val id: String, val title: String) {
    N("n", "1"),
    PAGE("page", "Page 1"),
    OF("of", "1 / 10"),
    PAGE_OF("pageof", "Page 1 of 10"),
    DASH("dash", "- 1 -")
}

/**
 * Number style (Digits vs Roman numerals).
 */
enum class NumberStyle(val id: String, val title: String) {
    NUM("num", "1, 2, 3"),
    ROMAN_LOWER("romanl", "i, ii, iii"),
    ROMAN_UPPER("romanu", "I, II, III")
}

/**
 * Standard font choice.
 */
enum class NumberFont(val id: String, val title: String) {
    HELVETICA("helvetica", "Sans (Helvetica)"),
    TIMES("times", "Serif (Times)"),
    COURIER("courier", "Monospace (Courier)")
}

/**
 * Font variant style.
 */
enum class NumberFontStyle(val id: String, val title: String) {
    REGULAR("regular", "Regular"),
    BOLD("bold", "Bold"),
    ITALIC("italic", "Italic"),
    BOLD_ITALIC("bolditalic", "Bold italic")
}

/**
 * Vertical position alignment on page.
 */
enum class VerticalPosition(val id: String, val title: String) {
    TOP("top", "Top"),
    BOTTOM("bottom", "Bottom")
}

/**
 * Horizontal position alignment on page.
 */
enum class HorizontalPosition(val id: String, val title: String) {
    LEFT("left", "Left"),
    CENTER("center", "Center"),
    RIGHT("right", "Right")
}

/**
 * Predefined 6-slot position coordinate.
 */
data class PageNumberPosition(
    val v: VerticalPosition = VerticalPosition.BOTTOM,
    val h: HorizontalPosition = HorizontalPosition.CENTER
)

/**
 * Custom normalized position (0.0 to 1.0).
 */
data class CustomPosition(
    val x: Float,
    val y: Float
)

/**
 * 3-step progress steps matching HTML template.
 */
enum class PageNumberStep(val number: Int, val label: String) {
    UPLOAD(1, "Upload"),
    CUSTOMIZE(2, "Customize"),
    EXPORT(3, "Export")
}

/**
 * Metadata for chosen source PDF.
 */
data class PageNumberDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Configuration options for stamping page numbers.
 */
data class PageNumberConfig(
    val format: NumberFormat = NumberFormat.N,
    val style: NumberStyle = NumberStyle.NUM,
    val startNumber: Int = 1,
    val fromPage: Int = 1,
    val toPage: Int = 1,
    val font: NumberFont = NumberFont.HELVETICA,
    val fstyle: NumberFontStyle = NumberFontStyle.REGULAR,
    val sizePt: Float = 11f,
    val marginPt: Float = 36f,
    val opacity: Float = 1.0f,
    val colorHex: String = "#0A0A0A",
    val pos: PageNumberPosition = PageNumberPosition(),
    val customPos: CustomPosition? = null
)

/**
 * Export execution result.
 */
data class PageNumberExportResult(
    val outputFile: File,
    val numberedPagesCount: Int,
    val fileSizeBytes: Long
)

