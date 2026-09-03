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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

enum class ToolbarDockPosition {
    Left, Right, Top, Bottom
}

enum class AnnotationTool(val label: String) {
    Pen("Pen"),
    Highlighter("Highlighter"),
    Eraser("Eraser"),
    Shapes("Shapes"),
    Text("Text"),
    Stamp("Stamp"),
    Ruler("Ruler")
}

enum class ShapeType(val label: String) {
    Rectangle("Rectangle"),
    Oval("Oval"),
    Line("Line"),
    Arrow("Arrow")
}

enum class StampType(val label: String, val colorHex: Long) {
    APPROVED("APPROVED", 0xFF4CAF50),
    CONFIDENTIAL("CONFIDENTIAL", 0xFFF44336),
    DRAFT("DRAFT", 0xFFFF9800),
    FINAL("FINAL", 0xFF2196F3),
    REJECTED("REJECTED", 0xFFE91E63),
    SIGN_HERE("SIGN HERE", 0xFF9C27B0)
}

enum class EraserTargetFilter(val label: String) {
    All("Erase All"),
    PenOnly("Pen Only"),
    HighlighterOnly("Highlighter Only"),
    ShapesOnly("Shapes Only")
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
    selectedEraserFilter: EraserTargetFilter = EraserTargetFilter.All,
    onEraserFilterSelect: (EraserTargetFilter) -> Unit = {},
    selectedStamp: StampType = StampType.APPROVED,
    onStampSelect: (StampType) -> Unit = {},
    dockPosition: ToolbarDockPosition = ToolbarDockPosition.Left,
    onDockPositionChange: (ToolbarDockPosition) -> Unit = {},
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {},
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    canRedo: Boolean = false,
    onRedo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showShapesPopover by remember { mutableStateOf(false) }
    var showColorControls by remember { mutableStateOf(false) }
    var showEraserPopover by remember { mutableStateOf(false) }
    var showStampsPopover by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    fun performHaptic() {
        try {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } catch (_: Exception) {}
    }

