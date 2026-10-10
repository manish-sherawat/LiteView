package com.nexus.feature.dashboard

import com.nexus.core.theme.DocumentAccentColors

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.fadeSlideIn
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.animations.*
import com.nexus.core.ui.components.NexusButton
import com.nexus.core.ui.components.NexusDialog
import com.nexus.core.ui.NexusTextField
import com.nexus.core.ui.components.NexusTopBar
import com.nexus.feature.dashboard.data.TrashedDocument

// ─── Recycle Bin Screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var singleDocToDeletePermanently by remember { mutableStateOf<TrashedDocument?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        if (uiState.isSelectionMode) {
            viewModel.clearSelection()
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexusTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            NexusTopBar(
                title = if (uiState.isSelectionMode) "${uiState.selectedIds.size} Selected" else "Recycle Bin",
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .clip(CircleShape)
                            .springBounceClick {
                                if (uiState.isSelectionMode) {
                                    viewModel.clearSelection()
                                } else {
                                    onBack()
                                }
                            }
                            .padding(NexusTheme.spacing.sm)
                            .size(24.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                    )
                },
                actions = {
                    if (uiState.items.isNotEmpty() || uiState.totalCount > 0) {
                        if (!uiState.isSelectionMode) {
                            // Search toggle
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .springBounceClick {
                                        showSearchField = !showSearchField
                                        if (!showSearchField) viewModel.setSearchQuery("")
                                    }
                                    .padding(NexusTheme.spacing.xs),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = if (showSearchField) R.drawable.ic_close else R.drawable.ic_search),
                                    contentDescription = "Search",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                                )
                            }

                            // Select Mode toggle
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .springBounceClick { viewModel.toggleSelectionMode() }
                                    .padding(NexusTheme.spacing.xs),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = "Select items",
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                                )
                            }

                            // Empty Trash action
                            Box(
                                modifier = Modifier
                                    .clip(NexusTheme.shapes.small)
                                    .background(NexusTheme.colors.error.copy(alpha = 0.12f))
                                    .springBounceClick { showEmptyTrashDialog = true }
                                    .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xs),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_delete),
                                        contentDescription = "Empty Trash",
                                        modifier = Modifier.size(16.dp),
                                        colorFilter = ColorFilter.tint(NexusTheme.colors.error)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    NexusText(
                                        text = "Empty",
                                        style = NexusTheme.typography.caption.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = NexusTheme.colors.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            // Selection Mode Actions: Select All & Close
                            Box(
                                modifier = Modifier
                                    .clip(NexusTheme.shapes.small)
                                    .springBounceClick {
                                        if (uiState.selectedIds.size == uiState.items.size) {
                                            viewModel.clearSelection()
                                        } else {
                                            viewModel.selectAll()
                                        }
                                    }
                                    .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xs),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = if (uiState.selectedIds.size == uiState.items.size) "Deselect All" else "Select All",
                                    style = NexusTheme.typography.caption.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = NexusTheme.colors.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
            )

            // ── Search Field (Animated Visibility) ───────────────────────────
            AnimatedVisibility(
                visible = showSearchField,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                ) {
                    NexusTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = "Search trashed documents...",
                        leadingIcon = {
                            Image(
                                painter = painterResource(id = R.drawable.ic_search),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                            )
                        },
                        trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                            @Composable {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Clear",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .springBounceClick { viewModel.setSearchQuery("") },
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Main Content ─────────────────────────────────────────────────
            if (uiState.totalCount == 0 && !uiState.isLoading) {
                // Empty state
                RecycleBinEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = if (uiState.isSelectionMode) 130.dp else 40.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                ) {
                    // Summary Banner Card
                    item {
                        RecycleBinStorageCard(
                            totalCount = uiState.totalCount,
                            formattedTotalSize = uiState.formattedTotalSize,
                            retentionDays = uiState.retentionDays
                        )
                    }

                    // Section header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NexusTheme.spacing.xs, bottom = NexusTheme.spacing.xxs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NexusText(
                                text = "TRASHED FILES (${uiState.items.size})",
                                style = NexusTheme.typography.label.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }

                    // Items list
                    itemsIndexed(
                        items = uiState.items,
                        key = { _, item -> item.doc.id }
                    ) { index, item ->
                        val isSelected = uiState.selectedIds.contains(item.doc.id)
                        TrashedDocumentCard(
                            item = item,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = isSelected,
                            modifier = Modifier.staggeredSlideFadeIn(index = index, baseDelayMs = 25, maxDelayMs = 200, offsetY = 12.dp),
                            onToggleSelect = { viewModel.toggleSelection(item.doc.id) },
                            onRestore = { viewModel.restore(item.doc) },
                            onDeletePermanently = { singleDocToDeletePermanently = item.doc }
                        )
                    }
                }
            }
        }

        // ── Multi-Selection Floating Action Dock ─────────────────────────────
        AnimatedVisibility(
            visible = uiState.isSelectionMode && uiState.selectedIds.isNotEmpty(),
            enter = slideInVertically(
                initialOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.md)
                    .shadow(
                        elevation = 16.dp,
                        shape = NexusTheme.shapes.large,
                        spotColor = Color.Black.copy(alpha = 0.35f),
                        ambientColor = Color.Black.copy(alpha = 0.15f)
                    )
                    .clip(NexusTheme.shapes.large)
                    .background(NexusTheme.colors.surface)
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.8f),
                        shape = NexusTheme.shapes.large
                    )
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        NexusText(
                            text = "${uiState.selectedIds.size} Selected",
                            style = NexusTheme.typography.title.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                        NexusText(
                            text = "Batch Actions",
                            style = NexusTheme.typography.labelXs,
                            color = NexusTheme.colors.textSecondary
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Restore Selected Button
                        Box(
                            modifier = Modifier
                                .clip(NexusTheme.shapes.medium)
                                .background(NexusTheme.colors.primary)
                                .springBounceClick { viewModel.restoreSelected() }
                                .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_rotate),
                                    contentDescription = "Restore",
                                    modifier = Modifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                NexusText(
                                    text = "Restore (${uiState.selectedIds.size})",
                                    style = NexusTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = NexusTheme.colors.onPrimary
                                )
                            }
                        }

                        // Delete Permanently Selected Button
                        Box(
                            modifier = Modifier
                                .clip(NexusTheme.shapes.medium)
                                .background(NexusTheme.colors.error.copy(alpha = 0.15f))
                                .border(1.dp, NexusTheme.colors.error.copy(alpha = 0.3f), NexusTheme.shapes.medium)
                                .springBounceClick { showDeleteSelectedDialog = true }
                                .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = "Delete",
                                modifier = Modifier.size(17.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.error)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    // 1. Empty Trash Dialog
    if (showEmptyTrashDialog) {
        NexusDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { NexusText("Empty Recycle Bin?", style = NexusTheme.typography.h2) },
            text = {
                NexusText(
                    "All ${uiState.totalCount} trashed documents (${uiState.formattedTotalSize}) will be permanently deleted. This action cannot be undone."
                )
            },
            confirmButton = {
                NexusButton(
                    text = "Empty Trash",
                    containerColor = NexusTheme.colors.error,
                    contentColor = NexusTheme.colors.background,
                    onClick = {
                        viewModel.emptyTrash()
                        showEmptyTrashDialog = false
                    }
                )
            },
            dismissButton = {
                NexusButton(
                    text = "Cancel",
                    isOutlined = true,
                    onClick = { showEmptyTrashDialog = false }
                )
            }
        )
    }

    // 2. Single Document Delete Permanently Dialog
    singleDocToDeletePermanently?.let { doc ->
        NexusDialog(
            onDismissRequest = { singleDocToDeletePermanently = null },
            title = { NexusText("Delete Permanently?", style = NexusTheme.typography.h2) },
            text = {
                NexusText("Are you sure you want to permanently delete \"${doc.fileName}\"? This cannot be undone.")
            },
            confirmButton = {
                NexusButton(
                    text = "Delete",
                    containerColor = NexusTheme.colors.error,
                    contentColor = NexusTheme.colors.background,
                    onClick = {
                        viewModel.deletePermanently(doc)
                        singleDocToDeletePermanently = null
                    }
                )
            },
            dismissButton = {
                NexusButton(
                    text = "Cancel",
                    isOutlined = true,
                    onClick = { singleDocToDeletePermanently = null }
                )
            }
        )
    }

    // 3. Batch Delete Selected Dialog
    if (showDeleteSelectedDialog) {
        NexusDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { NexusText("Delete Selected Files?", style = NexusTheme.typography.h2) },
            text = {
                NexusText(
                    "Permanently delete ${uiState.selectedIds.size} selected file${if (uiState.selectedIds.size > 1) "s" else ""}? This action cannot be undone."
                )
            },
            confirmButton = {
                NexusButton(
                    text = "Delete Forever",
                    containerColor = NexusTheme.colors.error,
                    contentColor = NexusTheme.colors.background,
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteSelectedDialog = false
                    }
                )
            },
            dismissButton = {
                NexusButton(
                    text = "Cancel",
                    isOutlined = true,
                    onClick = { showDeleteSelectedDialog = false }
                )
            }
        )
    }
}

