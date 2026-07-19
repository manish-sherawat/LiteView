package com.nexus.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.preferences.HomeStyle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.*
import com.nexus.core.ui.utils.glassBackground
import com.nexus.core.ui.utils.liquidGlass
import kotlinx.coroutines.launch

// ─── Nav Item Model ───────────────────────────────────────────────────────────

data class NexusNavItem(
    val label: String,
    val selectedIconText: String = "",
    val unselectedIconText: String = "",
    val selectedIconRes: Int? = null,
    val unselectedIconRes: Int? = null,
    val route: String,
    val badge: Int = 0
)

// ─── Main Floating Bottom Nav ─────────────────────────────────────────────────

@Composable
fun NexusFloatingBottomNav(
    items: List<NexusNavItem>,
    currentRoute: String?,
    onItemSelected: (NexusNavItem) -> Unit,
    homeStyle: HomeStyle,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    // Middle item (index 1 of 3) is treated as the center FAB action
    val isCenterFab = items.size == 3

    // ── Per-item layout measurements ──────────────────────────────────────────
    var canvasRootX by remember { mutableStateOf(0f) }
    val itemRootCentersX = remember { mutableStateListOf<Float>() }
    val itemWidths       = remember { mutableStateListOf<Float>() }

    // ── Sliding indicator animatables ─────────────────────────────────────────
    val indicatorX      = remember { Animatable(0f) }
    val indicatorWidth  = remember { Animatable(0f) }
    val indicatorAlpha  = remember { Animatable(0f) }
    val indicatorScaleY = remember { Animatable(1f) }

    // ── Container entry / exit ────────────────────────────────────────────────
    val containerAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = entrySpring(),
        label         = "navContainerAlpha"
    )
    val shadowElevation by animateDpAsState(
        targetValue   = if (visible) 24.dp else 0.dp,
        animationSpec = navPillSpring(),
        label         = "navShadow"
    )

    // ── Drive indicator spring physics on tab change ──────────────────────────
    LaunchedEffect(selectedIndex, itemRootCentersX.size, itemWidths.size, canvasRootX) {
        if (itemRootCentersX.size <= selectedIndex) return@LaunchedEffect
        if (itemWidths.size <= selectedIndex) return@LaunchedEffect
        // Never draw the indicator beneath the FAB slot
        if (isCenterFab && selectedIndex == 1) return@LaunchedEffect

        val targetCenterX = itemRootCentersX[selectedIndex] - canvasRootX
        val targetW       = itemWidths[selectedIndex]
        val targetLeft    = targetCenterX - targetW / 2f

        if (indicatorAlpha.value < 0.05f) {
            // First frame — snap then fade in
            indicatorX.snapTo(targetLeft)
            indicatorWidth.snapTo(targetW)
            launch { indicatorAlpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
            return@LaunchedEffect
        }

        // Phase 1: vertical squish (rubber-band)
        launch { indicatorScaleY.animateTo(0.82f, spring(dampingRatio = 0.50f, stiffness = 520f)) }

        // Phase 2: slide to target
        launch {
            kotlinx.coroutines.delay(40)
            indicatorX.animateTo(targetLeft, spring(dampingRatio = 0.68f, stiffness = 320f))
        }
        launch {
            indicatorWidth.animateTo(targetW, spring(dampingRatio = 0.72f, stiffness = 280f))
        }

        // Phase 3: arrival splat
        kotlinx.coroutines.delay(220)
        launch { indicatorScaleY.animateTo(1.18f, spring(dampingRatio = 0.40f, stiffness = 260f)) }
        kotlinx.coroutines.delay(150)
        launch { indicatorScaleY.animateTo(1f,    spring(dampingRatio = 0.58f, stiffness = 300f)) }
    }

    val pillShape = NexusTheme.shapes.pill
    val isDark    = androidx.compose.foundation.isSystemInDarkTheme()

    // ── Indicator colors ──────────────────────────────────────────────────────
    val indicatorFill   = NexusTheme.colors.primary.copy(alpha = if (isDark) 0.16f else 0.10f)
    val indicatorStroke = NexusTheme.colors.primary.copy(alpha = if (isDark) 0.30f else 0.20f)

    // ── Glowing border: pulsing alpha driven by infinite transition ───────────
    val glowTransition = rememberInfiniteTransition(label = "navGlow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue  = 0.45f,
        targetValue   = 0.90f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2400, easing = com.nexus.core.ui.animations.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navGlowAlpha"
    )
    val primaryColor = NexusTheme.colors.primary

    // ── Pill glass background ─────────────────────────────────────────────────
    val glassAlpha = if (isDark) 0.42f else 0.60f
    val glassTint  = NexusTheme.colors.surfaceVariant

    // The glow border modifier is applied AFTER clip so it paints over the
    // clipped surface edge — using drawWithContent so the border sits on top.
    val glowBorderModifier = Modifier.drawWithContent {
        drawContent()
        val strokePx = 1.2.dp.toPx()
        val inset    = strokePx / 2f
        val r        = size.height / 2f            // pill corner radius
        val brush    = Brush.linearGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0f),
                primaryColor.copy(alpha = glowAlpha),
                primaryColor.copy(alpha = glowAlpha * 0.6f),
                primaryColor.copy(alpha = glowAlpha),
                primaryColor.copy(alpha = 0f)
            ),
            start = Offset(0f, size.height / 2f),
            end   = Offset(size.width, size.height / 2f)
        )
        drawRoundRect(
            brush        = brush,
            topLeft      = Offset(inset, inset),
            size         = Size(size.width - strokePx, size.height - strokePx),
            cornerRadius = CornerRadius(r, r),
            style        = Stroke(width = strokePx)
        )
    }

    val backgroundModifier = if (homeStyle == HomeStyle.APPLE_GLASSMORPHIC) {
        Modifier
            .glassBackground(fallbackColor = glassTint, alpha = glassAlpha, shape = pillShape)
            .then(Modifier.liquidGlass())
    } else {
        Modifier
            .background(glassTint.copy(alpha = 0.94f), pillShape)
            .border(0.8.dp, NexusTheme.colors.divider.copy(alpha = 0.35f), pillShape)
    }

    // ── Bottom safe-area offset ───────────────────────────────────────────────
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomOffset = 20.dp + with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }.coerceAtMost(28.dp)

    // ── Pill ambient shadow color ─────────────────────────────────────────────
    val ambientShadow = if (isDark)
        Color.Black.copy(alpha = 0.55f)
    else
        NexusTheme.colors.primary.copy(alpha = 0.06f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(top = 12.dp, bottom = bottomOffset)
            .graphicsLayer { alpha = containerAlpha },
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {

            // ── Pill shell ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation    = shadowElevation,
                        shape        = pillShape,
                        clip         = false,
                        spotColor    = Color.Black.copy(alpha = 0.22f),
                        ambientColor = ambientShadow
                    )
                    .clip(pillShape)
                    .then(backgroundModifier)
            )

            // ── Glow border overlay (separate layer so it never enters the
            //    liquidGlass RenderEffect pipeline which would corrupt blur) ─────
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val strokePx = 1.4.dp.toPx()
                        val inset    = strokePx / 2f
                        val r        = size.height / 2f
                        val brush    = Brush.linearGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0f),
                                primaryColor.copy(alpha = glowAlpha * 0.70f),
                                primaryColor.copy(alpha = glowAlpha),
                                primaryColor.copy(alpha = glowAlpha * 0.70f),
                                primaryColor.copy(alpha = 0f)
                            ),
                            start = Offset(size.width * 0.05f, size.height / 2f),
                            end   = Offset(size.width * 0.95f, size.height / 2f)
                        )
                        drawRoundRect(
                            brush        = brush,
                            topLeft      = Offset(inset, inset),
                            size         = Size(size.width - strokePx, size.height - strokePx),
                            cornerRadius = CornerRadius(r, r),
                            style        = Stroke(width = strokePx)
                        )
                    }
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { coords ->
                        canvasRootX = coords.positionInRoot().x
                    }
                    .graphicsLayer { alpha = indicatorAlpha.value }
                    .drawBehind {
                        if (indicatorWidth.value <= 0f) return@drawBehind

                        val indicatorH = 46.dp.toPx()
                        val centerY    = size.height / 2f
                        val radius     = indicatorH / 2f
                        val scaleY     = indicatorScaleY.value
                        val scaledH    = indicatorH * scaleY
                        val top        = centerY - scaledH / 2f
                        val iW         = indicatorWidth.value
                        val iX         = indicatorX.value

                        // Filled pill
                        drawRoundRect(
                            color        = indicatorFill,
                            topLeft      = Offset(iX, top),
                            size         = Size(iW, scaledH),
                            cornerRadius = CornerRadius(radius, radius)
                        )
                        // Stroke ring
                        drawRoundRect(
                            color        = indicatorStroke,
                            topLeft      = Offset(iX + 0.5f, top + 0.5f),
                            size         = Size(iW - 1f, scaledH - 1f),
                            cornerRadius = CornerRadius(radius, radius),
                            style        = Stroke(width = 1.dp.toPx())
                        )
                    }
            )

            // ── Icons row ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isCenter = isCenterFab && index == 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coords ->
                                val rootCenterX = coords.positionInRoot().x + coords.size.width / 2f
                                val w           = coords.size.width.toFloat()
                                if (itemRootCentersX.size <= index) {
                                    itemRootCentersX.add(rootCenterX)
                                } else {
                                    itemRootCentersX[index] = rootCenterX
                                }
                                if (itemWidths.size <= index) {
                                    itemWidths.add(w)
                                } else {
                                    itemWidths[index] = w
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCenter) {
                            NexusCenterFabIcon(item = item, onClick = { onItemSelected(item) })
                        } else {
                            NexusNavIconWithLabel(
                                item       = item,
                                isSelected = currentRoute == item.route,
                                onClick    = { onItemSelected(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Regular Nav Icon + Animated Label ───────────────────────────────────────

@Composable
private fun NexusNavIconWithLabel(
    item: NexusNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconScale        = remember { Animatable(if (isSelected) 1.08f else 1f) }
    val iconTranslationY = remember { Animatable(0f) }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            launch {
                iconTranslationY.animateTo(-6f, spring(dampingRatio = 0.38f, stiffness = 400f))
                iconTranslationY.animateTo(0f,  spring(dampingRatio = 0.60f, stiffness = 280f))
            }
            launch {
                iconScale.animateTo(1.22f, spring(dampingRatio = 0.38f, stiffness = 380f))
                iconScale.animateTo(1.08f, spring(dampingRatio = 0.65f, stiffness = 300f))
            }
        } else {
            launch { iconScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 320f)) }
            launch { iconTranslationY.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 300f)) }
        }
    }

    val iconTint by animateColorAsState(
        targetValue   = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textSecondary,
        animationSpec = tween(durationMillis = DurationScreenEnter, easing = EmphasizedDecelerateEasing),
        label         = "iconTint"
    )

    val labelAlpha by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0.70f,
        animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerateEasing),
        label         = "labelAlpha"
    )
    val labelScale by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label         = "labelScale"
    )
    // Icon drifts slightly upward to balance with the label below
    val iconOffsetY by animateFloatAsState(
        targetValue   = if (isSelected) -1.5f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 340f),
        label         = "iconOffsetY"
    )

    Column(
        modifier            = Modifier
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .springBounceClick(enabled = true, scaleDown = 0.88f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon (with optional badge)
        Box(contentAlignment = Alignment.TopEnd) {
            NavIcon(
                item         = item,
                isSelected   = isSelected,
                tint         = iconTint,
                scale        = iconScale.value,
                translationY = iconTranslationY.value + iconOffsetY
            )
            if (item.badge > 0) {
                BadgeDot(count = item.badge)
            }
        }

        // Animated label
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha  = labelAlpha
                    scaleX = labelScale
                    scaleY = labelScale
                }
                .height(13.dp),
            contentAlignment = Alignment.Center
        ) {
            NexusText(
                text  = item.label,
                color = iconTint,
                style = NexusTheme.typography.caption.copy(
                    fontSize      = 9.5.sp,
                    fontWeight    = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.3.sp
                ),
                maxLines = 1
            )
        }
    }
}

