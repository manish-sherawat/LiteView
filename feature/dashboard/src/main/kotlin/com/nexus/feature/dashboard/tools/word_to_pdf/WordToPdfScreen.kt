package com.nexus.feature.dashboard.tools.word_to_pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.AnimatedSuccessCheck
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WordToPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen State
    var currentStep by remember { mutableIntStateOf(1) } // 1: Upload, 2: Select/Configure, 3: Convert/Success
    var documentModel by remember { mutableStateOf<WordDocumentModel?>(null) }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var quality by remember { mutableFloatStateOf(85f) } // 50f to 100f

    var isLoading by remember { mutableStateOf(false) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionProgress by remember { mutableFloatStateOf(0f) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var previewPage by remember { mutableStateOf<WordPageModel?>(null) }
    var savedPdfUri by remember { mutableStateOf<Uri?>(null) }

    // Pick Word document launcher
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                val result = WordDocumentRenderer.parseWordDocument(context, uri)
                isLoading = false
                result.onSuccess { model ->
                    documentModel = model
                    selectedPages = model.pages.map { it.pageNumber }.toSet()
                    currentStep = 2
                }.onFailure { err ->
                    errorMessage = err.message ?: "Failed to read Word document"
                }
            }
        }
    }

    // Save PDF destination launcher
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && documentModel != null && selectedPages.isNotEmpty()) {
            isConverting = true
            conversionProgress = 0f
            errorMessage = null

            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                        WordDocumentRenderer.exportToPdf(
                            pages = documentModel!!.pages,
                            selectedPageNumbers = selectedPages,
                            quality = quality,
                            outputStream = outputStream,
                            onProgress = { p ->
                                conversionProgress = p
                            }
                        )
                    }
                    savedPdfUri = destUri
                    currentStep = 3
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Error creating PDF"
                } finally {
                    isConverting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            NexusTopBar(
                title = "Word to PDF",
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
                }
            )
        },
        containerColor = NexusTheme.colors.background,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentStep) {
                1 -> {
                    // STEP 1: Upload Dropzone
                    WordToPdfUploadStep(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onChooseFile = {
                            docPickerLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword"
                                )
                            )
                        }
                    )
                }
                2 -> {
                    // STEP 2: Workspace & Page Grid
                    val doc = documentModel
                    if (doc != null) {
                        WordToPdfWorkspaceStep(
                            document = doc,
                            selectedPages = selectedPages,
                            quality = quality,
                            onQualityChange = { quality = it },
                            onTogglePage = { pageNum ->
                                selectedPages = if (selectedPages.contains(pageNum)) {
                                    selectedPages - pageNum
                                } else {
                                    selectedPages + pageNum
                                }
                            },
                            onInvertSelection = {
                                val allPages = doc.pages.map { it.pageNumber }.toSet()
                                selectedPages = allPages - selectedPages
                            },
                            onToggleAll = {
                                val allPages = doc.pages.map { it.pageNumber }.toSet()
                                selectedPages = if (selectedPages.size == allPages.size) {
                                    emptySet()
                                } else {
                                    allPages
                                }
                            },
                            onPreviewPage = { page ->
                                previewPage = page
                            },
                            onBackToUpload = {
                                documentModel = null
                                selectedPages = emptySet()
                                currentStep = 1
                            },
                            isConverting = isConverting,
                            conversionProgress = conversionProgress,
                            onConvert = {
                                val baseName = doc.fileName.removeSuffix(".docx").removeSuffix(".doc")
                                createPdfLauncher.launch("$baseName.pdf")
                            }
                        )
                    }
                }
                3 -> {
                    // STEP 3: Success State
                    WordToPdfSuccessStep(
                        pageCount = selectedPages.size,
                        onOpenPdf = {
                            savedPdfUri?.let { uri ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open PDF"))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No PDF viewer app found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onSharePdf = {
                            savedPdfUri?.let { uri ->
                                try {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share PDF"))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Could not share PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onConvertAnother = {
                            documentModel = null
                            selectedPages = emptySet()
                            savedPdfUri = null
                            currentStep = 1
                        }
                    )
                }
            }

            // Page Preview Modal Dialog
            previewPage?.let { page ->
                val isSelected = selectedPages.contains(page.pageNumber)
                WordPagePreviewDialog(
                    page = page,
                    totalPages = documentModel?.pages?.size ?: 1,
                    isSelected = isSelected,
                    onToggleSelect = {
                        selectedPages = if (isSelected) {
                            selectedPages - page.pageNumber
                        } else {
                            selectedPages + page.pageNumber
                        }
                    },
                    onDismiss = { previewPage = null }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1: UPLOAD & DROPZONE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordToPdfUploadStep(
    isLoading: Boolean,
    errorMessage: String?,
    onChooseFile: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Header
        WordToPdfHeader(currentStep = 1)

        Spacer(modifier = Modifier.height(28.dp))

        // Dropzone Card with dashed border
        val strokeColor = NexusTheme.colors.divider
        val surfaceColor = NexusTheme.colors.surface

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(surfaceColor)
                .drawBehind {
                    drawRoundRect(
                        color = strokeColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f), 0f)
                        ),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                }
                .clickable(enabled = !isLoading) { onChooseFile() }
                .padding(horizontal = 24.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Word Fan-Card Illustration
                WordFanCardsIllustration()

                Spacer(modifier = Modifier.height(24.dp))

                NexusText(
                    text = "Drop your Word file here",
                    style = NexusTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = NexusTheme.colors.textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                NexusText(
                    text = "or browse files from your device",
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(26.dp))

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = NexusTheme.colors.primary,
                            strokeWidth = 2.5.dp
                        )
                        NexusText(
                            text = "Reading & paginating document…",
                            style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                } else {
                    Button(
                        onClick = onChooseFile,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusTheme.colors.primary,
                            contentColor = NexusTheme.colors.onPrimary
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = "Choose file",
                            style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusTheme.colors.onPrimary
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    NexusText(
                        text = errorMessage,
                        style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                        color = NexusTheme.colors.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    NexusText(
                        text = ".docx files only",
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.textSecondary.copy(alpha = 0.8f)
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(3.dp)
                            .background(NexusTheme.colors.divider, CircleShape)
                    )
                    NexusText(
                        text = "No upload, no sign-up",
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.textSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2: WORKSPACE & PAGE SELECTION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordToPdfWorkspaceStep(
    document: WordDocumentModel,
    selectedPages: Set<Int>,
    quality: Float,
    onQualityChange: (Float) -> Unit,
    onTogglePage: (Int) -> Unit,
    onInvertSelection: () -> Unit,
    onToggleAll: () -> Unit,
    onPreviewPage: (WordPageModel) -> Unit,
    onBackToUpload: () -> Unit,
    isConverting: Boolean,
    conversionProgress: Float,
    onConvert: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Controls Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NexusTheme.colors.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.SolidColor(NexusTheme.colors.divider)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                NexusText(
                    text = "Word to PDF",
                    style = NexusTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = NexusTheme.colors.textPrimary
                )
                NexusText(
                    text = "Each selected page becomes one PDF page",
                    style = NexusTheme.typography.caption,
                    color = NexusTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // File Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, NexusTheme.colors.divider, RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                            .clickable(enabled = !isConverting) { onBackToUpload() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Choose a different file",
                            tint = NexusTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        NexusText(
                            text = document.fileName,
                            style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = NexusTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        NexusText(
                            text = "${document.pages.size} pages · ${document.fileSizeFormatted}",
                            style = NexusTheme.typography.caption,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Export Quality Section with NexusLiquidSlider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "EXPORT QUALITY",
                        style = NexusTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                NexusTheme.colors.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        NexusText(
                            text = when {
                                quality < 65f -> "Standard (${quality.toInt()}%)"
                                quality < 88f -> "High (${quality.toInt()}%)"
                                else -> "Maximum (${quality.toInt()}%)"
                            },
                            style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = NexusTheme.colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                NexusLiquidSlider(
                    value = quality,
                    onValueChange = onQualityChange,
                    valueRange = 50f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Selection Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "${selectedPages.size} of ${document.pages.size} pages selected",
                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                        color = NexusTheme.colors.textSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onInvertSelection,
                            enabled = !isConverting && document.pages.size > 1 && selectedPages.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            NexusText(
                                text = "Invert",
                                style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selectedPages.isNotEmpty()) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                            )
                        }

                        val allSelected = selectedPages.size == document.pages.size && document.pages.isNotEmpty()
                        TextButton(
                            onClick = onToggleAll,
                            enabled = !isConverting && document.pages.size > 0,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            NexusText(
                                text = if (allSelected) "Clear" else "Select all",
                                style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = NexusTheme.colors.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Convert Button
                NexusButton(
                    text = if (isConverting) "Converting…" else "Convert ${selectedPages.size} page${if (selectedPages.size == 1) "" else "s"} to PDF",
                    onClick = onConvert,
                    enabled = selectedPages.isNotEmpty() && !isConverting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                )

                // Animated Progress Bar during conversion
                AnimatedVisibility(visible = isConverting) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        LinearProgressIndicator(
                            progress = { conversionProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NexusTheme.colors.primary,
                            trackColor = NexusTheme.colors.surfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                NexusText(
                    text = "Pages are saved with high fidelity directly on your device.",
                    style = NexusTheme.typography.caption.copy(fontSize = 11.sp),
                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.8f)
                )
            }
        }

        // Grid of Pages
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(document.pages, key = { it.pageNumber }) { page ->
                val isSelected = selectedPages.contains(page.pageNumber)

                WordPageCard(
                    page = page,
                    isSelected = isSelected,
                    onToggleSelect = { onTogglePage(page.pageNumber) },
                    onPreview = { onPreviewPage(page) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAGE CARD ITEM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordPageCard(
    page: WordPageModel,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit
) {
    val borderColor = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .background(if (isSelected) NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f) else NexusTheme.colors.surface)
            .clickable { onToggleSelect() }
            .padding(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // Standard A4 ratio
                    .clip(RoundedCornerShape(8.dp))
                    .background(NexusTheme.colors.surfaceVariant)
                    .border(0.5.dp, NexusTheme.colors.divider, RoundedCornerShape(8.dp))
            ) {
                Image(
                    bitmap = page.thumbnailBitmap.asImageBitmap(),
                    contentDescription = "Page ${page.pageNumber}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Selection Checkmark Bubble (Top Left)
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(26.dp)
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(if (isSelected) NexusTheme.colors.primary else Color.Black.copy(alpha = 0.28f))
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Eye Preview Button (Top Right)
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(30.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f))
                        .border(0.5.dp, NexusTheme.colors.divider, CircleShape)
                        .clickable { onPreview() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = "Preview Page",
                        tint = NexusTheme.colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Page Label
            NexusText(
                text = "Page ${page.pageNumber}",
                style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = NexusTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAGE PREVIEW MODAL DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordPagePreviewDialog(
    page: WordPageModel,
    totalPages: Int,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NexusTheme.colors.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .shadow(24.dp, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Dialog Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "Page ${page.pageNumber} of $totalPages",
                        style = NexusTheme.typography.titleLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = NexusTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.divider)

                // Dialog Image Viewer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = page.fullBitmap.asImageBitmap(),
                        contentDescription = "Page Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shadow(6.dp, RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                HorizontalDivider(color = NexusTheme.colors.divider)

                // Dialog Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        NexusText(
                            text = "Close",
                            style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                            color = NexusTheme.colors.textPrimary
                        )
                    }

                    Button(
                        onClick = {
                            onToggleSelect()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) NexusTheme.colors.surfaceVariant else NexusTheme.colors.primary,
                            contentColor = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.onPrimary
                        )
                    ) {
                        NexusText(
                            text = if (isSelected) "Deselect this page" else "Select this page",
                            style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 3: SUCCESS STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordToPdfSuccessStep(
    pageCount: Int,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onConvertAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(NexusTheme.colors.success.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            AnimatedSuccessCheck(
                modifier = Modifier.size(48.dp),
                color = NexusTheme.colors.success,
                strokeWidth = 10f
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        NexusText(
            text = "Conversion Complete!",
            style = NexusTheme.typography.display2.copy(fontWeight = FontWeight.Bold),
            color = NexusTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        NexusText(
            text = "Saved $pageCount page${if (pageCount == 1) "" else "s"} as PDF successfully.",
            style = NexusTheme.typography.body,
            color = NexusTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        NexusButton(
            text = "Open PDF",
            onClick = onOpenPdf,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onSharePdf,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NexusTheme.colors.divider)
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                tint = NexusTheme.colors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            NexusText(
                text = "Share PDF",
                style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = NexusTheme.colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(
            onClick = onConvertAnother,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            NexusText(
                text = "Convert Another Document",
                style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = NexusTheme.colors.primary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HERO HEADER & STEP INDICATOR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordToPdfHeader(currentStep: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Brand Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(NexusTheme.colors.primary)
                    .shadow(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = NexusTheme.colors.onPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
            NexusText(
                text = "Word to PDF",
                style = NexusTheme.typography.titleLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = NexusTheme.colors.textPrimary
            )
        }

        // Title
        NexusText(
            text = "Turn Word documents into PDFs.",
            style = NexusTheme.typography.display2.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.0).sp
            ),
            color = NexusTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        NexusText(
            text = "Upload a .docx file, check every page, and download a PDF. Everything runs on your device.",
            style = NexusTheme.typography.body.copy(fontSize = 14.sp),
            color = NexusTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Breadcrumb Steps Progress
        WordToPdfStepsProgress(currentStep = currentStep)
    }
}

@Composable
private fun WordToPdfStepsProgress(currentStep: Int) {
    val steps = listOf("Upload", "Select", "Convert")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 1..3) {
            val label = steps[i - 1]
            val isActive = currentStep == i
            val isDone = currentStep > i

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> NexusTheme.colors.primary
                                isDone -> NexusTheme.colors.primary.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            1.5.dp,
                            when {
                                isActive || isDone -> NexusTheme.colors.primary
                                else -> NexusTheme.colors.divider
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = NexusTheme.colors.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        NexusText(
                            text = "$i",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isActive) NexusTheme.colors.onPrimary else NexusTheme.colors.textSecondary
                        )
                    }
                }

                NexusText(
                    text = label,
                    style = NexusTheme.typography.caption.copy(
                        fontWeight = if (isActive || isDone) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isActive || isDone) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary
                )
            }

            if (i < 3) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.5.dp)
                        .background(if (currentStep > i) NexusTheme.colors.primary else NexusTheme.colors.divider)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DROPZONE FAN-CARD ILLUSTRATION
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WordFanCardsIllustration() {
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Left Card (Rotated -8 deg)
        MiniDocumentCard(
            modifier = Modifier
                .offset(x = (-38).dp, y = 8.dp)
                .rotate(-8f)
        )

        // Right Card (Rotated +8 deg)
        MiniDocumentCard(
            modifier = Modifier
                .offset(x = 38.dp, y = 8.dp)
                .rotate(8f)
        )

        // Center Card (Upright)
        MiniDocumentCard(
            modifier = Modifier
                .offset(x = 0.dp, y = 0.dp),
            isCenter = true
        )

        // Center "W" Tag Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 4.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NexusTheme.colors.primary)
                .shadow(6.dp, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            NexusText(
                text = "W",
                style = NexusTheme.typography.titleLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = NexusTheme.colors.onPrimary
            )
        }
    }
}

@Composable
private fun MiniDocumentCard(
    modifier: Modifier = Modifier,
    isCenter: Boolean = false
) {
    val borderColor = if (isCenter) NexusTheme.colors.primary else NexusTheme.colors.divider
    val lineBaseColor = if (isCenter) NexusTheme.colors.textSecondary else NexusTheme.colors.divider

    Box(
        modifier = modifier
            .size(width = 62.dp, height = 82.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(NexusTheme.colors.surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(lineBaseColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(lineBaseColor.copy(alpha = 0.6f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(lineBaseColor.copy(alpha = 0.6f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(lineBaseColor.copy(alpha = 0.6f))
            )
        }
    }
}
