package com.nexus.feature.reader.office.excel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.core.preferences.UserPreferencesRepository
import com.nexus.core.utils.ErrorHandler
import com.nexus.feature.dashboard.data.DocumentBookmark
import com.nexus.feature.dashboard.data.DocumentBookmarkDao
import com.nexus.feature.dashboard.data.RecentDocument
import com.nexus.feature.dashboard.data.RecentDocumentDao
import com.nexus.feature.reader.office.excel.model.ExcelReaderUiState
import com.nexus.feature.reader.office.excel.model.XlsxCell
import com.nexus.feature.reader.office.excel.model.XlsxRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.apache.poi.poifs.crypt.Decryptor
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class ExcelReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recentDocumentDao: RecentDocumentDao,
    private val bookmarkDao: DocumentBookmarkDao,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExcelReaderUiState>(ExcelReaderUiState.Loading())
    val uiState: StateFlow<ExcelReaderUiState> = _uiState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<DocumentBookmark>>(emptyList())
    val bookmarks: StateFlow<List<DocumentBookmark>> = _bookmarks.asStateFlow()

    private val _initialScrollPosition = MutableStateFlow<Pair<Int, Int>?>(null)
    val initialScrollPosition: StateFlow<Pair<Int, Int>?> = _initialScrollPosition.asStateFlow()

    private val _keepScreenAwake = MutableStateFlow(false)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    fun setKeepScreenAwake(value: Boolean) {
        viewModelScope.launch {
            prefsRepository.setKeepScreenAwake(value)
            _keepScreenAwake.value = value
        }
    }

    init {
        // Required for Apache POI 5.x on Android
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl")
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl")

        viewModelScope.launch {
            _keepScreenAwake.value = prefsRepository.keepScreenAwake.first()
        }
    }

    private fun safeDecode(str: String): String {
        return try {
            val d1 = URLDecoder.decode(str, StandardCharsets.UTF_8.toString())
            if (d1.contains("%")) {
                try { URLDecoder.decode(d1, StandardCharsets.UTF_8.toString()) } catch (_: Exception) { d1 }
            } else d1
        } catch (_: Exception) {
            str
        }
    }

    private var currentEncodedUri: String? = null
    private val _currentUriFlow = MutableStateFlow<String?>(null)

    val isStarred: StateFlow<Boolean> = combine(prefsRepository.starredUris, _currentUriFlow) { starredSet, uri ->
        uri?.let { starredSet.contains(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleFavorite(onResult: (Boolean) -> Unit) {
        val uri = currentEncodedUri ?: return
        viewModelScope.launch {
            val isNowStarred = prefsRepository.toggleStarredUri(uri)
            onResult(isNowStarred)
        }
    }

    fun loadDocument(encodedUri: String, docType: String = "XLSX", password: String? = null) {
        currentEncodedUri = encodedUri
        _currentUriFlow.value = encodedUri
        viewModelScope.launch {
            _uiState.value = ExcelReaderUiState.Loading()
            withContext(Dispatchers.IO) {
                try {
                    val decodedUri = safeDecode(encodedUri)
                    val uriStr = decodedUri

                    viewModelScope.launch(Dispatchers.IO) {
                        bookmarkDao.getBookmarksForDocument(decodedUri).collect { bks ->
                            _bookmarks.value = bks
                        }
                    }

                    val uri = Uri.parse(uriStr)

                    val fileSize = try {
                        if (uri.scheme == "file") java.io.File(uri.path ?: "").length()
                        else {
                            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (c.moveToFirst() && idx >= 0) c.getLong(idx) else 0L
                            } ?: 0L
                        }
                    } catch (_: Exception) { 0L }

                    val inputStream = openStream(uri)
                        ?: throw IllegalStateException("Cannot open file")

                    val rememberPosition = prefsRepository.rememberReadingPosition.first()
                    if (rememberPosition) {
                        val recentDoc = recentDocumentDao.findByUri(uriStr)
                        if (recentDoc != null &&
                            (recentDoc.lastScrollIndex > 0 || recentDoc.lastScrollOffset > 0)
                        ) {
                            _initialScrollPosition.value =
                                Pair(recentDoc.lastScrollIndex, recentDoc.lastScrollOffset)
                        }
                    }

                    val result = parseXlsx(inputStream, password)

                    val count = if (result is ExcelReaderUiState.Ready) result.sheetsRows.getOrNull(0)?.size ?: 0 else 0
                    if (count > 0) {
                        context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
                            .edit().putInt(uriStr, count).apply()
                    }

                    if (result is ExcelReaderUiState.Ready) {
                        try {
                            val name = try {
                                if (uri.scheme == "file") java.io.File(uri.path ?: "").name
                                else {
                                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                                    } ?: uri.lastPathSegment ?: "Spreadsheet Document"
                                }
                            } catch (_: Exception) { "Spreadsheet Document" }

                            val recentDoc = recentDocumentDao.findByUri(uriStr)
                            recentDocumentDao.upsert(
                                RecentDocument(
                                    uri = uriStr,
                                    fileName = name,
                                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    fileSizeBytes = fileSize,
                                    lastOpenedAt = System.currentTimeMillis(),
                                    documentType = "XLSX",
                                    lastScrollIndex = recentDoc?.lastScrollIndex ?: 0,
                                    lastScrollOffset = recentDoc?.lastScrollOffset ?: 0,
                                    pageCount = count
                                )
                            )
                        } catch (_: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = result
                    }
                } catch (e: org.apache.poi.EncryptedDocumentException) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = ExcelReaderUiState.PasswordRequired(encodedUri, docType, password != null)
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = ExcelReaderUiState.Error(
                            ErrorHandler.getUserFriendlyMessage(
                                e as? Exception ?: Exception(e)
                            )
                        )
                    }
                }
            }
        }
    }

    fun saveScrollPosition(encodedUri: String, index: Int, offset: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rememberPosition = prefsRepository.rememberReadingPosition.first()
                if (rememberPosition) {
                    val uriStr = safeDecode(encodedUri)
                    recentDocumentDao.updateScrollPosition(uriStr, index, offset)
                }
            } catch (_: Throwable) {}
        }
    }

    fun switchSheet(sheetIndex: Int) {
        val current = _uiState.value
        if (current is ExcelReaderUiState.Ready) {
            _uiState.value = current.copy(currentSheet = sheetIndex)
        }
    }

    fun toggleGridlines() {
        val current = _uiState.value
        if (current is ExcelReaderUiState.Ready) {
            _uiState.value = current.copy(showGridlines = !current.showGridlines)
        }
    }

    fun selectCell(row: Int, col: Int) {
        val current = _uiState.value
        if (current is ExcelReaderUiState.Ready) {
            _uiState.value = current.copy(selectedCell = Pair(row, col))
        }
    }

    private var searchJob: Job? = null

    fun setSearchQuery(query: String) {
        searchJob?.cancel()
        val current = _uiState.value
        if (current is ExcelReaderUiState.Ready) {
            if (query.isEmpty()) {
                _uiState.value = current.copy(searchQuery = query, searchMatches = emptyList(), currentMatchIndex = 0)
                return
            }
            searchJob = viewModelScope.launch(Dispatchers.Default) {
                val matches = mutableListOf<Pair<Int, Int>>()
                val rows = current.sheetsRows.getOrNull(current.currentSheet) ?: emptyList()
                for ((rIdx, row) in rows.withIndex()) {
                    yield()
                    for ((cIdx, cell) in row.cells.withIndex()) {
                        if (cell.text.contains(query, ignoreCase = true) && !cell.isHidden) {
                            matches.add(Pair(rIdx, cIdx))
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    val latest = _uiState.value
                    if (latest is ExcelReaderUiState.Ready) {
                        _uiState.value = latest.copy(
                            searchQuery = query,
                            searchMatches = matches,
                            currentMatchIndex = 0,
                            selectedCell = matches.firstOrNull()
                        )
                    }
                }
            }
        }
    }

    fun nextSearchMatch() {
        val current = _uiState.value
        if (current is ExcelReaderUiState.Ready && current.searchMatches.isNotEmpty()) {
            val nextIdx = (current.currentMatchIndex + 1) % current.searchMatches.size
            _uiState.value = current.copy(
                currentMatchIndex = nextIdx,
                selectedCell = current.searchMatches[nextIdx]
            )
        }
    }

    fun previousSearchMatch() {
        val current = _uiState.value
        if (current is ExcelReaderUiState.Ready && current.searchMatches.isNotEmpty()) {
            val prevIdx = if (current.currentMatchIndex - 1 < 0)
                current.searchMatches.size - 1
            else
                current.currentMatchIndex - 1
            _uiState.value = current.copy(
                currentMatchIndex = prevIdx,
                selectedCell = current.searchMatches[prevIdx]
            )
        }
    }

    private fun openStream(uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            java.io.FileInputStream(java.io.File(uri.path ?: return null))
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private suspend fun parseXlsx(stream: java.io.InputStream, password: String? = null): ExcelReaderUiState {
        var finalStream = stream
        var poifsToClose: POIFSFileSystem? = null

        if (password != null) {
            val poifs = POIFSFileSystem(stream)
            poifsToClose = poifs
            val info = EncryptionInfo(poifs)
            val decryptor = Decryptor.getInstance(info)
            if (!decryptor.verifyPassword(password)) {
                poifs.close()
                return ExcelReaderUiState.Error("Incorrect password.")
            }
            finalStream = decryptor.getDataStream(poifs)
        }

        try {
            WorkbookFactory.create(finalStream).use { workbook ->
                val sheetNames = (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) }
                val sheetsRows = mutableListOf<List<XlsxRow>>()
                val sheetsColumnWidths = mutableListOf<List<Int>>()
                val dataFormatter = org.apache.poi.ss.usermodel.DataFormatter()
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                for (sheetIdx in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIdx)
                    val rows = mutableListOf<XlsxRow>()

                    val mergedRegions = (0 until sheet.numMergedRegions).map { sheet.getMergedRegion(it) }
                    val maxCol = (sheet.maxOfOrNull { it.lastCellNum.toInt() } ?: 0).coerceAtMost(200)
                    val colMaxChars = IntArray(maxCol) { 0 }

                    val maxRow = sheet.lastRowNum.coerceAtMost(1000)
                    for (rowIdx in 0..maxRow) {
                        if (rowIdx % 50 == 0) yield()
                        val row = sheet.getRow(rowIdx)
                        val cells = (0 until maxCol).map { colIdx ->
                            val cell = row?.getCell(colIdx)
                            val text = if (cell != null) dataFormatter.formatCellValue(cell, evaluator) else ""
                            val rawFormula = if (cell?.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                                try { cell.cellFormula } catch (e: Exception) { null }
                            } else null

                            var colSpan = 1
                            var rowSpan = 1
                            var isHidden = false
                            for (region in mergedRegions) {
                                if (region.isInRange(rowIdx, colIdx)) {
                                    if (region.firstRow == rowIdx && region.firstColumn == colIdx) {
                                        colSpan = region.lastColumn - region.firstColumn + 1
                                        rowSpan = region.lastRow - region.firstRow + 1
                                    } else {
                                        isHidden = true
                                    }
                                    break
                                }
                            }
                            if (!isHidden) colMaxChars[colIdx] = maxOf(colMaxChars[colIdx], text.length)

                            var isBold = false
                            var bgColor: String? = null
                            var textColor: String? = null
                            val style = cell?.cellStyle
                            if (style != null) {
                                try {
                                    val font = workbook.getFontAt(style.fontIndex)
                                    isBold = font?.bold == true
                                    if (font is XSSFFont) {
                                        font.xssfColor?.rgb?.let { rgb ->
                                            if (rgb.size >= 3) {
                                                val o = if (rgb.size == 4) 1 else 0
                                                textColor = "#%02X%02X%02X".format(rgb[o].toInt() and 0xFF, rgb[o+1].toInt() and 0xFF, rgb[o+2].toInt() and 0xFF)
                                            }
                                        }
                                    }
                                    style.fillForegroundColorColor?.let { color ->
                                        if (color is XSSFColor) {
                                            color.rgb?.let { rgb ->
                                                if (rgb.size >= 3) {
                                                    val o = if (rgb.size == 4) 1 else 0
                                                    bgColor = "#%02X%02X%02X".format(rgb[o].toInt() and 0xFF, rgb[o+1].toInt() and 0xFF, rgb[o+2].toInt() and 0xFF)
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                            XlsxCell(text, rawFormula, isBold, bgColor, textColor, colSpan, rowSpan, isHidden)
                        }
                        rows.add(XlsxRow(cells))
                    }
                    sheetsRows.add(rows)
                    sheetsColumnWidths.add(colMaxChars.map { (it * 8 + 32).coerceIn(80, 260) })
                }

                return ExcelReaderUiState.Ready(
                    sheetNames = sheetNames,
                    sheetsRows = sheetsRows,
                    sheetsColumnWidths = sheetsColumnWidths
                )
            }
        } finally {
            poifsToClose?.close()
        }
    }
}