    fun dismissAllPopovers() {
        showShapesPopover = false
        showColorControls = false
        showEraserPopover = false
        showStampsPopover = false
    }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            dismissAllPopovers()
        }
    }

    val configuration = LocalConfiguration.current
    val isHorizontalRibbon = dockPosition == ToolbarDockPosition.Top || dockPosition == ToolbarDockPosition.Bottom
    val maxPillHeight = (configuration.screenHeightDp * 0.72f).toInt().dp

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)) { if (dockPosition == ToolbarDockPosition.Right) it else -it } + fadeIn(),
        exit = slideOutHorizontally(animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)) { if (dockPosition == ToolbarDockPosition.Right) it else -it } + fadeOut(),
        modifier = modifier
    ) {
        if (isCollapsed) {
            // ── Compact Collapsible Avatar Bubble ──
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .glassBackground(blurRadius = 30f, alpha = 0.90f, fallbackColor = NexusTheme.colors.surfaceVariant)
                    .border(1.5.dp, NexusTheme.colors.primary.copy(alpha = 0.7f), CircleShape)
                    .springBounceClick {
                        performHaptic()
                        onToggleCollapse()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (activeTool) {
                        AnnotationTool.Pen -> rememberPenIcon(true)
                        AnnotationTool.Highlighter -> rememberHighlighterIcon(true)
                        AnnotationTool.Eraser -> rememberEraserIcon(true)
                        AnnotationTool.Shapes -> rememberShapesIcon(true)
                        AnnotationTool.Text -> rememberTextIcon(true)
                        AnnotationTool.Stamp -> rememberStampIcon(true)
                        AnnotationTool.Ruler -> rememberRulerIcon(true)
                        null -> rememberPenIcon(false)
                    },
                    contentDescription = "Active Tool Avatar",
                    tint = if (activeTool != null) NexusTheme.colors.primary else NexusTheme.colors.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
                // Color Dot Indicator anchored cleanly
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(1.5.dp, NexusTheme.colors.surface, CircleShape)
                )
            }
        } else {
            var liveDragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val density = LocalDensity.current
            val tapThresholdPx = with(density) { 18.dp.toPx() }
            val dragThresholdPx = with(density) { 48.dp.toPx() }

            val dragHandleContent: @Composable () -> Unit = {
                var totalDragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.15f))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    totalDragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    liveDragOffset = androidx.compose.ui.geometry.Offset.Zero
                                },
                                onDragEnd = {
                                    val (dx, dy) = totalDragOffset
                                    val distance = totalDragOffset.getDistance()
                                    liveDragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    if (distance < tapThresholdPx) {
                                        performHaptic()
                                        onToggleCollapse()
                                    } else {
                                        performHaptic()
                                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                            if (dx > dragThresholdPx) onDockPositionChange(ToolbarDockPosition.Right)
                                            else if (dx < -dragThresholdPx) onDockPositionChange(ToolbarDockPosition.Left)
                                        } else {
                                            if (dy > dragThresholdPx) onDockPositionChange(ToolbarDockPosition.Bottom)
                                            else if (dy < -dragThresholdPx) onDockPositionChange(ToolbarDockPosition.Top)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    totalDragOffset = androidx.compose.ui.geometry.Offset.Zero
                                    liveDragOffset = androidx.compose.ui.geometry.Offset.Zero
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragOffset += dragAmount
                                    liveDragOffset += dragAmount
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rememberDragHandleIcon(),
                        contentDescription = "Dock Drag Handle",
                        tint = NexusTheme.colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val toolButtonsContent: @Composable () -> Unit = {
                // 1. Pen Tool
                ToolIconButton(
                    isSelected = activeTool == AnnotationTool.Pen,
                    onClick = {
                        performHaptic()
                        dismissAllPopovers()
                        onToolSelect(AnnotationTool.Pen)
                    },
                    icon = rememberPenIcon(isFilled = activeTool == AnnotationTool.Pen),
                    contentDescription = "Pen"
                )

                // 2. Highlighter Tool
                ToolIconButton(
                    isSelected = activeTool == AnnotationTool.Highlighter,
                    onClick = {
                        performHaptic()
                        dismissAllPopovers()
                        onToolSelect(AnnotationTool.Highlighter)
                    },
                    icon = rememberHighlighterIcon(isFilled = activeTool == AnnotationTool.Highlighter),
                    contentDescription = "Highlighter"
                )

                // 3. Eraser Tool
                ToolIconButton(
                    isSelected = activeTool == AnnotationTool.Eraser,
                    onClick = {
                        performHaptic()
                        if (activeTool == AnnotationTool.Eraser) {
                            showEraserPopover = !showEraserPopover
                        } else {
                            onToolSelect(AnnotationTool.Eraser)
                            showEraserPopover = true
                        }
                        showShapesPopover = false
                        showColorControls = false
                    },
                    icon = rememberEraserIcon(isFilled = activeTool == AnnotationTool.Eraser),
                    contentDescription = "Eraser"
                )

                // 4. Shapes Tool
                ToolIconButton(
                    isSelected = activeTool == AnnotationTool.Shapes,
                    onClick = {
                        performHaptic()
                        onToolSelect(AnnotationTool.Shapes)
                        showColorControls = false
                        showEraserPopover = false
                        showStampsPopover = false
                        showShapesPopover = !showShapesPopover
                    },
                    icon = rememberShapesIcon(isFilled = activeTool == AnnotationTool.Shapes),
                    contentDescription = "Shapes"
                )

                // 5. Text Box Tool
                ToolIconButton(
                    isSelected = activeTool == AnnotationTool.Text,
                    onClick = {
                        performHaptic()
                        dismissAllPopovers()
                        onToolSelect(AnnotationTool.Text)
                    },
                    icon = rememberTextIcon(isFilled = activeTool == AnnotationTool.Text),
                    contentDescription = "Text Box"
                )

                // 6. Stamp Tool
                ToolIconButton(
                    isSelected = activeTool == AnnotationTool.Stamp,
                    onClick = {
                        performHaptic()
                        onToolSelect(AnnotationTool.Stamp)
                        showColorControls = false
                        showEraserPopover = false
                        showShapesPopover = false
                        showStampsPopover = !showStampsPopover
                    },
                    icon = rememberStampIcon(isFilled = activeTool == AnnotationTool.Stamp),
                    contentDescription = "Stamps"
                )

                // 7. Distance Ruler Tool
                ToolIconButton(
                    isSelected = activeTool == AnnotationTool.Ruler,
                    onClick = {
                        performHaptic()
                        dismissAllPopovers()
                        onToolSelect(AnnotationTool.Ruler)
                    },
                    icon = rememberRulerIcon(isFilled = activeTool == AnnotationTool.Ruler),
                    contentDescription = "Ruler"
                )

                // 8. Color Palette Toggle
                ColorIconButton(
                    isSelected = showColorControls,
                    currentColor = selectedColor,
                    isHighlighter = activeTool == AnnotationTool.Highlighter,
                    onClick = {
                        performHaptic()
                        showShapesPopover = false
                        showEraserPopover = false
                        showStampsPopover = false
                        showColorControls = !showColorControls
                    }
                )

                // Divider line
                Box(
                    modifier = Modifier
                        .size(if (isHorizontalRibbon) 1.dp else 20.dp, if (isHorizontalRibbon) 20.dp else 1.dp)
                        .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
                )

                // 6. Undo
                ToolIconButton(
                    isSelected = false,
                    onClick = {
                        performHaptic()
                        onUndo()
                    },
                    icon = rememberUndoIcon(),
                    contentDescription = "Undo",
                    enabled = canUndo
                )

                // 7. Redo
                ToolIconButton(
                    isSelected = false,
                    onClick = {
                        performHaptic()
                        onRedo()
                    },
                    icon = rememberRedoIcon(),
                    contentDescription = "Redo",
                    enabled = canRedo
                )
            }

            // ── Popovers Composables ──
            val shapesPopoverContent: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = showShapesPopover && activeTool == AnnotationTool.Shapes,
                    enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 350f)) + fadeIn(tween(180)),
                    exit = scaleOut(spring(dampingRatio = 0.8f, stiffness = 350f)) + fadeOut(tween(180))
                ) {
                    NexusSurface(
                        shape = NexusTheme.shapes.medium,
                        elevation = 8.dp,
                        color = Color.Transparent,
                        modifier = Modifier
                            .clip(NexusTheme.shapes.medium)
                            .glassBackground(blurRadius = 30f, alpha = 0.92f, fallbackColor = NexusTheme.colors.surfaceVariant)
                            .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), NexusTheme.shapes.medium)
                            .pointerInput(Unit) { detectTapGestures { } }
                            .padding(8.dp)
                    ) {
                        if (isHorizontalRibbon) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ShapeType.values().forEach { shape ->
                                    val isSelected = selectedShape == shape
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.18f)
                                                else Color.Transparent
                                            )
                                            .springBounceClick {
                                                performHaptic()
                                                onShapeSelect(shape)
                                                showShapesPopover = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                        NexusText(
                                            text = shape.label,
                                            style = NexusTheme.typography.caption,
                                            color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                                performHaptic()
                                                onShapeSelect(shape)
                                                showShapesPopover = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
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
                                            modifier = Modifier.size(18.dp)
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
                }
            }

            val eraserPopoverContent: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = showEraserPopover && activeTool == AnnotationTool.Eraser,
                    enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 350f)) + fadeIn(tween(180)),
                    exit = scaleOut(spring(dampingRatio = 0.8f, stiffness = 350f)) + fadeOut(tween(180))
                ) {
                    Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                        PdfAnnotationEraserOptionsPopover(
                            selectedEraserFilter = selectedEraserFilter,
                            onEraserFilterSelect = {
                                performHaptic()
                                onEraserFilterSelect(it)
                                showEraserPopover = false
                            }
                        )
                    }
                }
            }

            val colorPopoverContent: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = showColorControls,
                    enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 350f)) + fadeIn(tween(180)),
                    exit = scaleOut(spring(dampingRatio = 0.8f, stiffness = 350f)) + fadeOut(tween(180))
                ) {
                    Box(modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }) {
                        PdfAnnotationColorVerticalPopover(
                            selectedColor = selectedColor,
                            onColorSelect = {
                                performHaptic()
                                onColorSelect(it)
                            },
                            strokeWidth = strokeWidth,
                            onStrokeWidthSelect = {
                                performHaptic()
                                onStrokeWidthSelect(it)
                            }
                        )
                    }
                }
            }

            val stampsPopoverContent: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = showStampsPopover && activeTool == AnnotationTool.Stamp,
                    enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = 350f)) + fadeIn(tween(180)),
                    exit = scaleOut(spring(dampingRatio = 0.8f, stiffness = 350f)) + fadeOut(tween(180))
                ) {
                    PdfAnnotationStampsPopover(
                        selectedStamp = selectedStamp,
                        onStampSelect = { stamp ->
                            performHaptic()
                            onStampSelect(stamp)
                            showStampsPopover = false
                        },
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { } }
                    )
                }
            }

            // ── Floating Anchored Container depending on Dock Position ──
            val mainPillContent: @Composable () -> Unit = {
                NexusSurface(
                    shape = NexusTheme.shapes.pill,
                    elevation = 8.dp,
                    color = Color.Transparent,
                    modifier = Modifier
                        .clip(NexusTheme.shapes.pill)
                        .glassBackground(blurRadius = 30f, alpha = 0.88f, fallbackColor = NexusTheme.colors.surfaceVariant)
                        .border(1.dp, NexusTheme.colors.primary.copy(alpha = 0.45f), NexusTheme.shapes.pill)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(if (isHorizontalRibbon) PaddingValues(horizontal = 8.dp, vertical = 5.dp) else PaddingValues(vertical = 6.dp, horizontal = 5.dp))
                ) {
                    if (isHorizontalRibbon) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dragHandleContent()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                toolButtonsContent()
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dragHandleContent()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .heightIn(max = maxPillHeight)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                toolButtonsContent()
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.offset {
                    IntOffset(liveDragOffset.x.roundToInt(), liveDragOffset.y.roundToInt())
                },
                contentAlignment = when (dockPosition) {
                    ToolbarDockPosition.Left -> Alignment.CenterStart
                    ToolbarDockPosition.Right -> Alignment.CenterEnd
                    ToolbarDockPosition.Top -> Alignment.TopCenter
                    ToolbarDockPosition.Bottom -> Alignment.BottomCenter
                }
            ) {
                when (dockPosition) {
                    ToolbarDockPosition.Left -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mainPillContent()
                            shapesPopoverContent()
                            stampsPopoverContent()
                            eraserPopoverContent()
                            colorPopoverContent()
                        }
                    }
                    ToolbarDockPosition.Right -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shapesPopoverContent()
                            stampsPopoverContent()
                            eraserPopoverContent()
                            colorPopoverContent()
                            mainPillContent()
                        }
                    }
                    ToolbarDockPosition.Top -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mainPillContent()
                            shapesPopoverContent()
                            stampsPopoverContent()
                            eraserPopoverContent()
                            colorPopoverContent()
                        }
                    }
                    ToolbarDockPosition.Bottom -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shapesPopoverContent()
                            stampsPopoverContent()
                            eraserPopoverContent()
                            colorPopoverContent()
                            mainPillContent()
                        }
                    }
                }
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
    val primaryColor = NexusTheme.colors.primary
    val targetIconTint = if (isSelected) Color.White else NexusTheme.colors.textPrimary

    val animatedIconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = tween(durationMillis = 200),
        label = "toolIconTint"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 450f),
        label = "toolScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .scale(animatedScale)
            .clip(CircleShape)
            .background(
                if (isSelected) Brush.linearGradient(listOf(primaryColor, primaryColor.copy(alpha = 0.85f)))
                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                brush = if (isSelected) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.3f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
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
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun ColorIconButton(
    isSelected: Boolean,
    currentColor: Color,
    onClick: () -> Unit,
    isHighlighter: Boolean = false
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 420f),
        label = "colorScale"
    )

    val displayColor = if (isHighlighter) currentColor.copy(alpha = 0.55f) else currentColor

    // Dynamic contrast luminance calculation
    val luminance = (currentColor.red * 0.299f + currentColor.green * 0.587f + currentColor.blue * 0.114f)
    val iconTint = if (luminance < 0.55f) Color.White else Color(0xFF1E1E1E)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .scale(animatedScale)
            .clip(CircleShape)
            .background(
                if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.30f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                brush = if (isSelected) Brush.sweepGradient(listOf(NexusTheme.colors.primary, Color.White, NexusTheme.colors.primary)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
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
                .background(displayColor)
                .border(
                    width = 1.5.dp,
                    color = if (isHighlighter) NexusTheme.colors.primary.copy(alpha = 0.8f) else Color.White,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = rememberPaletteIcon(isFilled = isSelected),
                contentDescription = "Colors",
                tint = iconTint,
                modifier = Modifier.size(15.dp)
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
    val configuration = LocalConfiguration.current
    val maxPopoverHeight = (configuration.screenHeightDp * 0.55f).toInt().dp

    NexusSurface(
        shape = NexusTheme.shapes.medium,
        elevation = 10.dp,
        color = Color.Transparent,
        modifier = modifier
            .clip(NexusTheme.shapes.medium)
            .glassBackground(blurRadius = 35f, alpha = 0.94f, fallbackColor = NexusTheme.colors.surfaceVariant)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        NexusTheme.colors.primary.copy(alpha = 0.7f),
                        NexusTheme.colors.divider.copy(alpha = 0.3f)
                    )
                ),
                NexusTheme.shapes.medium
            )
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = maxPopoverHeight)
        ) {
            // Live Stroke Preview Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = selectedColor,
                        start = Offset(4f, size.height / 2f),
                        end = Offset(size.width - 4f, size.height / 2f),
                        strokeWidth = (strokeWidth * 1.5f).dp.toPx().coerceIn(2f, 22f),
                        cap = StrokeCap.Round
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Vertical Column of Color Swatches
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEach { color ->
                        val isSelected = selectedColor == color
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.22f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                            label = "swatchScale"
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.8.dp,
                                    color = if (isSelected) NexusTheme.colors.primary else Color.Gray.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .springBounceClick { onColorSelect(color) }
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (color == Color(0xFF000000)) Color.White else Color.Black.copy(alpha = 0.7f))
                                )
                            }
                        }
                    }
                }

                // Dynamic Height Divider line
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(210.dp)
                        .background(NexusTheme.colors.divider.copy(alpha = 0.6f))
                )

                // Vertical Column of Stroke Width Options
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    strokeWidths.forEach { width ->
                        val isSelected = strokeWidth == width
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.20f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                            label = "strokeScale"
                        )
                        val dotSize = (width.coerceIn(2f, 12f) * 1.35f).coerceIn(5f, 18f).dp

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 1.8.dp else 0.dp,
                                    color = if (isSelected) NexusTheme.colors.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .springBounceClick { onStrokeWidthSelect(width) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textSecondary)
                            )
                        }
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

