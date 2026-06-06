package com.nexus.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

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
fun NexusDocsViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    hapticFeedbackEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkNexusColors else lightNexusColors

    CompositionLocalProvider(
        LocalNexusColors provides colors,
        LocalNexusTypography provides defaultNexusTypography,
        LocalNexusShapes provides defaultNexusShapes,
        LocalHapticFeedbackEnabled provides hapticFeedbackEnabled,
        content = content
    )
}