// ─── Shared icon renderer ─────────────────────────────────────────────────────

@Composable
private fun NavIcon(
    item: NexusNavItem,
    isSelected: Boolean,
    tint: Color,
    scale: Float,
    translationY: Float
) {
    val iconModifier = Modifier
        .size(22.dp)
        .graphicsLayer {
            scaleX            = scale
            scaleY            = scale
            this.translationY = translationY
        }

    when {
        isSelected && item.selectedIconRes != null -> Image(
            painter            = painterResource(id = item.selectedIconRes),
            contentDescription = item.label,
            modifier           = iconModifier,
            colorFilter        = ColorFilter.tint(tint)
        )
        !isSelected && item.unselectedIconRes != null -> Image(
            painter            = painterResource(id = item.unselectedIconRes),
            contentDescription = item.label,
            modifier           = iconModifier,
            colorFilter        = ColorFilter.tint(tint)
        )
        else -> NexusText(
            text     = if (isSelected) item.selectedIconText else item.unselectedIconText,
            color    = tint,
            style    = NexusTheme.typography.h2,
            modifier = Modifier.graphicsLayer {
                scaleX            = scale
                scaleY            = scale
                this.translationY = translationY
            }
        )
    }
}

// ─── Center FAB ───────────────────────────────────────────────────────────────

