package com.nexus.feature.dashboard.tools.txt_to_pdf.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.txt_to_pdf.*

/**
 * Main format & edit workspace card containing text editor, live word/page stats,
 * and comprehensive typography & layout controls.
 */
@Composable
fun TxtWorkspaceCard(
    docInfo: TxtDocInfo,
    titleText: String,
    onTitleChange: (String) -> Unit,
    bodyText: String,
    onBodyTextChange: (String) -> Unit,
    stats: TxtStats,
    options: TxtOptions,
    onOptionsChange: (TxtOptions) -> Unit,
    onBackToStart: () -> Unit,
    onReplaceWithFile: () -> Unit,
    onClearText: () -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val textPrimary = NexusTheme.colors.textPrimary
    val mutedColor = Color(0xFF707070)
    val labelColor = Color(0xFF707070)
    val inputBg = Color(0xFFFAFAFA)
    val inputBorder = Color(0xFFEBEBEB)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        // ── Card Header ──────────────────────────────────────────────────────
        NexusText(
            text = "Text to PDF",
            style = NexusTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        NexusText(
            text = "Your text becomes a searchable, selectable PDF",
            style = NexusTheme.typography.body.copy(
                fontSize = 13.5.sp,
                color = mutedColor
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── File Info Row with Back Button ───────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, Color(0xFFCFCFCF), RoundedCornerShape(12.dp))
                    .clickable { onBackToStart() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Start over",
                    tint = textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                NexusText(
                    text = docInfo.fileName,
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NexusText(
                    text = docInfo.fileMeta,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        color = mutedColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ── Title Input (Optional) ───────────────────────────────────────────
        NexusText(
            text = "TITLE (OPTIONAL)",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = labelColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = titleText,
            onValueChange = onTitleChange,
            placeholder = {
                NexusText(
                    text = "e.g. Meeting notes",
                    style = NexusTheme.typography.body.copy(fontSize = 14.sp, color = Color(0xFFA3A3A3))
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = inputBg,
                focusedBorderColor = textPrimary,
                unfocusedBorderColor = inputBorder
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── Your Text Editor ─────────────────────────────────────────────────
        NexusText(
            text = "YOUR TEXT",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = labelColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = bodyText,
            onValueChange = onBodyTextChange,
            placeholder = {
                NexusText(
                    text = "Paste or type your text here…",
                    style = NexusTheme.typography.body.copy(fontSize = 14.sp, color = Color(0xFFA3A3A3))
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 360.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = inputBg,
                focusedBorderColor = textPrimary,
                unfocusedBorderColor = inputBorder
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Text Stats Bar & Quick Action Links ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Stats
            val statsAnnotated = buildAnnotatedString {
                if (bodyText.isBlank()) {
                    withStyle(SpanStyle(color = mutedColor)) {
                        append("No text yet")
                    }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textPrimary)) {
                        append("${stats.words}")
                    }
                    withStyle(SpanStyle(color = mutedColor)) {
                        append(" words · ")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textPrimary)) {
                        append("${stats.characters}")
                    }
                    withStyle(SpanStyle(color = mutedColor)) {
                        append(" chars · about ")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textPrimary)) {
                        append("${stats.estimatedPages}")
                    }
                    withStyle(SpanStyle(color = mutedColor)) {
                        append(if (stats.estimatedPages == 1) " page" else " pages")
                    }
                }
            }

            NexusText(
                text = statsAnnotated.text,
                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
                color = mutedColor,
                modifier = Modifier.weight(1f, fill = false)
            )

            // Links: Replace with file / Clear
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexusText(
                    text = "Replace",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onReplaceWithFile() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                NexusText(
                    text = "Clear",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClearText() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = borderColor)
        Spacer(modifier = Modifier.height(18.dp))

        // ── Options Grid ─────────────────────────────────────────────────────
        // Row 1: Page Size & Orientation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                DropdownField(
                    label = "PAGE SIZE",
                    selectedValue = options.pageSize.label,
                    options = TxtPageSize.values().map { it.label },
                    onSelect = { label ->
                        val selected = TxtPageSize.values().find { it.label == label } ?: options.pageSize
                        onOptionsChange(options.copy(pageSize = selected))
                    }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DropdownField(
                    label = "ORIENTATION",
                    selectedValue = options.orientation.label,
                    options = TxtOrientation.values().map { it.label },
                    onSelect = { label ->
                        val selected = TxtOrientation.values().find { it.label == label } ?: options.orientation
                        onOptionsChange(options.copy(orientation = selected))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2: Font Family & Font Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1.3f)) {
                DropdownField(
                    label = "FONT",
                    selectedValue = options.fontFamily.label,
                    options = TxtFontFamily.values().map { it.label },
                    onSelect = { label ->
                        val selected = TxtFontFamily.values().find { it.label == label } ?: options.fontFamily
                        onOptionsChange(options.copy(fontFamily = selected))
                    }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DropdownField(
                    label = "FONT SIZE",
                    selectedValue = options.fontSize.label,
                    options = TxtFontSize.values().map { it.label },
                    onSelect = { label ->
                        val selected = TxtFontSize.values().find { it.label == label } ?: options.fontSize
                        onOptionsChange(options.copy(fontSize = selected))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 3: Line Spacing & Margins
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                DropdownField(
                    label = "LINE SPACING",
                    selectedValue = options.lineSpacing.label,
                    options = TxtLineSpacing.values().map { it.label },
                    onSelect = { label ->
                        val selected = TxtLineSpacing.values().find { it.label == label } ?: options.lineSpacing
                        onOptionsChange(options.copy(lineSpacing = selected))
                    }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                DropdownField(
                    label = "MARGINS",
                    selectedValue = options.margin.label,
                    options = TxtMarginOption.values().map { it.label },
                    onSelect = { label ->
                        val selected = TxtMarginOption.values().find { it.label == label } ?: options.margin
                        onOptionsChange(options.copy(margin = selected))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Checkboxes ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOptionsChange(options.copy(pageNumbers = !options.pageNumbers)) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Checkbox(
                    checked = options.pageNumbers,
                    onCheckedChange = { onOptionsChange(options.copy(pageNumbers = it)) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF0A0A0A),
                        checkmarkColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                NexusText(
                    text = "Page numbers",
                    style = NexusTheme.typography.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = textPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOptionsChange(options.copy(wrapLines = !options.wrapLines)) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Checkbox(
                    checked = options.wrapLines,
                    onCheckedChange = { onOptionsChange(options.copy(wrapLines = it)) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF0A0A0A),
                        checkmarkColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                NexusText(
                    text = "Wrap long lines",
                    style = NexusTheme.typography.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                    color = textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Primary Convert to PDF Button ────────────────────────────────────
        val canExport = bodyText.isNotBlank() && !isExporting

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (canExport) Color(0xFF0A0A0A) else Color(0xFF0A0A0A).copy(alpha = 0.35f))
                .springBounceClick(enabled = canExport, onClick = onExport),
            contentAlignment = Alignment.Center
        ) {
            if (isExporting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    NexusText(
                        text = "Converting…",
                        style = NexusTheme.typography.buttonLabel.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusText(
                        text = "Convert to PDF",
                        style = NexusTheme.typography.buttonLabel.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }

        // Status or Error Message
        AnimatedVisibility(
            visible = !errorMessage.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            errorMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                NexusText(
                    text = it,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFDC2626)
                    )
                )
            }
        }
    }
}

/**
 * Reusable dropdown option selector matching the reference design.
 */
@Composable
private fun DropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = Color(0xFF707070)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.2.dp, Color(0xFFEBEBEB), RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            NexusText(
                text = selectedValue,
                style = NexusTheme.typography.body.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color.White)
                    .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(8.dp))
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            NexusText(
                                text = opt,
                                style = NexusTheme.typography.body.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (opt == selectedValue) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (opt == selectedValue) Color(0xFF0A0A0A) else Color(0xFF404040)
                            )
                        },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

