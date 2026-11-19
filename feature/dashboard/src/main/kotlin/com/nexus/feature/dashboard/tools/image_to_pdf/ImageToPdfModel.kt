package com.nexus.feature.dashboard.tools.image_to_pdf

import android.graphics.Bitmap
import android.net.Uri

enum class PageSize(val label: String, val widthPt: Float, val heightPt: Float) {
    FIT_IMAGE("Fit image", 0f, 0f),
    A4("A4", 595f, 842f),
    LETTER("Letter", 612f, 792f)
}

enum class ImageToPdfStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    ARRANGE(2, "Arrange"),
    SAVE(3, "Save")
}

data class ImagePageItem(
    val id: Long,
    val name: String,
    val uri: Uri? = null,
    val bitmap: Bitmap? = null,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDeg: Int = 0
)

data class PageGeometry(
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val imageXPt: Float,
    val imageYPt: Float,
    val imageWidthPt: Float,
    val imageHeightPt: Float
)

data class ImageToPdfConfig(
    val pageSize: PageSize = PageSize.FIT_IMAGE,
    val marginPt: Int = 16
)
