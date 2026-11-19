@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.protect_pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.nexus.feature.dashboard.tools.protect_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen for encrypting and password-protecting PDF documents.
 * 100% offline, on-device AES-128 encryption with customizable permissions.
 */
@Composable
fun ProtectPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ── Main State ──────────────────────────────────────────────────────────
    var currentStep by remember { mutableStateOf(ProtectStep.UPLOAD) }
    var docInfo by remember { mutableStateOf<ProtectPdfDocInfo?>(null) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }

    var passwordText by remember { mutableStateOf("") }
    var confirmPasswordText by remember { mutableStateOf("") }
    var permissions by remember { mutableStateOf(ProtectPdfPermissions()) }

    var isLoadingPdf by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val strength by remember(passwordText) {
        derivedStateOf { ProtectPdfEngine.scorePassword(passwordText) }
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

    fun resetState() {
        if (isExporting || isLoadingPdf) return
        currentStep = ProtectStep.UPLOAD
        docInfo = null
        thumbnail = null
        passwordText = ""
        confirmPasswordText = ""
        permissions = ProtectPdfPermissions()
        statusMessage = null
    }

    // Intercept back button when in format workspace
    BackHandler(enabled = currentStep != ProtectStep.UPLOAD) {
        if (passwordText.isNotBlank()) {
            showDiscardDialog = true
        } else {
            resetState()
        }
    }

    // ── PDF Save Launcher ───────────────────────────────────────────────────
    val saveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && docInfo != null && passwordText.isNotBlank()) {
            isExporting = true
            statusMessage = null

            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                        ProtectPdfEngine.protectPdfAndSave(
                            context = context,
                            inputUri = docInfo!!.uri,
                            userPassword = passwordText,
                            permissions = permissions,
                            outputStream = outStream
                        )
                    }

                    currentStep = ProtectStep.EXPORT
                    showToast("Done! Protected PDF saved.")
                    delay(500)
                } catch (e: Exception) {
                    statusMessage = "Something went wrong while protecting the PDF: ${e.message}"
                } finally {
                    isExporting = false
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
                    val (info, thumb) = ProtectPdfEngine.readPdfDocInfo(context, uri)
                    docInfo = info
                    thumbnail = thumb
                    passwordText = ""
                    confirmPasswordText = ""
                    permissions = ProtectPdfPermissions()
                    currentStep = ProtectStep.PROTECT
                    statusMessage = null
                } catch (e: Exception) {
                    statusMessage = e.message ?: "Couldn't open that PDF."
                } finally {
                    isLoadingPdf = false
                }
            }
        }
    }

    fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("application/pdf", "*/*"))
    }

    fun triggerExport() {
        val info = docInfo ?: return
        if (passwordText.length < 4) {
            statusMessage = "Password must be at least 4 characters."
            return
        }
        if (passwordText != confirmPasswordText) {
            statusMessage = "The two passwords don't match."
            return
        }
        statusMessage = null
        saveDocLauncher.launch("${info.baseName}-protected.pdf")
    }

    fun copyPasswordToClipboard() {
        if (passwordText.isBlank()) {
            showToast("Type or generate a password first.")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("PDF Password", passwordText)
        clipboard?.setPrimaryClip(clip)
        showToast("Password copied to clipboard.")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Navigation Bar ──────────────────────────────────────────
            NexusTopBar(
                title = "Protect PDF",
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep != ProtectStep.UPLOAD) {
                            if (passwordText.isNotBlank()) {
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
                ProtectStepIndicator(
                    currentStep = currentStep,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Section 2: Upload Dropzone or Workspace Card
                if (currentStep == ProtectStep.UPLOAD) {
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
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0A0A0A),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                NexusText(
                                    text = "Opening PDF…",
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )
                            }
                        }
                    } else {
                        ProtectDropzone(
                            onPickFile = { openFilePicker() },
                            errorMessage = statusMessage
                        )
                    }
                } else if (docInfo != null) {
                    ProtectWorkspaceCard(
                        docInfo = docInfo!!,
                        thumbnail = thumbnail,
                        passwordText = passwordText,
                        onPasswordChange = {
                            passwordText = it
                            statusMessage = null
                        },
                        confirmPasswordText = confirmPasswordText,
                        onConfirmPasswordChange = {
                            confirmPasswordText = it
                            statusMessage = null
                        },
                        strength = strength,
                        permissions = permissions,
                        onPermissionsChange = { permissions = it },
                        onGeneratePassword = {
                            val gen = ProtectPdfEngine.generateStrongPassword(16)
                            passwordText = gen
                            confirmPasswordText = gen
                            statusMessage = null
                            showToast("Strong password generated. Copy it before you continue.")
                        },
                        onCopyPassword = { copyPasswordToClipboard() },
                        onBackToStart = {
                            if (passwordText.isNotBlank()) {
                                showDiscardDialog = true
                            } else {
                                resetState()
                            }
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
                        text = "Discard password settings?",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                },
                text = {
                    NexusText(
                        text = "Going back will discard your password and permission settings.",
                        style = NexusTheme.typography.body.copy(fontSize = 13.5.sp),
                        color = Color(0xFF707070)
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

