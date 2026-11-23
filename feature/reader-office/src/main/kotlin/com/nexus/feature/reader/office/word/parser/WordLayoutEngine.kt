package com.nexus.feature.reader.office.word.parser

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableRow
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType

enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Standard paper size definition in typographic points (1/72 inch).
 * 1 twip = 1/20 pt. 1 mm ≈ 2.8346 pt.
 */
data class PaperSize(
    val name: String,
    val widthPt: Float,
    val heightPt: Float,
    val widthMm: Float,
    val heightMm: Float
) {
    companion object {
        val A4 = PaperSize("A4", 595.28f, 841.89f, 210f, 297f)
        val LETTER = PaperSize("Letter", 612.0f, 792.0f, 215.9f, 279.4f)
        val LEGAL = PaperSize("Legal", 612.0f, 1008.0f, 215.9f, 355.6f)
        val EXECUTIVE = PaperSize("Executive", 522.0f, 756.0f, 184.1f, 266.7f)
        val A3 = PaperSize("A3", 841.89f, 1190.55f, 297f, 420f)
        val A5 = PaperSize("A5", 419.53f, 595.28f, 148f, 210f)
        val B5 = PaperSize("B5", 498.90f, 708.66f, 176f, 250f)

        val ALL = listOf(A4, LETTER, LEGAL, EXECUTIVE, A3, A5, B5)

        fun matchClosest(widthPt: Float, heightPt: Float): PaperSize {
            val minSide = minOf(widthPt, heightPt)
            val maxSide = maxOf(widthPt, heightPt)
            return ALL.minByOrNull {
                val sMin = minOf(it.widthPt, it.heightPt)
                val sMax = maxOf(it.widthPt, it.heightPt)
                kotlin.math.abs(sMin - minSide) + kotlin.math.abs(sMax - maxSide)
            } ?: A4
        }
    }
}

data class DocumentMargins(
    val topPt: Float = 54f,       // ~0.75 in default
    val bottomPt: Float = 54f,
    val leftPt: Float = 54f,
    val rightPt: Float = 54f,
    val headerPt: Float = 36f,
    val footerPt: Float = 36f
) {
    fun summary(): String {
        return "Top ${topPt.toInt()}pt · Bottom ${bottomPt.toInt()}pt · Left ${leftPt.toInt()}pt · Right ${rightPt.toInt()}pt"
    }
}

/**
 * Encapsulates the calculated page boundaries and printable area for Word layout.
 */
data class PageSetup(
    val paperSize: PaperSize,
    val widthPt: Float,
    val heightPt: Float,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val margins: DocumentMargins = DocumentMargins()
) {
    val printableWidthPt: Float
        get() = (widthPt - margins.leftPt - margins.rightPt).coerceIn(240f, 1200f)

    val printableHeightPt: Float
        get() = (heightPt - margins.topPt - margins.bottomPt - margins.headerPt - margins.footerPt).coerceIn(300f, 1600f)

    val aspectRatio: Float
        get() = if (heightPt > 0) widthPt / heightPt else 0.707f
}

object WordLayoutEngine {

    /**
     * Extracts page setup (paper size, orientation, margins) from document sectPr.
     */
    fun extractPageSetup(doc: XWPFDocument): PageSetup {
        return try {
            val sectPr = doc.document.body?.sectPr
            var w = PaperSize.A4.widthPt
            var h = PaperSize.A4.heightPt
            var orient = PageOrientation.PORTRAIT

            val pgSz = sectPr?.pgSz
            if (pgSz != null) {
                val rawW = pgSz.w?.toString()?.toFloatOrNull()
                val rawH = pgSz.h?.toString()?.toFloatOrNull()
                if (rawW != null && rawW > 0) w = rawW / 20f
                if (rawH != null && rawH > 0) h = rawH / 20f

                val orientStr = pgSz.orient?.toString()?.lowercase() ?: ""
                if (orientStr.contains("landscape") || w > h) {
                    orient = PageOrientation.LANDSCAPE
                    if (w < h) {
                        val temp = w
                        w = h
                        h = temp
                    }
                }
            }

            val pgMar = sectPr?.pgMar
            val margins = if (pgMar != null) {
                val top = (pgMar.top?.toString()?.toFloatOrNull() ?: 1080f) / 20f
                val bottom = (pgMar.bottom?.toString()?.toFloatOrNull() ?: 1080f) / 20f
                val left = (pgMar.left?.toString()?.toFloatOrNull() ?: 1080f) / 20f
                val right = (pgMar.right?.toString()?.toFloatOrNull() ?: 1080f) / 20f
                val header = (pgMar.header?.toString()?.toFloatOrNull() ?: 720f) / 20f
                val footer = (pgMar.footer?.toString()?.toFloatOrNull() ?: 720f) / 20f
                DocumentMargins(
                    topPt = top.coerceIn(18f, 90f),
                    bottomPt = bottom.coerceIn(18f, 90f),
                    leftPt = left.coerceIn(18f, 90f),
                    rightPt = right.coerceIn(18f, 90f),
                    headerPt = header.coerceIn(10f, 40f),
                    footerPt = footer.coerceIn(10f, 40f)
                )
            } else {
                DocumentMargins()
            }

            val matchedPaper = PaperSize.matchClosest(w, h)
            PageSetup(
                paperSize = matchedPaper,
                widthPt = w,
                heightPt = h,
                orientation = orient,
                margins = margins
            )
        } catch (_: Exception) {
            PageSetup(
                paperSize = PaperSize.A4,
                widthPt = PaperSize.A4.widthPt,
                heightPt = PaperSize.A4.heightPt,
                orientation = PageOrientation.PORTRAIT,
                margins = DocumentMargins()
            )
        }
    }

