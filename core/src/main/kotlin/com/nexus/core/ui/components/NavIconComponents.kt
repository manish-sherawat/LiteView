package com.nexus.core.ui.components

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.RawRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.*

// ─── Regular Nav Icon + Animated Label ───────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NexusNavIconWithLabel(
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
        targetValue   = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 550f),
        label         = "navPressScale"
    )

    // Selection pop-in bounce spring
    val selectionScale = remember { Animatable(if (isSelected) 1f else 0.94f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            selectionScale.animateTo(
                targetValue   = 1f,
                animationSpec = spring(
                    dampingRatio = 0.65f, // refined, silky micro-bounce without snap hitch
                    stiffness    = 420f
                )
            )
        } else {
            selectionScale.animateTo(
                targetValue   = 0.94f,
                animationSpec = spring(
                    dampingRatio = 0.88f,
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
internal fun NavIcon(
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
internal fun LottieNavIcon(
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
internal fun BadgeDot(count: Int) {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 480f))
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
