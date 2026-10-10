package com.nexus.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// ─── Tool Categories ─────────────────────────────────────────────────────────

enum class ToolCategory(val title: String) {
    ALL("All Tools"),
    CONVERT("Convert"),
    ORGANIZE("Organize"),
    SECURITY("Security & Size")
}

// ─── Tool Data Model ──────────────────────────────────────────────────────────

data class Tool(
    val id: String,
    val title: String,
    val description: String,
    val subtitle: String = "",
    val category: ToolCategory = ToolCategory.ALL,
    val accentColor: Color = Color(0xFF2563EB),
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val badge: String? = null,
    val isHero: Boolean = false,
    val isAvailable: Boolean = true
)

// ─── Centralized Tools Registry ───────────────────────────────────────────────

val toolsList = listOf(
    Tool(
        id = "image_to_pdf",
        title = "Image to PDF",
        subtitle = "Images to PDF",
        description = "Convert photos, scans and gallery images into a clean PDF document",
        category = ToolCategory.CONVERT,
        accentColor = Color(0xFFEF4444), // Coral Red
        iconRes = com.nexus.feature.dashboard.R.drawable.img_to_pdf,
        badge = "Popular",
        isHero = true,
        isAvailable = true
    ),
    Tool(
        id = "word_to_pdf",
        title = "Word to PDF",
        subtitle = "DOCX to PDF",
        description = "Convert Word documents preserving typography and layouts",
        category = ToolCategory.CONVERT,
        accentColor = Color(0xFF2563EB), // Royal Blue
        iconRes = com.nexus.feature.dashboard.R.drawable.word_to_pdf,
        badge = "Popular",
        isHero = false,
        isAvailable = true
    ),
    Tool(
        id = "xlsx_to_pdf",
        title = "Excel to PDF",
        subtitle = "Spreadsheets to PDF",
        description = "Convert spreadsheets and data tables into formatted PDF pages",
        category = ToolCategory.CONVERT,
        accentColor = Color(0xFF10B981), // Emerald Green
        iconRes = com.nexus.core.R.drawable.ic_file_sheet_new,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    ),
    Tool(
        id = "txt_to_pdf",
        title = "Text to PDF",
        subtitle = "Text notes to PDF",
        description = "Render plain text notes and code into paginated PDFs",
        category = ToolCategory.CONVERT,
        accentColor = Color(0xFF6366F1), // Indigo
        iconRes = com.nexus.core.R.drawable.ic_file_txt_new,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    ),
    Tool(
        id = "merge_pdf",
        title = "Merge PDFs",
        subtitle = "Combine files",
        description = "Join multiple PDF files into a single unified document",
        category = ToolCategory.ORGANIZE,
        accentColor = Color(0xFF8B5CF6), // Purple
        icon = Icons.Rounded.PictureAsPdf,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    ),
    Tool(
        id = "split_pdf",
        title = "Split PDF",
        subtitle = "Extract pages",
        description = "Extract specific page ranges or split into separate documents",
        category = ToolCategory.ORGANIZE,
        accentColor = Color(0xFFEC4899), // Pink
        icon = Icons.Rounded.PictureAsPdf,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    ),
    Tool(
        id = "rotate_pdf",
        title = "Rotate Pages",
        subtitle = "Fix orientation",
        description = "Rotate individual or all pages in 90° increments",
        category = ToolCategory.ORGANIZE,
        accentColor = Color(0xFFF59E0B), // Amber
        iconRes = com.nexus.core.R.drawable.ic_rotate,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    ),
    Tool(
        id = "protect_pdf",
        title = "Protect PDF",
        subtitle = "Encrypt & lock",
        description = "Add password encryption to secure your sensitive documents",
        category = ToolCategory.SECURITY,
        accentColor = Color(0xFF06B6D4), // Cyan
        iconRes = com.nexus.core.R.drawable.ic_lock,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    ),
    Tool(
        id = "compress_pdf",
        title = "Compress PDF",
        subtitle = "Reduce file size",
        description = "Reduce oversized PDF files while preserving sharpness",
        category = ToolCategory.SECURITY,
        accentColor = Color(0xFF14B8A6), // Teal
        iconRes = com.nexus.core.R.drawable.ic_maximize,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    ),
    Tool(
        id = "repair_pdf",
        title = "Repair PDF",
        subtitle = "Fix corrupted file",
        description = "Diagnose and rebuild corrupted or unreadable PDF files",
        category = ToolCategory.SECURITY,
        accentColor = Color(0xFF64748B), // Slate
        icon = Icons.AutoMirrored.Rounded.Article,
        badge = "Coming soon",
        isHero = false,
        isAvailable = false
    )
)
