@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.split_pdf

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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import java.io.File
import kotlin.math.ceil

/**
 * Split PDF screen implementing the precision HTML template specification.
 * Supports splitting by equal parts ("Every N pages") and custom page ranges ("Custom ranges").
 * Produces individual PDFs or a combined ZIP archive, completely on-device.
 */
@Composable
fun SplitPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colors = NexusTheme.colors

    // ─── State ───────────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(SplitStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<SplitDocInfo?>(null) }
    var splitMode by remember { mutableStateOf(SplitMode.EVERY) }

    // Stepper & Range Inputs
    var nValue by remember { mutableIntStateOf(1) }
    var rangeInput by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }

    // Previews & Parts
    var parts by remember { mutableStateOf<List<SplitPart>>(emptyList()) }
    val partThumbnails = remember { mutableStateMapOf<Int, Bitmap>() }
    var generationToken by remember { mutableIntStateOf(0) }

    // Processing & Export
    var isSplitting by remember { mutableStateOf(false) }
    var splitProgress by remember { mutableFloatStateOf(0f) }
    var exportResult by remember { mutableStateOf<SplitExportResult?>(null) }
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

    // ─── Compute Parts ────────────────────────────────────────────────────────
    fun updateParts(doc: SplitDocInfo) {
        if (splitMode == SplitMode.EVERY) {
            rangeError = null
            parts = SplitPdfEngine.computeEveryParts(doc.pageCount, nValue)
        } else {
            when (val res = SplitPdfEngine.parseRangeParts(rangeInput, doc.pageCount)) {
                is SplitRangeResult.Success -> {
                    rangeError = null
                    parts = res.parts
                }
                is SplitRangeResult.Error -> {
                    rangeError = res.message
                    parts = emptyList()
                }
            }
        }
    }

    // Load thumbnails for first page of each part
    fun loadPartThumbnails(doc: SplitDocInfo, currentParts: List<SplitPart>, token: Int) {
        coroutineScope.launch {
            for (p in currentParts) {
                if (token != generationToken) return@launch
                val startPage = p.startPage
                if (!partThumbnails.containsKey(startPage)) {
                    try {
                        val bmp = SplitPdfEngine.renderThumbnail(context, doc.uri, startPage - 1, 260)
                        if (token == generationToken) {
                            partThumbnails[startPage] = bmp
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Launch PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            statusMessage = "Opening PDF…"
            isStatusError = false
            coroutineScope.launch {
                try {
                    val info = SplitPdfEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    nValue = 1
                    rangeInput = ""
                    rangeError = null
                    splitMode = SplitMode.EVERY
                    partThumbnails.clear()
                    val token = ++generationToken
                    updateParts(info)
                    currentStep = SplitStep.SPLIT
                    statusMessage = null
                    loadPartThumbnails(info, parts, token)

                    if (info.pageCount == 1) {
                        showToast("This PDF has only one page, so there is nothing to split.")
                    }
                } catch (e: Exception) {
                    statusMessage = "Couldn’t open that PDF. It may be damaged or password-protected."
                    isStatusError = true
                }
            }
        }
    }

    // Perform Split
    fun performSplit() {
        val doc = docInfo ?: return
        if (parts.isEmpty() || isSplitting) return

        isSplitting = true
        splitProgress = 0f
        statusMessage = null
        isStatusError = false

        coroutineScope.launch {
            try {
                val result = SplitPdfEngine.splitPdf(
                    context = context,
                    docInfo = doc,
                    parts = parts,
                    onProgress = { p -> splitProgress = p }
                )
                exportResult = result
                currentStep = SplitStep.EXPORT
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                if (result.zipFile != null) {
                    showToast("Done! ${result.totalParts} PDFs saved in a ZIP (${SplitPdfEngine.formatBytes(result.totalBytes)}).")
                } else {
                    showToast("Done! Your PDF was saved (${SplitPdfEngine.formatBytes(result.totalBytes)}).")
                }
            } catch (e: Exception) {
                statusMessage = "Something went wrong while splitting. The PDF may be damaged."
                isStatusError = true
            } finally {
                isSplitting = false
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
                    // Back button
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

                    // Scissors Logo Squircle
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
                        text = "Split PDF",
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
                // Header Titles
                NexusText(
                    text = "Split one PDF into many.",
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
                    text = "Cut a PDF by page ranges or into equal parts and get every piece in a ZIP. Everything runs on your device.",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Steps Progress Indicator (1 Upload -> 2 Split -> 3 Export)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SplitStep.entries.forEachIndexed { idx, step ->
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

                        if (idx < SplitStep.entries.size - 1) {
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
                if (currentStep == SplitStep.UPLOAD) {
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
                            // Cut Illustration (Two document minis with scissors in between)
                            Box(
                                modifier = Modifier
                                    .size(width = 140.dp, height = 90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Left mini
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = 10.dp)
                                        .size(width = 48.dp, height = 66.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface)
                                        .border(1.5.dp, colors.primary, RoundedCornerShape(8.dp))
                                )
                                // Right mini
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .offset(x = (-10).dp)
                                        .size(width = 48.dp, height = 66.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface)
                                        .border(1.5.dp, colors.primary, RoundedCornerShape(8.dp))
                                )
                                // Dashed cut line
                                Canvas(modifier = Modifier.fillMaxHeight().width(2.dp)) {
                                    drawLine(
                                        color = colors.primary,
                                        start = Offset(size.width / 2, 0f),
                                        end = Offset(size.width / 2, size.height),
                                        strokeWidth = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                    )
                                }
                                // Center scissors circle
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCut,
                                        contentDescription = null,
                                        tint = colors.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
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
                                style = NexusTheme.typography.body.copy(
                                    fontSize = 14.5.sp
                                ),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )

                            // Choose File Button
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
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
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

                // ─── STEP 2: SPLIT CONTROLS & WORKSPACE ───────────────────────
                if (currentStep == SplitStep.SPLIT && docInfo != null) {
                    val doc = docInfo!!

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            // Selected File Overview Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.45f))
                                    .border(1.dp, colors.divider, RoundedCornerShape(20.dp))
                                    .padding(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Thumbnail preview
                                    val thumb = partThumbnails[1]
                                    Box(
                                        modifier = Modifier
                                            .size(width = 150.dp, height = 200.dp)
                                            .shadow(8.dp, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .border(1.dp, colors.divider, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb.asImageBitmap(),
                                                contentDescription = "Document preview",
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
                                    }

                                    // "PDF" Chip
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.primary)
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        NexusText(
                                            text = "PDF",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = colors.onPrimary
                                        )
                                    }

                                    // File Name & Meta
                                    NexusText(
                                        text = doc.name,
                                        style = NexusTheme.typography.title.copy(
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )

                                    NexusText(
                                        text = "${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"} · ${SplitPdfEngine.formatBytes(doc.sizeBytes)}",
                                        style = NexusTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = colors.textSecondary
                                    )

                                    // Change file button
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surface)
                                            .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                            .springBounceClick {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                pdfPickerLauncher.launch("application/pdf")
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = null,
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            NexusText(
                                                text = "Change file",
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

                            // Segmented Mode Switcher (.seg in HTML)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(5.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    SplitMode.entries.forEach { mode ->
                                        val isSelected = splitMode == mode
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) colors.surface else Color.Transparent)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    splitMode = mode
                                                    updateParts(doc)
                                                    val token = ++generationToken
                                                    loadPartThumbnails(doc, parts, token)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NexusText(
                                                text = mode.title,
                                                style = NexusTheme.typography.body.copy(
                                                    fontSize = 13.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (isSelected) colors.textPrimary else colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // ─── Mode 1: Every N Pages ────────────────────────
                            if (splitMode == SplitMode.EVERY) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    NexusText(
                                        text = "PAGES PER FILE",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = colors.textSecondary
                                    )

                                    // Stepper
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Minus Button
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.surface)
                                                .border(1.5.dp, colors.divider, RoundedCornerShape(14.dp))
                                                .springBounceClick {
                                                    if (nValue > 1) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        nValue--
                                                        updateParts(doc)
                                                        val token = ++generationToken
                                                        loadPartThumbnails(doc, parts, token)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NexusText(
                                                text = "−",
                                                style = NexusTheme.typography.title.copy(
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = colors.textPrimary
                                            )
                                        }

                                        // Number Display
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                                .border(1.5.dp, colors.divider, RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NexusText(
                                                text = "$nValue",
                                                style = NexusTheme.typography.title.copy(
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = colors.textPrimary
                                            )
                                        }

                                        // Plus Button
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.surface)
                                                .border(1.5.dp, colors.divider, RoundedCornerShape(14.dp))
                                                .springBounceClick {
                                                    if (nValue < doc.pageCount) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        nValue++
                                                        updateParts(doc)
                                                        val token = ++generationToken
                                                        loadPartThumbnails(doc, parts, token)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NexusText(
                                                text = "+",
                                                style = NexusTheme.typography.title.copy(
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = colors.textPrimary
                                            )
                                        }
                                    }

                                    // Quick Choices
                                    val half = ceil(doc.pageCount / 2.0).toInt()
                                    val quickOptions = mutableListOf<Pair<Int, String>>()
                                    quickOptions.add(1 to "Each page")
                                    if (doc.pageCount >= 2) quickOptions.add(2 to "Every 2")
                                    if (doc.pageCount >= 5) quickOptions.add(5 to "Every 5")
                                    if (doc.pageCount >= 10) quickOptions.add(10 to "Every 10")
                                    if (doc.pageCount > 2 && quickOptions.none { it.first == half }) {
                                        quickOptions.add(half to "In half")
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        items(quickOptions) { (optN, optLabel) ->
                                            val isSelected = nValue == optN
                                            Box(
                                                modifier = Modifier
                                                    .height(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) colors.primary else colors.surface)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (isSelected) colors.primary else colors.divider,
                                                        shape = CircleShape
                                                    )
                                                    .springBounceClick {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        nValue = optN
                                                        updateParts(doc)
                                                        val token = ++generationToken
                                                        loadPartThumbnails(doc, parts, token)
                                                    }
                                                    .padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                NexusText(
                                                    text = optLabel,
                                                    style = NexusTheme.typography.caption.copy(
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                                    ),
                                                    color = if (isSelected) colors.onPrimary else colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // ─── Mode 2: Custom Ranges ────────────────────
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    NexusText(
                                        text = "PAGE RANGES (ONE FILE PER RANGE)",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = colors.textSecondary
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                            .border(
                                                width = 1.5.dp,
                                                color = if (rangeError != null) colors.error else colors.divider,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = rangeInput,
                                            onValueChange = {
                                                rangeInput = it
                                                updateParts(doc)
                                                val token = ++generationToken
                                                loadPartThumbnails(doc, parts, token)
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontFamily = NexusFontFamily,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            ),
                                            decorationBox = { innerTextField ->
                                                if (rangeInput.isEmpty()) {
                                                    NexusText(
                                                        text = if (doc.pageCount > 3) "e.g. 1-3, 4-6, 7" else "e.g. 1",
                                                        style = NexusTheme.typography.body.copy(
                                                            fontSize = 15.sp,
                                                            color = colors.textSecondary.copy(alpha = 0.6f)
                                                        )
                                                    )
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    NexusText(
                                        text = rangeError ?: "Separate ranges with commas. Each range becomes its own PDF.",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 12.5.sp,
                                            fontWeight = if (rangeError != null) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (rangeError != null) colors.error else colors.textSecondary
                                    )
                                }
                            }

                            // Divider line
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.divider)
                            )

                            // Files to create section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NexusText(
                                    text = "FILES TO CREATE",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = colors.textSecondary
                                )

                                if (parts.isNotEmpty()) {
                                    NexusText(
                                        text = "${parts.size} file${if (parts.size == 1) "" else "s"}",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.textPrimary
                                    )
                                }
                            }

                            // Parts Grid
                            if (parts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.3f))
                                        .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = if (splitMode == SplitMode.RANGE) {
                                            rangeError ?: "Type page ranges above to see the files that will be created."
                                        } else {
                                            "Nothing to split."
                                        },
                                        style = NexusTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                // 2-Column Responsive Grid of Parts
                                val chunkedParts = remember(parts) { parts.chunked(2) }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    chunkedParts.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            row.forEach { part ->
                                                PartCard(
                                                    part = part,
                                                    thumbnail = partThumbnails[part.startPage],
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (row.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            // Export Button
                            val exportLabel = when {
                                isSplitting -> "Splitting…"
                                parts.size > 1 -> "Split into ${parts.size} files (ZIP)"
                                parts.size == 1 -> "Download 1 file"
                                else -> "Split PDF"
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (parts.isNotEmpty() && !isSplitting) colors.primary else colors.surfaceVariant)
                                    .springBounceClick {
                                        if (parts.isNotEmpty() && !isSplitting) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            performSplit()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isSplitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = colors.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.FileDownload,
                                            contentDescription = null,
                                            tint = if (parts.isNotEmpty()) colors.onPrimary else colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    NexusText(
                                        text = exportLabel,
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (parts.isNotEmpty() && !isSplitting) colors.onPrimary else colors.textSecondary
                                    )
                                }
                            }

                            // Progress Bar when splitting
                            if (isSplitting) {
                                val animatedP by animateFloatAsState(targetValue = splitProgress, label = "splitProgress")
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                // ─── STEP 3: SUCCESS / EXPORTED ───────────────────────────────
                if (currentStep == SplitStep.EXPORT && exportResult != null) {
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
                                text = "Splitting Complete!",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )

                            NexusText(
                                text = if (res.zipFile != null) {
                                    "Successfully created ${res.totalParts} PDF files packed into ZIP (${SplitPdfEngine.formatBytes(res.totalBytes)})."
                                } else {
                                    "Successfully created 1 PDF file (${SplitPdfEngine.formatBytes(res.totalBytes)})."
                                },
                                style = NexusTheme.typography.body.copy(fontSize = 14.5.sp),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )

                            // Share Action Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.primary)
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val targetFile = res.zipFile ?: res.outputFiles.firstOrNull()
                                        if (targetFile != null) {
                                            try {
                                                val fileUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    targetFile
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = if (res.zipFile != null) "application/zip" else "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Split Files"))
                                            } catch (e: Exception) {
                                                showToast("Cannot open share sheet: ${e.message}")
                                            }
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
                                        text = if (res.zipFile != null) "Share ZIP Archive" else "Share Split PDF",
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.onPrimary
                                    )
                                }
                            }

                            // Split Another PDF Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface)
                                    .border(1.5.dp, colors.divider, RoundedCornerShape(14.dp))
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentStep = SplitStep.UPLOAD
                                        docInfo = null
                                        parts = emptyList()
                                        exportResult = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "Split another PDF",
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
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

/**
 * Single part preview card matching `.part` in HTML.
 */
@Composable
private fun PartCard(
    part: SplitPart,
    thumbnail: Bitmap?,
    modifier: Modifier = Modifier
) {
    val colors = NexusTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.divider, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Thumbnail container (.pthumb in HTML: aspect 0.74, badges)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Part ${part.index}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.primary,
                        strokeWidth = 2.dp
                    )
                }

                // Top-Left Index Badge (.idx in HTML)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "${part.index}",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.onPrimary
                    )
                }

                // Bottom-Right Count Badge (.cnt in HTML)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "${part.pageCount} p${if (part.pageCount == 1) "" else "s"}",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF0A0A0A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            NexusText(
                text = "Part ${part.index}",
                style = NexusTheme.typography.body.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            NexusText(
                text = part.label,
                style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

