@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.extract_pages

import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.extract_pages.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExtractPagesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // ── Main State ──────────────────────────────────────────────────────────
    var docInfo by remember { mutableStateOf<ExtractPdfDocInfo?>(null) }
    var currentStep by remember { mutableStateOf(ExtractStep.UPLOAD) }

    val selectedPages = remember { mutableStateListOf<Int>() }
    var lastInteractedPage by remember { mutableStateOf<Int?>(null) }

    val thumbnails = remember { mutableStateMapOf<Int, Bitmap>() }
    var generationToken by remember { mutableIntStateOf(0) }

    var rangeInput by remember { mutableStateOf("") }
    var rangeErrorMessage by remember { mutableStateOf<String?>(null) }

    var isExporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Page preview dialog state
    var previewPageNumber by remember { mutableStateOf<Int?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Floating toast
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

    fun loadThumbnails(doc: ExtractPdfDocInfo, gen: Int) {
        coroutineScope.launch {
            for (p in 1..doc.pageCount) {
                if (gen != generationToken) return@launch
                try {
                    val bmp = ExtractPagesEngine.renderThumbnail(context, doc.uri, p - 1, 260)
                    if (gen != generationToken) return@launch
                    thumbnails[p] = bmp
                } catch (_: Exception) {}
                delay(12)
            }
        }
    }

    // PDF file picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            statusMessage = "Opening PDF…"
            isError = false
            coroutineScope.launch {
                try {
                    val info = ExtractPagesEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    selectedPages.clear()
                    lastInteractedPage = null
                    rangeInput = ""
                    rangeErrorMessage = null
                    thumbnails.clear()
                    val gen = ++generationToken
                    currentStep = ExtractStep.SELECT
                    statusMessage = null
                    loadThumbnails(info, gen)
                } catch (e: Exception) {
                    statusMessage = "Couldn't open that PDF. It may be damaged or password-protected."
                    isError = true
                }
            }
        }
    }

    // Apply Range Text Action
    fun applyRange() {
        val doc = docInfo ?: return
        when (val result = ExtractPagesEngine.parsePageRanges(rangeInput, doc.pageCount)) {
            is RangeParseResult.Error -> {
                rangeErrorMessage = result.message
            }
            is RangeParseResult.Success -> {
                rangeErrorMessage = null
                var newlyAdded = 0
                for (p in result.pages) {
                    if (!selectedPages.contains(p)) {
                        selectedPages.add(p)
                        newlyAdded++
                    }
                }
                lastInteractedPage = result.pages.firstOrNull()
                showToast("Selected ${result.pages.size} page${if (result.pages.size == 1) "" else "s"}.")
                rangeInput = ""
            }
        }
    }

    // Export PDF Action
    fun exportPages() {
        val doc = docInfo ?: return
        if (selectedPages.isEmpty() || isExporting) return

        isExporting = true
        statusMessage = null

        coroutineScope.launch {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir

                val outputFile = File(downloadsDir, "${doc.baseName}-extracted.pdf")
                val result = ExtractPagesEngine.extractPages(
                    context = context,
                    inputUri = doc.uri,
                    selectedPages = selectedPages.toList(),
                    outputFile = outputFile
                )

                currentStep = ExtractStep.EXPORT
                showToast("Done! Saved ${result.extractedPagesCount} page${if (result.extractedPagesCount == 1) "" else "s"} (${ExtractPagesEngine.formatBytes(result.fileSizeBytes)}).")
            } catch (e: Exception) {
                statusMessage = "Something went wrong while exporting. The PDF may be damaged."
                isError = true
            } finally {
                isExporting = false
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0A0A0A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_extract_pages),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                        NexusText(
                            text = "Extract Pages",
                            style = NexusTheme.typography.title.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = Color(0xFF0A0A0A)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0A0A0A)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Stepper Header Span ───────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ExtractStepIndicator(currentStep = currentStep)
                }

                // ── Step 1: Dropzone (when no document loaded) ───────────────
                if (currentStep == ExtractStep.UPLOAD || docInfo == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExtractDropzone(
                            onPickFile = { pdfPickerLauncher.launch("application/pdf") },
                            errorMessage = if (isError) statusMessage else null
                        )
                    }
                } else {
                    // ── Step 2 / 3: Workspace Controls Card Span ─────────────
                    val doc = docInfo!!
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExtractControlsCard(
                            fileName = doc.name,
                            pageCount = doc.pageCount,
                            formattedSize = ExtractPagesEngine.formatBytes(doc.sizeBytes),
                            rangeInput = rangeInput,
                            onRangeInputChange = {
                                rangeInput = it
                                rangeErrorMessage = null
                            },
                            onApplyRange = { applyRange() },
                            rangeErrorMessage = rangeErrorMessage,
                            selectedCount = selectedPages.size,
                            onSelectAllOrClear = {
                                if (selectedPages.size == doc.pageCount) {
                                    selectedPages.clear()
                                } else {
                                    selectedPages.clear()
                                    selectedPages.addAll(1..doc.pageCount)
                                }
                            },
                            onInvert = {
                                val inverted = (1..doc.pageCount).filter { !selectedPages.contains(it) }
                                selectedPages.clear()
                                selectedPages.addAll(inverted)
                            },
                            onExport = { exportPages() },
                            isExporting = isExporting,
                            onChangeFile = {
                                docInfo = null
                                selectedPages.clear()
                                currentStep = ExtractStep.UPLOAD
                                pdfPickerLauncher.launch("application/pdf")
                            }
                        )
                    }

                    // ── Page Grid Cards ──────────────────────────────────────
                    items(doc.pageCount) { index ->
                        val pageNum = index + 1
                        val isSelected = selectedPages.contains(pageNum)
                        ExtractPageGridItem(
                            pageNumber = pageNum,
                            isSelected = isSelected,
                            thumbnail = thumbnails[pageNum],
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedPages.remove(pageNum)
                                } else {
                                    selectedPages.add(pageNum)
                                }
                                lastInteractedPage = pageNum
                            },
                            onPreview = {
                                previewPageNumber = pageNum
                                isLoadingPreview = true
                                previewBitmap = null
                                coroutineScope.launch {
                                    try {
                                        previewBitmap = ExtractPagesEngine.renderHighRes(
                                            context = context,
                                            uri = doc.uri,
                                            pageIndex = pageNum - 1
                                        )
                                    } catch (_: Exception) {}
                                    finally {
                                        isLoadingPreview = false
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ── Floating Export Bar (Bottom pill when scrolled) ─────────────
            val isScrolled = remember {
                derivedStateOf { gridState.firstVisibleItemIndex >= 2 }
            }

            ExtractFloatingBar(
                isVisible = isScrolled.value && selectedPages.isNotEmpty() && docInfo != null,
                selectedCount = selectedPages.size,
                isExporting = isExporting,
                onExport = { exportPages() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )

            // ── Page Preview Dialog ──────────────────────────────────────────
            val previewNum = previewPageNumber
            if (docInfo != null && previewNum != null) {
                ExtractPagePreviewDialog(
                    isOpen = true,
                    pageNumber = previewNum,
                    totalPages = docInfo?.pageCount ?: 1,
                    previewBitmap = previewBitmap,
                    isSelected = selectedPages.contains(previewNum),
                    isLoading = isLoadingPreview,
                    onToggleSelect = {
                        if (selectedPages.contains(previewNum)) {
                            selectedPages.remove(previewNum)
                        } else {
                            selectedPages.add(previewNum)
                        }
                    },
                    onDismiss = {
                        previewPageNumber = null
                        previewBitmap = null
                    }
                )
            }

            // ── Floating Toast Pill ──────────────────────────────────────────
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isScrolled.value && selectedPages.isNotEmpty()) 80.dp else 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0A0A0A))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = Color.White
                    )
                    NexusText(
                        text = toastMessage ?: "",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
