package com.nexus.core.ui.utils

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Applies a glassmorphism effect to the background.
 * Falls back to a semi-transparent color with a dynamic glass border highlight.
 * 
 * @param blurRadius The radius of the blur effect in pixels.
 * @param fallbackColor The color to use when blur is not supported.
 * @param alpha The opacity of the background color overlay.
 * @param shape The shape of the background and border.
 */
fun Modifier.glassBackground(
    blurRadius: Float = 30f,
    fallbackColor: Color = Color.Black.copy(alpha = 0.6f),
    alpha: Float = 0.7f,
    shape: Shape = RectangleShape
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val hazeState = LocalHazeState.current
    val glassColor = fallbackColor.copy(alpha = alpha)
    // Subtle white edge highlight simulating glass refraction
    val glassBorderColor = Color.White.copy(alpha = if (isDark) 0.12f else 0.24f)

    var modifier = this.clip(shape)

    if (hazeState != null) {
        modifier = modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = fallbackColor,
                tint = HazeTint(glassColor),
                blurRadius = blurRadius.dp
            )
        )
    }

    modifier
        .background(glassColor, shape)
        .border(0.5.dp, glassBorderColor, shape)
}

/**
 * Applies a translucent frosted-glass haze effect with theme-adaptive tinting and specular border.
 * When [hazeState] (or [LocalHazeState.current]) is present, samples and blurs content beneath it.
 *
 * @param hazeState Optional [HazeState]. If null, falls back to [LocalHazeState.current].
 * @param shape The clip and border shape.
 * @param blurRadius Frosted blur radius (defaults to 28.dp).
 * @param tintColor Optional tint overlay color. Defaults to translucent surface tint.
 * @param borderWidth Width of the glass specular border (defaults to 0.5.dp). Set to 0.dp to disable.
 * @param borderColor Optional border color. Defaults to theme-calibrated specular white/divider.
 * @param fallbackColor Optional solid/semi-translucent fallback color if haze is not active.
 * @param fillAlpha Optional custom alpha opacity for the background surface.
 */
fun Modifier.hazeGlassBackground(
    hazeState: HazeState? = null,
    shape: Shape = RectangleShape,
    blurRadius: Dp = 15.dp,
    tintColor: Color? = null,
    borderWidth: Dp = 0.5.dp,
    borderColor: Color? = null,
    fallbackColor: Color? = null,
    fillAlpha: Float? = null
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val resolvedHazeState = hazeState ?: LocalHazeState.current

    val surfaceColor = NexusTheme.colors.surface
    val hazeTintAlpha = if (isDark) 0.65f else 0.75f
    // High-opacity frosted surface (~94% light / ~88% dark): removes transparent see-through look
    val hazeFillAlpha = fillAlpha ?: if (isDark) 0.88f else 0.94f
    val fallbackAlpha = if (isDark) 0.92f else 0.96f

    val hazeTintColor = tintColor ?: surfaceColor.copy(alpha = hazeTintAlpha)
    val baseFillColor = surfaceColor.copy(alpha = if (resolvedHazeState != null) hazeFillAlpha else fallbackAlpha)

    var modifier = this.clip(shape)

    if (resolvedHazeState != null) {
        modifier = modifier.hazeEffect(
            state = resolvedHazeState,
            style = HazeStyle(
                backgroundColor = surfaceColor,
                tint = HazeTint(hazeTintColor),
                blurRadius = blurRadius
            )
        )
    }

    modifier = modifier.background(fallbackColor ?: baseFillColor, shape)

    if (borderWidth > 0.dp) {
        val defaultBorder = if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            NexusTheme.colors.divider.copy(alpha = 0.40f)
        }
        modifier = modifier.border(borderWidth, borderColor ?: defaultBorder, shape)
    }

    modifier
}
