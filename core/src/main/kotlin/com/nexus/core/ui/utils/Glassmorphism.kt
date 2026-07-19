package com.nexus.core.ui.utils

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

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
    val glassColor = fallbackColor.copy(alpha = alpha)
    // Subtle white edge highlight simulating glass refraction
    val glassBorderColor = Color.White.copy(alpha = if (isDark) 0.12f else 0.24f)

    this
        .background(glassColor, shape)
        .border(0.5.dp, glassBorderColor, shape)
}
