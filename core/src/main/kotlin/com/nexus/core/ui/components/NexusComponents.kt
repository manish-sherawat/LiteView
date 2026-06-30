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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.colorSpring
import com.nexus.core.ui.animations.pressSpring
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.animations.springBounceCombinedClick
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import com.nexus.core.R

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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, NexusTheme.shapes.large, spotColor = Color.Black.copy(alpha = 0.12f), ambientColor = Color.Black.copy(alpha = 0.05f))
            .clip(NexusTheme.shapes.large)
            .background(NexusTheme.colors.surfaceVariant)
            .border(1.dp, NexusTheme.colors.textPrimary.copy(alpha = 0.2f), NexusTheme.shapes.large)
            .then(
                if (onClick != null || onLongClick != null) 
                    Modifier.springBounceCombinedClick(
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
            .heightIn(min = 48.dp)
            .wrapContentHeight()
            .clip(NexusTheme.shapes.pill)
            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = "Search",
                colorFilter = ColorFilter.tint(NexusTheme.colors.textSecondary),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = NexusTheme.typography.body.copy(color = NexusTheme.colors.textPrimary),
                    cursorBrush = SolidColor(NexusTheme.colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { 
                            contentDescription = placeholderText 
                        }
                )
                if (query.isEmpty()) {
                    NexusText(
                        text = placeholderText,
                        style = NexusTheme.typography.body,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
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
}

@Composable
fun NexusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOutlined: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = NexusTheme.colors.primary,
    contentColor: Color = NexusTheme.colors.onPrimary,
    shape: androidx.compose.ui.graphics.Shape = NexusTheme.shapes.pill
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = pressSpring(),
        label = "btnScale"
    )
    
    val bgColor = if (isOutlined) Color.Transparent else containerColor
    val finalContentColor = if (isOutlined) containerColor else contentColor

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(if (isOutlined) Modifier.border(1.dp, containerColor, shape) else Modifier.background(bgColor))
            .clickable(interactionSource, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        NexusText(text = text, color = finalContentColor, style = NexusTheme.typography.buttonLabel)
    }
}

@Composable
fun NexusSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) NexusTheme.colors.primary else NexusTheme.colors.surfaceVariant.copy(alpha = 0.8f),
        animationSpec = com.nexus.core.ui.animations.colorSpring(),
        label = "switchTrack"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) NexusTheme.colors.onPrimary else NexusTheme.colors.textSecondary,
        animationSpec = com.nexus.core.ui.animations.colorSpring(),
        label = "switchThumb"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.Transparent else NexusTheme.colors.textSecondary.copy(alpha = 0.7f),
        animationSpec = com.nexus.core.ui.animations.colorSpring(),
        label = "switchBorder"
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.65f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "switchOffset"
    )

    Box(
        modifier = modifier
            .size(52.dp, 30.dp)
            .clip(NexusTheme.shapes.pill)
            .background(trackColor)
            .border(1.5.dp, borderColor, NexusTheme.shapes.pill)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { newValue -> onCheckedChange?.invoke(newValue) }
            )
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }
                .size(22.dp)
                .shadow(if (checked) 8.dp else 2.dp, CircleShape, spotColor = if (checked) trackColor else Color.Black)
                .clip(CircleShape)
                .background(thumbColor)
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
            
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) NexusTheme.colors.primary else Color.Transparent,
                animationSpec = com.nexus.core.ui.animations.colorSpring(),
                label = "tabBg"
            )
            
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) NexusTheme.colors.onPrimary else NexusTheme.colors.textSecondary,
                animationSpec = com.nexus.core.ui.animations.colorSpring(),
                label = "tabContent"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(NexusTheme.shapes.pill)
                    .background(bgColor)
                    .springBounceClick { onOptionSelected(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = optionLabel(option),
                    color = contentColor,
                    style = NexusTheme.typography.buttonLabel
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
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .fillMaxWidth()
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val percent = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + percent * (valueRange.endInclusive - valueRange.start))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val percent = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + percent * (valueRange.endInclusive - valueRange.start))
                    }
                )
            }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val percent = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + percent * (valueRange.endInclusive - valueRange.start))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(NexusTheme.shapes.pill)
                .background(NexusTheme.colors.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(NexusTheme.colors.primary)
            )
        }
        
        // Thumb
        Box(
            modifier = Modifier
                .offset(x = maxWidth * fraction - 12.dp)
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(NexusTheme.colors.primary)
        )
    }
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
    var isVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { isVisible = true }

    Dialog(onDismissRequest = onDismissRequest) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = androidx.compose.animation.scaleIn(
                animationSpec = com.nexus.core.ui.animations.dialogSpring(),
                initialScale = 0.8f
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.scaleOut(
                targetScale = 0.8f,
                animationSpec = androidx.compose.animation.core.tween(150)
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(150)
            )
        ) {
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
}

@Composable
fun NexusIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = NexusTheme.colors.surface,
    elevation: androidx.compose.ui.unit.Dp = 4.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.9f else 1f,
        animationSpec = pressSpring(),
        label = "iconBtnScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation, CircleShape)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(interactionSource, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(48.dp).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
