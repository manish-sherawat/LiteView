@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.pdf_to_docx

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PDF to Word (DOCX) Tool Screen implementing the precision HTML template specification.
 * Converts PDF pages into editable OpenXML .docx documents and formatted plain text (.txt).
 */
@Composable
fun PdfToDocxScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colors = NexusTheme.colors

    // ─── State ───────────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(PdfToDocxStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<PdfToDocxDocInfo?>(null) }
    var curPage by remember { mutableIntStateOf(1) } // 1-indexed

    // Preview mode: "word" (flow preview) or "pdf" (original canvas)
    var viewMode by remember { mutableStateOf("word") }

    // Conversion settings
    var conversionMode by remember { mutableStateOf(ConversionMode.TEXT) }
    var detectHeadings by remember { mutableStateOf(true) }
    var mergeLines by remember { mutableStateOf(true) }
    var pageBreaks by remember { mutableStateOf(true) }

    // Word Typography
    var wordFont by remember { mutableStateOf(WordFontFamily.CALIBRI) }
    var fontSizePt by remember { mutableIntStateOf(11) }
    var lineSpacing by remember { mutableStateOf(WordLineSpacing.MODERN) }
    var wordMargin by remember { mutableStateOf(WordMargin.NORMAL) }

    // Pages inclusion set
    val includedPages = remember { mutableStateListOf<Int>() }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val pageBlocks = remember { mutableStateMapOf<Int, List<WordBlock>>() }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Deck tab (0: Mode, 1: Word Style, 2: Pages)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Range input
    var rangeInput by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }

    // Exporting
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportResult by remember { mutableStateOf<PdfToDocxExportResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    // Dialogs
    var showChangeFileDialog by remember { mutableStateOf(false) }

    // Toast
    var toastMessage by remember { mutableStateOf<String?>(null) }
    fun showToast(msg: String) {
        toastMessage = msg
        coroutineScope.launch {
            delay(3500)
            if (toastMessage == msg) {
                toastMessage = null
            }
        }
    }

    // Load page thumbnail & extracted text blocks
    fun loadPageData(doc: PdfToDocxDocInfo, pageNum: Int) {
        if (!pageBitmaps.containsKey(pageNum) || !pageBlocks.containsKey(pageNum)) {
            isLoadingPreview = true
            coroutineScope.launch {
                try {
                    if (!pageBitmaps.containsKey(pageNum)) {
                        val bmp = PdfToDocxEngine.renderThumbnail(context, doc.uri, pageNum - 1, 600)
                        pageBitmaps[pageNum] = bmp
                    }
                    if (!pageBlocks.containsKey(pageNum)) {
                        val blocks = PdfToDocxEngine.extractPageBlocks(
                            context = context,
                            uri = doc.uri,
                            pageIndex = pageNum - 1,
                            detectHeadings = detectHeadings,
                            mergeLines = mergeLines
                        )
                        pageBlocks[pageNum] = blocks
                    }
                } catch (_: Exception) {}
                finally {
                    isLoadingPreview = false
                }
            }
        }
    }

    // PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            statusMessage = "Analyzing PDF…"
            isStatusError = false
            coroutineScope.launch {
                try {
                    val info = PdfToDocxEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    curPage = 1
                    viewMode = "word"
                    conversionMode = ConversionMode.TEXT
                    detectHeadings = true
                    mergeLines = true
                    pageBreaks = true
                    includedPages.clear()
                    includedPages.addAll(1..info.pageCount)
                    pageBitmaps.clear()
                    pageBlocks.clear()
                    rangeInput = ""
                    rangeError = null
                    statusMessage = null
                    currentStep = PdfToDocxStep.REVIEW

                    // Preload first page
                    loadPageData(info, 1)

                    // Preload neighboring thumbnails
                    for (p in 2..minOf(info.pageCount, 6)) {
                        loadPageData(info, p)
                    }
                } catch (e: Exception) {
                    statusMessage = "Couldn’t open that PDF. It may be password-protected or damaged."
                    isStatusError = true
                }
            }
        }
    }

    // Launcher for saving the DOCX file safely via SAF
    val saveDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { destUri: Uri? ->
        val doc = docInfo
        if (destUri != null && doc != null) {
            isExporting = true
            exportProgress = 0f
            statusMessage = null
            isStatusError = false

            coroutineScope.launch {
                try {
                    for (p in includedPages) {
                        if (!pageBlocks.containsKey(p)) {
                            val b = PdfToDocxEngine.extractPageBlocks(context, doc.uri, p - 1, detectHeadings, mergeLines)
                            pageBlocks[p] = b
                        }
                    }

                    val result = PdfToDocxEngine.generateDocx(
                        context = context,
                        docInfo = doc,
                        pagesToInclude = includedPages.toList(),
                        blocksPerPage = pageBlocks.toMap(),
                        mode = conversionMode,
                        font = wordFont,
                        sizePt = fontSizePt,
                        spacing = lineSpacing,
                        margin = wordMargin,
                        pageBreaks = pageBreaks,
                        destUri = destUri,
                        outputFileName = "${doc.baseName}.docx",
                        onProgress = { p -> exportProgress = p }
                    )
                    exportResult = result
                    currentStep = PdfToDocxStep.EXPORT
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showToast("Done! Word file saved successfully.")
                } catch (e: Exception) {
                    statusMessage = "Something went wrong while creating the Word file: ${e.message}"
                    isStatusError = true
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // Launcher for saving the TXT file safely via SAF
    val saveTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { destUri: Uri? ->
        val doc = docInfo
        if (destUri != null && doc != null) {
            isExporting = true
            statusMessage = null
            isStatusError = false

            coroutineScope.launch {
                try {
                    for (p in includedPages) {
                        if (!pageBlocks.containsKey(p)) {
                            val b = PdfToDocxEngine.extractPageBlocks(context, doc.uri, p - 1, detectHeadings, mergeLines)
                            pageBlocks[p] = b
                        }
                    }

                    val result = PdfToDocxEngine.exportPlainText(
                        context = context,
                        docInfo = doc,
                        pagesToInclude = includedPages.toList(),
                        blocksPerPage = pageBlocks.toMap(),
                        destUri = destUri,
                        outputFileName = "${doc.baseName}.txt"
                    )
                    exportResult = result
                    currentStep = PdfToDocxStep.EXPORT
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showToast("Done! Plain text file saved successfully.")
                } catch (e: Exception) {
                    statusMessage = "Failed to export plain text: ${e.message}"
                    isStatusError = true
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // Export DOCX handler
    fun startDocxExport() {
        val doc = docInfo ?: return
        if (includedPages.isEmpty() || isExporting) return
        saveDocxLauncher.launch("${doc.baseName}.docx")
    }

    // Export TXT handler
    fun startTxtExport() {
        val doc = docInfo ?: return
        if (includedPages.isEmpty() || isExporting) return
        saveTxtLauncher.launch("${doc.baseName}.txt")
    }

    val isScrolled by remember { derivedStateOf { scrollState.value > 10 } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Procedural dot-grid background accent
        PdfToDocxDotCanvas()

        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky Top Bar
            PdfToDocxTopBar(
                onBack = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                },
                isScrolled = isScrolled
            )

            // Scrollable Body Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 16.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 115.dp
                    )
            ) {
                NexusText(
                    text = "Turn any PDF into an editable Word file.",
                    style = NexusTheme.typography.titleLarge.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.1).sp,
                        lineHeight = 32.sp
                    ),
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                NexusText(
                    text = "Extract paragraphs, headings, and lists into a clean, editable .docx document—or include crisp page snapshots for scanned pages. Everything runs privately on your device.",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Steps Indicator
                PdfToDocxStepsRow(currentStep = currentStep)

                Spacer(modifier = Modifier.height(24.dp))

                // ─── STEP 1: UPLOAD DROPZONE ──────────────────────────────────
                if (currentStep == PdfToDocxStep.UPLOAD) {
                    PdfToDocxUploadStep(
                        onSelectPdf = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        statusMessage = statusMessage,
                        isStatusError = isStatusError
                    )
                }

                // ─── STEP 2: REVIEW & CONVERT WORKSPACE ───────────────────────
                if (currentStep == PdfToDocxStep.REVIEW && docInfo != null) {
                    val info = docInfo!!

                    // 1. Interactive Preview Stage Card
                    PdfToDocxPreviewStage(
                        info = info,
                        curPage = curPage,
                        viewMode = viewMode,
                        onViewModeChange = { viewMode = it },
                        curBmp = pageBitmaps[curPage],
                        blocks = pageBlocks[curPage],
                        isLoadingPreview = isLoadingPreview,
                        isIncluded = includedPages.contains(curPage),
                        onToggleInclude = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (includedPages.contains(curPage)) {
                                if (includedPages.size > 1) includedPages.remove(curPage)
                            } else {
                                includedPages.add(curPage)
                            }
                        },
                        onCopyText = {
                            val blocks = pageBlocks[curPage] ?: emptyList()
                            val pageText = blocks.joinToString("\n\n") { it.text }
                            clipboard.setText(AnnotatedString(pageText))
                            showToast("Copied page $curPage text to clipboard.")
                        },
                        onPrevPage = {
                            if (curPage > 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                curPage--
                                loadPageData(info, curPage)
                            }
                        },
                        onNextPage = {
                            if (curPage < info.pageCount) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                curPage++
                                loadPageData(info, curPage)
                            }
                        },
                        onUpdateBlockText = { index, newText ->
                            val currentList = pageBlocks[curPage]?.toMutableList() ?: mutableListOf()
                            if (index in currentList.indices) {
                                currentList[index] = currentList[index].copy(text = newText)
                                pageBlocks[curPage] = currentList
                            }
                        },
                        wordFont = wordFont,
                        fontSizePt = fontSizePt
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. 3-Tab Segmented Control Deck
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(20.dp))
                    ) {
                        Column {
                            // Deck Tab Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("1. Conversion Mode", "2. Word Style", "3. Pages").forEachIndexed { idx, label ->
                                    val isSelected = selectedTab == idx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) colors.surface else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.dp else 0.dp,
                                                color = if (isSelected) colors.divider else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedTab = idx
                                            }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = label,
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) colors.textPrimary else colors.textSecondary
                                        )
                                    }
                                }
                            }

                            // Deck Panes
                            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                                when (selectedTab) {
                                    0 -> PdfToDocxModeTab(
                                        conversionMode = conversionMode,
                                        onModeChange = { conversionMode = it },
                                        detectHeadings = detectHeadings,
                                        onDetectHeadingsChange = {
                                            detectHeadings = it
                                            pageBlocks.clear()
                                            loadPageData(info, curPage)
                                        },
                                        mergeLines = mergeLines,
                                        onMergeLinesChange = {
                                            mergeLines = it
                                            pageBlocks.clear()
                                            loadPageData(info, curPage)
                                        },
                                        pageBreaks = pageBreaks,
                                        onPageBreaksChange = { pageBreaks = it }
                                    )
                                    1 -> PdfToDocxStyleTab(
                                        wordFont = wordFont,
                                        onFontChange = { wordFont = it },
                                        fontSizePt = fontSizePt,
                                        onFontSizeChange = { fontSizePt = it },
                                        lineSpacing = lineSpacing,
                                        onLineSpacingChange = { lineSpacing = it },
                                        wordMargin = wordMargin,
                                        onMarginChange = { wordMargin = it }
                                    )
                                    2 -> PdfToDocxPagesTab(
                                        curPage = curPage,
                                        totalPages = info.pageCount,
                                        includedPages = includedPages.toList(),
                                        onIncludeAll = {
                                            includedPages.clear()
                                            includedPages.addAll(1..info.pageCount)
                                            showToast("All ${info.pageCount} pages included.")
                                        },
                                        onSkipFirst = {
                                            includedPages.clear()
                                            includedPages.addAll(2..info.pageCount)
                                            showToast("First page skipped.")
                                        },
                                        onOddOnly = {
                                            includedPages.clear()
                                            includedPages.addAll((1..info.pageCount).filter { it % 2 == 1 })
                                            showToast("Odd pages included.")
                                        },
                                        onEvenOnly = {
                                            includedPages.clear()
                                            includedPages.addAll((1..info.pageCount).filter { it % 2 == 0 })
                                            showToast("Even pages included.")
                                        },
                                        onApplyRange = { rng ->
                                            val parsed = PdfToDocxEngine.parseRanges(rng, info.pageCount)
                                            parsed.fold(
                                                onSuccess = { list ->
                                                    includedPages.clear()
                                                    includedPages.addAll(list)
                                                    rangeInput = ""
                                                    rangeError = null
                                                    showToast("Included ${list.size} pages from range.")
                                                },
                                                onFailure = { err ->
                                                    rangeError = err.message ?: "Invalid range."
                                                }
                                            )
                                        },
                                        rangeInput = rangeInput,
                                        rangeError = rangeError,
                                        onRangeInputChange = {
                                            rangeInput = it
                                            rangeError = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Page Thumbnails Carousel
                    PdfToDocxThumbnailsStrip(
                        info = info,
                        curPage = curPage,
                        includedPages = includedPages.toList(),
                        pageBitmaps = pageBitmaps,
                        pageBlocks = pageBlocks,
                        onSelectPage = { p ->
                            curPage = p
                            loadPageData(info, p)
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. Selected File Summary Card
                    PdfToDocxDocSummaryCard(
                        info = info,
                        includedCount = includedPages.size,
                        onChangeFile = {
                            showChangeFileDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5. Dual Export CTAs (.docx and .txt)
                    val canExport = includedPages.isNotEmpty() && !isExporting
                    val docxLabel = when {
                        isExporting -> "Generating Word Document…"
                        includedPages.isEmpty() -> "Select at least 1 page to convert"
                        else -> "Download Word (.docx) · ${includedPages.size} page${if (includedPages.size == 1) "" else "s"}"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (canExport) colors.primary else colors.surfaceVariant)
                            .border(width = 1.dp, color = if (canExport) colors.primary else colors.divider, shape = RoundedCornerShape(16.dp))
                            .springBounceClick {
                                if (canExport) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startDocxExport()
                                }
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                            }
                            NexusText(
                                text = docxLabel,
                                style = NexusTheme.typography.buttonLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                color = if (canExport) colors.onPrimary else colors.textSecondary.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary TXT Export
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                            .springBounceClick {
                                if (canExport) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    startTxtExport()
                                }
                            }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "Download Plain Text (.txt)",
                            style = NexusTheme.typography.buttonLabel.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        NexusText(
                            text = statusMessage ?: "",
                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                            color = if (isStatusError) colors.error else colors.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ─── STEP 3: EXPORT SUCCESS ───────────────────────────────────
                if (currentStep == PdfToDocxStep.EXPORT && exportResult != null) {
                    PdfToDocxExportCard(
                        res = exportResult!!,
                        onOpenFile = {
                            try {
                                val mime = if (exportResult!!.isTxt) "text/plain" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                val fileUri = exportResult!!.outputUri ?: (if (exportResult!!.outputFile != null) FileProvider.getUriForFile(context, "${context.packageName}.provider", exportResult!!.outputFile!!) else null)
                                if (fileUri != null) {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(fileUri, mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(viewIntent, "Open File"))
                                } else {
                                    showToast("File not found.")
                                }
                            } catch (_: Exception) {
                                showToast("Unable to open file.")
                            }
                        },
                        onShareFile = {
                            try {
                                val mime = if (exportResult!!.isTxt) "text/plain" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                val fileUri = exportResult!!.outputUri ?: (if (exportResult!!.outputFile != null) FileProvider.getUriForFile(context, "${context.packageName}.provider", exportResult!!.outputFile!!) else null)
                                if (fileUri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = mime
                                        putExtra(Intent.EXTRA_STREAM, fileUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                                } else {
                                    showToast("File not found.")
                                }
                            } catch (_: Exception) {
                                showToast("Unable to share file.")
                            }
                        },
                        onProcessAnother = {
                            currentStep = PdfToDocxStep.UPLOAD
                            docInfo = null
                            includedPages.clear()
                            pageBitmaps.clear()
                            pageBlocks.clear()
                            exportResult = null
                        }
                    )
                }
            }
        }

        // ─── Floating Toast Notification ──────────────────────────────────────
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 95.dp, start = 24.dp, end = 24.dp)
                .zIndex(20f)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp))
            ) {
                NexusText(
                    text = toastMessage ?: "",
                    style = NexusTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.onPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ─── Confirm Dialog: Change File ──────────────────────────────────────
        if (showChangeFileDialog) {
            AlertDialog(
                onDismissRequest = { showChangeFileDialog = false },
                title = { NexusText("Change PDF file?", style = NexusTheme.typography.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary) },
                text = { NexusText("Going back will discard your live text edits in the preview.", style = NexusTheme.typography.body.copy(fontSize = 14.sp), color = colors.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        showChangeFileDialog = false
                        currentStep = PdfToDocxStep.UPLOAD
                        docInfo = null
                        includedPages.clear()
                        pageBitmaps.clear()
                        pageBlocks.clear()
                    }) {
                        NexusText("Change file", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeFileDialog = false }) {
                        NexusText("Cancel", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.sp), color = colors.textPrimary)
                    }
                },
                containerColor = colors.surface
            )
        }
    }
}

