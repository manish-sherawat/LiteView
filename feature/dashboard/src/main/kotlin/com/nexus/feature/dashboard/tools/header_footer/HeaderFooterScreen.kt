@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.header_footer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
 * Header & Footer PDF Tool Screen implementing the precision HTML template specification.
 * Supports per-page custom 6-slot headers & footers, typography presets, dynamic tokens,
 * multi-page mirror/copy, and high-fidelity device-local PDF generation.
 */
@Composable
fun HeaderFooterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colors = NexusTheme.colors

    // ─── State ───────────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(HeaderFooterStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<HeaderFooterDocInfo?>(null) }
    var curPage by remember { mutableIntStateOf(1) } // 1-indexed

    // Map of page number to configuration
    val configs = remember { mutableStateMapOf<Int, PageHFConfig>() }
    var activeSlot by remember { mutableStateOf(HFSlot.FC) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Text, 1: Appearance, 2: Apply
    var activeRegion by remember { mutableStateOf("footer") } // "header" or "footer"

    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Range copy input
    var rangeInput by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }

    // Exporting
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportResult by remember { mutableStateOf<HeaderFooterExportResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    // Dialogs
    var showChangeFileDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

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

    // Helper: current page's config
    val curConfig = configs[curPage] ?: PageHFConfig()

    fun updateCurConfig(transform: (PageHFConfig) -> PageHFConfig) {
        val updated = transform(configs[curPage] ?: PageHFConfig())
        if (updated.isActive) {
            configs[curPage] = updated
        } else {
            configs.remove(curPage)
        }
    }

    // Load page thumbnail
    fun loadPageBitmap(doc: HeaderFooterDocInfo, pageNum: Int) {
        if (!pageBitmaps.containsKey(pageNum)) {
            isLoadingPreview = true
            coroutineScope.launch {
                try {
                    val bmp = HeaderFooterEngine.renderThumbnail(context, doc.uri, pageNum - 1, 600)
                    pageBitmaps[pageNum] = bmp
                } catch (_: Exception) {}
                finally {
                    isLoadingPreview = false
                }
            }
        }
    }

    // Copy to pages helper
    fun copyTo(pages: List<Int>, mirrorEven: Boolean = false) {
        val src = configs[curPage] ?: return
        val total = docInfo?.pageCount ?: 1
        var count = 0
        pages.filter { it in 1..total && it != curPage }.forEach { p ->
            val copy = if (mirrorEven && p % 2 == 0) {
                src.copy(
                    hl = src.hr,
                    hr = src.hl,
                    fl = src.fr,
                    fr = src.fl
                )
            } else {
                src.copy()
            }
            configs[p] = copy
            count++
        }
        showToast(if (count > 0) "Copied header & footer to $count page${if (count == 1) "" else "s"}." else "No other pages to copy to.")
    }

    // PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            statusMessage = "Opening PDF…"
            isStatusError = false
            coroutineScope.launch {
                try {
                    val info = HeaderFooterEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    curPage = 1
                    configs.clear()
                    pageBitmaps.clear()
                    activeSlot = HFSlot.FC
                    activeRegion = "footer"
                    selectedTab = 0
                    rangeInput = ""
                    rangeError = null
                    statusMessage = null
                    currentStep = HeaderFooterStep.CUSTOMIZE

                    // Preload first page bitmap
                    loadPageBitmap(info, 1)

                    // Preload neighboring thumbnails
                    for (p in 2..minOf(info.pageCount, 6)) {
                        loadPageBitmap(info, p)
                    }
                } catch (e: Exception) {
                    statusMessage = "Couldn’t open that PDF. It may be password-protected or damaged."
                    isStatusError = true
                }
            }
        }
    }

    // Launcher for saving the header & footer PDF safely via SAF
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        val doc = docInfo
        if (destUri != null && doc != null) {
            isExporting = true
            exportProgress = 0f
            statusMessage = null
            isStatusError = false

            coroutineScope.launch {
                try {
                    val result = HeaderFooterEngine.applyHeaderFooter(
                        context = context,
                        docInfo = doc,
                        configs = configs.toMap(),
                        destUri = destUri,
                        outputFileName = "${doc.baseName}-header-footer.pdf",
                        onProgress = { p -> exportProgress = p }
                    )
                    exportResult = result
                    currentStep = HeaderFooterStep.EXPORT
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showToast("Done! Headers & footers applied to ${result.pagesUpdatedCount} pages.")
                } catch (e: Exception) {
                    statusMessage = "Something went wrong while exporting: ${e.message}"
                    isStatusError = true
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // Export handler
    fun startExport() {
        val doc = docInfo ?: return
        val activeEntries = configs.filter { it.value.isActive }
        if (activeEntries.isEmpty() || isExporting) return
        savePdfLauncher.launch("${doc.baseName}-header-footer.pdf")
    }

    val isScrolled by remember { derivedStateOf { scrollState.value > 10 } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Procedural dot-grid background accent
        HeaderFooterDotCanvas()

        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky Top Bar
            HeaderFooterTopBar(
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
                    text = "Add headers & footers, your way.",
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
                    text = "Place page numbers, dates, titles, or custom text at the top and bottom of any page. Give every page its own style, or copy one setup across your PDF. Everything runs on your device.",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Steps Indicator
                HeaderFooterStepsRow(currentStep = currentStep)

                Spacer(modifier = Modifier.height(24.dp))

                // ─── STEP 1: UPLOAD DROPZONE ──────────────────────────────────
                if (currentStep == HeaderFooterStep.UPLOAD) {
                    HeaderFooterUploadStep(
                        onSelectPdf = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        statusMessage = statusMessage,
                        isStatusError = isStatusError
                    )
                }

                // ─── STEP 2: CUSTOMIZE WORKSPACE ──────────────────────────────
                if (currentStep == HeaderFooterStep.CUSTOMIZE && docInfo != null) {
                    val info = docInfo!!

                    // 1. Quick Presets Strip
                    HeaderFooterQuickPresets(
                        curPage = curPage,
                        onApplyPageXOfY = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            updateCurConfig { it.copy(fc = "Page {page} of {total}") }
                            activeSlot = HFSlot.FC
                            activeRegion = "footer"
                            showToast("Preset applied to Page $curPage.")
                        },
                        onApplyCenteredNum = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            updateCurConfig { it.copy(fc = "— {page} —") }
                            activeSlot = HFSlot.FC
                            activeRegion = "footer"
                            showToast("Preset applied to Page $curPage.")
                        },
                        onApplyDocTitleNum = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            updateCurConfig {
                                it.copy(
                                    hl = "{file}",
                                    hr = "{date}",
                                    fc = "Page {page} of {total}",
                                    ruleH = true,
                                    ruleF = true
                                )
                            }
                            activeSlot = HFSlot.HL
                            activeRegion = "header"
                            showToast("Preset applied to Page $curPage.")
                        },
                        onApplyConfidential = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            updateCurConfig {
                                it.copy(
                                    hc = "CONFIDENTIAL",
                                    fl = "{file}",
                                    fr = "Page {page} of {total}",
                                    style = HFFontStyle.BOLD,
                                    colorHex = "#404040",
                                    ruleH = true
                                )
                            }
                            activeSlot = HFSlot.HC
                            activeRegion = "header"
                            showToast("Preset applied to Page $curPage.")
                        },
                        onClearPage = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            configs.remove(curPage)
                            showToast("Cleared Page $curPage.")
                        }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2. Interactive Preview Stage Card
                    HeaderFooterPreviewStage(
                        info = info,
                        curPage = curPage,
                        curConfig = curConfig,
                        curBmp = pageBitmaps[curPage],
                        isLoadingPreview = isLoadingPreview,
                        activeSlot = activeSlot,
                        onSlotClick = { slot, reg ->
                            activeSlot = slot
                            activeRegion = reg
                            selectedTab = 0
                        },
                        onPrevPage = {
                            if (curPage > 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                curPage--
                                loadPageBitmap(info, curPage)
                            }
                        },
                        onNextPage = {
                            if (curPage < info.pageCount) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                curPage++
                                loadPageBitmap(info, curPage)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. 3-Tab Segmented Control Deck
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
                                listOf("1. Text", "2. Appearance", "3. Apply").forEachIndexed { idx, label ->
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
                                                fontSize = 12.5.sp,
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
                                    0 -> HeaderFooterTextTab(
                                        curPage = curPage,
                                        activeSlot = activeSlot,
                                        activeRegion = activeRegion,
                                        curConfig = curConfig,
                                        onRegionChange = { reg, slot ->
                                            activeRegion = reg
                                            activeSlot = slot
                                        },
                                        onSlotChange = { slot -> activeSlot = slot },
                                        onUpdateSlotText = { slot, text ->
                                            updateCurConfig { cfg -> cfg.copyWithSlot(slot, text) }
                                        },
                                        onInsertTag = { tag ->
                                            insertTagIntoSlot(activeSlot, tag, curConfig, ::updateCurConfig)
                                        }
                                    )
                                    1 -> HeaderFooterAppearanceTab(
                                        curConfig = curConfig,
                                        onUpdate = ::updateCurConfig
                                    )
                                    2 -> HeaderFooterApplyTab(
                                        curPage = curPage,
                                        totalPages = info.pageCount,
                                        onCopyAll = { copyTo((1..info.pageCount).toList()) },
                                        onCopySkipFirst = { copyTo((2..info.pageCount).toList()) },
                                        onCopyOdd = { copyTo((1..info.pageCount).filter { it % 2 == 1 }) },
                                        onCopyEven = { copyTo((1..info.pageCount).filter { it % 2 == 0 }) },
                                        onMirrorEven = { copyTo((1..info.pageCount).toList(), mirrorEven = true) },
                                        onApplyRange = { rng ->
                                            val parsed = HeaderFooterEngine.parseRanges(rng, info.pageCount)
                                            parsed.fold(
                                                onSuccess = { list ->
                                                    copyTo(list)
                                                    rangeInput = ""
                                                    rangeError = null
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

                    // 4. Thumbnails Strip
                    val editedCount = configs.filter { it.value.isActive }.size
                    HeaderFooterThumbnailsStrip(
                        info = info,
                        curPage = curPage,
                        editedCount = editedCount,
                        configs = configs,
                        pageBitmaps = pageBitmaps,
                        onSelectPage = { p ->
                            curPage = p
                            loadPageBitmap(info, p)
                        },
                        onResetAll = { showResetAllDialog = true }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5. Selected File Summary Card
                    HeaderFooterDocSummaryCard(
                        info = info,
                        onChangeFile = {
                            if (editedCount > 0) showChangeFileDialog = true else {
                                currentStep = HeaderFooterStep.UPLOAD
                                docInfo = null
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 6. Primary Export CTA Button
                    val canExport = editedCount > 0 && !isExporting
                    val exportLabel = when {
                        isExporting -> "Applying headers & footers…"
                        editedCount == 0 -> "Add header or footer to continue"
                        else -> "Apply to $editedCount page${if (editedCount == 1) "" else "s"} and download"
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
                                    startExport()
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
                                text = exportLabel,
                                style = NexusTheme.typography.buttonLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                color = if (canExport) colors.onPrimary else colors.textSecondary.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    NexusText(
                        text = "Tip: Use “Cover background strip” if your PDF already has old page numbers you want to cover up cleanly.",
                        style = NexusTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                if (currentStep == HeaderFooterStep.EXPORT && exportResult != null) {
                    HeaderFooterExportCard(
                        res = exportResult!!,
                        onOpen = {
                            try {
                                val uri = exportResult!!.outputUri ?: (if (exportResult!!.outputFile != null) FileProvider.getUriForFile(context, "${context.packageName}.provider", exportResult!!.outputFile!!) else null)
                                if (uri != null) {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(viewIntent, "Open PDF"))
                                } else {
                                    showToast("File not found.")
                                }
                            } catch (_: Exception) {
                                showToast("Unable to open PDF viewer.")
                            }
                        },
                        onShare = {
                            try {
                                val uri = exportResult!!.outputUri ?: (if (exportResult!!.outputFile != null) FileProvider.getUriForFile(context, "${context.packageName}.provider", exportResult!!.outputFile!!) else null)
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                } else {
                                    showToast("File not found.")
                                }
                            } catch (_: Exception) {
                                showToast("Unable to share file.")
                            }
                        },
                        onProcessAnother = {
                            currentStep = HeaderFooterStep.UPLOAD
                            docInfo = null
                            configs.clear()
                            pageBitmaps.clear()
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
                title = { NexusText("Change file?", style = NexusTheme.typography.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary) },
                text = { NexusText("Going back will discard your current headers and footers.", style = NexusTheme.typography.body.copy(fontSize = 14.sp), color = colors.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        showChangeFileDialog = false
                        currentStep = HeaderFooterStep.UPLOAD
                        docInfo = null
                        configs.clear()
                        pageBitmaps.clear()
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

        // ─── Confirm Dialog: Reset All Configurations ─────────────────────────
        if (showResetAllDialog) {
            AlertDialog(
                onDismissRequest = { showResetAllDialog = false },
                title = { NexusText("Remove all headers & footers?", style = NexusTheme.typography.title.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary) },
                text = { NexusText("Clear your customizations from every page in this PDF?", style = NexusTheme.typography.body.copy(fontSize = 14.sp), color = colors.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetAllDialog = false
                        configs.clear()
                        showToast("All headers & footers removed.")
                    }) {
                        NexusText("Reset all", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetAllDialog = false }) {
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
private fun HeaderFooterDotCanvas() {
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
private fun HeaderFooterTopBar(
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
                    painter = painterResource(id = R.drawable.header_footer),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            NexusText(
                text = "Header & Footer",
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
private fun HeaderFooterStepsRow(currentStep: HeaderFooterStep) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HeaderFooterStep.entries.forEachIndexed { idx, step ->
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

            if (idx < HeaderFooterStep.entries.size - 1) {
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
private fun HeaderFooterUploadStep(
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
                modifier = Modifier.size(width = 132.dp, height = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 76.dp, height = 104.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.surface)
                        .border(1.5.dp, colors.divider, RoundedCornerShape(9.dp))
                        .padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                        Box(modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.divider))
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 4.dp)
                        .width(118.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.primary, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText("HEADER  ·  DATE", style = NexusTheme.typography.caption.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold), color = colors.primary)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-4).dp)
                        .width(118.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.primary, RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText("PAGE 1 OF 12", style = NexusTheme.typography.caption.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold), color = colors.primary)
                }
            }

            NexusText(
                text = "Tap to choose a PDF file",
                style = NexusTheme.typography.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            NexusText(
                text = "Works with reports, eBooks, invoices, contracts, and slides. Safe, offline, zero uploads.",
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
private fun HeaderFooterQuickPresets(
    curPage: Int,
    onApplyPageXOfY: () -> Unit,
    onApplyCenteredNum: () -> Unit,
    onApplyDocTitleNum: () -> Unit,
    onApplyConfidential: () -> Unit,
    onClearPage: () -> Unit
) {
    val colors = NexusTheme.colors
    NexusText(
        text = "Quick presets",
        style = NexusTheme.typography.title.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        color = colors.textPrimary
    )

    Spacer(modifier = Modifier.height(6.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item { PresetChip(label = "Page X of Y", onClick = onApplyPageXOfY) }
        item { PresetChip(label = "Centered #", onClick = onApplyCenteredNum) }
        item { PresetChip(label = "Doc Title + #", onClick = onApplyDocTitleNum) }
        item { PresetChip(label = "Confidential", onClick = onApplyConfidential) }
        item { PresetChip(label = "Clear page", isDestructive = true, onClick = onClearPage) }
    }
}

@Composable
private fun HeaderFooterPreviewStage(
    info: HeaderFooterDocInfo,
    curPage: Int,
    curConfig: PageHFConfig,
    curBmp: Bitmap?,
    isLoadingPreview: Boolean,
    activeSlot: HFSlot,
    onSlotClick: (HFSlot, String) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit
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

                NexusText(
                    text = "Page $curPage of ${info.pageCount}",
                    style = NexusTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, colors.divider, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (curBmp != null) {
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

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            val hActive = curConfig.hasHeaderText || curConfig.ruleH
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (curConfig.mask && hActive) Color.White.copy(alpha = 0.94f) else Color.Transparent)
                                    .border(
                                        width = if (curConfig.ruleH) 1.dp else 0.dp,
                                        color = if (curConfig.ruleH) Color(0x44000000) else Color.Transparent
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    InteractiveSlotCell(
                                        slot = HFSlot.HL,
                                        rawText = curConfig.hl,
                                        align = TextAlign.Start,
                                        pageNum = curPage,
                                        total = info.pageCount,
                                        baseName = info.baseName,
                                        config = curConfig,
                                        isSelected = activeSlot == HFSlot.HL,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSlotClick(HFSlot.HL, "header") }
                                    )
                                    InteractiveSlotCell(
                                        slot = HFSlot.HC,
                                        rawText = curConfig.hc,
                                        align = TextAlign.Center,
                                        pageNum = curPage,
                                        total = info.pageCount,
                                        baseName = info.baseName,
                                        config = curConfig,
                                        isSelected = activeSlot == HFSlot.HC,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSlotClick(HFSlot.HC, "header") }
                                    )
                                    InteractiveSlotCell(
                                        slot = HFSlot.HR,
                                        rawText = curConfig.hr,
                                        align = TextAlign.End,
                                        pageNum = curPage,
                                        total = info.pageCount,
                                        baseName = info.baseName,
                                        config = curConfig,
                                        isSelected = activeSlot == HFSlot.HR,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSlotClick(HFSlot.HR, "header") }
                                    )
                                }
                            }

                            val fActive = curConfig.hasFooterText || curConfig.ruleF
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (curConfig.mask && fActive) Color.White.copy(alpha = 0.94f) else Color.Transparent)
                                    .border(
                                        width = if (curConfig.ruleF) 1.dp else 0.dp,
                                        color = if (curConfig.ruleF) Color(0x44000000) else Color.Transparent
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    InteractiveSlotCell(
                                        slot = HFSlot.FL,
                                        rawText = curConfig.fl,
                                        align = TextAlign.Start,
                                        pageNum = curPage,
                                        total = info.pageCount,
                                        baseName = info.baseName,
                                        config = curConfig,
                                        isSelected = activeSlot == HFSlot.FL,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSlotClick(HFSlot.FL, "footer") }
                                    )
                                    InteractiveSlotCell(
                                        slot = HFSlot.FC,
                                        rawText = curConfig.fc,
                                        align = TextAlign.Center,
                                        pageNum = curPage,
                                        total = info.pageCount,
                                        baseName = info.baseName,
                                        config = curConfig,
                                        isSelected = activeSlot == HFSlot.FC,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSlotClick(HFSlot.FC, "footer") }
                                    )
                                    InteractiveSlotCell(
                                        slot = HFSlot.FR,
                                        rawText = curConfig.fr,
                                        align = TextAlign.End,
                                        pageNum = curPage,
                                        total = info.pageCount,
                                        baseName = info.baseName,
                                        config = curConfig,
                                        isSelected = activeSlot == HFSlot.FR,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onSlotClick(HFSlot.FR, "footer") }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = colors.primary,
                            strokeWidth = 2.5.dp
                        )
                        NexusText(
                            text = "Rendering page $curPage…",
                            style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val activeSlotsNames = HFSlot.entries.filter { curConfig[it].isNotBlank() }.map { it.label }
            val readoutText = if (!curConfig.isActive) {
                "No header or footer on this page yet · Tap a zone or pick a preset"
            } else {
                val parts = if (activeSlotsNames.isNotEmpty()) activeSlotsNames.joinToString(", ") else "Divider lines only"
                "Page $curPage: $parts (${curConfig.size} pt ${curConfig.font.label})"
            }

            NexusText(
                text = readoutText,
                style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeaderFooterTextTab(
    curPage: Int,
    activeSlot: HFSlot,
    activeRegion: String,
    curConfig: PageHFConfig,
    onRegionChange: (String, HFSlot) -> Unit,
    onSlotChange: (HFSlot) -> Unit,
    onUpdateSlotText: (HFSlot, String) -> Unit,
    onInsertTag: (String) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusText(
                text = "Text for Page $curPage",
                style = NexusTheme.typography.title.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.divider, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
                    NexusText(
                        text = "Editing: ${activeSlot.label}",
                        style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                }
            }
        }

        // Region Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val hasHeader = curConfig.hasHeaderText || curConfig.ruleH
            val hasFooter = curConfig.hasFooterText || curConfig.ruleF

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (activeRegion == "header") colors.primary else Color.Transparent)
                    .clickable {
                        val nextSlot = if (!activeSlot.isHeader) HFSlot.HC else activeSlot
                        onRegionChange("header", nextSlot)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NexusText(
                        text = "Header (Top)",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = if (activeRegion == "header") FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (activeRegion == "header") colors.onPrimary else colors.textPrimary
                    )
                    if (hasHeader) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (activeRegion == "header") colors.onPrimary else colors.primary))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (activeRegion == "footer") colors.primary else Color.Transparent)
                    .clickable {
                        val nextSlot = if (activeSlot.isHeader) HFSlot.FC else activeSlot
                        onRegionChange("footer", nextSlot)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NexusText(
                        text = "Footer (Bottom)",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = if (activeRegion == "footer") FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (activeRegion == "footer") colors.onPrimary else colors.textPrimary
                    )
                    if (hasFooter) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (activeRegion == "footer") colors.onPrimary else colors.primary))
                    }
                }
            }
        }

        if (activeRegion == "header") {
            SlotInputField("Left Header", curConfig.hl, activeSlot == HFSlot.HL) {
                onSlotChange(HFSlot.HL)
                onUpdateSlotText(HFSlot.HL, it)
            }
            SlotInputField("Center Header", curConfig.hc, activeSlot == HFSlot.HC) {
                onSlotChange(HFSlot.HC)
                onUpdateSlotText(HFSlot.HC, it)
            }
            SlotInputField("Right Header", curConfig.hr, activeSlot == HFSlot.HR) {
                onSlotChange(HFSlot.HR)
                onUpdateSlotText(HFSlot.HR, it)
            }
        } else {
            SlotInputField("Left Footer", curConfig.fl, activeSlot == HFSlot.FL) {
                onSlotChange(HFSlot.FL)
                onUpdateSlotText(HFSlot.FL, it)
            }
            SlotInputField("Center Footer", curConfig.fc, activeSlot == HFSlot.FC) {
                onSlotChange(HFSlot.FC)
                onUpdateSlotText(HFSlot.FC, it)
            }
            SlotInputField("Right Footer", curConfig.fr, activeSlot == HFSlot.FR) {
                onSlotChange(HFSlot.FR)
                onUpdateSlotText(HFSlot.FR, it)
            }
        }

        NexusText(
            text = "Insert tag into ${activeSlot.label}:",
            style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TokenPill("+ Page #", "{page}", onInsertTag)
            TokenPill("+ Total", "{total}", onInsertTag)
            TokenPill("+ Page of Total", "Page {page} of {total}", onInsertTag)
            TokenPill("+ Date", "{date}", onInsertTag)
            TokenPill("+ File name", "{file}", onInsertTag)
        }
    }
}

@Composable
private fun HeaderFooterAppearanceTab(
    curConfig: PageHFConfig,
    onUpdate: ((PageHFConfig) -> PageHFConfig) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Font Family
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusText(
                text = "Font family",
                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HFFontFamily.entries.forEach { f ->
                    val isF = curConfig.font == f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isF) colors.primary else Color.Transparent)
                            .clickable { onUpdate { it.copy(font = f) } }
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

        // Font Style
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NexusText(
                text = "Font style",
                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                color = colors.textSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HFFontStyle.entries.forEach { s ->
                    val isS = curConfig.style == s
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isS) colors.primary else Color.Transparent)
                            .clickable { onUpdate { it.copy(style = s) } }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = s.label,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isS) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isS) colors.onPrimary else colors.textPrimary
                        )
                    }
                }
            }
        }

        // Font Size Liquid Slider
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NexusText("Font size", style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
                NexusText("${curConfig.size} pt", style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
            }
            NexusLiquidSlider(
                value = curConfig.size.toFloat(),
                onValueChange = { v -> onUpdate { it.copy(size = v.toInt()) } },
                valueRange = 7f..24f
            )
        }

        // Margin Liquid Slider
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NexusText("Top / bottom margin", style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
                NexusText("${curConfig.vMargin} pt", style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
            }
            NexusLiquidSlider(
                value = curConfig.vMargin.toFloat(),
                onValueChange = { v -> onUpdate { it.copy(vMargin = v.toInt()) } },
                valueRange = 10f..60f
            )
        }

        // Start Page #
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusText("Start page #", style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (curConfig.startNum > 1) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.4f))
                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                        .clickable(enabled = curConfig.startNum > 1) { onUpdate { it.copy(startNum = (it.startNum - 1).coerceAtLeast(1)) } },
                    contentAlignment = Alignment.Center
                ) {
                    NexusText("−", style = NexusTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = if (curConfig.startNum > 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f))
                }
                NexusText("${curConfig.startNum}", style = NexusTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                        .clickable { onUpdate { it.copy(startNum = it.startNum + 1) } },
                    contentAlignment = Alignment.Center
                ) {
                    NexusText("+", style = NexusTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
                }
            }
        }

        // Ink Tone Swatches
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                NexusText("Ink tone", style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = colors.textSecondary)
                NexusText(curConfig.colorHex.uppercase(), style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "#0A0A0A" to "Ink Black",
                    "#404040" to "Charcoal",
                    "#707070" to "Muted",
                    "#A3A3A3" to "Light"
                ).forEach { (hex, name) ->
                    val isColor = curConfig.colorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isColor) colors.primary else colors.surface)
                            .border(1.dp, if (isColor) colors.primary else colors.divider, RoundedCornerShape(10.dp))
                            .clickable { onUpdate { it.copy(colorHex = hex) } }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            )
                            NexusText(
                                text = name,
                                style = NexusTheme.typography.caption.copy(fontSize = 10.5.sp, fontWeight = if (isColor) FontWeight.Bold else FontWeight.Medium),
                                color = if (isColor) colors.onPrimary else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // Divider Rules & Mask Checkboxes
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckboxPill("Header divider line", curConfig.ruleH) {
                onUpdate { cfg -> cfg.copy(ruleH = it) }
            }
            CheckboxPill("Footer divider line", curConfig.ruleF) {
                onUpdate { cfg -> cfg.copy(ruleF = it) }
            }
            CheckboxPill("Cover background strip", curConfig.mask) {
                onUpdate { cfg -> cfg.copy(mask = it) }
            }
        }
    }
}

