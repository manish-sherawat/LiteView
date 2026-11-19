package com.nexus.feature.dashboard.tools.txt_to_pdf

import android.graphics.Typeface
import android.net.Uri

/**
 * Step workflow matching the reference spec:
 * 1: Add text (dropzone / upload / paste)
 * 2: Format (workspace options & text editor)
 * 3: Convert (generation & download)
 */
enum class TxtStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Add text"),
    FORMAT(2, "Format"),
    CONVERT(3, "Convert")
}

/**
 * Supported page sizes with points dimensions (1 pt = 1/72 inch).
 */
enum class TxtPageSize(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595.28f, 841.89f),
    LETTER("Letter", 612.0f, 792.0f),
    LEGAL("Legal", 612.0f, 1008.0f),
    A5("A5", 419.53f, 595.28f)
}

/**
 * Page orientation option.
 */
enum class TxtOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

/**
 * Font families matching the reference:
 * - Sans (Helvetica)
 * - Serif (Times)
 * - Monospace (Courier)
 */
enum class TxtFontFamily(val label: String, val androidTypeface: Typeface) {
    SANS("Sans (Helvetica)", Typeface.SANS_SERIF),
    SERIF("Serif (Times)", Typeface.SERIF),
    MONOSPACE("Monospace (Courier)", Typeface.MONOSPACE)
}

/**
 * Font size options in points.
 */
enum class TxtFontSize(val label: String, val sizePt: Float) {
    PT_10("10 pt", 10f),
    PT_11("11 pt", 11f),
    PT_12("12 pt", 12f),
    PT_14("14 pt", 14f),
    PT_16("16 pt", 16f),
    PT_18("18 pt", 18f)
}

/**
 * Line spacing multiplier options.
 */
enum class TxtLineSpacing(val label: String, val multiplier: Float) {
    SINGLE("Single (1.0)", 1.0f),
    ONE_FIFTEEN("1.15", 1.15f),
    ONE_HALF("1.5", 1.5f),
    DOUBLE("Double (2.0)", 2.0f)
}

/**
 * Margin options in points.
 */
enum class TxtMarginOption(val label: String, val marginPt: Float) {
    NARROW("Narrow (0.5 in)", 36f),
    NORMAL("Normal", 56f),
    WIDE("Wide (1 in)", 72f)
}

/**
 * Form formatting configuration.
 */
data class TxtOptions(
    val pageSize: TxtPageSize = TxtPageSize.A4,
    val orientation: TxtOrientation = TxtOrientation.PORTRAIT,
    val fontFamily: TxtFontFamily = TxtFontFamily.SANS,
    val fontSize: TxtFontSize = TxtFontSize.PT_12,
    val lineSpacing: TxtLineSpacing = TxtLineSpacing.ONE_FIFTEEN,
    val margin: TxtMarginOption = TxtMarginOption.NORMAL,
    val pageNumbers: Boolean = true,
    val wrapLines: Boolean = true
)

/**
 * Loaded document metadata.
 */
data class TxtDocInfo(
    val uri: Uri? = null,
    val fileName: String = "Typed text",
    val fileMeta: String = "Write or paste below",
    val sizeBytes: Long = 0L,
    val baseName: String = "document"
)

/**
 * Live text analysis stats.
 */
data class TxtStats(
    val words: Int = 0,
    val characters: Int = 0,
    val estimatedPages: Int = 1
)