// ─── Sub-Composables ──────────────────────────────────────────────────────────

@Composable
private fun PdfToDocxDotCanvas() {
    val colors = NexusTheme.colors
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        val dotColor = colors.divider.copy(alpha = 0.38f)
        val dotRadius = 1.1.dp.toPx()
        val spacing = 22.dp.toPx()
        val width = size.width
        val height = size.height

        var x = spacing / 2
        while (x < width) {
            var y = spacing / 2
            while (y < height) {
                val alphaFade = (1f - (y / height)).coerceIn(0f, 1f)
                drawCircle(
                    color = dotColor.copy(alpha = dotColor.alpha * alphaFade),
                    radius = dotRadius,
                    center = Offset(x, y)
                )
                y += spacing
            }
            x += spacing
        }
    }
}

@Composable
private fun PdfToDocxTopBar(
    onBack: () -> Unit,
    isScrolled: Boolean
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.96f))
            .border(
                width = if (isScrolled) 1.dp else 0.dp,
                color = if (isScrolled) colors.divider.copy(alpha = 0.6f) else Color.Transparent
            )
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp,
                start = 16.dp,
                end = 20.dp,
                bottom = 12.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.divider, CircleShape)
                    .springBounceClick { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.pdf_to_docx),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            NexusText(
                text = "PDF to Word",
                style = NexusTheme.typography.title.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp
                ),
                color = colors.textPrimary
            )
        }
    }
}

