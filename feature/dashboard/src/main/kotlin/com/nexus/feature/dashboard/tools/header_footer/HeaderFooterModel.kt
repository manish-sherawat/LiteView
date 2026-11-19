package com.nexus.feature.dashboard.tools.header_footer

import android.net.Uri
import java.io.File

/**
 * 3-step progress steps matching HTML template specification.
 */
enum class HeaderFooterStep(val number: Int, val label: String) {
    UPLOAD(1, "Upload"),
    CUSTOMIZE(2, "Customize"),
    EXPORT(3, "Export")
}

/**
 * Font families supported by standard PDF fonts.
 */
enum class HFFontFamily(val id: String, val label: String) {
    SANS("sans", "Sans"),
    SERIF("serif", "Serif"),
    MONO("mono", "Mono")
}

/**
 * Font weight / style.
 */
enum class HFFontStyle(val id: String, val label: String) {
    REGULAR("normal", "Regular"),
    BOLD("bold", "Bold"),
    ITALIC("italic", "Italic")
}

/**
 * 6 Header / Footer layout slots.
 */
enum class HFSlot(val id: String, val label: String, val isHeader: Boolean) {
    HL("hl", "Header Left", true),
    HC("hc", "Header Center", true),
    HR("hr", "Header Right", true),
    FL("fl", "Footer Left", false),
    FC("fc", "Footer Center", false),
    FR("fr", "Footer Right", false)
}

/**
 * Per-page Header & Footer configuration.
 */
data class PageHFConfig(
    val hl: String = "",
    val hc: String = "",
    val hr: String = "",
    val fl: String = "",
    val fc: String = "",
    val fr: String = "",
    val font: HFFontFamily = HFFontFamily.SANS,
    val style: HFFontStyle = HFFontStyle.REGULAR,
    val size: Int = 10,
    val startNum: Int = 1,
    val colorHex: String = "#0A0A0A",
    val vMargin: Int = 28,
    val hMargin: Int = 36,
    val ruleH: Boolean = false,
    val ruleF: Boolean = false,
    val mask: Boolean = false
) {
    val hasHeaderText: Boolean get() = hl.isNotBlank() || hc.isNotBlank() || hr.isNotBlank()
    val hasFooterText: Boolean get() = fl.isNotBlank() || fc.isNotBlank() || fr.isNotBlank()
    val hasAnyText: Boolean get() = hasHeaderText || hasFooterText
    val isActive: Boolean get() = hasAnyText || ruleH || ruleF

    operator fun get(slot: HFSlot): String = when (slot) {
        HFSlot.HL -> hl
        HFSlot.HC -> hc
        HFSlot.HR -> hr
        HFSlot.FL -> fl
        HFSlot.FC -> fc
        HFSlot.FR -> fr
    }

    fun copyWithSlot(slot: HFSlot, value: String): PageHFConfig = when (slot) {
        HFSlot.HL -> copy(hl = value)
        HFSlot.HC -> copy(hc = value)
        HFSlot.HR -> copy(hr = value)
        HFSlot.FL -> copy(fl = value)
        HFSlot.FC -> copy(fc = value)
        HFSlot.FR -> copy(fr = value)
    }
}

/**
 * Source document metadata.
 */
data class HeaderFooterDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Result of the Header & Footer export operation.
 */
data class HeaderFooterExportResult(
    val outputFile: File? = null,
    val outputUri: Uri? = null,
    val outputFileName: String = "",
    val pagesUpdatedCount: Int,
    val fileSizeBytes: Long
)

