package com.nexus.core.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.hwpf.HWPFDocument
import java.io.InputStream
import java.io.OutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy

object PdfConverter {

    suspend fun convertTxtToPdf(context: Context, inputUri: Uri, outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size in points
            
            val paint = Paint().apply {
                textSize = 12f
            }
            val keywordPaint = Paint().apply {
                textSize = 12f
                color = android.graphics.Color.BLUE
                isFakeBoldText = true
            }
            val keywords = listOf("val ", "var ", "fun ", "class ", "import ", "if ", "else ")
            
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = 50f
            val xPosition = 50f
            val lineHeight = paint.fontSpacing
            val pageHeight = pageInfo.pageHeight - 50f
            var pageNumber = 1
            
            fun drawPageNumber(c: Canvas, num: Int) {
                val text = "Page $num"
                val textWidth = paint.measureText(text)
                c.drawText(text, (pageInfo.pageWidth - textWidth) / 2f, pageInfo.pageHeight - 30f, paint)
            }
            
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (yPosition + lineHeight > pageHeight - 40f) { // leave space for page number
                            drawPageNumber(canvas, pageNumber)
                            pdfDocument.finishPage(page)
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            yPosition = 50f
                            pageNumber++
                        }
                        
                        val currentLine = line ?: ""
                        // Basic syntax highlighting simulation for Kotlin/Java
                        var matchedKeyword = false
                        for (kw in keywords) {
                            if (currentLine.trim().startsWith(kw)) {
                                matchedKeyword = true
                                break
                            }
                        }
                        if (matchedKeyword) {
                            canvas.drawText(currentLine, xPosition, yPosition, keywordPaint)
                        } else {
                            canvas.drawText(currentLine, xPosition, yPosition, paint)
                        }
                        yPosition += lineHeight
                    }
                }
            }
            
            drawPageNumber(canvas, pageNumber)
            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
        }
    }

    suspend fun convertWordToPdf(context: Context, docUris: List<Uri>, outputStream: OutputStream, templateParams: Map<String, String> = emptyMap(), quality: Int = 100) {
        withContext(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            
            val paint = Paint().apply {
                textSize = 12f
            }
            
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = 50f
            val xPosition = 50f
            val lineHeight = paint.fontSpacing
            val pageHeight = pageInfo.pageHeight - 50f
            var pageNumber = 1
            
            fun drawPageNumber(c: Canvas, num: Int) {
                val text = "Page $num"
                val textWidth = paint.measureText(text)
                c.drawText(text, (pageInfo.pageWidth - textWidth) / 2f, pageInfo.pageHeight - 30f, paint)
            }
            
            for (inputUri in docUris) {
                context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                val textLines = extractTextFromWord(inputStream)
                
                for (rawParagraphText in textLines) {
                    var paragraphText = rawParagraphText
                    // Advanced: Smart Template Mapping
                    for ((key, value) in templateParams) {
                        paragraphText = paragraphText.replace("{{$key}}", value)
                    }
                    
                    if (paragraphText.isBlank()) {
                        yPosition += lineHeight
                        continue
                    }
                    val words = paragraphText.split(" ")
                    var currentLine = ""
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                        if (paint.measureText(testLine) > pageInfo.pageWidth - 100f) {
                            if (yPosition + lineHeight > pageHeight - 40f) {
                                drawPageNumber(canvas, pageNumber)
                                pdfDocument.finishPage(page)
                                page = pdfDocument.startPage(pageInfo)
                                canvas = page.canvas
                                yPosition = 50f
                                pageNumber++
                            }
                            canvas.drawText(currentLine, xPosition, yPosition, paint)
                            yPosition += lineHeight
                            currentLine = word
                        } else {
                            currentLine = testLine
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        if (yPosition + lineHeight > pageHeight - 40f) {
                            drawPageNumber(canvas, pageNumber)
                            pdfDocument.finishPage(page)
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            yPosition = 50f
                            pageNumber++
                        }
                        canvas.drawText(currentLine, xPosition, yPosition, paint)
                        yPosition += lineHeight
                    }
                    // Add basic paragraph spacing
                    yPosition += lineHeight * 0.5f
                }
                }
                // Draw page number for the last page of this document
                drawPageNumber(canvas, pageNumber)
                
                // If there are more documents, we could add a page break here, 
                // but let's just let it flow or start a new page.
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 50f
                pageNumber++
            }
            
            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
        }
    }
    
    private fun extractTextFromWord(inputStream: InputStream): List<String> {
        return try {
            val document = XWPFDocument(inputStream)
            document.paragraphs.map { it.text }
        } catch (e: Exception) {
            // Fallback for older .doc formats if needed, though POI HWPF might be trickier
            try {
                // Rewind stream if it was a buffered stream, but let's assume simple XWPF for now.
                // In a real app we'd need to use a buffered stream with mark/reset or read bytes first.
                // For simplicity, we just return error text if it fails.
                listOf("Error parsing Word document. Make sure it's a valid .docx file.")
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }
    
    // Add XLSX to PDF conversion placeholder or basic impl
    suspend fun convertXlsxToPdf(context: Context, inputUri: Uri, outputStream: OutputStream, repeatFirstRow: Boolean = true) {
         withContext(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            
            val paint = Paint().apply {
                textSize = 10f
            }
            
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var yPosition = 50f
            val xPosition = 50f
            val lineHeight = paint.fontSpacing
            val pageHeight = pageInfo.pageHeight - 50f
            
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                try {
                    val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(inputStream)
                    val sheet = workbook.getSheetAt(0)
                    
                    var pageNumber = 1
                    fun drawPageNumber(c: Canvas, num: Int) {
                        val text = "Page $num"
                        val textWidth = paint.measureText(text)
                        c.drawText(text, (pageInfo.pageWidth - textWidth) / 2f, pageInfo.pageHeight - 30f, paint)
                    }

                    var headerRowText: String? = null
                    for (row in sheet) {
                        val rowText = StringBuilder()
                        for (cell in row) {
                            rowText.append(cell.toString()).append(" | ")
                        }
                        
                        // Advanced: Capture header row
                        if (row.rowNum == 0 && repeatFirstRow) {
                            headerRowText = rowText.toString()
                        }
                        
                        if (yPosition + lineHeight + 15f > pageHeight - 40f) {
                            drawPageNumber(canvas, pageNumber)
                            pdfDocument.finishPage(page)
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            yPosition = 50f
                            pageNumber++
                            
                            // Advanced: Repeating Headers on new pages
                            if (headerRowText != null) {
                                val boldPaint = Paint(paint).apply { isFakeBoldText = true }
                                canvas.drawText(headerRowText, xPosition, yPosition, boldPaint)
                                canvas.drawLine(xPosition, yPosition + 5f, pageInfo.pageWidth - 50f, yPosition + 5f, boldPaint)
                                yPosition += lineHeight + 10f
                            }
                        }
                        
                        canvas.drawText(rowText.toString(), xPosition, yPosition, paint)
                        // Draw a basic horizontal line for grid
                        canvas.drawLine(xPosition, yPosition + 5f, pageInfo.pageWidth - 50f, yPosition + 5f, paint)
                        yPosition += lineHeight + 10f
                    }
                    drawPageNumber(canvas, pageNumber)
                    workbook.close()
                } catch (e: Exception) {
                     canvas.drawText("Error reading XLSX: ${e.message}", xPosition, yPosition, paint)
                }
            }
            
            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
        }
    }

    suspend fun convertDummyTextToPdf(text: String, outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            
            val paint = Paint().apply {
                textSize = 14f
            }
            
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            var yPosition = 100f
            val xPosition = 50f
            val lineHeight = paint.fontSpacing
            
            val lines = text.split("\n")
            for (line in lines) {
                canvas.drawText(line, xPosition, yPosition, paint)
                yPosition += lineHeight
            }
            
            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
        }
    }

    // Advanced Feature: Apply Bates Numbering / Stamping
    suspend fun applyBatesNumbering(context: Context, inputUri: Uri, outputStream: OutputStream, prefix: String = "DOC") {
        withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            try {
                val inputStream = context.contentResolver.openInputStream(inputUri) ?: return@withContext
                document = PDDocument.load(inputStream)
                
                for (i in 0 until document.numberOfPages) {
                    val page = document.getPage(i)
                    val cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)
                    cs.beginText()
                    cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 12f)
                    cs.setNonStrokingColor(0, 0, 0)
                    cs.newLineAtOffset(page.mediaBox.width - 100f, 30f)
                    cs.showText("$prefix-${String.format("%04d", i + 1)}")
                    cs.endText()
                    cs.close()
                }
                document.save(outputStream)
            } finally {
                document?.close()
            }
        }
    }

    suspend fun mergePdfs(context: Context, uris: List<Uri>, outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            val merger = PDFMergerUtility()
            merger.destinationStream = outputStream
            
            val inputStreams = mutableListOf<InputStream>()
            try {
                for (uri in uris) {
                    context.contentResolver.openInputStream(uri)?.let {
                        inputStreams.add(it)
                        merger.addSource(it)
                    }
                }
                
                // Merge the documents
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            } finally {
                // Ensure all input streams are closed
                inputStreams.forEach {
                    try { it.close() } catch (e: Exception) { /* ignore */ }
                }
            }
        }
    }

    suspend fun splitPdf(context: Context, inputUri: Uri, outputStream: OutputStream, pageRange: String) {
        withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            var newDocument: PDDocument? = null
            try {
                val inputStream = context.contentResolver.openInputStream(inputUri) ?: return@withContext
                document = PDDocument.load(inputStream)
                newDocument = PDDocument()
                
                val totalPages = document.numberOfPages
                val pagesToExtract = parsePageRange(pageRange, totalPages)
                
                for (pageIndex in pagesToExtract) {
                    if (pageIndex in 0 until totalPages) {
                        newDocument.addPage(document.getPage(pageIndex))
                    }
                }
                
                newDocument.save(outputStream)
            } finally {
                document?.close()
                newDocument?.close()
            }
        }
    }

    private fun parsePageRange(rangeStr: String, totalPages: Int): List<Int> {
        val pages = mutableSetOf<Int>()
        if (rangeStr.isBlank()) {
            // Default to all pages if empty
            return (0 until totalPages).toList()
        }
        
        val parts = rangeStr.split(",")
        for (part in parts) {
            val p = part.trim()
            if (p.contains("-")) {
                val bounds = p.split("-")
                if (bounds.size == 2) {
                    val start = bounds[0].trim().toIntOrNull()
                    val end = bounds[1].trim().toIntOrNull()
                    if (start != null && end != null) {
                        // user input is 1-indexed, we need 0-indexed
                        val s = (start - 1).coerceAtLeast(0)
                        val e = (end - 1).coerceAtMost(totalPages - 1)
                        if (s <= e) {
                            pages.addAll(s..e)
                        }
                    }
                }
            } else {
                val page = p.toIntOrNull()
                if (page != null) {
                    val idx = page - 1
                    if (idx in 0 until totalPages) {
                        pages.add(idx)
                    }
                }
            }
        }
        return pages.sorted()
    }

    suspend fun protectPdf(context: Context, inputUri: Uri, outputStream: OutputStream, password: String, ownerPassword: String = password, watermarkText: String = "") {
        withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            try {
                val inputStream = context.contentResolver.openInputStream(inputUri) ?: return@withContext
                document = PDDocument.load(inputStream)
                
                // Advanced: Invisible/Translucent Watermarking
                if (watermarkText.isNotBlank()) {
                    for (page in document.pages) {
                        val cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)
                        cs.beginText()
                        cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 50f)
                        cs.setNonStrokingColor(200, 200, 200) // Light gray translucent-like
                        val rad = Math.toRadians(45.0)
                        cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(rad, 100f, 200f))
                        cs.showText(watermarkText)
                        cs.endText()
                        cs.close()
                    }
                }
                
                val ap = AccessPermission()
                // If a separate owner password is provided, we can restrict editing/printing by default
                if (ownerPassword != password) {
                    ap.setCanModify(false)
                    ap.setCanExtractContent(false)
                }
                
                val spp = StandardProtectionPolicy(ownerPassword, password, ap)
                spp.encryptionKeyLength = 128
                
                document.protect(spp)
                document.save(outputStream)
            } finally {
                document?.close()
            }
        }
    }

    suspend fun rotatePdf(context: Context, inputUri: Uri, outputStream: OutputStream, degrees: Int, pageRange: String = "") {
        withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            try {
                val inputStream = context.contentResolver.openInputStream(inputUri) ?: return@withContext
                document = PDDocument.load(inputStream)
                
                val totalPages = document.numberOfPages
                val pagesToRotate = if (pageRange.isBlank()) {
                    (0 until totalPages).toList()
                } else {
                    parsePageRange(pageRange, totalPages)
                }
                
                for (pageIndex in pagesToRotate) {
                    if (pageIndex in 0 until totalPages) {
                        val page = document.getPage(pageIndex)
                        page.rotation = (page.rotation + degrees) % 360
                    }
                }
                
                document.save(outputStream)
            } finally {
                document?.close()
            }
        }
    }

    suspend fun repairPdf(context: Context, inputUri: Uri, outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            try {
                // PDDocument.load() often repairs broken cross-reference tables automatically
                val inputStream = context.contentResolver.openInputStream(inputUri) ?: return@withContext
                document = PDDocument.load(inputStream)
                document.save(outputStream)
            } finally {
                document?.close()
            }
        }
    }

    suspend fun compressPdf(context: Context, inputUri: Uri, outputStream: OutputStream, level: Float) {
        withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            try {
                // PDFBox doesn't have an advanced native compressor out of the box without external image processing
                // Saving it normally with PDFBox restructures it, which often compresses unoptimized files.
                val inputStream = context.contentResolver.openInputStream(inputUri) ?: return@withContext
                document = PDDocument.load(inputStream)
                document.save(outputStream)
            } finally {
                document?.close()
            }
        }
    }

    suspend fun convertImagesToPdf(context: Context, imageUris: List<Uri>, outputStream: OutputStream, quality: Int = 100) {
        withContext(Dispatchers.IO) {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            try {
                for (uri in imageUris) {
                    val options = android.graphics.BitmapFactory.Options()
                    // Apply downsampling if quality is significantly reduced
                    if (quality <= 50) {
                        options.inSampleSize = 2
                    }
                    
                    val inputStream = context.contentResolver.openInputStream(uri)
                    var bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream?.close()

                    if (bitmap != null) {
                        // Apply scaling if quality is between 50 and 100
                        if (quality in 51..99) {
                            val scaleFactor = quality / 100f
                            val newWidth = (bitmap.width * scaleFactor).toInt()
                            val newHeight = (bitmap.height * scaleFactor).toInt()
                            if (newWidth > 0 && newHeight > 0) {
                                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                                if (scaledBitmap != bitmap) {
                                    bitmap.recycle()
                                    bitmap = scaledBitmap
                                }
                            }
                        }

                        // Apply JPEG compression to reduce details
                        if (quality < 100) {
                            val baos = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, baos)
                            val compressedBytes = baos.toByteArray()
                            bitmap.recycle()
                            bitmap = android.graphics.BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size) ?: bitmap
                        }

                        // Create a page with the same dimensions as the image
                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas = page.canvas
                        val paint = android.graphics.Paint()
                        
                        canvas.drawBitmap(bitmap, 0f, 0f, paint)
                        pdfDocument.finishPage(page)
                        bitmap.recycle() // Free memory
                    }
                }
                pdfDocument.writeTo(outputStream)
            } finally {
                pdfDocument.close()
            }
        }
    }
}
