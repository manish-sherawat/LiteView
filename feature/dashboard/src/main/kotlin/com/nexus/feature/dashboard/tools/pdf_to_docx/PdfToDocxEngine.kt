package com.nexus.feature.dashboard.tools.pdf_to_docx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object PdfToDocxEngine {

    /**
     * Reads display name, file size, and page count of chosen PDF.
     */
    suspend fun readPdfDocInfo(context: Context, uri: Uri): PdfToDocxDocInfo = withContext(Dispatchers.IO) {
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

        PdfToDocxDocInfo(
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
                    val renderW = targetWidth.coerceIn(120, 1400)
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
     * Extracts text lines and reconstructs structured headings, paragraphs, and bullet points for a page.
     */
    suspend fun extractPageBlocks(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        detectHeadings: Boolean = true,
        mergeLines: Boolean = true
    ): List<WordBlock> = withContext(Dispatchers.IO) {
        val blocks = mutableListOf<WordBlock>()
        var extractedRawText = ""

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.sortByPosition = true
                    extractedRawText = stripper.getText(doc) ?: ""
                }
            }
        } catch (_: Exception) {}

        if (extractedRawText.isBlank()) {
            return@withContext emptyList()
        }

        val lines = extractedRawText.split("\n")
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return@withContext emptyList()
        }

        val bulletRegex = Regex("^([•●▪▸◦–—\\-*]|\\d+[.)]|[a-zA-Z][.)])\\s+")

        fun classify(line: String, index: Int): BlockType {
            if (bulletRegex.containsMatchIn(line)) return BlockType.LI
            if (detectHeadings) {
                val isShort = line.length in 3..70
                val isNotEndingWithPeriod = !line.endsWith(".") && !line.endsWith(",") && !line.endsWith(";")
                val isAllUpper = line.length > 4 && line == line.uppercase() && line.any { it.isLetter() }
                val isTitleCase = line.split(" ").count { it.firstOrNull()?.isUpperCase() == true } >= 2

                if (isShort && (isAllUpper || (isTitleCase && isNotEndingWithPeriod))) {
                    return if (index == 0 || isAllUpper) BlockType.H1 else BlockType.H2
                }
            }
            return BlockType.P
        }

        if (!mergeLines) {
            return@withContext lines.map { line ->
                val type = classify(line, 0)
                val cleanText = if (type == BlockType.LI) line.replace(bulletRegex, "") else line
                WordBlock(type, cleanText)
            }
        }

        // Merge contiguous body lines into flowing paragraphs
        var currentBlockType: BlockType? = null
        var currentParagraph = StringBuilder()

        for ((idx, line) in lines.withIndex()) {
            val type = classify(line, idx)
            val cleanText = if (type == BlockType.LI) line.replace(bulletRegex, "") else line

            if (type == BlockType.H1 || type == BlockType.H2 || type == BlockType.LI) {
                if (currentParagraph.isNotEmpty() && currentBlockType != null) {
                    blocks.add(WordBlock(currentBlockType, currentParagraph.toString().trim()))
                    currentParagraph = StringBuilder()
                }
                blocks.add(WordBlock(type, cleanText))
                currentBlockType = null
            } else {
                // Paragraph merging
                if (currentBlockType == BlockType.P) {
                    val prevEndsWithHyphen = currentParagraph.endsWith("-")
                    if (prevEndsWithHyphen && cleanText.firstOrNull()?.isLowerCase() == true) {
                        currentParagraph.deleteCharAt(currentParagraph.length - 1)
                        currentParagraph.append(cleanText)
                    } else {
                        currentParagraph.append(" ").append(cleanText)
                    }
                } else {
                    if (currentParagraph.isNotEmpty() && currentBlockType != null) {
                        blocks.add(WordBlock(currentBlockType, currentParagraph.toString().trim()))
                    }
                    currentParagraph = StringBuilder(cleanText)
                    currentBlockType = BlockType.P
                }
            }
        }

        if (currentParagraph.isNotEmpty() && currentBlockType != null) {
            blocks.add(WordBlock(currentBlockType, currentParagraph.toString().trim()))
        }

        blocks
    }

    private fun xmlEscape(str: String): String {
        return str
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Generates a fully standards-compliant Microsoft Word OpenXML (.docx) document
     * with text styling, paragraphs, headings, bullet lists, and embedded high-res images.
     */
    suspend fun generateDocx(
        context: Context,
        docInfo: PdfToDocxDocInfo,
        pagesToInclude: List<Int>,
        blocksPerPage: Map<Int, List<WordBlock>>,
        mode: ConversionMode,
        font: WordFontFamily,
        sizePt: Int,
        spacing: WordLineSpacing,
        margin: WordMargin,
        pageBreaks: Boolean,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val sortedPages = pagesToInclude.filter { it in 1..docInfo.pageCount }.sorted()
        if (sortedPages.isEmpty()) {
            throw IllegalStateException("No pages selected for conversion.")
        }

        onProgress(0.05f)

        val fontName = xmlEscape(font.fontName)
        val baseHalfPt = sizePt * 2
        val h1HalfPt = (sizePt * 1.55f).roundToInt() * 2
        val h2HalfPt = (sizePt * 1.25f).roundToInt() * 2
        val lineTwips = (spacing.value * 240).roundToInt()
        val mar = margin.twips

        val mediaFiles = mutableListOf<Pair<String, ByteArray>>()
        val imageRels = mutableListOf<String>()
        val bodyXmlParts = mutableListOf<String>()
        var docPrId = 1

        for ((idx, pageNum) in sortedPages.withIndex()) {
            val blocks = blocksPerPage[pageNum] ?: emptyList()

            // Page Break before subsequent pages if enabled
            if (idx > 0 && pageBreaks) {
                bodyXmlParts.add("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }

            if (mode != ConversionMode.VISUAL) {
                for (b in blocks) {
                    val safeText = xmlEscape(b.text)
                    if (safeText.isBlank()) continue

                    when (b.type) {
                        BlockType.H1 -> {
                            bodyXmlParts.add(
                                """
                                <w:p>
                                  <w:pPr><w:pStyle w:val="Heading1"/><w:spacing w:before="240" w:after="120" w:line="$lineTwips" w:lineRule="auto"/></w:pPr>
                                  <w:r><w:rPr><w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/><w:b/><w:sz w:val="$h1HalfPt"/><w:szCs w:val="$h1HalfPt"/></w:rPr><w:t xml:space="preserve">$safeText</w:t></w:r>
                                </w:p>
                                """.trimIndent()
                            )
                        }
                        BlockType.H2 -> {
                            bodyXmlParts.add(
                                """
                                <w:p>
                                  <w:pPr><w:pStyle w:val="Heading2"/><w:spacing w:before="180" w:after="100" w:line="$lineTwips" w:lineRule="auto"/></w:pPr>
                                  <w:r><w:rPr><w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/><w:b/><w:sz w:val="$h2HalfPt"/><w:szCs w:val="$h2HalfPt"/></w:rPr><w:t xml:space="preserve">$safeText</w:t></w:r>
                                </w:p>
                                """.trimIndent()
                            )
                        }
                        BlockType.LI -> {
                            bodyXmlParts.add(
                                """
                                <w:p>
                                  <w:pPr><w:ind w:left="480" w:hanging="240"/><w:spacing w:after="100" w:line="$lineTwips" w:lineRule="auto"/></w:pPr>
                                  <w:r><w:rPr><w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/><w:sz w:val="$baseHalfPt"/><w:szCs w:val="$baseHalfPt"/></w:rPr><w:t xml:space="preserve">•  $safeText</w:t></w:r>
                                </w:p>
                                """.trimIndent()
                            )
                        }
                        BlockType.P -> {
                            bodyXmlParts.add(
                                """
                                <w:p>
                                  <w:pPr><w:spacing w:after="140" w:line="$lineTwips" w:lineRule="auto"/></w:pPr>
                                  <w:r><w:rPr><w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/><w:sz w:val="$baseHalfPt"/><w:szCs w:val="$baseHalfPt"/></w:rPr><w:t xml:space="preserve">$safeText</w:t></w:r>
                                </w:p>
                                """.trimIndent()
                            )
                        }
                    }
                }
            }

            // Embed high-resolution page snapshot if in 'hybrid' or 'visual' mode, or if page had 0 selectable text
            val needsImage = mode == ConversionMode.VISUAL || mode == ConversionMode.HYBRID || blocks.isEmpty()
            if (needsImage) {
                try {
                    val snapBmp = renderThumbnail(context, docInfo.uri, pageNum - 1, 1200)
                    val stream = ByteArrayOutputStream()
                    snapBmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val imgBytes = stream.toByteArray()

                    val rId = "rIdImg$pageNum"
                    val fileName = "page_$pageNum.jpg"
                    mediaFiles.add("word/media/$fileName" to imgBytes)
                    imageRels.add("<Relationship Id=\"$rId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$fileName\"/>")

                    val availIn = (12240 - mar * 2) / 1440f
                    val cx = (availIn.coerceAtLeast(4.5f) * 914400f).roundToInt()
                    val cy = (cx * (snapBmp.height.toFloat() / snapBmp.width.toFloat())).roundToInt()
                    val idNum = docPrId++

                    bodyXmlParts.add(
                        """
                        <w:p>
                          <w:pPr><w:jc w:val="center"/><w:spacing w:before="120" w:after="120"/></w:pPr>
                          <w:r>
                            <w:drawing>
                              <wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
                                <wp:extent cx="$cx" cy="$cy"/>
                                <wp:docPr id="$idNum" name="Page $pageNum"/>
                                <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                                  <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                    <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                      <pic:nvPicPr>
                                        <pic:cNvPr id="$idNum" name="$fileName"/>
                                        <pic:cNvPicPr/>
                                      </pic:nvPicPr>
                                      <pic:blipFill>
                                        <a:blip r:embed="$rId" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/>
                                        <a:stretch><a:fillRect/></a:stretch>
                                      </pic:blipFill>
                                      <pic:spPr>
                                        <a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                                        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                                      </pic:spPr>
                                    </pic:pic>
                                  </a:graphicData>
                                </a:graphic>
                              </wp:inline>
                            </w:drawing>
                          </w:r>
                        </w:p>
                        """.trimIndent()
                    )
                } catch (_: Exception) {}
            }

            val p = 0.05f + 0.85f * ((idx + 1).toFloat() / sortedPages.size)
            onProgress(p.coerceIn(0.05f, 0.90f))
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val docRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  ${imageRels.joinToString("\n  ")}
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="$fontName" w:hAnsi="$fontName" w:cs="$fontName"/>
        <w:sz w:val="$baseHalfPt"/>
        <w:szCs w:val="$baseHalfPt"/>
        <w:color w:val="0A0A0A"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:rPr><w:b/><w:sz w:val="$h1HalfPt"/><w:szCs w:val="$h1HalfPt"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:next w:val="Normal"/>
    <w:qFormat/>
    <w:rPr><w:b/><w:sz w:val="$h2HalfPt"/><w:szCs w:val="$h2HalfPt"/></w:rPr>
  </w:style>
</w:styles>"""

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
    ${bodyXmlParts.joinToString("\n    ")}
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="$mar" w:right="$mar" w:bottom="$mar" w:left="$mar" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""

        ZipOutputStream(outputStream).use { zos ->
            fun addZipEntry(entryName: String, data: ByteArray) {
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }

            addZipEntry("[Content_Types].xml", contentTypesXml.toByteArray(Charsets.UTF_8))
            addZipEntry("_rels/.rels", rootRelsXml.toByteArray(Charsets.UTF_8))
            addZipEntry("word/_rels/document.xml.rels", docRelsXml.toByteArray(Charsets.UTF_8))
            addZipEntry("word/styles.xml", stylesXml.toByteArray(Charsets.UTF_8))
            addZipEntry("word/document.xml", documentXml.toByteArray(Charsets.UTF_8))

            for ((path, bytes) in mediaFiles) {
                addZipEntry(path, bytes)
            }
        }

        onProgress(1f)
        sortedPages.size
    }

    /**
     * Generates a DOCX file directly to a destination SAF [Uri].
     */
    suspend fun generateDocx(
        context: Context,
        docInfo: PdfToDocxDocInfo,
        pagesToInclude: List<Int>,
        blocksPerPage: Map<Int, List<WordBlock>>,
        mode: ConversionMode,
        font: WordFontFamily,
        sizePt: Int,
        spacing: WordLineSpacing,
        margin: WordMargin,
        pageBreaks: Boolean,
        destUri: Uri,
        outputFileName: String,
        onProgress: (Float) -> Unit
    ): PdfToDocxExportResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(destUri)
            ?: throw IllegalStateException("Cannot open destination file.")
        val count = stream.use { out ->
            generateDocx(context, docInfo, pagesToInclude, blocksPerPage, mode, font, sizePt, spacing, margin, pageBreaks, out, onProgress)
        }
        PdfToDocxExportResult(
            outputFile = null,
            outputUri = destUri,
            outputFileName = outputFileName,
            pagesConvertedCount = count,
            fileSizeBytes = 0L,
            isTxt = false
        )
    }

    /**
     * Generates a DOCX file and saves to Downloads.
     */
    suspend fun generateDocx(
        context: Context,
        docInfo: PdfToDocxDocInfo,
        pagesToInclude: List<Int>,
        blocksPerPage: Map<Int, List<WordBlock>>,
        mode: ConversionMode,
        font: WordFontFamily,
        sizePt: Int,
        spacing: WordLineSpacing,
        margin: WordMargin,
        pageBreaks: Boolean,
        onProgress: (Float) -> Unit
    ): PdfToDocxExportResult = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var outFile = File(downloadsDir, "${docInfo.baseName}.docx")
        var counter = 1
        while (outFile.exists() && outFile.length() > 0) {
            val candidate = File(downloadsDir, "${docInfo.baseName}($counter).docx")
            if (!candidate.exists()) {
                outFile = candidate
                break
            }
            counter++
        }

        val count = FileOutputStream(outFile).use { fos ->
            generateDocx(context, docInfo, pagesToInclude, blocksPerPage, mode, font, sizePt, spacing, margin, pageBreaks, fos, onProgress)
        }

        PdfToDocxExportResult(
            outputFile = outFile,
            outputUri = null,
            outputFileName = outFile.name,
            pagesConvertedCount = count,
            fileSizeBytes = outFile.length(),
            isTxt = false
        )
    }

    /**
     * Exports selected pages as plain text (.txt) directly to an [OutputStream].
     */
    suspend fun exportPlainText(
        context: Context,
        docInfo: PdfToDocxDocInfo,
        pagesToInclude: List<Int>,
        blocksPerPage: Map<Int, List<WordBlock>>,
        outputStream: OutputStream
    ): Int = withContext(Dispatchers.IO) {
        val sortedPages = pagesToInclude.filter { it in 1..docInfo.pageCount }.sorted()
        val textBuilder = StringBuilder()

        for (p in sortedPages) {
            val blocks = blocksPerPage[p] ?: emptyList()
            textBuilder.append("--- Page $p ---\n")
            if (blocks.isEmpty()) {
                textBuilder.append("[Scanned Page / No Selectable Text]\n\n")
            } else {
                for (b in blocks) {
                    if (b.type == BlockType.LI) {
                        textBuilder.append("• ").append(b.text).append("\n")
                    } else {
                        textBuilder.append(b.text).append("\n\n")
                    }
                }
            }
            textBuilder.append("\n")
        }

        outputStream.write(textBuilder.toString().toByteArray(Charsets.UTF_8))
        sortedPages.size
    }

    /**
     * Exports plain text directly to a destination SAF [Uri].
     */
    suspend fun exportPlainText(
        context: Context,
        docInfo: PdfToDocxDocInfo,
        pagesToInclude: List<Int>,
        blocksPerPage: Map<Int, List<WordBlock>>,
        destUri: Uri,
        outputFileName: String
    ): PdfToDocxExportResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(destUri)
            ?: throw IllegalStateException("Cannot open destination file.")
        val count = stream.use { out ->
            exportPlainText(context, docInfo, pagesToInclude, blocksPerPage, out)
        }
        PdfToDocxExportResult(
            outputFile = null,
            outputUri = destUri,
            outputFileName = outputFileName,
            pagesConvertedCount = count,
            fileSizeBytes = 0L,
            isTxt = true
        )
    }

    /**
     * Exports selected pages as plain text (.txt) to Downloads.
     */
    suspend fun exportPlainText(
        context: Context,
        docInfo: PdfToDocxDocInfo,
        pagesToInclude: List<Int>,
        blocksPerPage: Map<Int, List<WordBlock>>
    ): PdfToDocxExportResult = withContext(Dispatchers.IO) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var outFile = File(downloadsDir, "${docInfo.baseName}.txt")
        var counter = 1
        while (outFile.exists() && outFile.length() > 0) {
            val candidate = File(downloadsDir, "${docInfo.baseName}($counter).txt")
            if (!candidate.exists()) {
                outFile = candidate
                break
            }
            counter++
        }

        val count = FileOutputStream(outFile).use { fos ->
            exportPlainText(context, docInfo, pagesToInclude, blocksPerPage, fos)
        }

        PdfToDocxExportResult(
            outputFile = outFile,
            outputUri = null,
            outputFileName = outFile.name,
            pagesConvertedCount = count,
            fileSizeBytes = outFile.length(),
            isTxt = true
        )
    }

    /**
     * Parses page numbers and ranges (e.g. "1-5, 8").
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

