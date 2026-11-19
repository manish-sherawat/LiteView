package com.nexus.feature.dashboard.tools.compress_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.compress_pdf.CompressEstimates
import com.nexus.feature.dashboard.tools.compress_pdf.CompressPdfConfig
import com.nexus.feature.dashboard.tools.compress_pdf.CompressPdfDocInfo
import com.nexus.feature.dashboard.tools.compress_pdf.CompressPdfEngine
import com.nexus.feature.dashboard.tools.compress_pdf.CompressPreset

@Composable
fun CompressControlsCard(
    docInfo: CompressPdfDocInfo,
    config: CompressPdfConfig,
    estimates: CompressEstimates?,
    estimatedSizeBytes: Long,
    isBusy: Boolean,
    progress: Float,
    statusMessage: String?,
    isError: Boolean,
    onQualityChanged: (Int) -> Unit,
    onKeepTextChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onPreview: () -> Unit,
    onCompress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Title & Subtitle ──────────────────────────────────────────────
            NexusText(
                text = "Compress PDF",
                style = NexusTheme.typography.h2.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = NexusTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            NexusText(
                text = "Make the file smaller, and see how it will look first.",
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.5.sp
                ),
                color = NexusTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── File Row with Folded PDF Badge & Clear Button ─────────────────
            FileHeaderRow(
                name = docInfo.name,
                meta = "${docInfo.pageCount} page${if (docInfo.pageCount == 1) "" else "s"} · ${CompressPdfEngine.formatFileSize(docInfo.sizeBytes)}",
                onClear = onClear
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Quality Label & Pill ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusText(
                    text = "QUALITY",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(NexusTheme.colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    NexusText(
                        text = config.qualityName,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Estimate Box ──────────────────────────────────────────────────
            EstimateBox(
                actualSize = docInfo.sizeBytes,
                estimatedSize = estimatedSizeBytes,
                quality = config.quality,
                estimates = estimates,
                onQualityChanged = onQualityChanged
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── Switch Row: Keep Text Selectable ──────────────────────────────
            KeepTextSwitchRow(
                keepText = config.keepTextSelectable,
                enabled = !isBusy,
                onToggle = { onKeepTextChanged(!config.keepTextSelectable) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Action Buttons Stack ──────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Preview Button (Ghost)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NexusTheme.colors.surface)
                        .border(1.2.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .springBounceClick(enabled = !isBusy) { onPreview() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RemoveRedEye,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = NexusTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    NexusText(
                        text = "Preview",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }

                // Compress Button (Primary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isBusy) NexusTheme.colors.textPrimary.copy(alpha = 0.5f) else NexusTheme.colors.textPrimary)
                        .springBounceClick(enabled = !isBusy) { onCompress() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = NexusTheme.colors.background,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = "Compressing…",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.background
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_compress_pdf),
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = NexusTheme.colors.background
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = "Compress",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.background
                        )
                    }
                }
            }

            // ── Progress Bar ──────────────────────────────────────────────────
            if (isBusy) {
                Spacer(modifier = Modifier.height(14.dp))
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NexusTheme.colors.textPrimary,
                        trackColor = NexusTheme.colors.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NexusTheme.colors.textPrimary,
                        trackColor = NexusTheme.colors.surfaceVariant
                    )
                }
            }

            // ── Status / Error Message ────────────────────────────────────────
            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                NexusText(
                    text = statusMessage,
                    style = NexusTheme.typography.bodySmall.copy(
                        fontSize = 13.5.sp,
                        fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isError) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary
                )
            }
        }
    }
}

/**
 * File Header Row with PDF badge and Clear button.
 */
@Composable
private fun FileHeaderRow(
    name: String,
    meta: String,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folded PDF Badge
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 46.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(NexusTheme.colors.textPrimary)
                .drawBehind {
                    // Folded corner triangle effect
                    val foldSize = 10.dp.toPx()
                    val path = Path().apply {
                        moveTo(size.width - foldSize, 0f)
                        lineTo(size.width, foldSize)
                        lineTo(size.width - foldSize, foldSize)
                        close()
                    }
                    drawPath(path, color = Color(0xFF666666))
                },
            contentAlignment = Alignment.Center
        ) {
            NexusText(
                text = "PDF",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                ),
                color = NexusTheme.colors.background
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = name,
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            NexusText(
                text = meta,
                style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                color = NexusTheme.colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Clear Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                .springBounceClick { onClear() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            NexusText(
                text = "Clear",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary
            )
        }
    }
}