    fun extractPageMargins(doc: XWPFDocument): DocumentMargins {
        return extractPageSetup(doc).margins
    }

    /**
     * Checks if a paragraph explicitly triggers a page break (manual page break, section break, or form feed).
     */
    fun hasExplicitPageBreak(para: XWPFParagraph): Boolean {
        try {
            if (para.isPageBreak) return true

            // Check if paragraph ends a section (sectPr on pPr)
            val pPr = try { para.ctp.pPr } catch (_: Exception) { null }
            if (pPr?.sectPr != null) {
                return true
            }

            // Check runs for page break elements or form feed
            for (run in para.runs) {
                val ctr = run.ctr
                if (ctr != null) {
                    for (br in ctr.brList) {
                        if (br.type == STBrType.PAGE) return true
                    }
                    if (ctr.lastRenderedPageBreakList.isNotEmpty()) {
                        return true
                    }
                }
                val text = run.text() ?: ""
                if (text.contains('\u000C')) return true
            }

            // Check raw XML node for page breaks
            val domNode = para.ctp.domNode
            val childNodes = domNode.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                val localName = (node.localName ?: node.nodeName.substringAfterLast(':')).lowercase()
                if (localName == "br" && node.attributes?.getNamedItem("w:type")?.nodeValue == "page") {
                    return true
                }
                if (localName == "lastrenderedpagebreak") {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Accurately estimates the typographic height (in points) of a paragraph when rendered,
     * accounting for font size, embedded pictures, explicit line breaks, line height, margins,
     * and subtracting left indent and hanging indent from available width for accurate wrapping.
     */
    fun estimateParagraphHeight(para: XWPFParagraph, printableWidthPt: Float): Float {
        val styleId = try { para.style ?: "" } catch (_: Exception) { "" }
        val isHeading = styleId.lowercase().startsWith("heading")
        if (isHeading) {
            val level = styleId.filter { it.isDigit() }.firstOrNull()?.digitToInt()?.coerceIn(1, 6) ?: 1
            return when (level) {
                1 -> 68f
                2 -> 52f
                3 -> 42f
                else -> 36f
            }
        }

        // Account for embedded pictures inside paragraph runs
        var pictureHeight = 0f
        for (run in para.runs) {
            val picCount = try { run.embeddedPictures.size } catch (_: Exception) { 0 }
            if (picCount > 0) {
                pictureHeight += (picCount * 220f)
            }
        }

        val text = para.text ?: ""
        if (text.isBlank() && pictureHeight > 0f) {
            return pictureHeight + 16f
        }
        if (text.isBlank()) return 18f

        // Extract paragraph indents: left, right, hanging, and first line
        val rawIndentLeft = try { para.indentationLeft } catch (_: Exception) { -1 }
        val rawIndentRight = try { para.indentationRight } catch (_: Exception) { -1 }
        val rawIndentHanging = try { para.indentationHanging } catch (_: Exception) { -1 }
        val rawIndentFirstLine = try { para.indentationFirstLine } catch (_: Exception) { -1 }

        val leftIndentPt = if (rawIndentLeft > 0) (rawIndentLeft / 20f).coerceIn(0f, 180f) else 0f
        val rightIndentPt = if (rawIndentRight > 0) (rawIndentRight / 20f).coerceIn(0f, 120f) else 0f
        val hangingIndentPt = if (rawIndentHanging > 0) (rawIndentHanging / 20f).coerceIn(0f, 100f) else 0f
        val firstLineIndentPt = if (rawIndentFirstLine > 0) (rawIndentFirstLine / 20f).coerceIn(0f, 100f) else 0f

        // Check if list item
        val isList = try { para.numID != null } catch (_: Exception) { false }
        val listLevel = try { para.numIlvl?.toInt() ?: 0 } catch (_: Exception) { 0 }
        val listIndentPt = if (isList) ((listLevel + 1) * 16f).coerceIn(16f, 140f) else 0f

        // Available width for line wrapping: subtract left indent and hanging indent so justified text never clips
        val totalDeductionPt = leftIndentPt + rightIndentPt + hangingIndentPt + listIndentPt
        val availableWrappingWidthPt = (printableWidthPt - totalDeductionPt).coerceIn(100f, printableWidthPt)

        // Determine effective font size from runs
        var maxFontSize = 12f
        for (run in para.runs) {
            val fs = try { run.fontSizeAsDouble?.toFloat() } catch (_: Exception) { null }
            if (fs != null && fs > maxFontSize) maxFontSize = fs
        }

        // Estimate characters per line based on font size and actual available wrapping width
        val avgCharWidth = maxFontSize * 0.52f
        val charsPerLine = (availableWrappingWidthPt / avgCharWidth).toInt().coerceIn(12, 110)

        // Count explicit line breaks (<w:br/>) within paragraph runs
        var explicitLineBreaks = 0
        for (run in para.runs) {
            explicitLineBreaks += try { run.ctr?.brList?.size ?: 0 } catch (_: Exception) { 0 }
            val runText = run.text() ?: ""
            explicitLineBreaks += runText.count { it == '\n' || it == '\r' }
        }

        val wrappedLines = Math.max(1, Math.ceil(text.length.toDouble() / charsPerLine).toInt())
        val totalLines = wrappedLines + explicitLineBreaks

        // Handle POI's -1 default for unset spacing
        val rawSpacingBefore = try { para.spacingBefore.toDouble() } catch (_: Exception) { -1.0 }
        val rawSpacingAfter = try { para.spacingAfter.toDouble() } catch (_: Exception) { -1.0 }

        val spacingBeforePt = if (rawSpacingBefore > 0) (rawSpacingBefore / 20.0).coerceIn(0.0, 36.0).toFloat() else 0f
        // Enforce proper paragraph spacing (spacing after) on list items
        val spacingAfterPt = if (isList) {
            if (rawSpacingAfter > 0) (rawSpacingAfter / 20.0).coerceIn(3.0, 36.0).toFloat() else 4f
        } else {
            if (rawSpacingAfter > 0) (rawSpacingAfter / 20.0).coerceIn(0.0, 36.0).toFloat() else 6f
        }

        val rawSpacingBetween = try { para.spacingBetween.toDouble() } catch (_: Exception) { -1.0 }
        val lineMultiplier = if (rawSpacingBetween > 0) {
            (rawSpacingBetween / 240.0).coerceIn(1.15, 2.2).toFloat()
        } else 1.25f

        val linePx = maxFontSize * lineMultiplier * 1.25f

        return (totalLines * linePx) + spacingBeforePt + spacingAfterPt + pictureHeight
    }

    /**
     * Estimates the height of a single table row.
     */
    fun estimateTableRowHeight(row: XWPFTableRow, printableWidthPt: Float): Float {
        val cellCount = row.tableCells.size.coerceAtLeast(1)
        val cellWidthPt = printableWidthPt / cellCount
        var maxLines = 1

        for (cell in row.tableCells) {
            val text = cell.text ?: ""
            val cellCharsPerLine = (cellWidthPt / 6.2f).toInt().coerceIn(10, 80)
            val lines = Math.max(1, Math.ceil(text.length.toDouble() / cellCharsPerLine).toInt())
            if (lines > maxLines) maxLines = lines
        }

        return (maxLines * 18f) + 16f
    }

    /**
     * Estimates the full table height.
     */
    fun estimateTableHeight(table: XWPFTable, printableWidthPt: Float): Float {
        var total = 28f // outer margins
        for (row in table.rows) {
            total += estimateTableRowHeight(row, printableWidthPt)
        }
        return total
    }

    /**
     * Estimates the typographic height of a diagram visual block.
     */
    fun estimateDiagramHeight(linesCount: Int): Float {
        return 30f + 28f + (linesCount * 17.5f)
    }

    /**
     * Generates complete CSS styles for the Word viewer layout engine, enforcing strict
     * bounds checking, responsive page margin scaling on mobile, table responsiveness,
     * and preventing text from stacking vertically one letter per line.
     */
    fun generateStylesheet(setup: PageSetup): String {
        val margins = setup.margins
        return """
<style>
  :root {
    --page-width: ${setup.widthPt}pt;
    --page-height: ${setup.heightPt}pt;
    --page-aspect-ratio: ${setup.aspectRatio};
    --doc-margin-top: ${margins.topPt}pt;
    --doc-margin-bottom: ${margins.bottomPt}pt;
    --doc-margin-left: ${margins.leftPt}pt;
    --doc-margin-right: ${margins.rightPt}pt;
    --primary-color: #2563eb;
    --primary-light: rgba(37, 99, 235, 0.08);
    --border-color: rgba(148, 163, 184, 0.35);
    --text-color: #1e293b;
    --text-muted: #64748b;
    --bg-page: #ffffff;
    --bg-canvas: #f1f5f9;
    --page-shadow: 0 8px 30px rgba(0, 0, 0, 0.12), 0 2px 6px rgba(0, 0, 0, 0.05);
    --page-border: 1px solid rgba(203, 213, 225, 0.8);
  }

  /* Universal Box-Sizing & Min-Width Constraint */
  *, *::before, *::after {
    box-sizing: border-box !important;
    min-width: 0;
  }

  html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    max-width: 100%;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    font-size: 15px;
    line-height: 1.65;
    color: var(--text-color);
    background-color: var(--bg-canvas);
    writing-mode: horizontal-tb !important;
    text-orientation: mixed !important;
    word-wrap: normal;
    overflow-wrap: break-word;
    word-break: normal;
    -webkit-font-smoothing: antialiased;
    -webkit-text-size-adjust: 100%;
  }

  /* Structural Block Containers */
  h1, h2, h3, h4, h5, h6, p, li, blockquote, div, .docx-paragraph, .docx-list-item {
    min-width: 0;
    max-width: 100% !important;
    box-sizing: border-box !important;
    word-break: normal !important;
    overflow-wrap: break-word !important;
    white-space: normal;
    writing-mode: horizontal-tb !important;
    text-orientation: mixed !important;
  }

  /* Inline Text Runs - Strict Inline Direction & Flow (Never stack vertically) */
  span, .docx-run, a, .docx-hyperlink, em, strong, u, s, sub, sup {
    display: inline !important;
    writing-mode: horizontal-tb !important;
    text-orientation: mixed !important;
    direction: inherit !important;
    unicode-bidi: isolate;
    white-space: normal;
    word-break: normal !important;
    overflow-wrap: break-word !important;
    max-width: none !important;
    min-width: auto !important;
    vertical-align: baseline;
  }

  pre, code, .diagram-code-content {
    white-space: pre !important;
    overflow-x: auto !important;
    word-break: normal !important;
  }

  /* ══════════════════════════════════════════
     PAGED VIEW MODE (Standard Paper Size Pages)
     ══════════════════════════════════════════ */
  body.mode-paged {
    background-color: var(--bg-canvas) !important;
    padding: 20px 12px 120px 12px;
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
  }

  body.mode-paged .docx-paged-container {
    width: 100%;
    max-width: var(--page-width);
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  body.mode-paged .docx-page {
    width: 100%;
    max-width: var(--page-width);
    min-width: 0;
    min-height: var(--page-height);
    margin: 18px auto 28px auto;
    background-color: var(--bg-page);
    box-shadow: var(--page-shadow);
    border: var(--page-border);
    border-radius: 4px;
    padding: var(--doc-margin-top) var(--doc-margin-right) var(--doc-margin-bottom) var(--doc-margin-left);
    box-sizing: border-box;
    position: relative;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    overflow: hidden; /* Constrains shading, borders, and nested containers strictly within page boundaries */
    break-after: page;
    page-break-after: always;
  }

  body.mode-paged .docx-page-content {
    flex: 1 1 auto;
    display: block;
    width: 100%;
    min-width: 0;
    max-width: 100%;
    box-sizing: border-box;
    overflow-wrap: break-word;
    word-break: normal;
  }

  body.mode-paged .docx-page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid var(--border-color);
    padding-bottom: 8px;
    margin-bottom: 18px;
    font-size: 11px;
    color: var(--text-muted);
    font-weight: 500;
    user-select: none;
    width: 100%;
  }

  body.mode-paged .docx-page-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-top: 1px solid var(--border-color);
    padding-top: 10px;
    margin-top: 24px;
    font-size: 11px;
    color: var(--text-muted);
    font-weight: 500;
    user-select: none;
    width: 100%;
  }

  /* ══════════════════════════════════════════
     RESPONSIVE BOUNDS FOR MOBILE CANVASES (< 768px)
     Full-Width Web Reading Experience
     ══════════════════════════════════════════ */
  @media screen and (max-width: 768px) {
    :root {
      --doc-margin-left: 16px !important;
      --doc-margin-right: 16px !important;
      --doc-margin-top: 18px !important;
      --doc-margin-bottom: 20px !important;
    }
    body.mode-paged {
      padding: 0 0 100px 0 !important;
      background-color: var(--bg-page) !important;
      width: 100% !important;
    }
    body.mode-paged .docx-paged-container {
      width: 100% !important;
      max-width: 100% !important;
      padding: 0 !important;
      margin: 0 !important;
    }
    body.mode-paged .docx-page {
      width: 100% !important;
      max-width: 100% !important;
      min-height: auto !important;
      margin: 0 !important;
      border-radius: 0 !important;
      border: none !important;
      border-bottom: 1px solid var(--border-color) !important;
      box-shadow: none !important;
      padding: 20px 16px 28px 16px !important;
      box-sizing: border-box !important;
    }
    body.mode-paged .docx-page:last-child {
      border-bottom: none !important;
    }
  }

  /* ══════════════════════════════════════════
     REFLOW / MOBILE CONTINUOUS VIEW MODE
     ══════════════════════════════════════════ */
  body.mode-reflow {
    background-color: var(--bg-canvas) !important;
    padding: 12px 14px 120px 14px;
    display: block;
    width: 100%;
  }

  body.mode-reflow .docx-paged-container {
    width: 100%;
    max-width: 100%;
    margin: 0;
    display: block;
  }

  body.mode-reflow .docx-page {
    width: 100% !important;
    max-width: 100% !important;
    min-height: auto !important;
    margin: 0 !important;
    padding: 8px 2px !important;
    background-color: transparent !important;
    box-shadow: none !important;
    border: none !important;
    border-radius: 0 !important;
    display: block !important;
  }

  body.mode-reflow .docx-page + .docx-page {
    border-top: 1.5px dashed rgba(148, 163, 184, 0.45) !important;
    margin-top: 24px !important;
    padding-top: 20px !important;
  }

  body.mode-reflow .docx-page-header {
    display: none;
  }

  body.mode-reflow .docx-page-footer {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    border-top: none;
    padding-top: 8px;
    font-size: 11px;
    color: var(--text-muted);
    opacity: 0.75;
  }

  /* ── Typography & Structural Styles ── */
  h1, h2, h3, h4, h5, h6 {
    color: #0f172a;
    font-weight: 700;
    line-height: 1.3;
    margin-top: 1.25em;
    margin-bottom: 0.4em;
    page-break-after: avoid;
    break-after: avoid;
  }
  h1 {
    font-size: 1.8em;
    letter-spacing: -0.02em;
    padding: 9px 14px;
    border-left: 5px solid var(--primary-color);
    background: var(--primary-light);
    border-radius: 6px;
    margin-top: 1.3em;
  }
  h2 {
    font-size: 1.4em;
    letter-spacing: -0.015em;
    padding: 7px 12px;
    border-left: 4px solid rgba(37, 99, 235, 0.7);
    background: rgba(37, 99, 235, 0.04);
    border-radius: 5px;
    margin-top: 1.15em;
  }
  h3 { font-size: 1.22em; color: #1e40af; margin-top: 1.0em; }
  h4 { font-size: 1.08em; color: #2563eb; margin-top: 0.9em; }
  h5 { font-size: 0.95em; text-transform: uppercase; letter-spacing: 0.05em; color: #475569; margin-top: 0.8em; }
  h6 { font-size: 0.85em; text-transform: uppercase; letter-spacing: 0.06em; color: #64748b; margin-top: 0.7em; }

  p {
    margin: 0.45em 0;
    line-height: 1.65;
  }

  blockquote {
    margin: 16px 0;
    padding: 10px 16px;
    border-left: 4px solid var(--primary-color);
    background: var(--primary-light);
    border-radius: 0 8px 8px 0;
    font-style: italic;
    color: #334155;
  }

  /* ── Visual Diagram Blocks ── */
  .diagram-visual-block {
    margin: 20px 0;
    background: #f8fafc;
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    break-inside: avoid;
    page-break-inside: avoid;
  }
  .diagram-header-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 7px 14px;
    background: rgba(37, 99, 235, 0.08);
    border-bottom: 1px solid rgba(37, 99, 235, 0.16);
    font-size: 11.5px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.6px;
    color: #2563eb;
  }
  .diagram-badge {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .diagram-controls {
    display: inline-flex;
    gap: 4px;
    background: rgba(0, 0, 0, 0.05);
    padding: 2px;
    border-radius: 4px;
  }
  .diag-tab-btn {
    border: none;
    background: transparent;
    padding: 2px 8px;
    font-size: 11px;
    font-weight: 600;
    color: var(--text-muted);
    border-radius: 3px;
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .diag-tab-btn.active {
    background: white;
    color: var(--primary-color);
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }
  .diagram-code-content {
    margin: 0;
    padding: 15px 18px;
    font-family: 'Cascadia Code', 'Fira Code', 'Courier New', Consolas, monospace !important;
    font-size: 13px !important;
    line-height: 1.25 !important;
    letter-spacing: 0px !important;
    white-space: pre !important;
    overflow-x: auto;
    color: #0f172a !important;
    tab-size: 4;
  }

  /* ── Inline Citation Tags ── */
  .inline-citation {
    display: inline-flex;
    align-items: center;
    font-size: 0.82em;
    font-weight: 600;
    line-height: 1.2;
    color: var(--primary-color);
    background-color: var(--primary-light);
    border: 1px solid rgba(37, 99, 235, 0.28);
    border-radius: 4px;
    padding: 1px 5px;
    margin: 0 2px;
    vertical-align: baseline;
    position: relative;
    top: -0.12em;
    user-select: all;
    transition: background 0.15s ease, border-color 0.15s ease;
    cursor: pointer;
  }
  .inline-citation:hover {
    background-color: rgba(37, 99, 235, 0.16);
    border-color: var(--primary-color);
  }
  .citation-bracket {
    opacity: 0.55;
    font-size: 0.9em;
  }

  /* ── Mathematical Notation ── */
  .math-formula {
    font-family: "Cambria Math", "Latin Modern Math", "STIX Two Math", "KaTeX_Math", Georgia, serif;
    font-style: normal;
    color: #1e3a8a;
  }
  .math-block {
    margin: 16px 0;
    padding: 12px 18px;
    text-align: center;
    background: rgba(37, 99, 235, 0.04);
    border: 1px solid rgba(37, 99, 235, 0.12);
    border-radius: 8px;
    overflow-x: auto;
    break-inside: avoid;
    page-break-inside: avoid;
  }
  .math-fraction {
    display: inline-flex;
    flex-direction: column;
    vertical-align: middle;
    text-align: center;
    padding: 0 4px;
    font-size: 0.95em;
  }
  .math-num {
    border-bottom: 1.5px solid currentColor;
    padding-bottom: 2px;
    line-height: 1.1;
  }
  .math-den {
    padding-top: 2px;
    line-height: 1.1;
  }
  .math-radical {
    display: inline-flex;
    align-items: baseline;
    padding: 0 2px;
  }
  .math-deg {
    font-size: 0.75em;
    margin-right: -2px;
    position: relative;
    top: -0.2em;
  }
  .math-sqrt-sym {
    font-size: 1.15em;
    line-height: 1;
    margin-right: 1px;
  }
  .math-radicand {
    border-top: 1.5px solid currentColor;
    padding: 0 3px;
    display: inline-block;
  }
  .math-base { display: inline-block; }
  .math-sup { font-size: 0.78em; line-height: 1; vertical-align: super; }
  .math-sub { font-size: 0.78em; line-height: 1; vertical-align: sub; }
  .math-nary { display: inline-flex; align-items: center; margin: 0 4px; }
  .math-nary-op { font-size: 1.4em; line-height: 1; font-weight: 500; }
  .math-nary-limits { display: inline-flex; flex-direction: column; font-size: 0.75em; line-height: 1; margin: 0 2px; }
  .math-delim { font-size: 1.1em; font-weight: 500; }
  .math-func-name { font-style: normal; font-weight: 600; margin-right: 3px; }

  /* ── Responsive Tables & Safe Horizontal Flow ── */
  .table-container {
    width: 100%;
    max-width: 100%;
    overflow-x: auto !important;
    -webkit-overflow-scrolling: touch;
    margin: 16px 0;
    box-sizing: border-box !important;
    border-radius: 8px;
    border: 1px solid var(--border-color);
  }
  table, .docx-table {
    width: 100%;
    min-width: 100%;
    max-width: none !important; /* Never clamp to 100% inside overflow-x: auto, preventing vertical column squishing */
    border-collapse: collapse;
    table-layout: auto;
    font-size: 0.9em;
    margin: 0;
    display: table !important;
    writing-mode: horizontal-tb !important;
    direction: inherit !important;
  }
  thead tr, tr:first-child {
    background: var(--primary-light);
  }
  th {
    padding: 9px 12px;
    text-align: left;
    font-weight: 600;
    border: 1px solid var(--border-color);
    color: #1e3a8a;
    min-width: 64px;
    box-sizing: border-box !important;
    white-space: normal !important;
    word-break: normal !important;
    overflow-wrap: normal !important; /* Prevents Chromium table engine from collapsing min-content to 1 character */
    word-wrap: normal !important;
    writing-mode: horizontal-tb !important;
    direction: inherit !important;
  }
  td {
    padding: 8px 12px;
    border: 1px solid var(--border-color);
    vertical-align: top;
    min-width: 64px;
    box-sizing: border-box !important;
    white-space: normal !important;
    word-break: normal !important;
    overflow-wrap: normal !important; /* Prevents Chromium table engine from collapsing min-content to 1 character */
    word-wrap: normal !important;
    writing-mode: horizontal-tb !important;
    direction: inherit !important;
  }
  tr:nth-child(even) td { background: rgba(0, 0, 0, 0.02); }
  tr:hover td { background: rgba(37, 99, 235, 0.04); }

  /* ── Images & Figures (Strict Page Boundaries & Safe Auto-Scaling) ── */
  .docx-figure-container {
    width: 100% !important;
    max-width: 100% !important;
    box-sizing: border-box !important;
    margin: 14px auto;
    text-align: center;
    overflow: hidden;
    break-inside: avoid;
    page-break-inside: avoid;
  }
  img, .docx-image {
    max-width: 100% !important;
    max-height: calc(var(--page-height, 842pt) - var(--doc-margin-top, 54pt) - var(--doc-margin-bottom, 54pt) - 60pt) !important;
    height: auto !important;
    width: auto;
    object-fit: contain;
    object-position: center;
    border-radius: 6px;
    margin: 10px auto;
    display: block;
    box-sizing: border-box !important;
    overflow: hidden;
    break-inside: avoid;
    page-break-inside: avoid;
  }
  .docx-image-frame {
    display: inline-block;
    max-width: 100% !important;
    box-sizing: border-box !important;
    vertical-align: middle;
    margin: 4px 0;
    overflow: hidden;
  }
  .docx-image-frame img, .docx-inline-image {
    display: inline-block;
    vertical-align: middle;
    max-width: 100% !important;
    max-height: 80vh;
    height: auto !important;
    object-fit: contain;
    box-sizing: border-box !important;
    border-radius: 4px;
    overflow: hidden;
  }
  a, .docx-hyperlink, a span, a strong, a em, a u, a s {
    color: var(--primary-color) !important;
    text-decoration: underline;
    font-size: inherit !important;
    line-height: inherit !important;
    letter-spacing: inherit !important;
    font-family: inherit;
    word-break: normal;
    overflow-wrap: break-word;
  }
  .docx-list-item {
    margin-top: 0;
    margin-bottom: 4.5pt !important;
    box-sizing: border-box !important;
    max-width: 100% !important;
  }
  hr {
    border: none;
    border-top: 1px solid var(--border-color);
    margin: 20px 0;
  }

  /* ── Footnotes & Citations Section ── */
  .docx-footnotes-section {
    margin-top: 36px;
    padding-top: 14px;
    border-top: 1.5px solid var(--border-color);
    break-inside: avoid;
    page-break-inside: avoid;
  }
  .docx-footnotes-divider {
    width: 60px;
    height: 2px;
    background: var(--primary-color);
    margin-bottom: 12px;
    border-radius: 1px;
  }
  .docx-footnotes-header {
    margin-bottom: 10px;
  }
  .docx-footnotes-title {
    font-size: 12px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    color: var(--text-muted);
  }
  .docx-footnotes-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .docx-footnote-item {
    font-size: 12.5px;
    line-height: 1.5;
    color: var(--text-muted);
    display: flex;
    align-items: baseline;
    gap: 6px;
  }
  .docx-footnote-num {
    font-weight: 700;
    color: var(--primary-color);
    flex-shrink: 0;
  }
  .docx-footnote-body {
    flex: 1;
    word-break: normal;
    overflow-wrap: break-word;
  }
  .docx-footnote-backlink {
    text-decoration: none;
    color: var(--primary-color);
  }
  .docx-citation-anchor {
    text-decoration: none;
    color: inherit;
  }
  .docx-footnote-ref a {
    text-decoration: none;
    color: var(--primary-color) !important;
    font-weight: 600;
  }

  /* ── Vertical Text Directions ── */
  .text-dir-tbrl {
    writing-mode: vertical-rl !important;
    text-orientation: mixed !important;
    display: inline-block;
  }
  .text-dir-btlr {
    writing-mode: vertical-lr !important;
    text-orientation: mixed !important;
    transform: rotate(180deg);
    display: inline-block;
  }

  /* ── Dark Mode ── */
  .dark-mode {
    --primary-color: #60a5fa;
    --primary-light: rgba(96, 165, 250, 0.12);
    --border-color: rgba(148, 163, 184, 0.22);
    --text-color: #e2e8f0;
    --text-muted: #94a3b8;
    --bg-page: #1e293b;
    --bg-canvas: #090d16;
    --page-shadow: 0 10px 30px rgba(0, 0, 0, 0.6), 0 2px 8px rgba(0, 0, 0, 0.4);
    --page-border: 1px solid rgba(51, 65, 85, 0.7);
    color: #e2e8f0;
  }
  .dark-mode .diag-tab-btn.active {
    background: #334155;
    color: #93c5fd;
  }
  .dark-mode .diagram-vector-svg {
    background: rgba(15, 23, 42, 0.9) !important;
    box-shadow: inset 0 0 0 1px rgba(51, 65, 85, 0.6) !important;
  }
  .dark-mode .diagram-vector-svg text {
    fill: #f1f5f9 !important;
  }
  .dark-mode .tree-leaf-node, .dark-mode .tree-branch-node {
    background: rgba(96, 165, 250, 0.12) !important;
    border-color: rgba(96, 165, 250, 0.25) !important;
    color: #e2e8f0 !important;
  }
  .dark-mode.mode-paged {
    background-color: var(--bg-canvas) !important;
  }
  .dark-mode .docx-page {
    background-color: var(--bg-page) !important;
  }
  .dark-mode h1, .dark-mode h2, .dark-mode h3, .dark-mode h4, .dark-mode h5, .dark-mode h6 {
    color: #f1f5f9;
  }
  .dark-mode h1 {
    background: var(--primary-light);
    border-left-color: var(--primary-color);
  }
  .dark-mode h2 {
    background: rgba(96, 165, 250, 0.08);
    border-left-color: rgba(96, 165, 250, 0.8);
  }
  .dark-mode h3 { color: #93c5fd; }
  .dark-mode h4 { color: #bfdbfe; }
  .dark-mode blockquote {
    background: var(--primary-light);
    color: #cbd5e1;
  }
  .dark-mode .diagram-visual-block {
    background: #0f172a;
    border-color: #334155;
  }
  .dark-mode .diagram-header-bar {
    background: rgba(96, 165, 250, 0.12);
    border-bottom-color: rgba(96, 165, 250, 0.22);
    color: #93c5fd;
  }
  .dark-mode .diagram-code-content { color: #f8fafc !important; }
  .dark-mode .inline-citation {
    color: #93c5fd;
    background-color: rgba(96, 165, 250, 0.14);
    border-color: rgba(96, 165, 250, 0.32);
  }
  .dark-mode .math-formula { color: #93c5fd; }
  .dark-mode .math-block {
    background: rgba(96, 165, 250, 0.08);
    border-color: rgba(96, 165, 250, 0.2);
  }
  .dark-mode thead tr, .dark-mode tr:first-child { background: rgba(96, 165, 250, 0.16); }
  .dark-mode th { color: #93c5fd; }
  .dark-mode tr:nth-child(even) td { background: rgba(255, 255, 255, 0.03); }
  .dark-mode tr:hover td { background: rgba(96, 165, 250, 0.08); }

  @media print {
    body { background: white !important; padding: 0 !important; }
    .docx-page {
      box-shadow: none !important;
      border: none !important;
      margin: 0 !important;
      page-break-after: always !important;
      break-after: page !important;
    }
  }
</style>
""".trimIndent()
    }

    /**
     * Generates client-side JavaScript for tracking the active page and handling page jumps.
     */
    fun generateClientScript(): String {
        return """
<script>
  (function() {
    window.goToPage = function(pageNum) {
      var target = document.querySelector('.docx-page[data-page-number="' + pageNum + '"]');
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    };

    document.addEventListener('click', function(e) {
      if (e.target.tagName !== 'A' && !e.target.closest('a') && !e.target.closest('.inline-citation')) {
        if (window.AndroidInterface && window.AndroidInterface.toggle) {
          window.AndroidInterface.toggle();
        }
      }
    });

    if ('IntersectionObserver' in window) {
      var pages = document.querySelectorAll('.docx-page');
      var total = pages.length;
      var observer = new IntersectionObserver(function(entries) {
        var visiblePage = 1;
        var maxRatio = 0;
        entries.forEach(function(entry) {
          if (entry.isIntersecting && entry.intersectionRatio > maxRatio) {
            maxRatio = entry.intersectionRatio;
            var num = parseInt(entry.target.getAttribute('data-page-number'), 10);
            if (!isNaN(num)) visiblePage = num;
          }
        });
        if (maxRatio > 0 && window.AndroidInterface && window.AndroidInterface.onPageChanged) {
          window.AndroidInterface.onPageChanged(visiblePage, total);
        }
      }, {
        threshold: [0.1, 0.3, 0.5, 0.7]
      });

      pages.forEach(function(p) { observer.observe(p); });
    }
  })();
</script>
""".trimIndent()
    }
}
