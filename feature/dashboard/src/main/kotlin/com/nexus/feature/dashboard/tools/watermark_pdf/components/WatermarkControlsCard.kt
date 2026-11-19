package com.nexus.feature.dashboard.tools.watermark_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Icon
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.watermark_pdf.WatermarkConfig
import com.nexus.feature.dashboard.tools.watermark_pdf.WatermarkInk
import com.nexus.feature.dashboard.tools.watermark_pdf.WatermarkLayoutMode

@Composable
fun WatermarkControlsCard(
    config: WatermarkConfig,
    onConfigChange: (WatermarkConfig) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // ── Card Header: Customize & Reset ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NexusText(
                    text = "Customize",
                    style = NexusTheme.typography.title.copy(
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = NexusTheme.colors.textPrimary
                )

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

            // ── Field 1: Watermark Text Input ─────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NexusText(
                    text = "Watermark text",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.textPrimary
                )

                // Input Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.2.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (config.text.isEmpty()) {
                        NexusText(
                            text = "Type your text",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = NexusTheme.colors.textSecondary.copy(alpha = 0.6f)
                            )
                        )
                    }

                    BasicTextField(
                        value = config.text,
                        onValueChange = { newText ->
                            if (newText.length <= 40) {
                                onConfigChange(config.copy(text = newText))
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary,
                            letterSpacing = 0.5.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(NexusTheme.colors.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Preset Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    items(WatermarkConfig.PRESET_TEXTS) { preset ->
                        val isSelected = config.text.trim() == preset

                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.surface,
                            animationSpec = tween(180),
                            label = "chipBg"
                        )
                        val chipText by animateColorAsState(
                            targetValue = if (isSelected) NexusTheme.colors.background else NexusTheme.colors.textSecondary,
                            animationSpec = tween(180),
                            label = "chipText"
                        )
                        val chipBorder by animateColorAsState(
                            targetValue = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.divider.copy(alpha = 0.8f),
                            animationSpec = tween(180),
                            label = "chipBorder"
                        )

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(chipBg)
                                .border(1.dp, chipBorder, CircleShape)
                                .clickable { onConfigChange(config.copy(text = preset)) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            NexusText(
                                text = preset,
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp
                                ),
                                color = chipText
                            )
                        }
                    }
                }
            }

            // ── Field 2: Layout & Ink (Segmented Controls) ────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Layout Mode
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexusText(
                        text = "Layout",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )

                    SegmentedLayoutPicker(
                        selected = config.layoutMode,
                        onSelect = { onConfigChange(config.copy(layoutMode = it)) }
                    )
                }

                // Ink Mode
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexusText(
                        text = "Ink",
                        style = NexusTheme.typography.label.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )

                    SegmentedInkPicker(
                        selected = config.ink,
                        onSelect = { onConfigChange(config.copy(ink = it)) }
                    )
                }
            }

            // ── Field 3: Size Slider ──────────────────────────────────────────
            SliderControlItem(
                label = "Size",
                valueDisplay = "${config.sizePt.toInt()} pt",
                value = config.sizePt,
                range = 24f..160f,
                onValueChange = { onConfigChange(config.copy(sizePt = it)) }
            )

            // ── Field 4: Opacity Slider ───────────────────────────────────────
            SliderControlItem(
                label = "Opacity",
                valueDisplay = "${config.opacityPct}%",
                value = config.opacityPct.toFloat(),
                range = 5f..80f,
                onValueChange = { onConfigChange(config.copy(opacityPct = it.toInt())) }
            )

            // ── Field 5: Rotation Slider & Quick Angle Chips ─────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SliderControlItem(
                    label = "Rotation",
                    valueDisplay = "${config.rotationDeg.toInt()}°",
                    value = config.rotationDeg,
                    range = -90f..90f,
                    onValueChange = { onConfigChange(config.copy(rotationDeg = it)) }
                )

                // Quick Angle Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    WatermarkConfig.PRESET_ANGLES.forEach { angle ->
                        val isSelected = config.rotationDeg == angle

                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                            animationSpec = tween(180),
                            label = "angleBg"
                        )
                        val chipText by animateColorAsState(
                            targetValue = if (isSelected) NexusTheme.colors.background else NexusTheme.colors.textPrimary,
                            animationSpec = tween(180),
                            label = "angleText"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBg)
                                .clickable { onConfigChange(config.copy(rotationDeg = angle)) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            NexusText(
                                text = "${angle.toInt()}°",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = chipText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderControlItem(
    label: String,
    valueDisplay: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusText(
                text = label,
                style = NexusTheme.typography.label.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textPrimary
            )

            // Pill Badge with value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                NexusText(
                    text = valueDisplay,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            }
        }

        NexusLiquidSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            accentColor = NexusTheme.colors.primary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SegmentedLayoutPicker(
    selected: WatermarkLayoutMode,
    onSelect: (WatermarkLayoutMode) -> Unit
) {
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
                label = "Single",
                icon = Icons.Rounded.CenterFocusStrong,
                isSelected = selected == WatermarkLayoutMode.SINGLE,
                onClick = { onSelect(WatermarkLayoutMode.SINGLE) },
                modifier = Modifier.weight(1f)
            )
            SegmentedOption(
                label = "Tiled",
                icon = Icons.Rounded.GridOn,
                isSelected = selected == WatermarkLayoutMode.TILED,
                onClick = { onSelect(WatermarkLayoutMode.TILED) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentedInkPicker(
    selected: WatermarkInk,
    onSelect: (WatermarkInk) -> Unit
) {
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
                label = "Black",
                icon = null,
                isSelected = selected == WatermarkInk.BLACK,
                onClick = { onSelect(WatermarkInk.BLACK) },
                modifier = Modifier.weight(1f)
            )
            SegmentedOption(
                label = "Gray",
                icon = null,
                isSelected = selected == WatermarkInk.GRAY,
                onClick = { onSelect(WatermarkInk.GRAY) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentedOption(
    label: String,
    icon: ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.surface else Color.Transparent,
        animationSpec = tween(160),
        label = "segBg"
    )
    val textAndIconColor by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary,
        animationSpec = tween(160),
        label = "segColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable { onClick() },
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
                    modifier = Modifier.size(15.dp),
                    tint = textAndIconColor
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            NexusText(
                text = label,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textAndIconColor
            )
        }
    }
}
