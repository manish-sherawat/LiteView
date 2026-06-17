package com.nexus.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.nexus.core.preferences.ThemeMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import com.nexus.core.ui.animations.colorSpring

object NexusTheme {
    val colors: NexusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusColors.current

    val typography: NexusTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusTypography.current

    val shapes: NexusShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusShapes.current
}

val LocalHapticFeedbackEnabled = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun animateNexusColorsAsState(targetColors: NexusColors): NexusColors {
    val background by animateColorAsState(targetColors.background, animationSpec = colorSpring(), label = "bg")
    val surface by animateColorAsState(targetColors.surface, animationSpec = colorSpring(), label = "surface")
    val surfaceVariant by animateColorAsState(targetColors.surfaceVariant, animationSpec = colorSpring(), label = "surfaceVariant")
    val primary by animateColorAsState(targetColors.primary, animationSpec = colorSpring(), label = "primary")
    val onPrimary by animateColorAsState(targetColors.onPrimary, animationSpec = colorSpring(), label = "onPrimary")
    val textPrimary by animateColorAsState(targetColors.textPrimary, animationSpec = colorSpring(), label = "textPrimary")
    val textSecondary by animateColorAsState(targetColors.textSecondary, animationSpec = colorSpring(), label = "textSecondary")
    val divider by animateColorAsState(targetColors.divider, animationSpec = colorSpring(), label = "divider")
    val error by animateColorAsState(targetColors.error, animationSpec = colorSpring(), label = "error")

    return NexusColors(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        primary = primary,
        onPrimary = onPrimary,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        divider = divider,
        error = error
    )
}

@Composable
fun NexusDocsViewerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    hapticFeedbackEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val targetColors = when (themeMode) {
        ThemeMode.LIGHT -> lightNexusColors
        ThemeMode.DARK -> darkNexusColors
        ThemeMode.SEPIA -> sepiaNexusColors
        ThemeMode.FOREST -> forestNexusColors
        ThemeMode.SUNSET -> sunsetNexusColors
        ThemeMode.SYSTEM -> if (isSystemDark) darkNexusColors else lightNexusColors
    }
    val animatedColors = animateNexusColorsAsState(targetColors)

    CompositionLocalProvider(
        LocalNexusColors provides animatedColors,
        LocalNexusTypography provides defaultNexusTypography,
        LocalNexusShapes provides defaultNexusShapes,
        LocalHapticFeedbackEnabled provides hapticFeedbackEnabled,
        content = content
    )
}
