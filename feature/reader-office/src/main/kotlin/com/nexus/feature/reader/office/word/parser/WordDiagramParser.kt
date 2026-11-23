package com.nexus.feature.reader.office.word.parser

import org.apache.poi.xwpf.usermodel.XWPFParagraph

object WordDiagramParser {

    private val BOX_DRAWING_RANGE = '\u2500'..'\u257F'
    private val BLOCK_ELEMENTS_RANGE = '\u2580'..'\u259F'

    private val ASCII_BOX_PATTERNS = listOf(
        Regex("""\+[-=]{2,}\+"""),               // +---+ or +===+
        Regex("""\|[\s\w\W]{2,}\|"""),            // |   |
        Regex("""\+[-=]{3,}"""),                  // +---
        Regex("""[-=]{3,}\+"""),                  // ---+
        Regex("""[-=]{2,}>"""),                   // --> or ==>
        Regex("""<[-=]{2,}"""),                   // <-- or <==
        Regex("""\|[-_]{2,}\|""")                 // |___|
    )

    /**
     * Checks if a paragraph's text represents a line of an ASCII / box diagram.
     */
    fun isDiagramParagraph(para: XWPFParagraph): Boolean {
        val text = para.text ?: return false
        if (text.isBlank()) return false

        // 1. Unicode box drawing characters (U+2500 to U+257F)
        if (text.any { it in BOX_DRAWING_RANGE }) return true

        // 2. Unicode block elements (U+2580 to U+259F)
        if (text.any { it in BLOCK_ELEMENTS_RANGE }) return true

        // 3. Tree hierarchy indicators
        if (text.contains("├──") || text.contains("└──") || text.contains("│   ") ||
            text.contains("|--") || text.contains("\\--") || text.contains("+--")
        ) {
            return true
        }

        // 4. ASCII box patterns
        val hasAsciiBoxMatch = ASCII_BOX_PATTERNS.any { it.containsMatchIn(text) }
        if (hasAsciiBoxMatch) {
            val symbolCount = text.count { it in "+|-=><_/^\\" }
            if (symbolCount >= 3) return true
        }

        // 5. Monospace font check with ASCII structures
        val isMonospace = para.runs.any { run ->
            val font = run.fontFamily?.lowercase() ?: ""
            font.contains("courier") || font.contains("consolas") ||
                font.contains("monospace") || font.contains("fira code") ||
                font.contains("source code") || font.contains("cascadia")
        }
        if (isMonospace) {
            val symbolCount = text.count { it in "+|-=><_/^\\┌┐└┘├┤┬┴┼─│" }
            if (symbolCount >= 3 || (text.contains("  ") && symbolCount >= 2)) {
                return true
            }
        }

        return false
    }

    /**
     * Renders a group of consecutive diagram paragraphs into a graphic structure
     * (Vector SVG graphic or styled visual node tree) with a toggleable source fallback.
     */
    fun renderDiagramBlock(paragraphs: List<XWPFParagraph>): String {
        val lines = paragraphs.map { it.text ?: "" }
        val diagramId = "diag_" + (paragraphs.firstOrNull()?.text?.hashCode() ?: System.currentTimeMillis()).toString().replace("-", "n")

        val isTree = lines.any { it.contains("├──") || it.contains("└──") || it.contains("|--") || it.contains("\\--") }
        val graphicHtml = if (isTree) {
            renderGraphicTree(lines)
        } else {
            renderVectorSvg(lines)
        }

        val rawAscii = lines.joinToString("\n") { line ->
            line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        }

        val sb = StringBuilder()
        sb.append("<div class=\"diagram-visual-block\" id=\"$diagramId\">\n")
        sb.append("  <div class=\"diagram-header-bar\">\n")
        sb.append("    <span class=\"diagram-badge\">\n")
        sb.append("      <svg class=\"diagram-icon\" viewBox=\"0 0 24 24\" width=\"14\" height=\"14\" fill=\"currentColor\"><path d=\"M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z\"/></svg>\n")
        sb.append("      ${if (isTree) "Hierarchy Tree Graphic" else "Vector Diagram Graphic"}\n")
        sb.append("    </span>\n")
        sb.append("    <div class=\"diagram-controls\">\n")
        sb.append("      <button type=\"button\" class=\"diag-tab-btn active\" onclick=\"(function(b){ var p=b.closest('.diagram-visual-block'); p.querySelector('.diagram-graphic-view').style.display='block'; p.querySelector('.diagram-code-view').style.display='none'; p.querySelectorAll('.diag-tab-btn').forEach(function(x){x.classList.remove('active');}); b.classList.add('active'); })(this)\">Graphic</button>\n")
        sb.append("      <button type=\"button\" class=\"diag-tab-btn\" onclick=\"(function(b){ var p=b.closest('.diagram-visual-block'); p.querySelector('.diagram-graphic-view').style.display='none'; p.querySelector('.diagram-code-view').style.display='block'; p.querySelectorAll('.diag-tab-btn').forEach(function(x){x.classList.remove('active');}); b.classList.add('active'); })(this)\">Source</button>\n")
        sb.append("    </div>\n")
        sb.append("  </div>\n")

        // 1. Primary Graphics View
        sb.append("  <div class=\"diagram-graphic-view\" style=\"display:block;padding:16px;overflow-x:auto;-webkit-overflow-scrolling:touch;\">\n")
        sb.append(graphicHtml)
        sb.append("  </div>\n")

        // 2. Secondary Code / ASCII View
        sb.append("  <div class=\"diagram-code-view\" style=\"display:none;\">\n")
        sb.append("    <pre class=\"diagram-code-content\"><code>$rawAscii</code></pre>\n")
        sb.append("  </div>\n")
        sb.append("</div>\n")

        return sb.toString()
    }

