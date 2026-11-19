package com.nexus.feature.dashboard.tools.xlsx_to_pdf

import android.net.Uri
import java.io.File

/**
 * 3-step progress indicator matching HTML:
 * 1 Upload -> 2 Select -> 3 Convert
 */
enum class XlsxStep(val stepNumber: Int, val label: String) {
    UPLOAD(1, "Upload"),
    SELECT(2, "Select"),
    CONVERT(3, "Convert")
}

enum class PageOrientation(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PageSizeOption(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", 595f, 842f),
    LETTER("Letter", 612f, 792f),
    A3("A3", 842f, 1191f),
    LEGAL("Legal", 612f, 1008f)
}

enum class TextSizeOption(val label: String, val sizePt: Float) {
    AUTO("Auto", 0f),
    SMALL("Small", 7f),
    MEDIUM("Medium", 9f),
    LARGE("Large", 11f)
}

data class XlsxOptions(
    val orientation: PageOrientation = PageOrientation.AUTO,
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val textSize: TextSizeOption = TextSizeOption.AUTO,
    val fitAllColumns: Boolean = true,
    val repeatHeader: Boolean = true,
    val showGridlines: Boolean = true
)

data class SheetData(
    val index: Int,
    val name: String,
    val rows: List<List<String>>,
    val colsCount: Int
)

data class XlsxDocInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val baseName: String,
    val sheets: List<SheetData>
)

data class XlsxToPdfResult(
    val outputFile: File,
    val convertedSheetsCount: Int,
    val fileSizeBytes: Long
)

