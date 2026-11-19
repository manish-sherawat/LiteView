package com.nexus.feature.dashboard.tools.txt_to_pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object TextToPdfEngine {

    private const val MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024L // 15 MB limit

    /**
     * Map of common typographic characters to standardized equivalents for clean PDF rendering.
     */
    private val CHAR_MAP = mapOf(
        '\u2018' to "'",
        '\u2019' to "'",
        '\u201C' to "\"",
        '\u201D' to "\"",
        '\u2013' to "-",
        '\u2014' to "-",
        '\u2026' to "...",
        '\u00A0' to " ",
        '\u2022' to "-",
        '\u2192' to "->"
    )

    /**
     * Formats bytes into human-readable string:
     * - < 1000 B: "N B"
     * - < 1 MB: "N.1 kB"
     * - >= 1 MB: "N.2 MB"
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1_000_000 -> String.format(Locale.US, "%.1f kB", bytes / 1000.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    /**
     * Normalizes line endings, tabs, and standard typographic quotes/dashes.
     */
    fun cleanText(raw: String): Pair<String, Int> {
        var lost = 0
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\r' -> {
                    if (i + 1 < raw.length && raw[i + 1] == '\n') {
                        i++
                    }
                    sb.append('\n')
                }
                c == '\t' -> {
                    sb.append("    ")
                }
                CHAR_MAP.containsKey(c) -> {
                    sb.append(CHAR_MAP[c])
                }
                c == '\u0000' -> {
                    // Skip null bytes
                    lost++
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        return Pair(sb.toString(), lost)
    }

    /**
     * Reads a text file from a Uri, checking size limits and charset decoding.
     */
    suspend fun readTextFromUri(context: Context, uri: Uri): Pair<TxtDocInfo, String> = withContext(Dispatchers.IO) {
        var fileName = "Document.txt"
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

        if (fileSize > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("That file is too large (max 15 MB).")
        }

        val baseName = fileName.replace(Regex("(?i)\\.[^.]+$"), "").ifBlank { "document" }

        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes()
        } ?: throw IllegalStateException("Could not open file.")

        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("That file is too large (max 15 MB).")
        }

        // Quick check for binary file
        val checkLen = min(bytes.size, 2000)
        for (b in 0 until checkLen) {
            if (bytes[b] == 0.toByte()) {
                throw IllegalArgumentException("That doesn't look like a text file.")
            }
        }

        // Try UTF-8 first, fallback to ISO-8859-1
        val text = try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            String(bytes, Charset.forName("ISO-8859-1"))
        }

        val info = TxtDocInfo(
            uri = uri,
            fileName = fileName,
            fileMeta = formatFileSize(if (fileSize > 0) fileSize else bytes.size.toLong()),
            sizeBytes = if (fileSize > 0) fileSize else bytes.size.toLong(),
            baseName = baseName
        )

        Pair(info, text)
    }

    /**
     * Layout computation data structure representing split pages.
     */
    private data class PageLayoutPlan(
        val pageW: Int,
        val pageH: Int,
        val margin: Float,
        val pages: List<List<String>>, // List of pages, each with its lines of text
        val titleLines: List<String>,
        val lineHeight: Float,
        val titleLineHeight: Float,
        val bodyPaint: Paint,
        val titlePaint: Paint,
        val footerPaint: Paint
    )

    /**
     * Core layout calculation shared between estimation and actual rendering.
     */
    private fun planLayout(
        title: String,
        text: String,
        options: TxtOptions
    ): PageLayoutPlan {
        val baseW = options.pageSize.widthPt
        val baseH = options.pageSize.heightPt

        val isLandscape = options.orientation == TxtOrientation.LANDSCAPE
        val pageW = if (isLandscape) max(baseW, baseH).toInt() else min(baseW, baseH).toInt()
        val pageH = if (isLandscape) min(baseW, baseH).toInt() else max(baseW, baseH).toInt()

        val margin = options.margin.marginPt
        val usableW = max(100f, pageW - 2 * margin)
        val usableBottom = pageH - margin - (if (options.pageNumbers) 24f else 0f)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = options.fontSize.sizePt
            typeface = options.fontFamily.androidTypeface
            color = Color.rgb(10, 10, 10)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = options.fontSize.sizePt * 1.8f
            typeface = Typeface.create(options.fontFamily.androidTypeface, Typeface.BOLD)
            color = Color.rgb(10, 10, 10)
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            typeface = Typeface.SANS_SERIF
            color = Color.rgb(140, 140, 140)
            textAlign = Paint.Align.CENTER
        }

        val lineHeight = options.fontSize.sizePt * options.lineSpacing.multiplier * 1.25f
        val titleLineHeight = options.fontSize.sizePt * 1.8f * 1.2f

        // Split title into lines if present
        val titleLines = mutableListOf<String>()
        if (title.isNotBlank()) {
            val words = title.trim().split(Regex("\\s+"))
            val cur = StringBuilder()
            for (word in words) {
                val candidate = if (cur.isEmpty()) word else "$cur $word"
                if (titlePaint.measureText(candidate) <= usableW) {
                    if (cur.isNotEmpty()) cur.append(' ')
                    cur.append(word)
                } else {
                    if (cur.isNotEmpty()) {
                        titleLines.add(cur.toString())
                        cur.setLength(0)
                    }
                    // Handle long unbroken word
                    if (titlePaint.measureText(word) > usableW) {
                        breakWord(word, titlePaint, usableW, titleLines)
                    } else {
                        cur.append(word)
                    }
                }
            }
            if (cur.isNotEmpty()) {
                titleLines.add(cur.toString())
            }
        }

        // Split text into paragraphs and wrap lines
        val paragraphs = text.split('\n')
        val allLines = mutableListOf<String>()

        for (para in paragraphs) {
            if (para.isEmpty()) {
                allLines.add("")
                continue
            }

            if (!options.wrapLines) {
                allLines.add(para)
                continue
            }

            // Word-wrap paragraph
            val words = para.split(Regex("(?<=\\s)|(?=\\s)"))
            val curLine = StringBuilder()

            for (token in words) {
                val candidate = curLine.toString() + token
                if (bodyPaint.measureText(candidate) <= usableW) {
                    curLine.append(token)
                } else {
                    if (curLine.isNotEmpty() && curLine.toString().trim().isNotEmpty()) {
                        allLines.add(curLine.toString().trimEnd())
                        curLine.setLength(0)
                    }
                    if (token.isBlank()) {
                        // Skip extra whitespace on line break
                        continue
                    }
                    // If the single token exceeds usable width, break character-by-character
                    if (bodyPaint.measureText(token) > usableW) {
                        breakWord(token, bodyPaint, usableW, allLines)
                    } else {
                        curLine.append(token)
                    }
                }
            }
            if (curLine.isNotEmpty()) {
                allLines.add(curLine.toString().trimEnd())
            }
        }

        // Distribute lines across pages
        val pages = mutableListOf<MutableList<String>>()
        var currentPageLines = mutableListOf<String>()
        pages.add(currentPageLines)

        var y = margin
        if (titleLines.isNotEmpty()) {
            y += titleLines.size * titleLineHeight + options.fontSize.sizePt
        }

        for (line in allLines) {
            if (y + lineHeight > usableBottom) {
                // New page
                currentPageLines = mutableListOf()
                pages.add(currentPageLines)
                y = margin
            }
            currentPageLines.add(line)
            y += lineHeight
        }

        return PageLayoutPlan(
            pageW = pageW,
            pageH = pageH,
            margin = margin,
            pages = pages,
            titleLines = titleLines,
            lineHeight = lineHeight,
            titleLineHeight = titleLineHeight,
            bodyPaint = bodyPaint,
            titlePaint = titlePaint,
            footerPaint = footerPaint
        )
    }

    /**
     * Splits an oversized word character by character so it never overflows margin.
     */
    private fun breakWord(word: String, paint: Paint, maxWidth: Float, output: MutableList<String>) {
        val cur = StringBuilder()
        for (c in word) {
            val candidate = cur.toString() + c
            if (paint.measureText(candidate) <= maxWidth) {
                cur.append(c)
            } else {
                if (cur.isNotEmpty()) {
                    output.add(cur.toString())
                    cur.setLength(0)
                }
                cur.append(c)
            }
        }
        if (cur.isNotEmpty()) {
            output.add(cur.toString())
        }
    }

    /**
     * Fast calculation of stats (words, characters, and estimated pages).
     */
    fun calculateStats(text: String, title: String, options: TxtOptions): TxtStats {
        val raw = text.trim()
        if (raw.isEmpty()) {
            return TxtStats(0, 0, 1)
        }

        val words = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val characters = raw.length

        val plan = planLayout(title, text, options)
        val pagesCount = max(1, plan.pages.size)

        return TxtStats(
            words = words,
            characters = characters,
            estimatedPages = pagesCount
        )
    }

    /**
     * Renders the formatted text into a PDF document and streams the result.
     */
    suspend fun convertTextToPdf(
        context: Context,
        title: String,
        text: String,
        options: TxtOptions,
        outputStream: OutputStream,
        onProgress: (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val (cleanedText, _) = cleanText(text)
        val (cleanedTitle, _) = cleanText(title)

        val plan = planLayout(cleanedTitle, cleanedText, options)
        val totalPages = max(1, plan.pages.size)

        val pdfDocument = PdfDocument()

        try {
            for (p in 0 until totalPages) {
                onProgress(p + 1, totalPages)

                val pageInfo = PdfDocument.PageInfo.Builder(plan.pageW, plan.pageH, p + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Background fill
                canvas.drawColor(Color.WHITE)

                var y = plan.margin

                // Draw title on page 1
                if (p == 0 && plan.titleLines.isNotEmpty()) {
                    for (tLine in plan.titleLines) {
                        y += plan.titlePaint.textSize
                        canvas.drawText(tLine, plan.margin, y, plan.titlePaint)
                        y += plan.titlePaint.textSize * 0.2f
                    }
                    y += options.fontSize.sizePt
                }

                // Draw page lines
                val lines = plan.pages[p]
                for (line in lines) {
                    y += plan.lineHeight
                    if (line.isNotEmpty()) {
                        canvas.drawText(line, plan.margin, y - plan.lineHeight * 0.2f, plan.bodyPaint)
                    }
                }

                // Draw page number footer if enabled
                if (options.pageNumbers) {
                    val footerText = "${p + 1} / $totalPages"
                    val footerY = plan.pageH - plan.margin / 2f + 2f
                    canvas.drawText(footerText, plan.pageW / 2f, footerY, plan.footerPaint)
                }

                pdfDocument.finishPage(page)
            }

            pdfDocument.writeTo(outputStream)
            totalPages
        } finally {
            pdfDocument.close()
        }
    }
}

