package com.nexus.feature.dashboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTextField
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusDialog
import com.nexus.feature.dashboard.data.RecentDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagerScreen(
    tags: List<TagUiModel>,
    documents: List<RecentDocumentUiModel>,
    onBack: () -> Unit,
    onRenameTag: (oldName: String, newName: String, colorHex: String, emoji: String?) -> Unit,
    onUpsertTag: (name: String, colorHex: String, emoji: String?) -> Unit,
    onDeleteTag: (name: String) -> Unit,
    onOpenDocument: (RecentDocument) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var editingTag by remember { mutableStateOf<TagUiModel?>(null) }
    var deletingTag by remember { mutableStateOf<TagUiModel?>(null) }
    var inspectingTag by remember { mutableStateOf<TagUiModel?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }

    val filteredTags = remember(tags, searchQuery) {
        if (searchQuery.isBlank()) tags
        else tags.filter { it.name.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    val totalTaggedDocuments = remember(documents) {
        documents.count { it.tags.isNotEmpty() }
    }

    Scaffold(
        containerColor = NexusTheme.colors.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusTheme.colors.surface)
                    .statusBarsPadding()
                    .padding(start = NexusTheme.spacing.md, end = NexusTheme.spacing.md, top = NexusTheme.spacing.md, bottom = NexusTheme.spacing.sm)
            ) {
                // Header Top Row: Back button, Titles, New Tag Pill Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f))
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), CircleShape)
                            .springBounceClick(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        NexusText(
                            text = "Tag Library",
                            style = NexusTheme.typography.title.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                        NexusText(
                            text = "${tags.size} custom tags · $totalTaggedDocuments tagged files",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = NexusTheme.colors.textSecondary
                        )
                    }

                    // Create Tag Pill Button
                    Box(
                        modifier = Modifier
                            .clip(NexusTheme.shapes.pill)
                            .background(NexusTheme.colors.primary)
                            .springBounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showCreateTagDialog = true
                            }
                            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xxs)
                        ) {
                            NexusText(
                                text = "+",
                                color = NexusTheme.colors.onPrimary,
                                style = NexusTheme.typography.body.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            NexusText(
                                text = "New Tag",
                                color = NexusTheme.colors.onPrimary,
                                style = NexusTheme.typography.body.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NexusTheme.shapes.pill)
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.pill)
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = "Search",
                            modifier = Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = NexusTheme.typography.body.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = NexusTheme.colors.textPrimary
                            ),
                            cursorBrush = SolidColor(NexusTheme.colors.primary),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    NexusText(
                                        text = "Search tags in library...",
                                        style = NexusTheme.typography.body,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(NexusTheme.colors.textSecondary.copy(alpha = 0.2f))
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(12.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (filteredTags.isEmpty()) {
                if (searchQuery.isNotBlank()) {
                    // Search Empty State
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(NexusTheme.spacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                            modifier = Modifier.widthIn(max = 400.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(NexusTheme.colors.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_tag),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary)
                                )
                            }
                            NexusText(
                                text = "No matching tags found",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NexusTheme.colors.textPrimary
                            )
                            NexusText(
                                text = "Try searching for another tag name or clear search filter.",
                                style = NexusTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                modifier = Modifier
                                    .clip(NexusTheme.shapes.pill)
                                    .background(NexusTheme.colors.surfaceVariant)
                                    .border(1.dp, NexusTheme.colors.divider, NexusTheme.shapes.pill)
                                    .clickable { searchQuery = "" }
                                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs)
                            ) {
                                NexusText(
                                    text = "Clear Search",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                } else {
                    // Main Empty State with Illustration & Quick-start Presets
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Illustration
                        Image(
                            painter = painterResource(id = R.drawable.tag_empty_state),
                            contentDescription = "No tags illustration",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .padding(horizontal = NexusTheme.spacing.sm),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Primary CTA
                        Box(
                            modifier = Modifier
                                .clip(NexusTheme.shapes.pill)
                                .background(NexusTheme.colors.primary)
                                .springBounceClick {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showCreateTagDialog = true
                                }
                                .padding(horizontal = NexusTheme.spacing.lg, vertical = NexusTheme.spacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_tag_filled),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(NexusTheme.colors.onPrimary)
                                )
                                NexusText(
                                    text = "Create your first tag",
                                    color = NexusTheme.colors.onPrimary,
                                    style = NexusTheme.typography.body.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        // Quick-start Presets Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NexusTheme.spacing.xl, bottom = NexusTheme.spacing.md)
                        ) {
                            NexusText(
                                text = "QUICK START",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = NexusTheme.colors.textSecondary,
                                modifier = Modifier.padding(start = 2.dp, bottom = NexusTheme.spacing.sm)
                            )

                            val quickPresets = listOf(
                                Triple("Work", "#3B82F6", "💼"),
                                Triple("Personal", "#8B5CF6", "📝"),
                                Triple("Urgent", "#EF4444", "⚡"),
                                Triple("Archive", "#64748B", "📁")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                                ) {
                                    quickPresets.take(2).forEach { (name, colorHex, emoji) ->
                                        PresetChip(
                                            name = name,
                                            colorHex = colorHex,
                                            emoji = emoji,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onUpsertTag(name, colorHex, emoji)
                                            }
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                                ) {
                                    quickPresets.drop(2).forEach { (name, colorHex, emoji) ->
                                        PresetChip(
                                            name = name,
                                            colorHex = colorHex,
                                            emoji = emoji,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onUpsertTag(name, colorHex, emoji)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                ) {
                    items(filteredTags, key = { it.name }) { tagModel ->
                        TagCardItem(
                            tag = tagModel,
                            onInspect = { inspectingTag = tagModel },
                            onEdit = { editingTag = tagModel },
                            onDelete = { deletingTag = tagModel }
                        )
                    }
                }
            }
        }
    }

    // ─── Bottom Sheet: Create / Edit Tag ────────────────────────────────────────
    if (showCreateTagDialog) {
        TagEditorSheet(
            initialTag = null,
            onDismissRequest = { showCreateTagDialog = false },
            onSave = { name, colorHex, emoji ->
                onUpsertTag(name, colorHex, emoji)
                showCreateTagDialog = false
            }
        )
    }

    if (editingTag != null) {
        TagEditorSheet(
            initialTag = editingTag,
            onDismissRequest = { editingTag = null },
            onSave = { name, colorHex, emoji ->
                onRenameTag(editingTag!!.name, name, colorHex, emoji)
                editingTag = null
            }
        )
    }

    // ─── Dialog: Delete Confirmation ───────────────────────────────────────────
    if (deletingTag != null) {
        val target = deletingTag!!
        val attachedFilesCount = remember(target, documents) {
            documents.count { doc -> doc.tags.any { it.name == target.name } }
        }
        NexusDialog(
            onDismissRequest = { deletingTag = null },
            title = {
                NexusText(
                    text = "Delete Tag Globally?",
                    style = NexusTheme.typography.h2
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                    NexusText(
                        text = "Are you sure you want to delete #${target.name}?",
                        style = NexusTheme.typography.body
                    )
                    if (attachedFilesCount > 0) {
                        NexusText(
                            text = "This will remove the tag from $attachedFilesCount document(s). Physical files will NOT be deleted.",
                            style = NexusTheme.typography.caption,
                            color = NexusTheme.colors.warning
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDeleteTag(target.name)
                        deletingTag = null
                    }
                ) {
                    NexusText(
                        text = "Delete",
                        color = NexusTheme.colors.error,
                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) {
                    NexusText(text = "Cancel", color = NexusTheme.colors.textSecondary)
                }
            }
        )
    }

    // ─── Dialog / Bottom Sheet: Inspect Tag Documents ─────────────────────────
    if (inspectingTag != null) {
        val target = inspectingTag!!
        val matchingDocs = remember(target, documents) {
            documents.filter { doc -> doc.tags.any { it.name == target.name } }
        }

        NexusDialog(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 500.dp),
            onDismissRequest = { inspectingTag = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(target.color.copy(alpha = 0.15f))
                            .border(1.dp, target.color.copy(alpha = 0.4f), CircleShape)
                            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
                    ) {
                        NexusText(
                            text = target.chipLabel,
                            color = target.color,
                            style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    NexusText(
                        text = "Files (${matchingDocs.size})",
                        style = NexusTheme.typography.title
                    )
                }
            },
            text = {
                if (matchingDocs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NexusTheme.spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "No documents currently have this tag.",
                            style = NexusTheme.typography.body,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                    ) {
                        items(matchingDocs, key = { it.doc.uri }) { docModel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NexusTheme.shapes.small)
                                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.7f))
                                    .clickable {
                                        inspectingTag = null
                                        onOpenDocument(docModel.doc)
                                    }
                                    .padding(NexusTheme.spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    NexusText(
                                        text = docModel.doc.fileName,
                                        style = NexusTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    NexusText(
                                        text = docModel.doc.documentType,
                                        style = NexusTheme.typography.caption,
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(NexusTheme.shapes.small)
                                        .background(NexusTheme.colors.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
                                ) {
                                    NexusText(
                                        text = "Open",
                                        style = NexusTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                        color = NexusTheme.colors.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingTag = null }) {
                    NexusText(text = "Close", color = NexusTheme.colors.primary)
                }
            }
        )
    }
}

@Composable
private fun PresetChip(
    name: String,
    colorHex: String,
    emoji: String,
    onClick: () -> Unit
) {
    val chipColor = remember(colorHex) {
        try { Color(android.graphics.Color.parseColor(colorHex)) }
        catch (_: Exception) { Color(0xFF3B82F6) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.medium)
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.7f), NexusTheme.shapes.medium)
            .springBounceClick(onClick = onClick)
            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(chipColor)
        )

        NexusText(
            text = "$emoji  $name",
            style = NexusTheme.typography.body.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = NexusTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Image(
            painter = painterResource(id = R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun TagCardItem(
    tag: TagUiModel,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tagColor = tag.color

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.medium)
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.65f), NexusTheme.shapes.medium)
            .clickable(onClick = onInspect)
            .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tag Pill Preview
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(tagColor.copy(alpha = 0.14f))
                .border(1.dp, tagColor.copy(alpha = 0.45f), CircleShape)
                .padding(horizontal = NexusTheme.spacing.sm, vertical = NexusTheme.spacing.xs)
        ) {
            NexusText(
                text = tag.chipLabel,
                color = tagColor,
                style = NexusTheme.typography.body.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = if (tag.count == 1) "1 file tagged" else "${tag.count} files tagged",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = NexusTheme.colors.textSecondary
            )
        }

        // Edit button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f))
                .springBounceClick(onClick = onEdit),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_rename),
                contentDescription = "Edit Tag",
                modifier = Modifier.size(15.dp),
                colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Delete button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(NexusTheme.colors.error.copy(alpha = 0.10f))
                .springBounceClick(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = "Delete Tag",
                modifier = Modifier.size(15.dp),
                colorFilter = ColorFilter.tint(NexusTheme.colors.error)
            )
        }
    }
}
