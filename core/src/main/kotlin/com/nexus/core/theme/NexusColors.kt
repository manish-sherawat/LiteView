package com.nexus.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
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

val LocalNexusColors = compositionLocalOf {
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
    background = Color(0xFFF1F1F3),
    surface = Color(0xFFFCFCFE),
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

val sepiaNexusColors = NexusColors(
    background = Color(0xFFFBF0D9), // Warm parchment
    surface = Color(0xFFF2E6CD),
    surfaceVariant = Color(0xFFE8DCC3),
    primary = Color(0xFF5D4037),    // Soft brown accent
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF3E2723), // Espresso text
    textSecondary = Color(0xFF795548),
    divider = Color(0xFFD7CCC8),
    error = Color(0xFFD32F2F)
)



val forestNexusColors = NexusColors(
    background = Color(0xFFE8F5E9), // Light sage green
    surface = Color(0xFFF1F8E9),    // Very light green/white
    surfaceVariant = Color(0xFFC8E6C9),
    primary = Color(0xFF2A9D8F),    // Deep forest green
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1B2620), // Dark grey-green
    textSecondary = Color(0xFF455A64),
    divider = Color(0xFFA5D6A7),
    error = Color(0xFFD32F2F)
)

val sunsetNexusColors = NexusColors(
    background = Color(0xFFFFF3E0), // Soft peach
    surface = Color(0xFFFFF8E1),    // Very warm white
    surfaceVariant = Color(0xFFFFE0B2),
    primary = Color(0xFFFF6B6B),    // Deep sunset orange
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF3E2723), // Dark warm brown
    textSecondary = Color(0xFF5D4037),
    divider = Color(0xFFFFCC80),
    error = Color(0xFFD32F2F)
)
