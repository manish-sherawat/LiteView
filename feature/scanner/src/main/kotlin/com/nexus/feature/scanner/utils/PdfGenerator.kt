package com.nexus.feature.scanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.nexus.feature.scanner.ui.ScannedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {
    
    suspend fun generatePdf(
        context: Context, 
        pages: List<ScannedPage>,
        fileName: String = "Scanned_Document_${System.currentTimeMillis()}",
        targetDirUri: Uri? = null
    ): Uri? = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext null
        
        val pdfDocument = PdfDocument()
        
        for ((index, page) in pages.withIndex()) {
            val bitmap = page.croppedBitmap
            
            // Standard A4 aspect ratio at 72 PPI (approx 595x842)
            val a4Width = 595
            val a4Height = 842
            
            val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, index + 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas = pdfPage.canvas
            
            // Fit image into A4 page while preserving aspect ratio
            val scaleX = a4Width.toFloat() / bitmap.width
            val scaleY = a4Height.toFloat() / bitmap.height
            val scale = minOf(scaleX, scaleY)
            
            val scaledWidth = (bitmap.width * scale).toInt()
            val scaledHeight = (bitmap.height * scale).toInt()
            
            val left = (a4Width - scaledWidth) / 2
            val top = (a4Height - scaledHeight) / 2
            
            val destRect = Rect(left, top, left + scaledWidth, top + scaledHeight)
            canvas.drawBitmap(bitmap, null, destRect, null)
            
            pdfDocument.finishPage(pdfPage)
        }
        
        val finalFileName = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"

        try {
            if (targetDirUri != null) {
                val documentFile = DocumentFile.fromTreeUri(context, targetDirUri)
                val newFile = documentFile?.createFile("application/pdf", finalFileName)
                newFile?.uri?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        pdfDocument.writeTo(out)
                    }
                    return@withContext uri
                }
            }

            // Fallback if targetDirUri is null or failed
            val docsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (!docsDir.exists()) docsDir.mkdirs()
            
            val outputFile = File(docsDir, finalFileName)
            
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            // Trigger media scanner to make it visible to other apps and dashboard immediately
            android.media.MediaScannerConnection.scanFile(
                context, 
                arrayOf(outputFile.absolutePath), 
                arrayOf("application/pdf"), 
                null
            )
            return@withContext Uri.fromFile(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            pdfDocument.close()
        }
    }
}
