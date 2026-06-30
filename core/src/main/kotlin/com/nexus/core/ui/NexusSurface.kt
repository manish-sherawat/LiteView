package com.nexus.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme

@Composable
fun NexusSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NexusTheme.shapes.medium,
    color: Color = NexusTheme.colors.surface,
    elevation: Dp = 8.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = NexusTheme.colors.primary.copy(alpha = 0.1f),
                spotColor = NexusTheme.colors.primary.copy(alpha = 0.1f)
            )
            .clip(shape)
            .background(color)
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape)
                else Modifier.border(1.dp, NexusTheme.colors.textPrimary.copy(alpha = 0.2f), shape)
            ),
        content = content
    )
}
