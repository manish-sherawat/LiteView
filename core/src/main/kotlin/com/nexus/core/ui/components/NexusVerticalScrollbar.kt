package com.nexus.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.utils.glassBackground
import kotlin.math.roundToInt

@Composable
fun NexusVerticalScrollbar(
    pageCount: Int,
    sliderValue: Float,
    isScrolling: Boolean = false,
    onSliderValueChange: (Float) -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    var isDraggingLocal by remember { mutableStateOf(false) }
    val showIndicator = isScrolling || isDraggingLocal

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight(0.7f)
            .width(48.dp) // Wider touch target for easier grabbing, thumb is aligned to right
            .pointerInput(pageCount) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDraggingLocal = true
                    onDragStarted()
                    
                    do {
                        val event = awaitPointerEvent()
                        val y = event.changes.first().position.y
                        
                        val trackHeight = size.height.toFloat()
                        val thumbHeightPx = 40.dp.toPx()
                        val maxScroll = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
                        
                        val desiredThumbTop = y - thumbHeightPx / 2
                        val newOffsetPx = desiredThumbTop.coerceIn(0f, maxScroll)
                        
                        val fraction = if (maxScroll > 0) newOffsetPx / maxScroll else 0f
                        val newValue = 1f + fraction * (pageCount - 1)
                        
                        onSliderValueChange(newValue)
                        
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    
                    isDraggingLocal = false
                    onDragStopped()
                }
            }
    ) {
        val trackHeight = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() }
        val maxScroll = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
        
        // Protect from NaN or infinite when pageCount is 1
        val thumbOffsetPx = if (pageCount > 1) {
            (sliderValue - 1f) / (pageCount - 1f) * maxScroll
        } else 0f

        // Track - subtle rounded line
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(4.dp)
                .padding(vertical = 4.dp)
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.4f), CircleShape)
        )
        
        val thumbWidth by animateDpAsState(
            targetValue = if (isDraggingLocal) 10.dp else 6.dp,
            animationSpec = tween(durationMillis = 150)
        )
        
        val thumbHeight by animateDpAsState(
            targetValue = if (isDraggingLocal) 48.dp else 40.dp,
            animationSpec = tween(durationMillis = 150)
        )

        // Thumb and Indicator Row
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .wrapContentWidth(align = Alignment.End, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Dynamic Page Indicator
            AnimatedVisibility(
                visible = showIndicator,
                enter = fadeIn() + scaleIn(initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)),
                exit = fadeOut() + scaleOut(targetScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .shadow(elevation = 6.dp, shape = NexusTheme.shapes.pill)
                        .clip(NexusTheme.shapes.pill)
                        .background(NexusTheme.colors.surface)
                        .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), NexusTheme.shapes.pill)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    NexusText(
                        text = "${sliderValue.roundToInt()} / $pageCount",
                        style = NexusTheme.typography.body.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = NexusTheme.colors.primary,
                        maxLines = 1
                    )
                }
            }
            
            // Thumb
            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .background(NexusTheme.colors.primary, CircleShape)
            )
        }
    }
}
