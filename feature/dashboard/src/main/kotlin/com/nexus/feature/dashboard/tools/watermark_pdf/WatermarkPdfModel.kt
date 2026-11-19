package com.nexus.feature.dashboard.tools.watermark_pdf

import android.graphics.Bitmap
import android.net.Uri

/**
 * Watermark layout modes.
 * SINGLE: One central watermark per page.
 * TILED: Repeating grid of diagonal watermarks across the entire page.
 */
enum class WatermarkLayoutMode(val label: String) {
    SINGLE("Single"),
    TILED("Tiled")
}

/**
 * Ink color presets for watermark text.
 */
enum class WatermarkInk(val label: String, val grayLevel: Float) {
    BLACK("Black", 0.05f),
    GRAY("Gray", 0.50f)
}

/**
 * Steps in the watermarking workflow matching the progress indicator.
 */
enum class WatermarkStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    CUSTOMIZE(2, "Customize"),
    DOWNLOAD(3, "Download")
}

/**
 * State of the watermark generation job.
 */
enum class WatermarkProcessState {
    IDLE,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * User configuration for the watermark.
 */
data class WatermarkConfig(
    val text: String = "DRAFT",
    val sizePt: Float = 96f,
    val opacityPct: Int = 18,
    val rotationDeg: Float = 45f,
    val layoutMode: WatermarkLayoutMode = WatermarkLayoutMode.SINGLE,
    val ink: WatermarkInk = WatermarkInk.BLACK
) {
    companion object {
        val DEFAULTS = WatermarkConfig()
        val PRESET_TEXTS = listOf("DRAFT", "CONFIDENTIAL", "COPY", "SAMPLE", "DO NOT COPY")
        val PRESET_ANGLES = listOf(-45f, 0f, 45f, 90f)
    }
}

/**
 * Metadata for the selected PDF file.
 */
data class PdfFileInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val firstPageBitmap: Bitmap? = null
)
