package com.nexus.feature.dashboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTextField
import com.nexus.core.ui.animations.springBounceClick

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagementDialog(
    fileName: String,
    currentTags: List<TagUiModel>,
    allAvailableTags: List<TagUiModel>,
    onDismissRequest: () -> Unit,
    onSaveTags: (List<String>) -> Unit,
    onSaveTagDefinition: ((name: String, colorHex: String, emoji: String?) -> Unit)? = null,
    onOpenTagManager: (() -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val isCompact = configuration.screenWidthDp < 360

    val haptic = LocalHapticFeedback.current

    var tagInput by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf(TagColorPresets.PRESET_COLORS.first().hex) }
    var selectedEmoji by remember { mutableStateOf<String?>(null) }
    var showColorEmojiCustomizer by remember { mutableStateOf(false) }

    val assignedTags = remember(currentTags) {
        mutableStateListOf<TagUiModel>().apply { addAll(currentTags) }
    }

    val assignedNames = assignedTags.map { it.name }

    val suggestedTags = remember(allAvailableTags, assignedNames) {
        allAvailableTags.filter { it.name !in assignedNames }
    }

    val activeColor = remember(selectedColorHex) {
        try { Color(android.graphics.Color.parseColor(selectedColorHex)) }
        catch (_: Exception) { Color(0xFF3B82F6) }
    }

    fun addTag(name: String, colorHex: String = selectedColorHex, emoji: String? = selectedEmoji) {
        val clean = name.trim().removePrefix("#").trim()
        if (clean.isNotEmpty() && clean !in assignedTags.map { it.name }) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            assignedTags.add(TagUiModel(name = clean, colorHex = colorHex, emoji = emoji))
            onSaveTagDefinition?.invoke(clean, colorHex, emoji)
            tagInput = ""
        }
    }

    // Use Dialog directly so we fully control the surface — avoids NexusDialog nested scroll issues
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWidth = when {
            isTablet   -> 0.62f
            isLandscape -> 0.72f
            isCompact  -> 0.98f
            else       -> 0.92f
        }
        val maxHeightFraction = if (isLandscape) 0.88f else 0.82f

        Box(
            modifier = Modifier
                .fillMaxWidth(dialogWidth)
                .widthIn(min = 280.dp, max = 520.dp)
                .fillMaxHeight(maxHeightFraction)
                .wrapContentHeight(align = Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NexusTheme.shapes.large)
                    .background(NexusTheme.colors.surface)
                    .imePadding()
            ) {
                // ── Header ──────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = NexusTheme.spacing.md, end = NexusTheme.spacing.md, top = NexusTheme.spacing.md, bottom = NexusTheme.spacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(NexusTheme.colors.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_tag),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                colorFilter = ColorFilter.tint(NexusTheme.colors.primary)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        NexusText(
                            text = "Edit Tags",
                            style = NexusTheme.typography.title.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompact) 16.sp else 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onOpenTagManager != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(NexusTheme.shapes.small)
                                .background(NexusTheme.colors.surfaceVariant)
                                .border(
                                    0.8.dp,
                                    NexusTheme.colors.divider.copy(alpha = 0.4f),
                                    NexusTheme.shapes.small
                                )
                                .springBounceClick {
                                    onDismissRequest()
                                    onOpenTagManager()
                                }
                                .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
                        ) {
                            NexusText(
                                text = "Library ⚙️",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = NexusTheme.colors.primary
                            )
                        }
                    }
                }

                // Thin divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(NexusTheme.colors.divider.copy(alpha = 0.4f))
                )

                // ── Scrollable Body ─────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)
                ) {

                    // ── File name pill ──────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NexusTheme.shapes.small)
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                            .border(
                                0.8.dp,
                                NexusTheme.colors.divider.copy(alpha = 0.3f),
                                NexusTheme.shapes.small
                            )
                            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xs)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_file_gen_new),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        NexusText(
                            text = fileName,
                            style = NexusTheme.typography.caption.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = if (isCompact) 11.sp else 12.sp
                            ),
                            color = NexusTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ── Assigned Tags ───────────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NexusText(
                                text = "Assigned (${assignedTags.size})",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = NexusTheme.colors.textSecondary
                            )
                            if (assignedTags.isNotEmpty()) {
                                NexusText(
                                    text = "Tap ✕ to remove",
                                    style = NexusTheme.typography.labelXs,
                                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.5f)
                                )
                            }
                        }

                        if (assignedTags.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                assignedTags.forEach { tagModel ->
                                    AssignedTagChip(
                                        tagModel = tagModel,
                                        onRemove = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            assignedTags.remove(tagModel)
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NexusTheme.shapes.small)
                                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f))
                                    .border(
                                        0.8.dp,
                                        NexusTheme.colors.divider.copy(alpha = 0.25f),
                                        NexusTheme.shapes.small
                                    )
                                    .padding(vertical = NexusTheme.spacing.sm),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "No tags yet — type below or pick a suggestion",
                                    style = NexusTheme.typography.labelXs,
                                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // ── Tag Input ───────────────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                        NexusTextField(
                            value = tagInput,
                            onValueChange = { tagInput = it },
                            placeholder = "New tag name...",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }),
                            leadingIcon = {
                                ColorDotButton(
                                    activeColor = activeColor,
                                    selectedEmoji = selectedEmoji,
                                    isExpanded = showColorEmojiCustomizer,
                                    onClick = { showColorEmojiCustomizer = !showColorEmojiCustomizer }
                                )
                            },
                            trailingIcon = {
                                AddTagButton(
                                    enabled = tagInput.isNotBlank(),
                                    onClick = { addTag(tagInput) }
                                )
                            }
                        )

                        // ── Color & Emoji Customiser ────────────────────────
                        AnimatedVisibility(
                            visible = showColorEmojiCustomizer,
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeIn(animationSpec = tween(180)),
                            exit = shrinkVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            ) + fadeOut(animationSpec = tween(140))
                        ) {
                            ColorEmojiPicker(
                                activeColor = activeColor,
                                selectedColorHex = selectedColorHex,
                                selectedEmoji = selectedEmoji,
                                haptic = haptic,
                                onColorSelected = { selectedColorHex = it },
                                onEmojiSelected = { selectedEmoji = it }
                            )
                        }
                    }

                    // ── Suggested Tags ──────────────────────────────────────
                    if (suggestedTags.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)) {
                            NexusText(
                                text = "From Library",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = NexusTheme.colors.textSecondary
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                suggestedTags.forEach { tagModel ->
                                    SuggestedTagChip(
                                        tagModel = tagModel,
                                        onAdd = { addTag(tagModel.name, tagModel.colorHex, tagModel.emoji) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Footer Buttons ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(NexusTheme.colors.divider.copy(alpha = 0.4f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(NexusTheme.shapes.pill)
                            .background(NexusTheme.colors.surface)
                            .border(1.2.dp, NexusTheme.colors.divider.copy(alpha = 0.7f), NexusTheme.shapes.pill)
                            .springBounceClick(onClick = onDismissRequest)
                            .padding(vertical = NexusTheme.spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "Cancel",
                            color = NexusTheme.colors.textPrimary,
                            style = NexusTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompact) 13.sp else 14.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(NexusTheme.shapes.pill)
                            .background(NexusTheme.colors.primary)
                            .springBounceClick {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (tagInput.isNotBlank()) addTag(tagInput)
                                onSaveTags(assignedTags.map { it.name })
                                onDismissRequest()
                            }
                            .padding(vertical = NexusTheme.spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "Save",
                            color = NexusTheme.colors.onPrimary,
                            style = NexusTheme.typography.body.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompact) 13.sp else 14.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun AssignedTagChip(
    tagModel: TagUiModel,
    onRemove: () -> Unit
) {
    val tagColor = tagModel.color
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(tagColor.copy(alpha = 0.13f))
            .border(1.dp, tagColor.copy(alpha = 0.4f), CircleShape)
            .padding(start = NexusTheme.spacing.xs, end = NexusTheme.spacing.xxs, top = NexusTheme.spacing.xxs, bottom = NexusTheme.spacing.xxs)
    ) {
        NexusText(
            text = tagModel.chipLabel,
            color = tagColor,
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove",
                modifier = Modifier.size(9.dp),
                colorFilter = ColorFilter.tint(tagColor)
            )
        }
    }
}

@Composable
private fun SuggestedTagChip(
    tagModel: TagUiModel,
    onAdd: () -> Unit
) {
    val tagColor = tagModel.color
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(tagColor.copy(alpha = 0.10f))
            .border(0.8.dp, tagColor.copy(alpha = 0.35f), CircleShape)
            .springBounceClick(onClick = onAdd)
            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xxs)
    ) {
        NexusText(
            text = "+ ${tagModel.chipLabel}",
            style = NexusTheme.typography.caption.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = tagColor
        )
    }
}

@Composable
private fun ColorDotButton(
    activeColor: Color,
    selectedEmoji: String?,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isExpanded) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ), label = "dotScale"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isExpanded) activeColor else activeColor.copy(alpha = 0.4f),
        animationSpec = tween(180), label = "dotBorder"
    )
    Box(
        modifier = Modifier
            .scale(animatedScale)
            .size(26.dp)
            .clip(CircleShape)
            .background(activeColor.copy(alpha = 0.16f))
            .border(1.2.dp, animatedBorderColor, CircleShape)
            .springBounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selectedEmoji != null) {
            NexusText(
                text = selectedEmoji,
                style = NexusTheme.typography.caption
            )
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }
    }
}

