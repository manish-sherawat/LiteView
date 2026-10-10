package com.nexus.feature.reader.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight, zero-dependency Compose Syntax Highlighter for text, logs, code,
 * config, documentation, and data files.
 */
object SyntaxHighlighter {

    // ─── Theme Palettes ────────────────────────────────────────────────────────
    class Palette(
        val keyword: Color,
        val string: Color,
        val comment: Color,
        val number: Color,
        val key: Color,
        val tag: Color,
        val attribute: Color,
        val header: Color,
        val logError: Color,
        val logWarn: Color,
        val logInfo: Color,
        val logDebug: Color,
        val accent: Color
    )

    fun createPalette(colors: com.nexus.core.theme.NexusColors): Palette {
        return Palette(
            keyword = colors.primary,
            string = colors.success,
            comment = colors.textSecondary,
            number = colors.warning,
            key = colors.textPrimary,
            tag = colors.primary,
            attribute = colors.textSecondary,
            header = colors.primary,
            logError = colors.error,
            logWarn = colors.warning,
            logInfo = colors.success,
            logDebug = colors.textSecondary,
            accent = colors.primary
        )
    }

    private val LOG_EXTENSIONS = setOf("log")
    private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "rst")
    private val JSON_EXTENSIONS = setOf("json", "jsonl", "geojson")
    private val XML_EXTENSIONS = setOf("xml", "html", "htm", "svg")
    private val CONFIG_EXTENSIONS = setOf("yaml", "yml", "toml", "ini", "cfg", "conf")
    private val CSV_EXTENSIONS = setOf("csv", "tsv")

    private val CODE_KEYWORDS = setOf(
        "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "delete", "do", "else", "enum", "export",
        "extends", "false", "finally", "for", "from", "function", "fun", "if", "implements",
        "import", "in", "instanceof", "interface", "let", "new", "null", "of", "package",
        "private", "protected", "public", "return", "super", "switch", "this", "throw",
        "true", "try", "typeof", "val", "var", "void", "while", "with", "yield",
        "def", "self", "None", "True", "False", "lambda", "elif", "pass", "raise"
    )

    fun highlight(
        line: String,
        extension: String,
        colors: com.nexus.core.theme.NexusColors
    ): AnnotatedString {
        if (line.isEmpty()) return AnnotatedString("")
        val ext = extension.lowercase()
        val palette = createPalette(colors)
        val defaultColor = colors.textPrimary

        return when {
            LOG_EXTENSIONS.contains(ext) -> highlightLog(line, palette, defaultColor)
            MARKDOWN_EXTENSIONS.contains(ext) -> highlightMarkdown(line, palette, defaultColor)
            JSON_EXTENSIONS.contains(ext) -> highlightJson(line, palette, defaultColor)
            XML_EXTENSIONS.contains(ext) -> highlightXml(line, palette, defaultColor)
            CONFIG_EXTENSIONS.contains(ext) -> highlightConfig(line, palette, defaultColor)
            CSV_EXTENSIONS.contains(ext) -> highlightCsv(line, palette, defaultColor)
            else -> highlightCode(line, palette, defaultColor)
        }
    }

    // ─── Log Highlighting ──────────────────────────────────────────────────────
    private fun highlightLog(line: String, palette: Palette, defaultColor: Color): AnnotatedString {
        val upper = line.uppercase()
        val highlightColor = when {
            upper.contains("FATAL") || upper.contains("ERROR") || upper.contains("CRITICAL") || upper.contains("FAIL") || upper.contains("EXCEPTION") -> palette.logError
            upper.contains("WARN") || upper.contains("WARNING") -> palette.logWarn
            upper.contains("INFO") -> palette.logInfo
            upper.contains("DEBUG") || upper.contains("TRACE") || upper.contains("VERBOSE") -> palette.logDebug
            else -> null
        }

        return buildAnnotatedString {
            if (highlightColor != null) {
                val tagMatch = Regex("""\b(FATAL|ERROR|CRITICAL|FAIL|EXCEPTION|WARN|WARNING|INFO|DEBUG|TRACE|VERBOSE)\b""", RegexOption.IGNORE_CASE).find(line)
                if (tagMatch != null) {
                    append(line.substring(0, tagMatch.range.first))
                    withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                        append(tagMatch.value)
                    }
                    val rest = line.substring(tagMatch.range.last + 1)
                    if (highlightColor == palette.logError) {
                        withStyle(SpanStyle(color = highlightColor.copy(alpha = 0.9f))) {
                            append(rest)
                        }
                    } else {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(rest)
                        }
                    }
                } else {
                    withStyle(SpanStyle(color = highlightColor)) {
                        append(line)
                    }
                }
            } else {
                withStyle(SpanStyle(color = defaultColor)) {
                    append(line)
                }
            }
        }
    }

    // ─── Markdown Highlighting ─────────────────────────────────────────────────
    private fun highlightMarkdown(line: String, palette: Palette, defaultColor: Color): AnnotatedString {
        val trimmed = line.trimStart()
        return buildAnnotatedString {
            when {
                trimmed.startsWith("#") -> {
                    val hashes = trimmed.takeWhile { it == '#' }
                    val weight = if (hashes.length <= 2) FontWeight.Bold else FontWeight.SemiBold
                    withStyle(SpanStyle(color = palette.header, fontWeight = weight)) {
                        append(line)
                    }
                }
                trimmed.startsWith(">") -> {
                    withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                        append(line)
                    }
                }
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold)) {
                        append(line)
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    val bulletIndex = line.indexOf(trimmed.first())
                    append(line.substring(0, bulletIndex))
                    withStyle(SpanStyle(color = palette.accent, fontWeight = FontWeight.Bold)) {
                        append(trimmed.substring(0, 2))
                    }
                    append(trimmed.substring(2))
                }
                else -> {
                    var currentIndex = 0
                    val inlineRegex = Regex("""(`[^`]+`|\*\*[^*]+\*\*|__[^_]+__)""")
                    val matches = inlineRegex.findAll(line)
                    for (m in matches) {
                        if (m.range.first > currentIndex) {
                            withStyle(SpanStyle(color = defaultColor)) {
                                append(line.substring(currentIndex, m.range.first))
                            }
                        }
                        val text = m.value
                        if (text.startsWith("`")) {
                            withStyle(SpanStyle(color = palette.string, background = palette.comment.copy(alpha = 0.15f))) {
                                append(text)
                            }
                        } else {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                                append(text)
                            }
                        }
                        currentIndex = m.range.last + 1
                    }
                    if (currentIndex < line.length) {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(line.substring(currentIndex))
                        }
                    }
                }
            }
        }
    }

    // ─── JSON Highlighting ─────────────────────────────────────────────────────
    private fun highlightJson(line: String, palette: Palette, defaultColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            val len = line.length
            while (i < len) {
                val c = line[i]
                when {
                    c == '"' -> {
                        val end = findStringEnd(line, i)
                        val str = line.substring(i, end)
                        val after = line.substring(end).trimStart()
                        if (after.startsWith(":")) {
                            withStyle(SpanStyle(color = palette.key, fontWeight = FontWeight.SemiBold)) {
                                append(str)
                            }
                        } else {
                            withStyle(SpanStyle(color = palette.string)) {
                                append(str)
                            }
                        }
                        i = end
                    }
                    line.startsWith("true", i) || line.startsWith("null", i) -> {
                        val token = if (line.startsWith("true", i)) "true" else "null"
                        withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold)) {
                            append(token)
                        }
                        i += token.length
                    }
                    line.startsWith("false", i) -> {
                        withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold)) {
                            append("false")
                        }
                        i += 5
                    }
                    c.isDigit() || (c == '-' && i + 1 < len && line[i + 1].isDigit()) -> {
                        var end = i + 1
                        while (end < len && (line[end].isDigit() || line[end] == '.' || line[end] == 'e' || line[end] == 'E' || line[end] == '+' || line[end] == '-')) end++
                        withStyle(SpanStyle(color = palette.number)) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    else -> {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(c)
                        }
                        i++
                    }
                }
            }
        }
    }

    // ─── XML / HTML / SVG Highlighting ─────────────────────────────────────────
    private fun highlightXml(line: String, palette: Palette, defaultColor: Color): AnnotatedString {
        val trimmed = line.trim()
        if (trimmed.startsWith("<!--") && trimmed.endsWith("-->")) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                    append(line)
                }
            }
        }

        return buildAnnotatedString {
            var i = 0
            val len = line.length
            var inTag = false
            while (i < len) {
                val c = line[i]
                when {
                    line.startsWith("<!--", i) -> {
                        val endIdx = line.indexOf("-->", i)
                        val end = if (endIdx >= 0) endIdx + 3 else len
                        withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    c == '<' -> {
                        inTag = true
                        withStyle(SpanStyle(color = palette.tag, fontWeight = FontWeight.SemiBold)) {
                            append(c)
                        }
                        i++
                        var end = i
                        if (end < len && (line[end] == '/' || line[end] == '?' || line[end] == '!')) end++
                        while (end < len && !line[end].isWhitespace() && line[end] != '>' && line[end] != '/') end++
                        withStyle(SpanStyle(color = palette.tag, fontWeight = FontWeight.SemiBold)) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    c == '>' -> {
                        inTag = false
                        withStyle(SpanStyle(color = palette.tag, fontWeight = FontWeight.SemiBold)) {
                            append(c)
                        }
                        i++
                    }
                    inTag && (c == '"' || c == '\'') -> {
                        val quote = c
                        var end = i + 1
                        while (end < len && line[end] != quote) {
                            if (line[end] == '\\') end++
                            end++
                        }
                        if (end < len) end++
                        withStyle(SpanStyle(color = palette.string)) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    inTag && c.isLetter() -> {
                        var end = i + 1
                        while (end < len && (line[end].isLetterOrDigit() || line[end] == '-' || line[end] == '_' || line[end] == ':')) end++
                        val token = line.substring(i, end)
                        withStyle(SpanStyle(color = palette.attribute)) {
                            append(token)
                        }
                        i = end
                    }
                    else -> {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(c)
                        }
                        i++
                    }
                }
            }
        }
    }

    // ─── Config (YAML / TOML / INI) Highlighting ───────────────────────────────
    private fun highlightConfig(line: String, palette: Palette, defaultColor: Color): AnnotatedString {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                    append(line)
                }
            }
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = palette.header, fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            }
        }

        return buildAnnotatedString {
            val colonIdx = line.indexOf(':')
            val eqIdx = line.indexOf('=')
            val sepIdx = when {
                colonIdx >= 0 && eqIdx >= 0 -> minOf(colonIdx, eqIdx)
                colonIdx >= 0 -> colonIdx
                else -> eqIdx
            }

            if (sepIdx > 0) {
                val key = line.substring(0, sepIdx)
                val sep = line[sepIdx]
                val value = line.substring(sepIdx + 1)
                withStyle(SpanStyle(color = palette.key, fontWeight = FontWeight.SemiBold)) {
                    append(key)
                }
                withStyle(SpanStyle(color = defaultColor)) {
                    append(sep)
                }
                withStyle(SpanStyle(color = palette.string)) {
                    append(value)
                }
            } else {
                withStyle(SpanStyle(color = defaultColor)) {
                    append(line)
                }
            }
        }
    }

    // ─── CSV / TSV Highlighting ────────────────────────────────────────────────
    private fun highlightCsv(line: String, palette: Palette, defaultColor: Color): AnnotatedString {
        val delimiter = if (line.contains('\t')) '\t' else ','
        return buildAnnotatedString {
            val tokens = line.split(delimiter)
            tokens.forEachIndexed { index, token ->
                if (index > 0) {
                    withStyle(SpanStyle(color = palette.accent, fontWeight = FontWeight.Bold)) {
                        append(delimiter)
                    }
                }
                val trimmedToken = token.trim()
                when {
                    trimmedToken.startsWith("\"") && trimmedToken.endsWith("\"") -> {
                        withStyle(SpanStyle(color = palette.string)) { append(token) }
                    }
                    trimmedToken.toDoubleOrNull() != null -> {
                        withStyle(SpanStyle(color = palette.number)) { append(token) }
                    }
                    else -> {
                        withStyle(SpanStyle(color = defaultColor)) { append(token) }
                    }
                }
            }
        }
    }

    // ─── Generic Code Highlighting ─────────────────────────────────────────────
    private fun highlightCode(line: String, palette: Palette, defaultColor: Color): AnnotatedString {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                    append(line)
                }
            }
        }

        return buildAnnotatedString {
            var i = 0
            val len = line.length
            while (i < len) {
                val c = line[i]
                when {
                    line.startsWith("//", i) || (c == '#' && (i == 0 || line[i - 1].isWhitespace())) -> {
                        withStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)) {
                            append(line.substring(i))
                        }
                        i = len
                    }
                    c == '"' || c == '\'' || c == '`' -> {
                        val quote = c
                        var end = i + 1
                        while (end < len && line[end] != quote) {
                            if (line[end] == '\\') end++
                            end++
                        }
                        if (end < len) end++
                        withStyle(SpanStyle(color = palette.string)) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    c.isLetter() || c == '_' || c == '$' -> {
                        var end = i + 1
                        while (end < len && (line[end].isLetterOrDigit() || line[end] == '_' || line[end] == '$')) end++
                        val word = line.substring(i, end)
                        if (CODE_KEYWORDS.contains(word)) {
                            withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold)) {
                                append(word)
                            }
                        } else {
                            withStyle(SpanStyle(color = defaultColor)) {
                                append(word)
                            }
                        }
                        i = end
                    }
                    c.isDigit() -> {
                        var end = i + 1
                        while (end < len && (line[end].isLetterOrDigit() || line[end] == '.')) end++
                        withStyle(SpanStyle(color = palette.number)) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    else -> {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(c)
                        }
                        i++
                    }
                }
            }
        }
    }

    private fun findStringEnd(line: String, start: Int): Int {
        var end = start + 1
        val len = line.length
        while (end < len && line[end] != '"') {
            if (line[end] == '\\') end++
            end++
        }
        if (end < len) end++
        return end
    }
}
