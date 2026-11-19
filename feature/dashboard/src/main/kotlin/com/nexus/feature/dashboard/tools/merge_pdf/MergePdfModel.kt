package com.nexus.feature.dashboard.tools.merge_pdf

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * 3-Step workflow states matching the HTML specification.
 */
enum class MergeStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    ORDER(2, "Order"),
    SAVE(3, "Save")
}

/**
 * Information about an individual PDF added to the merge list.
 */
data class MergePdfItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val thumbnail: Bitmap? = null
)

/**
 * Result of a completed merge operation.
 */
data class MergeResult(
    val file: File,
    val sizeBytes: Long,
    val pageCount: Int,
    val fileCount: Int,
    val outputName: String
)
