@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.delete_pages

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Delete PDF Pages Screen implementing the precision HTML specification.
 * Supports interactive thumbnail click-to-delete, page range selection,
 * quick selection filters, and device-local PDF cleaning.
 */
@Composable
fun DeletePagesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val colors = NexusTheme.colors

    // ─── State ───────────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(DeleteStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<DeleteDocInfo?>(null) }
    var curPage by remember { mutableIntStateOf(1) } // 1-indexed

    // Marked page numbers (1-based)
    val markedPages = remember { mutableStateListOf<Int>() }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Range input
    var rangeInput by remember { mutableStateOf("") }
    var rangeError by remember { mutableStateOf<String?>(null) }

    // Exporting
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportResult by remember { mutableStateOf<DeleteExportResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }

    // Dialogs
    var showChangeFileDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

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
    fun loadPageBitmap(doc: DeleteDocInfo, pageNum: Int) {
        if (!pageBitmaps.containsKey(pageNum)) {
            isLoadingPreview = true
            coroutineScope.launch {
                try {
                    val bmp = DeletePagesEngine.renderThumbnail(context, doc.uri, pageNum - 1, 600)
                    pageBitmaps[pageNum] = bmp
                } catch (_: Exception) {}
                finally {
                    isLoadingPreview = false
                }
            }
        }
    }

    // Toggle single page deletion
    fun togglePage(pageNum: Int) {
        val total = docInfo?.pageCount ?: 1
        if (pageNum in markedPages) {
            markedPages.remove(pageNum)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
            if (total == 1) {
                showToast("This PDF only has 1 page, so it cannot be deleted.")
                return
            }
            markedPages.add(pageNum)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    val info = DeletePagesEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    curPage = 1
                    markedPages.clear()
                    pageBitmaps.clear()
                    rangeInput = ""
                    rangeError = null
                    statusMessage = null
                    currentStep = DeleteStep.SELECT

                    // Preload first page bitmap
                    loadPageBitmap(info, 1)

                    // Preload neighboring thumbnails
                    for (p in 2..minOf(info.pageCount, 6)) {
                        loadPageBitmap(info, p)
                    }
                } catch (e: Exception) {
                    statusMessage = "Couldn’t open that PDF. It may be password-protected or damaged."
                    isStatusError = true
                }
            }
        }
    }

    // Export handler
    fun startExport() {
        val doc = docInfo ?: return
        val k = markedPages.size
        val rem = doc.pageCount - k
        if (k == 0 || rem < 1 || isExporting) return

        isExporting = true
        exportProgress = 0f
        statusMessage = null
        isStatusError = false

        coroutineScope.launch {
            try {
                val result = DeletePagesEngine.deletePages(
                    context = context,
                    docInfo = doc,
                    markedPages = markedPages.toSet(),
                    onProgress = { p -> exportProgress = p }
                )
                exportResult = result
                currentStep = DeleteStep.EXPORT
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showToast("Done! Deleted $k pages · Kept $rem pages (${DeletePagesEngine.formatBytes(result.fileSizeBytes)}).")
            } catch (e: Exception) {
                statusMessage = "Something went wrong while removing pages. The PDF may be damaged or restricted."
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

                    // Delete Logo Squircle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    NexusText(
                        text = "Delete Pages",
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
                    text = "Delete any page, effortlessly.",
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
                    text = "Remove unwanted pages from your PDF in seconds. Click pages to mark them for removal, or type page ranges. Everything runs on your device.",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp
                    ),
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Steps Indicator (1 Upload -> 2 Select -> 3 Export)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeleteStep.entries.forEachIndexed { idx, step ->
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

                        if (idx < DeleteStep.entries.size - 1) {
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
                if (currentStep == DeleteStep.UPLOAD) {
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
                            // Layered pages illustration with delete badge
                            Box(
                                modifier = Modifier
                                    .size(width = 130.dp, height = 90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background page
                                Box(
                                    modifier = Modifier
                                        .size(width = 66.dp, height = 80.dp)
                                        .offset(x = (-16).dp, y = (-4).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant.copy(alpha = 0.7f))
                                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                                )
                                // Foreground page with hatched lines
                                Box(
                                    modifier = Modifier
                                        .size(width = 68.dp, height = 84.dp)
                                        .offset(x = 8.dp, y = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface)
                                        .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colors.divider)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.65f)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colors.divider)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.75f)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colors.divider)
                                        )
                                    }
                                }

                                // Delete Badge with red trash / cross icon
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 6.dp, y = 6.dp)
                                        .clip(CircleShape)
                                        .background(colors.error)
                                        .shadow(4.dp, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            NexusText(
                                text = "Drop your PDF here",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary
                            )

                            NexusText(
                                text = "or browse files from your device",
                                style = NexusTheme.typography.body.copy(
                                    fontSize = 13.5.sp
                                ),
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.primary)
                                    .padding(horizontal = 22.dp, vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "Choose PDF",
                                    style = NexusTheme.typography.buttonLabel.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = colors.onPrimary
                                )
                            }

                            NexusText(
                                text = "PDF files only · No upload, no sign-up",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.sp
                                ),
                                color = colors.textSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        NexusText(
                            text = statusMessage ?: "",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isStatusError) colors.error else colors.primary
                        )
                    }
                }

                // ─── STEP 2: SELECT WORKSPACE ─────────────────────────────────
                if (currentStep == DeleteStep.SELECT && docInfo != null) {
                    val info = docInfo!!
                    val markedCount = markedPages.size
                    val remainingCount = info.pageCount - markedCount
                    val isCurrentMarked = curPage in markedPages

                    // 1. Doc Info Header Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    NexusText(
                                        text = info.name,
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    NexusText(
                                        text = "${info.pageCount} page${if (info.pageCount == 1) "" else "s"} · ${DeletePagesEngine.formatBytes(info.sizeBytes)}",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            // Change file button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceVariant)
                                    .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (markedPages.isNotEmpty()) {
                                            showChangeFileDialog = true
                                        } else {
                                            currentStep = DeleteStep.UPLOAD
                                            docInfo = null
                                            pageBitmaps.clear()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                NexusText(
                                    text = "Change file",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2. Interactive Preview Stage Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Top Stage Toolbar: Navigation
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Prev Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (curPage > 1) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.4f))
                                        .border(1.dp, colors.divider, CircleShape)
                                        .clickable(enabled = curPage > 1) {
                                            if (curPage > 1) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                curPage--
                                                loadPageBitmap(info, curPage)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Previous page",
                                        tint = if (curPage > 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Caption
                                NexusText(
                                    text = "Page $curPage of ${info.pageCount}",
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.textPrimary
                                )

                                // Next Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (curPage < info.pageCount) colors.surfaceVariant else colors.surfaceVariant.copy(alpha = 0.4f))
                                        .border(1.dp, colors.divider, CircleShape)
                                        .clickable(enabled = curPage < info.pageCount) {
                                            if (curPage < info.pageCount) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                curPage++
                                                loadPageBitmap(info, curPage)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "Next page",
                                        tint = if (curPage < info.pageCount) colors.textPrimary else colors.textSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Interactive Canvas Preview Container
                            val curBmp = pageBitmaps[curPage]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                    .border(
                                        width = if (isCurrentMarked) 2.dp else 1.dp,
                                        color = if (isCurrentMarked) colors.error else colors.divider,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        togglePage(curPage)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (curBmp != null) {
                                    Image(
                                        bitmap = curBmp.asImageBitmap(),
                                        contentDescription = "Page $curPage",
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(12.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .shadow(if (isCurrentMarked) 2.dp else 6.dp),
                                        contentScale = ContentScale.Fit,
                                        colorFilter = if (isCurrentMarked) {
                                            // Dimmed and desaturated when marked for deletion
                                            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.2f) })
                                        } else null
                                    )

                                    // Marked for Deletion Overlay Badge
                                    if (isCurrentMarked) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(Color(0x55000000)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.error)
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    NexusText(
                                                        text = "Marked for deletion",
                                                        style = NexusTheme.typography.caption.copy(
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = colors.primary,
                                            strokeWidth = 2.5.dp
                                        )
                                        NexusText(
                                            text = "Rendering page $curPage…",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Stage Actions: Mark / Restore & Invert
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Toggle Mark Button
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isCurrentMarked) colors.error.copy(alpha = 0.12f) else colors.surfaceVariant)
                                        .border(
                                            width = 1.dp,
                                            color = if (isCurrentMarked) colors.error else colors.divider,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .springBounceClick {
                                            togglePage(curPage)
                                        }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrentMarked) Icons.Outlined.Refresh else Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = if (isCurrentMarked) colors.error else colors.textPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        NexusText(
                                            text = if (isCurrentMarked) "Restore this page" else "Mark page for deletion",
                                            style = NexusTheme.typography.buttonLabel.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = if (isCurrentMarked) colors.error else colors.textPrimary
                                        )
                                    }
                                }

                                // Invert Selection Button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceVariant)
                                        .border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                                        .springBounceClick {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val currentSet = markedPages.toSet()
                                            markedPages.clear()
                                            for (p in 1..info.pageCount) {
                                                if (p !in currentSet) {
                                                    markedPages.add(p)
                                                }
                                            }
                                            if (markedPages.size == info.pageCount) {
                                                showToast("Inverted selection marks all pages. Unmark at least 1 page before exporting.")
                                            } else {
                                                showToast("Inverted selection: ${markedPages.size} page${if (markedPages.size == 1) "" else "s"} marked.")
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = "Invert selection",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = colors.textPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Readout Summary
                            val readoutText = when {
                                markedCount == 0 -> "Keeping all ${info.pageCount} pages · Click page to mark for deletion"
                                remainingCount == 0 -> "All ${info.pageCount} pages marked · Unmark at least 1 page to export"
                                else -> "$markedCount page${if (markedCount == 1) "" else "s"} marked for deletion · $remainingCount page${if (remainingCount == 1) "" else "s"} will be kept"
                            }
                            val readoutColor = when {
                                remainingCount == 0 -> colors.error
                                markedCount > 0 -> colors.primary
                                else -> colors.textSecondary
                            }

                            NexusText(
                                text = readoutText,
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = readoutColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Quick Selection Chips Row
                    NexusText(
                        text = "Quick selection",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            QuickFilterChip(
                                label = "Odd pages",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    markedPages.clear()
                                    for (p in 1..info.pageCount step 2) markedPages.add(p)
                                    showToast("Marked ${markedPages.size} odd pages for deletion.")
                                }
                            )
                        }
                        item {
                            QuickFilterChip(
                                label = "Even pages",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    markedPages.clear()
                                    for (p in 2..info.pageCount step 2) markedPages.add(p)
                                    showToast("Marked ${markedPages.size} even pages for deletion.")
                                }
                            )
                        }
                        item {
                            QuickFilterChip(
                                label = "First page",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    markedPages.clear()
                                    markedPages.add(1)
                                    curPage = 1
                                    loadPageBitmap(info, 1)
                                    showToast("Marked first page for deletion.")
                                }
                            )
                        }
                        item {
                            QuickFilterChip(
                                label = "Last page",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    markedPages.clear()
                                    markedPages.add(info.pageCount)
                                    curPage = info.pageCount
                                    loadPageBitmap(info, info.pageCount)
                                    showToast("Marked last page for deletion.")
                                }
                            )
                        }
                        item {
                            QuickFilterChip(
                                label = "Clear selection",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    markedPages.clear()
                                    showToast("Selection cleared.")
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. Select by Range Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.divider, RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NexusText(
                                text = "Select by page range",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary
                            )

                            // Text Field
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.7f))
                                    .border(
                                        width = 1.dp,
                                        color = if (rangeError != null) colors.error else colors.divider,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                if (rangeInput.isEmpty()) {
                                    NexusText(
                                        text = "e.g. 2-5, 8, 11-13",
                                        style = NexusTheme.typography.body.copy(fontSize = 13.5.sp),
                                        color = colors.textSecondary.copy(alpha = 0.5f)
                                    )
                                }
                                BasicTextField(
                                    value = rangeInput,
                                    onValueChange = {
                                        rangeInput = it
                                        rangeError = null
                                    },
                                    textStyle = TextStyle(
                                        color = colors.textPrimary,
                                        fontSize = 13.5.sp,
                                        fontFamily = NexusTheme.typography.body.fontFamily
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            // Error or Help message
                            NexusText(
                                text = rangeError
                                    ?: "Enter single pages or ranges separated by commas. A PDF must keep at least 1 page.",
                                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
                                color = if (rangeError != null) colors.error else colors.textSecondary.copy(alpha = 0.7f)
                            )

                            // Buttons: Mark pages / Keep only these
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Mark pages
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceVariant)
                                        .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                                        .springBounceClick {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val parsed = DeletePagesEngine.parseRanges(rangeInput, info.pageCount)
                                            parsed.onSuccess { list ->
                                                val testSet = markedPages.toSet() + list
                                                if (testSet.size >= info.pageCount) {
                                                    rangeError = "You cannot delete every page in the PDF. Leave at least 1 page unmarked."
                                                } else {
                                                    for (p in list) {
                                                        if (p !in markedPages) markedPages.add(p)
                                                    }
                                                    rangeInput = ""
                                                    rangeError = null
                                                    showToast("Marked ${list.size} page${if (list.size == 1) "" else "s"} for deletion.")
                                                }
                                            }.onFailure { err ->
                                                rangeError = err.message ?: "Invalid range input."
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = "Mark pages",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = colors.textPrimary
                                    )
                                }

                                // Keep only these
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceVariant)
                                        .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                                        .springBounceClick {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val parsed = DeletePagesEngine.parseRanges(rangeInput, info.pageCount)
                                            parsed.onSuccess { keepList ->
                                                val keepSet = keepList.toSet()
                                                markedPages.clear()
                                                for (p in 1..info.pageCount) {
                                                    if (p !in keepSet) {
                                                        markedPages.add(p)
                                                    }
                                                }
                                                rangeInput = ""
                                                rangeError = null
                                                showToast("Keeping ${keepSet.size} page${if (keepSet.size == 1) "" else "s"} and marking ${markedPages.size} for deletion.")
                                            }.onFailure { err ->
                                                rangeError = err.message ?: "Invalid range input."
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = "Keep only these",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 5. Thumbnails Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        NexusText(
                            text = if (markedCount > 0) "Pages · $markedCount marked for deletion" else "Pages",
                            style = NexusTheme.typography.title.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.textPrimary
                        )

                        TextButton(
                            onClick = {
                                if (markedPages.isNotEmpty()) {
                                    showResetAllDialog = true
                                }
                            },
                            enabled = markedPages.isNotEmpty()
                        ) {
                            NexusText(
                                text = "Reset all",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (markedPages.isNotEmpty()) colors.error else colors.textSecondary.copy(alpha = 0.4f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filmstrip LazyRow
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items((1..info.pageCount).toList()) { p ->
                            // Load thumbnail if needed
                            LaunchedEffect(p) {
                                loadPageBitmap(info, p)
                            }

                            val isPSelected = p == curPage
                            val isPMarked = p in markedPages
                            val pBmp = pageBitmaps[p]

                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surface)
                                    .border(
                                        width = if (isPSelected) 2.dp else if (isPMarked) 1.5.dp else 1.dp,
                                        color = if (isPSelected) colors.primary else if (isPMarked) colors.error else colors.divider,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        curPage = p
                                        loadPageBitmap(info, p)
                                    }
                                    .padding(6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Thumbnail preview container
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(108.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surfaceVariant.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (pBmp != null) {
                                            Image(
                                                bitmap = pBmp.asImageBitmap(),
                                                contentDescription = "Page $p",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit,
                                                colorFilter = if (isPMarked) {
                                                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.2f) })
                                                } else null
                                            )
                                        } else {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = colors.primary,
                                                strokeWidth = 2.dp
                                            )
                                        }

                                        // DELETE badge overlay
                                        if (isPMarked) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(Color(0x44000000)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(colors.error)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    NexusText(
                                                        text = "DELETE",
                                                        style = NexusTheme.typography.caption.copy(
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        ),
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Bottom row with Page number & Mini Delete button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        NexusText(
                                            text = "Page $p",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 11.sp,
                                                fontWeight = if (isPSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isPMarked) colors.error else colors.textPrimary
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(if (isPMarked) colors.error.copy(alpha = 0.15f) else colors.surfaceVariant)
                                                .clickable {
                                                    togglePage(p)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPMarked) Icons.Outlined.Refresh else Icons.Default.Delete,
                                                contentDescription = if (isPMarked) "Restore page" else "Delete page",
                                                tint = if (isPMarked) colors.error else colors.textSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // 6. Primary Export CTA Button
                    val canExport = markedCount > 0 && remainingCount >= 1 && !isExporting
                    val exportLabel = when {
                        isExporting -> "Removing pages…"
                        markedCount == 0 -> "Select pages to delete"
                        remainingCount == 0 -> "Cannot delete all pages (keep at least 1)"
                        else -> "Delete $markedCount page${if (markedCount == 1) "" else "s"} and download"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (canExport) colors.primary else colors.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = if (canExport) colors.primary else colors.divider,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .springBounceClick {
                                if (canExport) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    startExport()
                                }
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            }
                            NexusText(
                                text = exportLabel,
                                style = NexusTheme.typography.buttonLabel.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (canExport) colors.onPrimary else colors.textSecondary.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    NexusText(
                        text = "Deleted pages are completely removed from the exported PDF file, reducing its size and page count.",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        ),
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        NexusText(
                            text = statusMessage ?: "",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isStatusError) colors.error else colors.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ─── STEP 3: EXPORT SUCCESS ───────────────────────────────────
                if (currentStep == DeleteStep.EXPORT && exportResult != null) {
                    val res = exportResult!!

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surface)
                            .border(1.5.dp, colors.divider, RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            NexusText(
                                text = "PDF Cleaned Successfully!",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center
                            )

                            // Result Details Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        NexusText(
                                            text = "File name",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                                            color = colors.textSecondary
                                        )
                                        NexusText(
                                            text = res.outputFile.name,
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        NexusText(
                                            text = "Pages kept",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                                            color = colors.textSecondary
                                        )
                                        NexusText(
                                            text = "${res.remainingPagesCount} pages (Deleted ${res.deletedPagesCount})",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = colors.textPrimary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        NexusText(
                                            text = "File size",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                                            color = colors.textSecondary
                                        )
                                        NexusText(
                                            text = DeletePagesEngine.formatBytes(res.fileSizeBytes),
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = colors.textPrimary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        NexusText(
                                            text = "Location",
                                            style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                                            color = colors.textSecondary
                                        )
                                        NexusText(
                                            text = "Downloads folder",
                                            style = NexusTheme.typography.caption.copy(
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = colors.primary
                                        )
                                    }
                                }
                            }

                            // Open File Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.primary)
                                    .springBounceClick {
                                        try {
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                res.outputFile
                                            )
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(fileUri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(viewIntent, "Open Cleaned PDF"))
                                        } catch (e: Exception) {
                                            showToast("Unable to open PDF viewer.")
                                        }
                                    }
                                    .padding(vertical = 14.dp),
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
                                        text = "Open File",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.onPrimary
                                    )
                                }
                            }

                            // Share File Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceVariant)
                                    .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                                    .springBounceClick {
                                        try {
                                            val fileUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                res.outputFile
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Cleaned PDF"))
                                        } catch (e: Exception) {
                                            showToast("Unable to share file.")
                                        }
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    NexusText(
                                        text = "Share File",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = colors.textPrimary
                                    )
                                }
                            }

                            // Process Another PDF
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Transparent)
                                    .springBounceClick {
                                        currentStep = DeleteStep.UPLOAD
                                        docInfo = null
                                        markedPages.clear()
                                        pageBitmaps.clear()
                                        exportResult = null
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "Process Another PDF",
                                    style = NexusTheme.typography.buttonLabel.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = colors.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Floating Toast Notification ──────────────────────────────────────
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 95.dp,
                    start = 24.dp,
                    end = 24.dp
                )
                .zIndex(20f)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp))
            ) {
                NexusText(
                    text = toastMessage ?: "",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.onPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ─── Confirm Dialog: Change File ──────────────────────────────────────
        if (showChangeFileDialog) {
            AlertDialog(
                onDismissRequest = { showChangeFileDialog = false },
                title = {
                    NexusText(
                        text = "Change file?",
                        style = NexusTheme.typography.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                },
                text = {
                    NexusText(
                        text = "Going back will discard your current page selection.",
                        style = NexusTheme.typography.body.copy(fontSize = 14.sp),
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showChangeFileDialog = false
                            currentStep = DeleteStep.UPLOAD
                            docInfo = null
                            markedPages.clear()
                            pageBitmaps.clear()
                        }
                    ) {
                        NexusText(
                            text = "Change file",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeFileDialog = false }) {
                        NexusText(
                            text = "Cancel",
                            style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.sp),
                            color = colors.textPrimary
                        )
                    }
                },
                containerColor = colors.surface
            )
        }

        // ─── Confirm Dialog: Reset All Marked Pages ───────────────────────────
        if (showResetAllDialog) {
            AlertDialog(
                onDismissRequest = { showResetAllDialog = false },
                title = {
                    NexusText(
                        text = "Clear all marked pages?",
                        style = NexusTheme.typography.title.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                },
                text = {
                    NexusText(
                        text = "Restore all ${markedPages.size} marked page${if (markedPages.size == 1) "" else "s"} so no pages are deleted?",
                        style = NexusTheme.typography.body.copy(fontSize = 14.sp),
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetAllDialog = false
                            markedPages.clear()
                            showToast("All pages restored.")
                        }
                    ) {
                        NexusText(
                            text = "Reset all",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetAllDialog = false }) {
                        NexusText(
                            text = "Cancel",
                            style = NexusTheme.typography.buttonLabel.copy(fontSize = 14.sp),
                            color = colors.textPrimary
                        )
                    }
                },
                containerColor = colors.surface
            )
        }
    }
}

@Composable
private fun QuickFilterChip(
    label: String,
    onClick: () -> Unit
) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
            .springBounceClick { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textPrimary
        )
    }
}

