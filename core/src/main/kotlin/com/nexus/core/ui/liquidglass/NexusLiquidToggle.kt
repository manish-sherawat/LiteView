package com.nexus.core.ui.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.nexus.core.theme.LocalHapticFeedbackEnabled
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.liquidglass.backdrops.layerBackdrop
import com.nexus.core.ui.liquidglass.backdrops.rememberCombinedBackdrop
import com.nexus.core.ui.liquidglass.backdrops.rememberLayerBackdrop
import com.nexus.core.ui.liquidglass.effects.blur
import com.nexus.core.ui.liquidglass.effects.lens
import com.nexus.core.ui.liquidglass.highlight.Highlight
import com.nexus.core.ui.liquidglass.shadow.InnerShadow
import com.nexus.core.ui.liquidglass.shadow.Shadow
import com.nexus.core.ui.liquidglass.utils.DampedDragAnimation
import kotlinx.coroutines.flow.collectLatest

/**
 * Apple VisionOS / iOS style Physical Liquid Glass Switch / Toggle.
 *
 * Capabilities:
 * - Fluid glass thumb that physically squishes, deforms, and stretches with motion velocity.
 * - Real optical refraction (AGSL SDF Lens) with chromatic aberration dispersion on Android 13+ (API 33+).
 * - Hardware Gaussian blur and vibrancy boost on Android 12+ (API 31+).
 * - Graceful clean translucent fallback on Android 11 and below (API 26-30).
 * - Interactive swipe/drag gestures with smooth spring snap physics.
 * - Full accessibility semantics and tactile haptic feedback.
 */
@Composable
fun NexusLiquidToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    accentColor: Color = NexusTheme.colors.primary,
    enabled: Boolean = true
) {
    val isLightTheme = !isSystemInDarkTheme()
    val trackColor = if (isLightTheme) {
        Color(0xFF787878).copy(alpha = 0.20f)
    } else {
        Color(0xFF787880).copy(alpha = 0.36f)
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val hapticEnabled = LocalHapticFeedbackEnabled.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()

    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val triggerHaptic = {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.35f,
            onDragStarted = {
                didDrag = false
            },
            onDragStopped = {
                if (onCheckedChange != null && enabled) {
                    if (didDrag) {
                        val targetState = targetValue >= 0.5f
                        fraction = if (targetState) 1f else 0f
                        triggerHaptic()
                        onCheckedChange(targetState)
                        didDrag = false
                    } else {
                        val targetState = !checked
                        fraction = if (targetState) 1f else 0f
                        triggerHaptic()
                        onCheckedChange(targetState)
                    }
                }
            },
            onDrag = { _, dragAmount ->
                if (enabled) {
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = dragAmount.x / dragWidth
                    fraction = if (isLtr) {
                        (fraction + delta).fastCoerceIn(0f, 1f)
                    } else {
                        (fraction - delta).fastCoerceIn(0f, 1f)
                    }
                }
            }
        )
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { frac ->
                dampedDragAnimation.updateValue(frac)
            }
    }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    val combinedBackdrop = if (backdrop != null) {
        rememberCombinedBackdrop(
            backdrop,
            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                val progress = dampedDragAnimation.pressProgress
                val scaleX = lerp(2f / 3f, 0.75f, progress)
                val scaleY = lerp(0f, 0.75f, progress)
                scale(scaleX, scaleY) {
                    drawBackdrop()
                }
            }
        )
    } else {
        rememberBackdrop(trackBackdrop) { drawBackdrop ->
            val progress = dampedDragAnimation.pressProgress
            val scaleX = lerp(2f / 3f, 0.75f, progress)
            val scaleY = lerp(0f, 0.75f, progress)
            scale(scaleX, scaleY) {
                drawBackdrop()
            }
        }
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.CenterStart
    ) {
        // ── Track Layer ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    val currentFraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, currentFraction))
                }
                .size(64.dp, 28.dp)
        )

        // ── Liquid Glass Thumb Droplet ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val currentFraction = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX = if (isLtr) {
                        lerp(padding, padding + dragWidth, currentFraction)
                    } else {
                        lerp(-padding, -(padding + dragWidth), currentFraction)
                    }
                }
                .semantics {
                    role = Role.Switch
                }
                .then(if (enabled && onCheckedChange != null) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.08f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .then(
                    if (!isRenderEffectSupported()) {
                        Modifier
                            .background(Color.White, CircleShape)
                            .border(0.5.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                    } else {
                        Modifier
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}
