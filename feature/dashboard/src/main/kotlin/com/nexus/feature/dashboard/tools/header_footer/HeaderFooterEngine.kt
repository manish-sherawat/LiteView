package com.nexus.feature.dashboard.tools.header_footer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HeaderFooterEngine {

    /**
     * Reads display name, file size, and page count of chosen PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): HeaderFooterDocInfo = withContext(Dispatchers.IO) {
        var fileName = "Document.pdf"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    cursor.getString(nameIndex)?.let { fileName = it }
                }
                if (sizeIndex != -1) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

        val baseName = fileName.replace(Regex("(?i)\\.pdf$"), "")

        var pageCount = 1
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount.coerceAtLeast(1)
                }
            }
        } catch (_: Exception) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { doc ->
                        pageCount = doc.numberOfPages.coerceAtLeast(1)
                    }
                }
            } catch (_: Exception) {}
        }

        HeaderFooterDocInfo(
            uri = uri,
            name = fileName,
            sizeBytes = fileSize,
            pageCount = pageCount,
            baseName = baseName
        )
    }

    /**
     * Renders a lightweight, high-fidelity thumbnail bitmap of a given 0-indexed page using [PdfRenderer].
     */
    suspend fun renderThumbnail(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 300
    ): Bitmap = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(validIndex).use { page ->
                    val aspect = page.width.toFloat() / page.height.toFloat()
                    val renderW = targetWidth.coerceIn(120, 800)
                    val renderH = (renderW / aspect).toInt().coerceAtLeast(120)

                    val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    /**
     * Replaces dynamic tokens {page}, {total}, {date}, and {file}.
     */
    fun resolveTokens(
        text: String,
        pageNum: Int,
        pageCount: Int,
        baseName: String,
        startNum: Int
    ): String {
        if (text.isBlank()) return ""
        val offset = startNum - 1
        val displayPage = (pageNum + offset).coerceAtLeast(1)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        return text
            .replace(Regex("(?i)\\{page\\}"), displayPage.toString())
            .replace(Regex("(?i)\\{total\\}"), pageCount.toString())
            .replace(Regex("(?i)\\{date\\}"), today)
            .replace(Regex("(?i)\\{file\\}"), baseName)
    }

    /**
     * Sanitizes strings to standard WinAnsi ASCII range so PDFBox standard fonts never crash.
     */
    fun safeWinAnsi(str: String): String {
        return str
            .replace('‘', '\'')
            .replace('’', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('–', '-')
            .replace('—', '-')
            .replace("…", "...")
            .replace(Regex("[^\\x20-\\x7E\\xA0-\\xFF]"), "")
    }

    /**
     * Parses page numbers and ranges (e.g. "2-5, 8, 11-13").
     */
    fun parseRanges(input: String, totalPages: Int): Result<List<Int>> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Type some page numbers or ranges first."))
        }

        val parts = trimmed.split(Regex("[,\\s;]+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return Result.failure(IllegalArgumentException("Type some page numbers or ranges first."))
        }

        val parsedPages = mutableSetOf<Int>()
        val rangeRegex = Regex("^(\\d+)(?:[-–—](\\d+))?$")

        for (part in parts) {
            val match = rangeRegex.matchEntire(part)
                ?: return Result.failure(IllegalArgumentException("“$part” isn’t a valid page number or range."))

            val start = match.groupValues[1].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("“$part” isn’t a valid page number or range."))
            val end = if (match.groupValues[2].isNotBlank()) {
                match.groupValues[2].toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("“$part” isn’t a valid page number or range."))
            } else {
                start
            }

            val (a, b) = if (start <= end) start to end else end to start

            if (a < 1 || b > totalPages) {
                return Result.failure(
                    IllegalArgumentException(
                        "This PDF has $totalPages page${if (totalPages == 1) "" else "s"}. “$part” is out of range."
                    )
                )
            }

            for (p in a..b) {
                parsedPages.add(p)
            }
        }

        return Result.success(parsedPages.sorted())
    }

    private fun getPdfFont(family: HFFontFamily, style: HFFontStyle): PDFont {
        return when (family) {
            HFFontFamily.SANS -> when (style) {
                HFFontStyle.REGULAR -> PDType1Font.HELVETICA
                HFFontStyle.BOLD -> PDType1Font.HELVETICA_BOLD
                HFFontStyle.ITALIC -> PDType1Font.HELVETICA_OBLIQUE
            }
            HFFontFamily.SERIF -> when (style) {
                HFFontStyle.REGULAR -> PDType1Font.TIMES_ROMAN
                HFFontStyle.BOLD -> PDType1Font.TIMES_BOLD
                HFFontStyle.ITALIC -> PDType1Font.TIMES_ITALIC
            }
            HFFontFamily.MONO -> when (style) {
                HFFontStyle.REGULAR -> PDType1Font.COURIER
                HFFontStyle.BOLD -> PDType1Font.COURIER_BOLD
                HFFontStyle.ITALIC -> PDType1Font.COURIER_OBLIQUE
            }
        }
    }

    private fun parseColor(hex: String): Triple<Float, Float, Float> {
        val clean = hex.removePrefix("#")
        val parsed = clean.toLongOrNull(16) ?: 0L
        val r = ((parsed shr 16) and 0xFF).toFloat() / 255f
        val g = ((parsed shr 8) and 0xFF).toFloat() / 255f
        val b = (parsed and 0xFF).toFloat() / 255f
        return Triple(r, g, b)
    }

    /**
     * Applies headers and footers to the document pages and streams out directly to [OutputStream].
     */
    suspend fun applyHeaderFooter(
        context: Context,
        docInfo: HeaderFooterDocInfo,
        configs: Map<Int, PageHFConfig>,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val activeEntries = configs.filter { it.value.isActive }
        if (activeEntries.isEmpty()) {
            throw IllegalStateException("No headers or footers configured.")
        }

        onProgress(0.05f)

        var srcDoc: PDDocument? = null
        try {
            val stream = context.contentResolver.openInputStream(docInfo.uri)
                ?: throw IllegalStateException("Failed to open source PDF.")

            srcDoc = PDDocument.load(stream)
            onProgress(0.15f)

            val totalActive = activeEntries.size
            var processed = 0

            for ((pageNumber, config) in activeEntries) {
                if (pageNumber < 1 || pageNumber > srcDoc.numberOfPages) continue
                val page = srcDoc.getPage(pageNumber - 1)
                val box = page.cropBox ?: page.mediaBox
                val bx = box.lowerLeftX
                val by = box.lowerLeftY
                val w = box.width
                val h = box.height

                val font = getPdfFont(config.font, config.style)
                val (r, g, b) = parseColor(config.colorHex)
                val sz = config.size.toFloat()
                val vM = config.vMargin.toFloat()
                val hM = config.hMargin.toFloat()

                val headerY = by + h - vM - sz * 0.85f
                val footerY = by + vM

                val hActive = config.hasHeaderText || config.ruleH
                val fActive = config.hasFooterText || config.ruleF

                PDPageContentStream(srcDoc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    // 1. Opaque white mask strip
                    if (config.mask) {
                        cs.setNonStrokingColor(1f, 1f, 1f)
                        if (hActive) {
                            val stripBottom = headerY - sz * 0.55f
                            cs.addRect(bx, stripBottom, w, (by + h) - stripBottom)
                            cs.fill()
                        }
                        if (fActive) {
                            val stripTop = footerY + sz * 1.25f
                            cs.addRect(bx, by, w, stripTop - by)
                            cs.fill()
                        }
                    }

                    // 2. Horizontal divider lines
                    if (config.ruleH) {
                        val ry = headerY - sz * 0.42f
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(0.65f)
                        cs.moveTo(bx + hM, ry)
                        cs.lineTo(bx + w - hM, ry)
                        cs.stroke()
                    }
                    if (config.ruleF) {
                        val ry = footerY + sz * 1.15f
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(0.65f)
                        cs.moveTo(bx + hM, ry)
                        cs.lineTo(bx + w - hM, ry)
                        cs.stroke()
                    }

                    // 3. Draw text slots
                    fun drawSlot(raw: String, align: String, yPos: Float) {
                        if (raw.isBlank()) return
                        val resolved = safeWinAnsi(resolveTokens(raw, pageNumber, docInfo.pageCount, docInfo.baseName, config.startNum))
                        if (resolved.isBlank()) return

                        try {
                            val tw = font.getStringWidth(resolved) / 1000f * sz
                            val xPos = when (align) {
                                "left" -> bx + hM
                                "center" -> bx + (w - tw) / 2f
                                "right" -> bx + w - hM - tw
                                else -> bx + hM
                            }

                            cs.beginText()
                            cs.setFont(font, sz)
                            cs.setNonStrokingColor(r, g, b)
                            cs.newLineAtOffset(xPos, yPos)
                            cs.showText(resolved)
                            cs.endText()
                        } catch (_: Exception) {}
                    }

                    drawSlot(config.hl, "left", headerY)
                    drawSlot(config.hc, "center", headerY)
                    drawSlot(config.hr, "right", headerY)

                    drawSlot(config.fl, "left", footerY)
                    drawSlot(config.fc, "center", footerY)
                    drawSlot(config.fr, "right", footerY)
                }

                processed++
                val p = 0.15f + 0.75f * (processed.toFloat() / totalActive)
                onProgress(p.coerceIn(0.15f, 0.90f))
            }

            srcDoc.save(outputStream)
            onProgress(1f)
            activeEntries.size
        } finally {
            try { srcDoc?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Applies headers and footers to the document pages and saves directly to a SAF destination [Uri].
     */
    suspend fun applyHeaderFooter(
        context: Context,
        docInfo: HeaderFooterDocInfo,
        configs: Map<Int, PageHFConfig>,
        destUri: Uri,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): HeaderFooterExportResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(destUri)
            ?: throw IllegalStateException("Cannot open destination file.")
        val count = stream.use { out ->
            applyHeaderFooter(context, docInfo, configs, out, onProgress)
        }
        HeaderFooterExportResult(
            outputFile = null,
            outputUri = destUri,
            outputFileName = outputFileName,
            pagesUpdatedCount = count,
            fileSizeBytes = 0L
        )
    }

    /**
     * Applies headers and footers to the document pages and exports to Downloads.
     */
    suspend fun applyHeaderFooter(
        context: Context,
        docInfo: HeaderFooterDocInfo,
        configs: Map<Int, PageHFConfig>,
        onProgress: (Float) -> Unit
    ): HeaderFooterExportResult = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        var outFile = File(downloadsDir, "${docInfo.baseName}-header-footer.pdf")
        var counter = 1
        while (outFile.exists() && outFile.length() > 0) {
            val candidate = File(downloadsDir, "${docInfo.baseName}-header-footer($counter).pdf")
            if (!candidate.exists()) {
                outFile = candidate
                break
            }
            counter++
        }

        val count = FileOutputStream(outFile).use { fos ->
            applyHeaderFooter(context, docInfo, configs, fos, onProgress)
        }

        HeaderFooterExportResult(
            outputFile = outFile,
            outputUri = null,
            outputFileName = outFile.name,
            pagesUpdatedCount = count,
            fileSizeBytes = outFile.length()
        )
    }

    /**
     * Formats bytes into human readable format.
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format("%.1f kB", bytes / 1000.0)
            else -> String.format("%.2f MB", bytes / 1_000_000.0)
        }
    }
}

