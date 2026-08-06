package com.nexus.feature.reader.pdf.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.utils.glassBackground
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

enum class AnnotationTool(val label: String) {
    Pen("Pen"),
    Highlighter("Highlighter"),
    Eraser("Eraser"),
    Shapes("Shapes")
}

enum class ShapeType(val label: String) {
    Rectangle("Rectangle"),
    Oval("Oval"),
    Line("Line"),
    Arrow("Arrow")
}

@Composable
fun PdfAnnotationPill(
    isVisible: Boolean,
    activeTool: AnnotationTool?,
    onToolSelect: (AnnotationTool) -> Unit,
    selectedShape: ShapeType,
    onShapeSelect: (ShapeType) -> Unit,
    selectedColor: Color,
    onColorSelect: (Color) -> Unit,
    strokeWidth: Float,
    onStrokeWidthSelect: (Float) -> Unit,
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    canRedo: Boolean = false,
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showShapesPopover by remember { mutableStateOf(false) }
    var showColorControls by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            showShapesPopover = false
            showColorControls = false
        }
    }

    val configuration = LocalConfiguration.current
    val maxPillHeight = (configuration.screenHeightDp * 0.72f).dp

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutHorizontally(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { }
            }
        ) {
            // ── Main Vertical Tool Pill ──
            NexusSurface(
                shape = NexusTheme.shapes.pill,
                elevation = 8.dp,
                color = Color.Transparent,
                modifier = Modifier
                    .clip(NexusTheme.shapes.pill)
                    .glassBackground(blurRadius = 30f, alpha = 0.85f, fallbackColor = NexusTheme.colors.surfaceVariant)
                    .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.pill)
                    .padding(vertical = 6.dp, horizontal = 5.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .heightIn(max = maxPillHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Pen Tool (Solid Ink)
                    ToolIconButton(
                        isSelected = activeTool == AnnotationTool.Pen,
                        onClick = {
                            showShapesPopover = false
                            showColorControls = false
                            onToolSelect(AnnotationTool.Pen)
                        },
                        icon = rememberPenIcon(isFilled = activeTool == AnnotationTool.Pen),
                        contentDescription = "Pen"
                    )

                    // 2. Highlighter Tool (Translucent Overlay)
                    ToolIconButton(
                        isSelected = activeTool == AnnotationTool.Highlighter,
                        onClick = {
                            showShapesPopover = false
                            showColorControls = false
                            onToolSelect(AnnotationTool.Highlighter)
                        },
                        icon = rememberHighlighterIcon(isFilled = activeTool == AnnotationTool.Highlighter),
                        contentDescription = "Highlighter"
                    )

                    // 3. Eraser
                    ToolIconButton(
                        isSelected = activeTool == AnnotationTool.Eraser,
                        onClick = {
                            showShapesPopover = false
                            showColorControls = false
                            onToolSelect(AnnotationTool.Eraser)
                        },
                        icon = rememberEraserIcon(isFilled = activeTool == AnnotationTool.Eraser),
                        contentDescription = "Eraser"
                    )

                    // 4. Shapes
                    ToolIconButton(
                        isSelected = activeTool == AnnotationTool.Shapes,
                        onClick = {
                            onToolSelect(AnnotationTool.Shapes)
                            showColorControls = false
                            showShapesPopover = !showShapesPopover
                        },
                        icon = rememberShapesIcon(isFilled = activeTool == AnnotationTool.Shapes),
                        contentDescription = "Shapes"
                    )

                    // 5. Color Palette Toggle
                    ColorIconButton(
                        isSelected = showColorControls,
                        currentColor = selectedColor,
                        onClick = {
                            showShapesPopover = false
                            showColorControls = !showColorControls
                        }
                    )

                    // Divider line
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(1.dp)
                            .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
                    )

                    // 5. Undo
                    ToolIconButton(
                        isSelected = false,
                        onClick = onUndo,
                        icon = rememberUndoIcon(),
                        contentDescription = "Undo",
                        enabled = canUndo
                    )

                    // 6. Redo
                    ToolIconButton(
                        isSelected = false,
                        onClick = onRedo,
                        icon = rememberRedoIcon(),
                        contentDescription = "Redo",
                        enabled = canRedo
                    )
                }
            }

            // ── Anchored Shapes Secondary Popover ──
            AnimatedVisibility(
                visible = showShapesPopover && activeTool == AnnotationTool.Shapes,
                enter = slideInHorizontally(tween(180)) { -it / 2 } + fadeIn(tween(180)),
                exit = slideOutHorizontally(tween(180)) { -it / 2 } + fadeOut(tween(180))
            ) {
                NexusSurface(
                    shape = NexusTheme.shapes.medium,
                    elevation = 6.dp,
                    color = Color.Transparent,
                    modifier = Modifier
                        .clip(NexusTheme.shapes.medium)
                        .glassBackground(blurRadius = 30f, alpha = 0.90f, fallbackColor = NexusTheme.colors.surfaceVariant)
                        .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.medium)
                        .padding(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShapeType.values().forEach { shape ->
                            val isSelected = selectedShape == shape
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.18f)
                                        else Color.Transparent
                                    )
                                    .springBounceClick {
                                        onShapeSelect(shape)
                                        showShapesPopover = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = when (shape) {
                                        ShapeType.Rectangle -> rememberSquareIcon(isFilled = isSelected)
                                        ShapeType.Oval -> rememberCircleIcon(isFilled = isSelected)
                                        ShapeType.Line -> rememberLineIcon(isFilled = isSelected)
                                        ShapeType.Arrow -> rememberArrowIcon(isFilled = isSelected)
                                    },
                                    contentDescription = shape.label,
                                    tint = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                NexusText(
                                    text = shape.label,
                                    style = NexusTheme.typography.caption,
                                    color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // ── Anchored Vertical Color Popover (Opens Next to Main Tool Pill) ──
            AnimatedVisibility(
                visible = showColorControls,
                enter = slideInHorizontally(tween(180)) { -it / 2 } + fadeIn(tween(180)),
                exit = slideOutHorizontally(tween(180)) { -it / 2 } + fadeOut(tween(180))
            ) {
                PdfAnnotationColorVerticalPopover(
                    selectedColor = selectedColor,
                    onColorSelect = onColorSelect,
                    strokeWidth = strokeWidth,
                    onStrokeWidthSelect = onStrokeWidthSelect
                )
            }
        }
    }
}

