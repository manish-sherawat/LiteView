package com.nexus.feature.reader.presentation.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.engine.ParagraphInfo
import com.nexus.feature.reader.presentation.engine.TextAlignment
import com.nexus.feature.reader.presentation.engine.TextSpanInfo

/**
 * Rich Text and Typography component that faithfully renders inline spans,
 * weights, colors, baseline shifts, paragraph alignments, and hyperlinks.
 */
@Composable
fun SlideRichText(
    paragraphs: List<ParagraphInfo>,
    modifier: Modifier = Modifier,
    defaultColor: Color = NexusTheme.colors.textPrimary,
    searchQuery: String = "",
    fontScale: Float = 1f,
    onLinkClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val linkHandler = onLinkClick ?: { url ->
        try {
            uriHandler.openUri(url)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (paragraph in paragraphs) {
            if (paragraph.isBullet) {
                val bulletIndent = if (fontScale < 0.4f) (paragraph.indent * 4f).dp else (paragraph.indent * 16f).coerceAtLeast(0f).dp
                val bulletSpacing = if (fontScale < 0.4f) 3.dp else 8.dp
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(bulletSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = bulletIndent)
                ) {
                    if (!paragraph.bulletChar.isNullOrBlank()) {
                        NexusText(
                            text = paragraph.bulletChar,
                            style = NexusTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (fontScale < 0.4f) 6.sp else 12.sp
                            ),
                            color = NexusTheme.colors.primary,
                            modifier = Modifier.padding(top = if (fontScale < 0.4f) 0.dp else 2.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(top = if (fontScale < 0.4f) 2.dp else 7.dp)
                                .size(if (fontScale < 0.4f) 2.dp else 6.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.primary)
                        )
                    }
                    ParagraphContent(
                        paragraph = paragraph,
                        defaultColor = defaultColor,
                        searchQuery = searchQuery,
                        fontScale = fontScale,
                        onLinkClick = linkHandler,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                ParagraphContent(
                    paragraph = paragraph,
                    defaultColor = defaultColor,
                    searchQuery = searchQuery,
                    fontScale = fontScale,
                    onLinkClick = linkHandler,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ParagraphContent(
    paragraph: ParagraphInfo,
    defaultColor: Color,
    searchQuery: String = "",
    fontScale: Float = 1f,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textAlign = when (paragraph.alignment) {
        TextAlignment.LEFT -> TextAlign.Start
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.End
        TextAlignment.JUSTIFY -> TextAlign.Justify
    }

    val annotatedString = remember(paragraph.spans, defaultColor, searchQuery, fontScale) {
        buildAnnotatedStringFromSpans(paragraph.spans, defaultColor, searchQuery, fontScale, onLinkClick)
    }

    val firstSpanSize = paragraph.spans.firstOrNull()?.fontSize ?: 14f
    val baseFontSize = (firstSpanSize * fontScale).coerceIn(if (fontScale < 0.4f) 5f else 8f, 64f)
    val computedLineHeight = (baseFontSize * paragraph.lineSpacingMultiplier).sp

    androidx.compose.foundation.text.BasicText(
        text = annotatedString,
        modifier = modifier,
        style = NexusTheme.typography.body.copy(
            textAlign = textAlign,
            lineHeight = computedLineHeight
        ),
        overflow = TextOverflow.Ellipsis,
        softWrap = true
    )
}

/**
 * Robust fallback font resolver that accurately maps Office, Windows, macOS, and custom fonts
 * to supported Android Compose FontFamilies.
 */
fun resolveFontFamily(fontName: String?): androidx.compose.ui.text.font.FontFamily {
    if (fontName.isNullOrBlank()) return androidx.compose.ui.text.font.FontFamily.Default
    val normalized = fontName.trim().lowercase()
    return when {
        // Monospace fonts
        normalized.contains("mono") || normalized.contains("consolas") ||
                normalized.contains("courier") || normalized.contains("source code") ||
                normalized.contains("menlo") || normalized.contains("lucida console") ||
                normalized.contains("inconsolata") || normalized.contains("fira code") ->
            androidx.compose.ui.text.font.FontFamily.Monospace

        // Serif fonts
        normalized.contains("times") || normalized.contains("serif") ||
                normalized.contains("georgia") || normalized.contains("garamond") ||
                normalized.contains("cambria") || normalized.contains("palatino") ||
                normalized.contains("baskerville") || normalized.contains("book antiqua") ||
                normalized.contains("bodoni") || normalized.contains("century schoolbook") ||
                normalized.contains("minion") || normalized.contains("didot") ->
            androidx.compose.ui.text.font.FontFamily.Serif

        // Cursive / Decorative script fonts
        normalized.contains("cursive") || normalized.contains("script") ||
                normalized.contains("comic") || normalized.contains("handwriting") ||
                normalized.contains("papyrus") || normalized.contains("brush") ||
                normalized.contains("bradley") || normalized.contains("chalkboard") ->
            androidx.compose.ui.text.font.FontFamily.Cursive

        // Sans-Serif fonts (the vast majority of modern presentation fonts)
        normalized.contains("sans") || normalized.contains("calibri") ||
                normalized.contains("aptos") || normalized.contains("segoe") ||
                normalized.contains("arial") || normalized.contains("helvetica") ||
                normalized.contains("tahoma") || normalized.contains("verdana") ||
                normalized.contains("trebuchet") || normalized.contains("century gothic") ||
                normalized.contains("corbel") || normalized.contains("candara") ||
                normalized.contains("roboto") || normalized.contains("lato") ||
                normalized.contains("open sans") || normalized.contains("montserrat") ||
                normalized.contains("poppins") || normalized.contains("inter") ||
                normalized.contains("gothic") || normalized.contains("impact") ->
            androidx.compose.ui.text.font.FontFamily.SansSerif

        else -> androidx.compose.ui.text.font.FontFamily.Default
    }
}

/**
 * Sanitizes presentation text by converting symbol/Wingdings fonts and unrenderable Private Use Area
 * code points (0xF020..0xF0FF) to valid, crisp Unicode characters to prevent black strip tofu boxes.
 */
fun sanitizePresentationText(text: String, fontFamily: String?): String {
    if (text.isEmpty()) return text
    val fontLower = fontFamily?.lowercase() ?: ""
    val isSymbolFont = fontLower.contains("wingdings") || fontLower.contains("symbol") ||
            fontLower.contains("webdings") || fontLower.contains("marlett")

    val sb = StringBuilder(text.length)
    for (ch in text) {
        val code = ch.code
        if (code in 0xF020..0xF0FF) {
            sb.append(mapSymbolCodeToUnicode(code - 0xF000, fontLower))
        } else if (isSymbolFont && code in 0x20..0x7E) {
            sb.append(mapSymbolCodeToUnicode(code, fontLower))
        } else if (ch == '\uFFFD' || (code in 0xE000..0xF8FF && !isSymbolFont)) {
            sb.append('•')
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

private fun mapSymbolCodeToUnicode(charCode: Int, fontLower: String): String {
    return when {
        fontLower.contains("wingdings 2") -> when (charCode) {
            0x24 -> "✓"
            0x25 -> "✗"
            0x50 -> "★"
            0x51 -> "☆"
            0x71 -> "■"
            0x72 -> "□"
            0x73 -> "▲"
            0x74 -> "▼"
            0x75 -> "◆"
            0x76 -> "◇"
            0xA7 -> "▪"
            0xB7 -> "•"
            else -> "•"
        }
        fontLower.contains("wingdings 3") -> when (charCode) {
            0x70 -> "▲"
            0x71 -> "▼"
            0x72 -> "◀"
            0x73 -> "▶"
            0x74 -> "◄"
            0x75 -> "►"
            else -> "➔"
        }
        fontLower.contains("symbol") -> when (charCode) {
            0x61 -> "α"
            0x62 -> "β"
            0x67 -> "γ"
            0x64 -> "δ"
            0x65 -> "ε"
            0x70 -> "π"
            0x73 -> "σ"
            0x74 -> "τ"
            0x77 -> "ω"
            0xB7 -> "•"
            0xD7 -> "×"
            0xF7 -> "÷"
            0xA3 -> "≤"
            0xB3 -> "≥"
            0xB9 -> "≠"
            0xA5 -> "∞"
            else -> charCode.toChar().toString()
        }
        else -> when (charCode) {
            0x6C -> "●"
            0x6D -> "■"
            0x6E -> "■"
            0x70 -> "◆"
            0x71 -> "❑"
            0x75 -> "◆"
            0x76 -> "❖"
            0x77 -> "✦"
            0x84 -> "✓"
            0xFC -> "✓"
            0xFD -> "✗"
            0xFE -> "☒"
            0xFF -> "☐"
            0xA7 -> "▪"
            0xB7 -> "•"
            0xD8 -> "➢"
            0xE0 -> "✉"
            0xF0 -> "☛"
            0xF1 -> "☞"
            else -> "•"
        }
    }
}

fun buildAnnotatedStringFromSpans(
    spans: List<TextSpanInfo>,
    defaultColor: Color,
    searchQuery: String = "",
    fontScale: Float = 1f,
    onLinkClick: ((String) -> Unit)? = null
): AnnotatedString {
    return buildAnnotatedString {
        for (span in spans) {
            val sanitizedText = sanitizePresentationText(span.text, span.fontFamily)
            if (sanitizedText.isEmpty()) continue

            val startIndex = length
            append(sanitizedText)
            val endIndex = length

            val textDecorations = mutableListOf<TextDecoration>()
            if (span.isUnderline) textDecorations.add(TextDecoration.Underline)
            if (span.isStrikethrough) textDecorations.add(TextDecoration.LineThrough)
            val combinedDecoration = when {
                textDecorations.size == 2 -> TextDecoration.combine(textDecorations)
                textDecorations.size == 1 -> textDecorations.first()
                else -> TextDecoration.None
            }

            // Ensure color is never transparent black
            val spanColor = when {
                span.colorArgb != null && (span.colorArgb != 0) -> Color(span.colorArgb)
                span.linkUrl != null -> Color(0xFF388AF6)
                else -> defaultColor
            }

            val rawSize = if (span.fontSize > 0) span.fontSize else 14f
            val minFontSize = if (fontScale < 0.4f) 5f else 8f
            val scaledSize = (rawSize * fontScale).coerceIn(minFontSize, 64f)

            val composeFontFamily = resolveFontFamily(span.fontFamily)
            val spanBgColor = span.backgroundColorArgb?.let { Color(it) } ?: Color.Unspecified

            val spanStyle = SpanStyle(
                color = spanColor,
                fontSize = scaledSize.sp,
                fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = composeFontFamily,
                background = spanBgColor,
                textDecoration = combinedDecoration,
                baselineShift = when {
                    span.isSuperscript -> BaselineShift.Superscript
                    span.isSubscript -> BaselineShift.Subscript
                    else -> BaselineShift.None
                }
            )

            addStyle(spanStyle, startIndex, endIndex)

            val url = span.linkUrl
            if (!url.isNullOrBlank()) {
                addLink(
                    androidx.compose.ui.text.LinkAnnotation.Url(
                        url = url,
                        linkInteractionListener = if (onLinkClick != null) { { onLinkClick(url) } } else null
                    ),
                    startIndex,
                    endIndex
                )
            }
        }

        if (searchQuery.isNotBlank()) {
            val plainText = spans.joinToString("") { sanitizePresentationText(it.text, it.fontFamily) }
            var index = plainText.indexOf(searchQuery, ignoreCase = true)
            while (index >= 0) {
                addStyle(
                    SpanStyle(
                        background = Color(0xFFFFEB3B), // Bright yellow highlight
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    ),
                    index,
                    index + searchQuery.length
                )
                index = plainText.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
            }
        }
    }
}
