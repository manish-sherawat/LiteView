@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nexus.feature.dashboard.tools.merge_pdf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.merge_pdf.MergePdfEngine
import com.nexus.feature.dashboard.tools.merge_pdf.MergePdfItem

@Composable
fun MergeSummaryCard(
    items: List<MergePdfItem>,
    outputFileName: String,
    onOutputFileNameChange: (String) -> Unit,
    onSortAZ: () -> Unit,
    onReverse: () -> Unit,
    onSaveCombined: () -> Unit,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    val totalFiles = items.size
    val totalPages = items.sumOf { it.pageCount }
    val totalSizeBytes = items.sumOf { it.sizeBytes }
    val canMerge = totalFiles >= 2 && !isBusy

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
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                NexusText(
                    text = "Combined file",
                    style = NexusTheme.typography.title.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            }

            // ── Body ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // ── 3 Stats Boxes ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        title = "FILES",
                        value = "$totalFiles",
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "PAGES",
                        value = "$totalPages",
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "TOTAL",
                        value = MergePdfEngine.formatFileSize(totalSizeBytes),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── Merge Order Strip ─────────────────────────────────────────
                NexusText(
                    text = "MERGE ORDER",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Chip
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(NexusTheme.colors.surface)
                                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                                    .padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.textPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NexusText(
                                        text = "${index + 1}",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = NexusTheme.colors.background
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                NexusText(
                                    text = item.name.removeSuffix(".pdf"),
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NexusTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Connecting arrow
                            if (index < items.lastIndex) {
                                NexusText(
                                    text = "→",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NexusTheme.colors.divider
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── Quick Sort Buttons ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sort A-Z
                    val canSort = totalFiles >= 2 && !isBusy
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.surface)
                            .border(
                                1.dp,
                                if (canSort) NexusTheme.colors.divider.copy(alpha = 0.8f) else NexusTheme.colors.divider.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = canSort) { onSortAZ() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = if (canSort) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            NexusText(
                                text = "Sort A–Z",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (canSort) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                            )
                        }
                    }

                    // Reverse
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.surface)
                            .border(
                                1.dp,
                                if (canSort) NexusTheme.colors.divider.copy(alpha = 0.8f) else NexusTheme.colors.divider.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = canSort) { onReverse() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (canSort) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            NexusText(
                                text = "Reverse",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (canSort) NexusTheme.colors.textPrimary else NexusTheme.colors.divider
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── File Name Field ───────────────────────────────────────────
                NexusText(
                    text = "FILE NAME",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.2.dp, NexusTheme.colors.divider.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = outputFileName,
                        onValueChange = onOutputFileNameChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        cursorBrush = SolidColor(NexusTheme.colors.textPrimary),
                        textStyle = TextStyle(
                            color = NexusTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    NexusText(
                        text = ".pdf",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── Merge & Download Button ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canMerge) NexusTheme.colors.textPrimary else NexusTheme.colors.textPrimary.copy(alpha = 0.4f)
                        )
                        .springBounceClick(enabled = canMerge) { onSaveCombined() },
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
                                fontSize = 15.sp,
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
                            text = "Merge & download",
                            style = NexusTheme.typography.body.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.background
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tip
                NexusText(
                    text = "Drag cards or use Up and Down to change the order.",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        color = NexusTheme.colors.textSecondary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NexusText(
                text = value,
                style = NexusTheme.typography.h2.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            NexusText(
                text = title,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = NexusTheme.colors.textSecondary
            )
        }
    }
}
