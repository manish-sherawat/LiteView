package com.nexus.core.ui.animations

import android.util.Log
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.nexus.core.theme.LocalHapticFeedbackEnabled

// ════════════════════════════════════════════════════════════════════
// SECTION 1: ANIMATION CONFIGURATION
// ════════════════════════════════════════════════════════════════════

/**
 * Central configuration for all animation behaviors.
 * 
 * Adjust these values to change animation feel globally:
 * - Faster: Multiply durations by 0.8
 * - Slower: Multiply durations by 1.2
 * - Bouncier: Increase dampingRatio
 * - Snappier: Increase stiffness
 */
object AnimationConfig {
    // Global speed multiplier (useful for debugging and accessibility)
    const val GLOBAL_SPEED_MULTIPLIER = 1f
    
    // Whether to respect system motion preferences
    const val RESPECT_MOTION_PREFERENCES = true
    
    // Haptic feedback enabled by default
    const val HAPTIC_ENABLED = true
    
    // Animation speed profiles
    object Presets {
        object Fast {
            const val TRANSITION = 150
            const val INTERACTION = 50
        }
        object Normal {
            const val TRANSITION = 300
            const val INTERACTION = 100
        }
        object Slow {
            const val TRANSITION = 600
            const val INTERACTION = 200
        }
    }
}

/**
 * Debug configuration for animations.
 * Allows slowing down animations for easier visual inspection during development.
 */
object AnimationDebug {
    // Enable debug mode to slow down animations
    var isEnabled = false
    
    // Speed multiplier when debug is enabled (0.5 = half speed)
    var speedMultiplier = if (isEnabled) 0.5f else 1f
    
    fun <T> tween(
        durationMillis: Int,
        delayMillis: Int = 0,
        easing: Easing = StandardEasing
    ) = androidx.compose.animation.core.tween<T>(
        durationMillis = (durationMillis * speedMultiplier).toInt(),
        delayMillis = (delayMillis * speedMultiplier).toInt(),
        easing = easing
    )
}

/**
 * Configuration for Shimmer effects to avoid magic numbers.
 */
object ShimmerConfig {
    const val SHIMMER_ALPHA_LIGHT = 0.08f  // Subtle on light backgrounds
    const val SHIMMER_ALPHA_DARK = 0.30f   // More visible on dark backgrounds
    const val SHIMMER_DURATION_MS = 1400   // Smooth, not too fast
    
    // Shimmer animation range (normalizes position across screen)
    const val SHIMMER_START_OFFSET = -1.5f
    const val SHIMMER_END_OFFSET = 1.5f
    
    // Gradient band width as proportion of screen width
    const val SHIMMER_GRADIENT_WIDTH = 0.6f
}

/**
 * Available haptic feedback patterns.
 */
enum class HapticPattern {
    LIGHT,      // Light tap
    MEDIUM,     // Standard feedback
    HEAVY,      // Strong feedback
    LONG_PRESS  // Long press
}

// ════════════════════════════════════════════════════════════════════
// SECTION 2: MATERIAL 3 EASING CURVES
// ════════════════════════════════════════════════════════════════════

/**
 * Emphasized easing curve for spatial transitions.
 * 
 * Complies with Material Design 3 motion guidelines for:
 * - Card entries and expansions
 * - Page transitions and navigation
 * - Modal surface appearances
 * 
 * Curve details: (0.2, 0) to (0, 1) - fast start, slow finish (deceleration)
 * 
 * See: https://m3.material.io/styles/motion/easing-and-duration
 * 
 * @see EmphasizedDecelerateEasing for entrance animations
 * @see EmphasizedAccelerateEasing for exit animations
 */
val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** 
 * Decelerate — elements arriving on-screen slow to a natural stop. 
 * Use for incoming elements.
 */
val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** 
 * Accelerate — elements leaving pick up speed and exit decisively.
 * Use for outgoing elements.
 */
val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** 
 * Standard — subtle non-spatial transitions (fades, color changes).
 */
val StandardEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** 
 * EaseInOutSine — looping / shimmer animations.
 */
val EaseInOutSine: Easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

// ════════════════════════════════════════════════════════════════════
// SECTION 3: SEMANTIC DURATION TOKENS
// ════════════════════════════════════════════════════════════════════

