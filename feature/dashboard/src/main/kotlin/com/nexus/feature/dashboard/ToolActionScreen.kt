package com.nexus.feature.dashboard

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.R
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.core.utils.PdfConverter
import kotlinx.coroutines.launch

enum class ToolPhase {
    UPLOAD,
    CONFIGURE,
    FINALIZE,
    PROCESSING,
    SUCCESS
}

// Note: Tool model and toolsList registry are now centralized in ToolsModel.kt


@Composable
fun ToolActionScreen(
    toolId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (toolId == "image_to_pdf") {
        com.nexus.feature.dashboard.tools.image_to_pdf.ImageToPdfScreen(
            onBack = onBack,
            modifier = modifier
        )
        return
    }
    
    if (toolId == "word_to_pdf") {
        com.nexus.feature.dashboard.tools.word_to_pdf.WordToPdfScreen(
            onBack = onBack,
            modifier = modifier
        )
        return
    }

    val tool = toolsList.find { it.id == toolId } ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var currentPhase by remember { mutableStateOf(ToolPhase.UPLOAD) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // File details
    var fileName by remember { mutableStateOf("") }
    var finalFileName by remember { mutableStateOf("Converted_${System.currentTimeMillis()}") }
    var fileDetails by remember { mutableStateOf("") }

    // Convert tool options
    var enableHighQuality by remember { mutableStateOf(true) }
    var preserveFormatting by remember { mutableStateOf(true) }
    var optimizeForWeb by remember { mutableStateOf(false) }

    // Organizer/Optimizer tool options
    var password by remember { mutableStateOf("") }
    var pageRange by remember { mutableStateOf("") }
    var rotationDegrees by remember { mutableStateOf("90") }
    var compressionLevel by remember { mutableFloatStateOf(0.5f) } // 0.0 to 1.0

    // Helper to get file name from Uri
    fun getFileName(uri: Uri): String {
        var name = "Unknown Document"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && (selectedUri != null || selectedUris.isNotEmpty())) {
            currentPhase = ToolPhase.PROCESSING
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                        when (toolId) {
                            "word_to_pdf" -> {
                                val urisToProcess = if (selectedUris.isEmpty() && selectedUri != null) listOf(selectedUri!!) else selectedUris
                                PdfConverter.convertWordToPdf(context, urisToProcess, outputStream)
                            }
                            "xlsx_to_pdf" -> PdfConverter.convertXlsxToPdf(context, selectedUri!!, outputStream)
                            "txt_to_pdf" -> PdfConverter.convertTxtToPdf(context, selectedUri!!, outputStream)
                            "csv_convert" -> PdfConverter.convertTxtToPdf(context, selectedUri!!, outputStream) // Fallback for now
                            "merge_pdf" -> {
                                if (selectedUris.isEmpty() && selectedUri != null) {
                                    PdfConverter.mergePdfs(context, listOf(selectedUri!!), outputStream)
                                } else {
                                    PdfConverter.mergePdfs(context, selectedUris, outputStream)
                                }
                            }
                            "split_pdf" -> {
                                PdfConverter.splitPdf(context, selectedUri!!, outputStream, pageRange)
                            }
                            "protect_pdf" -> {
                                PdfConverter.protectPdf(context, selectedUri!!, outputStream, password)
                            }
                            "rotate_pdf" -> {
                                val degrees = rotationDegrees.toIntOrNull() ?: 90
                                PdfConverter.rotatePdf(context, selectedUri!!, outputStream, degrees)
                            }
                            "compress_pdf" -> {
                                PdfConverter.compressPdf(context, selectedUri!!, outputStream, compressionLevel)
                            }
                            "repair_pdf", "pdfa_archive" -> {
                                PdfConverter.repairPdf(context, selectedUri!!, outputStream)
                            }
                            "merge_sheets", "e_signature" -> {
                                val dummyContent = "This is a placeholder for $toolId.\nRequires advanced document manipulation."
                                PdfConverter.convertDummyTextToPdf(dummyContent, outputStream)
                            }
                            else -> {
                                throw Exception("Not implemented yet: $toolId")
                            }
                        }
                    }
                    currentPhase = ToolPhase.SUCCESS
                } catch (e: Exception) {
                    errorMessage = e.message ?: "An error occurred"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    currentPhase = ToolPhase.FINALIZE
                }
            }
        } else {
            // Cancelled save
            currentPhase = ToolPhase.FINALIZE
        }
    }

    val isMultiSelect = toolId == "merge_pdf" || toolId == "merge_sheets"

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = getFileName(uri)
            val nameWithoutExt = fileName.substringBeforeLast(".")
            finalFileName = "${nameWithoutExt}_modified"
            fileDetails = "1 File Selected"
            currentPhase = ToolPhase.CONFIGURE
        }
    }

    val openMultipleDocsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            fileName = "Multiple Files Selected"
            finalFileName = "Merged_Document_${System.currentTimeMillis()}"
            fileDetails = "${uris.size} Files Selected"
            currentPhase = ToolPhase.CONFIGURE
        }
    }
    
    fun launchPicker() {
        val mimeTypes = when (toolId) {
            "word_to_pdf" -> arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            "xlsx_to_pdf", "merge_sheets" -> arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            "txt_to_pdf" -> arrayOf("text/plain")
            "csv_convert" -> arrayOf("text/csv")
            else -> arrayOf("application/pdf") // Default to pdf for merge, split, rotate, etc.
        }
        
        if (isMultiSelect) {
            openMultipleDocsLauncher.launch(mimeTypes)
        } else {
            openDocLauncher.launch(mimeTypes)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        NexusTopBar(
            title = tool.title,
            navigationIcon = {
                Image(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .clip(CircleShape)
                        .springBounceClick {
                            if (currentPhase == ToolPhase.CONFIGURE || currentPhase == ToolPhase.FINALIZE) {
                                currentPhase = ToolPhase.UPLOAD
                                selectedUri = null
                                selectedUris = emptyList()
                            } else {
                                onBack()
                            }
                        }
                        .padding(NexusTheme.spacing.sm)
                        .size(24.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(NexusTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Header info
            if (currentPhase == ToolPhase.UPLOAD) {
                Spacer(modifier = Modifier.height(24.dp))
                if (tool.iconRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = tool.iconRes),
                        contentDescription = tool.title,
                        modifier = Modifier.size(72.dp)
                    )
                } else if (tool.icon != null) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        modifier = Modifier.size(72.dp),
                        tint = NexusTheme.colors.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                NexusText(
                    text = tool.title,
                    style = NexusTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                NexusText(
                    text = tool.description,
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(48.dp))
            }

            Crossfade(targetState = currentPhase, label = "phase_crossfade") { phase ->
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (phase) {
                        ToolPhase.UPLOAD -> {
                            // Phase 1: Upload Dropzone
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .clip(NexusTheme.shapes.large)
                                    .background(NexusTheme.colors.primary.copy(alpha = 0.05f))
                                    .clickable { launchPicker() },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(2.dp, NexusTheme.colors.primary.copy(alpha = 0.5f), NexusTheme.shapes.large)
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.upload),
                                        contentDescription = "Upload Illustration",
                                        modifier = Modifier
                                            .size(140.dp)
                                            .padding(bottom = NexusTheme.spacing.md)
                                    )
                                    NexusText(
                                        text = "Tap to select your document",
                                        style = NexusTheme.typography.title,
                                        color = NexusTheme.colors.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    NexusText(
                                        text = if (isMultiSelect) "Selecting multiple files is supported" else "Select a single file to process",
                                        style = NexusTheme.typography.caption,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                        
                        ToolPhase.CONFIGURE -> {
                            // Phase 2: Preview & Configuration
                            
                            // Document Preview Area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .shadow(8.dp, NexusTheme.shapes.medium)
                                    .clip(NexusTheme.shapes.medium)
                                    .background(NexusTheme.colors.surface)
                                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.3f), NexusTheme.shapes.medium),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (tool.iconRes != null) {
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(id = tool.iconRes),
                                            contentDescription = "Document",
                                            modifier = Modifier.size(42.dp)
                                        )
                                    } else if (tool.icon != null) {
                                        Icon(
                                            imageVector = tool.icon,
                                            contentDescription = "Document",
                                            modifier = Modifier.size(42.dp),
                                            tint = NexusTheme.colors.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    NexusText(
                                        text = fileName,
                                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                                        color = NexusTheme.colors.textPrimary,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = NexusTheme.spacing.lg)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    NexusText(
                                        text = fileDetails,
                                        style = NexusTheme.typography.caption,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            // Options List
                            Column(modifier = Modifier.fillMaxWidth()) {
                                NexusText(
                                    text = "Settings",
                                    style = NexusTheme.typography.title,
                                    modifier = Modifier.padding(bottom = NexusTheme.spacing.md)
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(NexusTheme.shapes.medium)
                                        .background(NexusTheme.colors.surface)
                                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.medium)
                                        .padding(NexusTheme.spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                                ) {
                                    when (toolId) {
                                        "word_to_pdf", "xlsx_to_pdf", "txt_to_pdf", "csv_convert" -> {
                                            AdvancedOptionToggle(
                                                title = "High Quality Mode",
                                                description = "Preserve maximum image resolution",
                                                checked = enableHighQuality,
                                                onCheckedChange = { enableHighQuality = it }
                                            )
                                            AdvancedOptionToggle(
                                                title = "Preserve Formatting",
                                                description = "Keep exact layout and fonts if possible",
                                                checked = preserveFormatting,
                                                onCheckedChange = { preserveFormatting = it }
                                            )
                                            AdvancedOptionToggle(
                                                title = "Optimize for Web",
                                                description = "Compress final output for fast web viewing",
                                                checked = optimizeForWeb,
                                                onCheckedChange = { optimizeForWeb = it }
                                            )
                                        }
                                        "protect_pdf" -> {
                                            OutlinedTextField(
                                                value = password,
                                                onValueChange = { password = it },
                                                label = { NexusText("PDF Password", color = NexusTheme.colors.textSecondary) },
                                                visualTransformation = PasswordVisualTransformation(),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = NexusTheme.colors.primary,
                                                    unfocusedBorderColor = NexusTheme.colors.divider,
                                                    focusedTextColor = NexusTheme.colors.textPrimary,
                                                    unfocusedTextColor = NexusTheme.colors.textPrimary
                                                )
                                            )
                                        }
                                        "split_pdf" -> {
                                            OutlinedTextField(
                                                value = pageRange,
                                                onValueChange = { pageRange = it },
                                                label = { NexusText("Page Range (e.g., 1-5, 8)", color = NexusTheme.colors.textSecondary) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = NexusTheme.colors.primary,
                                                    unfocusedBorderColor = NexusTheme.colors.divider,
                                                    focusedTextColor = NexusTheme.colors.textPrimary,
                                                    unfocusedTextColor = NexusTheme.colors.textPrimary
                                                )
                                            )
                                        }
                                        "rotate_pdf" -> {
                                            OutlinedTextField(
                                                value = rotationDegrees,
                                                onValueChange = { rotationDegrees = it },
                                                label = { NexusText("Degrees (90, 180, 270)", color = NexusTheme.colors.textSecondary) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = NexusTheme.colors.primary,
                                                    unfocusedBorderColor = NexusTheme.colors.divider,
                                                    focusedTextColor = NexusTheme.colors.textPrimary,
                                                    unfocusedTextColor = NexusTheme.colors.textPrimary
                                                )
                                            )
                                        }
                                        "compress_pdf" -> {
                                            NexusText(
                                                text = "Compression Level",
                                                style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                                                color = NexusTheme.colors.textPrimary
                                            )
                                            Slider(
                                                value = compressionLevel,
                                                onValueChange = { compressionLevel = it },
                                                valueRange = 0f..1f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = NexusTheme.colors.primary,
                                                    activeTrackColor = NexusTheme.colors.primary,
                                                    inactiveTrackColor = NexusTheme.colors.surfaceVariant
                                                )
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                NexusText("Low", style = NexusTheme.typography.caption, color = NexusTheme.colors.textSecondary)
                                                NexusText("High", style = NexusTheme.typography.caption, color = NexusTheme.colors.textSecondary)
                                            }
                                        }
                                        "merge_pdf", "merge_sheets" -> {
                                            NexusText(
                                                text = "Documents will be merged in the order they were selected.",
                                                style = NexusTheme.typography.body,
                                                color = NexusTheme.colors.textSecondary
                                            )
                                        }
                                        else -> {
                                            NexusText(
                                                text = "No additional options available for this tool.",
                                                style = NexusTheme.typography.body,
                                                color = NexusTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            // Bottom Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                            ) {
                                NexusButton(
                                    text = "Cancel",
                                    onClick = { 
                                        currentPhase = ToolPhase.UPLOAD
                                        selectedUri = null
                                        selectedUris = emptyList()
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    containerColor = NexusTheme.colors.surfaceVariant,
                                    contentColor = NexusTheme.colors.textPrimary
                                )
                                NexusButton(
                                    text = "Next",
                                    onClick = { currentPhase = ToolPhase.FINALIZE },
                                    modifier = Modifier.weight(1f).height(56.dp)
                                )
                            }
                        }
                        
                        ToolPhase.FINALIZE -> {
                            // Phase 3: Finalize & Save
                            Column(modifier = Modifier.fillMaxWidth()) {
                                NexusText(
                                    text = "Save Document",
                                    style = NexusTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = NexusTheme.spacing.xs)
                                )
                                NexusText(
                                    text = "Review your output details before saving.",
                                    style = NexusTheme.typography.body,
                                    color = NexusTheme.colors.textSecondary,
                                    modifier = Modifier.padding(bottom = NexusTheme.spacing.xl)
                                )
                                
                                OutlinedTextField(
                                    value = finalFileName,
                                    onValueChange = { finalFileName = it },
                                    label = { NexusText("Output File Name", color = NexusTheme.colors.textSecondary) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NexusTheme.colors.primary,
                                        unfocusedBorderColor = NexusTheme.colors.divider,
                                        focusedTextColor = NexusTheme.colors.textPrimary,
                                        unfocusedTextColor = NexusTheme.colors.textPrimary
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Mock metadata tags
                                NexusText(
                                    text = "Tags (Optional)",
                                    style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                    color = NexusTheme.colors.textPrimary,
                                    modifier = Modifier.padding(bottom = NexusTheme.spacing.xs)
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                                    Chip(text = "Work", isSelected = true)
                                    Chip(text = "Personal", isSelected = false)
                                    Chip(text = "Archive", isSelected = false)
                                }
                                
                                Spacer(modifier = Modifier.height(48.dp))
                                
                                if (errorMessage != null) {
                                    NexusText(
                                        text = "Failed: $errorMessage",
                                        style = NexusTheme.typography.body,
                                        color = NexusTheme.colors.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = NexusTheme.spacing.md)
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
                                ) {
                                    NexusButton(
                                        text = "Back",
                                        onClick = { currentPhase = ToolPhase.CONFIGURE },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        containerColor = NexusTheme.colors.surfaceVariant,
                                        contentColor = NexusTheme.colors.textPrimary
                                    )
                                    NexusButton(
                                        text = "Save Now",
                                        onClick = { createDocLauncher.launch("$finalFileName.pdf") },
                                        modifier = Modifier.weight(1f).height(56.dp)
                                    )
                                }
                            }
                        }
                        
                        ToolPhase.PROCESSING -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = NexusTheme.spacing.xl)
                            ) {
                                CircularProgressIndicator(
                                    color = NexusTheme.colors.primary,
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                NexusText(
                                    text = "Processing document...",
                                    style = NexusTheme.typography.title,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NexusText(
                                    text = "Applying advanced settings...",
                                    style = NexusTheme.typography.body,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }
                        }
                        
                        ToolPhase.SUCCESS -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = NexusTheme.spacing.xl)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.success.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.nexus.core.ui.animations.AnimatedSuccessCheck(
                                        modifier = Modifier.size(40.dp),
                                        color = NexusTheme.colors.success,
                                        strokeWidth = 8f
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                NexusText(
                                    text = "Saved successfully!",
                                    style = NexusTheme.typography.titleLarge,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                NexusText(
                                    text = "Your document has been processed and saved.",
                                    style = NexusTheme.typography.body,
                                    color = NexusTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(48.dp))
                                NexusButton(
                                    text = "Done",
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                NexusButton(
                                    text = "Process Another",
                                    onClick = { 
                                        currentPhase = ToolPhase.UPLOAD
                                        selectedUri = null
                                        selectedUris = emptyList()
                                        fileName = ""
                                        finalFileName = ""
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    containerColor = NexusTheme.colors.surfaceVariant,
                                    contentColor = NexusTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedOptionToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = NexusTheme.spacing.md)) {
            NexusText(
                text = title,
                style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = NexusTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            NexusText(
                text = description,
                style = NexusTheme.typography.caption,
                color = NexusTheme.colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NexusTheme.colors.background,
                checkedTrackColor = NexusTheme.colors.primary,
                uncheckedThumbColor = NexusTheme.colors.textSecondary,
                uncheckedTrackColor = NexusTheme.colors.surfaceVariant
            )
        )
    }
}

@Composable
fun Chip(text: String, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .clip(NexusTheme.shapes.medium)
            .background(if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant)
            .border(
                1.dp, 
                if (isSelected) Color.Transparent else NexusTheme.colors.divider, 
                NexusTheme.shapes.medium
            )
            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
    ) {
        NexusText(
            text = text,
            style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = if (isSelected) NexusTheme.colors.background else NexusTheme.colors.textPrimary
        )
    }
}

