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

    val spacing: NexusSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalNexusSpacing.current
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

    val success by animateColorAsState(targetColors.success, animationSpec = colorSpring(), label = "success")
    val warning by animateColorAsState(targetColors.warning, animationSpec = colorSpring(), label = "warning")
    val searchHighlight by animateColorAsState(targetColors.searchHighlight, animationSpec = colorSpring(), label = "searchHighlight")
    val surfaceElevated by animateColorAsState(targetColors.surfaceElevated, animationSpec = colorSpring(), label = "surfaceElevated")
    val surfaceGlass by animateColorAsState(targetColors.surfaceGlass, animationSpec = colorSpring(), label = "surfaceGlass")
    val onSurface by animateColorAsState(targetColors.onSurface, animationSpec = colorSpring(), label = "onSurface")
    val trackBackground by animateColorAsState(targetColors.trackBackground, animationSpec = colorSpring(), label = "trackBackground")

    return NexusColors(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        primary = primary,
        onPrimary = onPrimary,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        divider = divider,
        error = error,
        success = success,
        warning = warning,
        searchHighlight = searchHighlight,
        surfaceElevated = surfaceElevated,
        surfaceGlass = surfaceGlass,
        onSurface = onSurface,
        trackBackground = trackBackground,
        surfaceGradient = targetColors.surfaceGradient,
        primaryGradient = targetColors.primaryGradient
    )
}

@Composable
fun NexusDocsViewerTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    hapticFeedbackEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val targetColors = when (themeMode) {
        ThemeMode.LIGHT -> lightNexusColors
        ThemeMode.DARK -> darkNexusColors
        ThemeMode.AMOLED -> amoledNexusColors
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
        LocalNexusSpacing provides defaultNexusSpacing,
        LocalHapticFeedbackEnabled provides hapticFeedbackEnabled,
        content = content
    )
}
