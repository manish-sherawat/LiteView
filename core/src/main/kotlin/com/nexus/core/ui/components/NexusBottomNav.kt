package com.nexus.core.ui.components

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*
import com.nexus.core.R
import com.nexus.core.preferences.HomeStyle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.*
import com.nexus.core.ui.utils.LocalGlassEffectConfig
import com.nexus.core.ui.utils.liquidGlass
import com.nexus.core.ui.utils.glassBackground
import kotlinx.coroutines.launch

// ─── Screen Navigation Sealed Class ──────────────────────────────────────────

sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
    @RawRes val animRawRes: Int? = null,
) {
    data object Home     : Screens(R.string.home, R.drawable.ic_home_outline, R.drawable.ic_home_filled, "home")
    data object Settings : Screens(R.string.settings, R.drawable.ic_settings_outline, R.drawable.ic_settings_filled, "settings")
}

// ─── Nav Item Model ───────────────────────────────────────────────────────────

data class NexusNavItem(
    val label: String,
    val selectedIconText: String = "",
    val unselectedIconText: String = "",
    val selectedIconRes: Int? = null,
    val unselectedIconRes: Int? = null,
    @RawRes val animRawRes: Int? = null,
    val route: String,
    val badge: Int = 0
)

// ─── Floating Tab Bar Scroll Connection Helper ────────────────────────────────

