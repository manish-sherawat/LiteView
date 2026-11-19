package com.nexus.feature.dashboard.tools.xlsx_to_pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object XlsxToPdfEngine {

    /**
     * Formats bytes into human-readable string matching the HTML spec:
     * - < 1000 B: "N B"
     * - < 1 MB: "N.1 kB"
     * - >= 1 MB: "N.2 MB"
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format(Locale.US, "%.1f kB", bytes / 1000.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    /**
     * Reads metadata and sheet contents from an Excel (.xlsx, .xls, .xlsm) or CSV (.csv) file.
     */
    suspend fun readSpreadsheet(context: Context, uri: Uri): XlsxDocInfo = withContext(Dispatchers.IO) {
        var fileName = "Spreadsheet.xlsx"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    cursor.getString(nameIndex)?.let { fileName = it }
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        val baseName = fileName.replace(Regex("(?i)\\.(xlsx|xls|xlsm|csv|ods)$"), "")
        val tempFile = copyUriToTempFile(context, uri, fileName)
        val lowerName = fileName.lowercase(Locale.US)
        val sheets = mutableListOf<SheetData>()

        try {
            when {
                lowerName.endsWith(".csv") -> {
                    val csvRows = parseCsvFile(tempFile)
                    val colsCount = csvRows.maxOfOrNull { it.size } ?: 0
                    sheets.add(
                        SheetData(
                            index = 1,
                            name = baseName.ifBlank { "Sheet 1" },
                            rows = csvRows,
                            colsCount = colsCount
                        )
                    )
                }
                lowerName.endsWith(".xls") -> {
                    FileInputStream(tempFile).use { fis ->
                        val workbook = HSSFWorkbook(fis)
                        for (i in 0 until workbook.numberOfSheets) {
                            val sheet = workbook.getSheetAt(i)
                            sheets.add(extractSheetData(sheet, i + 1))
                        }
                    }
                }
                else -> {
                    // Default to OOXML (.xlsx, .xlsm, .ods)
                    FileInputStream(tempFile).use { fis ->
                        val workbook = XSSFWorkbook(fis)
                        for (i in 0 until workbook.numberOfSheets) {
                            val sheet = workbook.getSheetAt(i)
                            sheets.add(extractSheetData(sheet, i + 1))
                        }
                    }
                }
            }
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        XlsxDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            baseName = baseName,
            sheets = sheets
        )
    }

    private fun extractSheetData(sheet: Sheet, index: Int): SheetData {
        val rawRows = mutableListOf<List<String>>()
        val maxCol = 100 // Reasonable safety bound

        for (row in sheet) {
            val cellList = mutableListOf<String>()
            val lastCellNum = min(row.lastCellNum.toInt(), maxCol)
            for (c in 0 until max(0, lastCellNum)) {
                val cell = row.getCell(c)
                cellList.add(getCellValue(cell))
            }
            rawRows.add(cellList)
        }

        // Trim trailing empty rows
        while (rawRows.isNotEmpty() && rawRows.last().all { it.isBlank() }) {
            rawRows.removeAt(rawRows.size - 1)
        }

        // Find max non-empty column count
        var cols = 0
        for (r in rawRows) {
            var lastNonEmpty = r.size
            while (lastNonEmpty > 0 && r[lastNonEmpty - 1].isBlank()) {
                lastNonEmpty--
            }
            if (lastNonEmpty > cols) cols = lastNonEmpty
        }

        // Normalize all rows to length cols
        val normalizedRows = rawRows.map { row ->
            val trimmed = row.take(cols).toMutableList()
            while (trimmed.size < cols) trimmed.add("")
            trimmed
        }

        return SheetData(
            index = index,
            name = sheet.sheetName ?: "Sheet $index",
            rows = normalizedRows,
            colsCount = cols
        )
    }

    private fun getCellValue(cell: Cell?): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue?.trim() ?: ""
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            sdf.format(cell.dateCellValue)
                        } catch (_: Exception) {
                            cell.numericCellValue.toString()
                        }
                    } else {
                        val num = cell.numericCellValue
                        if (num == num.toLong().toDouble()) {
                            num.toLong().toString()
                        } else {
                            num.toString()
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        cell.stringCellValue?.trim() ?: ""
                    } catch (_: Exception) {
                        try {
                            val num = cell.numericCellValue
                            if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                        } catch (_: Exception) {
                            cell.cellFormula ?: ""
                        }
                    }
                }
                CellType.BLANK -> ""
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseCsvFile(file: File): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.isBlank()) continue
                val parsed = parseCsvLine(currentLine)
                rows.add(parsed)
            }
        }
        return rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    /**
     * Converts selected sheets into a beautifully paginated PDF document.
     */
    suspend fun convertSheetsToPdf(
        context: Context,
        docInfo: XlsxDocInfo,
        selectedSheetIndices: Set<Int>,
        options: XlsxOptions,
        outputStream: OutputStream,
        onProgress: (current: Int, total: Int) -> Unit
    ): XlsxToPdfResult = withContext(Dispatchers.IO) {
        val sheetsToConvert = docInfo.sheets
            .filter { it.index in selectedSheetIndices && it.rows.isNotEmpty() }
            .sortedBy { it.index }

        if (sheetsToConvert.isEmpty()) {
            throw IllegalArgumentException("The selected sheets are empty.")
        }

        val pdfDocument = PdfDocument()
        val tempExportFile = File(context.cacheDir, "${docInfo.baseName}_converted_${System.currentTimeMillis()}.pdf")

        val totalSheets = sheetsToConvert.size
        var globalPageNumber = 1

        try {
            for ((sheetIndex, sheet) in sheetsToConvert.withIndex()) {
                val cols = sheet.colsCount
                val rows = sheet.rows
                if (cols == 0 || rows.isEmpty()) continue

                // 1. Determine orientation
                val isLandscape = when (options.orientation) {
                    PageOrientation.AUTO -> cols > 6
                    PageOrientation.PORTRAIT -> false
                    PageOrientation.LANDSCAPE -> true
                }

                val baseW = options.pageSize.widthPt
                val baseH = options.pageSize.heightPt
                val pageW = if (isLandscape) max(baseW, baseH).toInt() else min(baseW, baseH).toInt()
                val pageH = if (isLandscape) min(baseW, baseH).toInt() else max(baseW, baseH).toInt()

                // Margins
                val marginLeft = 28f
                val marginRight = 28f
                val marginTop = 36f
                val marginBottom = 34f
                val usableW = pageW.toFloat() - marginLeft - marginRight
                val usableH = pageH.toFloat() - marginTop - marginBottom

                // Text size
                val baseFontSize = when (options.textSize) {
                    TextSizeOption.AUTO -> {
                        if (options.fitAllColumns) {
                            max(5.5f, min(9f, 9f - (cols - 6) * 0.45f))
                        } else {
                            8.5f
                        }
                    }
                    else -> options.textSize.sizePt
                }

                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = baseFontSize
                    color = Color.BLACK
                }

                val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = baseFontSize
                    color = Color.WHITE
                    isFakeBoldText = true
                }

                val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 8f
                    color = Color.rgb(120, 120, 120)
                }

                val borderPaint = Paint().apply {
                    color = Color.rgb(207, 207, 207)
                    style = Paint.Style.STROKE
                    strokeWidth = 0.5f
                }

                val headerBgPaint = Paint().apply {
                    color = Color.rgb(10, 10, 10)
                    style = Paint.Style.FILL
                }

                val altRowBgPaint = Paint().apply {
                    color = Color.rgb(250, 250, 250)
                    style = Paint.Style.FILL
                }

                // Calculate column widths
                val colWidths = FloatArray(cols)
                for (c in 0 until cols) {
                    var maxLen = 0f
                    val sampleRows = rows.take(40)
                    for (r in sampleRows) {
                        val text = r.getOrNull(c) ?: ""
                        val w = textPaint.measureText(text)
                        if (w > maxLen) maxLen = w
                    }
                    colWidths[c] = (maxLen + 12f).coerceIn(36f, 320f)
                }

                // If fitAllColumns: Scale column widths to exactly match usableW
                if (options.fitAllColumns) {
                    val sumWidths = colWidths.sum()
                    if (sumWidths > 0f) {
                        val factor = usableW / sumWidths
                        for (c in 0 until cols) {
                            colWidths[c] = colWidths[c] * factor
                        }
                    }
                }

                val lineHeight = (baseFontSize * 1.35f).coerceAtLeast(14f)
                val cellPaddingV = if (options.fitAllColumns) 3f else 4.5f
                val rowHeight = lineHeight + (cellPaddingV * 2)

                val hasHeader = options.repeatHeader && rows.size > 1
                val headerRow = if (hasHeader) rows.first() else null
                val dataRows = if (hasHeader) rows.drop(1) else rows

                var rowIndex = 0
                while (rowIndex < dataRows.size || rowIndex == 0) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, globalPageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // 1. Draw top metadata (Sheet name and page number)
                    canvas.drawText(sheet.name, marginLeft, 22f, metaPaint)
                    val pageText = "Page $globalPageNumber"
                    val pageTextW = metaPaint.measureText(pageText)
                    canvas.drawText(pageText, pageW - marginRight - pageTextW, pageH - 16f, metaPaint)

                    var curY = marginTop

                    // 2. Draw Table Header if enabled
                    if (headerRow != null) {
                        drawTableRow(
                            canvas = canvas,
                            row = headerRow,
                            colWidths = colWidths,
                            startX = marginLeft,
                            startY = curY,
                            height = rowHeight,
                            isHeader = true,
                            isAlt = false,
                            textPaint = headerTextPaint,
                            bgPaint = headerBgPaint,
                            borderPaint = borderPaint,
                            showGridlines = options.showGridlines
                        )
                        curY += rowHeight
                    }

                    // 3. Draw Data Rows on this page
                    var isAlt = false
                    while (rowIndex < dataRows.size) {
                        if (curY + rowHeight > pageH - marginBottom) {
                            // Page full, break to start next page
                            break
                        }
                        val r = dataRows[rowIndex]
                        drawTableRow(
                            canvas = canvas,
                            row = r,
                            colWidths = colWidths,
                            startX = marginLeft,
                            startY = curY,
                            height = rowHeight,
                            isHeader = false,
                            isAlt = isAlt,
                            textPaint = textPaint,
                            bgPaint = if (isAlt) altRowBgPaint else null,
                            borderPaint = borderPaint,
                            showGridlines = options.showGridlines
                        )
                        curY += rowHeight
                        isAlt = !isAlt
                        rowIndex++
                    }

                    pdfDocument.finishPage(page)
                    globalPageNumber++
                    if (dataRows.isEmpty()) break
                }

                onProgress(sheetIndex + 1, totalSheets)
            }

            // Save PDF
            pdfDocument.writeTo(outputStream)

            XlsxToPdfResult(
                outputFile = tempExportFile,
                convertedSheetsCount = sheetsToConvert.size,
                fileSizeBytes = tempExportFile.length()
            )
        } finally {
            try { pdfDocument.close() } catch (_: Exception) {}
        }
    }

    private fun drawTableRow(
        canvas: Canvas,
        row: List<String>,
        colWidths: FloatArray,
        startX: Float,
        startY: Float,
        height: Float,
        isHeader: Boolean,
        isAlt: Boolean,
        textPaint: Paint,
        bgPaint: Paint?,
        borderPaint: Paint,
        showGridlines: Boolean
    ) {
        var curX = startX
        for (c in colWidths.indices) {
            val w = colWidths[c]
            val cellText = row.getOrNull(c) ?: ""

            // Background
            if (bgPaint != null) {
                canvas.drawRect(curX, startY, curX + w, startY + height, bgPaint)
            }

            // Border
            if (showGridlines) {
                canvas.drawRect(curX, startY, curX + w, startY + height, borderPaint)
            }

            // Text
            if (cellText.isNotEmpty()) {
                val clipLeft = curX + 4f
                val clipRight = curX + w - 4f
                val textY = startY + (height / 2f) + (textPaint.textSize / 3f)

                canvas.save()
                canvas.clipRect(clipLeft, startY, clipRight, startY + height)
                canvas.drawText(cellText, clipLeft, textY, textPaint)
                canvas.restore()
            }

            curX += w
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri, originalName: String): File {
        val extension = when {
            originalName.lowercase(Locale.US).endsWith(".csv") -> ".csv"
            originalName.lowercase(Locale.US).endsWith(".xls") -> ".xls"
            originalName.lowercase(Locale.US).endsWith(".xlsm") -> ".xlsm"
            else -> ".xlsx"
        }
        val tempFile = File(context.cacheDir, "xlsx_import_${System.currentTimeMillis()}$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open spreadsheet from Uri: $uri")
        return tempFile
    }
}

