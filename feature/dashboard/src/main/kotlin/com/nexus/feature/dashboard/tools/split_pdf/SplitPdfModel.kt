package com.nexus.feature.dashboard.tools.split_pdf

import android.net.Uri
import java.io.File

/**
 * Split method mode matching HTML template.
 */
enum class SplitMode(val title: String) {
    EVERY("Every N pages"),
    RANGE("Custom ranges")
}

/**
 * 3-step progress indicators matching HTML template.
 */
enum class SplitStep(val number: Int, val label: String) {
    UPLOAD(1, "Upload"),
    SPLIT(2, "Split"),
    EXPORT(3, "Export")
}

/**
 * Metadata for the selected source PDF document.
 */
data class SplitDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Represents a single cut / output part of the split document.
 */
data class SplitPart(
    val index: Int,
    val startPage: Int, // 1-indexed
    val endPage: Int    // 1-indexed
) {
    val pageCount: Int get() = (endPage - startPage + 1).coerceAtLeast(1)
    val label: String get() = if (startPage == endPage) "Page $startPage" else "Pages $startPage–$endPage"
}

/**
 * Range validation result.
 */
sealed class SplitRangeResult {
    data class Success(val parts: List<SplitPart>) : SplitRangeResult()
    data class Error(val message: String) : SplitRangeResult()
}

/**
 * Split operation export result.
 */
data class SplitExportResult(
    val outputFiles: List<File>,
    val zipFile: File?,
    val totalParts: Int,
    val totalBytes: Long
)