// ─── Storage Reclaimable Banner Card ──────────────────────────────────────────

@Composable
private fun RecycleBinStorageCard(
    totalCount: Int,
    formattedTotalSize: String,
    retentionDays: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fadeSlideIn(delay = 50)
            .shadow(
                elevation = 3.dp,
                shape = NexusTheme.shapes.large,
                spotColor = Color.Black.copy(alpha = 0.08f),
                ambientColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(NexusTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        NexusTheme.colors.surfaceVariant.copy(alpha = 0.85f),
                        NexusTheme.colors.surface
                    )
                )
            )
            .shimmerGlow(
                accentColor = NexusTheme.colors.primary,
                durationMs = 2800
            )
            .border(
                width = 0.8.dp,
                color = NexusTheme.colors.divider.copy(alpha = 0.7f),
                shape = NexusTheme.shapes.large
            )
            .padding(NexusTheme.spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(NexusTheme.shapes.medium)
                    .background(NexusTheme.colors.primary.copy(alpha = 0.12f))
                    .ambientGlow(
                        color = NexusTheme.colors.primary,
                        alpha = 0.25f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                NexusText(
                    text = "$totalCount item${if (totalCount != 1) "s" else ""} · $formattedTotalSize reclaimable",
                    style = NexusTheme.typography.title.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = NexusTheme.colors.textPrimary
                )
                NexusText(
                    text = if (retentionDays > 0) "Auto-purges files older than $retentionDays days" else "Auto-purge disabled",
                    style = NexusTheme.typography.caption,
                    color = NexusTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ─── Trashed Document Card ────────────────────────────────────────────────────

@Composable
private fun TrashedDocumentCard(
    item: TrashedDocumentUiModel,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val lowerName = item.doc.fileName.lowercase()
    val (formatIconRes, formatColor) = when {
        lowerName.endsWith(".pdf") -> Pair(R.drawable.ic_file_pdf_new, DocumentAccentColors.PDF)
        lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> Pair(R.drawable.ic_file_word_new, DocumentAccentColors.DOCX)
        lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") -> Pair(R.drawable.ic_file_sheet_new, DocumentAccentColors.XLSX)
        lowerName.endsWith(".txt") -> Pair(R.drawable.ic_file_txt_new, DocumentAccentColors.TXT)
        else -> Pair(R.drawable.ic_file_gen_new, NexusTheme.colors.primary)
    }

    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider.copy(alpha = 0.6f),
        label = "cardBorderColor"
    )

    val cardBgColor by animateColorAsState(
        targetValue = if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.08f) else NexusTheme.colors.surface,
        label = "cardBgColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.medium)
            .background(cardBgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.8.dp,
                color = cardBorderColor,
                shape = NexusTheme.shapes.medium
            )
            .clickable {
                if (isSelectionMode) {
                    onToggleSelect()
                }
            }
            .padding(NexusTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox when selection mode is active
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider,
                                shape = CircleShape
                            )
                            .springBounceClick { onToggleSelect() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Selected",
                                modifier = Modifier.size(14.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // File format icon badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(NexusTheme.shapes.medium)
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f))
                        .ambientGlow(color = formatColor, alpha = 0.35f)
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = formatIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // File Name & Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    NexusText(
                        text = item.doc.fileName,
                        style = NexusTheme.typography.body.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = NexusTheme.colors.textPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                    ) {
                        NexusText(
                            text = item.formattedSize,
                            style = NexusTheme.typography.label,
                            color = NexusTheme.colors.textSecondary
                        )

                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.textSecondary.copy(alpha = 0.5f))
                        )

                        NexusText(
                            text = item.formattedTrashedDate,
                            style = NexusTheme.typography.label,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }

                // Retention countdown pill
                val pillBg = if (item.isExpiringSoon) NexusTheme.colors.warning.copy(alpha = 0.15f) else NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f)
                val pillText = if (item.isExpiringSoon) NexusTheme.colors.warning else NexusTheme.colors.textSecondary

                Box(
                    modifier = Modifier
                        .clip(NexusTheme.shapes.large)
                        .background(pillBg)
                        .then(
                            if (item.isExpiringSoon) {
                                Modifier.breathePulse(
                                    minScale = 0.94f,
                                    maxScale = 1.05f,
                                    durationMs = 1400
                                )
                            } else Modifier
                        )
                        .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = item.retentionLabel,
                        style = NexusTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        color = pillText
                    )
                }
            }

            // Tags row if document had tags
            if (item.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (isSelectionMode) 36.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                ) {
                    item.tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(NexusTheme.shapes.small)
                                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f))
                                .padding(horizontal = NexusTheme.spacing.xs, vertical = 2.dp)
                        ) {
                            NexusText(
                                text = "#$tag",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Quick Actions: Restore & Delete Permanently (when not in multi-selection mode)
            if (!isSelectionMode) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = NexusTheme.colors.divider.copy(alpha = 0.4f),
                    thickness = 0.6.dp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restore Button
                    Box(
                        modifier = Modifier
                            .clip(NexusTheme.shapes.small)
                            .background(NexusTheme.colors.primary.copy(alpha = 0.12f))
                            .springBounceClick { onRestore() }
                            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_rotate),
                                contentDescription = "Restore",
                                modifier = Modifier.size(14.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            NexusText(
                                text = "Restore",
                                style = NexusTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                color = NexusTheme.colors.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Delete Permanently Button
                    Box(
                        modifier = Modifier
                            .clip(NexusTheme.shapes.small)
                            .background(NexusTheme.colors.error.copy(alpha = 0.10f))
                            .springBounceClick { onDeletePermanently() }
                            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = "Delete Permanently",
                                modifier = Modifier.size(14.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.error)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            NexusText(
                                text = "Delete",
                                style = NexusTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                color = NexusTheme.colors.error
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun RecycleBinEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NexusTheme.spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fadeSlideIn(delay = 100)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f))
                    .breathePulse(minScale = 0.94f, maxScale = 1.06f, durationMs = 2400),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary.copy(alpha = 0.6f))
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            NexusText(
                text = "Recycle Bin is Empty",
                style = NexusTheme.typography.title,
                color = NexusTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            NexusText(
                text = "Documents you delete will appear here. You can restore them anytime within your retention window before they are permanently purged.",
                style = NexusTheme.typography.bodySmall,
                color = NexusTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
