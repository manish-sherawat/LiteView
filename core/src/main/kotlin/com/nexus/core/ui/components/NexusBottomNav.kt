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
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.HazeStyle
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.nexus.core.ui.utils.readerNavGlassConfig
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
    visible: Boolean = true,
    homeStyle: HomeStyle = HomeStyle.APPLE_GLASSMORPHIC
) {
    val convertedItems = navigationItems.map { screen ->
        NexusNavItem(
            label = stringResource(id = screen.titleId),
            selectedIconRes = screen.iconIdActive,
            unselectedIconRes = screen.iconIdInactive,
            animRawRes = screen.animRawRes,
            route = screen.route
        )
    }

    NexusFloatingBottomNav(
        items = convertedItems,
        currentRoute = currentRoute,
        onItemSelected = { selectedItem ->
            val index = navigationItems.indexOfFirst { it.route == selectedItem.route }
            val screen = navigationItems.getOrNull(index) ?: Screens.Home
            onItemClick(screen, if (index >= 0) index else 0)
        },
        homeStyle = homeStyle,
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
    val morphSpring = remember { spring<Dp>(dampingRatio = 0.86f, stiffness = 380f) }

    val animCapsuleHeight by animateDpAsState(
        targetValue   = if (isCollapsed) 48.dp else 60.dp,
        animationSpec = morphSpring,
        label         = "capsuleH"
    )

    // Comfortable ergonomic tab width (calibrated dynamically for 2 or 3 tabs)
    val baseTabWidth = if (isCollapsed) 68.dp else (if (items.size <= 2) 112.dp else 96.dp)
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
                    dampingRatio = 0.84f,
                    stiffness = 360f
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
    val backdrop = com.nexus.core.ui.utils.LocalAppBackdrop.current
    val appleSurfaceOpacity = if (isDark) 0.12f else 0.35f
    val appleSurfaceTint = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val appleBgAlpha = if (isDark) 0.25f else 0.15f
    val appleBorderAlpha = if (isDark) 0.15f else 0.3f

    val hazeState = com.nexus.core.ui.utils.LocalHazeState.current
    val backgroundShellModifier = Modifier
        .clip(pillShape)
        .then(
            if (hazeState != null && hazeState.areas.isNotEmpty()) {
                Modifier.hazeChild(
                    state = hazeState,
                    style = dev.chrisbanes.haze.HazeStyle(
                        backgroundColor = NexusTheme.colors.surface,
                        tint = dev.chrisbanes.haze.HazeTint(appleSurfaceTint.copy(alpha = appleSurfaceOpacity)),
                        blurRadius = 32.dp
                    )
                )
            } else Modifier
        )
        .background(NexusTheme.colors.surface.copy(alpha = appleBgAlpha))
        .border(
            width = 0.5.dp,
            color = NexusTheme.colors.divider.copy(alpha = appleBorderAlpha),
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