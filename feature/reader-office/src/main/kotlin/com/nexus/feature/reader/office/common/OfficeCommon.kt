package com.nexus.feature.reader.office.common

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.NexusTextField
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.core.ui.components.NexusButton

class ImmersiveToggleInterface(
    private val onToggle: () -> Unit
) {
    private var onPageChanged: ((Int, Int) -> Unit)? = null

    constructor(
        onToggle: () -> Unit,
        onPageChanged: ((Int, Int) -> Unit)?
    ) : this(onToggle) {
        this.onPageChanged = onPageChanged
    }

    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun toggle() {
        handler.post { onToggle() }
    }

    @JavascriptInterface
    fun onPageChanged(page: Int, total: Int) {
        handler.post { onPageChanged?.invoke(page, total) }
    }
}

@Composable
fun OfficeBottomBarActionItem(
    label: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconPainter: Painter? = null,
    isActive: Boolean = false,
    activeBadgeColor: Color = NexusTheme.colors.primary.copy(alpha = 0.14f),
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val animatedBadgeColor by animateColorAsState(
        targetValue = if (isActive) activeBadgeColor else Color.Transparent,
        animationSpec = tween(200),
        label = "officeBadgeColor"
    )
    val animatedTint by animateColorAsState(
        targetValue = if (isActive) tint else if (!enabled) NexusTheme.colors.textSecondary.copy(alpha = 0.35f) else NexusTheme.colors.textPrimary.copy(alpha = 0.85f),
        animationSpec = tween(200),
        label = "officeIconTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(NexusTheme.shapes.medium)
            .springBounceClick(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 2.dp, vertical = NexusTheme.spacing.xxs)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(animatedBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = contentDescription,
                    colorFilter = ColorFilter.tint(animatedTint),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        NexusText(
            text = label,
            style = NexusTheme.typography.caption.copy(
                fontSize = 10.5.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.15.sp
            ),
            color = animatedTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun OfficePasswordPromptCard(
    isError: Boolean,
    onCancel: () -> Unit,
    onUnlock: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NexusTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        NexusSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = NexusTheme.shapes.large,
            elevation = 12.dp,
            borderWidth = 1.dp,
            borderColor = NexusTheme.colors.divider.copy(alpha = 0.5f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NexusTheme.spacing.xl)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.nexus.core.R.drawable.ic_lock),
                        contentDescription = "Password protected",
                        tint = NexusTheme.colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NexusText(
                    text = "Password Protected",
                    style = NexusTheme.typography.titleLarge,
                    color = NexusTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                NexusText(
                    text = "This document requires a password to open.",
                    style = NexusTheme.typography.body,
                    color = NexusTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                NexusTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Enter document password...",
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = com.nexus.core.R.drawable.ic_lock),
                            contentDescription = "Password lock",
                            tint = NexusTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NexusText(
                        text = "Incorrect password. Please try again.",
                        color = NexusTheme.colors.error,
                        style = NexusTheme.typography.caption,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexusTheme.spacing.sm)
                ) {
                    NexusButton(
                        text = "Cancel",
                        onClick = onCancel,
                        isOutlined = true,
                        modifier = Modifier.weight(1f)
                    )
                    NexusButton(
                        text = "Open",
                        enabled = password.isNotEmpty(),
                        onClick = {
                            if (password.isNotEmpty()) {
                                onUnlock(password)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
