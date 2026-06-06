package com.nexus.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme

@Composable
fun NexusButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    containerColor: Color = NexusTheme.colors.primary,
    contentColor: Color = NexusTheme.colors.onPrimary,
    shape: Shape = NexusTheme.shapes.pill,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    NexusSurface(
        modifier = modifier.clip(shape).clickable(
            interactionSource = interactionSource,
            indication = null, // Custom indication could go here
            enabled = enabled,
            onClick = onClick
        ),
        shape = shape,
        color = if (enabled) containerColor else NexusTheme.colors.surfaceVariant,
        elevation = if (enabled) 4.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            NexusText(
                text = text,
                color = if (enabled) contentColor else NexusTheme.colors.textSecondary,
                style = NexusTheme.typography.label
            )
        }
    }
}

@Composable
fun NexusIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = NexusTheme.colors.surface,
    elevation: androidx.compose.ui.unit.Dp = 4.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    NexusSurface(
        modifier = modifier.clip(CircleShape).clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        ),
        shape = CircleShape,
        color = containerColor,
        elevation = elevation
    ) {
        Box(
            modifier = Modifier.size(48.dp).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
