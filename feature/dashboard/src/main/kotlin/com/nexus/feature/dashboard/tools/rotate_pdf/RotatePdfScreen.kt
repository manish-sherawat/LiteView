@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.rotate_pdf

import android.graphics.Bitmap
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
import com.nexus.feature.dashboard.tools.rotate_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen for rotating PDF pages (single, bulk, odd, even, or range).
 * 100% offline, lossless vector orientation modification using PDFBox.
 */
@Composable
fun RotatePdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // ── Main State ──────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(RotateStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<RotatePdfDocInfo?>(null) }
    var pages by remember { mutableStateOf<List<RotatePageItem>>(emptyList()) }
    val selectedPageNumbers = remember { mutableStateListOf<Int>() }

    val thumbnails = remember { mutableStateMapOf<Int, Bitmap>() }

    var rangeInputText by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }

    var isLoadingPdf by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val totalRotatedCount by remember {
        derivedStateOf { pages.count { it.isTurned } }
    }

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
        if (isExporting || isLoadingPdf) return
        currentStep = RotateStep.UPLOAD
        docInfo = null
        pages = emptyList()
        selectedPageNumbers.clear()
        thumbnails.clear()
        rangeInputText = ""
        rangeError = null
        statusMessage = null
    }

    // Intercept back button when in rotate workspace
    BackHandler(enabled = currentStep != RotateStep.UPLOAD) {
        if (totalRotatedCount > 0) {
            showDiscardDialog = true
        } else {
            resetState()
        }
    }

    // ── Save Rotated PDF Launcher ───────────────────────────────────────────
    val saveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && docInfo != null && totalRotatedCount > 0) {
            isExporting = true
            statusMessage = null

            coroutineScope.launch {
                try {
                    val rotationsMap = pages
                        .filter { it.isTurned }
                        .associate { it.pageNumber to it.rotationDelta }

                    context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        RotatePdfEngine.applyRotationsAndSave(
                            context = context,
                            inputUri = docInfo!!.uri,
                            rotations = rotationsMap,
                            outputStream = outStream,
                            onProgress = { _, _ -> }
                        )
                    }

                    currentStep = RotateStep.EXPORT
                    showToast("Done! Saved your PDF.")
                    delay(500)
                } catch (e: Exception) {
                    statusMessage = "Something went wrong while saving: ${e.message}"
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // ── PDF File Picker ─────────────────────────────────────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingPdf = true
            statusMessage = null

            coroutineScope.launch {
                try {
                    val (info, initialRotations) = RotatePdfEngine.readPdfDocInfo(context, uri)
                    docInfo = info

                    val newPages = (1..info.pageCount).map { p ->
                        RotatePageItem(
                            pageNumber = p,
                            initialRotation = initialRotations.getOrElse(p - 1) { 0 },
                            rotationDelta = 0
                        )
                    }
                    pages = newPages
                    selectedPageNumbers.clear()
                    thumbnails.clear()
                    rangeInputText = ""
                    rangeError = null

                    currentStep = RotateStep.ROTATE
                    statusMessage = null

                    // Asynchronously load page thumbnails in background
                    coroutineScope.launch {
                        for (p in 1..info.pageCount) {
                            try {
                                val bmp = RotatePdfEngine.renderPageThumbnail(context, uri, p - 1)
                                thumbnails[p] = bmp
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    statusMessage = if (e.message?.contains("password", ignoreCase = true) == true) {
                        "This PDF is password-protected."
                    } else {
                        "Couldn't open that PDF. It may be damaged."
                    }
                } finally {
                    isLoadingPdf = false
                }
            }
        }
    }

    fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("application/pdf", "*/*"))
    }

    // ── Rotation Helpers ────────────────────────────────────────────────────
    fun applyRotation(targets: List<Int>, delta: Int) {
        val targetSet = targets.toSet()
        pages = pages.map { item ->
            if (targetSet.contains(item.pageNumber)) {
                val nextDelta = if (delta == 0) {
                    0 // Reset
                } else {
                    ((item.rotationDelta + delta) % 360 + 360) % 360
                }
                item.copy(rotationDelta = nextDelta)
            } else {
                item
            }
        }
    }

    fun applyBulkRotation(delta: Int) {
        val targets = if (selectedPageNumbers.isNotEmpty()) {
            selectedPageNumbers.toList()
        } else {
            pages.map { it.pageNumber }
        }
        applyRotation(targets, delta)
    }

    fun applyRangeSelection() {
        val info = docInfo ?: return
        val parseResult = RotatePdfEngine.parsePageRange(rangeInputText, info.pageCount)
        if (parseResult.isFailure) {
            rangeError = parseResult.exceptionOrNull()?.message
        } else {
            val validPages = parseResult.getOrThrow()
            rangeError = null
            selectedPageNumbers.clear()
            selectedPageNumbers.addAll(validPages)
            showToast("Selected ${validPages.size} page${if (validPages.size == 1) "" else "s"}.")
            rangeInputText = ""
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
                title = "Rotate PDF Pages",
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep != RotateStep.UPLOAD) {
                            if (totalRotatedCount > 0) {
                                showDiscardDialog = true
                            } else {
                                resetState()
                            }
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

            // ── Scrollable Body Grid ────────────────────────────────────────
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Section 1: Step Indicator (Full Span)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RotateStepIndicator(
                        currentStep = currentStep,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Section 2: Upload Dropzone (When in Step 1)
                if (currentStep == RotateStep.UPLOAD) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (isLoadingPdf) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
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
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    NexusText(
                                        text = "Opening PDF…",
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                            }
                        } else {
                            RotateDropzone(
                                onPickFile = { openFilePicker() },
                                errorMessage = statusMessage
                            )
                        }
                    }
                }

                // Section 3: Controls Card (When in Step 2 / 3)
                if (currentStep != RotateStep.UPLOAD && docInfo != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        RotateControlsCard(
                            docInfo = docInfo!!,
                            selectedPagesCount = selectedPageNumbers.size,
                            totalRotatedCount = totalRotatedCount,
                            onRotateAction = { delta -> applyBulkRotation(delta) },
                            rangeInputText = rangeInputText,
                            onRangeInputChange = {
                                rangeInputText = it
                                rangeError = null
                            },
                            onApplyRange = { applyRangeSelection() },
                            rangeError = rangeError,
                            onSelectAllOrClear = {
                                if (selectedPageNumbers.size == pages.size) {
                                    selectedPageNumbers.clear()
                                } else {
                                    selectedPageNumbers.clear()
                                    selectedPageNumbers.addAll(pages.map { it.pageNumber })
                                }
                            },
                            onSelectOdd = {
                                selectedPageNumbers.clear()
                                selectedPageNumbers.addAll(pages.filter { it.pageNumber % 2 != 0 }.map { it.pageNumber })
                            },
                            onSelectEven = {
                                selectedPageNumbers.clear()
                                selectedPageNumbers.addAll(pages.filter { it.pageNumber % 2 == 0 }.map { it.pageNumber })
                            },
                            onExport = {
                                if (docInfo != null && totalRotatedCount > 0) {
                                    saveDocLauncher.launch("${docInfo!!.baseName}-rotated.pdf")
                                }
                            },
                            isExporting = isExporting,
                            errorMessage = statusMessage,
                            onChangeFile = {
                                if (totalRotatedCount > 0) {
                                    showDiscardDialog = true
                                } else {
                                    resetState()
                                }
                            }
                        )
                    }

                    // Section 4: Grid of Individual Page Items
                    items(
                        items = pages,
                        key = { it.pageNumber }
                    ) { item ->
                        val isSelected = selectedPageNumbers.contains(item.pageNumber)
                        RotatePageGridItem(
                            item = item,
                            thumbnail = thumbnails[item.pageNumber],
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedPageNumbers.remove(item.pageNumber)
                                } else {
                                    selectedPageNumbers.add(item.pageNumber)
                                }
                            },
                            onRotateLeft = {
                                applyRotation(listOf(item.pageNumber), -90)
                            },
                            onRotateRight = {
                                applyRotation(listOf(item.pageNumber), 90)
                            }
                        )
                    }
                }
            }
        }

        // ── Discard Confirmation Dialog ──────────────────────────────────────
        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = {
                    NexusText(
                        text = "Discard rotations?",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                },
                text = {
                    NexusText(
                        text = "Going back will discard all your rotation changes.",
                        style = NexusTheme.typography.body.copy(fontSize = 13.5.sp),
                        color = Color(0xFF707070)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            resetState()
                        }
                    ) {
                        NexusText(
                            text = "Discard & Go Back",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        NexusText(
                            text = "Cancel",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = NexusTheme.colors.textPrimary
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = NexusTheme.colors.surface
            )
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

