@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexus.feature.dashboard.tools.merge_pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.merge_pdf.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MergePdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<MergePdfItem>>(emptyList()) }
    var currentStep by remember { mutableStateOf(MergeStep.UPLOAD) }
    var outputFileName by remember { mutableStateOf("merged") }

    var isBusy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var mergeResult by remember { mutableStateOf<MergeResult?>(null) }

    // Floating toast state
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

    var nextId by remember { mutableLongStateOf(1L) }

    // Coroutine helper to read and append files
    fun appendUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        statusMessage = "Reading files…"
        isError = false
        coroutineScope.launch {
            val newItems = mutableListOf<MergePdfItem>()
            for (uri in uris) {
                try {
                    val item = MergePdfEngine.readPdfItem(context, uri, nextId++)
                    newItems.add(item)
                } catch (_: Exception) {
                    // Skip damaged file
                }
            }

            if (newItems.isNotEmpty()) {
                items = items + newItems
                currentStep = MergeStep.ORDER
                statusMessage = null
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else {
                statusMessage = "Those files couldn’t be read."
                isError = true
            }
        }
    }

    // Multiple Document Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        appendUris(uris)
    }

    // Save/Download Merged PDF Launcher
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        val res = mergeResult
        if (destUri != null && res != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        res.file.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    currentStep = MergeStep.SAVE
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showToast("Done! ${res.fileCount} files merged into ${res.pageCount} pages (${MergePdfEngine.formatFileSize(res.sizeBytes)}).")
                } catch (e: Exception) {
                    statusMessage = "Failed to save file: ${e.message}"
                    isError = true
                }
            }
        }
    }

    // Execute Merge Operation
    fun executeMerge() {
        if (items.size < 2 || isBusy) return
        isBusy = true
        progress = 0f
        statusMessage = null
        isError = false

        coroutineScope.launch {
            try {
                val tempOutputFile = File(
                    context.cacheDir,
                    "merged_${System.currentTimeMillis()}.pdf"
                )

                val result = MergePdfEngine.mergePdfs(
                    context = context,
                    items = items,
                    outputFile = tempOutputFile,
                    outputName = outputFileName.ifBlank { "merged" },
                    onProgress = { cur, total ->
                        progress = if (total > 0) cur.toFloat() / total.toFloat() else 0f
                    }
                )

                mergeResult = result
                isBusy = false

                // Prompt user to select destination
                val cleanName = outputFileName.trim().replace(Regex("[\\\\/:*?\"<>|]+"), "").ifBlank { "merged" }
                saveFileLauncher.launch("${cleanName}.pdf")
            } catch (e: Exception) {
                isBusy = false
                statusMessage = "Something went wrong while merging. One of the files may be damaged."
                isError = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(NexusTheme.colors.textPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_merge_pdf),
                                contentDescription = null,
                                tint = NexusTheme.colors.background,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = "Merge PDF",
                            style = NexusTheme.typography.title.copy(
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NexusTheme.colors.surface
                )
            )
        },
        containerColor = NexusTheme.colors.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
            ) {
                // ── Hero Section ──────────────────────────────────────────────
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            NexusText(
                                text = "Bring files together",
                                style = NexusTheme.typography.h1.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-1).sp
                                ),
                                color = NexusTheme.colors.textPrimary
                            )
                            NexusText(
                                text = ".",
                                style = NexusTheme.typography.h1.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = NexusTheme.colors.textSecondary.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        NexusText(
                            text = "Combine multiple PDFs into one. Reorder them, then save. Everything runs on your device.",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.5.sp,
                                color = NexusTheme.colors.textSecondary,
                                lineHeight = 20.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Progress Steps
                        MergeStepIndicator(currentStep = currentStep)
                    }
                }

                // ── Step 1: Dropzone (When no document is loaded) ──────────────
                if (items.isEmpty()) {
                    item {
                        MergeDropzone(
                            onPickFiles = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                            errorMessage = statusMessage
                        )
                    }
                } else {
                    // ── Step 2 & 3: Workspace Grid ────────────────────────────
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Left Column: Workspace Card
                            MergeWorkspaceCard(
                                items = items,
                                isBusy = isBusy,
                                progress = progress,
                                statusMessage = statusMessage,
                                isError = isError,
                                onAddFiles = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                                onClearAll = {
                                    items = emptyList()
                                    mergeResult = null
                                    currentStep = MergeStep.UPLOAD
                                    statusMessage = null
                                    showToast("Cleared all files")
                                },
                                onMoveUp = { idx ->
                                    if (idx > 0) {
                                        val mutable = items.toMutableList()
                                        val temp = mutable[idx]
                                        mutable[idx] = mutable[idx - 1]
                                        mutable[idx - 1] = temp
                                        items = mutable
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onMoveDown = { idx ->
                                    if (idx < items.lastIndex) {
                                        val mutable = items.toMutableList()
                                        val temp = mutable[idx]
                                        mutable[idx] = mutable[idx + 1]
                                        mutable[idx + 1] = temp
                                        items = mutable
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onRemove = { idx ->
                                    val mutable = items.toMutableList()
                                    mutable.removeAt(idx)
                                    items = mutable
                                    if (items.isEmpty()) {
                                        currentStep = MergeStep.UPLOAD
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onSaveCombined = {
                                    executeMerge()
                                }
                            )

                            // Right Column: Summary Card
                            MergeSummaryCard(
                                items = items,
                                outputFileName = outputFileName,
                                onOutputFileNameChange = { outputFileName = it },
                                onSortAZ = {
                                    items = items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onReverse = {
                                    items = items.reversed()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onSaveCombined = {
                                    executeMerge()
                                },
                                isBusy = isBusy
                            )
                        }
                    }
                }
            }

            // ── Floating Toast Pill ───────────────────────────────────────────
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .shadow(16.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexusTheme.colors.textPrimary)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = NexusTheme.colors.background,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = toastMessage ?: "",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.background
                        )
                    }
                }
            }
        }
    }
}
