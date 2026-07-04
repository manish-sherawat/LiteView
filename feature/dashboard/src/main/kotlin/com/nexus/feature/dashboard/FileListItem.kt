package com.nexus.feature.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.dp
import com.nexus.core.navigation.DocumentType
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import androidx.compose.ui.graphics.graphicsLayer
import com.nexus.core.ui.components.FileTypeIcon
import com.nexus.core.ui.components.NexusCard
import com.nexus.core.ui.components.NexusDialog
import com.nexus.feature.dashboard.data.RecentDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.nexus.core.navigation.LocalSharedTransitionScope
import com.nexus.core.navigation.LocalAnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.nexus.core.ui.animations.*

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FileListItem(
    doc: RecentDocument,
    isAccessible: Boolean,
    isStarred: Boolean,
    onToggleStarred: () -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onShowDetails: () -> Unit,
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    isOpening: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialogExpanded by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf(doc.fileName) }

    val docType = runCatching { DocumentType.valueOf(doc.documentType) }
        .getOrDefault(DocumentType.UNKNOWN)
    
    val accentColor = remember(docType) {
        when (docType) {
            DocumentType.PDF  -> com.nexus.core.theme.DocumentAccentColors.PDF
            DocumentType.DOCX -> com.nexus.core.theme.DocumentAccentColors.DOCX
            DocumentType.XLSX -> com.nexus.core.theme.DocumentAccentColors.XLSX
            DocumentType.TXT  -> com.nexus.core.theme.DocumentAccentColors.TXT
            else              -> com.nexus.core.theme.DocumentAccentColors.UNKNOWN
        }
    }

    val cachedPageCount = remember(doc.uri) {
        val prefs = context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
        val count = prefs.getInt(doc.uri, -1)
        if (count > 0) count else null
    }

    val pageCountStr = if (cachedPageCount != null) " · $cachedPageCount pages" else ""
    val subtitle1 = "${docType.name} • ${formatFileSize(doc.fileSizeBytes)}"
    val subtitle2 = "Opened ${formatDate(doc.lastOpenedAt)}$pageCountStr"

    val showProgress = doc.lastOpenedAt > 0L
    val progressPct = remember(doc.lastScrollIndex, cachedPageCount) {
        if (cachedPageCount != null && cachedPageCount > 0) {
            ((doc.lastScrollIndex + 1).toFloat() / cachedPageCount * 100).toInt().coerceIn(1, 100)
        } else {
            15 
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scale"
    )

    NexusCard(
        accentColor = accentColor,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = isAccessible,
        modifier = modifier
            .fillMaxWidth()
            .padding(0.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isAccessible) 1f else 0.5f
            }
    ) {
        var thumbnail by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        androidx.compose.runtime.LaunchedEffect(doc.uri) {
            if (docType == DocumentType.PDF) {
                thumbnail = PdfThumbnailCache.getThumbnail(context, doc.uri)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var thumbnailModifier = Modifier
                    .size(80.dp)
                    .clip(NexusTheme.shapes.medium)
                    .background(if (thumbnail != null) NexusTheme.colors.surfaceVariant else accentColor.copy(alpha = 0.15f))
                    .then(if (thumbnail == null && docType == DocumentType.PDF) Modifier.shimmerEffect() else Modifier)
                
                val sharedScope = LocalSharedTransitionScope.current
                val animatedScope = LocalAnimatedVisibilityScope.current
                if (sharedScope != null && animatedScope != null) {
                    with(sharedScope) {
                        thumbnailModifier = thumbnailModifier.sharedElement(
                            state = rememberSharedContentState(key = "thumb_${doc.uri}"),
                            animatedVisibilityScope = animatedScope,
                            boundsTransform = { _, _ -> androidx.compose.animation.core.tween(300) }
                        )
                    }
                }

                Box(
                    modifier = thumbnailModifier,
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.Crossfade(
                        targetState = thumbnail,
                        animationSpec = androidx.compose.animation.core.tween(300),
                        label = "thumbnailCrossfade"
                    ) { bmp ->
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bmp,
                                contentDescription = "Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            FileTypeIcon(type = docType, size = 48.dp, drawContainer = false)
                        }
                    }
                    
                    if (isSelected) {
                        val pulseAlpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse").animateFloat(
                            initialValue = 0.4f,
                            targetValue = 0.8f,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = androidx.compose.animation.core.tween(1000),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )
                        Box(modifier = Modifier.fillMaxSize().border(3.dp, NexusTheme.colors.primary.copy(alpha = pulseAlpha), NexusTheme.shapes.medium))
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_check),
                            contentDescription = "Selected",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(NexusTheme.colors.primary, androidx.compose.foundation.shape.CircleShape)
                                .padding(4.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(NexusTheme.colors.onPrimary)
                        )
                    }
                    
                    if (!isAccessible) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_lock),
                            contentDescription = "Inaccessible",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(NexusTheme.colors.error, androidx.compose.foundation.shape.CircleShape)
                                .padding(4.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NexusText(
                            text = doc.fileName,
                            style = NexusTheme.typography.title.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isStarred) {
                            AnimatedVisibility(
                                visible = isStarred,
                                enter = scaleIn(animationSpec = pressSpring()) + fadeIn(),
                                exit = scaleOut(animationSpec = pressSpring()) + fadeOut()
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_star),
                                    contentDescription = "Starred",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(NexusTheme.colors.primary)
                                )
                            }
                        }
                    }
                    NexusText(
                        text = subtitle1,
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.textSecondary
                    )
                    NexusText(
                        text = subtitle2,
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                    )
                    if (!isAccessible) {
                        NexusText("File missing", color = NexusTheme.colors.error, style = NexusTheme.typography.caption)
                    }
                }

                Box {
                    if (isOpening) {
                        NexusText("⏳", color = accentColor)
                    } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable { menuExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                    contentDescription = "More Options",
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(NexusTheme.colors.textSecondary)
                                )
                            }
                    }
                    if (menuExpanded) {
                        FileOptionsDialog(
                            fileName = doc.fileName,
                            isStarred = isStarred,
                            onDismissRequest = { menuExpanded = false },
                            onShare = onShare,
                            onRename = { renameDialogExpanded = true },
                            onToggleStarred = onToggleStarred,
                            onRemove = { 
                                onRemove()
                                menuExpanded = false
                            },
                            onShowDetails = onShowDetails
                        )
                    }

                    if (renameDialogExpanded) {
                        NexusDialog(
                            onDismissRequest = { renameDialogExpanded = false },
                            title = { NexusText("Rename File", style = NexusTheme.typography.h2) },
                            text = {
                                com.nexus.core.ui.NexusTextField(
                                    value = newFileName,
                                    onValueChange = { newFileName = it },
                                    placeholder = "File name"
                                )
                            },
                            confirmButton = {
                                NexusText(
                                    "Save",
                                    color = NexusTheme.colors.primary,
                                    modifier = Modifier.clickable { 
                                        if (newFileName.isNotBlank()) {
                                            onRename(newFileName)
                                            renameDialogExpanded = false
                                        }
                                    }.padding(8.dp)
                                )
                            },
                            dismissButton = {
                                NexusText(
                                    "Cancel",
                                    color = NexusTheme.colors.textSecondary,
                                    modifier = Modifier.clickable { renameDialogExpanded = false }.padding(8.dp)
                                )
                            }
                        )
                    }
                }
            }


        }
    }
}

internal fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    return when {
        bytes < 1_024          -> "$bytes B"
        bytes < 1_048_576      -> "${"%.1f".format(bytes / 1_024f)} KB"
        bytes < 1_073_741_824  -> "${"%.1f".format(bytes / 1_048_576f)} MB"
        else                   -> "${"%.2f".format(bytes / 1_073_741_824f)} GB"
    }
}

internal fun formatDate(epochMs: Long): String {
    val now  = System.currentTimeMillis()
    val diff = now - epochMs
    return when {
        diff < 60_000       -> "Just now"
        diff < 3_600_000    -> "${diff / 60_000}m ago"
        diff < 86_400_000   -> "${diff / 3_600_000}h ago"
        diff < 172_800_000  -> "Yesterday"
        else                -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
    }
}
