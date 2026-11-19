package com.nexus.feature.dashboard.tools.ppt_to_pdf

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * 3-step progress indicator matching HTML:
 * 1 Upload -> 2 Select -> 3 Convert
 */
enum class PptStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    SELECT(2, "Select"),
    CONVERT(3, "Convert")
}

/**
 * Basic document metadata for the loaded PowerPoint presentation.
 */
data class PptDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val slideCount: Int,
    val baseName: String,
    val slideWidth: Float = 960f,
    val slideHeight: Float = 540f
)

/**
 * Represents an individual presentation slide item in the UI grid.
 */
data class PptSlideItem(
    val id: Int,
    val slideNumber: Int, // 1-indexed
    val title: String? = null,
    val thumbnail: Bitmap? = null,
    val fullBitmap: Bitmap? = null,
    val width: Float = 960f,
    val height: Float = 540f
) {
    val aspectRatio: Float
        get() = if (height > 0f) width / height else 16f / 9f
}

/**
 * Output result after converting PowerPoint slides into a PDF document.
 */
data class PptToPdfResult(
    val outputFile: File,
    val convertedSlidesCount: Int,
    val fileSizeBytes: Long
)

