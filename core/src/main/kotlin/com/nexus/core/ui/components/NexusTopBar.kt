package com.nexus.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusSurface

@Composable
fun NexusCollapsingTopBar(
    title: String,
    subtitle: String? = null,
    scrollProgress: Float,                  
    expandedHeight: Dp = 150.dp,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    expandedContent: (@Composable () -> Unit)? = null
) {
    val progress = scrollProgress.coerceIn(0f, 1f)

    val largeTitleOpacity  = (1.0f - progress * 2.5f).coerceIn(0f, 1f)
    val compactTitleOpacity = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
    val subtitleOpacity = (1.0f - progress * 3f).coerceIn(0f, 1f)

    val collapsedHeight = 64.dp
    val headerHeight = expandedHeight - (expandedHeight - collapsedHeight) * progress

    val bgColor = Color.Transparent
    val collapsedBgColor = NexusTheme.colors.surface.copy(alpha = 0.7f)

    val blendedBg = Color(
        red   = bgColor.red   + (collapsedBgColor.red   - bgColor.red)   * progress,
        green = bgColor.green + (collapsedBgColor.green - bgColor.green) * progress,
        blue  = bgColor.blue  + (collapsedBgColor.blue  - bgColor.blue)  * progress,
        alpha = bgColor.alpha + (collapsedBgColor.alpha - bgColor.alpha) * progress
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(blendedBg)
            .statusBarsPadding()
            .height(headerHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(collapsedHeight)
                .align(Alignment.BottomCenter)
        ) {
            if (navigationIcon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                ) {
                    navigationIcon()
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = if (navigationIcon != null) 56.dp else 16.dp)
                    .alpha(compactTitleOpacity)
            ) {
                NexusText(
                    text = title,
                    style = NexusTheme.typography.title,
                    color = NexusTheme.colors.textPrimary
                )
            }

            if (actions != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }

        if (largeTitleOpacity > 0.01f) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = 100.dp 
                    )
                    .alpha(largeTitleOpacity)
            ) {
                NexusText(
                    text = title,
                    style = NexusTheme.typography.h1,
                    color = NexusTheme.colors.textPrimary
                )
                if (subtitle != null && subtitleOpacity > 0.01f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    NexusText(
                        text = subtitle,
                        style = NexusTheme.typography.body,
                        color = NexusTheme.colors.textSecondary,
                        modifier = Modifier.alpha(subtitleOpacity)
                    )
                }
                if (expandedContent != null) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
fun NexusTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(NexusTheme.colors.surface.copy(alpha = 0.92f))
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp)
        ) {
            if (navigationIcon != null) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    navigationIcon()
                }
            }
            NexusText(
                text = title,
                style = titleStyle ?: NexusTheme.typography.title,
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .basicMarquee()
                    .padding(horizontal = 48.dp)
            )
            if (actions != null) {
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun NexusPillTopBar(
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
                color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f),
                elevation = 16.dp,
                borderWidth = 1.dp,
                borderColor = NexusTheme.colors.textPrimary.copy(alpha = 0.1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    navigationIcon()
                }
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        NexusSurface(
            modifier = Modifier.weight(1f),
            shape = NexusTheme.shapes.pill,
            color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f),
            elevation = 16.dp,
            borderWidth = 1.dp,
            borderColor = NexusTheme.colors.textPrimary.copy(alpha = 0.1f)
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
