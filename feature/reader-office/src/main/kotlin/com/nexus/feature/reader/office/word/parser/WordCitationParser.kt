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
     * Scans standard text and transforms inline citation tags into styled citation chips.
     * Leaves code and preformatted blocks untouched.
     */
    fun formatCitationTags(html: String): String {
        // Split by pre/code blocks so we don't accidentally format citations inside diagrams or code
        val parts = html.split(Regex("""(?=<pre[\s>])|(?<=</pre>)"""))
        val sb = StringBuilder()

        for (part in parts) {
            if (part.startsWith("<pre")) {
                sb.append(part)
            } else {
                sb.append(formatInlineCitations(part))
            }
        }
        return sb.toString()
    }

    private fun formatInlineCitations(text: String): String {
        var result = text

        // Clean any raw Word CITATION fields
        result = RAW_WORD_CITATION_FIELD.replace(result) { mr ->
            val key = mr.groupValues[1]
            """<span class="inline-citation citation-word" data-cite="$key" title="Citation: $key"><span class="citation-bracket">[</span>$key<span class="citation-bracket">]</span></span>"""
        }

        // Numeric citations: [1], [1, 2], [1-3]
        result = NUMERIC_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            """<span class="inline-citation citation-numeric" data-cite="$content" title="Citation: $content"><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></span>"""
        }

        // Keyed citations: [@smith2020]
        result = KEY_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            """<span class="inline-citation citation-key" data-cite="$content" title="Citation: $content"><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></span>"""
        }

        // Ref/Cite prefix: [ref: 12], [cite: Smith2020]
        result = PREFIX_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            """<span class="inline-citation citation-named" data-cite="$content" title="Citation: $content"><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></span>"""
        }

        // Author-Year citations: [Smith, 2020], [Doe et al., 2019]
        result = AUTHOR_YEAR_CITATION_REGEX.replace(result) { mr ->
            val content = mr.groupValues[1].trim()
            """<span class="inline-citation citation-author" data-cite="$content" title="Citation: $content"><span class="citation-bracket">[</span>$content<span class="citation-bracket">]</span></span>"""
        }

        return result
    }
}
