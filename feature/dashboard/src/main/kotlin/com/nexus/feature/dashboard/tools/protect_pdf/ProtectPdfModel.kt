package com.nexus.feature.dashboard.tools.protect_pdf

import android.net.Uri
import androidx.compose.ui.graphics.Color

/**
 * Step workflow matching the reference spec:
 * 1: Upload (Select PDF)
 * 2: Protect (Configure password & permissions)
 * 3: Export (Download encrypted PDF)
 */
enum class ProtectStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    PROTECT(2, "Protect"),
    EXPORT(3, "Export")
}

/**
 * PDF document info.
 */
data class ProtectPdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Password strength levels with color accents.
 */
enum class PasswordStrength(
    val score: Int,
    val label: String,
    val color: Color
) {
    EMPTY(0, "", Color(0xFFEBEBEB)),
    WEAK(1, "Weak", Color(0xFFE5484D)),
    FAIR(2, "Fair", Color(0xFFC77D00)),
    GOOD(3, "Good", Color(0xFF6D9A14)),
    STRONG(4, "Strong", Color(0xFF17A34A))
}

/**
 * Permission limits after opening the protected PDF.
 */
data class ProtectPdfPermissions(
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowEdit: Boolean = true
)

/**
 * Protect PDF operation result.
 */
data class ProtectPdfResult(
    val fileSizeBytes: Long,
    val pageCount: Int
)

