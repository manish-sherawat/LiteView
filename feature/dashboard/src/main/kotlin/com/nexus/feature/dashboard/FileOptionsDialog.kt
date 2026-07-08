package com.nexus.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.animations.fadeSlideIn
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.NexusText

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileOptionsDialog(
    fileName: String,
    isStarred: Boolean,
    onDismissRequest: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onToggleStarred: () -> Unit,
    onRemove: () -> Unit,
    onShowDetails: () -> Unit
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        contentColor = NexusTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .padding(horizontal = 16.dp)
        ) {
            NexusText(
                text = fileName,
                style = NexusTheme.typography.title,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
            )

            val options = listOf(
                Pair("Share file", "Share this document") to (R.drawable.ic_share to onShare),
                Pair("Rename file", "Change file name") to (R.drawable.ic_rename to onRename),
                Pair(if (isStarred) "Unstar file" else "Star file", "Add or remove bookmark") to (R.drawable.ic_star to onToggleStarred),
                Pair("File details", "View file properties") to (R.drawable.ic_info to onShowDetails),
                Pair("Delete file", "Move to trash") to (R.drawable.ic_delete to onRemove)
            )

            options.forEachIndexed { index, (labels, actionData) ->
                val (title, subtitle) = labels
                val (iconRes, action) = actionData
                val isDestructive = title == "Delete file"
                val iconColor = if (isDestructive) NexusTheme.colors.error else NexusTheme.colors.textPrimary
                
                Row(
                    modifier = Modifier
                        .fadeSlideIn(delay = index * 40)
                        .fillMaxWidth()
                        .clip(NexusTheme.shapes.medium)
                        .springBounceClick { 
                            onDismissRequest()
                            action() 
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = title,
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(iconColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        NexusText(
                            text = title,
                            style = NexusTheme.typography.body,
                            color = iconColor
                        )
                        NexusText(
                            text = subtitle,
                            style = NexusTheme.typography.caption,
                            color = if (isDestructive) iconColor.copy(alpha=0.8f) else NexusTheme.colors.textSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
