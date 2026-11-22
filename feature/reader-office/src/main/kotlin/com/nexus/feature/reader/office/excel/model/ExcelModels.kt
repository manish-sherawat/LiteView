package com.nexus.feature.reader.office.excel.model

/** A spreadsheet cell with display text and optional styling. */
data class XlsxCell(
    val text: String,
    val rawFormula: String? = null,
    val isBold: Boolean,
    val backgroundColorHex: String? = null,
    val textColorHex: String? = null,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val isHidden: Boolean = false
)

/** A spreadsheet table row. */
data class XlsxRow(val cells: List<XlsxCell>)

sealed class ExcelReaderUiState {
    data class Loading(val progress: Float? = null, val message: String? = null) : ExcelReaderUiState()

    data class Ready(
        val sheetNames: List<String>,
        val sheetsRows: List<List<XlsxRow>>,
        val sheetsColumnWidths: List<List<Int>>,
        val currentSheet: Int = 0,
        val showGridlines: Boolean = true,
        val selectedCell: Pair<Int, Int>? = null,
        val searchQuery: String = "",
        val searchMatches: List<Pair<Int, Int>> = emptyList(),
        val currentMatchIndex: Int = 0
    ) : ExcelReaderUiState()

    data class PasswordRequired(
        val encodedUri: String,
        val docType: String,
        val isError: Boolean = false
    ) : ExcelReaderUiState()

    data class Error(val message: String) : ExcelReaderUiState()
}
