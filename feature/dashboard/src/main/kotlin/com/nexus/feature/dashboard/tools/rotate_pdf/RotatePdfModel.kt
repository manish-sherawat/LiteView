package com.nexus.feature.dashboard.tools.rotate_pdf

import android.net.Uri

/**
 * Step progression matching the reference spec:
 * 1: Upload (Select PDF file)
 * 2: Rotate (Interactive grid and rotation actions)
 * 3: Export (Save rotated document)
 */
enum class RotateStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    ROTATE(2, "Rotate"),
    EXPORT(3, "Export")
}

/**
 * Loaded PDF file information.
 */
data class RotatePdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * UI model representing each PDF page card in the grid.
 */
data class RotatePageItem(
    val pageNumber: Int, // 1-indexed (1..pageCount)
    val initialRotation: Int = 0, // Native rotation from PDF
    val rotationDelta: Int = 0 // Relative user rotation (0, 90, 180, 270)
) {
    /**
     * Absolute final rotation angle applied to the page.
     */
    val effectiveRotation: Int
        get() = ((initialRotation + rotationDelta) % 360 + 360) % 360

    /**
     * True if the user has changed this page's orientation from its original rotation.
     */
    val isTurned: Boolean
        get() = rotationDelta != 0
}

/**
 * Result data returned upon saving the rotated PDF.
 */
data class RotatePdfResult(
    val totalRotatedPages: Int,
    val pageCount: Int,
    val fileSizeBytes: Long
)