@Composable
private fun NexusCenterFabIcon(
    item: NexusNavItem,
    onClick: () -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Breathing glow ring
    val pulseTransition = rememberInfiniteTransition(label = "fabPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue  = 0.55f,
        targetValue   = 0.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = com.nexus.core.ui.animations.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabPulseAlpha"
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue  = 1.00f,
        targetValue   = 1.38f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = com.nexus.core.ui.animations.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabPulseScale"
    )

    val fabBg     = NexusTheme.colors.primary
    val fabFg     = NexusTheme.colors.onPrimary
    val glowColor = NexusTheme.colors.primary

    Box(
        modifier         = Modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing radial glow behind button
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha  = pulseAlpha
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.50f),
                            glowColor.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        radius = 90f
                    ),
                    shape = CircleShape
                )
        )

        // FAB button
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation    = 12.dp,
                    shape        = CircleShape,
                    spotColor    = glowColor.copy(alpha = if (isDark) 0.65f else 0.35f),
                    ambientColor = glowColor.copy(alpha = if (isDark) 0.30f else 0.14f)
                )
                .clip(CircleShape)
                .background(fabBg)
                .springBounceClick(enabled = true, scaleDown = 0.84f, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (item.selectedIconRes != null) {
                Image(
                    painter            = painterResource(id = item.selectedIconRes),
                    contentDescription = item.label,
                    modifier           = Modifier.size(20.dp),
                    colorFilter        = ColorFilter.tint(fabFg)
                )
            } else {
                NexusText(
                    text  = item.selectedIconText,
                    color = fabFg,
                    style = NexusTheme.typography.h2
                )
            }
        }
    }
}

// ─── Badge Dot ────────────────────────────────────────────────────────────────

@Composable
private fun BadgeDot(count: Int) {
    Box(
        modifier = Modifier
            .offset(x = 8.dp, y = (-5).dp)
            .size(15.dp)
            .clip(CircleShape)
            .background(NexusTheme.colors.error),
        contentAlignment = Alignment.Center
    ) {
        if (count > 0) {
            NexusText(
                text     = if (count > 9) "9+" else count.toString(),
                color    = Color.White,
                style    = NexusTheme.typography.caption.copy(
                    fontSize   = 7.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

// ─── Normal Bottom Nav (delegates to Floating) ────────────────────────────────

@Composable
fun NexusNormalBottomNav(
    items: List<NexusNavItem>,
    currentRoute: String?,
    onItemSelected: (NexusNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NexusFloatingBottomNav(
        items          = items,
        currentRoute   = currentRoute,
        onItemSelected = onItemSelected,
        homeStyle      = HomeStyle.MINIMAL,
        modifier       = modifier
    )
}