package com.nexus.feature.dashboard.tools.image_to_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.nexus.core.ui.liquidglass.NexusLiquidSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.image_to_pdf.ImagePageItem
import com.nexus.feature.dashboard.tools.image_to_pdf.ImageToPdfConfig
import com.nexus.feature.dashboard.tools.image_to_pdf.PageSize
import kotlin.math.roundToInt

@Composable
fun ImageToPdfControlsCard(
    pages: List<ImagePageItem>,
    currentPageIndex: Int,
    config: ImageToPdfConfig,
    zoomScale: Float,
    onSelectPage: (Int) -> Unit,
    onMovePageUp: (Int) -> Unit,
    onMovePageDown: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onAddPages: () -> Unit,
    onAddBlankPage: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onConfigChange: (ImageToPdfConfig) -> Unit,
    modifier: Modifier = Modifier
) {
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
            // ── Section 1: Document Actions ───────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NexusText(
                    text = "DOCUMENT ACTIONS",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Add Pages Button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.surface)
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .springBounceClick { onAddPages() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.NoteAdd,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        NexusText(
                            text = "Add pages",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }

                    // Blank Page Button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.surface)
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .springBounceClick { onAddBlankPage() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        NexusText(
                            text = "Blank page",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }
            }

            // ── Section 2: View / Zoom ────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NexusText(
                    text = "VIEW",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = zoomScale > 0.5f) { onZoomOut() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (zoomScale > 0.5f) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.3f)
                        )
                    }

                    NexusText(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = zoomScale < 3f) { onZoomIn() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (zoomScale < 3f) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.3f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(1.dp)
                            .height(18.dp)
                            .background(NexusTheme.colors.divider.copy(alpha = 0.7f))
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onZoomReset() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = NexusTheme.colors.textPrimary
                        )
                    }
                }
            }

            // ── Section 3: Pages List ─────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NexusText(
                    text = "PAGES",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pages.forEachIndexed { index, page ->
                        val isSelected = index == currentPageIndex
                        PageRowItem(
                            index = index,
                            page = page,
                            isSelected = isSelected,
                            canMoveUp = index > 0,
                            canMoveDown = index < pages.size - 1,
                            onSelect = { onSelectPage(index) },
                            onMoveUp = { onMovePageUp(index) },
                            onMoveDown = { onMovePageDown(index) },
                            onDelete = { onDeletePage(index) }
                        )
                    }
                }
            }

            // ── Section 4: Page Size Segmented Picker ─────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NexusText(
                    text = "PAGE SIZE",
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
                        PageSize.entries.forEach { size ->
                            SegmentedSizeOption(
                                label = size.label,
                                isSelected = config.pageSize == size,
                                onClick = { onConfigChange(config.copy(pageSize = size)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Section 5: Margin Slider ──────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "MARGIN",
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
                            text = "${config.marginPt} pt",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                NexusLiquidSlider(
                    value = config.marginPt.toFloat(),
                    onValueChange = { onConfigChange(config.copy(marginPt = it.roundToInt())) },
                    valueRange = 0f..60f,
                    accentColor = NexusTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PageRowItem(
    index: Int,
    page: ImagePageItem,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.divider.copy(alpha = 0.7f),
        animationSpec = tween(150),
        label = "rowBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f) else NexusTheme.colors.surface,
        animationSpec = tween(150),
        label = "rowBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag grip icon
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = NexusTheme.colors.textSecondary.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 46.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f))
                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (page.bitmap != null) {
                    Image(
                        bitmap = page.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Article,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NexusTheme.colors.textSecondary.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title & File Name
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect() }
            ) {
                NexusText(
                    text = "Page ${index + 1}",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
                NexusText(
                    text = page.name.ifEmpty { "Blank page" },
                    style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp),
                    color = NexusTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Up button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canMoveUp) { onMoveUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (canMoveUp) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.25f)
                )
            }

            // Down button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canMoveDown) { onMoveDown() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (canMoveDown) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.25f)
                )
            }

            // Delete button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = NexusTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SegmentedSizeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.textPrimary else Color.Transparent,
        animationSpec = tween(160),
        label = "sizeBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.background else NexusTheme.colors.textSecondary,
        animationSpec = tween(160),
        label = "sizeColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable { onClick() },
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
