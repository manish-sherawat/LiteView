package com.nexus.core.ui.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import androidx.compose.ui.graphics.ColorFilter
import com.nexus.core.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

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
            .shadow(8.dp, NexusTheme.shapes.large, spotColor = Color.Black.copy(alpha = 0.12f), ambientColor = Color.Black.copy(alpha = 0.05f))
            .clip(NexusTheme.shapes.large)
            .background(NexusTheme.colors.surfaceVariant)
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) NexusTheme.colors.surface else NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f),
        label = "searchBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) NexusTheme.colors.primary else NexusTheme.colors.textPrimary.copy(alpha = 0.15f),
        label = "searchBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .wrapContentHeight()
            .clip(NexusTheme.shapes.pill)
            .background(bgColor)
            .border(1.5.dp, borderColor, NexusTheme.shapes.pill)
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
                colorFilter = ColorFilter.tint(if (isFocused) NexusTheme.colors.primary else NexusTheme.colors.textSecondary),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    interactionSource = interactionSource,
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
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.semantics { contentDescription = "" }
                    )
                }
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onClear() }
                        .semantics { contentDescription = "Clear search" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(NexusTheme.colors.surface), contentAlignment = Alignment.Center) {
                        NexusText("x", style = NexusTheme.typography.caption)
                    }
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
    shape: androidx.compose.ui.graphics.Shape = NexusTheme.shapes.pill,
    contentDescription: String = text,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed && enabled -> 0.96f
            isFocused && enabled -> 1.02f
            else -> 1f
        },
        animationSpec = pressSpring(),
        label = "btnScale"
    )
    
    val bgColor = if (isOutlined) Color.Transparent else containerColor
    val finalContentColor = if (isOutlined) containerColor else contentColor

    val horizontalPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isLoading) 12.dp else 24.dp,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "btnPadding"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled && !isLoading) 1f else if (isLoading) 1f else 0.5f)
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(if (isOutlined) Modifier.border(1.5.dp, containerColor, shape) else Modifier.background(bgColor))
            .indication(interactionSource, LocalIndication.current)
            .clickable(
                interactionSource = interactionSource, 
                indication = null,
                enabled = enabled && !isLoading, 
                role = Role.Button,
                onClick = {
                    try {
                        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                        if (vibrator.hasVibrator()) {
                            vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                        }
                    } catch (e: Exception) { }
                    onClick()
                }
            )
            .padding(horizontal = horizontalPadding, vertical = 12.dp)
            .animateContentSize(animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f))
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isLoading, 
            animationSpec = androidx.compose.animation.core.tween(200),
            label = "btnCrossfade"
        ) { loading ->
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = finalContentColor,
                    strokeWidth = 2.5.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = finalContentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    NexusText(text = text, color = finalContentColor, style = NexusTheme.typography.buttonLabel)
                }
            }
        }
    }
}

@Composable
fun NexusSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val springSpec = remember { 
        androidx.compose.animation.core.spring<androidx.compose.ui.unit.Dp>(
            dampingRatio = 0.65f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        )
    }
    
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
        targetValue = if (checked) Color.Transparent else NexusTheme.colors.textSecondary,
        animationSpec = com.nexus.core.ui.animations.colorSpring(),
        label = "switchBorder"
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = springSpec,
        label = "switchOffset"
    )

    Box(
        modifier = modifier
            .size(52.dp, 30.dp)
            .clip(NexusTheme.shapes.pill)
            .background(trackColor)
            .border(1.5.dp, borderColor, NexusTheme.shapes.pill)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = { newValue ->
                            try {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                if (vibrator.hasVibrator()) {
                                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
                                }
                            } catch (e: Exception) { }
                            onCheckedChange(newValue)
                        }
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                this.contentDescription = if (checked) "Switch on" else "Switch off"
                this.role = Role.Switch
            }
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
    val context = LocalContext.current
    val listState = rememberLazyListState()
    
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .clip(NexusTheme.shapes.pill)
            .background(NexusTheme.colors.surfaceVariant)
            .border(1.dp, if (!androidx.compose.foundation.isSystemInDarkTheme()) NexusTheme.colors.textPrimary.copy(alpha = 0.15f) else Color.Transparent, NexusTheme.shapes.pill)
            .padding(4.dp)
    ) {
        items(
            items = options,
            key = { option -> option.hashCode() }
        ) { option ->
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
                    .fillParentMaxWidth(1f / options.size.coerceAtLeast(1))
                    .clip(NexusTheme.shapes.pill)
                    .background(bgColor)
                    .springBounceClick {
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            if (vibrator.hasVibrator()) {
                                vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
                            }
                        } catch (e: Exception) { }
                        onOptionSelected(option)
                    }
                    .padding(vertical = 8.dp)
                    .semantics {
                        this.role = Role.Tab
                        this.contentDescription = optionLabel(option)
                        this.selected = isSelected
                    },
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
    var isDragging by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth()
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            if (vibrator.hasVibrator()) {
                                vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
                            }
                        } catch (e: Exception) { }
                        val percent = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + percent * (valueRange.endInclusive - valueRange.start))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val percent = (change.position.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + percent * (valueRange.endInclusive - valueRange.start))
                    },
                    onDragEnd = {
                        isDragging = false
                        try {
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            if (vibrator.hasVibrator()) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            }
                        } catch (e: Exception) { }
                    }
                )
            }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val percent = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(valueRange.start + percent * (valueRange.endInclusive - valueRange.start))
                }
            }
            .semantics {
                this.contentDescription = "Slider"
            },
        contentAlignment = Alignment.CenterStart
    ) {
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
        
        Box(
            modifier = Modifier
                .offset(x = maxWidth * fraction - if (isDragging) 14.dp else 12.dp)
                .size(if (isDragging) 28.dp else 24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .shadow(if (isDragging) 6.dp else 2.dp, CircleShape)
                .background(NexusTheme.colors.primary)
        )
        
        if (isDragging) {
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * fraction - 20.dp, y = -36.dp)
                    .background(NexusTheme.colors.primary, NexusTheme.shapes.pill)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val displayValue = (valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
                NexusText(
                    text = "${Math.round(displayValue * 100) / 100f}",
                    color = NexusTheme.colors.onPrimary,
                    style = NexusTheme.typography.caption
                )
            }
        }
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
    Dialog(onDismissRequest = onDismissRequest) {
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
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
                    .fillMaxWidth(0.85f)
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
                        Box(
                            modifier = Modifier
                                .padding(bottom = 24.dp)
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(end = 8.dp)
                        ) { 
                            text() 
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
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
    contentDescription: String? = null,
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
            .clickable(interactionSource, null, enabled = enabled, onClick = onClick)
            .semantics {
                this.role = Role.Button
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
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
