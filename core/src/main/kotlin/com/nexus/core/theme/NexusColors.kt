package com.nexus.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
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
    val error: Color,
    val success: Color,
    val warning: Color,
    val searchHighlight: Color,
    val surfaceElevated: Color,
    val surfaceGlass: Color,
    val onSurface: Color,
    val trackBackground: Color,
    val surfaceGradient: Brush,
    val primaryGradient: Brush
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
        error = Color.Unspecified,
        success = Color.Unspecified,
        warning = Color.Unspecified,
        searchHighlight = Color.Unspecified,
        surfaceElevated = Color.Unspecified,
        surfaceGlass = Color.Unspecified,
        onSurface = Color.Unspecified,
        trackBackground = Color.Unspecified,
        surfaceGradient = Brush.verticalGradient(listOf(Color.Unspecified, Color.Unspecified)),
        primaryGradient = Brush.verticalGradient(listOf(Color.Unspecified, Color.Unspecified))
    )
}

val lightNexusColors = NexusColors(
    background = Color(0xFFF6F8FA),      // Clean Apple/GitHub light-grey canvas
    surface = Color(0xFFFFFFFF),         // Crisp pure white card surface
    surfaceVariant = Color(0xFFEFF2F5),  // Soft container/shimmer
    primary = Color(0xFF0F172A),         // Deep slate black accent
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F172A),     // High-contrast slate-900 text
    textSecondary = Color(0xFF64748B),   // Slate-500 secondary text
    divider = Color(0xFFE2E8F0),         // Crisp subtle border
    error = Color(0xFFEF4444),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    searchHighlight = Color(0x66FFEB3B),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceGlass = Color(0xFFFFFFFF).copy(alpha = 0.55f),
    onSurface = Color(0xFF0F172A),
    trackBackground = Color(0xFFEDEDED),
    surfaceGradient = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEEEEEE))),
    primaryGradient = Brush.horizontalGradient(listOf(Color(0xFF151515), Color(0xFF2C2C2C)))
)

val darkNexusColors = NexusColors(
    background = Color(0xFF121212), // Material Dark Surface
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2C2C2C), 
    primary = Color(0xFFFFFFFF), 
    onPrimary = Color(0xFF000000),
    textPrimary = Color(0xFFE8E8E8),
    textSecondary = Color(0xFFAAAAAA),
    divider = Color(0xFF333333),
    error = Color(0xFFFF453A),
    success = Color(0xFF32D74B),
    warning = Color(0xFFFF9F0A),
    searchHighlight = Color(0x88FFD60A),
    surfaceElevated = Color(0xFF2C2C2C),
    surfaceGlass = Color(0xFF1E1E1E).copy(alpha = 0.65f),
    onSurface = Color(0xFFE8E8E8),
    trackBackground = Color(0xFF333333),
    surfaceGradient = Brush.verticalGradient(listOf(Color(0xFF2C2C2C), Color(0xFF1E1E1E))),
    primaryGradient = Brush.horizontalGradient(listOf(Color(0xFFE8E8E8), Color(0xFFCCCCCC)))
)

val amoledNexusColors = NexusColors(
    background = Color(0xFF000000), // True AMOLED black
    surface = Color(0xFF0F0F0F),    // Deeper grey for better contrast
    surfaceVariant = Color(0xFF181818), 
    primary = Color(0xFFFFFFFF), 
    onPrimary = Color(0xFF000000),
    textPrimary = Color(0xFFE8E8E8),
    textSecondary = Color(0xFFAAAAAA),
    divider = Color(0xFF2C2C2C),
    error = Color(0xFFFF453A), // Vibrant Apple Red
    success = Color(0xFF32D74B), // Vibrant Green
    warning = Color(0xFFFF9F0A), // Vibrant Orange
    searchHighlight = Color(0x88FFD60A), // Glowing Yellow
    surfaceElevated = Color(0xFF181818),
    surfaceGlass = Color(0xFF0F0F0F).copy(alpha = 0.65f),
    onSurface = Color(0xFFE8E8E8),
    trackBackground = Color(0xFF1A1A1A),
    surfaceGradient = Brush.verticalGradient(listOf(Color(0xFF181818), Color(0xFF0F0F0F))),
    primaryGradient = Brush.horizontalGradient(listOf(Color(0xFFE8E8E8), Color(0xFFCCCCCC)))
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
    error = Color(0xFFD32F2F),
    success = Color(0xFF4CAF50),
    warning = Color(0xFFFF9800),
    searchHighlight = Color(0x66FFEB3B),
    surfaceElevated = Color(0xFFE8DCC3),
    surfaceGlass = Color(0xFFF2E6CD).copy(alpha = 0.65f),
    onSurface = Color(0xFF3E2723),
    trackBackground = Color(0xFFD7CCC8),
    surfaceGradient = Brush.verticalGradient(listOf(Color(0xFFF2E6CD), Color(0xFFE8DCC3))),
    primaryGradient = Brush.horizontalGradient(listOf(Color(0xFF5D4037), Color(0xFF4E342E)))
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
    error = Color(0xFFD32F2F),
    success = Color(0xFF4CAF50),
    warning = Color(0xFFFF9800),
    searchHighlight = Color(0x66FFEB3B),
    surfaceElevated = Color(0xFFC8E6C9),
    surfaceGlass = Color(0xFFF1F8E9).copy(alpha = 0.65f),
    onSurface = Color(0xFF1B2620),
    trackBackground = Color(0xFFA5D6A7),
    surfaceGradient = Brush.verticalGradient(listOf(Color(0xFFF1F8E9), Color(0xFFC8E6C9))),
    primaryGradient = Brush.horizontalGradient(listOf(Color(0xFF2A9D8F), Color(0xFF21867A)))
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
    error = Color(0xFFD32F2F),
    success = Color(0xFF4CAF50),
    warning = Color(0xFFFF9800),
    searchHighlight = Color(0x66FFEB3B),
    surfaceElevated = Color(0xFFFFE0B2),
    surfaceGlass = Color(0xFFFFF8E1).copy(alpha = 0.65f),
    onSurface = Color(0xFF3E2723),
    trackBackground = Color(0xFFFFCC80),
    surfaceGradient = Brush.verticalGradient(listOf(Color(0xFFFFF8E1), Color(0xFFFFE0B2))),
    primaryGradient = Brush.horizontalGradient(listOf(Color(0xFFFF6B6B), Color(0xFFE53935)))
)

object DocumentAccentColors {
    val PDF  = Color(0xFFFF3B30) // Vibrant Red
    val DOCX = Color(0xFF0A84FF) // Vibrant Blue
    val XLSX = Color(0xFF32D74B) // Vibrant Green
    val PPTX = Color(0xFFFF9500) // Vibrant Orange
    val TXT  = Color(0xFFBF5AF2) // Vibrant Purple
    val UNKNOWN = Color(0xFF0A84FF)
}
