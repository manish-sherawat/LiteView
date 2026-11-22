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
     * Checks if a paragraph's text represents a line of an ASCII / box diagram ("Baski diagram").
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
     * Renders a group of consecutive diagram paragraphs into a stylized visual block.
     * Preserves exact whitespace and character positions.
     */
    fun renderDiagramBlock(paragraphs: List<XWPFParagraph>): String {
        val sb = StringBuilder()
        sb.append("<div class=\"diagram-visual-block\">\n")
        sb.append("  <div class=\"diagram-header-bar\">\n")
        sb.append("    <span class=\"diagram-badge\">\n")
        sb.append("      <svg class=\"diagram-icon\" viewBox=\"0 0 24 24\" width=\"14\" height=\"14\" fill=\"currentColor\"><path d=\"M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z\"/></svg>\n")
        sb.append("      Diagram\n")
        sb.append("    </span>\n")
        sb.append("  </div>\n")
        sb.append("  <pre class=\"diagram-code-content\"><code>")

        paragraphs.forEachIndexed { index, p ->
            val raw = p.text ?: ""
            val escaped = raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            sb.append(escaped)
            if (index < paragraphs.size - 1) {
                sb.append("\n")
            }
        }

        sb.append("</code></pre>\n")
        sb.append("</div>\n")
        return sb.toString()
    }
}
