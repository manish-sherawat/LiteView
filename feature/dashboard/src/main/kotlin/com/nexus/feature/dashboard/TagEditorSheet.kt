package com.nexus.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.nexus.core.ui.animations.fadeSlideIn
import com.nexus.core.ui.animations.springBounceClick

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditorSheet(
    initialTag: TagUiModel?,
    onDismissRequest: () -> Unit,
    onSave: (name: String, colorHex: String, emoji: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

    var name by remember { mutableStateOf(initialTag?.name ?: "") }
    var selectedColorHex by remember {
        mutableStateOf(initialTag?.colorHex ?: TagColorPresets.PRESET_COLORS.first().hex)
    }
    var selectedEmoji by remember { mutableStateOf(initialTag?.emoji) }

    val activeColor = remember(selectedColorHex) {
        try {
            Color(android.graphics.Color.parseColor(selectedColorHex))
        } catch (_: Exception) {
            Color(0xFF3B82F6)
        }
    }

    val isSaveEnabled = name.trim().isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NexusTheme.colors.surface,
        contentColor = NexusTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = NexusTheme.spacing.xs, bottom = NexusTheme.spacing.xs)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.8f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ─── Header ───────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.md)
                    .padding(bottom = NexusTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NexusText(
                    text = if (initialTag == null) "Create Tag" else "Edit Tag",
                    style = NexusTheme.typography.title.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = NexusTheme.colors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.85f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), CircleShape)
                        .springBounceClick(onClick = onDismissRequest),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(15.dp),
                        colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                    )
                }
            }

            // ─── Scrollable Body ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.md)
            ) {
                // ── Live Preview Pill ──
                Box(
                    modifier = Modifier
                        .fadeSlideIn(delay = 30)
                        .fillMaxWidth()
                        .padding(vertical = NexusTheme.spacing.xxs),
                    contentAlignment = Alignment.Center
                ) {
                    val previewText = if (name.isNotBlank()) "#${name.trim().removePrefix("#")}" else "#Preview"
                    Row(
                        modifier = Modifier
                            .clip(NexusTheme.shapes.pill)
                            .background(activeColor.copy(alpha = 0.14f))
                            .border(
                                width = 1.5.dp,
                                color = activeColor.copy(alpha = 0.38f),
                                shape = NexusTheme.shapes.pill
                            )
                            .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (!selectedEmoji.isNullOrBlank()) {
                            NexusText(
                                text = selectedEmoji!!,
                                style = NexusTheme.typography.body
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        NexusText(
                            text = previewText,
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = activeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ── Tag Name Input Field ──
                Box(
                    modifier = Modifier
                        .fadeSlideIn(delay = 60)
                        .fillMaxWidth()
                        .clip(NexusTheme.shapes.pill)
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.65f))
                        .border(1.5.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), NexusTheme.shapes.pill)
                        .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.sm)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { if (it.length <= 24) name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = NexusTheme.typography.body.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = NexusTheme.colors.textPrimary
                        ),
                        cursorBrush = SolidColor(NexusTheme.colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isSaveEnabled) {
                                    val clean = name.trim().removePrefix("#").trim()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSave(clean, selectedColorHex, selectedEmoji)
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (name.isEmpty()) {
                                NexusText(
                                    text = "Tag name...",
                                    style = NexusTheme.typography.body.copy(
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                // ── Pick Tag Color Section ──
                Column(
                    modifier = Modifier.fadeSlideIn(delay = 90),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)
                ) {
                    NexusText(
                        text = "PICK TAG COLOR",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)
                    ) {
                        TagColorPresets.PRESET_COLORS.forEach { preset ->
                            val isSelected = selectedColorHex.equals(preset.hex, ignoreCase = true)
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.12f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessHigh
                                ),
                                label = "swatchScale"
                            )

                            Box(
                                modifier = Modifier
                                    .scale(scale)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) NexusTheme.colors.textPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .springBounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedColorHex = preset.hex
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        colorFilter = ColorFilter.tint(Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Pick Custom Emoji / Icon Section ──
                Column(
                    modifier = Modifier.fadeSlideIn(delay = 120),
                    verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)
                ) {
                    NexusText(
                        text = "PICK CUSTOM EMOJI / ICON",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        ),
                        color = NexusTheme.colors.textSecondary
                    )

                    // 4 Columns Grid
                    val emojiItems = listOf<String?>(null) + TagColorPresets.PRESET_EMOJIS
                    val rows = emojiItems.chunked(4)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                    ) {
                        rows.forEach { rowEmojis ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs)
                            ) {
                                rowEmojis.forEach { emoji ->
                                    val isSelected = (emoji == null && selectedEmoji == null) || (emoji != null && selectedEmoji == emoji)
                                    val animatedBg by animateColorAsState(
                                        targetValue = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                                        animationSpec = tween(180),
                                        label = "emojiBg"
                                    )
                                    val animatedBorder by animateColorAsState(
                                        targetValue = if (isSelected) NexusTheme.colors.textPrimary else NexusTheme.colors.divider.copy(alpha = 0.6f),
                                        animationSpec = tween(180),
                                        label = "emojiBorder"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(NexusTheme.shapes.medium)
                                            .background(animatedBg)
                                            .border(1.5.dp, animatedBorder, NexusTheme.shapes.medium)
                                            .springBounceClick {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedEmoji = emoji
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (emoji == null) {
                                            NexusText(
                                                text = "None",
                                                style = NexusTheme.typography.body.copy(
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = if (isSelected) NexusTheme.colors.surface else NexusTheme.colors.textSecondary
                                            )
                                        } else {
                                            NexusText(
                                                text = emoji,
                                                style = NexusTheme.typography.h2
                                            )
                                        }
                                    }
                                }
                                // Fill empty columns in last row if not divisible by 4
                                repeat(4 - rowEmojis.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ─── Footer Buttons ───────────────────────────────────────────────────
            HorizontalDivider(
                color = NexusTheme.colors.divider.copy(alpha = 0.5f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(top = NexusTheme.spacing.xs)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexusTheme.spacing.md, vertical = NexusTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary Cancel Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(NexusTheme.shapes.pill)
                        .background(NexusTheme.colors.surface)
                        .border(1.5.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), NexusTheme.shapes.pill)
                        .springBounceClick(onClick = onDismissRequest)
                        .padding(vertical = NexusTheme.spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "Cancel",
                        style = NexusTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }

                // Primary Save Button
                val saveBg = if (isSaveEnabled) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant
                val saveTextColor = if (isSaveEnabled) NexusTheme.colors.onPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.6f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(NexusTheme.shapes.pill)
                        .background(saveBg)
                        .then(
                            if (isSaveEnabled) {
                                Modifier.springBounceClick {
                                    val clean = name.trim().removePrefix("#").trim()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSave(clean, selectedColorHex, selectedEmoji)
                                }
                            } else Modifier
                        )
                        .padding(vertical = NexusTheme.spacing.sm),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = "Save",
                        style = NexusTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = saveTextColor
                    )
                }
            }
        }
    }
}
