package com.nexus.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class NexusColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val error: Color
)

val LocalNexusColors = staticCompositionLocalOf {
    NexusColors(
        background = Color.Unspecified,
        surface = Color.Unspecified,
        surfaceVariant = Color.Unspecified,
        primary = Color.Unspecified,
        onPrimary = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        divider = Color.Unspecified,
        error = Color.Unspecified
    )
}

val lightNexusColors = NexusColors(
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F3F5),
    primary = Color(0xFF000000), // Minimal black accent
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF212529),
    textSecondary = Color(0xFF868E96),
    divider = Color(0xFFE9ECEF),
    error = Color(0xFFFA5252)
)

val darkNexusColors = NexusColors(
    background = Color(0xFF000000), // True AMOLED black
    surface = Color(0xFF121212),    // Very dark grey for surface depth
    surfaceVariant = Color(0xFF1E1E1E), // Slightly lighter for elevated surfaces
    primary = Color(0xFFFFFFFF), // Minimal white accent
    onPrimary = Color(0xFF000000),
    textPrimary = Color(0xFFE0E0E0),
    textSecondary = Color(0xFFA0A0A0),
    divider = Color(0xFF333333),
    error = Color(0xFFFF6B6B)
)
