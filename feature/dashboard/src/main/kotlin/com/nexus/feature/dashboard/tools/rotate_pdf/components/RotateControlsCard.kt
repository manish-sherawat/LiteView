package com.nexus.feature.dashboard.tools.rotate_pdf.components

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
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
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
import com.nexus.feature.dashboard.tools.rotate_pdf.RotatePdfDocInfo

/**
 * Control card providing bulk rotation buttons, range selector, selection tools,
 * and the primary download button.
 */
@Composable
fun RotateControlsCard(
    docInfo: RotatePdfDocInfo,
    selectedPagesCount: Int,
    totalRotatedCount: Int,
    onRotateAction: (delta: Int) -> Unit, // -90, 90, 180, or 0 (reset)
    rangeInputText: String,
    onRangeInputChange: (String) -> Unit,
    onApplyRange: () -> Unit,
    rangeError: String?,
    onSelectAllOrClear: () -> Unit,
    onSelectOdd: () -> Unit,
    onSelectEven: () -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean,
    errorMessage: String?,
    onChangeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val textPrimary = NexusTheme.colors.textPrimary
    val mutedColor = Color(0xFF707070)
    val labelColor = Color(0xFF707070)
    val inputBg = Color(0xFFFAFAFA)
    val inputBorder = Color(0xFFEBEBEB)

    val hasSelection = selectedPagesCount > 0
    val isAllSelected = selectedPagesCount == docInfo.pageCount && docInfo.pageCount > 0
    val canExport = totalRotatedCount > 0 && !isExporting

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
            text = "Rotate PDF Pages",
            style = NexusTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(2.dp))

        NexusText(
            text = "Tap pages to select them, or rotate everything at once",
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
                    .clickable { onChangeFile() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Choose different file",
                    tint = textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                NexusText(
                    text = docInfo.name,
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val pageWord = if (docInfo.pageCount == 1) "page" else "pages"
                val metaStr = "${docInfo.pageCount} $pageWord · ${com.nexus.feature.dashboard.tools.rotate_pdf.RotatePdfEngine.formatBytes(docInfo.sizeBytes)}"
                NexusText(
                    text = metaStr,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        color = mutedColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ── Rotation Action Buttons Row ──────────────────────────────────────
        NexusText(
            text = "ROTATE",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = labelColor
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left 90°
            RotationQuickButton(
                icon = Icons.AutoMirrored.Rounded.RotateLeft,
                text = "Left 90°",
                onClick = { onRotateAction(-90) },
                modifier = Modifier.weight(1f)
            )

            // Right 90°
            RotationQuickButton(
                icon = Icons.AutoMirrored.Rounded.RotateRight,
                text = "Right 90°",
                onClick = { onRotateAction(90) },
                modifier = Modifier.weight(1f)
            )

            // Flip 180°
            RotationQuickButton(
                icon = Icons.Rounded.Flip,
                text = "Flip 180°",
                onClick = { onRotateAction(180) },
                modifier = Modifier.weight(1f)
            )

            // Reset
            RotationQuickButton(
                icon = null,
                text = "Reset",
                enabled = totalRotatedCount > 0,
                onClick = { onRotateAction(0) },
                modifier = Modifier.weight(0.9f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Target Text ──────────────────────────────────────────────────────
        val targetAnnotated = buildAnnotatedString {
            if (hasSelection) {
                append("Rotation applies to ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textPrimary)) {
                    append("$selectedPagesCount selected ${if (selectedPagesCount == 1) "page" else "pages"}")
                }
                append(".")
            } else {
                append("No pages selected, so rotation applies to ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textPrimary)) {
                    append("all ${docInfo.pageCount} pages")
                }
                append(".")
            }
        }

        NexusText(
            text = targetAnnotated.text,
            style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
            color = mutedColor
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── Select by Page Numbers Field ─────────────────────────────────────
        NexusText(
            text = "SELECT BY PAGE NUMBERS",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = labelColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = rangeInputText,
                onValueChange = onRangeInputChange,
                placeholder = {
                    NexusText(
                        text = "e.g. 2-5, 8",
                        style = NexusTheme.typography.body.copy(fontSize = 14.sp, color = Color(0xFFA3A3A3))
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = inputBg,
                    focusedBorderColor = textPrimary,
                    unfocusedBorderColor = inputBorder
                )
            )

            Box(
                modifier = Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, Color(0xFFCFCFCF), RoundedCornerShape(12.dp))
                    .clickable { onApplyRange() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "Select",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Range helper or error text
        NexusText(
            text = rangeError ?: "Use PDF page numbers, e.g. 3 or 1,5,10-20.",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.5.sp,
                fontWeight = if (rangeError != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (rangeError != null) Color(0xFFDC2626) else mutedColor
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // ── Selection Count & Quick Filters ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val countAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textPrimary)) {
                    append("$selectedPagesCount")
                }
                append(" of ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textPrimary)) {
                    append("${docInfo.pageCount}")
                }
                append(" selected")
            }

            NexusText(
                text = countAnnotated.text,
                style = NexusTheme.typography.body.copy(fontSize = 13.sp),
                color = mutedColor
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                NexusText(
                    text = "Odd",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectOdd() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                NexusText(
                    text = "Even",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectEven() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                NexusText(
                    text = if (isAllSelected) "Clear" else "Select all",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectAllOrClear() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Primary Download Rotated PDF Button ──────────────────────────────
        val exportButtonLabel = if (isExporting) {
            "Saving…"
        } else if (totalRotatedCount > 0) {
            "Download PDF ($totalRotatedCount page${if (totalRotatedCount == 1) "" else "s"} rotated)"
        } else {
            "Download rotated PDF"
        }

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
                        text = "Saving…",
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
                        text = exportButtonLabel,
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
 * Quick button for rotation actions.
 */
@Composable
private fun RotationQuickButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val textPrimary = NexusTheme.colors.textPrimary
    val lineStrong = Color(0xFFCFCFCF)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                1.5.dp,
                if (enabled) lineStrong else lineStrong.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) textPrimary else textPrimary.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            NexusText(
                text = text,
                style = NexusTheme.typography.buttonLabel.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (enabled) textPrimary else textPrimary.copy(alpha = 0.35f)
            )
        }
    }
}

