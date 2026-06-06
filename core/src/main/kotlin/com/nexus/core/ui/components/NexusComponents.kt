package com.nexus.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.colorSpring
import com.nexus.core.ui.animations.pressSpring

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null && enabled) 0.96f else 1f,
        animationSpec = pressSpring(),
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(6.dp, NexusTheme.shapes.large, spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(NexusTheme.shapes.large)
            .background(NexusTheme.colors.surfaceVariant)
            .then(
                if (onClick != null || onLongClick != null) 
                    Modifier.combinedClickable(
                        interactionSource = interactionSource, 
                        indication = null, 
                        enabled = enabled,
                        onClick = onClick ?: {}, 
                        onLongClick = onLongClick
                    ) 
                else Modifier
            )
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        if (accentColor != null) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(3.dp).fillMaxHeight().background(accentColor))
        }
        content()
    }
}

@Composable
fun NexusSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "Search..."
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(NexusTheme.shapes.pill)
            .background(NexusTheme.colors.surfaceVariant)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
            cursorBrush = SolidColor(NexusTheme.colors.primary),
            modifier = Modifier.fillMaxWidth()
        )
        if (query.isEmpty()) {
            NexusText(
                text = placeholderText,
                style = NexusTheme.typography.body,
                color = NexusTheme.colors.textSecondary
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(NexusTheme.colors.surface)
                    .clickable { onClear() },
                contentAlignment = Alignment.Center
            ) {
                NexusText("x", style = NexusTheme.typography.caption)
            }
        }
    }
}

@Composable
fun NexusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOutlined: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = pressSpring(),
        label = "btnScale"
    )
    
    val bgColor = if (isOutlined) Color.Transparent else NexusTheme.colors.primary
    val contentColor = if (isOutlined) NexusTheme.colors.primary else NexusTheme.colors.onPrimary

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(NexusTheme.shapes.pill)
            .then(if (isOutlined) Modifier.border(1.dp, NexusTheme.colors.primary, NexusTheme.shapes.pill) else Modifier.background(bgColor))
            .clickable(interactionSource, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        NexusText(text = text, color = contentColor, style = NexusTheme.typography.label)
    }
}

@Composable
fun NexusSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val trackColor = if (checked) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant
    val offset by animateDpAsState(targetValue = if (checked) 22.dp else 0.dp, label = "switchOffset")

    Box(
        modifier = modifier
            .size(52.dp, 30.dp)
            .clip(NexusTheme.shapes.pill)
            .background(trackColor)
            .then(
                if (!checked) Modifier.border(1.5.dp, NexusTheme.colors.textSecondary.copy(alpha = 0.5f), NexusTheme.shapes.pill)
                else Modifier
            )
            .clickable { onCheckedChange?.invoke(!checked) }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = offset)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun <T> NexusTabRow(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.pill)
            .background(NexusTheme.colors.surfaceVariant)
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(NexusTheme.shapes.pill)
                    .background(if (isSelected) NexusTheme.colors.primary else Color.Transparent)
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = optionLabel(option),
                    color = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
fun NexusSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    // simplified slider
}

@Composable
fun NexusDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, NexusTheme.shapes.large)
                .clip(NexusTheme.shapes.large)
                .background(NexusTheme.colors.surface)
                .padding(24.dp)
        ) {
            Column {
                if (title != null) {
                    Box(modifier = Modifier.padding(bottom = 16.dp)) { title() }
                }
                if (text != null) {
                    Box(modifier = Modifier
                        .padding(bottom = 24.dp)
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                    ) { text() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}
