package com.nexus.feature.dashboard.tools.watermark_pdf

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.feature.dashboard.tools.watermark_pdf.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkPdfScreen(
    onBack: () -> Unit,
    onOpenPdf: ((Uri, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var config by remember { mutableStateOf(WatermarkConfig.DEFAULTS) }
    var fileInfo by remember { mutableStateOf<PdfFileInfo?>(null) }
    var currentStep by remember { mutableStateOf(WatermarkStep.UPLOAD) }
    var processState by remember { mutableStateOf(WatermarkProcessState.IDLE) }
    var errorMessage by remember { mutableStateOf("") }
    var savedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    var previewPageIndex by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    fun selectPreviewPage(targetPage: Int) {
        val file = fileInfo ?: return
        val validPage = targetPage.coerceIn(0, file.pageCount - 1)
        previewPageIndex = validPage
        coroutineScope.launch {
            val bmp = WatermarkPdfEngine.renderPageThumbnail(context, file.uri, validPage)
            if (bmp != null) {
                previewBitmap = bmp
            }
        }
    }

    // Launcher for picking the source PDF file
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val info = WatermarkPdfEngine.readPdfMetadata(context, uri)
                    fileInfo = info
                    previewPageIndex = 0
                    previewBitmap = info.firstPageBitmap
                    currentStep = WatermarkStep.CUSTOMIZE
                    processState = WatermarkProcessState.IDLE
                    errorMessage = ""
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (e: Exception) {
                    errorMessage = "Failed to read PDF file: ${e.message}"
                }
            }
        }
    }

    // Launcher for saving the watermarked PDF file to user-chosen destination
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val currentFile = fileInfo
        if (destUri != null && currentFile != null) {
            processState = WatermarkProcessState.PROCESSING
            errorMessage = ""
            coroutineScope.launch {
                try {
                    val count = context.contentResolver.openOutputStream(destUri)?.use { out ->
                        WatermarkPdfEngine.applyWatermark(
                            context = context,
                            inputUri = currentFile.uri,
                            outputStream = out,
                            config = config
                        )
                    } ?: 0

                    savedPdfUri = destUri
                    processState = WatermarkProcessState.SUCCESS
                    currentStep = WatermarkStep.DOWNLOAD
                    showSuccessDialog = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Watermarked $count pages successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    processState = WatermarkProcessState.ERROR
                    errorMessage = e.message ?: "An error occurred while watermarking the document."
                }
            }
        }
    }

    Scaffold(
        topBar = {
            NexusTopBar(
                title = "Watermark PDF",
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .clip(CircleShape)
                            .springBounceClick { onBack() }
                            .padding(NexusTheme.spacing.sm)
                            .size(24.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                    )
                },
                actions = {
                    // Offline Security Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .clip(CircleShape)
                            .background(NexusTheme.colors.primary.copy(alpha = 0.10f))
                            .border(0.8.dp, NexusTheme.colors.primary.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lock),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        NexusText(
                            text = "Offline",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.primary
                        )
                    }
                }
            )
        },
        containerColor = NexusTheme.colors.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 10.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Section 1: Hero Header ────────────────────────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        NexusText(
                            text = "Watermark your PDF",
                            style = NexusTheme.typography.h1.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                        NexusText(
                            text = ".",
                            style = NexusTheme.typography.h1.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = NexusTheme.colors.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    NexusText(
                        text = "Stamp words like DRAFT across every page. Your file never leaves this device.",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // ── 3-Step Stepper Progress Bar ───────────────────────────
                    WatermarkStepIndicator(currentStep = currentStep)
                }
            }

            // ── Section 2: Upload Dropzone (When No File is Picked) ────────────
            if (fileInfo == null) {
                item {
                    WatermarkDropzone(
                        onPickFile = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                }
            } else {
                // ── Section 3: File Bar (Selected File Details) ───────────────
                item {
                    val currentFile = fileInfo!!
                    FileBarItem(
                        fileName = currentFile.name,
                        fileMeta = "${currentFile.pageCount} page${if (currentFile.pageCount == 1) "" else "s"} · ${WatermarkPdfEngine.formatBytes(currentFile.sizeBytes)}",
                        onChangeFile = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                }

                // ── Section 4: Live Preview Card ──────────────────────────────
                item {
                    WatermarkPreviewCard(
                        config = config,
                        fileInfo = fileInfo,
                        previewBitmap = previewBitmap,
                        pageIndex = previewPageIndex,
                        onPrevPage = { selectPreviewPage(previewPageIndex - 1) },
                        onNextPage = { selectPreviewPage(previewPageIndex + 1) }
                    )
                }

                // ── Section 5: Customization Controls Card ────────────────────
                item {
                    WatermarkControlsCard(
                        config = config,
                        onConfigChange = { config = it },
                        onReset = {
                            config = WatermarkConfig.DEFAULTS
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }

                // ── Section 6: Action Button (Watermark & Save) ───────────────
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (errorMessage.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                NexusText(
                                    text = errorMessage,
                                    style = NexusTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }

                        val isBusy = processState == WatermarkProcessState.PROCESSING

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(8.dp, shape = RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(NexusTheme.colors.textPrimary)
                                .clickable(enabled = !isBusy) {
                                    val current = fileInfo ?: return@clickable
                                    val defaultOutName = "${current.name.removeSuffix(".pdf")}-watermarked.pdf"
                                    savePdfLauncher.launch(defaultOutName)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NexusTheme.colors.background
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                NexusText(
                                    text = "Processing...",
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NexusTheme.colors.background
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = NexusTheme.colors.background
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                NexusText(
                                    text = "Watermark & save",
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NexusTheme.colors.background
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Success Confirmation Dialog ───────────────────────────────────────────
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            containerColor = NexusTheme.colors.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    NexusText(
                        text = "Watermark Complete",
                        style = NexusTheme.typography.title.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }
            },
            text = {
                NexusText(
                    text = "Your document has been watermarked offline with \"${config.text}\" across all ${fileInfo?.pageCount ?: 1} pages and saved safely.",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        val uri = savedPdfUri
                        val name = fileInfo?.name ?: "Document.pdf"
                        if (uri != null && onOpenPdf != null) {
                            onOpenPdf(uri, name)
                        }
                    }
                ) {
                    NexusText(
                        text = "Open PDF",
                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = NexusTheme.colors.primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val uri = savedPdfUri
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Watermarked PDF"))
                        }
                    }
                ) {
                    NexusText(
                        text = "Share",
                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }
        )
    }
}

/**
 * File bar item displaying selected PDF details and a "Change file" button.
 */
@Composable
private fun FileBarItem(
    fileName: String,
    fileMeta: String,
    onChangeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF Badge with folded corner illusion
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NexusTheme.colors.textPrimary),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "PDF",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = NexusTheme.colors.background
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Name & Meta
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                NexusText(
                    text = fileName,
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NexusText(
                    text = fileMeta,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Change File Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { onChangeFile() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                NexusText(
                    text = "Change file",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            }
        }
    }
}