@Composable
private fun ToolIconButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.35f
    val targetBgColor = if (isSelected) NexusTheme.colors.primary else Color.Transparent
    val targetIconTint = if (isSelected) Color.White else NexusTheme.colors.textPrimary

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 200),
        label = "toolBgColor"
    )
    val animatedIconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = tween(durationMillis = 200),
        label = "toolIconTint"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "toolScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .scale(animatedScale)
            .clip(CircleShape)
            .background(animatedBgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                shape = CircleShape
            )
            .then(
                if (enabled) Modifier.springBounceClick(onClick = onClick)
                else Modifier
            )
            .padding(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedIconTint.copy(alpha = alpha),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ColorIconButton(
    isSelected: Boolean,
    currentColor: Color,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "colorScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .scale(animatedScale)
            .clip(CircleShape)
            .background(
                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) NexusTheme.colors.primary else Color.Transparent,
                shape = CircleShape
            )
            .springBounceClick(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(currentColor)
                .border(1.2.dp, Color.White, CircleShape)
        ) {
            Icon(
                imageVector = rememberPaletteIcon(isFilled = isSelected),
                contentDescription = "Colors",
                tint = if (currentColor == Color.Black || currentColor == Color(0xFF000000)) Color.White else Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
fun PdfAnnotationColorVerticalPopover(
    selectedColor: Color,
    onColorSelect: (Color) -> Unit,
    strokeWidth: Float,
    onStrokeWidthSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color(0xFFFFEB3B), // Yellow Highlighter
        Color(0xFF4CAF50), // Green
        Color(0xFF00BCD4), // Cyan
        Color(0xFFE91E63), // Pink
        Color(0xFFF44336), // Red
        Color(0xFF2196F3), // Blue
        Color(0xFF000000)  // Black
    )

    val strokeWidths = listOf(2f, 4f, 8f, 12f)

    NexusSurface(
        shape = NexusTheme.shapes.medium,
        elevation = 6.dp,
        color = Color.Transparent,
        modifier = modifier
            .clip(NexusTheme.shapes.medium)
            .glassBackground(blurRadius = 30f, alpha = 0.90f, fallbackColor = NexusTheme.colors.surfaceVariant)
            .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), NexusTheme.shapes.medium)
            .padding(vertical = 12.dp, horizontal = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vertical Column of Color Swatches
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { color ->
                    val isSelected = selectedColor == color
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) NexusTheme.colors.primary else Color.Gray.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .springBounceClick { onColorSelect(color) }
                    )
                }
            }

            // Divider line between colors and stroke thickness
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(180.dp)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
            )

            // Vertical Column of Stroke Width Options
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                strokeWidths.forEach { width ->
                    val isSelected = strokeWidth == width
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.20f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) NexusTheme.colors.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .springBounceClick { onStrokeWidthSelect(width) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size((width.coerceIn(2f, 12f)).dp)
                                .clip(CircleShape)
                                .background(if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textSecondary)
                        )
                    }
                }
            }
        }
    }
}