@Composable
fun rememberEraseAllIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "eraseAll",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M4 7h16M10 11v6M14 11v6M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun PdfAnnotationEraserOptionsPopover(
    selectedEraserFilter: EraserTargetFilter,
    onEraserFilterSelect: (EraserTargetFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxPopoverHeight = (configuration.screenHeightDp * 0.55f).toInt().dp

    NexusSurface(
        shape = NexusTheme.shapes.medium,
        elevation = 8.dp,
        color = Color.Transparent,
        modifier = modifier
            .widthIn(min = 160.dp, max = 190.dp)
            .clip(NexusTheme.shapes.medium)
            .glassBackground(blurRadius = 35f, alpha = 0.92f, fallbackColor = NexusTheme.colors.surfaceVariant)
            .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), NexusTheme.shapes.medium)
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .heightIn(max = maxPopoverHeight)
                .verticalScroll(rememberScrollState())
        ) {
            NexusText(
                text = "ERASE TARGET",
                style = NexusTheme.typography.caption.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                color = NexusTheme.colors.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            EraserTargetFilter.values().forEach { filter ->
                val isSelected = selectedEraserFilter == filter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) NexusTheme.colors.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .springBounceClick { onEraserFilterSelect(filter) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = when (filter) {
                            EraserTargetFilter.All -> rememberEraseAllIcon(isFilled = isSelected)
                            EraserTargetFilter.PenOnly -> rememberPenIcon(isFilled = isSelected)
                            EraserTargetFilter.HighlighterOnly -> rememberHighlighterIcon(isFilled = isSelected)
                            EraserTargetFilter.ShapesOnly -> rememberShapesIcon(isFilled = isSelected)
                        },
                        contentDescription = filter.label,
                        tint = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    NexusText(
                        text = filter.label,
                        style = NexusTheme.typography.caption.copy(
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                        ),
                        color = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun rememberDragHandleIcon(): ImageVector = remember {
    ImageVector.Builder(
        name = "dragHandle",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        addPath(
            pathData = PathParser().parsePathString("M9 6h6M9 12h6M9 18h6").toNodes(),
            stroke = stroke, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round
        )
    }.build()
}

@Composable
fun rememberTextIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "text",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        addPath(
            pathData = PathParser().parsePathString("M4 7V4h16v3M12 4v16M9 20h6").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.4f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberStampIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "stamp",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        val fill = if (isFilled) SolidColor(Color.Black) else null
        addPath(
            pathData = PathParser().parsePathString("M4 17h16M4 21h16M9 17V8a3 3 0 0 1 6 0v9").toNodes(),
            fill = fill, stroke = stroke, strokeLineWidth = if (isFilled) 2.2f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun rememberRulerIcon(isFilled: Boolean = false): ImageVector = remember(isFilled) {
    ImageVector.Builder(
        name = "ruler",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        val stroke = SolidColor(Color.Black)
        addPath(
            pathData = PathParser().parsePathString("M4 19L19 4M7 16l2-2M10 13l2-2M13 10l2-2M16 7l2-2").toNodes(),
            stroke = stroke, strokeLineWidth = if (isFilled) 2.4f else 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

@Composable
fun PdfAnnotationStampsPopover(
    selectedStamp: StampType,
    onStampSelect: (StampType) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxPopoverHeight = (configuration.screenHeightDp * 0.55f).toInt().dp

    NexusSurface(
        shape = NexusTheme.shapes.medium,
        elevation = 8.dp,
        color = Color.Transparent,
        modifier = modifier
            .widthIn(min = 175.dp, max = 205.dp)
            .clip(NexusTheme.shapes.medium)
            .glassBackground(blurRadius = 35f, alpha = 0.92f, fallbackColor = NexusTheme.colors.surfaceVariant)
            .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), NexusTheme.shapes.medium)
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .heightIn(max = maxPopoverHeight)
                .verticalScroll(rememberScrollState())
        ) {
            NexusText(
                text = "BUSINESS STAMPS",
                style = NexusTheme.typography.caption.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                color = NexusTheme.colors.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            StampType.values().forEach { stamp ->
                val isSelected = selectedStamp == stamp
                val badgeColor = Color(stamp.colorHex)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) badgeColor.copy(alpha = 0.20f)
                            else Color.Transparent
                        )
                        .springBounceClick { onStampSelect(stamp) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    NexusText(
                        text = stamp.label,
                        style = NexusTheme.typography.caption.copy(
                            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        color = if (isSelected) badgeColor else NexusTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

