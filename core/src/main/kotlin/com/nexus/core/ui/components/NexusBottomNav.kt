package com.nexus.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nexus.core.ui.animations.springBounceClick
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.preferences.HomeStyle
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.*

data class NexusNavItem(
    val label: String,
    val selectedIconText: String = "",
    val unselectedIconText: String = "",
    val selectedIconRes: Int? = null,
    val unselectedIconRes: Int? = null,
    val route: String,
    val badge: Int = 0
)

@Composable
fun NexusFloatingBottomNav(
    items: List<NexusNavItem>,
    currentRoute: String?,
    onItemSelected: (NexusNavItem) -> Unit,
    homeStyle: HomeStyle,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    val containerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = entrySpring(),
        label = "navContainerAlpha"
    )

    val shadowElevation by animateDpAsState(
        targetValue = if (visible) 10.dp else 0.dp,
        animationSpec = navPillSpring(),
        label = "navShadow"
    )

    val containerBg = NexusTheme.colors.surfaceVariant.copy(alpha = 0.85f)
    val pillShape = NexusTheme.shapes.pill

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 16.dp)
            .graphicsLayer { alpha = containerAlpha },
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Blurred Background
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shadow(
                        elevation = shadowElevation,
                        shape = pillShape,
                        clip = false,
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .clip(pillShape)
                    .background(containerBg.copy(alpha = 0.95f))
                    .border(
                        width = 0.5.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.4f),
                        shape = pillShape
                    )
                    .blur(radius = 16.dp)
            )

            // Foreground Icons
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    NexusBottomNavIcon(
                        item = item,
                        isSelected = currentRoute == item.route,
                        onClick = { onItemSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NexusBottomNavIcon(
    item: NexusNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {


    val iconScale by animateFloatAsState(
        targetValue   = if (isSelected) 1.12f else 1f,
        animationSpec = selectionSpring(),
        label         = "selectedIconScale"
    )

    val highlightAlpha by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DurationMedium1,
            easing         = EmphasizedDecelerateEasing
        ),
        label = "highlightAlpha"
    )

    val highlightSize by animateDpAsState(
        targetValue   = if (isSelected) 24.dp else 0.dp,
        animationSpec = selectionSpring(),
        label         = "highlightSize"
    )

    val iconTint by animateColorAsState(
        targetValue   = if (isSelected)
            NexusTheme.colors.primary
        else
            NexusTheme.colors.textSecondary,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DurationMedium2,
            easing         = EmphasizedDecelerateEasing
        ),
        label = "iconTint"
    )

    val highlightColor = NexusTheme.colors.primary.copy(alpha = highlightAlpha * 0.2f)

    Box(
        modifier = Modifier
            .size(52.dp)
            .springBounceClick(
                enabled = true,
                scaleDown = 0.88f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(highlightSize * 2)
                .clip(CircleShape)
                .background(highlightColor)
        )

        if (item.badge > 0) {
            BadgeDot(count = item.badge)
        }

        if (isSelected && item.selectedIconRes != null) {
            Image(
                painter = painterResource(id = item.selectedIconRes),
                contentDescription = item.label,
                modifier = Modifier.scale(iconScale).size(24.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
        } else if (!isSelected && item.unselectedIconRes != null) {
            Image(
                painter = painterResource(id = item.unselectedIconRes),
                contentDescription = item.label,
                modifier = Modifier.scale(iconScale).size(24.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
        } else {
            NexusText(
                text = if (isSelected) item.selectedIconText else item.unselectedIconText,
                color = iconTint,
                style = NexusTheme.typography.h2,
                modifier = Modifier.scale(iconScale)
            )
        }
    }
}

@Composable
private fun BoxScope.BadgeDot(count: Int) {
    Box(
        modifier = Modifier
            .offset(x = 10.dp, y = (-10).dp)
            .size(if (count > 9) 16.dp else 8.dp)
            .clip(CircleShape)
            .background(NexusTheme.colors.error)
            .align(Alignment.TopEnd),
        contentAlignment = Alignment.Center
    ) {
        if (count > 9) {
            NexusText(
                text = "9+",
                color = Color.White,
                style = NexusTheme.typography.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
fun NexusNormalBottomNav(
    items: List<NexusNavItem>,
    currentRoute: String?,
    onItemSelected: (NexusNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NexusFloatingBottomNav(
        items = items,
        currentRoute = currentRoute,
        onItemSelected = onItemSelected,
        homeStyle = HomeStyle.MINIMAL,
        modifier = modifier
    )
}