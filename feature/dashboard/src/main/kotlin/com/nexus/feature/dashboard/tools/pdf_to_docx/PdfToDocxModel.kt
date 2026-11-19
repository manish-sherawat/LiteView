package com.nexus.feature.dashboard.tools.pdf_to_docx

import android.net.Uri
import java.io.File

/**
 * 3-step progress steps matching HTML template specification.
 */
enum class PdfToDocxStep(val number: Int, val label: String) {
    UPLOAD(1, "Upload"),
    REVIEW(2, "Review & Tune"),
    EXPORT(3, "Download .docx")
}

/**
 * Conversion mode: Text, Hybrid, or Visual.
 */
enum class ConversionMode(val id: String, val title: String, val badge: String, val desc: String) {
    TEXT(
        "text",
        "Editable Text (.docx)",
        "Best for editing",
        "Reconstructs headings, paragraphs, and bullet lists into clean, flowing Word text."
    ),
    HYBRID(
        "hybrid",
        "Text + Page Snapshot",
        "Text + Visuals",
        "Places editable text first, followed by a crisp snapshot of the page for tables or diagrams."
    ),
    VISUAL(
        "visual",
        "Exact Visual Pages",
        "For scans",
        "Embeds each page as a high-resolution image inside Word. Ideal for scanned PDFs."
    )
}

/**
 * Word font family options.
 */
enum class WordFontFamily(val fontName: String, val label: String) {
    CALIBRI("Calibri", "Calibri"),
    ARIAL("Arial", "Arial"),
    TIMES("Times New Roman", "Times"),
    GEORGIA("Georgia", "Georgia")
}

/**
 * Word line spacing options.
 */
enum class WordLineSpacing(val value: Float, val label: String) {
    SINGLE(1.0f, "Single (1.0×)"),
    MODERN(1.15f, "Modern (1.15×)"),
    RELAXED(1.5f, "Relaxed (1.5×)")
}

/**
 * Word page margin options.
 */
enum class WordMargin(val twips: Int, val label: String) {
    NARROW(720, "Narrow (0.5\")"),
    NORMAL(1440, "Normal (1\")"),
    WIDE(1800, "Wide (1.25\")")
}

/**
 * Block types for structured document flow.
 */
enum class BlockType(val label: String) {
    H1("HEADING 1"),
    H2("HEADING 2"),
    LI("LIST ITEM"),
    P("PARAGRAPH")
}

/**
 * Editable text block representation.
 */
data class WordBlock(
    val type: BlockType,
    val text: String
)

/**
 * Document metadata.
 */
data class PdfToDocxDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val baseName: String
)

/**
 * Export execution result.
 */
data class PdfToDocxExportResult(
    val outputFile: File? = null,
    val outputUri: Uri? = null,
    val outputFileName: String = "",
    val pagesConvertedCount: Int,
    val fileSizeBytes: Long,
    val isTxt: Boolean = false
)