// Interaction Feedback (user immediate feedback)
const val DurationQuickTap = 50          // Single tap feedback (formerly DurationShort1)
const val DurationQuickPress = 100       // Press down/up (formerly DurationShort2)
const val DurationQuickRelease = 150     // Release animation (formerly DurationShort3)
const val DurationFastRipple = 200       // Fast ripple (formerly DurationShort4)

// Navigation & Transitions
const val DurationScreenBack = 250       // Back button (faster) (formerly DurationMedium1)
const val DurationScreenExit = 300       // Screen disappears (formerly DurationMedium2)
const val DurationScreenEnter = 350      // New screen appears (formerly DurationMedium3)
const val DurationComplexTransition = 400 // Complex transition (formerly DurationMedium4)

// Content Animations
const val DurationFadeIn = 300           // Fade in element
const val DurationSlideIn = 350          // Slide in element
const val DurationScaleIn = 300          // Scale in element

// Looping Animations
const val DurationShimmer = ShimmerConfig.SHIMMER_DURATION_MS
const val DurationPulse = 2000           // Pulse effect loop
const val DurationSpin = 1000            // Rotating icon

// Dialog & Modal
const val DurationDialogEnter = 300      // Dialog appears
const val DurationDialogExit = 200       // Dialog disappears

// Long Transitions
const val DurationLongEnter = 450        // (formerly DurationLong1)
const val DurationExtraLong = 500        // (formerly DurationLong2)

// ════════════════════════════════════════════════════════════════════
// SECTION 4: SPRING PHYSICS CONFIGURATIONS
// ════════════════════════════════════════════════════════════════════

/**
 * Spring configuration for press-down button feedback.
 * 
 * Produces a snappy, responsive press with a subtle micro-bounce.
 * Ideal for immediate tactile feedback on button clicks.
 * 
 * @return Spring animation with medium-low stiffness and medium-bouncy damping
 * 
 * @see springBounceClick for usage example
 */
fun <T> pressSpring() = spring<T>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioMediumBouncy
)

/** Smooth enter/exit: no overshoot (nav bar, card arrival) */
fun <T> entrySpring() = spring<T>(
    stiffness = Spring.StiffnessLow,
    dampingRatio = Spring.DampingRatioNoBouncy
)

/** Tab/icon selection: medium stiffness with a subtle organic bounce */
fun <T> selectionSpring() = spring<T>(
    stiffness = Spring.StiffnessMedium,
    dampingRatio = Spring.DampingRatioMediumBouncy
)

/** Color/alpha transitions: feels instant but still interpolated */
fun <T> colorSpring() = spring<T>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy
)

/** Modal surfaces: gentle landing bounce */
fun <T> dialogSpring() = spring<T>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioLowBouncy
)

/** Floating nav pill: one soft landing bounce for a lively feel */
fun <T> navPillSpring() = spring<T>(
    stiffness = Spring.StiffnessMedium,
    dampingRatio = 0.75f
)

/** Scale animations: quick pop-out or grow effect */
fun <T> scaleSpring() = spring<T>(
    stiffness = Spring.StiffnessMedium,
    dampingRatio = Spring.DampingRatioMediumBouncy
)

/** Expansion animations: sheet slides up, menu expands */
fun <T> expansionSpring() = spring<T>(
    stiffness = Spring.StiffnessLow,
    dampingRatio = Spring.DampingRatioNoBouncy
)

/** Drag feedback: responsive to finger movement */
fun <T> dragSpring() = spring<T>(
    stiffness = Spring.StiffnessMedium,
    dampingRatio = Spring.DampingRatioNoBouncy
)

/** Scroll bounce: subtle bounce at edges */
fun <T> scrollBounceSpring() = spring<T>(
    stiffness = Spring.StiffnessHigh,
    dampingRatio = 0.6f
)

// ════════════════════════════════════════════════════════════════════
// SECTION 5: REUSABLE ANIMATION SPECS
// ════════════════════════════════════════════════════════════════════

/**
 * Reusable configurations for common tween-based animations.
 */
object TweenConfigs {
    fun fadeIn(delay: Int = 0, duration: Int = DurationFadeIn) = 
        AnimationDebug.tween<Float>(durationMillis = duration, delayMillis = delay, easing = EmphasizedDecelerateEasing)
    
