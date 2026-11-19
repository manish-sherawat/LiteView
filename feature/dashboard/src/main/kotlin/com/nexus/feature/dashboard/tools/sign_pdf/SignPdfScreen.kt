@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.sign_pdf

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.feature.dashboard.tools.sign_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SignPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = NexusTheme.colors

    // ── Main State ──────────────────────────────────────────────────────────
    var docInfo by remember { mutableStateOf<SignPdfDocInfo?>(null) }
    var currentStep by remember { mutableStateOf(SignStep.UPLOAD) }
    var activeTab by remember { mutableStateOf(SignWorkspaceTab.DOCUMENT) }

    var currentPage by remember { mutableIntStateOf(1) }
    var zoomFactor by remember { mutableFloatStateOf(1.0f) }

    // Page rendering state
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageAspectRatio by remember { mutableFloatStateOf(0.707f) }
    var isLoadingPage by remember { mutableStateOf(false) }

    // Signatures and Placed Stamps
    val signatures = remember { mutableStateListOf<SignatureItem>() }
    var nextSigId by remember { mutableLongStateOf(1L) }

    val placedStamps = remember { mutableStateListOf<PlacedStamp>() }
    var nextStampId by remember { mutableLongStateOf(1L) }
    var selectedStampId by remember { mutableStateOf<Long?>(null) }

    // Dialog & Export State
    var isSignatureDialogOpen by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var savedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var exportResult by remember { mutableStateOf<SignPdfResult?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Floating toast
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

    // Function to reload/render current page
    fun loadPage(targetPage: Int) {
        val doc = docInfo ?: return
        val validPage = targetPage.coerceIn(1, doc.pageCount)
        currentPage = validPage
        selectedStampId = null
        isLoadingPage = true

        coroutineScope.launch {
            try {
                val (bmp, aspect) = SignPdfEngine.renderPdfPage(context, doc.uri, validPage - 1)
                pageBitmap = bmp
                pageAspectRatio = aspect
            } catch (e: Exception) {
                statusMessage = "Could not render page $validPage."
                isError = true
            } finally {
                isLoadingPage = false
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
                    val info = SignPdfEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    placedStamps.clear()
                    selectedStampId = null
                    currentStep = SignStep.SIGN
                    activeTab = SignWorkspaceTab.DOCUMENT
                    statusMessage = null
                    loadPage(1)
                } catch (e: Exception) {
                    statusMessage = "Couldn't open that PDF. It may be damaged or password-protected."
                    isError = true
                }
            }
        }
    }

    // Launcher for saving the signed PDF file safely via SAF
    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        val doc = docInfo
        if (destUri != null && doc != null) {
            isExporting = true
            statusMessage = null
            isError = false
            coroutineScope.launch {
                try {
                    val sigMap = signatures.associate { it.id to it.bitmap }
                    val count = context.contentResolver.openOutputStream(destUri)?.use { out ->
                        SignPdfEngine.embedSignatures(
                            context = context,
                            inputUri = doc.uri,
                            stamps = placedStamps.toList(),
                            signatures = sigMap,
                            outputStream = out
                        )
                    } ?: 0

                    savedPdfUri = destUri
                    exportResult = SignPdfResult(
                        outputUri = destUri,
                        outputFileName = "${doc.baseName}-signed.pdf",
                        stampsCount = count
                    )
                    currentStep = SignStep.EXPORT
                    showToast("Done! Signed PDF saved with $count signature${if (count == 1) "" else "s"}.")
                } catch (e: Exception) {
                    statusMessage = "Something went wrong while exporting: ${e.message}"
                    isError = true
                } finally {
                    isExporting = false
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            NexusTopBar(
                title = "Sign PDF",
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .clip(CircleShape)
                            .springBounceClick { onBack() }
                            .padding(NexusTheme.spacing.sm)
                            .size(24.dp),
                        colorFilter = ColorFilter.tint(colors.textPrimary)
                    )
                },
                actions = {
                    // Offline security pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.10f))
                            .border(0.8.dp, colors.primary.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lock),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            colorFilter = ColorFilter.tint(colors.primary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        NexusText(
                            text = "Offline",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.primary
                        )
                    }
                }
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
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // ── Stepper and Header ───────────────────────────────────────
                item {
                    SignStepIndicator(currentStep = currentStep)
                }

                // ── Step 1: Dropzone (when no document loaded) ───────────────
                if (currentStep == SignStep.UPLOAD || docInfo == null) {
                    item {
                        SignDropzone(
                            onPickFile = { pdfPickerLauncher.launch("application/pdf") },
                            errorMessage = if (isError) statusMessage else null
                        )
                    }
                } else if (currentStep == SignStep.EXPORT) {
                    // ── Step 3: Dedicated Export Success Step ─────────────────
                    item {
                        SignExportSuccessCard(
                            result = exportResult,
                            docInfo = docInfo,
                            onOpenPdf = {
                                val uri = savedPdfUri ?: return@SignExportSuccessCard
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open Signed PDF"))
                                } catch (_: Exception) {
                                    showToast("No PDF viewer app available to open file.")
                                }
                            },
                            onSharePdf = {
                                val uri = savedPdfUri ?: return@SignExportSuccessCard
                                try {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Signed PDF"))
                                } catch (_: Exception) {
                                    showToast("Unable to share file.")
                                }
                            },
                            onSignAnother = {
                                docInfo = null
                                placedStamps.clear()
                                signatures.clear()
                                selectedStampId = null
                                savedPdfUri = null
                                exportResult = null
                                currentStep = SignStep.UPLOAD
                                pageBitmap = null
                            }
                        )
                    }
                } else {
                    // ── Step 2: Workspace Card ───────────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(24.dp))
                                .clip(RoundedCornerShape(24.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.divider, RoundedCornerShape(24.dp))
                                .padding(24.dp)
                        ) {
                            // Card Title
                            NexusText(
                                text = "Sign PDF",
                                style = NexusTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.6).sp
                                ),
                                color = colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Top Actions (Add Signature & Export)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // "Add signature" ghost button
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surface)
                                        .border(1.2.dp, colors.divider, RoundedCornerShape(16.dp))
                                        .clickable { isSignatureDialogOpen = true }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                        tint = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    NexusText(
                                        text = "Add signature",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = colors.textPrimary
                                    )
                                }

                                // "Sign & Export" primary button
                                val canExport = placedStamps.isNotEmpty() && !isExporting
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (canExport) colors.primary else colors.surfaceVariant)
                                        .clickable(enabled = canExport) {
                                            val doc = docInfo ?: return@clickable
                                            savePdfLauncher.launch("${doc.baseName}-signed.pdf")
                                        }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isExporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = colors.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        NexusText(
                                            text = "Exporting…",
                                            style = NexusTheme.typography.buttonLabel.copy(
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = colors.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.FileDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(19.dp),
                                            tint = if (canExport) colors.onPrimary else colors.textSecondary.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        NexusText(
                                            text = "Sign & Save",
                                            style = NexusTheme.typography.buttonLabel.copy(
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = if (canExport) colors.onPrimary else colors.textSecondary.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Segmented Tabs: "Document" vs "Stamps (N)"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                    .border(1.dp, colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isDocTab = activeTab == SignWorkspaceTab.DOCUMENT
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isDocTab) colors.primary else Color.Transparent)
                                        .clickable { activeTab = SignWorkspaceTab.DOCUMENT },
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = "Document",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (isDocTab) colors.onPrimary else colors.textSecondary
                                    )
                                }

                                val isStampsTab = activeTab == SignWorkspaceTab.STAMPS
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isStampsTab) colors.primary else Color.Transparent)
                                        .clickable { activeTab = SignWorkspaceTab.STAMPS },
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = "Stamps (${placedStamps.size})",
                                        style = NexusTheme.typography.buttonLabel.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = if (isStampsTab) colors.onPrimary else colors.textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Tab Content
                            Crossfade(
                                targetState = activeTab,
                                animationSpec = tween(200),
                                label = "tabCrossfade"
                            ) { tab ->
                                when (tab) {
                                    SignWorkspaceTab.DOCUMENT -> {
                                        SignDocumentViewer(
                                            fileName = docInfo?.name ?: "",
                                            currentPage = currentPage,
                                            totalPages = docInfo?.pageCount ?: 1,
                                            zoomFactor = zoomFactor,
                                            pageBitmap = pageBitmap,
                                            pageAspectRatio = pageAspectRatio,
                                            isLoadingPage = isLoadingPage,
                                            placedStamps = placedStamps.toList(),
                                            signatureItems = signatures.associateBy { it.id },
                                            selectedStampId = selectedStampId,
                                            onSelectStamp = { selectedStampId = it },
                                            onStampMoved = { stampId, newX, newY ->
                                                val idx = placedStamps.indexOfFirst { it.id == stampId }
                                                if (idx != -1) {
                                                    val curr = placedStamps[idx]
                                                    placedStamps[idx] = curr.copy(x = newX, y = newY)
                                                }
                                            },
                                            onStampResized = { stampId, newWidthRatio ->
                                                val idx = placedStamps.indexOfFirst { it.id == stampId }
                                                if (idx != -1) {
                                                    val curr = placedStamps[idx]
                                                    placedStamps[idx] = curr.copy(widthRatio = newWidthRatio)
                                                }
                                            },
                                            onRemoveStamp = { stampId ->
                                                placedStamps.removeAll { it.id == stampId }
                                                if (selectedStampId == stampId) {
                                                    selectedStampId = null
                                                }
                                            },
                                            onPrevPage = {
                                                if (currentPage > 1) {
                                                    loadPage(currentPage - 1)
                                                }
                                            },
                                            onNextPage = {
                                                if (currentPage < (docInfo?.pageCount ?: 1)) {
                                                    loadPage(currentPage + 1)
                                                }
                                            },
                                            onZoomIn = { zoomFactor = (zoomFactor + 0.25f).coerceAtMost(2.5f) },
                                            onZoomOut = { zoomFactor = (zoomFactor - 0.25f).coerceAtLeast(0.5f) },
                                            onZoomReset = { zoomFactor = 1.0f },
                                            onChangeFile = { pdfPickerLauncher.launch("application/pdf") }
                                        )
                                    }

                                    SignWorkspaceTab.STAMPS -> {
                                        SignStampsPanel(
                                            placedStamps = placedStamps.toList(),
                                            signatureItems = signatures.associateBy { it.id },
                                            totalPages = docInfo?.pageCount ?: 1,
                                            onShowStamp = { stamp ->
                                                activeTab = SignWorkspaceTab.DOCUMENT
                                                if (currentPage != stamp.page) {
                                                    loadPage(stamp.page)
                                                }
                                                selectedStampId = stamp.id
                                            },
                                            onDuplicateToAllPages = { stamp ->
                                                val total = docInfo?.pageCount ?: 1
                                                var added = 0
                                                for (p in 1..total) {
                                                    if (p == stamp.page) continue
                                                    if (placedStamps.none { it.page == p && it.sigId == stamp.sigId }) {
                                                        placedStamps.add(
                                                            stamp.copy(
                                                                id = nextStampId++,
                                                                page = p
                                                            )
                                                        )
                                                        added++
                                                    }
                                                }
                                                if (added > 0) {
                                                    showToast("Added to $added more page${if (added == 1) "" else "s"}.")
                                                } else {
                                                    showToast("Already on every page.")
                                                }
                                            },
                                            onRemoveStamp = { stampId ->
                                                placedStamps.removeAll { it.id == stampId }
                                                if (selectedStampId == stampId) {
                                                    selectedStampId = null
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            if (!statusMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                NexusText(
                                    text = statusMessage ?: "",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (isError) colors.error else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ── Signature Dialog ─────────────────────────────────────────────
            SignatureDialog(
                isOpen = isSignatureDialogOpen,
                savedSignatures = signatures.toList(),
                onDismiss = { isSignatureDialogOpen = false },
                onUseExistingSignature = { sig ->
                    isSignatureDialogOpen = false
                    val defaultW = 0.28f
                    val newStamp = PlacedStamp(
                        id = nextStampId++,
                        sigId = sig.id,
                        page = currentPage,
                        x = 0.5f - defaultW / 2f,
                        y = 0.78f,
                        widthRatio = defaultW,
                        aspectRatio = sig.aspectRatio
                    )
                    placedStamps.add(newStamp)
                    selectedStampId = newStamp.id
                    activeTab = SignWorkspaceTab.DOCUMENT
                },
                onConfirmNewSignature = { trimmedBmp ->
                    isSignatureDialogOpen = false
                    val sig = SignatureItem(
                        id = nextSigId++,
                        bitmap = trimmedBmp,
                        aspectRatio = trimmedBmp.width.toFloat() / trimmedBmp.height.toFloat()
                    )
                    signatures.add(sig)

                    val defaultW = 0.28f
                    val newStamp = PlacedStamp(
                        id = nextStampId++,
                        sigId = sig.id,
                        page = currentPage,
                        x = 0.5f - defaultW / 2f,
                        y = 0.78f,
                        widthRatio = defaultW,
                        aspectRatio = sig.aspectRatio
                    )
                    placedStamps.add(newStamp)
                    selectedStampId = newStamp.id
                    activeTab = SignWorkspaceTab.DOCUMENT
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
                        .background(colors.textPrimary)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = colors.background
                    )
                    NexusText(
                        text = toastMessage ?: "",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.background
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Completion Screen Card for Step 3: EXPORT
 */
@Composable
private fun SignExportSuccessCard(
    result: SignPdfResult?,
    docInfo: SignPdfDocInfo?,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSignAnother: () -> Unit
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(24.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Success Icon Circle
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = colors.primary
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NexusText(
                text = "Document Signed Successfully!",
                style = NexusTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            NexusText(
                text = "Your signatures have been permanently embedded. The file is saved and ready to use.",
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        // Details pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, colors.divider.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusText(
                    text = "File Name",
                    style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                    color = colors.textSecondary
                )
                NexusText(
                    text = result?.outputFileName ?: "${docInfo?.name ?: "document"}",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.textPrimary,
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusText(
                    text = "Signatures Placed",
                    style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                    color = colors.textSecondary
                )
                NexusText(
                    text = "${result?.stampsCount ?: 1}",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.textPrimary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusText(
                    text = "Total Pages",
                    style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                    color = colors.textSecondary
                )
                NexusText(
                    text = "${docInfo?.pageCount ?: 1}",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.textPrimary
                )
            }
        }

        // Action Buttons: Open & Share
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Open Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary)
                    .clickable { onOpenPdf() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                NexusText(
                    text = "Open PDF",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.onPrimary
                )
            }

            // Share Button
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.2.dp, colors.divider, RoundedCornerShape(14.dp))
                    .clickable { onSharePdf() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                NexusText(
                    text = "Share",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.textPrimary
                )
            }
        }

        // Secondary Button: Sign Another Document
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSignAnother() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.textSecondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            NexusText(
                text = "Sign Another Document",
                style = NexusTheme.typography.buttonLabel.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.textSecondary
            )
        }
    }
}
