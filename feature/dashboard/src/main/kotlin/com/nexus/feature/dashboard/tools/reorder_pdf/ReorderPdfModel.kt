package com.nexus.feature.dashboard.tools.reorder_pdf

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * 3-step progress indicator state matching HTML:
 * 1 Upload -> 2 Reorder -> 3 Save
 */
enum class ReorderStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    REORDER(2, "Reorder"),
    SAVE(3, "Save")
}

/**
 * Basic document metadata for loaded PDF.
 */
data class ReorderPdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Representation of an individual page in the reordered sequence.
 * [pageNumber] is the 1-indexed original page number.
 */
data class ReorderPageItem(
    val pageNumber: Int,
    var thumbnail: Bitmap? = null
)

/**
 * Output result after saving reordered PDF.
 */
data class ReorderPdfResult(
    val outputFile: File,
    val pageCount: Int
)
