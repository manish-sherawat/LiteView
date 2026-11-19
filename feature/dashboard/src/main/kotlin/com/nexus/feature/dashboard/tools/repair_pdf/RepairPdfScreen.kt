@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.repair_pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.feature.dashboard.tools.repair_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Screen for diagnosing, rebuilding, and repairing corrupted or unreadable PDF files.
 * 100% offline and on-device processing.
 */
@Composable
fun RepairPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── Main State ──────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(RepairStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<RepairPdfDocInfo?>(null) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var cleanedBytes by remember { mutableStateOf<ByteArray?>(null) }

    var options by remember { mutableStateOf(RepairPdfOptions()) }
    var isRepairing by remember { mutableStateOf(false) }
    var repairProgress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<RepairPdfResult?>(null) }
    var repairedPdfBytes by remember { mutableStateOf<ByteArray?>(null) }

    var isLoadingPdf by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun showToast(msg: String) {
        toastMessage = msg
        coroutineScope.launch {
            delay(3800)
            if (toastMessage == msg) {
                toastMessage = null
            }
        }
    }

    fun resetState() {
        if (isRepairing || isLoadingPdf) return
        currentStep = RepairStep.UPLOAD
        docInfo = null
        thumbnail = null
        cleanedBytes = null
        options = RepairPdfOptions()
        isRepairing = false
        repairProgress = 0f
        statusMessage = null
        result = null
        repairedPdfBytes = null
    }

    // Intercept back button when inside workspace
    BackHandler(enabled = currentStep != RepairStep.UPLOAD) {
        if (result != null || isRepairing) {
            showDiscardDialog = true
        } else {
            resetState()
        }
    }

    // ── Save Document Launcher ──────────────────────────────────────────────
    val saveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        if (destUri != null && repairedPdfBytes != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        outStream.write(repairedPdfBytes!!)
                    }
                    showToast("Download started.")
                } catch (e: Exception) {
                    statusMessage = "Could not save file: ${e.message}"
                }
            }
        }
    }

    // ── PDF File Picker ─────────────────────────────────────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoadingPdf = true
            statusMessage = null

            coroutineScope.launch {
                try {
                    val (info, thumb, bytes) = RepairPdfEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    thumbnail = thumb
                    cleanedBytes = bytes
                    options = RepairPdfOptions()
                    result = null
                    repairedPdfBytes = null
                    repairProgress = 0f
                    currentStep = RepairStep.REPAIR
                    statusMessage = null
                } catch (e: Exception) {
                    statusMessage = e.message ?: "Couldn't read that file. Try another PDF."
                } finally {
                    isLoadingPdf = false
                }
            }
        }
    }

    fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("application/pdf", "*/*"))
    }

    fun executeRepair() {
        val bytes = cleanedBytes ?: return
        isRepairing = true
        statusMessage = null
        repairProgress = 0f

        coroutineScope.launch {
            try {
                val bos = ByteArrayOutputStream()
                val repairResult = RepairPdfEngine.repairPdf(
                    context = context,
                    cleanedBytes = bytes,
                    options = options,
                    onProgress = { repairProgress = it },
                    outputStream = bos
                )
                repairedPdfBytes = bos.toByteArray()
                result = repairResult
                currentStep = RepairStep.EXPORT
                showToast(
                    if (repairResult.unrecoverable > 0) {
                        "Repaired with some missing pages."
                    } else {
                        "Your PDF has been repaired."
                    }
                )
            } catch (e: Exception) {
                statusMessage = e.message ?: "Sorry, this file is too damaged to recover."
            } finally {
                isRepairing = false
            }
        }
    }

    // ── Main UI Scaffolding ─────────────────────────────────────────────────
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NexusTheme.colors.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top Navigation Bar ──────────────────────────────────────
                NexusTopBar(
                    title = "Repair PDF",
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep != RepairStep.UPLOAD) {
                                if (result != null || isRepairing) {
                                    showDiscardDialog = true
                                } else {
                                    resetState()
                                }
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = NexusTheme.colors.textPrimary
                            )
                        }
                    },
                    actions = {
                        // Offline Diagnostic Pill Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0A0A0A).copy(alpha = 0.08f))
                                .border(0.8.dp, Color(0xFF0A0A0A).copy(alpha = 0.25f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Build,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = NexusTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            NexusText(
                                text = "Offline",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = NexusTheme.colors.textPrimary
                            )
                        }
                    }
                )

                // ── Scrollable Body Content ──────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp
                        )
                ) {
                    // Step Indicator
                    RepairStepIndicator(
                        currentStep = currentStep,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Upload Dropzone or Workspace Card
                    if (currentStep == RepairStep.UPLOAD) {
                        if (isLoadingPdf) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(NexusTheme.colors.surface)
                                    .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(24.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF0A0A0A),
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                    NexusText(
                                        text = "Inspecting PDF document…",
                                        style = NexusTheme.typography.body.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                            }
                        } else {
                            RepairDropzone(
                                onPickFile = { openFilePicker() },
                                errorMessage = statusMessage
                            )
                        }
                    } else if (docInfo != null) {
                        RepairWorkspaceCard(
                            docInfo = docInfo!!,
                            thumbnail = thumbnail,
                            options = options,
                            onOptionsChange = { options = it },
                            isRepairing = isRepairing,
                            repairProgress = repairProgress,
                            statusMessage = statusMessage,
                            result = result,
                            onRepairClick = { executeRepair() },
                            onChangeFile = { resetState() },
                            onDownloadClick = {
                                val suggestedName = "${docInfo!!.baseName}-repaired.pdf"
                                saveDocLauncher.launch(suggestedName)
                            }
                        )
                    }
                }
            }

            // ── Floating Toast Pill ──────────────────────────────────────────
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                        start = 20.dp,
                        end = 20.dp
                    )
            ) {
                Box(
                    modifier = Modifier
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0A0A0A))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        NexusText(
                            text = toastMessage.orEmpty(),
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // ── Discard Confirmation Dialog ─────────────────────────────────────────
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                NexusText(
                    text = "Discard repaired session?",
                    style = NexusTheme.typography.title.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            },
            text = {
                NexusText(
                    text = "Leaving now will discard the current repair results. Make sure to download your repaired PDF first if needed.",
                    style = NexusTheme.typography.body.copy(
                        color = Color(0xFF707070)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        resetState()
                    }
                ) {
                    NexusText(
                        text = "Discard",
                        style = NexusTheme.typography.buttonLabel.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE5484D)
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false }
                ) {
                    NexusText(
                        text = "Stay",
                        style = NexusTheme.typography.buttonLabel.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = NexusTheme.colors.surface
        )
    }
}

