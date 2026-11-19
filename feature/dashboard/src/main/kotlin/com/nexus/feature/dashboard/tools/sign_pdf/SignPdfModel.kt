package com.nexus.feature.dashboard.tools.sign_pdf

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * High-level progress step matching the 3-step indicator from HTML:
 * 1 Upload -> 2 Sign -> 3 Export
 */
enum class SignStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    SIGN(2, "Sign"),
    EXPORT(3, "Export")
}

/**
 * Tab in the workspace card: "Document" vs "Stamps (N)".
 */
enum class SignWorkspaceTab {
    DOCUMENT,
    STAMPS
}

/**
 * Input mode inside the Add Signature modal dialog: Draw, Type, Upload.
 */
enum class SignatureInputMode {
    DRAW,
    TYPE,
    UPLOAD
}

/**
 * Pen stroke thickness options in Draw mode.
 */
enum class PenWeight(val strokeWidth: Float, val label: String) {
    THIN(4f, "Thin"),
    MEDIUM(7f, "Medium"),
    BOLD(11f, "Bold")
}

/**
 * Script / cursive font choices in Type mode.
 */
enum class ScriptFont(val displayName: String) {
    DANCING_SCRIPT("Dancing Script"),
    GREAT_VIBES("Great Vibes"),
    ALLURA("Allura"),
    CAVEAT("Caveat")
}

/**
 * Represetation of a reusable signature asset created via Draw, Type, or Upload.
 */
data class SignatureItem(
    val id: Long,
    val bitmap: Bitmap,
    val aspectRatio: Float // width / height
)

/**
 * A placed signature stamp positioned on a specific PDF page.
 * Coordinates are normalized (0.0f..1.0f) relative to page bounds:
 * [x] is left fraction, [y] is top fraction, [widthRatio] is width fraction.
 */
data class PlacedStamp(
    val id: Long,
    val sigId: Long,
    val page: Int, // 1-indexed page number
    var x: Float,
    var y: Float,
    var widthRatio: Float,
    val aspectRatio: Float
)

/**
 * Basic document metadata for the loaded PDF file.
 */
data class SignPdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Export execution result.
 */
data class SignPdfResult(
    val outputFile: File? = null,
    val outputUri: Uri? = null,
    val outputFileName: String = "",
    val stampsCount: Int
)