// ── Icons ──
@Composable
fun rememberPenIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "pen",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M20 17v-12c0 -1.121 -.879 -2 -2 -2s-2 .879 -2 2v12l2 2l2 -2").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M16 7h4").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M18 19h-13a2 2 0 1 1 0 -4h4a2 2 0 1 0 0 -4h-3").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberHighlighterIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "highlighter",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M3 19h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M12.5 5.5l4 4").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M4.5 13.5l4 4").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M21 15v4h-8l4 -4l4 0").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberEraserIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "eraser",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M19 20h-10.5l-4.21 -4.3a1 1 0 0 1 0 -1.41l10 -10a1 1 0 0 1 1.41 0l5 5a1 1 0 0 1 0 1.41l-9.2 9.3").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M18 13.3l-6.3 -6.3").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberShapesIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "shapes",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val circleFill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M3 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0").toNodes(),
            fill = circleFill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M17 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0").toNodes(),
            fill = circleFill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M3 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0").toNodes(),
            fill = circleFill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M17 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0").toNodes(),
            fill = circleFill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M5 7l0 10").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M7 5l10 0").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M7 19l10 0").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M19 7l0 10").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
    }.build()
}

@Composable
fun rememberSquareIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "rectangle",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M3 7a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberCircleIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "oval",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberLineIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "line",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val nodeFill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M4 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0").toNodes(),
            fill = nodeFill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M16 6a2 2 0 1 0 4 0a2 2 0 1 0 -4 0").toNodes(),
            fill = nodeFill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f
        )
        addPath(
            pathData = PathParser().parsePathString("M7.5 16.5l9 -9").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberArrowIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "arrow",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        addPath(
            pathData = PathParser().parsePathString("M5 12l14 0").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.4f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M13 18l6 -6").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.4f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M13 6l6 6").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.4f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberPaletteIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "palette",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M 13 6.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0").toNodes(),
            fill = SolidColor(Color.Black)
        )
        addPath(
            pathData = PathParser().parsePathString("M 17 10.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0").toNodes(),
            fill = SolidColor(Color.Black)
        )
        addPath(
            pathData = PathParser().parsePathString("M 6 12.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0").toNodes(),
            fill = SolidColor(Color.Black)
        )
        addPath(
            pathData = PathParser().parsePathString("M 8 7.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0").toNodes(),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

@Composable
fun rememberUndoIcon(): ImageVector = remember {
    ImageVector.Builder(
        name = "undo",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        addPath(
            pathData = PathParser().parsePathString("M9 14l-4 -4l4 -4").toNodes(),
            stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M5 10h11a4 4 0 1 1 0 8h-1").toNodes(),
            stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberRedoIcon(): ImageVector = remember {
    ImageVector.Builder(
        name = "redo",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        addPath(
            pathData = PathParser().parsePathString("M15 14l4 -4l-4 -4").toNodes(),
            stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = PathParser().parsePathString("M19 10h-11a4 4 0 1 0 0 8h1").toNodes(),
            stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}
