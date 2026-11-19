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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.ui.graphics.RectangleShape
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusSurface
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.utils.LocalHazeState
import com.nexus.core.ui.utils.hazeGlassBackground
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(blendedBg)
    ) {
        Spacer(Modifier.statusBarsPadding())
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
}

@Composable
fun NexusTopBar(
    title: String,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NexusTheme.colors.surface)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                }
                NexusText(
                    text     = title,
                    style    = titleStyle ?: NexusTheme.typography.title,
                    color    = NexusTheme.colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .basicMarquee()
                )
                actions()
            }
        }
        // Hairline bottom divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun NexusPillTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    outerVerticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
    innerVerticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
    iconSize: androidx.compose.ui.unit.Dp = 36.dp,
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
                    style = titleStyle ?: NexusTheme.typography.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                actions()
            }
        }
    }
}

/**
 * A floating circular action button designed with subtle shadows, thin borders,
 * theme-adaptive glassmorphism background, and spring bounce micro-interactions.
 * Ideal for document viewer toolbars (Back, Search, Rotate, More options).
 */
@Composable
fun NexusFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    elevation: Dp = 3.dp,
    borderWidth: Dp = 0.8.dp,
    hazeState: HazeState? = null,
    isActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val activeBg = if (isActive) {
        NexusTheme.colors.primary.copy(alpha = if (isDark) 0.22f else 0.14f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .size(size)
            .viewerSolidSurface(shape = CircleShape, elevation = elevation)
            .background(activeBg, CircleShape)
            .springBounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Applies a clean, simple solid surface background with subtle floating shadow and hairline border.
 */
fun Modifier.viewerSolidSurface(
    shape: Shape = CircleShape,
    elevation: Dp = 3.dp
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val surfaceColor = NexusTheme.colors.surface
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        NexusTheme.colors.divider.copy(alpha = 0.70f)
    }
    val ambientShadow = Color.Black.copy(alpha = if (isDark) 0.35f else 0.06f)
    val spotShadow = Color.Black.copy(alpha = if (isDark) 0.25f else 0.08f)

    this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            spotColor = spotShadow,
            ambientColor = ambientShadow
        )
        .clip(shape)
        .background(surfaceColor, shape)
        .border(width = 0.8.dp, color = borderColor, shape = shape)
}

/**
 * Backward-compatible alias for [viewerSolidSurface], ensuring all callers render clean solid styling.
 */
fun Modifier.viewerLiquidGlass(
    hazeState: HazeState? = null,
    shape: Shape = CircleShape,
    elevation: Dp = 3.dp
): Modifier = viewerSolidSurface(shape = shape, elevation = elevation)

/**
 * Action button designed specifically for the shared action pill in document viewers.
 */
@Composable
fun NexusViewerActionButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    tint: Color = NexusTheme.colors.textPrimary,
    activeTint: Color = NexusTheme.colors.primary
) {
    val isDark = isSystemInDarkTheme()
    val activeBg = if (isActive) {
        NexusTheme.colors.primary.copy(alpha = if (isDark) 0.22f else 0.14f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(activeBg)
            .springBounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(if (isActive) activeTint else tint)
        )
    }
}

/**
 * Subtle vertical divider line separating action buttons inside the shared action pill.
 */
@Composable
fun NexusViewerActionDivider(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val dividerColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        NexusTheme.colors.divider.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .width(1.dp)
            .height(18.dp)
            .background(dividerColor, CircleShape)
    )
}

/**
 * A floating circular icon button with subtle shadows and thin borders.
 */
@Composable
fun NexusFloatingIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    tint: Color = NexusTheme.colors.textPrimary,
    hazeState: HazeState? = null,
    isActive: Boolean = false,
    activeTint: Color = NexusTheme.colors.primary,
    elevation: Dp = 4.dp,
    borderWidth: Dp = 0.75.dp,
    standalone: Boolean = false
) {
    if (standalone) {
        NexusFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            size = size,
            elevation = elevation,
            borderWidth = borderWidth,
            hazeState = hazeState,
            isActive = isActive
        ) {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(if (isActive) activeTint else tint)
            )
        }
    } else {
        NexusViewerActionButton(
            painter = painter,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            isActive = isActive,
            size = size,
            iconSize = iconSize,
            tint = tint,
            activeTint = activeTint
        )
    }
}

/**
 * Modern floating solid top bar for document viewers (PDF, Office, Presentation, Text).
 *
 * Structures the top bar into 3 balanced, clean floating components with solid surface colors:
 * 1. Back button (floating solid circle)
 * 2. Title section (expanded, prominent floating solid pill)
 * 3. Combined actions section (shared single floating solid pill holding Search and More)
 */
@Composable
fun NexusViewerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    onTitleClick: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Back navigation button: circular floating solid button
        if (navigationIcon != null) {
            navigationIcon()
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .viewerSolidSurface(shape = CircleShape)
                    .springBounceClick(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.nexus.core.R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(NexusTheme.colors.textPrimary)
                )
            }
        }

        // 2. Title section: prominent expanded floating solid pill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .viewerSolidSurface(shape = CircleShape)
                .then(
                    if (onTitleClick != null) {
                        Modifier.springBounceClick(onClick = onTitleClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            NexusText(
                text = title,
                style = titleStyle ?: NexusTheme.typography.body.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee()
                    .then(titleModifier)
            )
        }

        // 3. Actions section: single shared solid pill capsule
        Box(
            modifier = Modifier
                .height(44.dp)
                .viewerSolidSurface(shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                actions()
            }
        }
    }
}

