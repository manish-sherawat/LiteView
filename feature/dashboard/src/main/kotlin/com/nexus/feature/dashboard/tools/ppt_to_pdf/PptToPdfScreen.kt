@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.ppt_to_pdf

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
import com.nexus.feature.dashboard.tools.ppt_to_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PptToPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // ── Main State ──────────────────────────────────────────────────────────
    var docInfo by remember { mutableStateOf<PptDocInfo?>(null) }
    var currentStep by remember { mutableStateOf(PptStep.UPLOAD) }

    var slides by remember { mutableStateOf<List<PptSlideItem>>(emptyList()) }
    val selectedSlideNumbers = remember { mutableStateListOf<Int>() }

    var isLoadingSlides by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Dialog preview state
    var previewSlideNumber by remember { mutableStateOf<Int?>(null) }

    // Floating toast feedback matching HTML
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
        if (isExporting || isLoadingSlides) return
        docInfo = null
        slides = emptyList()
        selectedSlideNumbers.clear()
        currentStep = PptStep.UPLOAD
        statusMessage = null
        exportProgress = null
    }

    // Intercept back button when in SELECT step
    BackHandler(enabled = currentStep != PptStep.UPLOAD) {
        resetState()
    }

    // ── PDF Save Launcher ───────────────────────────────────────────────────
    val saveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && docInfo != null && selectedSlideNumbers.isNotEmpty()) {
            isExporting = true
            statusMessage = null
            exportProgress = 0.05f

            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        PptToPdfEngine.convertSlidesToPdf(
                            context = context,
                            docInfo = docInfo!!,
                            slides = slides,
                            selectedSlideNumbers = selectedSlideNumbers.toSet(),
                            outputStream = outStream,
                            onProgress = { current, total ->
                                exportProgress = current.toFloat() / total.toFloat()
                            }
                        )
                    }
                    val count = selectedSlideNumbers.size
                    currentStep = PptStep.CONVERT
                    showToast("Done! Saved $count slide${if (count == 1) "" else "s"} as PDF.")
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

    // ── PowerPoint File Picker ──────────────────────────────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingSlides = true
            statusMessage = null
            loadProgress = 0f

            coroutineScope.launch {
                try {
                    val info = PptToPdfEngine.readDocInfo(context, uri)
                    docInfo = info

                    val parsedSlides = PptToPdfEngine.loadSlideItems(context, uri) { current, total ->
                        loadProgress = current.toFloat() / total.toFloat()
                    }

                    if (parsedSlides.isEmpty()) {
                        throw IllegalStateException("No slides found in presentation.")
                    }

                    slides = parsedSlides
                    selectedSlideNumbers.clear()
                    selectedSlideNumbers.addAll(parsedSlides.map { it.slideNumber }) // Select all by default

                    currentStep = PptStep.SELECT
                    statusMessage = null
                } catch (e: Exception) {
                    statusMessage = "Couldn't open that file. It may be damaged or password-protected."
                } finally {
                    isLoadingSlides = false
                }
            }
        }
    }

    fun openFilePicker() {
        filePickerLauncher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    // ── Export Action ───────────────────────────────────────────────────────
    fun triggerExport() {
        val info = docInfo ?: return
        if (selectedSlideNumbers.isEmpty()) return
        saveDocLauncher.launch("${info.baseName}.pdf")
    }

    // Check if user has scrolled past top controls for floating bar
    val showFloatingBar by remember {
        derivedStateOf {
            currentStep == PptStep.SELECT &&
                gridState.firstVisibleItemIndex >= 1 &&
                selectedSlideNumbers.isNotEmpty()
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
                title = "PowerPoint to PDF",
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep != PptStep.UPLOAD) {
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
                columns = GridCells.Adaptive(minSize = 155.dp),
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
                    PptStepIndicator(
                        currentStep = currentStep,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // ── Section 2: Upload Dropzone (When in Step 1) ──────────────
                if (currentStep == PptStep.UPLOAD) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (isLoadingSlides) {
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
                                        text = "Reading slides… this can take a moment.",
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                            }
                        } else {
                            PptDropzone(
                                onPickFile = { openFilePicker() },
                                errorMessage = statusMessage
                            )
                        }
                    }
                }

                // ── Section 3: Controls Card (When in Step 2 / 3) ────────────
                if (currentStep != PptStep.UPLOAD && docInfo != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PptControlsCard(
                            fileName = docInfo!!.name,
                            slideCount = slides.size,
                            formattedSize = PptToPdfEngine.formatFileSize(docInfo!!.sizeBytes),
                            selectedCount = selectedSlideNumbers.size,
                            onSelectAllOrClear = {
                                if (selectedSlideNumbers.size == slides.size) {
                                    selectedSlideNumbers.clear()
                                } else {
                                    selectedSlideNumbers.clear()
                                    selectedSlideNumbers.addAll(slides.map { it.slideNumber })
                                }
                            },
                            onInvert = {
                                val currentSet = selectedSlideNumbers.toSet()
                                val inverted = slides.map { it.slideNumber }.filterNot { it in currentSet }
                                selectedSlideNumbers.clear()
                                selectedSlideNumbers.addAll(inverted)
                            },
                            onExport = { triggerExport() },
                            isExporting = isExporting,
                            progress = exportProgress,
                            errorMessage = statusMessage,
                            onChangeFile = { resetState() }
                        )
                    }

                    // ── Section 4: Grid of Presentation Slides ──────────────
                    items(
                        items = slides,
                        key = { it.slideNumber }
                    ) { slide ->
                        val isSelected = selectedSlideNumbers.contains(slide.slideNumber)
                        PptSlideGridItem(
                            item = slide,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedSlideNumbers.remove(slide.slideNumber)
                                } else {
                                    selectedSlideNumbers.add(slide.slideNumber)
                                }
                            },
                            onOpenPreview = {
                                previewSlideNumber = slide.slideNumber
                            }
                        )
                    }
                }
            }
        }

        // ── Floating Bottom Export Bar ───────────────────────────────────────
        PptFloatingBar(
            isVisible = showFloatingBar,
            selectedCount = selectedSlideNumbers.size,
            isExporting = isExporting,
            onExport = { triggerExport() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        )

        // ── Modal Slide Preview Dialog ───────────────────────────────────────
        if (previewSlideNumber != null) {
            val previewSlide = slides.find { it.slideNumber == previewSlideNumber }
            val isSelected = selectedSlideNumbers.contains(previewSlideNumber)

            PptSlidePreviewDialog(
                slideNumber = previewSlideNumber!!,
                totalSlides = slides.size,
                bitmap = previewSlide?.fullBitmap ?: previewSlide?.thumbnail,
                isSelected = isSelected,
                onToggleSelect = {
                    val p = previewSlideNumber ?: return@PptSlidePreviewDialog
                    if (selectedSlideNumbers.contains(p)) {
                        selectedSlideNumbers.remove(p)
                    } else {
                        selectedSlideNumbers.add(p)
                    }
                },
                onDismiss = { previewSlideNumber = null }
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