@Composable
private fun PdfToDocxStepsRow(currentStep: PdfToDocxStep) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PdfToDocxStep.entries.forEachIndexed { idx, step ->
            val isActive = currentStep.number >= step.number
            val isCurrent = currentStep == step

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent) colors.primary else if (isActive) colors.surfaceVariant else colors.surface)
                        .border(
                            width = 1.5.dp,
                            color = if (isActive) colors.primary else colors.divider,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "${step.number}",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isCurrent) colors.onPrimary else if (isActive) colors.primary else colors.textSecondary
                    )
                }

                NexusText(
                    text = step.label,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isActive) colors.textPrimary else colors.textSecondary.copy(alpha = 0.6f)
                )
            }

            if (idx < PdfToDocxStep.entries.size - 1) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.5.dp)
                        .background(if (currentStep.number > step.number) colors.primary else colors.divider)
                )
            }
        }
    }
}

@Composable
private fun PdfToDocxUploadStep(
    onSelectPdf: () -> Unit,
    statusMessage: String?,
    isStatusError: Boolean
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
            .springBounceClick { onSelectPdf() }
            .padding(vertical = 44.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(width = 140.dp, height = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 96.dp)
                        .offset(x = (-18).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.7f))
                        .border(1.5.dp, colors.divider, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(modifier = Modifier.size(width = 30.dp, height = 5.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                        Box(modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(width = 74.dp, height = 100.dp)
                        .offset(x = 18.dp, y = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.5.dp, colors.primary, RoundedCornerShape(8.dp))
                        .shadow(6.dp)
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2B579A)), contentAlignment = Alignment.Center) {
                                NexusText("W", style = NexusTheme.typography.caption.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Box(modifier = Modifier.size(width = 24.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(3.5.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                        Box(modifier = Modifier.fillMaxWidth(0.9f).height(3.5.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(3.5.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                    }
                }
            }

            NexusText(
                text = "Tap to choose a PDF file",
                style = NexusTheme.typography.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            NexusText(
                text = "Documents, contracts, ebooks, resumes, or lecture notes. Converted into real, editable Word formatting.",
                style = NexusTheme.typography.body.copy(fontSize = 13.5.sp),
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary)
                    .padding(horizontal = 22.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "Select PDF",
                    style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = colors.onPrimary
                )
            }
        }
    }

    if (statusMessage != null) {
        Spacer(modifier = Modifier.height(14.dp))
        NexusText(
            text = statusMessage,
            style = NexusTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = if (isStatusError) colors.error else colors.primary
        )
    }
}

@Composable
private fun PdfToDocxPreviewStage(
    info: PdfToDocxDocInfo,
    curPage: Int,
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    curBmp: Bitmap?,
    blocks: List<WordBlock>?,
    isLoadingPreview: Boolean,
    isIncluded: Boolean,
    onToggleInclude: () -> Unit,
    onCopyText: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onUpdateBlockText: (Int, String) -> Unit,
    wordFont: WordFontFamily,
    fontSizePt: Int
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.divider, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // View Mode Segmented Switcher (Word Flow vs Original PDF)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (viewMode == "word") colors.primary else Color.Transparent)
                        .clickable { onViewModeChange("word") }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "Word Flow Preview",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = if (viewMode == "word") FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (viewMode == "word") colors.onPrimary else colors.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (viewMode == "pdf") colors.primary else Color.Transparent)
                        .clickable { onViewModeChange("pdf") }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "Original PDF Canvas",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = if (viewMode == "pdf") FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (viewMode == "pdf") colors.onPrimary else colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (curPage > 1) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.4f))
                        .border(1.dp, colors.divider, CircleShape)
                        .clickable(enabled = curPage > 1) { onPrevPage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Previous page",
                        tint = if (curPage > 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexusText(
                        text = "Page $curPage of ${info.pageCount}",
                        style = NexusTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )

                    // Included badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isIncluded) colors.primary.copy(alpha = 0.12f) else colors.error.copy(alpha = 0.12f))
                            .border(1.dp, if (isIncluded) colors.primary else colors.error, CircleShape)
                            .clickable { onToggleInclude() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        NexusText(
                            text = if (isIncluded) "Included" else "Skipped",
                            style = NexusTheme.typography.caption.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                            color = if (isIncluded) colors.primary else colors.error
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (curPage < info.pageCount) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.4f))
                        .border(1.dp, colors.divider, CircleShape)
                        .clickable(enabled = curPage < info.pageCount) { onNextPage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Next page",
                        tint = if (curPage < info.pageCount) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stage Canvas Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, colors.divider, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPreview) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.primary, strokeWidth = 2.5.dp)
                        NexusText(text = "Extracting page $curPage…", style = NexusTheme.typography.caption.copy(fontSize = 12.sp), color = colors.textSecondary)
                    }
                } else if (viewMode == "pdf" && curBmp != null) {
                    // Original PDF Page Snapshot
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(curBmp.width.toFloat() / curBmp.height.toFloat())
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .shadow(4.dp)
                    ) {
                        Image(
                            bitmap = curBmp.asImageBitmap(),
                            contentDescription = "Page $curPage",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }
                } else {
                    // Word Flow Layout (simulated paper page)
                    val fontCompose = when (wordFont) {
                        WordFontFamily.CALIBRI, WordFontFamily.ARIAL -> FontFamily.SansSerif
                        WordFontFamily.TIMES, WordFontFamily.GEORGIA -> FontFamily.Serif
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.92f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.divider, RoundedCornerShape(6.dp))
                            .shadow(3.dp)
                            .padding(14.dp)
                    ) {
                        val currentBlocks = blocks ?: emptyList()
                        if (currentBlocks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                NexusText(
                                    text = "No selectable text on this page.\n(Scanned page? Use 'Exact Visual Pages' mode)",
                                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                currentBlocks.forEachIndexed { idx, blk ->
                                    EditableBlockRow(
                                        block = blk,
                                        fontFamily = fontCompose,
                                        fontSize = fontSizePt.sp,
                                        onTextChange = { txt -> onUpdateBlockText(idx, txt) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action toolbar under stage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val blockCount = blocks?.size ?: 0
                val wordCount = blocks?.sumOf { it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() } } ?: 0
                NexusText(
                    text = "$blockCount blocks · ~$wordCount words",
                    style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
                    color = colors.textSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceVariant)
                            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                            .clickable { onCopyText() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(13.dp))
                            NexusText("Copy Text", style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isIncluded) colors.surfaceVariant else colors.primary)
                            .border(1.dp, if (isIncluded) colors.divider else colors.primary, RoundedCornerShape(8.dp))
                            .clickable { onToggleInclude() }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        NexusText(
                            text = if (isIncluded) "Skip Page" else "Include Page",
                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = if (isIncluded) colors.textPrimary else colors.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfToDocxModeTab(
    conversionMode: ConversionMode,
    onModeChange: (ConversionMode) -> Unit,
    detectHeadings: Boolean,
    onDetectHeadingsChange: (Boolean) -> Unit,
    mergeLines: Boolean,
    onMergeLinesChange: (Boolean) -> Unit,
    pageBreaks: Boolean,
    onPageBreaksChange: (Boolean) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NexusText(
            text = "Select Conversion Engine",
            style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
            color = colors.textSecondary
        )

        ConversionMode.entries.forEach { mode ->
            val isMode = conversionMode == mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isMode) colors.primary.copy(alpha = 0.08f) else colors.surface)
                    .border(1.5.dp, if (isMode) colors.primary else colors.divider, RoundedCornerShape(14.dp))
                    .clickable { onModeChange(mode) }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isMode) colors.primary else colors.surface)
                            .border(1.5.dp, if (isMode) colors.primary else colors.divider, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMode) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.onPrimary))
                        }
                    }
                    Column {
                        NexusText(mode.title, style = NexusTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
                        NexusText(mode.desc, style = NexusTheme.typography.caption.copy(fontSize = 12.sp), color = colors.textSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        NexusText(
            text = "Structure Detection",
            style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
            color = colors.textSecondary
        )

        CheckboxRow("Detect headings (H1 / H2) by font weight & size", detectHeadings, onDetectHeadingsChange)
        CheckboxRow("Merge wrapped lines into flowing paragraphs", mergeLines, onMergeLinesChange)
        CheckboxRow("Insert page break between PDF pages", pageBreaks, onPageBreaksChange)
    }
}

@Composable
private fun PdfToDocxStyleTab(
    wordFont: WordFontFamily,
    onFontChange: (WordFontFamily) -> Unit,
    fontSizePt: Int,
    onFontSizeChange: (Int) -> Unit,
    lineSpacing: WordLineSpacing,
    onLineSpacingChange: (WordLineSpacing) -> Unit,
    wordMargin: WordMargin,
    onMarginChange: (WordMargin) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Font Family
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusText("Default Word Font", style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WordFontFamily.entries.forEach { f ->
                    val isF = wordFont == f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isF) colors.primary else Color.Transparent)
                            .clickable { onFontChange(f) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = f.label,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isF) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isF) colors.onPrimary else colors.textPrimary
                        )
                    }
                }
            }
        }

        // Font Size Stepper
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NexusText("Body Font Size", style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
                NexusText("$fontSizePt pt", style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (fontSizePt > 9) colors.surface else colors.surface.copy(alpha = 0.4f))
                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                        .clickable(enabled = fontSizePt > 9) { onFontSizeChange((fontSizePt - 1).coerceAtLeast(9)) },
                    contentAlignment = Alignment.Center
                ) {
                    NexusText("−", style = NexusTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
                }

                NexusText(
                    text = "$fontSizePt pt",
                    style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (fontSizePt < 16) colors.surface else colors.surface.copy(alpha = 0.4f))
                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                        .clickable(enabled = fontSizePt < 16) { onFontSizeChange((fontSizePt + 1).coerceAtMost(16)) },
                    contentAlignment = Alignment.Center
                ) {
                    NexusText("+", style = NexusTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
                }
            }
        }

        // Line Spacing
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusText("Line Spacing", style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WordLineSpacing.entries.forEach { ls ->
                    val isLs = lineSpacing == ls
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLs) colors.primary else Color.Transparent)
                            .clickable { onLineSpacingChange(ls) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = ls.label,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isLs) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isLs) colors.onPrimary else colors.textPrimary
                        )
                    }
                }
            }
        }

        // Margins
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusText("Page Margins", style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WordMargin.entries.forEach { mg ->
                    val isMg = wordMargin == mg
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMg) colors.primary else Color.Transparent)
                            .clickable { onMarginChange(mg) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = mg.label,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isMg) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isMg) colors.onPrimary else colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfToDocxPagesTab(
    curPage: Int,
    totalPages: Int,
    includedPages: List<Int>,
    onIncludeAll: () -> Unit,
    onSkipFirst: () -> Unit,
    onOddOnly: () -> Unit,
    onEvenOnly: () -> Unit,
    onApplyRange: (String) -> Unit,
    rangeInput: String,
    rangeError: String?,
    onRangeInputChange: (String) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NexusText(
            text = "Select which pages to include in the Word export (${includedPages.size}/$totalPages selected)",
            style = NexusTheme.typography.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickFilterChip("All pages", Modifier.weight(1f), onClick = onIncludeAll)
            QuickFilterChip("Skip Page 1", Modifier.weight(1f), onClick = onSkipFirst)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickFilterChip("Odd only", Modifier.weight(1f), onClick = onOddOnly)
            QuickFilterChip("Even only", Modifier.weight(1f), onClick = onEvenOnly)
        }

        Spacer(modifier = Modifier.height(4.dp))

        NexusText(
            text = "Custom page range",
            style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.7f))
                    .border(
                        width = 1.dp,
                        color = if (rangeError != null) colors.error else colors.divider,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (rangeInput.isEmpty()) {
                    NexusText(
                        text = "e.g. 1-3, 5, 8-10",
                        style = NexusTheme.typography.body.copy(fontSize = 13.sp),
                        color = colors.textSecondary.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = rangeInput,
                    onValueChange = onRangeInputChange,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = NexusTheme.typography.body.fontFamily
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary)
                    .springBounceClick { onApplyRange(rangeInput) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "Apply",
                    style = NexusTheme.typography.buttonLabel.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = colors.onPrimary
                )
            }
        }

        NexusText(
            text = rangeError ?: "Tip: Enter comma-separated page ranges or single numbers.",
            style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
            color = if (rangeError != null) colors.error else colors.textSecondary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PdfToDocxThumbnailsStrip(
    info: PdfToDocxDocInfo,
    curPage: Int,
    includedPages: List<Int>,
    pageBitmaps: Map<Int, Bitmap>,
    pageBlocks: Map<Int, List<WordBlock>>,
    onSelectPage: (Int) -> Unit
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NexusText(
            text = "Pages · ${includedPages.size} of ${info.pageCount} included",
            style = NexusTheme.typography.title.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items((1..info.pageCount).toList()) { p ->
            val isCurrent = p == curPage
            val isInc = includedPages.contains(p)
            val thumb = pageBitmaps[p]
            val wordCount = pageBlocks[p]?.sumOf { it.text.split(Regex("\\s+")).count { w -> w.isNotBlank() } } ?: 0

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable { onSelectPage(p) }
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = if (isCurrent) colors.primary else if (isInc) colors.primary.copy(alpha = 0.4f) else colors.divider,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Page $p",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        NexusText("$p", style = NexusTheme.typography.caption.copy(fontSize = 12.sp), color = colors.textSecondary)
                    }

                    if (wordCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            NexusText("${wordCount}w", style = NexusTheme.typography.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }

                    if (!isInc) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            NexusText("SKIP", style = NexusTheme.typography.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.ExtraBold), color = Color.White)
                        }
                    }
                }

                NexusText(
                    text = "$p",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.5.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) colors.primary else colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun PdfToDocxDocSummaryCard(
    info: PdfToDocxDocInfo,
    includedCount: Int,
    onChangeFile: () -> Unit
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Outlined.Description, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    NexusText(info.name, style = NexusTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NexusText("${info.pageCount} pages ($includedCount included) · ${PdfToDocxEngine.formatBytes(info.sizeBytes)}", style = NexusTheme.typography.caption.copy(fontSize = 12.sp), color = colors.textSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                    .springBounceClick { onChangeFile() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                NexusText("Change file", style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun PdfToDocxExportCard(
    res: PdfToDocxExportResult,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
    onProcessAnother: () -> Unit
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(34.dp))
            }

            NexusText(
                text = if (res.isTxt) "Text Exported Successfully!" else "Word File Ready!",
                style = NexusTheme.typography.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceVariant.copy(alpha = 0.6f)).padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NexusText("File name", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp), color = colors.textSecondary)
                        NexusText(res.outputFileName.ifBlank { res.outputFile?.name ?: "document" }, style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NexusText("Pages converted", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp), color = colors.textSecondary)
                        NexusText("${res.pagesConvertedCount} pages", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NexusText("File size", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp), color = colors.textSecondary)
                        NexusText(if (res.fileSizeBytes > 0) PdfToDocxEngine.formatBytes(res.fileSizeBytes) else "Saved", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NexusText("Location", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp), color = colors.textSecondary)
                        NexusText("Saved to device", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.primary)
                    }
                }
            }

            // Open File Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary)
                    .springBounceClick { onOpenFile() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Outlined.FolderOpen, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                    NexusText("Open File", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold), color = colors.onPrimary)
                }
            }

            // Share File Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                    .springBounceClick { onShareFile() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    NexusText("Share File", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                }
            }

            // Process Another PDF
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Transparent)
                    .springBounceClick { onProcessAnother() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText("Process Another PDF", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = colors.primary)
            }
        }
    }
}

// ─── Helper Components ────────────────────────────────────────────────────────

@Composable
private fun EditableBlockRow(
    block: WordBlock,
    fontFamily: FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onTextChange: (String) -> Unit
) {
    val colors = NexusTheme.colors
    var isEditing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isEditing) colors.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent)
            .border(1.dp, if (isEditing) colors.primary else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    NexusText(
                        text = block.type.label,
                        style = NexusTheme.typography.caption.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                        color = colors.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            BasicTextField(
                value = block.text,
                onValueChange = onTextChange,
                textStyle = TextStyle(
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    fontWeight = if (block.type == BlockType.H1 || block.type == BlockType.H2) FontWeight.Bold else FontWeight.Normal,
                    color = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, if (checked) colors.primary else colors.divider, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NexusText(label, style = NexusTheme.typography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = colors.textPrimary)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.primary, checkmarkColor = colors.onPrimary)
        )
    }
}

@Composable
private fun QuickFilterChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = NexusTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
            .springBounceClick { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )
    }
}