    private fun renderGraphicTree(lines: List<String>): String {
        val sb = StringBuilder()
        sb.append("<div class=\"tree-graphic-container\" style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">\n")

        for (line in lines) {
            if (line.isBlank()) continue
            val cleanLine = line.trimEnd()

            // Count leading indent/pipes
            var indent = 0
            var textPart = cleanLine

            val treePrefixRegex = Regex("""^([\s│\s|]*)(├──|└──|\|--|\\\--|\+--)(.*)$""")
            val match = treePrefixRegex.find(cleanLine)

            if (match != null) {
                val leading = match.groupValues[1]
                val connector = match.groupValues[2]
                textPart = match.groupValues[3].trim()
                indent = (leading.length / 2) + 1
                val isLast = connector.contains("└") || connector.contains("\\")

                val badgeClass = if (textPart.contains(".")) "tree-leaf-node" else "tree-branch-node"
                val icon = if (textPart.contains(".")) "📄" else "📁"

                sb.append("  <div class=\"tree-node-row\" style=\"display:flex;align-items:center;padding:4px 0;margin-left:${indent * 24}px;\">\n")
                sb.append("    <span class=\"tree-connector-mark\" style=\"color:var(--primary-color,#2563eb);font-family:monospace;font-weight:bold;margin-right:8px;\">${if (isLast) "└─" else "├─"}</span>\n")
                sb.append("    <span class=\"$badgeClass\" style=\"display:inline-flex;align-items:center;padding:4px 10px;background:rgba(37,99,235,0.08);border:1px solid rgba(37,99,235,0.22);border-radius:6px;font-size:13px;font-weight:500;color:var(--text-color,#1e293b);\">\n")
                sb.append("      <span style=\"margin-right:6px;\">$icon</span> ${escapeHtml(textPart)}\n")
                sb.append("    </span>\n")
                sb.append("  </div>\n")
            } else {
                // Root node
                sb.append("  <div class=\"tree-root-row\" style=\"display:flex;align-items:center;padding:6px 0;font-weight:600;font-size:14px;color:var(--primary-color,#2563eb);\">\n")
                sb.append("    <span style=\"margin-right:8px;\">📦</span> ${escapeHtml(cleanLine.trim())}\n")
                sb.append("  </div>\n")
            }
        }

        sb.append("</div>\n")
        return sb.toString()
    }

    private fun renderVectorSvg(lines: List<String>): String {
        if (lines.isEmpty()) return ""

        val maxCols = lines.maxOfOrNull { it.length }?.coerceAtLeast(10) ?: 40
        val rowCount = lines.size
        val charW = 9.0
        val charH = 19.0
        val svgWidth = (maxCols * charW + 40).toInt().coerceIn(300, 1200)
        val svgHeight = (rowCount * charH + 30).toInt().coerceIn(100, 2000)

        val sb = StringBuilder()
        sb.append("<svg class=\"diagram-vector-svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" style=\"max-width:${svgWidth}px;height:auto;display:block;margin:0 auto;border-radius:6px;background:rgba(248,250,252,0.9);box-shadow:inset 0 0 0 1px rgba(203,213,225,0.6);\">\n")
        sb.append("  <defs>\n")
        sb.append("    <marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"8\" refY=\"5\" markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">\n")
        sb.append("      <path d=\"M 0 1 L 10 5 L 0 9 z\" fill=\"#2563eb\" />\n")
        sb.append("    </marker>\n")
        sb.append("  </defs>\n")

        for ((r, line) in lines.withIndex()) {
            val y = (r + 1) * charH + 6
            // Render text line with crisp SVG rendering
            val cleanLine = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sb.append("  <text x=\"20\" y=\"$y\" font-family=\"'Cascadia Code', Consolas, monospace\" font-size=\"13.5\" fill=\"#1e293b\" font-weight=\"500\" xml:space=\"preserve\">$cleanLine</text>\n")
        }

        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
