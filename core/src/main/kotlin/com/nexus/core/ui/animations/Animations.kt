package com.nexus.core.ui.animations

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.LocalHapticFeedbackEnabled

// ─── Material 3 Motion Easing Curves ─────────────────────────────────────────
// Source: https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration

/** Emphasized — for spatial transitions (card entry, page transitions) */
val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Decelerate — elements arriving on-screen slow to a natural stop */
val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Accelerate — elements leaving pick up speed and exit decisively */
val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Standard — subtle non-spatial transitions (fades, color changes) */
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** EaseInOutSine — looping / shimmer animations */
val EaseInOutSine: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

// ─── Duration Tokens (ms) ────────────────────────────────────────────────────
const val DurationShort1 = 50
const val DurationShort2 = 100
const val DurationShort3 = 150
const val DurationShort4 = 200
const val DurationMedium1 = 250
const val DurationMedium2 = 300
const val DurationMedium3 = 350
const val DurationMedium4 = 400
const val DurationLong1  = 450
const val DurationLong2  = 500

// ─── Spring Physics Helpers ───────────────────────────────────────────────────

/** Press-down feedback: snappy with a micro-bounce */
fun <T> pressSpring() = spring<T>(
    stiffness    = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioLowBouncy
)

/** Smooth enter/exit: no overshoot (nav bar, card arrival) */
fun <T> entrySpring() = spring<T>(
    stiffness    = Spring.StiffnessLow,
    dampingRatio = Spring.DampingRatioNoBouncy
)

/** Tab/icon selection: medium stiffness with a subtle organic bounce */
fun <T> selectionSpring() = spring<T>(
    stiffness    = Spring.StiffnessMedium,
    dampingRatio = Spring.DampingRatioMediumBouncy
)

/** Color/alpha transitions: feels instant but still interpolated */
fun <T> colorSpring() = spring<T>(
    stiffness    = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy
)

/** Modal surfaces: gentle landing bounce */
fun <T> dialogSpring() = spring<T>(
    stiffness    = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioLowBouncy
)

/** Floating nav pill: one soft landing bounce for a lively feel */
fun <T> navPillSpring() = spring<T>(
    stiffness    = Spring.StiffnessMedium,
    dampingRatio = 0.75f        // between NoBouncy (1f) and LowBouncy (0.75f)
)

// ─── springBounceClick ───────────────────────────────────────────────────────
fun Modifier.springBounceClick(
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue  = if (isPressed) scaleDown else 1f,
        animationSpec = pressSpring(),
        label        = "pressScale"
    )

    val hapticFeedbackEnabled = LocalHapticFeedbackEnabled.current
    val haptic = LocalHapticFeedback.current

    androidx.compose.runtime.LaunchedEffect(isPressed) {
        if (isPressed && hapticFeedbackEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication        = null,
            onClick           = onClick
        )
}

// ─── fadeSlideIn ─────────────────────────────────────────────────────────────
fun Modifier.fadeSlideIn(
    delay: Int = 0,
    offsetY: Dp = 30.dp
): Modifier = composed {
    var targetAlpha        by remember { mutableFloatStateOf(0f) }
    var targetTranslationY by remember { mutableFloatStateOf(1f) }

    val density  = LocalDensity.current
    val offsetYPx = remember(offsetY) { with(density) { offsetY.toPx() } }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (delay > 0) kotlinx.coroutines.delay(delay.toLong())
        targetAlpha        = 1f
        targetTranslationY = 0f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue   = targetAlpha,
        animationSpec = tween(DurationMedium2, easing = EmphasizedDecelerateEasing),
        label         = "fadeAlpha"
    )
    val animatedTranslationYProgress by animateFloatAsState(
        targetValue   = targetTranslationY,
        animationSpec = tween(DurationMedium3, easing = EmphasizedDecelerateEasing),
        label         = "translationYProgress"
    )

    this.graphicsLayer {
        alpha        = animatedAlpha
        translationY = animatedTranslationYProgress * offsetYPx
    }
}

fun Modifier.fadeSlideIn(delay: Int = 0, offsetY: Float): Modifier =
    fadeSlideIn(delay = delay, offsetY = offsetY.dp)

// ─── shimmerEffect ───────────────────────────────────────────────────────────
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -1.5f,
        targetValue  =  1.5f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    this.drawWithContent {
        drawContent()
        val bandStart = shimmerX * size.width
        val gradient  = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
            start  = Offset(bandStart, 0f),
            end    = Offset(bandStart + size.width * 0.6f, 0f)
        )
        drawRect(brush = gradient, size = size)
    }
}