@Composable
fun rememberFloatingTabBarScrollConnection(
    scrollThreshold: Dp = 36.dp,
    onCollapseChanged: ((Boolean) -> Unit)? = null
): NestedScrollConnection {
    val density = LocalDensity.current
    val thresholdPx = with(density) { scrollThreshold.toPx() }
    var downAccumulator by remember { mutableFloatStateOf(0f) }
    var upAccumulator by remember { mutableFloatStateOf(0f) }

    return remember(thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f) {
                    // Scrolling down (finger moves up) -> Collapse
                    upAccumulator = 0f
                    downAccumulator += -delta
                    if (downAccumulator > thresholdPx) {
                        onCollapseChanged?.invoke(true)
                        downAccumulator = 0f
                    }
                } else if (delta > 0f) {
                    // Scrolling up (finger moves down) -> Expand
                    downAccumulator = 0f
                    upAccumulator += delta
                    if (upAccumulator > (thresholdPx * 0.7f)) {
                        onCollapseChanged?.invoke(false)
                        upAccumulator = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
}

// ─── AppFloatingNavBar for Screens Model ──────────────────────────────────────

@Composable
fun AppFloatingNavBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollConnection: NestedScrollConnection? = null,
    visible: Boolean = true
) {
    val convertedItems = remember(navigationItems) {
        navigationItems.map { screen ->
            NexusNavItem(
                label = "",
                selectedIconRes = screen.iconIdActive,
                unselectedIconRes = screen.iconIdInactive,
                animRawRes = screen.animRawRes,
                route = screen.route
            )
        }
    }

    NexusFloatingBottomNav(
        items = convertedItems,
        currentRoute = currentRoute,
        onItemSelected = { selectedItem ->
            val index = navigationItems.indexOfFirst { it.route == selectedItem.route }
            val screen = navigationItems.getOrNull(index) ?: Screens.Home
            onItemClick(screen, if (index >= 0) index else 0)
        },
        homeStyle = HomeStyle.APPLE_GLASSMORPHIC,
        modifier = modifier,
        visible = visible
    )
}

// ─── Floating Bottom Nav ──────────────────────────────────────────────────────

@Composable
fun NexusFloatingBottomNav(
    items: List<NexusNavItem>,
    currentRoute: String?,
    onItemSelected: (NexusNavItem) -> Unit,
    homeStyle: HomeStyle,
    modifier: Modifier = Modifier,
    isCollapsed: Boolean = false,
    onScrollToTop: (() -> Unit)? = null,    // long-press Home = scroll to top
    visible: Boolean = true
) {
    val density = LocalDensity.current
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }
    val isCapsuleSelected = selectedIndex >= 0

    // ── Dynamic Dimensions & Fluid Spring Specs ───────────────────────────────
    val morphSpring = remember { spring<Dp>(dampingRatio = 0.85f, stiffness = 380f) }

    val animCapsuleHeight by animateDpAsState(
        targetValue   = if (isCollapsed) 48.dp else 60.dp,
        animationSpec = morphSpring,
        label         = "capsuleH"
    )

    // Comfortable ergonomic tab width (wider for natural left-to-center thumb reach)
    val baseTabWidth = if (isCollapsed) 68.dp else 96.dp
    val animTabWidth by animateDpAsState(
        targetValue   = baseTabWidth,
        animationSpec = morphSpring,
        label         = "tabW"
    )

    val animIndicatorHeight by animateDpAsState(
        targetValue   = if (isCollapsed) 38.dp else 48.dp,
        animationSpec = morphSpring,
        label         = "indH"
    )

    val animIconSize by animateDpAsState(
        targetValue   = if (isCollapsed) 22.dp else 24.dp,
        animationSpec = morphSpring,
        label         = "iconSize"
    )

    val horizontalPaddingDp = if (isCollapsed) 8.dp else 10.dp
    val tabSpacingDp        = 6.dp

    // ── Fluid Sliding Indicator Position Animatable ───────────────────────────
    // Using an animated tab index fraction eliminates composition frame resets
    // and guarantees perfect synchronization with dynamic width animations
    val tabFraction = remember { Animatable(if (selectedIndex >= 0) selectedIndex.toFloat() else 0f) }
    val indicatorAlpha = remember { Animatable(if (isCapsuleSelected) 1f else 0f) }

    // ── Container entry / exit ────────────────────────────────────────────────
    val containerAlpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = entrySpring(),
        label         = "navContainerAlpha"
    )
    val shadowElevation by animateDpAsState(
        targetValue   = if (visible) 16.dp else 0.dp,
        animationSpec = navPillSpring(),
        label         = "navShadow"
    )

    // ── Drive indicator spring physics on tab selection change ────────────────
    LaunchedEffect(selectedIndex, isCapsuleSelected) {
        if (!isCapsuleSelected || selectedIndex < 0) {
            indicatorAlpha.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }

        if (indicatorAlpha.value < 0.05f) {
            tabFraction.snapTo(selectedIndex.toFloat())
            launch { indicatorAlpha.animateTo(1f, tween(160, easing = FastOutSlowInEasing)) }
            return@LaunchedEffect
        }

        launch { indicatorAlpha.animateTo(1f, tween(120, easing = FastOutSlowInEasing)) }
        // Fluid, organic glide spring
        launch {
            tabFraction.animateTo(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = 380f
                )
            )
        }
    }

    val isDark = isSystemInDarkTheme()
    val pillShape = CircleShape

    val primaryColor = NexusTheme.colors.primary

    // ── Indicator colors with clean contrast ───────────────────────────────────
    val indicatorFill = primaryColor.copy(alpha = 0.12f)
    val indicatorStroke = primaryColor.copy(alpha = 0.18f)

    // ── Solid opaque background shell modifier ────────────────────────────────
    val backgroundShellModifier = Modifier
        .clip(pillShape)
        .background(NexusTheme.colors.surface)
        .border(
            width = 1.dp,
            color = NexusTheme.colors.divider.copy(alpha = 0.8f),
            shape = pillShape
        )

    // ── Full System Navigation Insets Clearance ────────────────────────────────
    val navBarsBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val targetBottomOffset = (if (isCollapsed) 10.dp else 16.dp) + navBarsBottom
    val animBottomOffset by animateDpAsState(
        targetValue   = targetBottomOffset,
        animationSpec = morphSpring,
        label         = "bottomOffset"
    )

    val ambientShadow = if (isDark)
        Color.Black.copy(alpha = 0.50f)
    else
        Color.Black.copy(alpha = 0.10f)

    val spotShadow = primaryColor.copy(alpha = if (isDark) 0.30f else 0.18f)

    // Layout container — centered alignment with comfortable reach
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 480.dp)
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = animBottomOffset)
            .graphicsLayer { alpha = containerAlpha },
        contentAlignment = Alignment.Center
    ) {
        // ── Navigation Capsule Pill ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .clip(pillShape)
                .semantics { selectableGroup() },
            contentAlignment = Alignment.Center
        ) {
            // Backdrop + solid shell
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation    = shadowElevation,
                        shape        = pillShape,
                        clip         = false,
                        spotColor    = spotShadow,
                        ambientColor = ambientShadow
                    )
                    .then(backgroundShellModifier)
            )

            // Sliding Indicator Pill + accessibility
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .semantics {
                        val activeItem = items.getOrNull(selectedIndex)
                        if (activeItem != null && isCapsuleSelected) {
                            stateDescription = "${activeItem.label} selected"
                        }
                    }
                    .graphicsLayer { alpha = indicatorAlpha.value }
                    .drawBehind {
                        if (indicatorAlpha.value <= 0f) return@drawBehind

                        val tabW = animTabWidth.toPx()
                        val spacing = tabSpacingDp.toPx()
                        val pad = horizontalPaddingDp.toPx()
                        val indH = animIndicatorHeight.toPx()
                        val centerY = size.height / 2f
                        val radius = indH / 2f
                        val top = centerY - indH / 2f

                        val currentLeft = pad + (tabW + spacing) * tabFraction.value
                        val iW = (tabW - 6.dp.toPx()).coerceAtLeast(0f)
                        val iX = currentLeft + 3.dp.toPx()

                        drawRoundRect(
                            color        = indicatorFill,
                            topLeft      = Offset(iX, top),
                            size         = Size(iW, indH),
                            cornerRadius = CornerRadius(radius, radius)
                        )
                        drawRoundRect(
                            color        = indicatorStroke,
                            topLeft      = Offset(iX + 0.5f, top + 0.5f),
                            size         = Size(iW - 1f, indH - 1f),
                            cornerRadius = CornerRadius(radius, radius),
                            style        = Stroke(width = 1.dp.toPx())
                        )
                    }
            )

            // Nav Items Row
            Row(
                modifier = Modifier
                    .height(animCapsuleHeight)
                    .padding(horizontal = horizontalPaddingDp),
                horizontalArrangement = Arrangement.spacedBy(tabSpacingDp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = isCapsuleSelected && currentRoute == item.route

                    Box(
                        modifier = Modifier
                            .width(animTabWidth)
                            .height(animCapsuleHeight)
                            // Accessibility: Tab role + selected state
                            .semantics {
                                role = Role.Tab
                                selected = isSelected
                                contentDescription = item.label
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        NexusNavIconWithLabel(
                            item        = item,
                            isSelected  = isSelected,
                            isCollapsed = isCollapsed,
                            iconSize    = animIconSize,
                            onClick     = { onItemSelected(item) },
                            // Long-press on first (Home) item = scroll to top
                            onLongClick = if (index == 0) onScrollToTop else null
                        )
                    }
                }
            }
        }
    }
}

