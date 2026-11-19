package com.nexus.feature.dashboard.tools.delete_pages

import android.net.Uri
import java.io.File

/**
 * 3-step progress steps matching HTML template specification.
 */
enum class DeleteStep(val number: Int, val label: String) {
    UPLOAD(1, "Upload"),
    SELECT(2, "Select"),
    EXPORT(3, "Export")
}

/**
 * Document metadata for the chosen source PDF.
 */
data class DeleteDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Export execution result after deleting marked pages.
 */
data class DeleteExportResult(
    val outputFile: File,
    val deletedPagesCount: Int,
    val remainingPagesCount: Int,
    val fileSizeBytes: Long
)

