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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.navigation.DocumentType
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
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
    isOpening: Boolean = false
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialogExpanded by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf(doc.fileName) }
    val docType = runCatching { DocumentType.valueOf(doc.documentType) }
        .getOrDefault(DocumentType.UNKNOWN)
    
    val accentColor = remember(docType) {
        when (docType) {
            DocumentType.PDF  -> Color(0xFFC4362A)
            DocumentType.DOCX -> Color(0xFF3B7DD8)
            DocumentType.XLSX -> Color(0xFF2E9E5B)
            DocumentType.TXT  -> Color(0xFF7C5CBF)
            else              -> Color(0xFF3B7DD8)
        }
    }

    val cachedPageCount = remember(doc.uri) {
        val prefs = context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
        val count = prefs.getInt(doc.uri, -1)
        if (count > 0) count else null
    }

    val pageCountStr = if (cachedPageCount != null) " · $cachedPageCount pages" else ""
    val subtitle = "${docType.name} · ${formatFileSize(doc.fileSizeBytes)}$pageCountStr · ${formatDate(doc.lastOpenedAt)}"

    val showProgress = doc.lastScrollIndex > 0
    val progressPct = remember(doc.lastScrollIndex, cachedPageCount) {
        if (cachedPageCount != null && cachedPageCount > 0) {
            ((doc.lastScrollIndex + 1).toFloat() / cachedPageCount * 100).toInt().coerceIn(1, 100)
        } else {
            15 
        }
    }

    NexusCard(
        accentColor = accentColor,
        onClick = onClick,
        enabled = isAccessible,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp)
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
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(NexusTheme.shapes.medium)
                        .background(if (thumbnail != null) NexusTheme.colors.surfaceVariant else accentColor.copy(alpha = 0.15f))
                        .then(if (thumbnail == null && docType == DocumentType.PDF) Modifier.shimmerEffect() else Modifier),
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
                                NexusText("⭐")
                            }
                        }
                    }
                    NexusText(
                        text = subtitle,
                        style = NexusTheme.typography.caption,
                        color = NexusTheme.colors.textSecondary
                    )
                }

                Box {
                    if (isOpening) {
                        NexusText("⏳", color = accentColor)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { menuExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            NexusText("⋮", style = NexusTheme.typography.h1, color = NexusTheme.colors.textSecondary)
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
                            onRemove = onRemove,
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

            if (showProgress) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progressPct / 100f,
                    animationSpec = pressSpring(),
                    label = "progressFill"
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(NexusTheme.shapes.pill)
                            .background(NexusTheme.colors.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(6.dp)
                                .clip(NexusTheme.shapes.pill)
                                .background(accentColor)
                        )
                    }
                    NexusText(
                        text = "$progressPct%",
                        style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                        color = accentColor
                    )
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
