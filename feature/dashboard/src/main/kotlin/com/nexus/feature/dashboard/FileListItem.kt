package com.nexus.feature.dashboard

import android.content.Context
import android.net.Uri
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
import androidx.compose.material3.*
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
import com.nexus.core.ui.components.NexusDialog
import com.nexus.core.ui.animations.*
import com.nexus.feature.dashboard.data.RecentDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
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
            DocumentType.PPTX -> DocumentAccentColors.PPTX
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

    val pageCountStr = if (cachedPageCount != null) " • $cachedPageCount pages" else ""

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

    // Extract clean folder path
    val (folderRoot, folderName) = remember(doc.uri) {
        parseFolderPath(doc.uri)
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "selectionScale"
    )
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(180),
        label = "selectionAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 420f),
        label = "scale"
    )

    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalAnimatedVisibilityScope.current

    // ─── Main Modern Paper Card ───────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (isAccessible) 1f else 0.55f
                }
                .shadow(
                    elevation = if (isSelected) 3.dp else 1.dp,
                    shape = NexusTheme.shapes.medium,
                    ambientColor = NexusTheme.colors.textPrimary.copy(alpha = 0.04f),
                    spotColor = NexusTheme.colors.textPrimary.copy(alpha = 0.08f)
                )
                .clip(NexusTheme.shapes.medium)
                .background(NexusTheme.colors.surface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider.copy(alpha = 0.65f),
                    shape = NexusTheme.shapes.medium
                )
                .springBounceCombinedClick(
                    enabled = true,
                    scaleDown = 0.97f,
                    hapticPattern = HapticPattern.LIGHT,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(NexusTheme.spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ─── 1. Left Thumbnail Paper Sheet (70 x 92 dp) ──────────────
                var thumbnailModifier = Modifier
                    .width(70.dp)
                    .height(92.dp)

                if (sharedScope != null && animatedScope != null) {
                    with(sharedScope) {
                        thumbnailModifier = thumbnailModifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "thumb_${doc.uri}"),
                            animatedVisibilityScope = animatedScope,
                            boundsTransform = { _, _ -> tween(300) }
                        )
                    }
                }

                Box(
                    modifier = thumbnailModifier,
                    contentAlignment = Alignment.Center
                ) {
                    // Paper Sheet Base
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        NexusTheme.colors.surface,
                                        accentColor.copy(alpha = 0.12f)
                                    )
                                )
                            )
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(9.dp)),
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
                                StylizedPaperGraphic(docType = docType, accentColor = accentColor)
                            }
                        } else {
                            // Non-PDF formats (Word, Excel, Text, etc.)
                            StylizedPaperGraphic(docType = docType, accentColor = accentColor)
                        }
                    }

                    // Selection Checkmark Overlay (Top-Left)
                    if (selectionAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = selectionAlpha }
                                .border(2.5.dp, NexusTheme.colors.primary, RoundedCornerShape(9.dp))
                        )
                        Image(
                            painter = painterResource(id = com.nexus.core.R.drawable.ic_check),
                            contentDescription = stringResource(id = R.string.cd_selected),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(NexusTheme.spacing.xxs)
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = selectionScale
                                    scaleY = selectionScale
                                    alpha = selectionAlpha
                                }
                                .background(NexusTheme.colors.primary, CircleShape)
                                .padding(NexusTheme.spacing.xxs),
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
                                .padding(NexusTheme.spacing.xxs)
                                .size(16.dp)
                                .background(NexusTheme.colors.error, CircleShape)
                                .padding(NexusTheme.spacing.xxs),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ─── 2. Right Body Column ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 1.dp),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                ) {
                    // Row 1: Document Title + Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        var titleModifier: Modifier = Modifier.weight(1f).padding(end = NexusTheme.spacing.xxs)
                        if (sharedScope != null && animatedScope != null) {
                            with(sharedScope) {
                                titleModifier = titleModifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "title_${doc.uri}"),
                                    animatedVisibilityScope = animatedScope,
                                    boundsTransform = { _, _ -> tween(300) }
                                )
                            }
                        }

                        NexusText(
                            text = doc.fileName,
                            style = NexusTheme.typography.title.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.5.sp
                            ),
                            color = NexusTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = titleModifier
                        )

                        // Quick Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // 1-Tap Star Button with Spring Burst Animation
                            NexusStarBurst(
                                isStarred = isStarred,
                                onToggle = onToggleStarred,
                                size = 28.dp,
                                iconSize = 17.dp
                            )

                            // Kebab Menu or Loading Progress
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isOpening) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = accentColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable { menuExpanded = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                            contentDescription = stringResource(id = R.string.cd_more_options),
                                            modifier = Modifier.size(18.dp),
                                            colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Row 2: Format Tag • Size • Relative Timestamp • Pages
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs)
                    ) {
                        NexusText(
                            text = docType.name,
                            color = accentColor,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.textSecondary.copy(alpha = 0.4f))
                        )

                        NexusText(
                            text = formatFileSize(doc.fileSizeBytes),
                            color = NexusTheme.colors.textSecondary,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.textSecondary.copy(alpha = 0.4f))
                        )

                        NexusText(
                            text = "${formatDate(doc.lastOpenedAt)}$pageCountStr",
                            color = NexusTheme.colors.textSecondary,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Row 3: Folder Breadcrumb Capsule
                    Box(
                        modifier = Modifier
                            .clip(NexusTheme.shapes.small)
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f))
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.small)
                            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs)
                        ) {
                            Image(
                                painter = painterResource(id = com.nexus.core.R.drawable.ic_folder),
                                contentDescription = "Folder",
                                modifier = Modifier.size(12.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                            NexusText(
                                text = folderRoot,
                                color = NexusTheme.colors.textSecondary,
                                style = NexusTheme.typography.labelXs,
                                maxLines = 1
                            )
                            NexusText(
                                text = "/",
                                color = NexusTheme.colors.divider,
                                style = NexusTheme.typography.labelXs
                            )
                            NexusText(
                                text = folderName,
                                color = NexusTheme.colors.textPrimary,
                                style = NexusTheme.typography.labelXs,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Row 4: Custom Tags (if assigned)
                    if (tags.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(tags) { tagModel ->
                                val tagColor = tagModel.color
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(tagColor.copy(alpha = 0.14f))
                                        .border(
                                            width = 0.8.dp,
                                            color = tagColor.copy(alpha = 0.45f),
                                            shape = CircleShape
                                        )
                                        .padding(horizontal = NexusTheme.spacing.xs, vertical = 2.dp)
                                ) {
                                    NexusText(
                                        text = tagModel.chipLabel,
                                        color = tagColor,
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Options Bottom Sheet
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
private fun StylizedPaperGraphic(docType: DocumentType, accentColor: Color) {
    val iconRes = when (docType) {
        DocumentType.PDF  -> com.nexus.core.R.drawable.ic_file_pdf_new
        DocumentType.DOCX -> com.nexus.core.R.drawable.ic_file_word_new
        DocumentType.XLSX -> com.nexus.core.R.drawable.ic_file_sheet_new
        DocumentType.TXT  -> com.nexus.core.R.drawable.ic_file_txt_new
        else              -> com.nexus.core.R.drawable.ic_file_gen_new
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(NexusTheme.spacing.xxs)
    ) {
        // Ambient Glowing Format Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .ambientGlow(color = accentColor, alpha = 0.35f, radiusMultiplier = 0.95f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = docType.name,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Format Badge Pill
        Box(
            modifier = Modifier
                .clip(NexusTheme.shapes.extraSmall)
                .background(accentColor)
                .padding(horizontal = NexusTheme.spacing.xs, vertical = 2.dp)
        ) {
            NexusText(
                text = docType.name,
                color = Color.White,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}

private fun parseFolderPath(uriStr: String): Pair<String, String> {
    return try {
        val uri = Uri.parse(uriStr)
        val path = if (uri.scheme == "file") uri.path ?: "" else uri.toString()
        val file = File(path)
        val parent = file.parentFile
        val grandParent = parent?.parentFile
        val root = grandParent?.name?.takeIf { it.isNotEmpty() && it != "0" } ?: "Internal Storage"
        val current = parent?.name?.takeIf { it.isNotEmpty() } ?: "Documents"
        Pair(root, current)
    } catch (_: Exception) {
        Pair("Internal Storage", "Documents")
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

@Composable
internal fun formatDate(epochMs: Long): String {
    val now  = System.currentTimeMillis()
    val diff = now - epochMs
    return when {
        diff < 60_000       -> stringResource(id = R.string.label_just_now)
        diff < 3_600_000    -> stringResource(id = R.string.label_m_ago, (diff / 60_000).toInt())
        diff < 86_400_000   -> stringResource(id = R.string.label_h_ago, (diff / 3_600_000).toInt())
        diff < 172_800_000  -> stringResource(id = R.string.label_yesterday)
        else                -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
    }
}
