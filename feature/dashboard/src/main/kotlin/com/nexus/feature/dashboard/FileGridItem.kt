package com.nexus.feature.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.nexus.core.ui.animations.pressSpring

@Composable
fun FileGridItem(
    doc: RecentDocument,
    isAccessible: Boolean,
    isStarred: Boolean,
    onToggleStarred: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    onShowDetails: () -> Unit,
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isOpening: Boolean = false,
    modifier: Modifier = Modifier
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

    val showProgress = doc.lastScrollIndex > 0
    val progressPct = remember(doc.lastScrollIndex, cachedPageCount) {
        if (cachedPageCount != null && cachedPageCount > 0) {
            ((doc.lastScrollIndex + 1).toFloat() / cachedPageCount * 100).toInt().coerceIn(1, 100)
        } else {
            15
        }
    }

    NexusCard(
        accentColor = if (isSelected) NexusTheme.colors.primary else accentColor,
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = isAccessible,
        modifier = modifier
            .aspectRatio(0.7f)
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            var thumbnail by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            LaunchedEffect(doc.uri) {
                if (docType == DocumentType.PDF) {
                    thumbnail = PdfThumbnailCache.getThumbnail(context, doc.uri)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(NexusTheme.shapes.small)
                    .background(if (thumbnail != null) NexusTheme.colors.surfaceVariant else accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
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
                        FileTypeIcon(type = docType, size = 36.dp, drawContainer = false)
                    }
                }

                val overlayAlpha by animateFloatAsState(
                    targetValue = if (isSelectionMode) 1f else 0f,
                    animationSpec = pressSpring(),
                    label = "selectionOverlayAlpha"
                )
                val overlayScale by animateFloatAsState(
                    targetValue = if (isSelectionMode) 1f else 0.4f,
                    animationSpec = pressSpring(),
                    label = "selectionOverlayScale"
                )
                val checkAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = pressSpring(),
                    label = "checkAlpha"
                )
                val checkScale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.3f,
                    animationSpec = pressSpring(),
                    label = "checkScale"
                )
                val overlayBgColor by animateColorAsState(
                    targetValue = if (isSelected) NexusTheme.colors.primary
                                  else NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f),
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "overlayBg"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(overlayBgColor)
                        .graphicsLayer {
                            alpha = overlayAlpha
                            scaleX = overlayScale
                            scaleY = overlayScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "✓",
                        color = NexusTheme.colors.onPrimary,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = checkAlpha
                                scaleX = checkScale
                                scaleY = checkScale
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NexusText(
                            text = doc.fileName,
                            style = NexusTheme.typography.body,
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
                    Spacer(modifier = Modifier.height(4.dp))
                    NexusText(
                        text = if (isAccessible) "${formatDate(doc.lastOpenedAt)} • ${docType.name}" else "Missing",
                        style = NexusTheme.typography.caption,
                        color = if (isAccessible) NexusTheme.colors.textSecondary else NexusTheme.colors.error
                    )
                }

                Box(modifier = Modifier.padding(end = 4.dp)) {
                    if (isOpening) {
                        NexusText("⏳", color = accentColor)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { menuExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            NexusText("⋮", style = NexusTheme.typography.h2, color = NexusTheme.colors.textSecondary)
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                NexusText(
                    text = formatFileSize(doc.fileSizeBytes),
                    style = NexusTheme.typography.caption,
                    color = NexusTheme.colors.textSecondary
                )
                
                if (showProgress) {
                    NexusText(
                        text = "$progressPct%",
                        style = NexusTheme.typography.caption,
                        color = accentColor
                    )
                }
            }

            if (showProgress) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progressPct / 100f,
                    animationSpec = pressSpring(),
                    label = "gridProgressFill"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(NexusTheme.shapes.pill)
                        .background(NexusTheme.colors.textSecondary.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(4.dp)
                            .clip(NexusTheme.shapes.pill)
                            .background(accentColor)
                    )
                }
            }
        }
    }
}
