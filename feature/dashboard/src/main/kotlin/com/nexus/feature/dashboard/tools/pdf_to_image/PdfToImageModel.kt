package com.nexus.feature.dashboard.tools.pdf_to_image

import android.graphics.Bitmap
import android.net.Uri

enum class ExportMode(val label: String) {
    PAGES("Pages"),
    IMAGES("Images")
}

enum class ImageFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val isLossless: Boolean
) {
    JPEG("JPEG", "jpg", "image/jpeg", false),
    PNG("PNG", "png", "image/png", true),
    WEBP("WEBP", "webp", "image/webp", false)
}

enum class PagesSelectionMode(val label: String) {
    ALL("All pages"),
    CUSTOM("Custom")
}

enum class PdfToImageStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    SETTINGS(2, "Settings"),
    DOWNLOAD(3, "Download")
}

data class PdfToImageConfig(
    val mode: ExportMode = ExportMode.PAGES,
    val format: ImageFormat = ImageFormat.JPEG,
    val qualityPct: Int = 85,
    val resolutionScale: Float = 1.2f,
    val pagesSelectionMode: PagesSelectionMode = PagesSelectionMode.ALL,
    val customPagesRange: String = ""
)

data class ExtractedImageItem(
    val id: Int,
    val pageNumber: Int,
    val bitmap: Bitmap,
    val width: Int,
    val height: Int
)

data class PdfDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val basePageWidth: Int,
    val basePageHeight: Int
)
