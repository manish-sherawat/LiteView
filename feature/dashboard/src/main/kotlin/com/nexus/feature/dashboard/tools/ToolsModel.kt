package com.nexus.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BrandingWatermark
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexus.feature.dashboard.R

// ─── Tool Categories ─────────────────────────────────────────────────────────

enum class ToolCategory(val title: String) {
    ALL("All Tools"),
    CONVERT("Convert"),
    ORGANIZE("Organize"),
    EDIT("Edit & Secure")
}

// ─── Tool Data Model ──────────────────────────────────────────────────────────

data class Tool(
    val id: String,
    val title: String,
    val description: String,
    val subtitle: String = "",
    val category: ToolCategory = ToolCategory.ALL,
    val accentColor: Color = Color(0xFF0A0A0A),
    val icon: ImageVector? = null,
    val iconRes: Int? = null,
    val badge: String? = null,
    val isHero: Boolean = false,
    val isAvailable: Boolean = true,
    val keywords: String = ""
)

// ─── Centralized Tools Registry (Clean Minimalist: All Badges Removed & Respective Icons) ───

val toolsList = listOf(
    Tool(
        id = "image_to_pdf",
        title = "Image to PDF",
        subtitle = "Images to PDF",
        description = "Convert photos & scans into clean PDF",
        category = ToolCategory.CONVERT,
        accentColor = Color.White,
        iconRes = R.drawable.img_to_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "jpg png photo scan picture jpeg convert camera gallery"
    ),
    Tool(
        id = "pdf_to_image",
        title = "PDF to Images",
        subtitle = "PDF to JPEG / PNG",
        description = "PDF to JPEG / PNG / WEBP",
        category = ToolCategory.CONVERT,
        accentColor = Color.White,
        icon = Icons.Outlined.Collections,
        iconRes = R.drawable.pdf_to_img,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "jpg png webp export pictures extract photos"
    ),
    Tool(
        id = "word_to_pdf",
        title = "Word to PDF",
        subtitle = "DOCX to PDF",
        description = "DOCX to PDF",
        category = ToolCategory.CONVERT,
        accentColor = Color.White,
        icon = Icons.Outlined.Description,
        iconRes = R.drawable.word_to_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "docx doc document word office microsoft pages"
    ),
    Tool(
        id = "pdf_to_docx",
        title = "PDF to Word",
        subtitle = "PDF to DOCX",
        description = "Turn any PDF into an editable Word file",
        category = ToolCategory.CONVERT,
        accentColor = Color.White,
        icon = Icons.Outlined.Description,
        iconRes = R.drawable.pdf_to_docx,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "pdf to word docx document convert edit extract paragraphs text"
    ),
    Tool(
        id = "excel_to_pdf",
        title = "Excel to PDF",
        subtitle = "Spreadsheets to PDF",
        description = "Spreadsheets to PDF",
        category = ToolCategory.CONVERT,
        accentColor = Color.White,
        icon = Icons.Outlined.TableChart,
        iconRes = R.drawable.excel_to_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "xlsx xls csv sheet spreadsheet excel table workbook"
    ),
    Tool(
        id = "ppt_to_pdf",
        title = "PowerPoint to PDF",
        subtitle = "Slides to PDF",
        description = "Slides to PDF, one or many per page",
        category = ToolCategory.CONVERT,
        accentColor = Color.White,
        icon = Icons.Outlined.Slideshow,
        iconRes = R.drawable.ppt_to_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "pptx ppt slides presentation deck powerpoint keynote"
    ),
    Tool(
        id = "txt_to_pdf",
        title = "Text to PDF",
        subtitle = "Text notes to PDF",
        description = "Text notes to PDF",
        category = ToolCategory.CONVERT,
        accentColor = Color.White,
        icon = Icons.Outlined.TextFields,
        iconRes = R.drawable.txt_to_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "txt notes plain paste text code log md markdown"
    ),
    Tool(
        id = "merge_pdf",
        title = "Merge PDFs",
        subtitle = "Combine files",
        description = "Combine files",
        category = ToolCategory.ORGANIZE,
        accentColor = Color.White,
        icon = Icons.AutoMirrored.Outlined.CallMerge,
        iconRes = R.drawable.merge_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "combine join together merge unite bundle stitch"
    ),
    Tool(
        id = "split_pdf",
        title = "Split PDF",
        subtitle = "Cut into parts or ZIP",
        description = "Cut a PDF by page ranges or into equal parts",
        category = ToolCategory.ORGANIZE,
        accentColor = Color.White,
        icon = Icons.Outlined.ContentCut,
        iconRes = R.drawable.split_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "split cut divide separate range chunk zip parts pdf every pages"
    ),
    Tool(
        id = "extract_pages",
        title = "Extract Pages",
        subtitle = "Pick pages",
        description = "Save picked pages as a new PDF",
        category = ToolCategory.ORGANIZE,
        accentColor = Color.White,
        icon = Icons.Outlined.ContentCut,
        iconRes = R.drawable.extract_pages,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "split select pages range extract separate slice"
    ),
    Tool(
        id = "rotate_pdf",
        title = "Rotate Pages",
        subtitle = "Fix orientation",
        description = "Fix sideways or upside-down pages",
        category = ToolCategory.ORGANIZE,
        accentColor = Color.White,
        icon = Icons.AutoMirrored.Outlined.RotateRight,
        iconRes = R.drawable.rotate_pages,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "turn orientation flip rotate pages portrait landscape 90 180"
    ),
    Tool(
        id = "reorder_pdf",
        title = "Reorder Pages",
        subtitle = "Drag pages",
        description = "Drag pages into a new order",
        category = ToolCategory.ORGANIZE,
        accentColor = Color.White,
        icon = Icons.Outlined.Reorder,
        iconRes = R.drawable.reorder_pages,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "sort arrange move order reorder drag rearrange sequence"
    ),
    Tool(
        id = "crop_pdf",
        title = "Crop Pages",
        subtitle = "Trim PDF margins",
        description = "Crop Your PDF Pages",
        category = ToolCategory.ORGANIZE,
        accentColor = Color.White,
        icon = Icons.Outlined.ContentCut,
        iconRes = R.drawable.crop_pages,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "crop trim margin margins cut frame area rectangle page size pdf"
    ),
    Tool(
        id = "delete_pages",
        title = "Delete Pages",
        subtitle = "Remove unwanted pages",
        description = "Delete any page, effortlessly",
        category = ToolCategory.ORGANIZE,
        accentColor = Color.White,
        icon = Icons.Default.Delete,
        iconRes = R.drawable.delete_pages,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "delete remove discard pages clean cut drop remove pages pdf"
    ),
    Tool(
        id = "sign_pdf",
        title = "Sign PDF",
        subtitle = "Add your signature",
        description = "Draw, type or upload a signature",
        category = ToolCategory.EDIT,
        accentColor = Color.White,
        icon = Icons.Outlined.Draw,
        iconRes = R.drawable.sign_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "signature stamp esign sign draw type autograph initial"
    ),
    Tool(
        id = "watermark_pdf",
        title = "Watermark PDF",
        subtitle = "Stamp text",
        description = "Stamp words like DRAFT on every page",
        category = ToolCategory.EDIT,
        accentColor = Color.White,
        icon = Icons.AutoMirrored.Outlined.BrandingWatermark,
        iconRes = R.drawable.watermark_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "draft confidential stamp watermark text label copyright"
    ),
    Tool(
        id = "page_number",
        title = "Page Number",
        subtitle = "Add page numbers",
        description = "Add Page Number to PDF Pages",
        category = ToolCategory.EDIT,
        accentColor = Color.White,
        icon = Icons.Outlined.Description,
        iconRes = R.drawable.page_number,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "page number numbering footer header numbers stamp count format pdf pages"
    ),
    Tool(
        id = "header_footer",
        title = "Header & Footer",
        subtitle = "Add header & footer",
        description = "Add Header And Footer To The PDF pages",
        category = ToolCategory.EDIT,
        accentColor = Color.White,
        icon = Icons.Outlined.Description,
        iconRes = R.drawable.header_footer,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "header footer top bottom title date page number numbering stamp repeat running pdf"
    ),
    Tool(
        id = "compress_pdf",
        title = "Compress PDF",
        subtitle = "Make it smaller",
        description = "Make the file smaller, preview first",
        category = ToolCategory.EDIT,
        accentColor = Color.White,
        icon = Icons.Outlined.Compress,
        iconRes = R.drawable.compress_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "reduce size shrink smaller optimize compress mb kb squish"
    ),
    Tool(
        id = "protect_pdf",
        title = "Protect PDF",
        subtitle = "Add a password",
        description = "Lock a PDF with a password",
        category = ToolCategory.EDIT,
        accentColor = Color.White,
        icon = Icons.Outlined.Lock,
        iconRes = R.drawable.protect_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "password encrypt secure lock protect privacy pin code"
    ),
    Tool(
        id = "repair_pdf",
        title = "Repair PDF",
        subtitle = "Fix broken files",
        description = "Recover pages from a damaged PDF",
        category = ToolCategory.EDIT,
        accentColor = Color.White,
        icon = Icons.Outlined.Build,
        iconRes = R.drawable.repair_pdf,
        badge = null,
        isHero = false,
        isAvailable = true,
        keywords = "fix corrupt corrupted damaged broken recover repair rebuild error"
    )
)