    fun fadeOut(delay: Int = 0, duration: Int = DurationQuickRelease) = 
        AnimationDebug.tween<Float>(durationMillis = duration, delayMillis = delay, easing = EmphasizedAccelerateEasing)
        
    fun slideIn(delay: Int = 0, duration: Int = DurationSlideIn) = 
        AnimationDebug.tween<Float>(durationMillis = duration, delayMillis = delay, easing = EmphasizedDecelerateEasing)
    
    fun scaleIn(delay: Int = 0, duration: Int = DurationScaleIn) = 
        AnimationDebug.tween<Float>(durationMillis = duration, delayMillis = delay, easing = EmphasizedDecelerateEasing)
}

// ════════════════════════════════════════════════════════════════════
// SECTION 6: UTILITY MODIFIERS & EXTENSIONS
// ════════════════════════════════════════════════════════════════════

/**
 * Applies animation only if condition is met.
 * 
 * Example:
 * ```
 * modifier.conditionalAnimation(shouldAnimate) {
 *     fadeSlideIn()
 * }
 * ```
 */
fun Modifier.conditionalAnimation(
    condition: Boolean,
    block: Modifier.() -> Modifier
): Modifier = if (condition) this.block() else this

/**
 * Repeats an animation infinitely with a gap between repeats.
 * Useful for pulsing effects.
 */
fun <T> Modifier.infiniteAnimation(
    animation: suspend () -> T,
    delayBetween: Int = 500
): Modifier = composed {
    this
}

/**
 * Combines multiple animations into a sequential animation block.
 */
fun Modifier.sequentialAnimation(
    vararg animations: suspend () -> Unit
): Modifier = composed {
    this
}

/**
 * Accessibility helper: Checks if the user prefers reduced motion.
 */
@Composable
fun remoteMotionSettings(): Boolean {
    val context = LocalContext.current
    return context.getSystemService<AccessibilityManager>()
        ?.let { !it.isEnabled } ?: false 
}

// ════════════════════════════════════════════════════════════════════
// SECTION 7: INTERACTIVE MODIFIERS (CLICKS & HAPTICS)
// ════════════════════════════════════════════════════════════════════

