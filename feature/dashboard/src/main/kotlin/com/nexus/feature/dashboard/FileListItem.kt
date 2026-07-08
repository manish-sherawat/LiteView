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
import androidx.compose.animation.core.tween
import com.nexus.core.ui.animations.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.Crossfade
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.spring

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
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
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialogExpanded by remember { mutableStateOf(false) }
    var newFileName by remember(doc.uri) { mutableStateOf(doc.fileName) }

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

    val cachedPageCount by produceState<Int?>(initialValue = null, key1 = doc.uri) {
        value = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("nexus_page_counts", Context.MODE_PRIVATE)
            val count = prefs.getInt(doc.uri, -1)
            if (count > 0) count else null
        }
    }

    val pageCountStr = if (cachedPageCount != null) " · $cachedPageCount pages" else ""
    val subtitle1 = "${docType.name} • ${formatFileSize(doc.fileSizeBytes)}"
    val subtitle2 = "Opened ${formatDate(doc.lastOpenedAt)}$pageCountStr"

    val progressPct = remember(doc.lastScrollIndex, cachedPageCount) {
        if (cachedPageCount != null && cachedPageCount!! > 0) {
            ((doc.lastScrollIndex + 1).toFloat() / cachedPageCount!!).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "selectionScale"
    )
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(200),
        label = "selectionAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "scale"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onRemove()
                    true // Confirm dismissal
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onShare()
                    false // Don't dismiss for share
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by androidx.compose.animation.animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> NexusTheme.colors.error
                    SwipeToDismissBoxValue.StartToEnd -> NexusTheme.colors.primary
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                animationSpec = tween(150),
                label = "dismissColor"
            )
            
            val iconScale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.5f else 1.2f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
                label = "iconScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp) // Match the card padding if any
                    .clip(NexusTheme.shapes.large)
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Image(
                        painter = painterResource(id = com.nexus.core.R.drawable.ic_share),
                        contentDescription = "Share",
                        modifier = Modifier.size(32.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Image(
                        painter = painterResource(id = com.nexus.core.R.drawable.ic_delete),
                        contentDescription = "Delete",
                        modifier = Modifier.size(32.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth()
    ) {



            NexusCard(
                accentColor = accentColor,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick?.invoke()
                },
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
                LaunchedEffect(doc.uri) {
                    if (docType == DocumentType.PDF) {
                        try {
                            thumbnail = PdfThumbnailCache.getThumbnail(context, doc.uri)
                        } catch (e: Exception) {
                            thumbnail = null
                        }
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
                                    boundsTransform = { _, _ -> tween(300) }
                                )
                            }
                        }

                        Box(
                            modifier = thumbnailModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(
                                targetState = thumbnail,
                                animationSpec = tween(300),
                                label = "thumbnailCrossfade"
                            ) { bmp ->
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = stringResource(id = R.string.cd_thumbnail),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    FileTypeIcon(type = docType, size = 48.dp, drawContainer = false)
                                }
                            }
                            
                            if (selectionAlpha > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = selectionAlpha }
                                        .border(3.dp, NexusTheme.colors.primary, NexusTheme.shapes.medium)
                                )
                                Image(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_check),
                                    contentDescription = stringResource(id = R.string.cd_selected),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .graphicsLayer {
                                            scaleX = selectionScale
                                            scaleY = selectionScale
                                            alpha = selectionAlpha
                                        }
                                        .background(NexusTheme.colors.primary, CircleShape)
                                        .padding(4.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                                )
                            }
                            
                            if (!isAccessible) {
                                Image(
                                    painter = painterResource(id = com.nexus.core.R.drawable.ic_lock),
                                    contentDescription = stringResource(id = R.string.cd_inaccessible),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .background(NexusTheme.colors.error, CircleShape)
                                        .padding(4.dp),
                                    colorFilter = ColorFilter.tint(Color.White)
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
                                var titleModifier: Modifier = Modifier.weight(1f, fill = false)
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
                                    style = NexusTheme.typography.title.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = titleModifier
                                )
                                if (isStarred) {
                                    AnimatedVisibility(
                                        visible = isStarred,
                                        enter = scaleIn(animationSpec = pressSpring()) + fadeIn(),
                                        exit = scaleOut(animationSpec = pressSpring()) + fadeOut()
                                    ) {
                                        Image(
                                            painter = painterResource(id = com.nexus.core.R.drawable.ic_star),
                                            contentDescription = stringResource(id = R.string.cd_starred),
                                            modifier = Modifier.size(20.dp),
                                            colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                                        )
                                    }
                                }
                            }
                            NexusText(
                                text = subtitle1,
                                style = NexusTheme.typography.caption,
                                color = NexusTheme.colors.textSecondary
                            )
                            
                            Column(modifier = Modifier.fillMaxWidth()) {
                                NexusText(
                                    text = subtitle2,
                                    style = NexusTheme.typography.caption,
                                    color = NexusTheme.colors.textSecondary
                                )
                                if (progressPct > 0f) {
                                    LinearProgressIndicator(
                                        progress = { progressPct },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, end = 16.dp)
                                            .height(2.dp)
                                            .clip(CircleShape),
                                        color = accentColor,
                                        trackColor = accentColor.copy(alpha = 0.2f)
                                    )
                                }
                            }
                            
                            if (!isAccessible) {
                                NexusText(
                                    stringResource(id = R.string.error_file_missing),
                                    color = NexusTheme.colors.error,
                                    style = NexusTheme.typography.caption
                                )
                            }
                        }

                        Box {
                            if (isOpening) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = accentColor,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable { menuExpanded = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = com.nexus.core.R.drawable.ic_more_vert),
                                        contentDescription = stringResource(id = R.string.cd_more_options),
                                        modifier = Modifier.size(24.dp),
                                        colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
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
                                    title = { NexusText(stringResource(id = R.string.dialog_rename_title), style = NexusTheme.typography.h2) },
                                    text = {
                                        com.nexus.core.ui.NexusTextField(
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
                }
            }
        } // End of SwipeToDismissBox


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
        diff < 3_600_000    -> stringResource(id = R.string.label_m_ago, diff / 60_000)
        diff < 86_400_000   -> stringResource(id = R.string.label_h_ago, diff / 3_600_000)
        diff < 172_800_000  -> stringResource(id = R.string.label_yesterday)
        else                -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
    }
}
