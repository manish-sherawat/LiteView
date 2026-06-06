package com.nexus.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusSurface
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import com.nexus.core.R
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import com.nexus.core.ui.animations.springBounceClick
import androidx.compose.animation.togetherWith

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
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            NexusSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var accumulatedDrag = 0f
                        detectVerticalDragGestures(
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                accumulatedDrag += dragAmount
                                if (accumulatedDrag > 100f) {
                                    onDismissRequest()
                                    accumulatedDrag = 0f
                                }
                            }
                        )
                    }
                    .clickable(
                        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                        indication = null,
                        onClick = {} // Prevent clicks on the dialog from dismissing it
                    ),
                shape = NexusTheme.shapes.large,
                elevation = 24.dp,
                color = NexusTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(NexusTheme.shapes.pill)
                            .background(NexusTheme.colors.textSecondary.copy(alpha = 0.2f))
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    NexusText(
                        text = fileName,
                        style = NexusTheme.typography.title,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
                    )

                    FileOptionItem(icon = "📤", iconRes = R.drawable.ic_share, label = "Share file", onClick = {
                        onDismissRequest()
                        onShare()
                    })
                    FileOptionItem(icon = "✏️", iconRes = R.drawable.ic_rename, label = "Rename file", onClick = {
                        onDismissRequest()
                        onRename()
                    })
                    FileOptionItem(
                        icon = if (isStarred) "🌟" else "⭐", 
                        iconRes = R.drawable.ic_star,
                        label = if (isStarred) "Unstar file" else "Star file", 
                        onClick = {
                            onDismissRequest()
                            onToggleStarred()
                        }
                    )
                    FileOptionItem(icon = "ℹ️", iconRes = R.drawable.ic_info, label = "File details", onClick = {
                        onDismissRequest()
                        onShowDetails()
                    })
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    FileOptionItem(
                        icon = "🗑️", 
                        iconRes = R.drawable.ic_delete,
                        label = "Remove from recents", 
                        onClick = {
                            onDismissRequest()
                            onRemove()
                        }, 
                        isDestructive = true
                    )
                }
            }
        }
    }
}

@Composable
private fun FileOptionItem(
    icon: String,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    iconRes: Int? = null
) {
    val contentColor = if (isDestructive) NexusTheme.colors.error else NexusTheme.colors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.medium)
            .springBounceClick(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = iconRes ?: icon,
                transitionSpec = { 
                    androidx.compose.animation.scaleIn() togetherWith androidx.compose.animation.scaleOut() 
                },
                label = "iconAnimation"
            ) { state ->
                if (state is Int) {
                    Image(
                        painter = painterResource(id = state),
                        contentDescription = label,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(contentColor)
                    )
                } else {
                    NexusText(text = state.toString(), style = NexusTheme.typography.title)
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        NexusText(
            text = label, 
            style = NexusTheme.typography.body,
            color = contentColor
        )
    }
}
