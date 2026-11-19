package com.nexus.feature.dashboard.tools.merge_pdf.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import com.nexus.feature.dashboard.tools.merge_pdf.MergePdfEngine
import com.nexus.feature.dashboard.tools.merge_pdf.MergePdfItem

@Composable
fun MergeWorkspaceCard(
    items: List<MergePdfItem>,
    isBusy: Boolean,
    progress: Float,
    statusMessage: String?,
    isError: Boolean,
    onAddFiles: () -> Unit,
    onClearAll: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSaveCombined: () -> Unit,
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
                text = "Merge PDF",
                style = NexusTheme.typography.h2.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = NexusTheme.colors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            NexusText(
                text = "Combine multiple PDFs into one",
                style = NexusTheme.typography.body.copy(fontSize = 14.5.sp),
                color = NexusTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Add PDFs Button (Dashed rounded box) ──────────────────────────
            val dashBorderColor = NexusTheme.colors.divider.copy(alpha = 0.85f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.45f))
                    .drawBehind {
                        drawRoundRect(
                            color = dashBorderColor,
                            style = Stroke(
                                width = 1.6.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                            ),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                        )
                    }
                    .clickable(enabled = !isBusy) { onAddFiles() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = NexusTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusText(
                        text = "Add PDFs",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── List Header Row: Count & Clear All ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val countText = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary)) {
                        append("${items.size}")
                    }
                    append(" file${if (items.size == 1) "" else "s"} in merge order")
                }

                NexusText(
                    text = countText.text,
                    style = NexusTheme.typography.caption.copy(fontSize = 13.5.sp),
                    color = NexusTheme.colors.textSecondary
                )

                NexusText(
                    text = "Clear all",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !isBusy) { onClearAll() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Top Save Button ───────────────────────────────────────────────
            val canSave = items.size >= 2 && !isBusy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (canSave) NexusTheme.colors.textPrimary else NexusTheme.colors.textPrimary.copy(alpha = 0.4f)
                    )
                    .springBounceClick(enabled = canSave) { onSaveCombined() },
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
                        text = "Merging…",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.background
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = NexusTheme.colors.background
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    NexusText(
                        text = "Save the combined PDF",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.background
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── File Cards List ───────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEachIndexed { index, item ->
                    FileCardItem(
                        index = index,
                        totalCount = items.size,
                        item = item,
                        isBusy = isBusy,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRemove = { onRemove(index) }
                    )
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
 * File card representation for each PDF in the merge queue.
 */
@Composable
private fun FileCardItem(
    index: Int,
    totalCount: Int,
    item: MergePdfItem,
    isBusy: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NexusTheme.colors.surface)
            .border(1.2.dp, NexusTheme.colors.divider.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top row: Number circle, thumbnail, file info, grip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Number circle
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.textPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "${index + 1}",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.background
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Thumbnail: 40px x 52px
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NexusTheme.colors.surfaceVariant)
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.thumbnail != null) {
                        Image(
                            bitmap = item.thumbnail.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        NexusText(
                            text = "PDF",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // File name & meta
                Column(modifier = Modifier.weight(1f)) {
                    NexusText(
                        text = item.name,
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
                        text = "${item.pageCount} page${if (item.pageCount == 1) "" else "s"} · ${MergePdfEngine.formatFileSize(item.sizeBytes)}",
                        style = NexusTheme.typography.caption.copy(fontSize = 12.5.sp),
                        color = NexusTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Grip icon
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Reorder handle",
                    modifier = Modifier.size(20.dp),
                    tint = NexusTheme.colors.divider
                )
            }

            // Divider line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Actions row: Up, Down, Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Up button
                val canUp = index > 0 && !isBusy
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surface)
                        .border(
                            1.dp,
                            if (canUp) NexusTheme.colors.divider.copy(alpha = 0.8f) else NexusTheme.colors.divider.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = canUp) { onMoveUp() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Move up",
                            modifier = Modifier.size(16.dp),
                            tint = if (canUp) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        NexusText(
                            text = "Up",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (canUp) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                        )
                    }
                }

                // Down button
                val canDown = index < totalCount - 1 && !isBusy
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surface)
                        .border(
                            1.dp,
                            if (canDown) NexusTheme.colors.divider.copy(alpha = 0.8f) else NexusTheme.colors.divider.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = canDown) { onMoveDown() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = "Move down",
                            modifier = Modifier.size(16.dp),
                            tint = if (canDown) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        NexusText(
                            text = "Down",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (canDown) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                        )
                    }
                }

                // Remove button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surface)
                        .border(1.2.dp, NexusTheme.colors.textPrimary, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isBusy) { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp),
                            tint = NexusTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        NexusText(
                            text = "Remove",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
