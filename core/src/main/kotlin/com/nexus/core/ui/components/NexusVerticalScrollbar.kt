package com.nexus.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import kotlin.math.roundToInt

@Composable
fun NexusVerticalScrollbar(
    pageCount: Int,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight(0.6f)
            .width(24.dp) // Thinner touch target for less interference
            .pointerInput(pageCount) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onDragStarted()
                    
                    do {
                        val event = awaitPointerEvent()
                        val y = event.changes.first().position.y
                        
                        val trackHeight = size.height.toFloat()
                        val thumbHeightPx = 32.dp.toPx() // Slightly smaller thumb height
                        val maxScroll = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
                        
                        val desiredThumbTop = y - thumbHeightPx / 2
                        val newOffsetPx = desiredThumbTop.coerceIn(0f, maxScroll)
                        
                        val fraction = if (maxScroll > 0) newOffsetPx / maxScroll else 0f
                        val newValue = 1f + fraction * (pageCount - 1)
                        
                        onSliderValueChange(newValue)
                        
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                    
                    onDragStopped()
                }
            }
    ) {
        val trackHeight = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }
        val maxScroll = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
        
        // Protect from NaN or infinite when pageCount is 1
        val thumbOffsetPx = if (pageCount > 1) {
            (sliderValue - 1f) / (pageCount - 1f) * maxScroll
        } else 0f

        // Track - very thin
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(2.dp)
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f), CircleShape)
        )
        
        // Thumb - sleek pill attached to the right edge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .size(width = 6.dp, height = 32.dp)
                .background(NexusTheme.colors.primary, CircleShape)
        )
    }
}
