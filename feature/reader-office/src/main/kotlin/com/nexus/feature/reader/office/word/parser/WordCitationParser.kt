package com.nexus.feature.reader.office.word.parser

object WordCitationParser {

    private val NUMERIC_CITATION_REGEX = Regex("""\[(\s*\d+(?:\s*[-–,]\s*\d+)*\s*)\]""")
    private val KEY_CITATION_REGEX = Regex("""\[(@[a-zA-Z0-9_\-]+(?:,\s*(?:p\.|pp\.|ch\.)\s*\d+(?:[-–]\d+)?)?)\]""")
    private val PREFIX_CITATION_REGEX = Regex("""\[(?:ref|cite|citation):\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
    private val AUTHOR_YEAR_CITATION_REGEX = Regex(
        """\[([A-Z][A-Za-z\s.,&]+(?:\s+et\s+al\.?)?,?\s+\d{4}[a-z]?(?:,\s*pp?\.\s*\d+(?:[-–]\d+)?)?(?:;\s*[A-Z][A-Za-z\s.,&]+(?:\s+et\s+al\.?)?,?\s+\d{4}[a-z]?)*)\]"""
    )
    private val RAW_WORD_CITATION_FIELD = Regex("""\bCITATION\s+([A-Za-z0-9_\-]+)(?:\s+\\[a-zA-Z0-9]+)*\b""")

    /**
     * Scans standard text and transforms inline citation tags into styled citation chips,
     * linking them directly to corresponding footnotes when available.
     * Leaves code and preformatted blocks untouched.
     */
    fun formatCitationTags(html: String, availableFootnoteIds: Set<String> = emptySet()): String {
        // Split by pre/code blocks so we don't accidentally format citations inside diagrams or code
        val parts = html.split(Regex("""(?=<pre[\s>])|(?<=</pre>)"""))
        val sb = StringBuilder()

        for (part in parts) {
            if (part.startsWith("<pre")) {
                sb.append(part)
            } else {
                sb.append(formatInlineCitations(part, availableFootnoteIds))
            }
        }
        return sb.toString()
    }

    private fun formatInlineCitations(text: String, availableFootnoteIds: Set<String>): String {
        var result = text

        // Clean any raw Word CITATION fields
        result = RAW_WORD_CITATION_FIELD.replace(result) { mr ->
            val key = mr.groupValues[1]
            val href = if (availableFootnoteIds.contains(key)) "href=\"#footnote_$key\"" else "href=\"#cite_$key\""
            """<span class="inline-citation citation-word" data-cite="$key" title="Citation: $key"><a class="docx-citation-anchor" $href><span class="citation-bracket">[</span>$key<span class="citation-bracket">]</span></a></span>"""
        }

        // Numeric citations: [1], [1, 2], [1-3]
        result = NUMERIC_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            val firstNum = content.filter { it.isDigit() }.takeIf { it.isNotEmpty() }
            val href = if (firstNum != null && availableFootnoteIds.contains(firstNum)) {
                "href=\"#footnote_$firstNum\" id=\"cite_ref_$firstNum\""
            } else {
                "href=\"#cite_$content\""
            }
            """<span class="inline-citation citation-numeric" data-cite="$content" title="Citation: $content"><a class="docx-citation-anchor" $href><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></a></span>"""
        }

        // Keyed citations: [@smith2020]
        result = KEY_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            val key = content.removePrefix("@")
            val href = if (availableFootnoteIds.contains(key)) "href=\"#footnote_$key\"" else "href=\"#cite_$key\""
            """<span class="inline-citation citation-key" data-cite="$content" title="Citation: $content"><a class="docx-citation-anchor" $href><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></a></span>"""
        }

        // Ref/Cite prefix: [ref: 12], [cite: Smith2020]
        result = PREFIX_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            val href = if (availableFootnoteIds.contains(content)) "href=\"#footnote_$content\"" else "href=\"#cite_$content\""
            """<span class="inline-citation citation-named" data-cite="$content" title="Citation: $content"><a class="docx-citation-anchor" $href><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></a></span>"""
        }

        // Author-Year citations: [Smith, 2020], [Doe et al., 2019]
        result = AUTHOR_YEAR_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            """<span class="inline-citation citation-author" data-cite="$content" title="Citation: $content"><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></span>"""
        }

        return result
    }

    /**
     * Formats extracted footnotes into a structured HTML footnotes section with back-links.
     */
    fun renderFootnotesSection(footnotesMap: Map<String, String>): String {
        if (footnotesMap.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("<section class=\"docx-footnotes-section\" id=\"doc_footnotes\">\n")
        sb.append("  <div class=\"docx-footnotes-divider\"></div>\n")
        sb.append("  <div class=\"docx-footnotes-header\">\n")
        sb.append("    <span class=\"docx-footnotes-title\">Footnotes &amp; References</span>\n")
        sb.append("  </div>\n")
        sb.append("  <div class=\"docx-footnotes-list\">\n")

        for ((id, content) in footnotesMap) {
            sb.append("    <div id=\"footnote_$id\" class=\"docx-footnote-item\">\n")
            sb.append("      <a href=\"#cite_ref_$id\" class=\"docx-footnote-backlink\" title=\"Jump back to reference in text\">\n")
            sb.append("        <span class=\"docx-footnote-num\">[$id]</span>\n")
            sb.append("      </a>\n")
            sb.append("      <span class=\"docx-footnote-body\">$content</span>\n")
            sb.append("    </div>\n")
        }

        sb.append("  </div>\n")
        sb.append("</section>\n")
        return sb.toString()
    }
}
