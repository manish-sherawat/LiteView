@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.page_number

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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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

/**
 * Add Page Numbers screen implementing the precision HTML template specification.
 * Provides live page preview with position alignment, format options, custom ranges,
 * font selection, text styling, and on-device stamping.
 */
@Composable
fun PageNumberScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colors = NexusTheme.colors

    // ─── State ───────────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(PageNumberStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<PageNumberDocInfo?>(null) }
    var config by remember { mutableStateOf(PageNumberConfig()) }

    // Preview
    var previewPageIndex by remember { mutableIntStateOf(1) } // 1-indexed
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Exporting
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportResult by remember { mutableStateOf<PageNumberExportResult?>(null) }
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

    // Load preview bitmap for given page
    fun loadPageBitmap(doc: PageNumberDocInfo, pageNum: Int) {
        if (!pageBitmaps.containsKey(pageNum)) {
            isLoadingPreview = true
            coroutineScope.launch {
                try {
                    val bmp = PageNumberEngine.renderThumbnail(context, doc.uri, pageNum - 1, 480)
                    pageBitmaps[pageNum] = bmp
                } catch (_: Exception) {}
                finally {
                    isLoadingPreview = false
                }
            }
        }
    }

    // File Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            statusMessage = "Opening PDF…"
            isStatusError = false
            coroutineScope.launch {
                try {
                    val info = PageNumberEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    config = config.copy(
                        fromPage = 1,
                        toPage = info.pageCount,
                        startNumber = 1
                    )
                    previewPageIndex = 1
                    pageBitmaps.clear()
                    currentStep = PageNumberStep.CUSTOMIZE
                    statusMessage = null
                    loadPageBitmap(info, 1)
                } catch (e: Exception) {
                    statusMessage = "Couldn’t open that PDF. It may be damaged or password-protected."
                    isStatusError = true
                }
            }
        }
    }

    // Execute Export
    fun performExport() {
        val doc = docInfo ?: return
        if (isExporting) return

        isExporting = true
        exportProgress = 0f
        statusMessage = null
        isStatusError = false

        coroutineScope.launch {
            try {
                val result = PageNumberEngine.addPageNumbers(
                    context = context,
                    docInfo = doc,
                    config = config,
                    onProgress = { p -> exportProgress = p }
                )
                exportResult = result
                currentStep = PageNumberStep.EXPORT
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showToast("Done! Numbered ${result.numberedPagesCount} pages (${PageNumberEngine.formatBytes(result.fileSizeBytes)}).")
            } catch (e: Exception) {
                statusMessage = "Something went wrong while adding numbers. The PDF may be damaged."
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

                    // Logo Squircle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    NexusText(
                        text = "Page Numbers",
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
                    text = "Number every page of your PDF.",
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
                    text = "Add clean page numbers anywhere on the page, with your own format, start number and style. Everything runs on your device.",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Steps Indicator (1 Upload -> 2 Customize -> 3 Export)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PageNumberStep.entries.forEachIndexed { idx, step ->
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

                        if (idx < PageNumberStep.entries.size - 1) {
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
                if (currentStep == PageNumberStep.UPLOAD) {
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
                            // 3-page illustration with page numbers 1, 2, 3
                            Box(
                                modifier = Modifier
                                    .size(width = 150.dp, height = 100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Page 1 (left)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = 10.dp)
                                        .size(width = 46.dp, height = 64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant)
                                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    NexusText(
                                        text = "1",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                // Page 3 (right)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .offset(x = (-10).dp)
                                        .size(width = 46.dp, height = 64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant)
                                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    NexusText(
                                        text = "3",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                // Page 2 (center, elevated)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(width = 54.dp, height = 74.dp)
                                        .shadow(8.dp, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface)
                                        .border(1.5.dp, colors.primary, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(y = 8.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(colors.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = "2",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = colors.onPrimary
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

                // ─── STEP 2: CUSTOMIZE WORKSPACE ──────────────────────────────
                if (currentStep == PageNumberStep.CUSTOMIZE && docInfo != null) {
                    val doc = docInfo!!

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                            // ── Live Interactive Preview Card (.preview in HTML) ──
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
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Live Page Canvas with Overlay
                                    val currentBmp = pageBitmaps[previewPageIndex]
                                    val currentLabel = PageNumberEngine.formatLabel(previewPageIndex, config)

                                    Box(
                                        modifier = Modifier
                                            .size(width = 240.dp, height = 330.dp)
                                            .shadow(12.dp, RoundedCornerShape(6.dp))
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White)
                                            .border(1.dp, colors.divider, RoundedCornerShape(6.dp))
                                            .pointerInput(Unit) {
                                                detectTapGestures { offset ->
                                                    // Map tap offset to normalized 0..1 coordinates
                                                    val normX = (offset.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                                                    val normY = (offset.y / size.height.toFloat()).coerceIn(0.05f, 0.95f)
                                                    config = config.copy(customPos = CustomPosition(normX, normY))
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentBmp != null) {
                                            Image(
                                                bitmap = currentBmp.asImageBitmap(),
                                                contentDescription = "Page $previewPageIndex",
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

                                        // Overlaid Page Number Badge
                                        if (currentLabel != null) {
                                            val fontColor = try {
                                                Color(android.graphics.Color.parseColor(config.colorHex))
                                            } catch (_: Exception) {
                                                Color.Black
                                            }.copy(alpha = config.opacity)

                                            val alignment = when {
                                                config.customPos != null -> Alignment.TopStart
                                                config.pos.v == VerticalPosition.TOP && config.pos.h == HorizontalPosition.LEFT -> Alignment.TopStart
                                                config.pos.v == VerticalPosition.TOP && config.pos.h == HorizontalPosition.CENTER -> Alignment.TopCenter
                                                config.pos.v == VerticalPosition.TOP && config.pos.h == HorizontalPosition.RIGHT -> Alignment.TopEnd
                                                config.pos.v == VerticalPosition.BOTTOM && config.pos.h == HorizontalPosition.LEFT -> Alignment.BottomStart
                                                config.pos.v == VerticalPosition.BOTTOM && config.pos.h == HorizontalPosition.CENTER -> Alignment.BottomCenter
                                                else -> Alignment.BottomEnd
                                            }

                                            val fontFam = when (config.font) {
                                                NumberFont.HELVETICA -> FontFamily.SansSerif
                                                NumberFont.TIMES -> FontFamily.Serif
                                                NumberFont.COURIER -> FontFamily.Monospace
                                            }

                                            val fontW = if (config.fstyle == NumberFontStyle.BOLD || config.fstyle == NumberFontStyle.BOLD_ITALIC) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            }

                                            val fontSt = if (config.fstyle == NumberFontStyle.ITALIC || config.fstyle == NumberFontStyle.BOLD_ITALIC) {
                                                FontStyle.Italic
                                            } else {
                                                FontStyle.Normal
                                            }

                                            val marginDp = (config.marginPt * 0.4f).dp

                                            val customP = config.customPos
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(
                                                        if (customP != null) {
                                                            Modifier.offset(
                                                                x = (240.dp * customP.x) - 20.dp,
                                                                y = (330.dp * customP.y) - 10.dp
                                                            )
                                                        } else {
                                                            Modifier.padding(marginDp)
                                                        }
                                                    ),
                                                contentAlignment = alignment
                                            ) {
                                                NexusText(
                                                    text = currentLabel,
                                                    style = TextStyle(
                                                        fontFamily = fontFam,
                                                        fontSize = (config.sizePt * 1.05f).sp,
                                                        fontWeight = fontW,
                                                        fontStyle = fontSt,
                                                        color = fontColor
                                                    )
                                                )
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
                                                    if (previewPageIndex > 1) {
                                                        previewPageIndex--
                                                        loadPageBitmap(doc, previewPageIndex)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "Previous page",
                                                tint = if (previewPageIndex > 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        NexusText(
                                            text = "Page $previewPageIndex of ${doc.pageCount}",
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
                                                    if (previewPageIndex < doc.pageCount) {
                                                        previewPageIndex++
                                                        loadPageBitmap(doc, previewPageIndex)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                contentDescription = "Next page",
                                                tint = if (previewPageIndex < doc.pageCount) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    NexusText(
                                        text = if (currentLabel != null) "Tip: tap anywhere on the page to place the number freely." else "This page is outside your range, so it won’t be numbered.",
                                        style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )

                                    // File info summary
                                    NexusText(
                                        text = doc.name,
                                        style = NexusTheme.typography.title.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    NexusText(
                                        text = "${doc.pageCount} pages · ${PageNumberEngine.formatBytes(doc.sizeBytes)}",
                                        style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
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
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = null,
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            NexusText(
                                                text = "Change file",
                                                style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Position Selector (3x2 Grid matching .pos in HTML) ─
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NexusText(
                                        text = "POSITION",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = colors.textSecondary
                                    )

                                    if (config.customPos != null) {
                                        Box(
                                            modifier = Modifier
                                                .clickable { config = config.copy(customPos = null) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            NexusText(
                                                text = "Reset position",
                                                style = NexusTheme.typography.caption.copy(
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = colors.primary
                                            )
                                        }
                                    }
                                }

                                val positions = listOf(
                                    PageNumberPosition(VerticalPosition.TOP, HorizontalPosition.LEFT) to "Top left",
                                    PageNumberPosition(VerticalPosition.TOP, HorizontalPosition.CENTER) to "Top",
                                    PageNumberPosition(VerticalPosition.TOP, HorizontalPosition.RIGHT) to "Top right",
                                    PageNumberPosition(VerticalPosition.BOTTOM, HorizontalPosition.LEFT) to "Bottom left",
                                    PageNumberPosition(VerticalPosition.BOTTOM, HorizontalPosition.CENTER) to "Bottom",
                                    PageNumberPosition(VerticalPosition.BOTTOM, HorizontalPosition.RIGHT) to "Bottom right"
                                )

                                val chunkedPos = positions.chunked(3)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    chunkedPos.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            row.forEach { (p, label) ->
                                                val isSelected = config.customPos == null && config.pos == p
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (isSelected) colors.primary else colors.surfaceVariant.copy(alpha = 0.5f))
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isSelected) colors.primary else colors.divider,
                                                            shape = RoundedCornerShape(10.dp)
                                                        )
                                                        .springBounceClick {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            config = config.copy(pos = p, customPos = null)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    NexusText(
                                                        text = label,
                                                        style = NexusTheme.typography.caption.copy(
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isSelected) colors.onPrimary else colors.textPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Options Grid ──────────────────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Format & Style
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Format
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "FORMAT",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        DropdownOptionPicker(
                                            options = NumberFormat.entries.map { it to it.title },
                                            selected = config.format,
                                            onSelected = { config = config.copy(format = it) }
                                        )
                                    }

                                    // Number Style
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "NUMBER STYLE",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        DropdownOptionPicker(
                                            options = NumberStyle.entries.map { it to it.title },
                                            selected = config.style,
                                            onSelected = { config = config.copy(style = it) }
                                        )
                                    }
                                }

                                // Start at & Number pages range
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Start numbering at
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "START NUMBERING AT",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                                .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            BasicTextField(
                                                value = "${config.startNumber}",
                                                onValueChange = { str ->
                                                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 1
                                                    config = config.copy(startNumber = num.coerceAtLeast(1))
                                                },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    fontFamily = NexusFontFamily,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.textPrimary
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    // Number pages range (from to)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "NUMBER PAGES",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                                    .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                BasicTextField(
                                                    value = "${config.fromPage}",
                                                    onValueChange = { str ->
                                                        val p = str.filter { it.isDigit() }.toIntOrNull() ?: 1
                                                        config = config.copy(fromPage = p.coerceIn(1, doc.pageCount))
                                                    },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        fontFamily = NexusFontFamily,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.textPrimary,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            NexusText(
                                                text = "to",
                                                style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                                color = colors.textSecondary
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
                                                    .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                BasicTextField(
                                                    value = "${config.toPage}",
                                                    onValueChange = { str ->
                                                        val p = str.filter { it.isDigit() }.toIntOrNull() ?: doc.pageCount
                                                        config = config.copy(toPage = p.coerceIn(1, doc.pageCount))
                                                    },
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        fontFamily = NexusFontFamily,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.textPrimary,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick Range Buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clip(CircleShape)
                                            .background(colors.surface)
                                            .border(1.dp, colors.divider, CircleShape)
                                            .springBounceClick {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                config = config.copy(fromPage = 1, toPage = doc.pageCount)
                                            }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NexusText(
                                            text = "Number all pages",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                            color = colors.textPrimary
                                        )
                                    }

                                    if (doc.pageCount > 1) {
                                        Box(
                                            modifier = Modifier
                                                .height(34.dp)
                                                .clip(CircleShape)
                                                .background(colors.surface)
                                                .border(1.dp, colors.divider, CircleShape)
                                                .springBounceClick {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    config = config.copy(fromPage = 2, toPage = doc.pageCount)
                                                    if (previewPageIndex == 1) {
                                                        previewPageIndex = 2
                                                        loadPageBitmap(doc, 2)
                                                    }
                                                }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NexusText(
                                                text = "Skip the first page",
                                                style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }

                                // Font & Size
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "FONT",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        DropdownOptionPicker(
                                            options = NumberFont.entries.map { it to it.title },
                                            selected = config.font,
                                            onSelected = { config = config.copy(font = it) }
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "SIZE",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        val sizes = listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 24f)
                                        DropdownOptionPicker(
                                            options = sizes.map { it to "${it.toInt()} pt" },
                                            selected = config.sizePt,
                                            onSelected = { config = config.copy(sizePt = it) }
                                        )
                                    }
                                }

                                // Margin & Font Style
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "DISTANCE FROM EDGE",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        val margins = listOf(20f to "Close (20)", 36f to "Normal (36)", 54f to "Far (54)", 72f to "Very far (72)")
                                        DropdownOptionPicker(
                                            options = margins,
                                            selected = config.marginPt,
                                            onSelected = { config = config.copy(marginPt = it) }
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        NexusText(
                                            text = "TEXT STYLE",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        DropdownOptionPicker(
                                            options = NumberFontStyle.entries.map { it to it.title },
                                            selected = config.fstyle,
                                            onSelected = { config = config.copy(fstyle = it) }
                                        )
                                    }
                                }

                                // Visibility / Opacity Slider
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        NexusText(
                                            text = "VISIBILITY",
                                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                            color = colors.textSecondary
                                        )
                                        NexusText(
                                            text = "${(config.opacity * 100).toInt()}%",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary
                                        )
                                    }
                                    NexusLiquidSlider(
                                        value = config.opacity,
                                        onValueChange = { config = config.copy(opacity = it) },
                                        valueRange = 0.1f..1.0f,
                                        accentColor = colors.primary,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Color Swatches
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NexusText(
                                        text = "COLOR",
                                        style = NexusTheme.typography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                        color = colors.textSecondary
                                    )
                                    val swatches = listOf(
                                        "#0A0A0A" to "Black",
                                        "#555555" to "Dark grey",
                                        "#8A8A8A" to "Grey",
                                        "#1D4ED8" to "Blue",
                                        "#DC2626" to "Red"
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        swatches.forEach { (hex, name) ->
                                            val isSelected = config.colorHex.equals(hex, ignoreCase = true)
                                            val swatchColor = Color(android.graphics.Color.parseColor(hex))
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.dp,
                                                        color = if (isSelected) colors.primary else colors.divider,
                                                        shape = CircleShape
                                                    )
                                                    .springBounceClick {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        config = config.copy(colorHex = hex)
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            // Divider
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.divider))

                            // Export Button
                            val totalPagesToNumber = (config.toPage - config.fromPage + 1).coerceAtLeast(0)
                            val exportLabel = if (isExporting) "Adding numbers…" else "Add numbers to $totalPagesToNumber page${if (totalPagesToNumber == 1) "" else "s"}"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (totalPagesToNumber > 0 && !isExporting) colors.primary else colors.surfaceVariant)
                                    .springBounceClick {
                                        if (totalPagesToNumber > 0 && !isExporting) {
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
                                            tint = if (totalPagesToNumber > 0) colors.onPrimary else colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    NexusText(
                                        text = exportLabel,
                                        style = NexusTheme.typography.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                        color = if (totalPagesToNumber > 0 && !isExporting) colors.onPrimary else colors.textSecondary
                                    )
                                }
                            }

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
                if (currentStep == PageNumberStep.EXPORT && exportResult != null) {
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
                                text = "Page Numbers Added!",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                ),
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )

                            NexusText(
                                text = "Successfully numbered ${res.numberedPagesCount} pages and saved to Downloads (${PageNumberEngine.formatBytes(res.fileSizeBytes)}).",
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
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Numbered PDF"))
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
                                        text = "Share Numbered PDF",
                                        style = NexusTheme.typography.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                                        color = colors.onPrimary
                                    )
                                }
                            }

                            // Number Another PDF Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surface)
                                    .border(1.5.dp, colors.divider, RoundedCornerShape(14.dp))
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentStep = PageNumberStep.UPLOAD
                                        docInfo = null
                                        exportResult = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "Number another PDF",
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

/**
 * Dropdown / Cycle option selector chip.
 */
@Composable
private fun <T> DropdownOptionPicker(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    val colors = NexusTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val currentTitle = options.firstOrNull { it.first == selected }?.second.orEmpty()

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            NexusText(
                text = currentTitle,
                style = NexusTheme.typography.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surface)
        ) {
            options.forEach { (value, title) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        NexusText(
                            text = title,
                            style = NexusTheme.typography.body.copy(
                                fontSize = 13.5.sp,
                                fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (value == selected) colors.primary else colors.textPrimary
                        )
                    },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