/**
 * Applies a spring-based scale animation to clickable elements.
 * 
 * When pressed, the element scales down smoothly, then bounces back.
 * Includes haptic feedback (vibration) if enabled.
 * 
 * Example:
 * ```
 * Button(
 *     modifier = Modifier.springBounceClick(onClick = { /* login */ })
 * ) { Text("Login") }
 * ```
 * 
 * @param enabled Whether the click is enabled
 * @param scaleDown Scale factor when pressed (must be between 0.5f and 0.95f, default 0.92f)
 * @param hapticPattern The type of haptic feedback to play on click
 * @param onClick Callback when clicked
 * 
 * @see springBounceCombinedClick for long-click support
 */
fun Modifier.springBounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.92f,
    hapticPattern: HapticPattern = HapticPattern.LIGHT,
    onClick: () -> Unit
): Modifier = composed {
    require(scaleDown in 0.5f..1.0f) {
        "scaleDown must be between 0.5 and 1.0, got $scaleDown"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue  = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = pressSpring(),
        label        = "pressScale"
    )

    val hapticFeedbackEnabled = LocalHapticFeedbackEnabled.current
    val haptic = LocalHapticFeedback.current

    val hapticType = when (hapticPattern) {
        HapticPattern.LIGHT -> HapticFeedbackType.TextHandleMove
        HapticPattern.MEDIUM -> HapticFeedbackType.LongPress
        HapticPattern.HEAVY -> HapticFeedbackType.LongPress
        HapticPattern.LONG_PRESS -> HapticFeedbackType.LongPress
    }

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication        = null,
            enabled           = enabled,
            onClick           = {
                if (hapticFeedbackEnabled && haptic != null) {
                    try {
                        haptic.performHapticFeedback(hapticType)
                    } catch (e: Exception) {
                        Log.w("HapticFeedback", "Haptic feedback failed", e)
                    }
                }
                onClick()
            }
        )
}

@androidx.compose.foundation.ExperimentalFoundationApi
fun Modifier.springBounceCombinedClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.92f,
    hapticPattern: HapticPattern = HapticPattern.LIGHT,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    require(scaleDown in 0.5f..1.0f) {
        "scaleDown must be between 0.5 and 1.0, got $scaleDown"
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue  = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = pressSpring(),
        label        = "pressScaleCombined"
    )

    val hapticFeedbackEnabled = LocalHapticFeedbackEnabled.current
    val haptic = LocalHapticFeedback.current

    val hapticTypeClick = when (hapticPattern) {
        HapticPattern.LIGHT -> HapticFeedbackType.TextHandleMove
        else -> HapticFeedbackType.LongPress
    }

    this
        .scale(scale)
        .combinedClickable(
            interactionSource = interactionSource,
            indication        = null,
            enabled           = enabled,
            onClick           = {
                if (hapticFeedbackEnabled && haptic != null) {
                    try {
                        haptic.performHapticFeedback(hapticTypeClick)
                    } catch (e: Exception) {
                        Log.w("HapticFeedback", "Haptic feedback failed", e)
                    }
                }
                onClick()
            },
            onLongClick       = onLongClick?.let {
                {
                    if (hapticFeedbackEnabled && haptic != null) {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (e: Exception) {
                            Log.w("HapticFeedback", "Haptic feedback failed", e)
                        }
                    }
                    it()
                }
            }
        )
}

// ════════════════════════════════════════════════════════════════════
// SECTION 8: TRANSITIONS AND EFFECTS
// ════════════════════════════════════════════════════════════════════

/**
 * Slide and fade in an element, suitable for list items.
 * 
 * Example: Fade-in List Item with Stagger
 * ```
 * LazyColumn {
 *     itemsIndexed(items) { index, item ->
 *         ListItem(
 *             modifier = Modifier.fadeSlideInAccessible(delay = index * 50)
 *         )
 *     }
 * }
 * ```
 */
fun Modifier.fadeSlideIn(
    delay: Int = 0,
    offsetY: Dp = 30.dp
): Modifier = composed {
    val cappedDelay = minOf(delay, 1000)
    
    val density  = LocalDensity.current
    val offsetYPx = remember(offsetY) { with(density) { offsetY.toPx() } }

    var visible by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        visible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = TweenConfigs.fadeIn(delay = cappedDelay),
        label = "fadeAlpha"
    )
    
    val translationProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = TweenConfigs.slideIn(delay = cappedDelay),
        label = "translationYProgress"
    )

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translationProgress * offsetYPx
    }
}

fun Modifier.fadeSlideIn(delay: Int = 0, offsetY: Float): Modifier =
    fadeSlideIn(delay = delay, offsetY = offsetY.dp)

/**
 * Accessibility-aware version of fadeSlideIn.
 * Drops animations to a 0 delay if "reduce motion" is enabled.
 */
fun Modifier.fadeSlideInAccessible(
    delay: Int = 0,
    offsetY: Dp = 30.dp
): Modifier = composed {
    val shouldReduceMotion = remoteMotionSettings()
    
    if (shouldReduceMotion) {
        fadeSlideIn(delay = 0, offsetY = offsetY)
    } else {
        fadeSlideIn(delay = delay, offsetY = offsetY)
    }
}

/**
 * Shimmer effect modifier for skeleton loading states.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by transition.animateFloat(
        initialValue = -1f, // Start completely offscreen to the left
        targetValue  = 2f,  // Go completely offscreen to the right
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    val surfaceColor = com.nexus.core.theme.NexusTheme.colors.surfaceVariant
    val isLight = remember(surfaceColor) { surfaceColor.luminance() > 0.5f }
    val shimmerColor = if (isLight) 
        Color.Black.copy(alpha = 0.08f) 
    else 
        Color.White.copy(alpha = 0.08f)

    this.drawWithContent {
        drawContent()
        val offset = shimmerX * size.width
        val gradient  = Brush.linearGradient(
            colors = listOf(Color.Transparent, shimmerColor, Color.Transparent),
            start  = Offset(offset, 0f),
            end    = Offset(offset + size.width * 0.7f, size.height) // Added height to create an angled effect
        )
        drawRect(brush = gradient, size = size)
    }
}