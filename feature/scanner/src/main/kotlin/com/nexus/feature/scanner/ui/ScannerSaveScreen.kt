package com.nexus.feature.scanner.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.components.NexusButton

@Composable
fun ScannerSaveScreen(
    viewModel: ScannerViewModel,
    scannedPages: List<ScannedPage>,
    onSaveConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    if (scannedPages.isEmpty()) return
    
    val context = LocalContext.current
    val fileName by viewModel.pdfFileName.collectAsState()
    val saveDirectoryUri by viewModel.saveDirectoryUri.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so we can write later
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            viewModel.updateSaveDirectory(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusButton(text = "Back", onClick = onBack)
            NexusText(
                text = "Save Document",
                style = NexusTheme.typography.title,
                color = NexusTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // PDF Thumbnail (Using first page)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NexusTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = scannedPages.first().croppedBitmap.asImageBitmap(),
                    contentDescription = "PDF Thumbnail",
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // File Name Input
            NexusText(
                text = "File Name",
                style = NexusTheme.typography.label,
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp)
            )
            BasicTextField(
                value = fileName,
                onValueChange = { viewModel.updatePdfFileName(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusTheme.colors.surface, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Location Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NexusTheme.colors.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    NexusText(
                        text = "Save Location",
                        style = NexusTheme.typography.label,
                        color = NexusTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val pathDisplay = if (saveDirectoryUri != null) {
                        saveDirectoryUri?.path?.substringAfterLast(":") ?: "Custom Location"
                    } else {
                        "Documents Folder"
                    }
                    NexusText(
                        text = pathDisplay,
                        style = NexusTheme.typography.body,
                        color = NexusTheme.colors.textPrimary
                    )
                }
                NexusButton(text = "Change", onClick = { launcher.launch(null) })
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save Button
            NexusButton(
                text = "Save PDF",
                onClick = onSaveConfirmed,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