// ─── Regular Nav Icon + Animated Label ───────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NexusNavIconWithLabel(
    item: NexusNavItem,
    isSelected: Boolean,
    isCollapsed: Boolean,
    iconSize: Dp = 22.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press squish spring
    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f),
        label         = "navPressScale"
    )

    // Selection pop-in bounce spring
    val selectionScale = remember { Animatable(if (isSelected) 1f else 0.94f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            selectionScale.snapTo(0.80f)
            selectionScale.animateTo(
                targetValue   = 1f,
                animationSpec = spring(
                    dampingRatio = 0.48f, // energetic, delightful pop
                    stiffness    = 420f
                )
            )
        } else {
            selectionScale.animateTo(
                targetValue   = 0.94f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness    = 380f
                )
            )
        }
    }

    val iconTint by animateColorAsState(
        targetValue   = if (isSelected) NexusTheme.colors.primary else NexusTheme.colors.textSecondary,
        animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerateEasing),
        label         = "iconTint"
    )

    val labelAlpha by animateFloatAsState(
        targetValue   = if (isCollapsed) 0f else if (isSelected) 1f else 0.85f,
        animationSpec = tween(
            durationMillis = if (isCollapsed) 140 else 200,
            easing         = if (isCollapsed) FastOutLinearInEasing else LinearOutSlowInEasing
        ),
        label         = "labelAlpha"
    )
    val labelHeight by animateDpAsState(
        targetValue   = if (isCollapsed) 0.dp else if (item.label.isEmpty()) 0.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label         = "labelHeight"
    )

    val totalIconScale = pressScale * selectionScale.value
    val hasIcon = item.selectedIconRes != null || item.unselectedIconRes != null || item.animRawRes != null || item.selectedIconText.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick?.invoke()
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (hasIcon) {
            // Icon with crossfade + badge pop-in
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(iconSize)
            ) {
                NavIcon(
                    item       = item,
                    isSelected = isSelected,
                    tint       = iconTint,
                    scale      = totalIconScale,
                    iconSize   = iconSize
                )
                // Badge: shown/hidden with pop-in scale animation inside BadgeDot
                if (item.badge > 0) {
                    BadgeDot(count = item.badge)
                }
            }

            // Animated label (collapses cleanly when collapsed)
            if (labelHeight > 0.dp && item.label.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clipToBounds()
                    .graphicsLayer { alpha = labelAlpha }
                    .height(labelHeight)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text  = item.label,
                    color = iconTint,
                    style = NexusTheme.typography.caption.copy(
                        fontSize      = 11.sp,
                        fontWeight    = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    ),
                    maxLines = 1
                )
            }
        }
        } else {
            NexusText(
                text  = item.label,
                color = iconTint,
                style = NexusTheme.typography.body.copy(
                    fontSize      = if (isSelected) 14.5.sp else 14.sp,
                    fontWeight    = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                ),
                maxLines = 1
            )
        }
    }
}

