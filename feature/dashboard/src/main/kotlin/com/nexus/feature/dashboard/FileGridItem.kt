package com.nexus.feature.dashboard

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.feature.dashboard.R
import com.nexus.core.navigation.DocumentType
import com.nexus.core.navigation.LocalAnimatedVisibilityScope
import com.nexus.core.navigation.LocalSharedTransitionScope
import com.nexus.core.theme.DocumentAccentColors
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTextField
import com.nexus.core.ui.animations.*
import com.nexus.core.ui.components.NexusDialog
import com.nexus.feature.dashboard.data.RecentDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun FileGridItem(
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
    onLongClick: (() -> Unit)? = null,
    tags: List<TagUiModel> = emptyList(),
    allAvailableTags: List<TagUiModel> = emptyList(),
    onSaveTags: (List<String>) -> Unit = {},
    onSaveTagDefinition: ((name: String, colorHex: String, emoji: String?) -> Unit)? = null,
    onOpenTagManager: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialogExpanded by remember { mutableStateOf(false) }
    var tagDialogExpanded by remember { mutableStateOf(false) }
    var newFileName by remember(doc.uri) { mutableStateOf(doc.fileName) }

    val docType = runCatching { DocumentType.valueOf(doc.documentType) }
        .getOrDefault(DocumentType.UNKNOWN)

    val accentColor = remember(docType) {
        when (docType) {
            DocumentType.PDF  -> DocumentAccentColors.PDF
            DocumentType.DOCX -> DocumentAccentColors.DOCX
            DocumentType.XLSX -> DocumentAccentColors.XLSX
            DocumentType.TXT  -> DocumentAccentColors.TXT
            else              -> DocumentAccentColors.UNKNOWN
        }
    }

    val cachedPageCount by produceState<Int?>(initialValue = null, key1 = doc.uri) {
        value = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
            val count = prefs.getInt(doc.uri, -1)
            if (count > 0) count else null
        }
    }

    val showProgress = doc.lastScrollIndex > 0
    val progressPct = remember(doc.lastScrollIndex, cachedPageCount) {
        if (cachedPageCount != null && cachedPageCount!! > 0) {
            ((doc.lastScrollIndex + 1).toFloat() / cachedPageCount!! * 100).toInt().coerceIn(1, 100)
        } else {
            15
        }
    }

    // PDF Thumbnail Loading
    var pdfThumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var isThumbnailLoading by remember { mutableStateOf(docType == DocumentType.PDF) }

    LaunchedEffect(doc.uri) {
        if (docType == DocumentType.PDF) {
            isThumbnailLoading = true
            try {
                pdfThumbnail = PdfThumbnailCache.getThumbnail(context, doc.uri)
            } catch (_: Exception) {
                pdfThumbnail = null
            } finally {
                isThumbnailLoading = false
            }
        }
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "gridSelectionScale"
    )
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(180),
        label = "gridSelectionAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "gridScale"
    )

    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current

    // Card Container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isAccessible) 1f else 0.55f
            }
            .shadow(
                elevation = if (isSelected) 3.dp else 1.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = NexusTheme.colors.textPrimary.copy(alpha = 0.04f),
                spotColor = NexusTheme.colors.textPrimary.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(NexusTheme.colors.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp)
            )
            .springBounceCombinedClick(
                enabled = true,
                scaleDown = 0.97f,
                hapticPattern = HapticPattern.LIGHT,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ─── 1. Top Thumbnail Area (Taller aspect ratio ~ 0.82f for portrait sheet) ───
            var thumbnailModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)

            if (sharedScope != null && animatedScope != null) {
                with(sharedScope) {
                    thumbnailModifier = thumbnailModifier.sharedElement(
                        state = rememberSharedContentState(key = "thumb_${doc.uri}"),
                        animatedVisibilityScope = animatedScope,
                        boundsTransform = { _, _ -> tween(300) }
                    )
                }
            }

            Box(
                modifier = thumbnailModifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                NexusTheme.colors.surface,
                                accentColor.copy(alpha = 0.14f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (docType == DocumentType.PDF) {
                    if (isThumbnailLoading) {
                        Box(modifier = Modifier.fillMaxSize().shimmerEffect())
                    } else if (pdfThumbnail != null) {
                        Image(
                            bitmap = pdfThumbnail!!,
                            contentDescription = stringResource(id = R.string.cd_thumbnail),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        GridStylizedPaperGraphic(docType = docType, accentColor = accentColor)
                    }
                } else {
                    // Non-PDF formats (Word, Excel, Text, etc.)
                    GridStylizedPaperGraphic(docType = docType, accentColor = accentColor)
                }

                // Format Micro-Badge (Top-Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(NexusTheme.colors.surface.copy(alpha = 0.90f))
                        .border(0.5.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    NexusText(
                        text = docType.name,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = accentColor
                    )
                }

                // Selection Checkmark Overlay (Top-Left)
                if (selectionAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = selectionAlpha }
                            .border(2.5.dp, NexusTheme.colors.primary)
                    )
                    Image(
                        painter = painterResource(id = com.nexus.core.R.drawable.ic_check),
                        contentDescription = stringResource(id = R.string.cd_selected),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = selectionScale
                                scaleY = selectionScale
                                alpha = selectionAlpha
                            }
                            .background(NexusTheme.colors.primary, CircleShape)
                            .padding(3.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                    )
                }

                // Inaccessible Lock Badge (Bottom-Right)
                if (!isAccessible) {
                    Image(
                        painter = painterResource(id = com.nexus.core.R.drawable.ic_lock),
                        contentDescription = "Missing",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(18.dp)
                            .background(NexusTheme.colors.error, CircleShape)
                            .padding(3.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }

                // Reading Progress Bar at Bottom of Thumbnail
                if (showProgress && isAccessible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressPct / 100f)
                                .background(accentColor)
                        )
                    }
                }
            }

            // Divider between thumbnail and details
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.6f))
            )

            // ─── 2. Bottom Card Details ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title + Action Icons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    var titleModifier: Modifier = Modifier.weight(1f).padding(end = 2.dp)
                    if (sharedScope != null && animatedScope != null) {
                        with(sharedScope) {
                            titleModifier = titleModifier.sharedElement(
                                state = rememberSharedContentState(key = "title_${doc.uri}"),
                                animatedVisibilityScope = animatedScope,
                                boundsTransform = { _, _ -> tween(300) }
                            )
                        }
                    }

                    NexusText(
                        text = doc.fileName,
                        style = NexusTheme.typography.body.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.5.sp
                        ),
                        color = NexusTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = titleModifier
                    )

                    // 1-Tap Star Button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onToggleStarred() },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (isStarred) com.nexus.core.R.drawable.ic_star_filled
                                else com.nexus.core.R.drawable.ic_star
                            ),
                            contentDescription = stringResource(id = R.string.cd_starred),
                            modifier = Modifier.size(15.dp),
                            colorFilter = ColorFilter.tint(
                                if (isStarred) Color(0xFFFFB300) else NexusTheme.colors.textSecondary.copy(alpha = 0.6f)
                            )
                        )
                    }
                }

                // Tag badge row (if tags assigned)
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(tags) { tagModel ->
                            val tagColor = tagModel.color
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(tagColor.copy(alpha = 0.14f))
                                    .border(
                                        width = 0.6.dp,
                                        color = tagColor.copy(alpha = 0.45f),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
                            ) {
                                NexusText(
                                    text = tagModel.chipLabel,
                                    color = tagColor,
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }

                // Subtitle Row: Size • Date / Kebab Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = if (isAccessible) formatFileSize(doc.fileSizeBytes) else "Missing",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (isAccessible) NexusTheme.colors.textSecondary else NexusTheme.colors.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Kebab Menu Icon
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { menuExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOpening) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = accentColor,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Image(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                contentDescription = stringResource(id = R.string.cd_more_options),
                                modifier = Modifier.size(16.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                        }
                    }
                }
            }
        }

        // Context Menu Options Sheet
        if (menuExpanded) {
            FileOptionsDialog(
                fileName = doc.fileName,
                fileSizeBytes = doc.fileSizeBytes,
                mimeType = doc.mimeType,
                isStarred = isStarred,
                onDismissRequest = { menuExpanded = false },
                onShare = onShare,
                onRename = { renameDialogExpanded = true },
                onToggleStarred = onToggleStarred,
                onManageTags = { tagDialogExpanded = true },
                onRemove = {
                    onRemove()
                    menuExpanded = false
                },
                onShowDetails = onShowDetails
            )
        }

        // Tag Management Dialog
        if (tagDialogExpanded) {
            TagManagementDialog(
                fileName = doc.fileName,
                currentTags = tags,
                allAvailableTags = allAvailableTags,
                onDismissRequest = { tagDialogExpanded = false },
                onSaveTags = { updatedTags ->
                    onSaveTags(updatedTags)
                },
                onSaveTagDefinition = onSaveTagDefinition,
                onOpenTagManager = onOpenTagManager
            )
        }

        // Rename Dialog
        if (renameDialogExpanded) {
            NexusDialog(
                onDismissRequest = { renameDialogExpanded = false },
                title = { NexusText(stringResource(id = R.string.dialog_rename_title), style = NexusTheme.typography.h2) },
                text = {
                    NexusTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = stringResource(id = R.string.hint_file_name)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newFileName.isNotBlank()) {
                                onRename(newFileName)
                                renameDialogExpanded = false
                            }
                        }
                    ) {
                        NexusText(
                            stringResource(id = R.string.action_save),
                            color = NexusTheme.colors.primary
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { renameDialogExpanded = false }
                    ) {
                        NexusText(
                            stringResource(id = R.string.action_cancel),
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun GridStylizedPaperGraphic(docType: DocumentType, accentColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(12.dp)
    ) {
        // Document lines graphic
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(46.dp).padding(bottom = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(2.5.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.35f)))
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(2.5.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.35f)))
            Box(modifier = Modifier.fillMaxWidth(0.95f).height(2.5.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.35f)))
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.5.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.20f)))
        }

        // Format Badge Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor)
                .padding(horizontal = 7.dp, vertical = 2.5.dp)
        ) {
            NexusText(
                text = docType.name,
                color = Color.White,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}
