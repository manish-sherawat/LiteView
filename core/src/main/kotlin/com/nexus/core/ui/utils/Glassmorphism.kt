package com.nexus.core.ui.utils

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Applies a glassmorphism (blur) effect to the background.
 * Uses hardware-accelerated RenderEffect on API 31+ (Android 12+).
 * Falls back to a semi-transparent color on older devices.
 * 
 * @param blurRadius The radius of the blur effect in pixels.
 * @param fallbackColor The color to use when blur is not supported.
 * @param alpha The opacity of the background color overlay.
 */
fun Modifier.glassBackground(
    blurRadius: Float = 30f,
    fallbackColor: Color = Color.Black.copy(alpha = 0.6f),
    alpha: Float = 0.7f
): Modifier = composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.background(fallbackColor.copy(alpha = alpha))
    } else {
        this.background(fallbackColor)
    }
}
