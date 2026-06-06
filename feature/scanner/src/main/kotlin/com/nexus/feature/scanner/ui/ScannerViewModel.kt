package com.nexus.feature.scanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ScannerState {
    CAMERA, REVIEW, SAVE_DETAILS, GENERATING_PDF, DONE
}

data class ScannedPage(
    val originalUri: Uri,
    val croppedBitmap: Bitmap
)

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerState.CAMERA)
    val uiState: StateFlow<ScannerState> = _uiState

    // All pages captured in this session
    private val _scannedPages = MutableStateFlow<List<ScannedPage>>(emptyList())
    val scannedPages: StateFlow<List<ScannedPage>> = _scannedPages

    val pdfFileName = MutableStateFlow("Scanned_Document_${System.currentTimeMillis()}")
    val saveDirectoryUri = MutableStateFlow<Uri?>(null)
    
    var generatedPdfUri: Uri? = null
    
    private val _ocrResult = MutableStateFlow<String?>(null)
    val ocrResult: StateFlow<String?> = _ocrResult

    fun onScanResult(context: Context, imageUris: List<Uri>, pdfUri: Uri?) {
        generatedPdfUri = pdfUri
        
        viewModelScope.launch(Dispatchers.IO) {
            val pages = imageUris.mapNotNull { uri ->
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    ScannedPage(uri, bitmap)
                } catch (e: Exception) {
                    null
                }
            }
            _scannedPages.value = pages
            
            if (pages.isNotEmpty()) {
                _uiState.value = ScannerState.REVIEW
            } else {
                _uiState.value = ScannerState.CAMERA
            }
        }
    }
    
    fun onScanCancelled() {
        if (_scannedPages.value.isEmpty()) {
            _uiState.value = ScannerState.DONE
        } else {
            _uiState.value = ScannerState.REVIEW
        }
    }

    fun deletePage(index: Int) {
        val pages = _scannedPages.value.toMutableList()
        if (index in pages.indices) {
            val removed = pages.removeAt(index)
            removed.croppedBitmap.recycle()
            _scannedPages.value = pages
            if (pages.isEmpty()) {
                _uiState.value = ScannerState.CAMERA
            }
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val pages = _scannedPages.value.toMutableList()
        if (fromIndex in pages.indices && toIndex in pages.indices) {
            val item = pages.removeAt(fromIndex)
            pages.add(toIndex, item)
            _scannedPages.value = pages
        }
    }

    fun extractText(index: Int) {
        val pages = _scannedPages.value
        if (index !in pages.indices) return
        
        val page = pages[index]
        val image = InputImage.fromBitmap(page.croppedBitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                _ocrResult.value = visionText.text
            }
            .addOnFailureListener { e ->
                _ocrResult.value = "Failed to extract text: ${e.message}"
            }
    }
    
    fun clearOcrResult() {
        _ocrResult.value = null
    }

    fun addMorePages() {
        _uiState.value = ScannerState.CAMERA
    }

    fun finishScanning() {
        _uiState.value = ScannerState.SAVE_DETAILS
    }
    
    fun updatePdfFileName(name: String) {
        pdfFileName.value = name
    }
    
    fun updateSaveDirectory(uri: Uri) {
        saveDirectoryUri.value = uri
    }
    
    fun confirmSave() {
        _uiState.value = ScannerState.GENERATING_PDF
    }

    fun reset() {
        _scannedPages.value.forEach { it.croppedBitmap.recycle() }
        _scannedPages.value = emptyList()
        generatedPdfUri = null
        _uiState.value = ScannerState.CAMERA
    }

    override fun onCleared() {
        super.onCleared()
        _scannedPages.value.forEach { it.croppedBitmap.recycle() }
    }
}
