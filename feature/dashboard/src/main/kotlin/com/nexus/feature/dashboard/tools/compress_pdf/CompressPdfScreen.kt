@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.compress_pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.compress_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CompressPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var docInfo by remember { mutableStateOf<CompressPdfDocInfo?>(null) }
    var config by remember { mutableStateOf(CompressPdfConfig()) }
    var currentStep by remember { mutableStateOf(CompressStep.UPLOAD) }

    var estimates by remember { mutableStateOf<CompressEstimates?>(null) }
    var estimatedSizeBytes by remember { mutableLongStateOf(0L) }

    var isBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var compressResult by remember { mutableStateOf<CompressResult?>(null) }

    // Preview state
    var showPreviewDialog by remember { mutableStateOf(false) }
    var previewPageNum by remember { mutableIntStateOf(1) }
    var previewPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    // Floating toast state
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

    // Refresh estimate numbers whenever config or document changes
    fun updateEstimates(info: CompressPdfDocInfo?, currentConfig: CompressPdfConfig) {
        if (info == null) {
            estimates = null
            estimatedSizeBytes = 0L
            return
        }
        val calculated = CompressPdfEngine.calculateEstimates(info, currentConfig.keepTextSelectable)
        estimates = calculated
        estimatedSizeBytes = CompressPdfEngine.estimateAtQuality(currentConfig.quality, calculated)
    }

    // Open PDF file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    statusMessage = "Opening PDF…"
                    isError = false
                    val info = CompressPdfEngine.readDocInfo(context, uri)
                    docInfo = info
                    config = CompressPdfConfig()
                    compressResult = null
                    currentStep = CompressStep.COMPRESS
                    statusMessage = null
                    updateEstimates(info, config)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (e: Exception) {
                    statusMessage = "Couldn’t open that PDF. It may be damaged or password-protected."
                    isError = true
                }
            }
        }
    }

    // Save/Download compressed PDF launcher
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        val res = compressResult
        if (destUri != null && res != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        res.file.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    currentStep = CompressStep.DOWNLOAD
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showToast("Downloaded.")
                } catch (e: Exception) {
                    statusMessage = "Failed to save file: ${e.message}"
                    isError = true
                }
            }
        }
    }

    // Execute compression
    fun executeCompression() {
        val currentInfo = docInfo ?: return
        if (isBusy) return

        isBusy = true
        progress = 0f
        statusMessage = null
        isError = false

        coroutineScope.launch {
            try {
                val tempOutputFile = File(
                    context.cacheDir,
                    "compressed_${System.currentTimeMillis()}.pdf"
                )

                val result = CompressPdfEngine.compressPdf(
                    context = context,
                    docInfo = currentInfo,
                    config = config,
                    outputFile = tempOutputFile,
                    onProgress = { cur, total ->
                        progress = if (total > 0) cur.toFloat() / total.toFloat() else 0f
                    }
                )

                compressResult = result
                isBusy = false

                if (result.sizeBytes < currentInfo.sizeBytes) {
                    currentStep = CompressStep.DOWNLOAD
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val origStr = CompressPdfEngine.formatFileSize(currentInfo.sizeBytes)
                    val resStr = CompressPdfEngine.formatFileSize(result.sizeBytes)
                    showToast("Done! $origStr → $resStr")

                    // Automatically prompt save dialog
                    val baseName = currentInfo.name.removeSuffix(".pdf")
                    saveFileLauncher.launch("${baseName}-compressed.pdf")
                } else {
                    statusMessage = "That result isn’t smaller than your original, so it wasn’t saved automatically."
                    isError = false
                }
            } catch (e: Exception) {
                isBusy = false
                statusMessage = "Something went wrong while compressing. Try another quality setting."
                isError = true
            }
        }
    }

    // Load preview page
    fun loadPreview(pageIndex: Int) {
        val currentInfo = docInfo ?: return
        isPreviewLoading = true
        coroutineScope.launch {
            val bmp = CompressPdfEngine.renderPreviewPage(
                context = context,
                uri = currentInfo.uri,
                pageIndex = pageIndex - 1,
                config = config
            )
            previewPageBitmap = bmp
            isPreviewLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(NexusTheme.colors.textPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_compress_pdf),
                                contentDescription = null,
                                tint = NexusTheme.colors.background,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = "Compress PDF",
                            style = NexusTheme.typography.title.copy(
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = NexusTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NexusTheme.colors.surface
                )
            )
        },
        containerColor = NexusTheme.colors.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {
                // ── Hero Section ──────────────────────────────────────────────
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            NexusText(
                                text = "Send a smaller PDF",
                                style = NexusTheme.typography.h1.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1).sp
                                ),
                                color = NexusTheme.colors.textPrimary
                            )
                            NexusText(
                                text = ".",
                                style = NexusTheme.typography.h1.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = NexusTheme.colors.textSecondary.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        NexusText(
                            text = "Make the file smaller, and see how it will look first. Everything runs on your device.",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.5.sp,
                                color = NexusTheme.colors.textSecondary,
                                lineHeight = 20.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Progress Steps
                        CompressStepIndicator(currentStep = currentStep)
                    }
                }

                // ── Step 1: Dropzone (When no document is loaded) ──────────────
                if (docInfo == null) {
                    item {
                        CompressDropzone(
                            onPickFile = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                            errorMessage = statusMessage
                        )
                    }
                } else {
                    // ── Step 2 & 3: Workspace Grid ────────────────────────────
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Left Column: Controls Card
                            CompressControlsCard(
                                docInfo = docInfo!!,
                                config = config,
                                estimates = estimates,
                                estimatedSizeBytes = estimatedSizeBytes,
                                isBusy = isBusy,
                                progress = progress,
                                statusMessage = statusMessage,
                                isError = isError,
                                onQualityChanged = { newQuality ->
                                    config = config.copy(quality = newQuality)
                                    compressResult = null
                                    updateEstimates(docInfo, config)
                                },
                                onKeepTextChanged = { newKeep ->
                                    config = config.copy(keepTextSelectable = newKeep)
                                    compressResult = null
                                    updateEstimates(docInfo, config)
                                },
                                onClear = {
                                    docInfo = null
                                    compressResult = null
                                    estimates = null
                                    currentStep = CompressStep.UPLOAD
                                    statusMessage = null
                                },
                                onPreview = {
                                    previewPageNum = 1
                                    showPreviewDialog = true
                                    loadPreview(1)
                                },
                                onCompress = {
                                    executeCompression()
                                }
                            )

                            // Right Column: Size Comparison & Preview Card
                            CompressCompareCard(
                                originalSizeBytes = docInfo!!.sizeBytes,
                                estimatedSizeBytes = estimatedSizeBytes,
                                firstPageBitmap = docInfo!!.firstPageBitmap,
                                result = compressResult,
                                onDownload = {
                                    val baseName = docInfo!!.name.removeSuffix(".pdf")
                                    saveFileLauncher.launch("${baseName}-compressed.pdf")
                                }
                            )
                        }
                    }
                }
            }

            // ── Floating Toast Pill ───────────────────────────────────────────
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(16.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexusTheme.colors.textPrimary)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = NexusTheme.colors.background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = toastMessage ?: "",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.background
                        )
                    }
                }
            }

            // ── Preview Modal Dialog ──────────────────────────────────────────
            if (showPreviewDialog && docInfo != null) {
                CompressPreviewDialog(
                    currentPage = previewPageNum,
                    totalPages = docInfo!!.pageCount,
                    pageBitmap = previewPageBitmap,
                    isLoading = isPreviewLoading,
                    originalSizeBytes = docInfo!!.sizeBytes,
                    estimatedSizeBytes = estimatedSizeBytes,
                    onPageChange = { newPage ->
                        previewPageNum = newPage
                        loadPreview(newPage)
                    },
                    onDismiss = {
                        showPreviewDialog = false
                    },
                    onConfirm = {
                        showPreviewDialog = false
                        executeCompression()
                    }
                )
            }
        }
    }
}
