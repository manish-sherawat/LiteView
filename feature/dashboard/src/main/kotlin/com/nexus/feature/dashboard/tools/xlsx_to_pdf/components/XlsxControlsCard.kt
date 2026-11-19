package com.nexus.feature.dashboard.tools.xlsx_to_pdf.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.nexus.feature.dashboard.tools.xlsx_to_pdf.*

@Composable
fun XlsxControlsCard(
    fileName: String,
    sheetCount: Int,
    formattedSize: String,
    selectedCount: Int,
    options: XlsxOptions,
    onOptionsChange: (XlsxOptions) -> Unit,
    onSelectAllOrClear: () -> Unit,
    onInvert: () -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean,
    progress: Float?,
    errorMessage: String?,
    onChangeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)
    val isAllSelected = selectedCount == sheetCount && sheetCount > 0
    val canInvert = sheetCount >= 2 && selectedCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        // ── Card Title & Subtitle ────────────────────────────────────────────
        NexusText(
            text = "Excel to PDF",
            style = NexusTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp
            ),
            color = NexusTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        NexusText(
            text = "Each selected sheet starts on a new page",
            style = NexusTheme.typography.body.copy(
                fontSize = 14.5.sp
            ),
            color = NexusTheme.colors.textSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── File Row with Back / Change Button ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, lineStrong, RoundedCornerShape(12.dp))
                    .background(NexusTheme.colors.surface)
                    .springBounceClick { onChangeFile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Choose a different file",
                    modifier = Modifier.size(18.dp),
                    tint = NexusTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                NexusText(
                    text = fileName,
                    style = NexusTheme.typography.body.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = NexusTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                NexusText(
                    text = "$sheetCount sheet${if (sheetCount == 1) "" else "s"} · $formattedSize",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Options Row: Orientation, Page size, Text size ───────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Orientation Selector
            Column(modifier = Modifier.weight(1f)) {
                OptionLabel("Orientation")
                OptionDropdown(
                    currentValue = options.orientation.label,
                    options = PageOrientation.entries.map { it.label },
                    onSelect = { label ->
                        val found = PageOrientation.entries.firstOrNull { it.label == label } ?: PageOrientation.AUTO
                        onOptionsChange(options.copy(orientation = found))
                    }
                )
            }

            // Page Size Selector
            Column(modifier = Modifier.weight(1f)) {
                OptionLabel("Page size")
                OptionDropdown(
                    currentValue = options.pageSize.label,
                    options = PageSizeOption.entries.map { it.label },
                    onSelect = { label ->
                        val found = PageSizeOption.entries.firstOrNull { it.label == label } ?: PageSizeOption.A4
                        onOptionsChange(options.copy(pageSize = found))
                    }
                )
            }

            // Text Size Selector
            Column(modifier = Modifier.weight(1f)) {
                OptionLabel("Text size")
                OptionDropdown(
                    currentValue = options.textSize.label,
                    options = TextSizeOption.entries.map { it.label },
                    onSelect = { label ->
                        val found = TextSizeOption.entries.firstOrNull { it.label == label } ?: TextSizeOption.AUTO
                        onOptionsChange(options.copy(textSize = found))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Toggles / Checkboxes Row ─────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckboxRow(
                text = "Fit all columns on page width",
                checked = options.fitAllColumns,
                onCheckedChange = { onOptionsChange(options.copy(fitAllColumns = it)) }
            )
            CheckboxRow(
                text = "First row is a header (repeat on each page)",
                checked = options.repeatHeader,
                onCheckedChange = { onOptionsChange(options.copy(repeatHeader = it)) }
            )
            CheckboxRow(
                text = "Show gridlines",
                checked = options.showGridlines,
                onCheckedChange = { onOptionsChange(options.copy(showGridlines = it)) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Selection Count & Quick Actions Row ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val annotatedCount = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary)) {
                    append("$selectedCount")
                }
                append(" of ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary)) {
                    append("$sheetCount")
                }
                append(" sheet${if (sheetCount == 1) "" else "s"} selected")
            }

            NexusText(
                text = annotatedCount.text,
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.sp
                ),
                color = NexusTheme.colors.textSecondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Invert Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canInvert) { onInvert() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    NexusText(
                        text = "Invert",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (canInvert) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.4f)
                    )
                }

                // Select All / Clear Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectAllOrClear() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    NexusText(
                        text = if (isAllSelected) "Clear" else "Select all",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Primary Convert Button ───────────────────────────────────────────
        val canExport = selectedCount > 0 && !isExporting
        val buttonText = when {
            isExporting -> "Converting…"
            selectedCount == 1 -> "Convert 1 sheet to PDF"
            else -> "Convert $selectedCount sheets to PDF"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (canExport) Color(0xFF0A0A0A)
                    else Color(0xFF0A0A0A).copy(alpha = 0.35f)
                )
                .springBounceClick(enabled = canExport) { onExport() }
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            NexusText(
                text = buttonText,
                style = NexusTheme.typography.buttonLabel.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
        }

        // ── Conversion Progress Bar ──────────────────────────────────────────
        if (progress != null || isExporting) {
            Spacer(modifier = Modifier.height(14.dp))
            val animatedProgress by animateFloatAsState(
                targetValue = (progress ?: 0.1f).coerceIn(0f, 1f),
                animationSpec = tween(180),
                label = "xlsxExportProgress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Color(0xFF0A0A0A))
                )
            }
        }

        // ── Status / Error Message ───────────────────────────────────────────
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            NexusText(
                text = errorMessage,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFFEF4444)
            )
        }
    }
}

@Composable
private fun OptionLabel(text: String) {
    NexusText(
        text = text.uppercase(),
        style = NexusTheme.typography.caption.copy(
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        ),
        color = NexusTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun OptionDropdown(
    currentValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.2.dp, Color(0xFFEBEBEB), RoundedCornerShape(12.dp))
                .background(Color(0xFFFAFAFA))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            NexusText(
                text = currentValue,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(NexusTheme.colors.surface)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        NexusText(
                            text = opt,
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.sp,
                                fontWeight = if (opt == currentValue) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = NexusTheme.colors.textPrimary
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

@Composable
private fun CheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF0A0A0A),
                checkmarkColor = Color.White,
                uncheckedColor = Color(0xFFCFCFCF)
            ),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        NexusText(
            text = text,
            style = NexusTheme.typography.body.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = NexusTheme.colors.textPrimary
        )
    }
}

