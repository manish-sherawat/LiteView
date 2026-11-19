@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.pdf_to_image

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.pdf_to_image.components.PdfToImageControlsCard
import com.nexus.feature.dashboard.tools.pdf_to_image.components.PdfToImageDropzone
import com.nexus.feature.dashboard.tools.pdf_to_image.components.PdfToImagePreviewCard
import com.nexus.feature.dashboard.tools.pdf_to_image.components.PdfToImageStepIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.roundToInt

@Composable
fun PdfToImageScreen(
    onBack: () -> Unit,
    onOpenExportedFile: ((Uri, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var docInfo by remember { mutableStateOf<PdfDocInfo?>(null) }
    var config by remember { mutableStateOf(PdfToImageConfig()) }
    var currentStep by remember { mutableStateOf(PdfToImageStep.UPLOAD) }

    // Embedded images scanning state
    var embeddedImages by remember { mutableStateOf<List<ExtractedImageItem>>(emptyList()) }
    var isScanningImages by remember { mutableStateOf(false) }

    // Preview state
    var currentPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentPreviewIndex by remember { mutableStateOf(0) }
    var zoomScale by remember { mutableStateOf(1f) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    // Export & progress state
    var isBusy by remember { mutableStateOf(false) }
    var exportProgressPct by remember { mutableStateOf(0f) }
    var exportProgressLabel by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var savedFileUri by remember { mutableStateOf<Uri?>(null) }
    var savedFileName by remember { mutableStateOf("") }
    var savedFileMimeType by remember { mutableStateOf("") }

    // Helper to calculate total items to convert based on mode and custom page range
    val pagesList = remember(docInfo, config.pagesSelectionMode, config.customPagesRange) {
        val total = docInfo?.pageCount ?: 1
        if (config.pagesSelectionMode == PagesSelectionMode.ALL) {
            (1..total).toList()
        } else {
            PdfToImageEngine.parsePageRange(config.customPagesRange, total) ?: emptyList()
        }
    }

    val totalItemsCount = if (config.mode == ExportMode.PAGES) pagesList.size else embeddedImages.size

    // ── Function to refresh the live preview bitmap ─────────────────────────
    fun refreshPreview(index: Int = currentPreviewIndex) {
        val doc = docInfo ?: return
        coroutineScope.launch {
            isPreviewLoading = true
            if (config.mode == ExportMode.PAGES) {
                val pageNumber = pagesList.getOrNull(index) ?: 1
                val bmp = PdfToImageEngine.renderPage(
                    context = context,
                    uri = doc.uri,
                    pageIndex = pageNumber - 1,
                    scale = config.resolutionScale
                )
                currentPreviewBitmap = bmp
            } else {
                val item = embeddedImages.getOrNull(index)
                currentPreviewBitmap = item?.bitmap
            }
            isPreviewLoading = false
        }
    }

    // Trigger preview refresh on relevant config change
    LaunchedEffect(docInfo, config.mode, config.resolutionScale, pagesList, currentPreviewIndex) {
        if (docInfo != null) {
            refreshPreview(currentPreviewIndex)
        }
    }

    // ── Document Pick Launcher ──────────────────────────────────────────────
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val info = PdfToImageEngine.readDocInfo(context, uri)
                    docInfo = info
                    currentStep = PdfToImageStep.SETTINGS
                    currentPreviewIndex = 0
                    zoomScale = 1f

                    // Start scanning embedded images asynchronously
                    isScanningImages = true
                    embeddedImages = emptyList()
                    val images = PdfToImageEngine.extractEmbeddedImages(context, uri)
                    embeddedImages = images
                    isScanningImages = false

                    refreshPreview(0)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Document Save Launchers (Single Image vs Multi-file ZIP) ────────────
    val saveExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (totalItemsCount > 1) "application/zip" else config.format.mimeType
        )
    ) { destinationUri: Uri? ->
        if (destinationUri == null || docInfo == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            isBusy = true
            exportProgressPct = 0f
            exportProgressLabel = "Preparing export..."

            val doc = docInfo!!
            val baseName = doc.name.removeSuffix(".pdf")
            val fmt = config.format

            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { outStream: OutputStream ->
                    if (config.mode == ExportMode.PAGES) {
                        if (pagesList.size == 1) {
                            // Single page -> direct image
                            val pageNum = pagesList.first()
                            exportProgressLabel = "Rendering page $pageNum..."
                            val bmp = PdfToImageEngine.renderPage(context, doc.uri, pageNum - 1, config.resolutionScale)
                                ?: throw IllegalStateException("Failed to render page")
                            PdfToImageEngine.exportSingleImage(bmp, fmt, config.qualityPct, outStream)
                            savedFileName = "$baseName-page-$pageNum.${fmt.extension}"
                            savedFileMimeType = fmt.mimeType
                        } else {
                            // Multiple pages -> Zip
                            val entries = mutableListOf<Pair<String, Bitmap>>()
                            val pad = pagesList.maxOrNull()?.toString()?.length ?: 2
                            pagesList.forEachIndexed { i, pNum ->
                                exportProgressLabel = "Rendering page ${i + 1} of ${pagesList.size}..."
                                exportProgressPct = (i.toFloat() / pagesList.size.toFloat()) * 0.7f
                                val bmp = PdfToImageEngine.renderPage(context, doc.uri, pNum - 1, config.resolutionScale)
                                if (bmp != null) {
                                    val formattedNum = pNum.toString().padStart(pad, '0')
                                    entries.add("$baseName-page-$formattedNum.${fmt.extension}" to bmp)
                                }
                            }
                            exportProgressLabel = "Compressing ZIP archive..."
                            PdfToImageEngine.exportImagesToZip(
                                images = entries,
                                format = fmt,
                                qualityPct = config.qualityPct,
                                outputStream = outStream
                            ) { cur, tot ->
                                exportProgressPct = 0.7f + ((cur.toFloat() / tot.toFloat()) * 0.3f)
                                exportProgressLabel = "Archiving $cur of $tot..."
                            }
                            savedFileName = "$baseName-pages.zip"
                            savedFileMimeType = "application/zip"
                        }
                    } else {
                        // Embedded images mode
                        if (embeddedImages.size == 1) {
                            val item = embeddedImages.first()
                            PdfToImageEngine.exportSingleImage(item.bitmap, fmt, config.qualityPct, outStream)
                            savedFileName = "$baseName-image-01.${fmt.extension}"
                            savedFileMimeType = fmt.mimeType
                        } else {
                            val entries = embeddedImages.mapIndexed { idx, item ->
                                "$baseName-image-${String.format("%02d", idx + 1)}.${fmt.extension}" to item.bitmap
                            }
                            exportProgressLabel = "Creating ZIP archive..."
                            PdfToImageEngine.exportImagesToZip(
                                images = entries,
                                format = fmt,
                                qualityPct = config.qualityPct,
                                outputStream = outStream
                            ) { cur, tot ->
                                exportProgressPct = (cur.toFloat() / tot.toFloat())
                                exportProgressLabel = "Archiving $cur of $tot..."
                            }
                            savedFileName = "$baseName-images.zip"
                            savedFileMimeType = "application/zip"
                        }
                    }
                }

                currentStep = PdfToImageStep.DOWNLOAD
                savedFileUri = destinationUri
                showSuccessDialog = true
            } catch (err: Exception) {
                Toast.makeText(context, "Export failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isBusy = false
                exportProgressPct = 1f
            }
        }
    }

    // ── Main UI Screen Layout ────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NexusTheme.colors.textPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pdf_to_image),
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = NexusTheme.colors.background
                            )
                        }

                        NexusText(
                            text = "PDF to Images",
                            style = NexusTheme.typography.title.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
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
                actions = {
                    // 100% Offline Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        NexusText(
                            text = "Offline",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NexusTheme.colors.background
                )
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
            // ── Hero Header Section ───────────────────────────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        NexusText(
                            text = "Turn your PDF into images",
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
                        text = "Convert pages or pull out embedded pictures as JPEG, PNG or WEBP. Your file never leaves this device.",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // ── 3-Step Progress Indicator ─────────────────────────────
                    PdfToImageStepIndicator(currentStep = currentStep)
                }
            }

            // ── Step 1: Dropzone ──────────────────────────────────────────────
            if (docInfo == null) {
                item {
                    PdfToImageDropzone(
                        onPickFile = { pickPdfLauncher.launch("application/pdf") },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                // ── Step 2: Workspace with Loaded File ─────────────────────────
                item {
                    val doc = docInfo!!
                    FileBarItem(
                        fileName = doc.name,
                        fileMeta = "${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"} · ${PdfToImageEngine.formatBytes(doc.sizeBytes)}",
                        onChangeFile = { pickPdfLauncher.launch("application/pdf") }
                    )
                }

                // Controls & Preview Cards
                item {
                    PdfToImageControlsCard(
                        config = config,
                        docInfo = docInfo,
                        embeddedImagesCount = embeddedImages.size,
                        isScanningImages = isScanningImages,
                        onConfigChange = { newConfig ->
                            config = newConfig
                            currentPreviewIndex = 0
                        },
                        onReset = {
                            config = PdfToImageConfig()
                            currentPreviewIndex = 0
                            zoomScale = 1f
                        }
                    )
                }

                item {
                    val doc = docInfo!!
                    val curWidth = currentPreviewBitmap?.width ?: ((doc.basePageWidth * config.resolutionScale).roundToInt())
                    val curHeight = currentPreviewBitmap?.height ?: ((doc.basePageHeight * config.resolutionScale).roundToInt())

                    PdfToImagePreviewCard(
                        config = config,
                        currentBitmap = currentPreviewBitmap,
                        currentIndex = currentPreviewIndex,
                        totalItems = totalItemsCount,
                        currentWidthPx = curWidth,
                        currentHeightPx = curHeight,
                        zoomScale = zoomScale,
                        isLoading = isPreviewLoading,
                        onPrev = {
                            if (currentPreviewIndex > 0) {
                                currentPreviewIndex--
                            }
                        },
                        onNext = {
                            if (currentPreviewIndex < totalItemsCount - 1) {
                                currentPreviewIndex++
                            }
                        },
                        onZoomIn = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.5f) },
                        onZoomOut = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.35f) },
                        onZoomReset = { zoomScale = 1f }
                    )
                }

                // ── Action Bar: Convert & Save Button ─────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        val isImages = config.mode == ExportMode.IMAGES
                        val count = totalItemsCount
                        val noun = if (isImages) "image" else "page"
                        val buttonText = when {
                            isBusy -> exportProgressLabel.ifEmpty { "Converting..." }
                            count > 0 -> "${if (isImages) "Extract" else "Convert"} $count $noun${if (count == 1) "" else "s"}"
                            isImages -> "No images to extract"
                            else -> "Choose valid pages"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(NexusTheme.colors.textPrimary)
                                .clickable(enabled = !isBusy && count > 0) {
                                    val currentDoc = docInfo ?: return@clickable
                                    val base = currentDoc.name.removeSuffix(".pdf")
                                    val outName = if (count == 1) {
                                        "$base-${if (isImages) "image-1" else "page-1"}.${config.format.extension}"
                                    } else {
                                        "$base-${if (isImages) "images" else "pages"}.zip"
                                    }
                                    saveExportLauncher.launch(outName)
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
                                    text = buttonText,
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 15.5.sp,
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
                                    text = buttonText,
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NexusTheme.colors.background
                                )
                            }
                        }

                        // Progress indicator bar
                        if (isBusy) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { exportProgressPct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = NexusTheme.colors.textPrimary,
                                trackColor = NexusTheme.colors.divider.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Success Dialog ────────────────────────────────────────────────────────
    if (showSuccessDialog) {
        val isZip = savedFileMimeType == "application/zip"
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
                        text = "Export Complete",
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
                    text = if (isZip) {
                        "Your images have been packaged and saved safely as \"$savedFileName\"."
                    } else {
                        "Your image has been saved safely as \"$savedFileName\"."
                    },
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
                        val uri = savedFileUri
                        if (uri != null) {
                            if (onOpenExportedFile != null) {
                                onOpenExportedFile(uri, savedFileName)
                            } else {
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, savedFileMimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(viewIntent, "Open File"))
                            }
                        }
                    }
                ) {
                    NexusText(
                        text = "Open",
                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = NexusTheme.colors.primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val uri = savedFileUri
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = savedFileMimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
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
            // PDF Badge
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

            // Change Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .background(NexusTheme.colors.surface)
                    .clickable { onChangeFile() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                NexusText(
                    text = "Change file",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            }
        }
    }
}