@Composable
private fun HeaderFooterApplyTab(
    curPage: Int,
    totalPages: Int,
    onCopyAll: () -> Unit,
    onCopySkipFirst: () -> Unit,
    onCopyOdd: () -> Unit,
    onCopyEven: () -> Unit,
    onMirrorEven: () -> Unit,
    onApplyRange: (String) -> Unit,
    rangeInput: String,
    rangeError: String?,
    onRangeInputChange: (String) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NexusText(
            text = "Copy Page $curPage’s setup to other pages",
            style = NexusTheme.typography.title.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickCopyButton("All pages", Modifier.weight(1f), onClick = onCopyAll)
            QuickCopyButton("Skip Page 1", Modifier.weight(1f), onClick = onCopySkipFirst)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickCopyButton("Odd pages", Modifier.weight(1f), onClick = onCopyOdd)
            QuickCopyButton("Even pages", Modifier.weight(1f), onClick = onCopyEven)
        }
        QuickCopyButton("Mirror Left/Right on Even", Modifier.fillMaxWidth(), onClick = onMirrorEven)

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
                        text = "e.g. 2-5, 8",
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
            text = rangeError ?: "Dynamic tags like {page} and {total} update automatically for each page.",
            style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
            color = if (rangeError != null) colors.error else colors.textSecondary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun HeaderFooterThumbnailsStrip(
    info: HeaderFooterDocInfo,
    curPage: Int,
    editedCount: Int,
    configs: Map<Int, PageHFConfig>,
    pageBitmaps: Map<Int, Bitmap>,
    onSelectPage: (Int) -> Unit,
    onResetAll: () -> Unit
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NexusText(
            text = if (editedCount > 0) "Pages · $editedCount customized" else "Pages",
            style = NexusTheme.typography.title.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )

        TextButton(
            onClick = onResetAll,
            enabled = editedCount > 0
        ) {
            NexusText(
                text = "Reset all",
                style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = if (editedCount > 0) colors.error else colors.textSecondary.copy(alpha = 0.4f)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items((1..info.pageCount).toList()) { p ->
            val isCurrent = p == curPage
            val pCfg = configs[p]
            val isPageEdited = pCfg != null && pCfg.isActive
            val thumb = pageBitmaps[p]

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
                            color = if (isCurrent) colors.primary else if (isPageEdited) colors.primary.copy(alpha = 0.5f) else colors.divider,
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

                    if (isPageEdited) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            NexusText("EDITED", style = NexusTheme.typography.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold), color = colors.onPrimary)
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
private fun HeaderFooterDocSummaryCard(
    info: HeaderFooterDocInfo,
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
                    NexusText("${info.pageCount} pages · ${HeaderFooterEngine.formatBytes(info.sizeBytes)}", style = NexusTheme.typography.caption.copy(fontSize = 12.sp), color = colors.textSecondary)
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
private fun HeaderFooterExportCard(
    res: HeaderFooterExportResult,
    onOpen: () -> Unit,
    onShare: () -> Unit,
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
                text = "PDF Exported Successfully!",
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
                        NexusText(res.outputFileName.ifBlank { res.outputFile?.name ?: "Document.pdf" }, style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NexusText("Pages updated", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp), color = colors.textSecondary)
                        NexusText("${res.pagesUpdatedCount} pages", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NexusText("File size", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp), color = colors.textSecondary)
                        NexusText(if (res.fileSizeBytes > 0) HeaderFooterEngine.formatBytes(res.fileSizeBytes) else "Saved", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NexusText("Location", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp), color = colors.textSecondary)
                        NexusText("Saved to device", style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = colors.primary)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary)
                    .springBounceClick { onOpen() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Outlined.FolderOpen, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                    NexusText("Open File", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold), color = colors.onPrimary)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                    .springBounceClick { onShare() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    NexusText("Share File", style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                }
            }

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

// ─── Slot & Control Atoms ─────────────────────────────────────────────────────

@Composable
private fun InteractiveSlotCell(
    slot: HFSlot,
    rawText: String,
    align: TextAlign,
    pageNum: Int,
    total: Int,
    baseName: String,
    config: PageHFConfig,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val display = if (rawText.isBlank()) {
        "·"
    } else {
        HeaderFooterEngine.resolveTokens(rawText, pageNum, total, baseName, config.startNum)
    }

    val parsedColor = try {
        Color(android.graphics.Color.parseColor(config.colorHex))
    } catch (_: Exception) {
        Color.Black
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (isSelected) Color(0x33000000) else Color.Transparent)
            .border(
                width = if (isSelected) 1.dp else 0.5.dp,
                color = if (isSelected) Color.Black else Color(0x22000000),
                shape = RoundedCornerShape(3.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        NexusText(
            text = display,
            style = TextStyle(
                fontSize = 8.5.sp,
                fontWeight = if (config.style == HFFontStyle.BOLD) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (config.style == HFFontStyle.ITALIC) FontStyle.Italic else FontStyle.Normal,
                fontFamily = when (config.font) {
                    HFFontFamily.SANS -> FontFamily.SansSerif
                    HFFontFamily.SERIF -> FontFamily.Serif
                    HFFontFamily.MONO -> FontFamily.Monospace
                },
                textAlign = align
            ),
            color = if (rawText.isBlank()) Color.Gray.copy(alpha = 0.4f) else parsedColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SlotInputField(
    label: String,
    text: String,
    isFocused: Boolean,
    onValueChange: (String) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = if (isFocused) colors.primary else colors.textSecondary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.6f))
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) colors.primary else colors.divider,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (text.isEmpty()) {
                NexusText(
                    text = "Empty",
                    style = NexusTheme.typography.body.copy(fontSize = 13.sp),
                    color = colors.textSecondary.copy(alpha = 0.4f)
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontFamily = NexusTheme.typography.body.fontFamily
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun TokenPill(
    label: String,
    tag: String,
    onInsert: (String) -> Unit
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .springBounceClick { onInsert(tag) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary
        )
    }
}

@Composable
private fun StepperControl(
    value: String,
    canDec: Boolean,
    canInc: Boolean,
    onDec: () -> Unit,
    onInc: () -> Unit
) {
    val colors = NexusTheme.colors
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
                .background(if (canDec) colors.surface else colors.surface.copy(alpha = 0.4f))
                .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                .clickable(enabled = canDec) { onDec() },
            contentAlignment = Alignment.Center
        ) {
            NexusText("−", style = NexusTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = if (canDec) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f))
        }

        NexusText(
            text = value,
            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
            color = colors.textPrimary
        )

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (canInc) colors.surface else colors.surface.copy(alpha = 0.4f))
                .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                .clickable(enabled = canInc) { onInc() },
            contentAlignment = Alignment.Center
        ) {
            NexusText("+", style = NexusTheme.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = if (canInc) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun CheckboxPill(
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
        NexusText(
            text = label,
            style = NexusTheme.typography.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = colors.textPrimary
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.primary, checkmarkColor = colors.onPrimary)
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDestructive) colors.error.copy(alpha = 0.1f) else colors.surface)
            .border(1.dp, if (isDestructive) colors.error.copy(alpha = 0.3f) else colors.divider, RoundedCornerShape(10.dp))
            .springBounceClick { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = if (isDestructive) colors.error else colors.textPrimary
        )
    }
}

@Composable
private fun QuickCopyButton(
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

private fun insertTagIntoSlot(
    slot: HFSlot,
    tag: String,
    config: PageHFConfig,
    update: ((PageHFConfig) -> PageHFConfig) -> Unit
) {
    val current = config[slot]
    val needSpace = current.isNotBlank() && !current.endsWith(" ")
    val next = current + (if (needSpace) " " else "") + tag
    update { it.copyWithSlot(slot, next) }
}