@Composable
private fun AddTagButton(enabled: Boolean, onClick: () -> Unit) {
    val animatedBg by animateColorAsState(
        targetValue = if (enabled) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant,
        animationSpec = tween(200), label = "addBtnBg"
    )
    val animatedText by animateColorAsState(
        targetValue = if (enabled) Color.White else NexusTheme.colors.textSecondary,
        animationSpec = tween(200), label = "addBtnText"
    )
    Box(
        modifier = Modifier
            .clip(NexusTheme.shapes.small)
            .background(animatedBg)
            .springBounceClick(onClick = onClick)
            .padding(horizontal = NexusTheme.spacing.xs, vertical = NexusTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = "+ Add",
            color = animatedText,
            style = NexusTheme.typography.caption.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            )
        )
    }
}

@Composable
private fun ColorEmojiPicker(
    activeColor: Color,
    selectedColorHex: String,
    selectedEmoji: String?,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onColorSelected: (String) -> Unit,
    onEmojiSelected: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.medium)
            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f))
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.3f), NexusTheme.shapes.medium)
            .padding(NexusTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
    ) {
        // Color label row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusText(
                text = "Tag Color",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = NexusTheme.colors.textSecondary
            )
            Box(
                modifier = Modifier
                    .clip(NexusTheme.shapes.extraSmall)
                    .background(activeColor.copy(alpha = 0.14f))
                    .padding(horizontal = NexusTheme.spacing.xs, vertical = 2.dp)
            ) {
                NexusText(
                    text = selectedColorHex,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = activeColor
                )
            }
        }

        // Color swatches — horizontal scroll, fixed height, no overflow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagColorPresets.PRESET_COLORS.forEach { preset ->
                val isSelected = selectedColorHex.equals(preset.hex, ignoreCase = true)
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessHigh
                    ), label = "colorScale"
                )
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(preset.color)
                        .border(
                            width = if (isSelected) 2.5.dp else 0.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .springBounceClick {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onColorSelected(preset.hex)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // Emoji label
        NexusText(
            text = "Emoji / Icon",
            style = NexusTheme.typography.caption.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = NexusTheme.colors.textSecondary
        )

        // Emoji swatches — horizontal scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // None chip
            EmojiChip(
                label = "✕",
                isSelected = selectedEmoji == null,
                onClick = { onEmojiSelected(null) }
            )
            TagColorPresets.PRESET_EMOJIS.forEach { emoji ->
                EmojiChip(
                    label = emoji,
                    isSelected = selectedEmoji == emoji,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEmojiSelected(emoji)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmojiChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ), label = "emojiScale"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(30.dp)
            .clip(NexusTheme.shapes.small)
            .background(
                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.18f)
                else NexusTheme.colors.surface
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.divider,
                shape = NexusTheme.shapes.small
            )
            .springBounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        NexusText(
            text = label,
            style = NexusTheme.typography.body
        )
    }
}
