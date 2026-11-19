package com.nexus.feature.dashboard.tools.extract_pages

import android.net.Uri
import java.io.File

/**
 * 3-step progress indicator matching HTML:
 * 1 Upload -> 2 Select -> 3 Export
 */
enum class ExtractStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    SELECT(2, "Select"),
    EXPORT(3, "Export")
}

/**
 * Basic document metadata for the loaded PDF file.
 */
data class ExtractPdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Result of parsing page range strings like "1-5, 8, 12-15".
 */
sealed class RangeParseResult {
    data class Success(val pages: List<Int>) : RangeParseResult()
    data class Error(val message: String) : RangeParseResult()
}

/**
 * Output result after extracting pages into a new PDF.
 */
data class ExtractResult(
    val outputFile: File,
    val extractedPagesCount: Int,
    val fileSizeBytes: Long
)