/**
 * Live Estimate Box with percentage badge, slider, and preset buttons.
 */
@Composable
private fun EstimateBox(
    actualSize: Long,
    estimatedSize: Long,
    quality: Int,
    estimates: CompressEstimates?,
    onQualityChanged: (Int) -> Unit
) {
    val pct = if (actualSize > 0) {
        (((actualSize - estimatedSize).toDouble() / actualSize) * 100).toInt()
    } else 0
    val isWorse = pct < 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top numbers row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Actual size
                Column {
                    NexusText(
                        text = "Actual",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NexusText(
                        text = CompressPdfEngine.formatFileSize(actualSize),
                        style = NexusTheme.typography.titleLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }

                // Estimated size + percentage badge
                Column(horizontalAlignment = Alignment.End) {
                    NexusText(
                        text = "Estimated",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NexusText(
                            text = "~${CompressPdfEngine.formatFileSize(estimatedSize)}",
                            style = NexusTheme.typography.h2.copy(
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = NexusTheme.colors.textPrimary
                        )

                        // Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isWorse) NexusTheme.colors.surface else NexusTheme.colors.textPrimary)
                                .border(
                                    1.2.dp,
                                    if (isWorse) NexusTheme.colors.textPrimary else Color.Transparent,
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            NexusText(
                                text = (if (isWorse) "+" else "−") + "${kotlin.math.abs(pct)}%",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isWorse) NexusTheme.colors.textPrimary else NexusTheme.colors.background
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Range Liquid Slider (10 to 100)
            NexusLiquidSlider(
                value = quality.toFloat(),
                onValueChange = { onQualityChanged(it.roundToInt()) },
                valueRange = 10f..100f,
                accentColor = NexusTheme.colors.primary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Presets: Low (20), Medium (50), High (80)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompressPreset.entries.forEach { preset ->
                    val isSelected = quality == preset.quality
                    val presetSize = when (preset) {
                        CompressPreset.LOW -> estimates?.let { CompressPdfEngine.formatFileSize(it.lowBytes) }
                        CompressPreset.MEDIUM -> estimates?.let { CompressPdfEngine.formatFileSize(it.medBytes) }
                        CompressPreset.HIGH -> estimates?.let { CompressPdfEngine.formatFileSize(it.highBytes) }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.surface)
                            .border(
                                1.5.dp,
                                if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.divider.copy(alpha = 0.8f),
                                RoundedCornerShape(14.dp)
                            )
                            .springBounceClick { onQualityChanged(preset.quality) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            NexusText(
                                text = preset.label,
                                style = NexusTheme.typography.body.copy(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (isSelected) NexusTheme.colors.background else NexusTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            NexusText(
                                text = presetSize?.let { "~$it" } ?: "…",
                                style = NexusTheme.typography.caption.copy(fontSize = 11.sp),
                                color = if (isSelected) NexusTheme.colors.background.copy(alpha = 0.75f) else NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Toggle Row: Keep Text Selectable.
 */
@Composable
private fun KeepTextSwitchRow(
    keepText: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggle() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = "Keep text selectable",
                style = NexusTheme.typography.body.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            NexusText(
                text = if (keepText) {
                    "Keeps real text and shrinks the pictures inside. Best for documents; savings can be small for text-only files."
                } else {
                    "Turns each page into a picture for the smallest size. Text can’t be selected or searched."
                },
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                ),
                color = NexusTheme.colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Custom Animated Switch Pill
        val switchBg by animateColorAsState(
            targetValue = if (keepText) NexusTheme.colors.textPrimary else NexusTheme.colors.surfaceVariant,
            animationSpec = tween(220),
            label = "switchBg"
        )
        val thumbOffset by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (keepText) 20.dp else 0.dp,
            animationSpec = tween(220),
            label = "thumbOffset"
        )

        Box(
            modifier = Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(switchBg)
                .border(1.5.dp, if (keepText) NexusTheme.colors.textPrimary else NexusTheme.colors.divider.copy(alpha = 0.8f), CircleShape)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.surface)
                    .shadow(elevation = 2.dp, shape = CircleShape)
            )
        }
    }
}
