package com.nexus.feature.dashboard.tools.crop_pages

import android.net.Uri
import java.io.File

/**
 * Normalized crop rectangle [0.0..1.0] relative to visible page top-left origin.
 */
data class CropRect(
    val x0: Float = 0f,
    val y0: Float = 0f,
    val x1: Float = 1f,
    val y1: Float = 1f
) {
    val isFull: Boolean get() = x0 <= 0.005f && y0 <= 0.005f && x1 >= 0.995f && y1 >= 0.995f
    val widthShare: Float get() = (x1 - x0).coerceIn(0.01f, 1f)
    val heightShare: Float get() = (y1 - y0).coerceIn(0.01f, 1f)

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/**
 * 3-step progress steps matching HTML template.
 */
enum class CropStep(val number: Int, val label: String) {
    UPLOAD(1, "Upload"),
    CROP(2, "Crop"),
    EXPORT(3, "Export")
}

/**
 * Metadata for chosen source PDF.
 */
data class CropDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Export execution result.
 */
data class CropExportResult(
    val outputFile: File,
    val croppedPagesCount: Int,
    val fileSizeBytes: Long
)

/**
 * Predefined aspect ratios for cropping.
 */
enum class QuickRatio(val id: String, val label: String, val ratio: Float?) {
    FREE("free", "Full page", null),
    SQUARE("1", "Square", 1.0f),
    FOUR_THREE("1.3333", "4:3", 4f / 3f),
    THREE_FOUR("0.75", "3:4", 3f / 4f),
    SIXTEEN_NINE("1.7778", "16:9", 16f / 9f),
    A4("0.7071", "A4", 0.7071f)
}

