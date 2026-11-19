@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.reorder_pdf

import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.reorder_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReorderPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ── Main State ──────────────────────────────────────────────────────────
    var docInfo by remember { mutableStateOf<ReorderPdfDocInfo?>(null) }
    var currentStep by remember { mutableStateOf(ReorderStep.UPLOAD) }

    var pageOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var history by remember { mutableStateOf<List<List<Int>>>(emptyList()) }
    var historyIndex by remember { mutableIntStateOf(0) }

    val thumbnails = remember { mutableStateMapOf<Int, Bitmap>() }
    var generationToken by remember { mutableIntStateOf(0) }

    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Page preview dialog state
    var previewPageNumber by remember { mutableStateOf<Int?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Floating Toast
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

    val isOriginalOrder = remember(pageOrder) {
        pageOrder.isNotEmpty() && pageOrder.indices.all { pageOrder[it] == it + 1 }
    }

    fun commitOrder(next: List<Int>) {
        if (next == pageOrder) return
        val newHist = history.take(historyIndex + 1) + listOf(next)
        history = newHist
        historyIndex = newHist.size - 1
        pageOrder = next
    }

    fun loadThumbnails(doc: ReorderPdfDocInfo, gen: Int) {
        coroutineScope.launch {
            for (p in 1..doc.pageCount) {
                if (gen != generationToken) return@launch
                try {
                    val bmp = ReorderPdfEngine.renderPageThumbnail(context, doc.uri, p - 1, 180)
                    if (gen != generationToken) return@launch
                    thumbnails[p] = bmp
                } catch (_: Exception) {}
                delay(15)
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
                    val info = ReorderPdfEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    val initialOrder = (1..info.pageCount).toList()
                    pageOrder = initialOrder
                    history = listOf(initialOrder)
                    historyIndex = 0
                    thumbnails.clear()
                    val gen = ++generationToken
                    currentStep = ReorderStep.REORDER
                    statusMessage = if (info.pageCount < 2) "This PDF has only one page, so there's nothing to reorder." else null
                    loadThumbnails(info, gen)
                } catch (e: Exception) {
                    statusMessage = "Couldn't open that PDF. It may be damaged or password-protected."
                    isError = true
                }
            }
        }
    }

    // Save Reordered PDF
    fun saveReorderedPdf() {
        val doc = docInfo ?: return
        if (isOriginalOrder || isBusy) return

        isBusy = true
        statusMessage = null

        coroutineScope.launch {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir

                val outputFile = File(downloadsDir, "${doc.baseName}-reordered.pdf")
                val result = ReorderPdfEngine.reorderPdf(
                    context = context,
                    inputUri = doc.uri,
                    newOrder = pageOrder,
                    outputFile = outputFile
                )

                currentStep = ReorderStep.SAVE
                showToast("Done! Saved ${result.pageCount} pages in your new order.")
            } catch (e: Exception) {
                statusMessage = "Something went wrong while saving. The PDF may be damaged."
                isError = true
            } finally {
                isBusy = false
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
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_reorder_pdf),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                        NexusText(
                            text = "Reorder Pages",
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ── Stepper Header ───────────────────────────────────────────
                item {
                    ReorderStepIndicator(currentStep = currentStep)
                }

                // ── Step 1: Dropzone ─────────────────────────────────────────
                if (currentStep == ReorderStep.UPLOAD || docInfo == null) {
                    item {
                        ReorderDropzone(
                            onPickFile = { pdfPickerLauncher.launch("application/pdf") },
                            errorMessage = if (isError) statusMessage else null
                        )
                    }
                } else {
                    // ── Step 2 / 3: Workspace Card ───────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(24.dp))
                                .padding(24.dp)
                        ) {
                            // Title & Subtitle
                            NexusText(
                                text = "Reorder Pages",
                                style = NexusTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.6).sp
                                ),
                                color = Color(0xFF0A0A0A)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            NexusText(
                                text = "Drag pages into a new order",
                                style = NexusTheme.typography.body.copy(
                                    fontSize = 14.5.sp
                                ),
                                color = Color(0xFF707070)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // ── File Info & History Controls Box ─────────────
                            val doc = docInfo!!
                            ReorderFileHeaderBox(
                                fileName = doc.name,
                                pageCount = doc.pageCount,
                                fileSizeBytes = doc.sizeBytes,
                                formattedSize = ReorderPdfEngine.formatBytes(doc.sizeBytes),
                                canUndo = historyIndex > 0,
                                canRedo = historyIndex < history.size - 1,
                                canReset = !isOriginalOrder,
                                isOriginalOrder = isOriginalOrder,
                                onChangeFile = { pdfPickerLauncher.launch("application/pdf") },
                                onUndo = {
                                    if (historyIndex > 0) {
                                        historyIndex--
                                        pageOrder = history[historyIndex]
                                    }
                                },
                                onRedo = {
                                    if (historyIndex < history.size - 1) {
                                        historyIndex++
                                        pageOrder = history[historyIndex]
                                    }
                                },
                                onReset = {
                                    commitOrder((1..doc.pageCount).toList())
                                },
                                onReverseOrder = {
                                    commitOrder(pageOrder.reversed())
                                }
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // ── Save Reordered PDF Primary Button ────────────
                            val canSave = !isOriginalOrder && !isBusy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (canSave) Color(0xFF0A0A0A) else Color(0xFFEBEBEB))
                                    .clickable(enabled = canSave) { saveReorderedPdf() }
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(19.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    NexusText(
                                        text = "Saving…",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (canSave) Color.White else Color(0xFF707070)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    NexusText(
                                        text = "Save reordered PDF",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (canSave) Color.White else Color(0xFF707070)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // ── List of Reorderable Page Rows ────────────────
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pageOrder.forEachIndexed { index, pNum ->
                                    ReorderPageRow(
                                        pageNumber = pNum,
                                        currentIndex = index,
                                        totalCount = pageOrder.size,
                                        thumbnail = thumbnails[pNum],
                                        onMoveUp = {
                                            if (index > 0) {
                                                val next = pageOrder.toMutableList()
                                                val temp = next[index]
                                                next[index] = next[index - 1]
                                                next[index - 1] = temp
                                                commitOrder(next)
                                            }
                                        },
                                        onMoveDown = {
                                            if (index < pageOrder.size - 1) {
                                                val next = pageOrder.toMutableList()
                                                val temp = next[index]
                                                next[index] = next[index + 1]
                                                next[index + 1] = temp
                                                commitOrder(next)
                                            }
                                        },
                                        onPreview = {
                                            val doc = docInfo ?: return@ReorderPageRow
                                            previewPageNumber = pNum
                                            isLoadingPreview = true
                                            previewBitmap = null
                                            coroutineScope.launch {
                                                try {
                                                    previewBitmap = ReorderPdfEngine.renderPageHighRes(
                                                        context = context,
                                                        uri = doc.uri,
                                                        pageIndex = pNum - 1
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

                            Spacer(modifier = Modifier.height(16.dp))

                            NexusText(
                                text = "Hold the dots and drag, or use the arrows. Tap a thumbnail to preview the page.",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFFA3A3A3),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!statusMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                NexusText(
                                    text = statusMessage ?: "",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isError) NexusTheme.colors.error else NexusTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // ── Page Preview Dialog ──────────────────────────────────────────
            PagePreviewDialog(
                isOpen = previewPageNumber != null,
                pageNumber = previewPageNumber ?: 1,
                previewBitmap = previewBitmap,
                isLoading = isLoadingPreview,
                onDismiss = {
                    previewPageNumber = null
                    previewBitmap = null
                }
            )

            // ── Floating Toast Pill ──────────────────────────────────────────
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
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
