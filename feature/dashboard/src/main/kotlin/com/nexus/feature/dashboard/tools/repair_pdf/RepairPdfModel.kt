package com.nexus.feature.dashboard.tools.repair_pdf

import android.net.Uri
import androidx.compose.ui.graphics.Color

/**
 * Step workflow matching the reference spec:
 * 1: Upload (Choose corrupted/damaged PDF)
 * 2: Repair (Configure recovery options & execute repair)
 * 3: Export (Download clean repaired PDF)
 */
enum class RepairStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    REPAIR(2, "Repair"),
    EXPORT(3, "Export")
}

/**
 * PDF initial health status detected upon reading the document.
 */
enum class PdfHealthStatus(
    val description: String,
    val indicatorColor: Color,
    val isWarning: Boolean
) {
    HEALTHY(
        description = "Opens, but may still have hidden damage",
        indicatorColor = Color(0xFF17A34A),
        isWarning = false
    ),
    REPAIR_RECOMMENDED(
        description = "Opens with errors, repair recommended",
        indicatorColor = Color(0xFFC77D00),
        isWarning = true
    ),
    REPAIR_NEEDED(
        description = "Can’t be opened normally, repair needed",
        indicatorColor = Color(0xFFC77D00),
        isWarning = true
    )
}

/**
 * Information regarding the selected damaged PDF document.
 */
data class RepairPdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int?,
    val baseName: String,
    val healthStatus: PdfHealthStatus
)

/**
 * Configurable repair options matching reference design.
 */
data class RepairPdfOptions(
    val rebuildAsImages: Boolean = true,
    val optimizeFile: Boolean = true
)

/**
 * Repair result statistics and repair notes.
 */
data class RepairPdfResult(
    val totalFound: Int,
    val recoveredIntact: Int,
    val rebuiltAsImages: Int,
    val unrecoverable: Int,
    val notes: List<String>,
    val outputFileSizeBytes: Long
)

