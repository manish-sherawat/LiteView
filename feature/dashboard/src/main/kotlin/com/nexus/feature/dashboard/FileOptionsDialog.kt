package com.nexus.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.fadeSlideIn
import com.nexus.core.ui.animations.springBounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOptionsDialog(
    fileName: String,
    fileSizeBytes: Long = 0L,
    mimeType: String? = null,
    isStarred: Boolean,
    onDismissRequest: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onToggleStarred: () -> Unit,
    onManageTags: () -> Unit,
    onRemove: () -> Unit,
    onShowDetails: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Determine format icon and accent based on file extension
    val lowerName = fileName.lowercase()
    val (formatIconRes, formatColor, formatLabel) = when {
        lowerName.endsWith(".pdf") -> Triple(R.drawable.ic_file_pdf_new, Color(0xFFEF4444), "PDF Document")
        lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> Triple(R.drawable.ic_file_word_new, Color(0xFF2563EB), "Word Document")
        lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") -> Triple(R.drawable.ic_file_sheet_new, Color(0xFF10B981), "Excel Spreadsheet")
        lowerName.endsWith(".txt") -> Triple(R.drawable.ic_file_txt_new, Color(0xFF8B5CF6), "Text Document")
        else -> Triple(R.drawable.ic_file_gen_new, NexusTheme.colors.primary, "Document")
    }

    val formattedSize = remember(fileSizeBytes) {
        formatFileSize(fileSizeBytes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        contentColor = NexusTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.8f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // ── Header: File Icon, Name, Meta (Type & Size), Favorite Button ──
            Row(
                modifier = Modifier
                    .fadeSlideIn(delay = 50)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Icon Container (46dp x 46dp, rounded 12dp)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = formatIconRes),
                        contentDescription = formatLabel,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // File Name & Meta
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    NexusText(
                        text = fileName,
                        style = NexusTheme.typography.title.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            lineHeight = 19.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NexusTheme.colors.textPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NexusText(
                            text = formatLabel,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = NexusTheme.colors.textSecondary
                        )

                        if (fileSizeBytes > 0L) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(NexusTheme.colors.textSecondary.copy(alpha = 0.5f))
                            )

                            NexusText(
                                text = formattedSize,
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Favorite Button (38dp circular with spring animation)
                val starScale by animateFloatAsState(
                    targetValue = if (isStarred) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                    label = "starScale"
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .scale(starScale)
                        .clip(CircleShape)
                        .background(
                            if (isStarred) Color(0xFFFFB300).copy(alpha = 0.16f)
                            else NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isStarred) Color(0xFFFFB300).copy(alpha = 0.7f)
                            else NexusTheme.colors.divider.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .springBounceClick { onToggleStarred() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(
                            id = if (isStarred) R.drawable.ic_star_filled else R.drawable.ic_star
                        ),
                        contentDescription = "Toggle Star",
                        modifier = Modifier.size(17.dp),
                        colorFilter = ColorFilter.tint(
                            if (isStarred) Color(0xFFFFB300) else NexusTheme.colors.textSecondary
                        )
                    )
                }
            }

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 14.dp),
                color = NexusTheme.colors.divider.copy(alpha = 0.45f),
                thickness = 0.8.dp
            )

            // ── Section 1: Quick Actions ─────────────────────────────────────
            NexusText(
                text = "Quick Actions",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier
                    .fadeSlideIn(delay = 100)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Share
                QuickActionItem(
                    iconRes = R.drawable.ic_share,
                    label = "Share",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismissRequest()
                        onShare()
                    }
                )

                // Rename
                QuickActionItem(
                    iconRes = R.drawable.ic_rename,
                    label = "Rename",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismissRequest()
                        onRename()
                    }
                )

                // Tags
                QuickActionItem(
                    iconRes = R.drawable.ic_tag,
                    label = "Tags",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismissRequest()
                        onManageTags()
                    }
                )

                // Star
                QuickActionItem(
                    iconRes = if (isStarred) R.drawable.ic_star_filled else R.drawable.ic_star,
                    label = if (isStarred) "Starred" else "Star",
                    iconTint = if (isStarred) Color(0xFFFFB300) else NexusTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onToggleStarred()
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Section 2: Manage ────────────────────────────────────────────
            NexusText(
                text = "Manage",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
            )

            // Management List Card
            Box(
                modifier = Modifier
                    .fadeSlideIn(delay = 150)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .springBounceClick {
                            onDismissRequest()
                            onShowDetails()
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = "File details",
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.width(13.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        NexusText(
                            text = "File details",
                            style = NexusTheme.typography.body.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                        NexusText(
                            text = "Size, location, and metadata",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp
                            ),
                            color = NexusTheme.colors.textSecondary
                        )
                    }

                    Image(
                        painter = painterResource(id = R.drawable.ic_arrow_right),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary.copy(alpha = 0.45f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Section 3: Destructive Action (Remove from recents) ───────────
            Row(
                modifier = Modifier
                    .fadeSlideIn(delay = 200)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusTheme.colors.error.copy(alpha = 0.08f))
                    .border(1.dp, NexusTheme.colors.error.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                    .springBounceClick {
                        onDismissRequest()
                        onRemove()
                    }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NexusTheme.colors.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Remove from recents",
                        modifier = Modifier.size(17.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.error)
                    )
                }

                Spacer(modifier = Modifier.width(13.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    NexusText(
                        text = "Remove from recents",
                        style = NexusTheme.typography.body.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = NexusTheme.colors.error
                    )
                    NexusText(
                        text = "Clears this document from recent history",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.error.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    iconRes: Int,
    label: String,
    iconTint: Color = NexusTheme.colors.textPrimary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .springBounceClick { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
        }

        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = NexusTheme.colors.textPrimary,
            maxLines = 1
        )
    }
}
