package com.nexus.feature.scanner.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.scanner.utils.PdfGenerator
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scannedPages by viewModel.scannedPages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                val pdfUri = scanResult.pdf?.uri
                val imageUris = scanResult.pages?.map { it.imageUri } ?: emptyList()
                viewModel.onScanResult(context, imageUris, pdfUri)
            } else {
                viewModel.onScanCancelled()
            }
        } else {
            viewModel.onScanCancelled()
        }
    }

    when (uiState) {
        ScannerState.CAMERA -> {
            LaunchedEffect(Unit) {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
                    .setScannerMode(SCANNER_MODE_FULL)
                    .build()

                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(context as Activity)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to start scanner", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
            }
            
            // Empty background while waiting for Google Scanner to launch
            Box(
                modifier = Modifier.fillMaxSize().background(NexusTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = NexusTheme.colors.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        ScannerState.REVIEW -> {
            ScannerReviewScreen(
                viewModel = viewModel,
                scannedPages = scannedPages,
                onAddMore = {
                    viewModel.addMorePages()
                },
                onFinish = {
                    viewModel.finishScanning()
                }
            )
        }
        ScannerState.SAVE_DETAILS -> {
            ScannerSaveScreen(
                viewModel = viewModel,
                scannedPages = scannedPages,
                onSaveConfirmed = {
                    viewModel.confirmSave()
                },
                onBack = {
                    // Navigate back to REVIEW
                    viewModel.onScanCancelled() 
                }
            )
        }
        ScannerState.GENERATING_PDF -> {
            Box(
                modifier = Modifier.fillMaxSize().background(NexusTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = NexusTheme.colors.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    NexusText(
                        text = "Saving PDF...",
                        style = NexusTheme.typography.title,
                        color = NexusTheme.colors.textPrimary
                    )
                }
                
                LaunchedEffect(Unit) {
                    coroutineScope.launch {
                        val fileName = viewModel.pdfFileName.value
                        val targetDirUri = viewModel.saveDirectoryUri.value
                        
                        // We can either use the generatedPdfUri from ML Kit, or build it ourselves.
                        // Let's stick with the existing PdfGenerator for consistency, 
                        // as it uses our ScannedPages which the user may have reordered or deleted.
                        val pdfUri = PdfGenerator.generatePdf(context, scannedPages, fileName, targetDirUri)
                        
                        if (pdfUri != null) {
                            Toast.makeText(context, "Saved successfully!", Toast.LENGTH_LONG).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                            viewModel.reset()
                        }
                    }
                }
            }
        }
        ScannerState.DONE -> {
            LaunchedEffect(Unit) {
                onBack()
            }
        }
    }
}
