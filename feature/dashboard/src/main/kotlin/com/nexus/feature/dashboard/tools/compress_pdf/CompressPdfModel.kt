package com.nexus.feature.dashboard.tools.compress_pdf

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * 3-Step workflow states matching the HTML specification.
 */
enum class CompressStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    COMPRESS(2, "Compress"),
    DOWNLOAD(3, "Download")
}

/**
 * Standard compression quality presets.
 */
enum class CompressPreset(val label: String, val quality: Int) {
    LOW("Low", 20),
    MEDIUM("Medium", 50),
    HIGH("High", 80)
}

/**
 * User compression configuration.
 */
data class CompressPdfConfig(
    val quality: Int = 50,
    val keepTextSelectable: Boolean = true
) {
    val levelT: Float
        get() = (quality - 10) / 90f

    /**
     * Compression parameters computed from quality level t [0..1]:
     * - q: JPEG compression quality factor (0.20..0.90)
     * - s: Image downscaling factor (0.35..1.00)
     * - dpi: Raster rendering target DPI (60..200)
     */
    val jpegQualityFactor: Float
        get() = (0.20f + 0.70f * levelT).coerceIn(0.15f, 0.95f)

    val scaleFactor: Float
        get() = (0.35f + 0.65f * levelT).coerceIn(0.25f, 1.0f)

    val rasterDpi: Float
        get() = (60f + 140f * levelT).coerceIn(50f, 300f)

    val qualityName: String
        get() = when {
            quality == 20 -> "Low"
            quality == 50 -> "Medium"
            quality == 80 -> "High"
            quality < 35 -> "Custom · Low"
            quality < 65 -> "Custom · Medium"
            else -> "Custom · High"
        }
}

/**
 * Information regarding the selected PDF document.
 */
data class CompressPdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val firstPageBitmap: Bitmap? = null,
    val basePageWidth: Int = 595,
    val basePageHeight: Int = 842
)

/**
 * Pre-calculated estimates for each preset.
 */
data class CompressEstimates(
    val lowBytes: Long,
    val medBytes: Long,
    val highBytes: Long
)

/**
 * Result of a completed compression execution.
 */
data class CompressResult(
    val key: String,
    val file: File,
    val sizeBytes: Long,
    val originalSizeBytes: Long
) {
    val savingsBytes: Long
        get() = originalSizeBytes - sizeBytes

    val savingsPercent: Int
        get() = if (originalSizeBytes > 0) {
            (((originalSizeBytes - sizeBytes).toDouble() / originalSizeBytes) * 100).toInt()
        } else 0
}