// ─── Shared Icon Renderer — with Crisp Vector Crossfade ──────────────────────

@Composable
private fun NavIcon(
    item: NexusNavItem,
    isSelected: Boolean,
    tint: Color,
    scale: Float,
    iconSize: Dp = 24.dp
) {
    val iconModifier = Modifier
        .size(iconSize)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }

    val iconRes = if (isSelected) (item.selectedIconRes ?: item.unselectedIconRes) else (item.unselectedIconRes ?: item.selectedIconRes)

    if (iconRes != null) {
        Crossfade(
            targetState   = isSelected,
            animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerateEasing),
            label         = "iconCrossfade"
        ) { selected ->
            val res = if (selected) (item.selectedIconRes ?: iconRes) else (item.unselectedIconRes ?: iconRes)
            Image(
                painter            = painterResource(id = res),
                contentDescription = item.label,
                modifier           = iconModifier,
                colorFilter        = ColorFilter.tint(tint)
            )
        }
    } else if (item.animRawRes != null) {
        LottieNavIcon(
            animRes = item.animRawRes,
            isSelected = isSelected,
            tint = tint,
            modifier = iconModifier
        )
    } else {
        NexusText(
            text     = if (isSelected) item.selectedIconText else item.unselectedIconText,
            color    = tint,
            style    = NexusTheme.typography.h2,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

@Composable
private fun LottieNavIcon(
    @RawRes animRes: Int,
    isSelected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animRes))
    val animatable = rememberLottieAnimatable()

    val colorFilter = remember(tint) {
        PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_ATOP)
    }
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = colorFilter,
            keyPath = arrayOf("**")
        )
    )

    LaunchedEffect(isSelected, composition) {
        val comp = composition ?: return@LaunchedEffect
        if (isSelected) {
            animatable.animate(
                composition = comp,
                iterations = 1,
                speed = 1.25f,
                initialProgress = 0f
            )
        } else {
            animatable.snapTo(comp, 0f)
        }
    }

    LottieAnimation(
        composition = composition,
        progress = { animatable.progress },
        dynamicProperties = dynamicProperties,
        modifier = modifier
    )
}

// ─── Badge Dot — with scale pop-in animation ──────────────────────────────────

@Composable
private fun BadgeDot(count: Int) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 480f))
    }
    Box(
        modifier = Modifier
            .offset(x = 6.dp, y = (-3).dp)
            .size(15.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
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