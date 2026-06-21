package com.nexus.nexusdocs.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.ui.NexusText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NexusSplashScreen(onSplashComplete: () -> Unit) {
    val isDark = isSystemInDarkTheme()

    // ── Colors exactly matching NexusTheme tokens ─────────────────────────────
    val bgColor = com.nexus.core.theme.NexusTheme.colors.background
    val surfaceColor = com.nexus.core.theme.NexusTheme.colors.surface
    val surfaceVariant = com.nexus.core.theme.NexusTheme.colors.surfaceVariant
    val primaryColor = com.nexus.core.theme.NexusTheme.colors.primary
    val textPrimary = com.nexus.core.theme.NexusTheme.colors.textPrimary
    val textSecondary = com.nexus.core.theme.NexusTheme.colors.textSecondary
    val accentBlue = Color(0xFF2563EB) // universal accent

    // ── Animation state ──────────────────────────────────────────────────────
    // Phase 1: Logo drops from above
    val logoTranslationY = remember { Animatable(-400f) }
    val logoScale        = remember { Animatable(0.55f) }
    val logoAlpha        = remember { Animatable(0f) }

    // Phase 2: Accent dot/corner wiggle (secondary physics)
    val cornerRotation   = remember { Animatable(0f) }
    val dotScale         = remember { Animatable(0f) }

    // Phase 3: Wink — small highlight flashes off/on
    val winkScaleY       = remember { Animatable(1f) }
    val winkAlpha        = remember { Animatable(1f) }

    // Text drifts up behind logo
    val textTranslationY = remember { Animatable(24f) }
    val textAlpha        = remember { Animatable(0f) }

    // Tagline
    val taglineAlpha     = remember { Animatable(0f) }

    // Phase 4: Exit — whole composition scales+fades out
    val exitScale        = remember { Animatable(1f) }
    val exitAlpha        = remember { Animatable(1f) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // ── PHASE 1: Logo drops in with spring bounce (~700ms) ───────────────
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(180, easing = LinearEasing))
        }
        launch {
            logoTranslationY.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.52f, stiffness = 370f)
            )
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 400f)
            )
        }

        // Text drifts up gently while logo settles
        launch {
            delay(200)
            launch {
                textTranslationY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 260f)
                )
            }
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(420, easing = FastOutSlowInEasing)
            )
        }

        // Wait for logo to mostly settle
        delay(460)

        // ── PHASE 2: Secondary physics — corner fold wiggle + dot pop ────────
        launch {
            // Accent corner rotates then snaps back
            cornerRotation.animateTo(
                targetValue = 22f,
                animationSpec = spring(dampingRatio = 0.28f, stiffness = 190f)
            )
            cornerRotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 280f)
            )
        }
        launch {
            // Accent dot pops in with overshoot
            dotScale.animateTo(
                targetValue = 1.25f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 350f)
            )
            dotScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)
            )
        }

        // Tagline fades in after icon settles
        launch {
            delay(180)
            taglineAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(380, easing = FastOutSlowInEasing)
            )
        }

        // Wait for secondary animations
        delay(680)

        // ── PHASE 3: Wink — icon highlight blinks off then back ─────────────
        // The accent dot collapses and re-expands (like a heartbeat)
        launch {
            winkScaleY.animateTo(0f, animationSpec = tween(110, easing = FastOutLinearInEasing))
            winkScaleY.animateTo(1f, animationSpec = tween(200, easing = LinearOutSlowInEasing))
        }
        launch {
            winkAlpha.animateTo(0f, animationSpec = tween(90, easing = LinearEasing))
            delay(110)
            winkAlpha.animateTo(1f, animationSpec = tween(220, easing = LinearEasing))
        }
        delay(350)

        // ── HOLD: Breathe moment before exit ────────────────────────────────
        delay(100)

        // ── PHASE 4: Exit — scale up + fade out ─────────────────────────────
        launch {
            exitScale.animateTo(
                targetValue = 1.09f,
                animationSpec = tween(420, easing = FastOutSlowInEasing)
            )
        }
        exitAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(370, easing = LinearEasing)
        )

        onSplashComplete()
    }

    // ── ROOT: full-screen, exit transform applied at top level ────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = exitScale.value
                scaleY = exitScale.value
                alpha  = exitAlpha.value
            }
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {

        // ── ICON + WORDMARK stack ─────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.graphicsLayer {
                translationY = logoTranslationY.value
                scaleX = logoScale.value
                scaleY = logoScale.value
                alpha  = logoAlpha.value
            }
        ) {
            // ── ICON CONTAINER ───────────────────────────────────────────────
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Shadow glow under icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .shadow(
                            elevation = if (isDark) 32.dp else 18.dp,
                            shape = RoundedCornerShape(24.dp),
                            spotColor = primaryColor.copy(alpha = if (isDark) 0.35f else 0.14f)
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(surfaceColor),
                    contentAlignment = Alignment.Center
                ) {
                    // ── DOCUMENT SHAPE ───────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 66.dp)
                    ) {
                        // Document body
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 2.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(surfaceVariant)
                        )

                        // Folded corner — animates with cornerRotation
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.TopEnd)
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(1f, 0f)
                                    rotationZ = cornerRotation.value
                                }
                                .clip(RoundedCornerShape(bottomStart = 4.dp))
                                .background(primaryColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                        )

                        // Triangle fold indicator
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.TopEnd)
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(1f, 0f)
                                    rotationZ = cornerRotation.value
                                }
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawPath(
                                    path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(size.width, 0f)
                                        lineTo(size.width, size.height)
                                        lineTo(0f, 0f)
                                        close()
                                    },
                                    color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF1F1F3)
                                )
                            }
                        }

                        // Document text lines
                        Column(
                            modifier = Modifier
                                .padding(start = 8.dp, top = 22.dp, end = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val lineColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE5E7EB)
                            repeat(4) { i ->
                                val widthFrac = when (i) {
                                    0 -> 0.75f
                                    1 -> 1.00f
                                    2 -> 0.90f
                                    else -> 0.55f
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(widthFrac)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(lineColor)
                                )
                            }
                        }
                    }

                    // ── ACCENT DOT (wink element) — bottom-right of icon box ─
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = dotScale.value
                                    scaleY = dotScale.value * winkScaleY.value
                                    alpha  = winkAlpha.value
                                }
                                .shadow(6.dp, CircleShape, spotColor = accentBlue)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accentBlue,
                                            accentBlue.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── WORDMARK ─────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer {
                    translationY = textTranslationY.value
                    alpha = textAlpha.value
                }
            ) {
                NexusText(
                    text = "Lite",
                    color = textPrimary,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                )
                NexusText(
                    text = "View",
                    color = primaryColor.copy(alpha = 0.45f),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
        }

        // ── TAGLINE — bottom of screen ────────────────────────────────────────
        NexusText(
            text = "Open anything",
            color = textSecondary.copy(alpha = 0.65f),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp)
                .graphicsLayer { alpha = taglineAlpha.value }
        )
    }
}
