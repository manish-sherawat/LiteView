@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.xlsx_to_pdf

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.feature.dashboard.tools.xlsx_to_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Excel / Spreadsheet to PDF conversion tool screen.
 * Completely responsive, 100% offline, on-device table layout rendering.
 */
@Composable
fun XlsxToPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // ── Main State ──────────────────────────────────────────────────────────
    var docInfo by remember { mutableStateOf<XlsxDocInfo?>(null) }
    var currentStep by remember { mutableStateOf(XlsxStep.UPLOAD) }

    var sheets by remember { mutableStateOf<List<SheetData>>(emptyList()) }
    val selectedSheetIndices = remember { mutableStateListOf<Int>() }
    var options by remember { mutableStateOf(XlsxOptions()) }

    var isLoadingWorkbook by remember { mutableStateOf(false) }

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Dialog preview state
    var previewSheetIndex by remember { mutableStateOf<Int?>(null) }

    // Floating toast feedback matching design
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun showToast(msg: String) {
        toastMessage = msg
        coroutineScope.launch {
            delay(3800)
            if (toastMessage == msg) {
                toastMessage = null
            }
        }
    }

    fun resetState() {
        if (isExporting || isLoadingWorkbook) return
        docInfo = null
        sheets = emptyList()
        selectedSheetIndices.clear()
        options = XlsxOptions()
        currentStep = XlsxStep.UPLOAD
        statusMessage = null
        exportProgress = null
        previewSheetIndex = null
    }

    // Intercept back button when in SELECT step
    BackHandler(enabled = currentStep != XlsxStep.UPLOAD) {
        resetState()
    }

    // ── PDF Save Launcher ───────────────────────────────────────────────────
    val saveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && docInfo != null && selectedSheetIndices.isNotEmpty()) {
            isExporting = true
            statusMessage = null
            exportProgress = 0.05f

            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        XlsxToPdfEngine.convertSheetsToPdf(
                            context = context,
                            docInfo = docInfo!!,
                            selectedSheetIndices = selectedSheetIndices.toSet(),
                            options = options,
                            outputStream = outStream,
                            onProgress = { current, total ->
                                exportProgress = current.toFloat() / total.toFloat()
                            }
                        )
                    }
                    val count = selectedSheetIndices.size
                    currentStep = XlsxStep.CONVERT
                    showToast("Done! Saved $count sheet${if (count == 1) "" else "s"} as PDF.")
                    delay(500)
                } catch (e: Exception) {
                    statusMessage = "Something went wrong while creating the PDF: ${e.message}"
                } finally {
                    isExporting = false
                    exportProgress = null
                }
            }
        }
    }

    // ── Excel File Picker ───────────────────────────────────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingWorkbook = true
            statusMessage = null

            coroutineScope.launch {
                try {
                    val info = XlsxToPdfEngine.readSpreadsheet(context, uri)
                    docInfo = info

                    val parsedSheets = info.sheets
                    if (parsedSheets.isEmpty()) {
                        throw IllegalStateException("No sheets found in workbook.")
                    }

                    sheets = parsedSheets
                    selectedSheetIndices.clear()
                    selectedSheetIndices.addAll(parsedSheets.map { it.index }) // Select all by default

                    currentStep = XlsxStep.SELECT
                    statusMessage = null
                } catch (e: Exception) {
                    statusMessage = "Couldn't open that file. It may be damaged or password-protected."
                } finally {
                    isLoadingWorkbook = false
                }
            }
        }
    }

    fun openFilePicker() {
        filePickerLauncher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
                "application/vnd.ms-excel", // .xls
                "application/vnd.ms-excel.sheet.macroEnabled.12", // .xlsm
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    // ── Export Action ───────────────────────────────────────────────────────
    fun triggerExport() {
        val info = docInfo ?: return
        if (selectedSheetIndices.isEmpty()) return
        saveDocLauncher.launch("${info.baseName}.pdf")
    }

    // Check if user has scrolled past top controls for floating bar
    val showFloatingBar by remember {
        derivedStateOf {
            currentStep == XlsxStep.SELECT &&
                gridState.firstVisibleItemIndex >= 1 &&
                selectedSheetIndices.isNotEmpty()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Navigation Bar ──────────────────────────────────────────
            NexusTopBar(
                title = "Excel to PDF",
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep != XlsxStep.UPLOAD) {
                            resetState()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = NexusTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    // Offline Security Pill Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0A0A0A).copy(alpha = 0.08f))
                            .border(0.8.dp, Color(0xFF0A0A0A).copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        NexusText(
                            text = "Offline",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }
            )

            // ── Scrollable Body ─────────────────────────────────────────────
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 220.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 110.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Section 1: Step Indicator (Full Span) ───────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    XlsxStepIndicator(
                        currentStep = currentStep,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // ── Section 2: Upload Dropzone (When in Step 1) ──────────────
                if (currentStep == XlsxStep.UPLOAD) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (isLoadingWorkbook) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(NexusTheme.colors.surface)
                                    .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF0A0A0A),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    NexusText(
                                        text = "Reading workbook… this can take a moment.",
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                            }
                        } else {
                            XlsxDropzone(
                                onPickFile = { openFilePicker() },
                                errorMessage = statusMessage
                            )
                        }
                    }
                }

                // ── Section 3: Controls Card (When in Step 2 / 3) ────────────
                if (currentStep != XlsxStep.UPLOAD && docInfo != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        XlsxControlsCard(
                            fileName = docInfo!!.name,
                            sheetCount = sheets.size,
                            formattedSize = XlsxToPdfEngine.formatFileSize(docInfo!!.sizeBytes),
                            selectedCount = selectedSheetIndices.size,
                            options = options,
                            onOptionsChange = { newOptions -> options = newOptions },
                            onSelectAllOrClear = {
                                if (selectedSheetIndices.size == sheets.size) {
                                    selectedSheetIndices.clear()
                                } else {
                                    selectedSheetIndices.clear()
                                    selectedSheetIndices.addAll(sheets.map { it.index })
                                }
                            },
                            onInvert = {
                                val currentSet = selectedSheetIndices.toSet()
                                val inverted = sheets.map { it.index }.filterNot { it in currentSet }
                                selectedSheetIndices.clear()
                                selectedSheetIndices.addAll(inverted)
                            },
                            onExport = { triggerExport() },
                            isExporting = isExporting,
                            progress = exportProgress,
                            errorMessage = statusMessage,
                            onChangeFile = { resetState() }
                        )
                    }

                    // ── Section 4: Grid of Worksheet Previews ───────────────
                    items(
                        items = sheets,
                        key = { it.index }
                    ) { sheet ->
                        val isSelected = selectedSheetIndices.contains(sheet.index)
                        XlsxSheetGridItem(
                            item = sheet,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedSheetIndices.remove(sheet.index)
                                } else {
                                    selectedSheetIndices.add(sheet.index)
                                }
                            },
                            onOpenPreview = {
                                previewSheetIndex = sheet.index
                            }
                        )
                    }
                }
            }
        }

        // ── Floating Bottom Export Bar ───────────────────────────────────────
        XlsxFloatingBar(
            isVisible = showFloatingBar,
            selectedCount = selectedSheetIndices.size,
            isExporting = isExporting,
            onExport = { triggerExport() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        )

        // ── Modal Sheet Preview Dialog ───────────────────────────────────────
        if (previewSheetIndex != null) {
            val previewSheet = sheets.find { it.index == previewSheetIndex }
            if (previewSheet != null) {
                val isSelected = selectedSheetIndices.contains(previewSheetIndex)

                XlsxSheetPreviewDialog(
                    sheet = previewSheet,
                    sheetNumber = previewSheet.index,
                    totalSheets = sheets.size,
                    isSelected = isSelected,
                    onToggleSelect = {
                        val idx = previewSheetIndex ?: return@XlsxSheetPreviewDialog
                        if (selectedSheetIndices.contains(idx)) {
                            selectedSheetIndices.remove(idx)
                        } else {
                            selectedSheetIndices.add(idx)
                        }
                    },
                    onDismiss = { previewSheetIndex = null }
                )
            }
        }

        // ── Success Toast Banner ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0A0A))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                NexusText(
                    text = toastMessage ?: "",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }
        }
    }
}
