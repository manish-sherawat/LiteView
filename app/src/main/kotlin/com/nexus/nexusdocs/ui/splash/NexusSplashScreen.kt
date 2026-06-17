package com.nexus.nexusdocs.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.ui.NexusText
import kotlinx.coroutines.delay

@Composable
fun NexusSplashScreen(onSplashComplete: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    
    // Sleek Minimalist Colors
    val bgColor = if (isDark) Color(0xFF0C0C0C) else Color(0xFFF4F5F7)
    val textColor = if (isDark) Color(0xFFFAFAFA) else Color(0xFF111827)
    val docColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val accentColor = Color(0xFF2563EB) // Royal Blue accent
    
    val enterAnim = remember { Animatable(0f) }
    val scanAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Logo pops in
        enterAnim.animateTo(1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f))
        // Scanning sweeps down multiple times
        scanAnim.animateTo(1f, animationSpec = tween(1200, easing = LinearEasing))
        scanAnim.snapTo(0f)
        scanAnim.animateTo(1f, animationSpec = tween(1200, easing = LinearEasing))
        delay(200)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val scale = 0.5f + (enterAnim.value * 0.5f)
                scaleX = scale
                scaleY = scale
                alpha = enterAnim.value
            }
        ) {
            // Document Scanner Graphic
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 150.dp)
                    .shadow(if (isDark) 4.dp else 24.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.15f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(docColor),
                contentAlignment = Alignment.TopCenter
            ) {
                // Lines of text inside document
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val lineColor = if (isDark) Color(0xFF333333) else Color(0xFFE5E7EB)
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).background(lineColor, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(lineColor, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(lineColor, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).background(lineColor, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(6.dp).background(lineColor, RoundedCornerShape(3.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(6.dp).background(lineColor, RoundedCornerShape(3.dp)))
                }

                // Scanning Laser Line
                if (scanAnim.value > 0f) {
                    val scanY = scanAnim.value * 150.dp.value
                    
                    // The glowing aura of the scanner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp) 
                            .graphicsLayer {
                                translationY = scanY.dp.toPx() - 30.dp.toPx()
                            }
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        accentColor.copy(alpha = 0.1f),
                                        accentColor.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    // The bright core laser line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .graphicsLayer {
                                translationY = scanY.dp.toPx()
                            }
                            .background(accentColor)
                            .shadow(8.dp, spotColor = accentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Minimalist Typography
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexusText(
                    text = "Nexus",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = textColor
                )
                NexusText(
                    text = "Docs",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    ),
                    color = accentColor
                )
            }
        }
        
        // Very subtle loading text at bottom
        val dots = when {
            scanAnim.value < 0.33f -> "."
            scanAnim.value < 0.66f -> ".."
            else -> "..."
        }
        NexusText(
            text = "Preparing Workspace$dots",
            color = if (isDark) Color(0xFF666666) else Color(0xFFAAAAAA),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .graphicsLayer { alpha = enterAnim.value }
        )
    }
}
