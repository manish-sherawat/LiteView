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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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

    // Root-space positions: items store absolute X, canvas stores its own X origin
    // We subtract canvasRootX from each item's absolute X to get canvas-local position
    var canvasRootX by remember { mutableStateOf(0f) }
    val itemRootCentersX = remember { mutableStateListOf<Float>() }

    // ── Liquid blob animatables ───────────────────────────────────────────────
    val blobLeadingX  = remember { Animatable(0f) }  // fast spring — leads
    val blobTrailingX = remember { Animatable(0f) }  // slow spring — lags = stretch
    val blobScaleY    = remember { Animatable(1f) }  // vertical squish/splat
    val blobScaleX    = remember { Animatable(1f) }  // horizontal squish
    val blobAlpha     = remember { Animatable(0f) }  // fade in on first render

    val containerAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = entrySpring(),
        label         = "navContainerAlpha"
    )
    val shadowElevation by animateDpAsState(
        targetValue   = if (visible) 10.dp else 0.dp,
        animationSpec = navPillSpring(),
        label         = "navShadow"
    )

    // ── Drive blob physics on tab change ─────────────────────────────────────
    // targetX is always in canvas-local space
    LaunchedEffect(selectedIndex, itemRootCentersX.size, canvasRootX) {
        if (itemRootCentersX.size <= selectedIndex) return@LaunchedEffect
        // Convert root-space to canvas-local space
        val targetX = itemRootCentersX[selectedIndex] - canvasRootX

        // First appearance: snap and fade in
        if (blobAlpha.value < 0.1f) {
            blobLeadingX.snapTo(targetX)
            blobTrailingX.snapTo(targetX)
            blobAlpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }

        // ── Phase 1: Squish at source — surface tension release ───────────────
        // Slower stiffness = heavier, more weighted feel
        launch { blobScaleX.animateTo(1.32f, spring(dampingRatio = 0.62f, stiffness = 380f)) }
        launch { blobScaleY.animateTo(0.76f, spring(dampingRatio = 0.62f, stiffness = 380f)) }

        kotlinx.coroutines.delay(75)

        // ── Phase 2: Stretch travel — leading races, trailing lags ────────────
        // Leading is fast, trailing follows with a deliberate lag — but not too slow
        launch {
            blobLeadingX.animateTo(targetX, spring(dampingRatio = 0.72f, stiffness = 280f))
        }
        launch {
            kotlinx.coroutines.delay(40)
            blobTrailingX.animateTo(targetX, spring(dampingRatio = 0.86f, stiffness = 170f))
        }

        // ── Phase 3: Arrival splat — jello settle ────────────────────────────
        // Longer delay = let the blob fully travel before squishing
        kotlinx.coroutines.delay(240)
        launch { blobScaleY.animateTo(1.22f, spring(dampingRatio = 0.35f, stiffness = 200f)) }
        launch { blobScaleX.animateTo(0.85f, spring(dampingRatio = 0.40f, stiffness = 220f)) }
        kotlinx.coroutines.delay(180)
        launch { blobScaleY.animateTo(1f, spring(dampingRatio = 0.52f, stiffness = 180f)) }
        launch { blobScaleX.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 200f)) }
    }

    val containerBg = NexusTheme.colors.surfaceVariant.copy(alpha = 0.92f)
    val pillShape   = NexusTheme.shapes.pill
    val blobColor   = NexusTheme.colors.primary.copy(alpha = 0.15f)

    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomOffset = 16.dp + with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }.coerceAtMost(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 16.dp, bottom = bottomOffset)
            .graphicsLayer { alpha = containerAlpha },
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {

            // ── Bar background ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation = shadowElevation,
                        shape     = pillShape,
                        clip      = false,
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .clip(pillShape)
                    .background(containerBg)
                    .border(
                        width = 0.5.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.4f),
                        shape = pillShape
                    )
            )

            // ── Liquid blob canvas ────────────────────────────────────────────
            // onGloballyPositioned captures the canvas's own root-space X so we
            // can convert item positions into canvas-local coordinates correctly.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { coords ->
                        canvasRootX = coords.positionInRoot().x
                    }
                    .graphicsLayer { alpha = blobAlpha.value }
                    .drawBehind {
                        if (itemRootCentersX.isEmpty()) return@drawBehind

                        val blobH   = 44.dp.toPx()
                        val halfH   = (blobH / 2f) * blobScaleY.value
                        val centerY = size.height / 2f
                        val lx      = blobLeadingX.value
                        val tx      = blobTrailingX.value
                        val minX    = minOf(lx, tx)
                        val maxX    = maxOf(lx, tx)
                        val halfW   = (blobH / 2f) * blobScaleX.value

                        // Clamp max travel so the stretch never gets excessively long
                        val rawTravel = maxX - minX
                        val travel    = rawTravel.coerceAtMost(blobH * 0.75f)

                        // Normalised stretch 0..1
                        val stretch = (travel / (blobH * 0.75f)).coerceIn(0f, 1f)

                        // Blob spans: centre of leading/trailing ± halfW end-cap radius
                        val left  = minX - halfW
                        val right = maxX + halfW

                        if (stretch < 0.05f) {
                            // At rest — perfect circle
                            drawCircle(
                                color  = blobColor,
                                radius = halfW,
                                center = Offset(lx, centerY)
                            )
                        } else {
                            // Stretched — stadium/capsule with proper semicircle end caps.
                            // Uses the Bézier circle approximation factor κ ≈ 0.5523
                            // so the caps ALWAYS stay perfectly round regardless of stretch.
                            val k    = 0.5523f          // bezier circle factor
                            val capR = halfH             // cap radius = always full height/2

                            // Straight-section edges: where the flat top/bottom live
                            // We move left/right in from the cap centres by 0 (the cap
                            // centre IS at minX/maxX — the halfW already accounts for cap).
                            // capCentreLeft  = minX,  capCentreRight = maxX
                            val cL = minX
                            val cR = maxX

                            val path = Path()
                            // Start: top of left cap centre
                            path.moveTo(cL, centerY - capR)
                            // Top straight edge
                            path.lineTo(cR, centerY - capR)
                            // Right end cap — bezier quarter-circle × 2
                            path.cubicTo(
                                cR + k * capR, centerY - capR,
                                cR + capR,     centerY - k * capR,
                                cR + capR,     centerY
                            )
                            path.cubicTo(
                                cR + capR,     centerY + k * capR,
                                cR + k * capR, centerY + capR,
                                cR,            centerY + capR
                            )
                            // Bottom straight edge
                            path.lineTo(cL, centerY + capR)
                            // Left end cap — bezier quarter-circle × 2
                            path.cubicTo(
                                cL - k * capR, centerY + capR,
                                cL - capR,     centerY + k * capR,
                                cL - capR,     centerY
                            )
                            path.cubicTo(
                                cL - capR,     centerY - k * capR,
                                cL - k * capR, centerY - capR,
                                cL,            centerY - capR
                            )
                            path.close()
                            drawPath(path, blobColor)
                        }
                    }
            )

            // ── Icons row ─────────────────────────────────────────────────────
            // Each item captures its root-space center X via positionInRoot()
            // This is the correct coordinate for subtracting canvasRootX later.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { alpha = blobAlpha.value },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            // positionInRoot() gives absolute screen position — 
                            // same coordinate space as canvasRootX above ✓
                            val rootCenterX = coords.positionInRoot().x + coords.size.width / 2f
                            if (itemRootCentersX.size <= index) {
                                itemRootCentersX.add(rootCenterX)
                            } else {
                                itemRootCentersX[index] = rootCenterX
                            }
                        }
                    ) {
                        NexusBottomNavIcon(
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

// ─── Individual Nav Icon with Bounce Physics ──────────────────────────────────

@Composable
private fun NexusBottomNavIcon(
    item: NexusNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconTranslationY = remember { Animatable(0f) }
    val iconScale        = remember { Animatable(if (isSelected) 1.1f else 1f) }

    // Icon bounces UP when selected — staggered to sync with blob arrival
    LaunchedEffect(isSelected) {
        if (isSelected) {
            kotlinx.coroutines.delay(100)  // wait for blob to be mid-travel
            launch {
                // Bounce up
                iconTranslationY.animateTo(-8f, spring(dampingRatio = 0.42f, stiffness = 360f))
                // Settle back with gentle overshoot
                iconTranslationY.animateTo(0f,  spring(dampingRatio = 0.62f, stiffness = 260f))
            }
            launch {
                iconScale.animateTo(1.20f, spring(dampingRatio = 0.45f, stiffness = 340f))
                iconScale.animateTo(1.08f, spring(dampingRatio = 0.68f, stiffness = 280f))
            }
        } else {
            launch { iconScale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 300f)) }
            launch { iconTranslationY.animateTo(0f, spring(dampingRatio = 0.80f, stiffness = 280f)) }
        }
    }

    val iconTint by animateColorAsState(
        targetValue   = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textSecondary,
        animationSpec = tween(durationMillis = DurationMedium3, easing = EmphasizedDecelerateEasing),
        label         = "iconTint"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .springBounceClick(enabled = true, scaleDown = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (item.badge > 0) {
            BadgeDot(count = item.badge)
        }

        val iconModifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX       = iconScale.value
                scaleY       = iconScale.value
                translationY = iconTranslationY.value
            }

        when {
            isSelected && item.selectedIconRes != null -> Image(
                painter            = painterResource(id = item.selectedIconRes),
                contentDescription = item.label,
                modifier           = iconModifier,
                colorFilter        = ColorFilter.tint(iconTint)
            )
            !isSelected && item.unselectedIconRes != null -> Image(
                painter            = painterResource(id = item.unselectedIconRes),
                contentDescription = item.label,
                modifier           = iconModifier,
                colorFilter        = ColorFilter.tint(iconTint)
            )
            else -> NexusText(
                text     = if (isSelected) item.selectedIconText else item.unselectedIconText,
                color    = iconTint,
                style    = NexusTheme.typography.h2,
                modifier = Modifier.graphicsLayer {
                    scaleX       = iconScale.value
                    scaleY       = iconScale.value
                    translationY = iconTranslationY.value
                }
            )
        }
    }
}

// ─── Badge Dot ────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.BadgeDot(count: Int) {
    Box(
        modifier = Modifier
            .offset(x = 10.dp, y = (-10).dp)
            .size(if (count > 9) 16.dp else 8.dp)
            .clip(CircleShape)
            .background(NexusTheme.colors.error)
            .align(Alignment.TopEnd),
        contentAlignment = Alignment.Center
    ) {
        if (count > 9) {
            NexusText(
                text     = "9+",
                color    = Color.White,
                style    = NexusTheme.typography.caption.copy(
                    fontSize   = 8.sp,
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