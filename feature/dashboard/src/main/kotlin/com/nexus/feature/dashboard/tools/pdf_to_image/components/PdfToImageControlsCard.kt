package com.nexus.feature.dashboard.tools.pdf_to_image.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.pdf_to_image.*
import kotlin.math.roundToInt

@Composable
fun PdfToImageControlsCard(
    config: PdfToImageConfig,
    docInfo: PdfDocInfo?,
    embeddedImagesCount: Int,
    isScanningImages: Boolean,
    onConfigChange: (PdfToImageConfig) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isImagesMode = config.mode == ExportMode.IMAGES
    val isPng = config.format == ImageFormat.PNG

    // Calculate approximate output dimensions for hint
    val outputWidth = ((docInfo?.basePageWidth ?: 595) * config.resolutionScale).roundToInt()
    val outputHeight = ((docInfo?.basePageHeight ?: 842) * config.resolutionScale).roundToInt()

    // Parse valid custom range count if applicable
    val totalPages = docInfo?.pageCount ?: 1
    val selectedPagesCount = if (config.pagesSelectionMode == PagesSelectionMode.ALL) {
        totalPages
    } else {
        PdfToImageEngine.parsePageRange(config.customPagesRange, totalPages)?.size
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // ── Card Header: Picture Settings & Reset ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = NexusTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusText(
                        text = "Picture settings",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }

                NexusText(
                    text = "Reset",
                    style = NexusTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .springBounceClick { onReset() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // ── Field 1: Mode (Pages vs Images) ───────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusText(
                    text = "MODE",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                val imagesLabel = when {
                    isScanningImages -> "Images (…)"
                    embeddedImagesCount > 0 -> "Images ($embeddedImagesCount)"
                    else -> "Images (0)"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        SegmentedOption(
                            label = "Pages",
                            isSelected = config.mode == ExportMode.PAGES,
                            enabled = true,
                            onClick = { onConfigChange(config.copy(mode = ExportMode.PAGES)) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOption(
                            label = imagesLabel,
                            isSelected = config.mode == ExportMode.IMAGES,
                            enabled = embeddedImagesCount > 0,
                            onClick = { onConfigChange(config.copy(mode = ExportMode.IMAGES)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Field 2: Format (JPEG, PNG, WEBP) ─────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusText(
                    text = "FORMAT",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        ImageFormat.entries.forEach { fmt ->
                            SegmentedOption(
                                label = fmt.label,
                                isSelected = config.format == fmt,
                                enabled = true,
                                onClick = { onConfigChange(config.copy(format = fmt)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Field 3: Quality Slider (Lossy Formats) ───────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isPng) 0.38f else 1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "QUALITY",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        NexusText(
                            text = "${config.qualityPct}%",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                NexusLiquidSlider(
                    value = config.qualityPct.toFloat(),
                    onValueChange = { onConfigChange(config.copy(qualityPct = it.roundToInt())) },
                    valueRange = 10f..100f,
                    enabled = !isPng,
                    accentColor = NexusTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isPng) {
                    NexusText(
                        text = "PNG is lossless, so quality doesn't apply.",
                        style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }

            // ── Field 4: Resolution Slider ────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isImagesMode) 0.38f else 1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "RESOLUTION",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        NexusText(
                            text = String.format("%.2f×", config.resolutionScale),
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                NexusLiquidSlider(
                    value = config.resolutionScale,
                    onValueChange = {
                        val rounded = (it * 20).roundToInt() / 20f
                        onConfigChange(config.copy(resolutionScale = rounded))
                    },
                    valueRange = 0.5f..4.0f,
                    enabled = !isImagesMode,
                    accentColor = NexusTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth()
                )

                NexusText(
                    text = if (isImagesMode) {
                        "Embedded images extract at original source size"
                    } else {
                        "Output ≈ $outputWidth × $outputHeight px per page"
                    },
                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                    color = NexusTheme.colors.textSecondary
                )
            }

            // ── Field 5: Pages Selection (All Pages vs Custom) ────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isImagesMode) 0.38f else 1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "PAGES",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    if (!isImagesMode) {
                        val badgeText = if (selectedPagesCount != null) {
                            "$selectedPagesCount of $totalPages"
                        } else {
                            "Invalid"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            NexusText(
                                text = badgeText,
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (selectedPagesCount != null) NexusTheme.colors.textPrimary else NexusTheme.colors.error
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        SegmentedOption(
                            label = "All pages",
                            isSelected = config.pagesSelectionMode == PagesSelectionMode.ALL,
                            enabled = !isImagesMode,
                            onClick = { onConfigChange(config.copy(pagesSelectionMode = PagesSelectionMode.ALL)) },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedOption(
                            label = "Custom",
                            isSelected = config.pagesSelectionMode == PagesSelectionMode.CUSTOM,
                            enabled = !isImagesMode,
                            onClick = { onConfigChange(config.copy(pagesSelectionMode = PagesSelectionMode.CUSTOM)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Custom page range input
                if (config.pagesSelectionMode == PagesSelectionMode.CUSTOM && !isImagesMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                width = 1.2.dp,
                                color = if (selectedPagesCount != null) NexusTheme.colors.divider.copy(alpha = 0.8f) else NexusTheme.colors.error,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (config.customPagesRange.isEmpty()) {
                            NexusText(
                                text = "e.g. 1-3, 5, 8",
                                style = TextStyle(
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.6f)
                                )
                            )
                        }

                        BasicTextField(
                            value = config.customPagesRange,
                            onValueChange = { onConfigChange(config.copy(customPagesRange = it)) },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NexusTheme.colors.textPrimary
                            ),
                            cursorBrush = SolidColor(NexusTheme.colors.textPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedOption(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.textPrimary else Color.Transparent,
        animationSpec = tween(160),
        label = "segBg"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> NexusTheme.colors.textSecondary.copy(alpha = 0.4f)
            isSelected -> NexusTheme.colors.background
            else -> NexusTheme.colors.textSecondary
        },
        animationSpec = tween(160),
        label = "segColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = textColor
        )
    }
}
