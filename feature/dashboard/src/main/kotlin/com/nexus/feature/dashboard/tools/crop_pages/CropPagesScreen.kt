@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.crop_pages

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.nexus.core.theme.NexusFontFamily
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Crop PDF Pages screen implementing the precision HTML template specification.
 * Supports auto-trimming margins, custom interactive crop boxes with handles,
 * aspect ratio locks, copying crops across odd/even/all pages or custom ranges,
 * and page-by-page visual filmstrip preview.
 */
@Composable
fun CropPagesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colors = NexusTheme.colors

    // ─── State ───────────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(CropStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<CropDocInfo?>(null) }
    var curPage by remember { mutableIntStateOf(1) } // 1-indexed

    // Page-to-CropRect map
    val crops = remember { mutableStateMapOf<Int, CropRect>() }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Range copy input
    var rangeInput by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }

    // Exporting
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportResult by remember { mutableStateOf<CropExportResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

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

    // Load page bitmap
    fun loadPageBitmap(doc: CropDocInfo, pageNum: Int) {
        if (!pageBitmaps.containsKey(pageNum)) {
            isLoadingPreview = true
            coroutineScope.launch {
                try {
                    val bmp = CropPagesEngine.renderThumbnail(context, doc.uri, pageNum - 1, 600)
                    pageBitmaps[pageNum] = bmp
                } catch (_: Exception) {}
                finally {
                    isLoadingPreview = false
                }
            }
        }
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
                    val info = CropPagesEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    curPage = 1
                    crops.clear()
                    pageBitmaps.clear()
                    rangeInput = ""
                    rangeError = null
                    currentStep = CropStep.CROP
                    statusMessage = null
                    loadPageBitmap(info, 1)

                    // Preload thumbnails for first few pages
                    for (p in 1..minOf(6, info.pageCount)) {
                        loadPageBitmap(info, p)
                    }
                } catch (e: Exception) {
                    statusMessage = "Couldn’t open that PDF. It may be damaged or password-protected."
                    isStatusError = true
                }
            }
        }
    }

    // Copy crop to given pages
    fun copyCropTo(pages: List<Int>) {
        val currentCrop = crops[curPage] ?: return
        var count = 0
        pages.forEach { p ->
            if (p != curPage) {
                crops[p] = currentCrop
                count++
            }
        }
        showToast(if (count > 0) "Copied crop to $count page${if (count == 1) "" else "s"}." else "No other pages to copy to.")
    }

    // Auto-trim margins on current page
    fun runAutoTrim() {
        val bmp = pageBitmaps[curPage]
        if (bmp == null) {
            showToast("Preview loading, please wait.")
            return
        }
        val trimmed = CropPagesEngine.autoTrim(bmp)
        if (trimmed.isFull) {
            showToast("This page looks blank, so there is nothing to trim.")
        } else {
            crops[curPage] = trimmed
            showToast("Margins trimmed. Fine-tune with handles.")
        }
    }

    // Execute Export
    fun performExport() {
        val doc = docInfo ?: return
        val croppedCount = crops.count { !it.value.isFull }
        if (croppedCount == 0 || isExporting) return

        isExporting = true
        exportProgress = 0f
        statusMessage = null
        isStatusError = false

        coroutineScope.launch {
            try {
                val result = CropPagesEngine.cropPdf(
                    context = context,
                    docInfo = doc,
                    crops = crops.toMap(),
                    onProgress = { p -> exportProgress = p }
                )
                exportResult = result
                currentStep = CropStep.EXPORT
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showToast("Done! Cropped ${result.croppedPagesCount} pages (${CropPagesEngine.formatBytes(result.fileSizeBytes)}).")
            } catch (e: Exception) {
                statusMessage = "Something went wrong while cropping. The PDF may be damaged."
                isStatusError = true
            } finally {
                isExporting = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // ─── Procedural dot-grid background accent (matching HTML) ────────────
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

        val isScrolled by remember { derivedStateOf { scrollState.value > 10 } }

        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Sticky Top Bar ───────────────────────────────────────────────
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
                            .springBounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
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

                    // Crop Logo Squircle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCut,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    NexusText(
                        text = "Crop Pages",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp
                        ),
                        color = colors.textPrimary
                    )
                }
            }

            // ─── Scrollable Body Content ──────────────────────────────────────
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
                    text = "Crop any page, your way.",
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
                    text = "Trim margins or keep just the part you need. Give every page its own crop, or copy one crop to many pages. Everything runs on your device.",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Steps Indicator (1 Upload -> 2 Crop -> 3 Export)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CropStep.entries.forEachIndexed { idx, step ->
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

                        if (idx < CropStep.entries.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(1.5.dp)
                                    .background(if (currentStep.number > step.number) colors.primary else colors.divider)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ─── STEP 1: UPLOAD DROPZONE ──────────────────────────────────
                if (currentStep == CropStep.UPLOAD) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
                            .springBounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pdfPickerLauncher.launch("application/pdf")
                            }
                            .padding(vertical = 44.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Crop Box Illustration
                            Box(
                                modifier = Modifier
                                    .size(width = 140.dp, height = 100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Mini document
                                Box(
                                    modifier = Modifier
                                        .size(width = 68.dp, height = 92.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface)
                                        .border(1.5.dp, colors.divider, RoundedCornerShape(8.dp))
                                )
                                // Interactive crop rectangle with corner anchor handles
                                Box(
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 72.dp)
                                        .border(2.dp, colors.primary, RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.35f))
                                ) {
                                    // 4 Corner anchors
                                    listOf(
                                        Alignment.TopStart, Alignment.TopEnd,
                                        Alignment.BottomStart, Alignment.BottomEnd
                                    ).forEach { align ->
                                        Box(
                                            modifier = Modifier
                                                .align(align)
                                                .offset(
                                                    x = if (align == Alignment.TopStart || align == Alignment.BottomStart) (-4).dp else 4.dp,
                                                    y = if (align == Alignment.TopStart || align == Alignment.TopEnd) (-4).dp else 4.dp
                                                )
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colors.primary)
                                        )
                                    }
                                }
                            }

                            NexusText(
                                text = "Drop your PDF here",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )

                            NexusText(
                                text = "or browse files from your device",
                                style = NexusTheme.typography.body.copy(fontSize = 14.5.sp),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.primary)
                                    .padding(horizontal = 28.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = colors.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    NexusText(
                                        text = "Choose file",
                                        style = NexusTheme.typography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        color = colors.onPrimary
                                    )
                                }
                            }

                            if (statusMessage != null) {
                                NexusText(
                                    text = statusMessage.orEmpty(),
                                    style = NexusTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = if (isStatusError) colors.error else colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                NexusText(
                                    text = "PDF files only",
                                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                                    color = colors.textSecondary.copy(alpha = 0.6f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(colors.divider)
                                )
                                NexusText(
                                    text = "No upload, no sign-up",
                                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                                    color = colors.textSecondary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // ─── STEP 2: CROP WORKSPACE ───────────────────────────────────
                if (currentStep == CropStep.CROP && docInfo != null) {
                    val doc = docInfo!!
                    val activeCrop = crops[curPage] ?: CropRect.FULL

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            // ── Interactive Crop Stage Card (.editor in HTML) ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.45f))
                                    .border(1.dp, colors.divider, RoundedCornerShape(20.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Toolbar (Auto-trim & Reset this page)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.surface)
                                                .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                                .springBounceClick {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    runAutoTrim()
                                                }
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCut,
                                                    contentDescription = null,
                                                    tint = colors.textPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                NexusText(
                                                    text = "Auto-trim margins",
                                                    style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                                    color = colors.textPrimary
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.surface)
                                                .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                                .springBounceClick {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    crops.remove(curPage)
                                                    showToast("Reset crop for page $curPage.")
                                                }
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        ) {
                                            NexusText(
                                                text = "Reset this page",
                                                style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                                color = colors.textPrimary
                                            )
                                        }
                                    }

                                    // Interactive Stage Canvas with Shading & Crop Box
                                    val currentBmp = pageBitmaps[curPage]

                                    Box(
                                        modifier = Modifier
                                            .size(width = 250.dp, height = 345.dp)
                                            .shadow(12.dp, RoundedCornerShape(4.dp))
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                            .border(1.dp, colors.divider, RoundedCornerShape(4.dp))
                                            .pointerInput(curPage) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        // Drag start inside page
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val w = size.width.toFloat()
                                                        val h = size.height.toFloat()
                                                        val dx = dragAmount.x / w
                                                        val dy = dragAmount.y / h

                                                        val cur = crops[curPage] ?: CropRect.FULL
                                                        val newX0 = (cur.x0 + dx).coerceIn(0f, cur.x1 - 0.1f)
                                                        val newY0 = (cur.y0 + dy).coerceIn(0f, cur.y1 - 0.1f)
                                                        val newX1 = (cur.x1 + dx).coerceIn(newX0 + 0.1f, 1f)
                                                        val newY1 = (cur.y1 + dy).coerceIn(newY0 + 0.1f, 1f)

                                                        crops[curPage] = CropRect(newX0, newY0, newX1, newY1)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentBmp != null) {
                                            Image(
                                                bitmap = currentBmp.asImageBitmap(),
                                                contentDescription = "Page $curPage",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = colors.primary,
                                                strokeWidth = 2.dp
                                            )
                                        }

                                        // Semi-transparent shade overlay outside crop rectangle
                                        if (!activeCrop.isFull) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val w = size.width
                                                val h = size.height

                                                val x0 = activeCrop.x0 * w
                                                val y0 = activeCrop.y0 * h
                                                val x1 = activeCrop.x1 * w
                                                val y1 = activeCrop.y1 * h

                                                val shadeColor = Color(0x880A0A0A)

                                                // Top shade
                                                drawRect(shadeColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, y0))
                                                // Bottom shade
                                                drawRect(shadeColor, Offset(0f, y1), androidx.compose.ui.geometry.Size(w, h - y1))
                                                // Left shade
                                                drawRect(shadeColor, Offset(0f, y0), androidx.compose.ui.geometry.Size(x0, y1 - y0))
                                                // Right shade
                                                drawRect(shadeColor, Offset(x1, y0), androidx.compose.ui.geometry.Size(w - x1, y1 - y0))

                                                // Crop border & rule of thirds
                                                drawRect(
                                                    color = Color.White,
                                                    topLeft = Offset(x0, y0),
                                                    size = androidx.compose.ui.geometry.Size(x1 - x0, y1 - y0),
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                                )

                                                // Rule of thirds lines
                                                val boxW = x1 - x0
                                                val boxH = y1 - y0
                                                val gridColor = Color.White.copy(alpha = 0.45f)
                                                drawLine(gridColor, Offset(x0 + boxW / 3f, y0), Offset(x0 + boxW / 3f, y1), 1.dp.toPx())
                                                drawLine(gridColor, Offset(x0 + 2f * boxW / 3f, y0), Offset(x0 + 2f * boxW / 3f, y1), 1.dp.toPx())
                                                drawLine(gridColor, Offset(x0, y0 + boxH / 3f), Offset(x1, y0 + boxH / 3f), 1.dp.toPx())
                                                drawLine(gridColor, Offset(x0, y0 + 2f * boxH / 3f), Offset(x1, y0 + 2f * boxH / 3f), 1.dp.toPx())
                                            }
                                        }
                                    }

                                    // Page Navigation Controls (< Page X of Y >)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(colors.surface)
                                                .border(1.dp, colors.divider, CircleShape)
                                                .springBounceClick {
                                                    if (curPage > 1) {
                                                        curPage--
                                                        loadPageBitmap(doc, curPage)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Previous page",
                                                tint = if (curPage > 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        NexusText(
                                            text = "Page $curPage of ${doc.pageCount}",
                                            style = NexusTheme.typography.body.copy(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = colors.textPrimary
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(colors.surface)
                                                .border(1.dp, colors.divider, CircleShape)
                                                .springBounceClick {
                                                    if (curPage < doc.pageCount) {
                                                        curPage++
                                                        loadPageBitmap(doc, curPage)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                contentDescription = "Next page",
                                                tint = if (curPage < doc.pageCount) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Readout Text (.readout in HTML)
                                    val readout = if (activeCrop.isFull) {
                                        "No crop on this page yet"
                                    } else {
                                        "Crop: ${(activeCrop.widthShare * 100).roundToInt()}% × ${(activeCrop.heightShare * 100).roundToInt()}% of the page"
                                    }

                                    NexusText(
                                        text = readout,
                                        style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (activeCrop.isFull) colors.textSecondary else colors.primary
                                    )
                                }
                            }

                            // ── Quick shapes for this page (.ratios in HTML) ──
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                NexusText(
                                    text = "QUICK SHAPES FOR THIS PAGE",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = colors.textSecondary
                                )

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(QuickRatio.entries.toTypedArray()) { qr ->
                                        Box(
                                            modifier = Modifier
                                                .height(38.dp)
                                                .clip(CircleShape)
                                                .background(colors.surface)
                                                .border(1.dp, colors.divider, CircleShape)
                                                .springBounceClick {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (qr == QuickRatio.FREE) {
                                                        crops.remove(curPage)
                                                    } else {
                                                        val r = qr.ratio ?: 1.0f
                                                        val wShare = 0.8f
                                                        val hShare = (0.8f / r).coerceIn(0.1f, 0.95f)
                                                        val x0 = (1f - wShare) / 2f
                                                        val y0 = (1f - hShare) / 2f
                                                        crops[curPage] = CropRect(x0, y0, x0 + wShare, y0 + hShare)
                                                    }
                                                }
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NexusText(
                                                text = qr.label,
                                                style = NexusTheme.typography.caption.copy(
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Copy crop to other pages (.copy in HTML) ──────
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                NexusText(
                                    text = "COPY THIS CROP TO OTHER PAGES",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = colors.textSecondary
                                )

                                val isCropSet = !activeCrop.isFull
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isCropSet) colors.surface else colors.surfaceVariant.copy(alpha = 0.4f))
                                            .border(1.dp, colors.divider, CircleShape)
                                            .springBounceClick {
                                                if (isCropSet) copyCropTo((1..doc.pageCount).toList())
                                            }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = "All pages",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                            color = if (isCropSet) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isCropSet) colors.surface else colors.surfaceVariant.copy(alpha = 0.4f))
                                            .border(1.dp, colors.divider, CircleShape)
                                            .springBounceClick {
                                                if (isCropSet) copyCropTo((1..doc.pageCount).filter { it % 2 != 0 })
                                            }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = "Odd pages",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                            color = if (isCropSet) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isCropSet) colors.surface else colors.surfaceVariant.copy(alpha = 0.4f))
                                            .border(1.dp, colors.divider, CircleShape)
                                            .springBounceClick {
                                                if (isCropSet) copyCropTo((1..doc.pageCount).filter { it % 2 == 0 })
                                            }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = "Even pages",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                            color = if (isCropSet) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                // Range input row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                            .border(
                                                width = 1.dp,
                                                color = if (rangeError != null) colors.error else colors.divider,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = rangeInput,
                                            onValueChange = {
                                                rangeInput = it
                                                rangeError = null
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontFamily = NexusFontFamily,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            ),
                                            decorationBox = { innerTextField ->
                                                if (rangeInput.isEmpty()) {
                                                    NexusText(
                                                        text = "e.g. 2-5, 8",
                                                        style = NexusTheme.typography.body.copy(
                                                            fontSize = 14.sp,
                                                            color = colors.textSecondary.copy(alpha = 0.6f)
                                                        )
                                                    )
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isCropSet) colors.surface else colors.surfaceVariant.copy(alpha = 0.4f))
                                            .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                            .springBounceClick {
                                                if (isCropSet) {
                                                    val res = CropPagesEngine.parseRangePages(rangeInput, doc.pageCount)
                                                    res.onSuccess { pList ->
                                                        rangeError = null
                                                        copyCropTo(pList)
                                                        rangeInput = ""
                                                    }.onFailure { err ->
                                                        rangeError = err.message
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = "Apply to pages",
                                            style = NexusTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                            color = if (isCropSet) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                NexusText(
                                    text = rangeError ?: "The crop is copied as a share of the page, so it fits pages of any size.",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (rangeError != null) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (rangeError != null) colors.error else colors.textSecondary
                                )
                            }

                            // ── Page Strip (.strip in HTML) ───────────────────
                            val croppedPagesTotal = crops.count { !it.value.isFull }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NexusText(
                                    text = if (croppedPagesTotal > 0) "PAGES · $croppedPagesTotal CROPPED" else "PAGES",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = colors.textSecondary
                                )

                                if (croppedPagesTotal > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clickable { crops.clear(); showToast("Reset all page crops.") }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        NexusText(
                                            text = "Reset all",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = colors.primary
                                        )
                                    }
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items((1..doc.pageCount).toList()) { p ->
                                    val isCurrent = p == curPage
                                    val isCropped = crops[p]?.isFull == false
                                    val bmp = pageBitmaps[p]

                                    Box(
                                        modifier = Modifier
                                            .width(88.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surface)
                                            .border(
                                                width = if (isCurrent) 2.dp else 1.dp,
                                                color = if (isCurrent) colors.primary else colors.divider,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .springBounceClick {
                                                curPage = p
                                                loadPageBitmap(doc, p)
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(108.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.surfaceVariant)
                                                    .border(1.dp, colors.divider, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (bmp != null) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "Page $p",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = colors.primary,
                                                        strokeWidth = 2.dp
                                                    )
                                                }

                                                if (isCropped) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
                                                            .clip(CircleShape)
                                                            .background(colors.primary)
                                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                                    ) {
                                                        NexusText(
                                                            text = "CROPPED",
                                                            style = NexusTheme.typography.caption.copy(
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            ),
                                                            color = colors.onPrimary
                                                        )
                                                    }
                                                }
                                            }

                                            NexusText(
                                                text = "Page $p",
                                                style = NexusTheme.typography.caption.copy(
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // File overview summary
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                NexusText(
                                    text = doc.name,
                                    style = NexusTheme.typography.title.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                NexusText(
                                    text = "${doc.pageCount} pages · ${CropPagesEngine.formatBytes(doc.sizeBytes)}",
                                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                                    color = colors.textSecondary
                                )
                            }

                            // Divider
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

                            // Export Button
                            val exportLabel = if (isExporting) {
                                "Cropping…"
                            } else if (croppedPagesTotal > 0) {
                                "Crop $croppedPagesTotal page${if (croppedPagesTotal == 1) "" else "s"} and download"
                            } else {
                                "Draw a crop to continue"
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (croppedPagesTotal > 0 && !isExporting) colors.primary else colors.surfaceVariant)
                                    .springBounceClick {
                                        if (croppedPagesTotal > 0 && !isExporting) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            performExport()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isExporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = colors.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.FileDownload,
                                            contentDescription = null,
                                            tint = if (croppedPagesTotal > 0) colors.onPrimary else colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    NexusText(
                                        text = exportLabel,
                                        style = NexusTheme.typography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        color = if (croppedPagesTotal > 0 && !isExporting) colors.onPrimary else colors.textSecondary
                                    )
                                }
                            }

                            NexusText(
                                text = "Cropping hides the outside area but doesn’t delete it from the file. Don’t rely on it to remove sensitive content.",
                                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
                                color = colors.textSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (isExporting) {
                                val animatedP by animateFloatAsState(targetValue = exportProgress, label = "exportProgress")
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(animatedP)
                                            .background(colors.primary)
                                    )
                                }
                            }

                            if (statusMessage != null) {
                                NexusText(
                                    text = statusMessage.orEmpty(),
                                    style = NexusTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = if (isStatusError) colors.error else colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // ─── STEP 3: EXPORT SUCCESS ───────────────────────────────────
                if (currentStep == CropStep.EXPORT && exportResult != null) {
                    val res = exportResult!!

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            NexusText(
                                text = "Cropping Complete!",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )

                            NexusText(
                                text = "Successfully cropped ${res.croppedPagesCount} pages and saved to Downloads (${CropPagesEngine.formatBytes(res.fileSizeBytes)}).",
                                style = NexusTheme.typography.body.copy(fontSize = 14.5.sp),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )

                            // Share Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.primary)
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        try {
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                res.outputFile
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Cropped PDF"))
                                        } catch (e: Exception) {
                                            showToast("Cannot share: ${e.message}")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = colors.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    NexusText(
                                        text = "Share Cropped PDF",
                                        style = NexusTheme.typography.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                                        color = colors.onPrimary
                                    )
                                }
                            }

                            // Crop Another PDF Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface)
                                    .border(1.5.dp, colors.divider, RoundedCornerShape(14.dp))
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentStep = CropStep.UPLOAD
                                        docInfo = null
                                        crops.clear()
                                        exportResult = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "Crop another PDF",
                                    style = NexusTheme.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Floating Toast Notification ─────────────────────────────────────
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350)
            ) + fadeIn(tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 85.dp,
                    start = 20.dp,
                    end = 20.dp
                )
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primary)
                    .border(1.dp, colors.divider.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    NexusText(
                        text = toastMessage.orEmpty(),
                        style = NexusTheme.typography.body.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.onPrimary
                    )
                }
            }
        }
    }
}

