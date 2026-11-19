package com.nexus.core.ui.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.EmphasizedDecelerateEasing
import com.nexus.core.ui.animations.entrySpring
import com.nexus.core.ui.components.NexusNavItem
import com.nexus.core.ui.liquidglass.backdrops.emptyBackdrop
import com.nexus.core.ui.liquidglass.backdrops.layerBackdrop
import com.nexus.core.ui.liquidglass.backdrops.rememberCombinedBackdrop
import com.nexus.core.ui.liquidglass.backdrops.rememberLayerBackdrop
import com.nexus.core.ui.liquidglass.effects.blur
import com.nexus.core.ui.liquidglass.effects.lens
import com.nexus.core.ui.liquidglass.effects.vibrancy
import com.nexus.core.ui.liquidglass.highlight.Highlight
import com.nexus.core.ui.liquidglass.shadow.InnerShadow
import com.nexus.core.ui.liquidglass.shadow.Shadow
import com.nexus.core.ui.liquidglass.utils.DampedDragAnimation
import com.nexus.core.ui.liquidglass.utils.InteractiveHighlight
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

/**
 * Native Liquid Glass Bottom Navigation Bar with Apple VisionOS / iOS style physical optics.
 *
 * Capabilities:
 * - Real optical refraction (AGSL SDF Lens) on Android 13+ (API 33+)
 * - Chromatic aberration dispersion (rainbow edges on indicator slide)
 * - Hardware Gaussian blur and vibrancy boost on Android 12+ (API 31+)
 * - Graceful clean translucent fallback on Android 11 and below (API 26-30)
 * - Spring-damped gesture tracking and squish deformation physics
 * - Full accessibility and tactile haptic feedback
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NexusLiquidBottomNav(
    items: List<NexusNavItem>,
    currentRoute: String?,
    onItemSelected: (NexusNavItem) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isCollapsed: Boolean = false,
    onScrollToTop: (() -> Unit)? = null,
    visible: Boolean = true,
    cornerRadius: Dp = 28.dp,
    blurRadius: Dp = 10.dp,
    refractionHeight: Dp = 20.dp,
    refractionAmount: Dp = 22.dp,
) {
    if (items.isEmpty()) return

    val resolvedBackdrop = backdrop ?: remember { emptyBackdrop() }
    val isLightTheme = !isSystemInDarkTheme()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    val tabsCount = items.size
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val primaryColor = NexusTheme.colors.primary

    // Adaptive container colors
    val containerGlassColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = if (isRenderEffectSupported()) 0.45f else 0.88f)
    } else {
        Color(0xFF141416).copy(alpha = if (isRenderEffectSupported()) 0.40f else 0.88f)
    }

    val tabsBackdrop = rememberLayerBackdrop()

    val containerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = entrySpring(),
        label = "liquidNavAlpha"
    )

    // Dynamic width & constant height:
    // When scrolling (isCollapsed == true), the WIDTH shrinks to a compact icon-only pill,
    // while the HEIGHT stays rock-solid at 62.dp.
    val morphSpring = remember { spring<Dp>(dampingRatio = 0.86f, stiffness = 380f) }
    val navBarsBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val targetBottomOffset = (if (isCollapsed) 10.dp else 16.dp) + navBarsBottom
    val animBottomOffset by animateDpAsState(
        targetValue = targetBottomOffset,
        animationSpec = morphSpring,
        label = "liquidNavBottomOffset"
    )

    val capsuleHeight = 62.dp
    val targetTabWidth = if (isCollapsed) 52.dp else 76.dp
    val animTabWidth by animateDpAsState(
        targetValue = targetTabWidth,
        animationSpec = morphSpring,
        label = "liquidNavTabWidth"
    )

    val capsulePadding = 4.dp
    val totalCapsuleWidth = animTabWidth * tabsCount + (capsulePadding * 2)

    val navShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val indicatorShape = remember(cornerRadius, capsulePadding) {
        RoundedCornerShape((cornerRadius - capsulePadding).coerceAtLeast(4.dp))
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }

    val offsetAnimation = remember { Animatable(0f) }

    // Damped drag animation with tap tracking
    val dampedDragAnimation = remember(animationScope, tabsCount) {
        var dragDistance = 0f
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.25f,
            onDragStarted = {
                dragDistance = 0f
            },
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
                if (dragDistance < 10f) {
                    // Tap on active indicator
                    val item = items.getOrNull(targetIndex)
                    if (item != null) {
                        if (targetIndex == 0 && item.route == currentRoute) {
                            onScrollToTop?.invoke()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onItemSelected(item)
                        }
                    }
                } else {
                    // Drag ended on targetIndex
                    val item = items.getOrNull(targetIndex)
                    if (item != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onItemSelected(item)
                    }
                }
            },
            onDrag = { _, dragAmount ->
                dragDistance += abs(dragAmount.x)
                val currentTabWidthPx = with(density) { animTabWidth.toPx() }.coerceAtLeast(1f)
                updateValue(
                    (targetValue + dragAmount.x / currentTabWidthPx * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                )
                animationScope.launch {
                    offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                }
            }
        )
    }

    // Sync external route changes
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in 0 until tabsCount) {
            currentIndex = selectedIndex
            dampedDragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    // Tab click handler
    val handleTabClick: (Int) -> Unit = { index ->
        val item = items.getOrNull(index)
        if (item != null) {
            if (index == currentIndex && item.route == currentRoute) {
                if (index == 0) {
                    onScrollToTop?.invoke()
                }
            } else {
                currentIndex = index
                dampedDragAnimation.animateToValue(index.toFloat())
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onItemSelected(item)
            }
        }
    }

    // Synchronous active index determination:
    // If the indicator is animating toward a target index, immediately highlight that target!
    // If idle or dragging, highlight the closest tab to the indicator's continuous position.
    val activeIndex = when {
        dampedDragAnimation.targetValue.isFinite() &&
                abs(dampedDragAnimation.targetValue - dampedDragAnimation.value) > 0.05f -> {
            dampedDragAnimation.targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
        }
        else -> {
            dampedDragAnimation.value.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
        }
    }

    val panelOffset by remember(density) {
        derivedStateOf {
            val totalWidthPx = with(density) { totalCapsuleWidth.toPx() }.coerceAtLeast(1f)
            val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
            with(density) {
                4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                val tabW = with(density) { animTabWidth.toPx() }
                val pad = with(density) { capsulePadding.toPx() }
                val x = if (isLtr) {
                    pad + (dampedDragAnimation.value + 0.5f) * tabW + panelOffset
                } else {
                    size.width - pad - (dampedDragAnimation.value + 0.5f) * tabW + panelOffset
                }
                Offset(x, size.height / 2f)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = animBottomOffset)
            .graphicsLayer { alpha = containerAlpha },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(totalCapsuleWidth)
                .height(capsuleHeight)
                .semantics { selectableGroup() },
            contentAlignment = Alignment.CenterStart
        ) {

            // ── LAYER 2: Ghost Row for sampling Tab Items in Refraction ─────────
            // MUST be non-interactive (no click / pointer consumption) so touch events
            // pass straight through to Layer 1.
            CompositionLocalProvider(
                LocalLiquidTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                }
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .drawBackdrop(
                            backdrop = resolvedBackdrop,
                            shape = { navShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur((blurRadius * 0.8f).toPx())
                                lens(
                                    refractionHeight.toPx() * progress,
                                    refractionAmount.toPx() * progress
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            onDrawSurface = { drawRect(containerGlassColor) }
                        )
                        .height(capsuleHeight - (capsulePadding * 2))
                        .width(totalCapsuleWidth)
                        .padding(horizontal = capsulePadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        LiquidTabItem(
                            item = item,
                            isActive = (index == activeIndex),
                            isCollapsed = isCollapsed,
                            modifier = Modifier.width(animTabWidth),
                            interactive = false
                        )
                    }
                }
            }

            // ── LAYER 1: Base Outer Liquid Capsule Dock & Interactive Items ──────
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .shadow(
                        elevation = if (isSystemInDarkTheme()) 18.dp else 12.dp,
                        shape = navShape,
                        spotColor = primaryColor.copy(alpha = 0.22f),
                        ambientColor = Color.Black.copy(alpha = 0.15f)
                    )
                    .drawBackdrop(
                        backdrop = resolvedBackdrop,
                        shape = { navShape },
                        effects = {
                            vibrancy()
                            blur(blurRadius.toPx())
                            lens(refractionHeight.toPx(), refractionAmount.toPx())
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = if (isLightTheme) 0.6f else 0.4f)
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 12f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = {
                            drawRect(containerGlassColor)
                        }
                    )
                    .then(
                        if (!isRenderEffectSupported()) {
                            Modifier.border(0.5.dp, NexusTheme.colors.divider.copy(alpha = 0.25f), navShape)
                        } else {
                            Modifier
                        }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(capsuleHeight)
                    .width(totalCapsuleWidth)
                    .padding(capsulePadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isItemActive = (index == activeIndex)
                    LiquidTabItem(
                        item = item,
                        isActive = isItemActive,
                        isCollapsed = isCollapsed,
                        modifier = Modifier.width(animTabWidth),
                        interactive = true,
                        onClick = {
                            handleTabClick(index)
                        },
                        onLongClick = if (index == 0) onScrollToTop else null
                    )
                }
            }

            // ── LAYER 3: Liquid Glass Sliding Active Indicator ──────────────────
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        val currentTabWidthPx = animTabWidth.toPx()
                        val padPx = capsulePadding.toPx()
                        val dragVal = dampedDragAnimation.value
                        val totalW = totalCapsuleWidth.toPx()
                        translationX = if (isLtr) {
                            padPx + dragVal * currentTabWidthPx + panelOffset
                        } else {
                            totalW - padPx - (dragVal + 1f) * currentTabWidthPx + panelOffset
                        }
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(resolvedBackdrop, tabsBackdrop),
                        shape = { indicatorShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                refractionHeight = lerp(refractionHeight.toPx() * 0.4f, refractionHeight.toPx() * 0.6f, progress),
                                refractionAmount = lerp(refractionAmount.toPx() * 0.5f, refractionAmount.toPx() * 0.75f, progress),
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(
                                width = 0.75.dp,
                                alpha = lerp(0.55f, 0.85f, dampedDragAnimation.pressProgress)
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 10.dp,
                                color = primaryColor.copy(alpha = 0.22f),
                                alpha = 0.9f
                            )
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 6.dp * (1f + progress * 0.5f),
                                alpha = lerp(0.20f, 0.40f, progress)
                            )
                        },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 12f
                            scaleX /= 1f - (velocity * 0.70f).fastCoerceIn(-0.25f, 0.25f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.25f, 0.25f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            val baseFill = primaryColor.copy(alpha = lerp(0.12f, 0.22f, progress))
                            drawRect(baseFill)
                        }
                    )
                    .then(
                        if (!isRenderEffectSupported()) {
                            Modifier
                                .background(primaryColor.copy(alpha = 0.16f), indicatorShape)
                                .border(1.dp, primaryColor.copy(alpha = 0.28f), indicatorShape)
                        } else {
                            Modifier
                        }
                    )
                    .height(capsuleHeight - (capsulePadding * 2))
                    .width(animTabWidth)
            )
        }
    }
}

/**
 * Individual Tab Item with clean vector icons, animated label, and badge.
 *
 * Supports both interactive mode (clickable with semantics in Layer 1) and
 * non-interactive mode (pure visual rendering in Layer 2 for refraction capture).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiquidTabItem(
    item: NexusNavItem,
    isActive: Boolean,
    isCollapsed: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tabScale = LocalLiquidTabScale.current

    val iconTint by animateColorAsState(
        targetValue = if (isActive) NexusTheme.colors.primary else NexusTheme.colors.textSecondary,
        animationSpec = tween(180, easing = EmphasizedDecelerateEasing),
        label = "liquidTabTint"
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else if (isActive) 1f else 0.75f,
        animationSpec = tween(if (isCollapsed) 140 else 200),
        label = "liquidTabLabelAlpha"
    )

    val labelHeight by animateDpAsState(
        targetValue = if (isCollapsed || item.label.isEmpty()) 0.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "liquidTabLabelHeight"
    )

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "liquidTabScale"
    )

    val activeAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(180, easing = EmphasizedDecelerateEasing),
        label = "liquidTabActiveAlpha"
    )

    val clickableModifier = if (interactive) {
        Modifier
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                role = Role.Tab
                selected = isActive
                contentDescription = item.label
            }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(clickableModifier)
            .graphicsLayer {
                val s = scale * tabScale()
                scaleX = s
                scaleY = s
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val selectedRes = item.selectedIconRes ?: item.unselectedIconRes
        val unselectedRes = item.unselectedIconRes ?: item.selectedIconRes

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (isCollapsed) 22.dp else 24.dp)
        ) {
            if (selectedRes != null && unselectedRes != null && selectedRes != unselectedRes) {
                // Unselected outline icon
                if (activeAlpha < 1f) {
                    Image(
                        painter = painterResource(id = unselectedRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(if (isCollapsed) 22.dp else 24.dp)
                            .graphicsLayer { alpha = 1f - activeAlpha },
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                }
                // Selected filled icon
                if (activeAlpha > 0f) {
                    Image(
                        painter = painterResource(id = selectedRes),
                        contentDescription = item.label,
                        modifier = Modifier
                            .size(if (isCollapsed) 22.dp else 24.dp)
                            .graphicsLayer { alpha = activeAlpha },
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                }
            } else if (selectedRes != null) {
                Image(
                    painter = painterResource(id = selectedRes),
                    contentDescription = item.label,
                    modifier = Modifier.size(if (isCollapsed) 22.dp else 24.dp),
                    colorFilter = ColorFilter.tint(iconTint)
                )
            }

            if (item.badge > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 8.dp, y = (-4).dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.error),
                    contentAlignment = Alignment.Center
                ) {
                    NexusText(
                        text = if (item.badge > 9) "9+" else item.badge.toString(),
                        color = Color.White,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        if (labelHeight > 0.dp && item.label.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .height(labelHeight)
                    .graphicsLayer { alpha = labelAlpha }
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = item.label,
                    color = iconTint,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
