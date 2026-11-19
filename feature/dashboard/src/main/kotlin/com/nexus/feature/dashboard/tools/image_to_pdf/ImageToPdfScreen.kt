@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.image_to_pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.image_to_pdf.components.ImageToPdfControlsCard
import com.nexus.feature.dashboard.tools.image_to_pdf.components.ImageToPdfDropzone
import com.nexus.feature.dashboard.tools.image_to_pdf.components.ImageToPdfEditorCard
import com.nexus.feature.dashboard.tools.image_to_pdf.components.ImageToPdfStepIndicator
import kotlinx.coroutines.launch
import java.io.OutputStream

@Composable
fun ImageToPdfScreen(
    onBack: () -> Unit,
    onOpenPdf: ((Uri, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var pages by remember { mutableStateOf<List<ImagePageItem>>(emptyList()) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var config by remember { mutableStateOf(ImageToPdfConfig()) }
    var zoomScale by remember { mutableStateOf(1f) }
    var currentStep by remember { mutableStateOf(ImageToPdfStep.UPLOAD) }

    // Processing & export state
    var isSaving by remember { mutableStateOf(false) }
    var saveProgressPct by remember { mutableStateOf(0f) }
    var saveProgressLabel by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var savedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var savedPdfName by remember { mutableStateOf("images.pdf") }

    var idCounter by remember { mutableLongStateOf(1L) }

    // Helper to add list of image URIs
    fun addImageUris(uris: List<Uri>) {
        coroutineScope.launch {
            val newItems = mutableListOf<ImagePageItem>()
            for (uri in uris) {
                val (bitmap, name) = ImageToPdfEngine.loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    newItems.add(
                        ImagePageItem(
                            id = idCounter++,
                            name = name,
                            uri = uri,
                            bitmap = bitmap,
                            width = bitmap.width,
                            height = bitmap.height
                        )
                    )
                }
            }
            if (newItems.isNotEmpty()) {
                pages = pages + newItems
                if (currentStep == ImageToPdfStep.UPLOAD) {
                    currentStep = ImageToPdfStep.ARRANGE
                    currentPageIndex = 0
                }
            }
        }
    }

    // ── Pick Initial / Additional Images Launcher ────────────────────────────
    val pickMultipleImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            addImageUris(uris)
        }
    }

    // ── Single Image Picker (for Add/Replace image on current page) ───────────
    val pickSingleImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && currentPageIndex in pages.indices) {
            coroutineScope.launch {
                val (bitmap, name) = ImageToPdfEngine.loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    val currentList = pages.toMutableList()
                    val target = currentList[currentPageIndex]
                    currentList[currentPageIndex] = target.copy(
                        bitmap = bitmap,
                        name = name,
                        uri = uri,
                        width = bitmap.width,
                        height = bitmap.height,
                        rotationDeg = 0
                    )
                    pages = currentList
                }
            }
        }
    }

    // ── PDF Document Save Launcher ───────────────────────────────────────────
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri: Uri? ->
        if (destinationUri == null || pages.isEmpty()) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            isSaving = true
            saveProgressPct = 0f
            saveProgressLabel = "Preparing document..."

            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { outStream: OutputStream ->
                    ImageToPdfEngine.generatePdf(
                        pages = pages,
                        config = config,
                        outputStream = outStream
                    ) { current, total ->
                        saveProgressPct = current.toFloat() / total.toFloat()
                        saveProgressLabel = "Saving page $current of $total..."
                    }
                }

                currentStep = ImageToPdfStep.SAVE
                savedPdfUri = destinationUri
                showSuccessDialog = true
            } catch (err: Exception) {
                Toast.makeText(context, "Failed to save PDF: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
        }
    }

    // ── Scaffold Layout ──────────────────────────────────────────────────────
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
                                painter = painterResource(id = R.drawable.ic_image_to_pdf),
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = NexusTheme.colors.background
                            )
                        }

                        NexusText(
                            text = "Image to PDF",
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
            // ── Hero Section ──────────────────────────────────────────────────
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        NexusText(
                            text = "Turn images into a PDF",
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
                        text = "Combine photos and screenshots into one document. Reorder, rotate and save, all on your device.",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // ── 3-Step Progress Bar ───────────────────────────────────
                    ImageToPdfStepIndicator(currentStep = currentStep)
                }
            }

            // ── Step 1: Dropzone ──────────────────────────────────────────────
            if (pages.isEmpty()) {
                item {
                    ImageToPdfDropzone(
                        onPickImages = { pickMultipleImagesLauncher.launch("image/*") },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else {
                // ── Step 2: Head Card with Summary & Actions ──────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(NexusTheme.colors.surface)
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Head Icon
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(NexusTheme.colors.textPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pdf_to_image),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = NexusTheme.colors.background
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Head Info
                            Column(modifier = Modifier.weight(1f)) {
                                NexusText(
                                    text = "Image to PDF",
                                    style = NexusTheme.typography.title.copy(
                                        fontSize = 17.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )
                                NexusText(
                                    text = "${pages.size} page${if (pages.size == 1) "" else "s"}",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = NexusTheme.colors.textSecondary
                                )
                            }

                            // Head Actions: Clear all & Save PDF
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Clear all button
                                NexusText(
                                    text = "Clear all",
                                    style = NexusTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NexusTheme.colors.textSecondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .springBounceClick {
                                            pages = emptyList()
                                            currentPageIndex = 0
                                            currentStep = ImageToPdfStep.UPLOAD
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )

                                // Save PDF Button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NexusTheme.colors.textPrimary)
                                        .clickable(enabled = !isSaving) {
                                            savePdfLauncher.launch(savedPdfName)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = NexusTheme.colors.background
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        NexusText(
                                            text = "Saving…",
                                            style = NexusTheme.typography.body.copy(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = NexusTheme.colors.background
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = NexusTheme.colors.background
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        NexusText(
                                            text = "Save PDF",
                                            style = NexusTheme.typography.body.copy(
                                                fontSize = 13.5.sp,
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

                // Controls & Editor Cards
                item {
                    ImageToPdfControlsCard(
                        pages = pages,
                        currentPageIndex = currentPageIndex,
                        config = config,
                        zoomScale = zoomScale,
                        onSelectPage = { currentPageIndex = it },
                        onMovePageUp = { idx ->
                            if (idx > 0) {
                                val list = pages.toMutableList()
                                val item = list.removeAt(idx)
                                list.add(idx - 1, item)
                                pages = list
                                currentPageIndex = idx - 1
                            }
                        },
                        onMovePageDown = { idx ->
                            if (idx < pages.size - 1) {
                                val list = pages.toMutableList()
                                val item = list.removeAt(idx)
                                list.add(idx + 1, item)
                                pages = list
                                currentPageIndex = idx + 1
                            }
                        },
                        onDeletePage = { idx ->
                            val list = pages.toMutableList()
                            list.removeAt(idx)
                            pages = list
                            if (pages.isEmpty()) {
                                currentStep = ImageToPdfStep.UPLOAD
                                currentPageIndex = 0
                            } else {
                                currentPageIndex = currentPageIndex.coerceAtMost(pages.size - 1)
                            }
                        },
                        onAddPages = { pickMultipleImagesLauncher.launch("image/*") },
                        onAddBlankPage = {
                            val blank = ImagePageItem(id = idCounter++, name = "Blank page")
                            pages = pages + blank
                            currentPageIndex = pages.size - 1
                        },
                        onZoomIn = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3f) },
                        onZoomOut = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.5f) },
                        onZoomReset = { zoomScale = 1f },
                        onConfigChange = { config = it }
                    )
                }

                item {
                    val activePage = pages.getOrNull(currentPageIndex)
                    ImageToPdfEditorCard(
                        page = activePage,
                        pageIndex = currentPageIndex,
                        config = config,
                        zoomScale = zoomScale,
                        onRotateImage = {
                            if (activePage != null && activePage.bitmap != null) {
                                val list = pages.toMutableList()
                                val current = list[currentPageIndex]
                                list[currentPageIndex] = current.copy(
                                    rotationDeg = (current.rotationDeg + 90) % 360
                                )
                                pages = list
                            }
                        },
                        onRemoveImage = {
                            if (activePage != null) {
                                val list = pages.toMutableList()
                                val current = list[currentPageIndex]
                                list[currentPageIndex] = current.copy(
                                    bitmap = null,
                                    name = "Blank page",
                                    rotationDeg = 0
                                )
                                pages = list
                            }
                        },
                        onAddOrReplaceImage = { pickSingleImageLauncher.launch("image/*") },
                        onDeletePage = {
                            val list = pages.toMutableList()
                            list.removeAt(currentPageIndex)
                            pages = list
                            if (pages.isEmpty()) {
                                currentStep = ImageToPdfStep.UPLOAD
                                currentPageIndex = 0
                            } else {
                                currentPageIndex = currentPageIndex.coerceAtMost(pages.size - 1)
                            }
                        }
                    )
                }

                // Progress indicator during export
                if (isSaving) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                NexusText(
                                    text = saveProgressLabel,
                                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                                    color = NexusTheme.colors.textSecondary
                                )
                                NexusText(
                                    text = "${(saveProgressPct * 100).toInt()}%",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { saveProgressPct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
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
                        text = "PDF Created",
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
                    text = "Saved a ${pages.size}-page PDF document successfully.",
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
                        if (uri != null) {
                            if (onOpenPdf != null) {
                                onOpenPdf(uri, savedPdfName)
                            } else {
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(viewIntent, "Open PDF"))
                            }
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
                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
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
