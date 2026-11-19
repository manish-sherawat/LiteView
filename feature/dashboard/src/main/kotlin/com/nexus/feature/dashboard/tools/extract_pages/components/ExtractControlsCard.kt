package com.nexus.feature.dashboard.tools.extract_pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText

@Composable
fun ExtractControlsCard(
    fileName: String,
    pageCount: Int,
    formattedSize: String,
    rangeInput: String,
    onRangeInputChange: (String) -> Unit,
    onApplyRange: () -> Unit,
    rangeErrorMessage: String?,
    selectedCount: Int,
    onSelectAllOrClear: () -> Unit,
    onInvert: () -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean,
    onChangeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)
    val isAllSelected = selectedCount == pageCount && pageCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        // ── Card Title & Subtitle ────────────────────────────────────────────
        NexusText(
            text = "Extract Pages",
            style = NexusTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp
            ),
            color = Color(0xFF0A0A0A)
        )

        Spacer(modifier = Modifier.height(4.dp))

        NexusText(
            text = "Save the pages you pick as a new PDF",
            style = NexusTheme.typography.body.copy(
                fontSize = 14.5.sp
            ),
            color = Color(0xFF707070)
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
                    .clickable { onChangeFile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Choose a different file",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF0A0A0A)
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
                    color = Color(0xFF0A0A0A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                NexusText(
                    text = "$pageCount page${if (pageCount == 1) "" else "s"} · $formattedSize",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = Color(0xFF707070)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Range Input Label ────────────────────────────────────────────────
        NexusText(
            text = "PAGE NUMBERS OR RANGES",
            style = NexusTheme.typography.label.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color(0xFF707070)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Range Input Field & Select Button ────────────────────────────────
        val isRangeError = !rangeErrorMessage.isNullOrBlank()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFAFAFA))
                    .border(
                        width = 1.5.dp,
                        color = if (isRangeError) Color(0xFF0A0A0A) else borderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (rangeInput.isEmpty()) {
                    NexusText(
                        text = if (pageCount > 1) "e.g. 1-${minOf(pageCount, 5)}" else "e.g. 1",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFA3A3A3)
                    )
                }
                BasicTextField(
                    value = rangeInput,
                    onValueChange = onRangeInputChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF0A0A0A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.5.dp, lineStrong, RoundedCornerShape(16.dp))
                    .clickable { onApplyRange() }
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "Select",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF0A0A0A)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Range Helper / Error Text ────────────────────────────────────────
        NexusText(
            text = if (isRangeError) rangeErrorMessage ?: "" else "Use PDF page numbers, e.g. 187-230 or 1,5,10-20.",
            style = NexusTheme.typography.caption.copy(
                fontSize = 13.sp,
                fontWeight = if (isRangeError) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isRangeError) Color(0xFF0A0A0A) else Color(0xFF707070)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── Selection Count & Invert / Select All Controls ───────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val countString = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0A0A0A))) {
                    append("$selectedCount")
                }
                append(" of ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0A0A0A))) {
                    append("$pageCount")
                }
                append(" selected")
            }

            NexusText(
                text = countString.text,
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF707070)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Invert Button (disabled if pageCount < 2 or selectedCount == 0)
                val canInvert = pageCount > 1 && selectedCount > 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canInvert) { onInvert() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    NexusText(
                        text = "Invert",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (canInvert) Color(0xFF0A0A0A) else Color(0xFFA3A3A3)
                    )
                }

                // Select All / Clear Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectAllOrClear() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    NexusText(
                        text = if (isAllSelected) "Clear" else "Select all",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF0A0A0A)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Primary Export Button ────────────────────────────────────────────
        val canExport = selectedCount > 0 && !isExporting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (canExport) Color(0xFF0A0A0A) else Color(0xFFEBEBEB))
                .clickable(enabled = canExport) { onExport() }
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                NexusText(
                    text = "Exporting…",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (canExport) Color.White else Color(0xFF707070)
                )
                Spacer(modifier = Modifier.width(10.dp))
                NexusText(
                    text = "Export $selectedCount selected page${if (selectedCount == 1) "" else "s"}",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (canExport) Color.White else Color(0xFF707070)
                )
            }
        }
    }
}
