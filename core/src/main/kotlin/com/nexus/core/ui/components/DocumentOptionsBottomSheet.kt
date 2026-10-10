package com.nexus.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.navigation.DocumentType
import com.nexus.core.theme.NexusFontFamily
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick

data class DocumentOptionItem(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val isStarred: Boolean = false,
    val showChevron: Boolean = true,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOptionsBottomSheet(
    sheetTitle: String = "Document Options",
    fileName: String,
    fileMeta: String,
    docType: DocumentType = DocumentType.UNKNOWN,
    groups: List<List<DocumentOptionItem>>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        contentColor = NexusTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.divider)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Sheet Header: Title + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusText(
                    text = sheetTitle,
                    style = NexusTheme.typography.title.copy(
                        fontFamily = NexusFontFamily,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = NexusTheme.colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.surfaceVariant)
                        .springBounceClick(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(14.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                    )
                }
            }

            // File Identity Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NexusTheme.colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    FileTypeIcon(
                        type = docType,
                        size = 22.dp,
                        drawContainer = false
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    NexusText(
                        text = fileName,
                        style = NexusTheme.typography.body.copy(
                            fontFamily = NexusFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    NexusText(
                        text = fileMeta,
                        style = NexusTheme.typography.caption.copy(
                            fontFamily = NexusFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NexusTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option Cards
            groups.forEachIndexed { groupIndex, groupItems ->
                if (groupIndex > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(NexusTheme.colors.surface)
                        .border(
                            width = 1.dp,
                            color = NexusTheme.colors.divider.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(18.dp)
                        )
                ) {
                    groupItems.forEachIndexed { itemIndex, item ->
                        if (itemIndex > 0) {
                            HorizontalDivider(
                                color = NexusTheme.colors.divider.copy(alpha = 0.5f),
                                thickness = 0.8.dp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.onClick() }
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Badge
                            val badgeBg = if (item.isStarred) {
                                NexusTheme.colors.primary
                            } else {
                                NexusTheme.colors.surfaceVariant
                            }

                            val iconTint = if (item.isStarred) {
                                NexusTheme.colors.onPrimary
                            } else {
                                NexusTheme.colors.textPrimary
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(badgeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = item.iconRes),
                                    contentDescription = item.title,
                                    modifier = Modifier.size(19.dp),
                                    colorFilter = ColorFilter.tint(iconTint)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Text Title & Subtitle
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                NexusText(
                                    text = item.title,
                                    style = NexusTheme.typography.body.copy(
                                        fontFamily = NexusFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                NexusText(
                                    text = item.subtitle,
                                    style = NexusTheme.typography.caption.copy(
                                        fontFamily = NexusFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = NexusTheme.colors.textSecondary
                                )
                            }

                            // Trailing Chevron
                            if (item.showChevron) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_arrow_right),
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
