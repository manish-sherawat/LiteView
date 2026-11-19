@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.txt_to_pdf

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
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
import com.nexus.feature.dashboard.tools.txt_to_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Text to PDF conversion tool screen.
 * Supports file uploads (.txt, .md, .csv, code files, logs) or direct writing/pasting,
 * live word/char/page estimation, multiple font options, and 100% offline PDF rendering.
 */
@Composable
fun TxtToPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── Main State ──────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(TxtStep.UPLOAD) }
    var docInfo by remember { mutableStateOf(TxtDocInfo()) }
    var titleText by remember { mutableStateOf("") }
    var bodyText by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(TxtOptions()) }
    var stats by remember { mutableStateOf(TxtStats()) }

    var isExporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Live debounced calculation of words, chars, and page count
    LaunchedEffect(bodyText, titleText, options) {
        delay(200)
        stats = TextToPdfEngine.calculateStats(bodyText, titleText, options)
    }

    fun showToast(msg: String) {
        toastMessage = msg
        coroutineScope.launch {
            delay(3800)
            if (toastMessage == msg) {
                toastMessage = null
            }
        }
    }

    fun resetToUpload() {
        if (isExporting) return
        currentStep = TxtStep.UPLOAD
        docInfo = TxtDocInfo()
        titleText = ""
        bodyText = ""
        statusMessage = null
    }

    // Intercept back button when in format workspace
    BackHandler(enabled = currentStep != TxtStep.UPLOAD) {
        if (bodyText.isNotBlank()) {
            showDiscardDialog = true
        } else {
            resetToUpload()
        }
    }

    // ── PDF Save Launcher ───────────────────────────────────────────────────
    val saveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && bodyText.isNotBlank()) {
            isExporting = true
            statusMessage = null

            coroutineScope.launch {
                try {
                    val pages = context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        TextToPdfEngine.convertTextToPdf(
                            context = context,
                            title = titleText,
                            text = bodyText,
                            options = options,
                            outputStream = outStream,
                            onProgress = { _, _ -> }
                        )
                    } ?: 1

                    currentStep = TxtStep.CONVERT
                    showToast("Done! Saved $pages page${if (pages == 1) "" else "s"} as PDF.")
                    delay(500)
                } catch (e: Exception) {
                    statusMessage = "Something went wrong while creating the PDF: ${e.message}"
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // ── Text File Picker ────────────────────────────────────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            statusMessage = null
            coroutineScope.launch {
                try {
                    val (info, text) = TextToPdfEngine.readTextFromUri(context, uri)
                    docInfo = info
                    titleText = info.baseName
                    bodyText = text
                    currentStep = TxtStep.FORMAT
                    statusMessage = null
                } catch (e: Exception) {
                    statusMessage = e.message ?: "Couldn't read that file."
                }
            }
        }
    }

    fun openFilePicker() {
        filePickerLauncher.launch(
            arrayOf(
                "text/plain",
                "text/*",
                "application/json",
                "application/xml",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    fun triggerExport() {
        if (bodyText.isBlank()) return
        val defaultName = if (titleText.isNotBlank()) {
            titleText.trim().replace(Regex("[\\\\/:*?\"<>|]+"), "").take(60)
        } else {
            docInfo.baseName
        }
        saveDocLauncher.launch("${defaultName.ifBlank { "document" }}.pdf")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Navigation Bar ──────────────────────────────────────────
            NexusTopBar(
                title = "Text to PDF",
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep != TxtStep.UPLOAD) {
                            if (bodyText.isNotBlank()) {
                                showDiscardDialog = true
                            } else {
                                resetToUpload()
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
                    // Offline Security Pill Badge
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
                            imageVector = Icons.Rounded.Lock,
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

            // ── Scrollable Body Content ──────────────────────────────────────
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
                // Section 1: Step Indicator
                TxtStepIndicator(
                    currentStep = currentStep,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Section 2: Either Dropzone or Workspace Card
                if (currentStep == TxtStep.UPLOAD) {
                    TxtDropzone(
                        onPickFile = { openFilePicker() },
                        onTypeOrPaste = {
                            docInfo = TxtDocInfo(
                                fileName = "Typed text",
                                fileMeta = "Write or paste below",
                                baseName = "document"
                            )
                            currentStep = TxtStep.FORMAT
                        },
                        errorMessage = statusMessage
                    )
                } else {
                    TxtWorkspaceCard(
                        docInfo = docInfo,
                        titleText = titleText,
                        onTitleChange = { titleText = it },
                        bodyText = bodyText,
                        onBodyTextChange = { bodyText = it },
                        stats = stats,
                        options = options,
                        onOptionsChange = { options = it },
                        onBackToStart = {
                            if (bodyText.isNotBlank()) {
                                showDiscardDialog = true
                            } else {
                                resetToUpload()
                            }
                        },
                        onReplaceWithFile = { openFilePicker() },
                        onClearText = {
                            bodyText = ""
                            titleText = ""
                        },
                        onExport = { triggerExport() },
                        isExporting = isExporting,
                        errorMessage = statusMessage
                    )
                }
            }
        }

        // ── Discard Confirmation Dialog ──────────────────────────────────────
        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = {
                    NexusText(
                        text = "Go back and clear text?",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                },
                text = {
                    NexusText(
                        text = "Going back will discard your current text and format settings.",
                        style = NexusTheme.typography.body.copy(fontSize = 13.5.sp),
                        color = Color(0xFF707070)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            resetToUpload()
                        }
                    ) {
                        NexusText(
                            text = "Discard & Go Back",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        NexusText(
                            text = "Cancel",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = NexusTheme.colors.textPrimary
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = NexusTheme.colors.surface
            )
        }

        // ── Success Toast Banner ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0A0A))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                NexusText(
                    text = toastMessage ?: "",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }
        }
    }
}

