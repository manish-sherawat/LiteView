package com.nexus.feature.reader.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.model.InkPoint
import com.nexus.feature.reader.presentation.model.InkStroke
import com.nexus.feature.reader.presentation.model.InkingTool
import kotlin.math.hypot

private val InkingColors = listOf(
    Color(0xFFFF3333), // Laser Red
    Color(0xFFFFB300), // Amber Yellow
    Color(0xFF00E676), // Neon Green
    Color(0xFF2979FF), // Electric Blue
    Color(0xFFFFFFFF)  // Clean White
)

/**
 * Slide inking and virtual laser pointer overlay.
 */
@Composable
fun SlideInkingOverlay(
    activeTool: InkingTool,
    slideIndex: Int,
    existingStrokes: List<InkStroke>,
    selectedColor: Color,
    strokeWidth: Float,
    onAddStroke: (InkStroke) -> Unit,
    onClearSlideStrokes: () -> Unit,
    onSelectTool: (InkingTool) -> Unit,
    onSelectColor: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeTool == InkingTool.NONE && existingStrokes.isEmpty()) return

    val currentStrokePoints = remember { mutableStateListOf<InkPoint>() }
    var laserPos by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        val canvasModifier = if (activeTool != InkingTool.NONE) {
            Modifier
                .fillMaxSize()
                .pointerInput(activeTool, selectedColor, strokeWidth) {
                    when (activeTool) {
                        InkingTool.LASER -> {
                            detectDragGestures(
                                onDragStart = { offset -> laserPos = offset },
                                onDrag = { change, _ ->
                                    change.consume()
                                    laserPos = change.position
                                },
                                onDragEnd = { laserPos = null },
                                onDragCancel = { laserPos = null }
                            )
                        }

                        InkingTool.PEN, InkingTool.HIGHLIGHTER -> {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStrokePoints.clear()
                                    currentStrokePoints.add(
                                        InkPoint(offset.x / size.width, offset.y / size.height)
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStrokePoints.add(
                                        InkPoint(change.position.x / size.width, change.position.y / size.height)
                                    )
                                },
                                onDragEnd = {
                                    if (currentStrokePoints.size >= 2) {
                                        onAddStroke(
                                            InkStroke(
                                                points = currentStrokePoints.toList(),
                                                colorArgb = selectedColor.value.toInt(),
                                                strokeWidth = if (activeTool == InkingTool.HIGHLIGHTER) strokeWidth * 3.5f else strokeWidth,
                                                isHighlighter = (activeTool == InkingTool.HIGHLIGHTER)
                                            )
                                        )
                                    }
                                    currentStrokePoints.clear()
                                },
                                onDragCancel = { currentStrokePoints.clear() }
                            )
                        }

                        InkingTool.ERASER -> {
                            detectDragGestures(
                                onDrag = { change, _ ->
                                    change.consume()
                                    // Touch-to-erase
                                }
                            )
                        }

                        InkingTool.NONE -> {}
                    }
                }
        } else {
            Modifier.fillMaxSize()
        }

        Canvas(modifier = canvasModifier) {
            val w = size.width
            val h = size.height

            // Render existing committed strokes
            for (stroke in existingStrokes) {
                if (stroke.points.size < 2) continue
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(first.x * w, first.y * h)
                for (p in stroke.points.drop(1)) {
                    path.lineTo(p.x * w, p.y * h)
                }
                val strokeColor = Color(stroke.colorArgb).let {
                    if (stroke.isHighlighter) it.copy(alpha = 0.35f) else it
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = stroke.strokeWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Render current active stroke in progress
            if (currentStrokePoints.size >= 2) {
                val path = Path()
                val first = currentStrokePoints.first()
                path.moveTo(first.x * w, first.y * h)
                for (p in currentStrokePoints.drop(1)) {
                    path.lineTo(p.x * w, p.y * h)
                }
                val isHighlighter = (activeTool == InkingTool.HIGHLIGHTER)
                val currentWidth = if (isHighlighter) strokeWidth * 3.5f else strokeWidth
                val strokeColor = if (isHighlighter) selectedColor.copy(alpha = 0.35f) else selectedColor
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = currentWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Render Glowing Laser Pointer
            laserPos?.let { pos ->
                val haloRadius = 24.dp.toPx()
                val coreRadius = 7.dp.toPx()

                // Neon halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            selectedColor.copy(alpha = 0.85f),
                            selectedColor.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = haloRadius
                    ),
                    radius = haloRadius,
                    center = pos
                )

                // Bright core
                drawCircle(
                    color = Color.White,
                    radius = coreRadius * 0.7f,
                    center = pos
                )
                drawCircle(
                    color = selectedColor,
                    radius = coreRadius,
                    style = Stroke(width = 2.dp.toPx()),
                    center = pos
                )
            }
        }

        // Floating Inking Controls Dock
        if (activeTool != InkingTool.NONE) {
            NexusSurface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.85f),
                elevation = 12.dp,
                borderWidth = 1.dp,
                borderColor = NexusTheme.colors.divider.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Laser Pointer Tool
                InkingToolButton(
                    icon = Icons.Default.NearMe,
                    label = "Laser",
                    isSelected = (activeTool == InkingTool.LASER),
                    onClick = { onSelectTool(InkingTool.LASER) }
                )

                // Pen Tool
                InkingToolButton(
                    icon = Icons.Default.Edit,
                    label = "Pen",
                    isSelected = (activeTool == InkingTool.PEN),
                    onClick = { onSelectTool(InkingTool.PEN) }
                )

                // Highlighter Tool
                InkingToolButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlighter",
                    isSelected = (activeTool == InkingTool.HIGHLIGHTER),
                    onClick = { onSelectTool(InkingTool.HIGHLIGHTER) }
                )

                // Color Selection Circles
                if (activeTool == InkingTool.PEN || activeTool == InkingTool.HIGHLIGHTER || activeTool == InkingTool.LASER) {
                    Spacer(modifier = Modifier.width(4.dp))
                    for (c in InkingColors) {
                        val isCurrent = (c == selectedColor)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) Color.White else Color.Black.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(c) }
                        )
                    }
                }

                // Clear All Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onClearSlideStrokes() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Exit Inking Tool Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onSelectTool(InkingTool.NONE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Inking",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
}

@Composable
private fun InkingToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.35f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) NexusTheme.colors.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) NexusTheme.colors.primary else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            NexusText(
                text = label,
                style = NexusTheme.typography.caption.copy(fontSize = 11.sp),
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f)
            )
        }
    }
}
