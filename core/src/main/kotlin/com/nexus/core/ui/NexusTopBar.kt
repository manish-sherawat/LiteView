package com.nexus.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme

@Composable
fun NexusTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    outerVerticalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    innerVerticalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    iconSize: androidx.compose.ui.unit.Dp = 48.dp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = outerVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null) {
            NexusSurface(
                modifier = Modifier.size(iconSize),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = NexusTheme.colors.surfaceVariant,
                elevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    navigationIcon()
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        NexusSurface(
            modifier = Modifier.weight(1f),
            shape = NexusTheme.shapes.pill,
            color = NexusTheme.colors.surfaceVariant,
            elevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = innerVerticalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexusText(
                    text = title,
                    style = titleStyle ?: NexusTheme.typography.h2,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                actions()
            }
        }
    }
}
