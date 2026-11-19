package com.nexus.feature.dashboard.tools.ppt_to_pdf.components

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun PptControlsCard(
    fileName: String,
    slideCount: Int,
    formattedSize: String,
    selectedCount: Int,
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
    val isAllSelected = selectedCount == slideCount && slideCount > 0
    val canInvert = slideCount >= 2 && selectedCount > 0

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
            text = "PowerPoint Slides to PDF",
            style = NexusTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp
            ),
            color = NexusTheme.colors.textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        NexusText(
            text = "Each selected slide becomes one PDF page",
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
                    text = "$slideCount slide${if (slideCount == 1) "" else "s"} · $formattedSize",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Selection Count & Quick Actions Row ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "K of N selected" counter
            val annotatedCount = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary)) {
                    append("$selectedCount")
                }
                append(" of ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary)) {
                    append("$slideCount")
                }
                append(" selected")
            }

            NexusText(
                text = annotatedCount.text,
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.5.sp
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
            selectedCount == 1 -> "Convert 1 slide to PDF"
            else -> "Convert $selectedCount slides to PDF"
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
                label = "pptExportProgress"
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
