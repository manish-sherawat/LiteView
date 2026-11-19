package com.nexus.feature.dashboard.tools.watermark_pdf.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick

@Composable
fun WatermarkDropzone(
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = NexusTheme.colors.divider.copy(alpha = 0.7f)
    val cornerRadiusPx = 24.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 18f), 0f)
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx.toPx(), cornerRadiusPx.toPx())
                )
            }
            .clickable { onPickFile() }
            .padding(horizontal = 24.dp, vertical = 42.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Stacked Paper Illustration ────────────────────────────────────
            StackedPaperIllustration()

            Spacer(modifier = Modifier.height(24.dp))

            // ── Title & Subtitle ──────────────────────────────────────────────
            NexusText(
                text = "Drop your PDF here",
                style = NexusTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = NexusTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            NexusText(
                text = "or browse files from your device",
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.5.sp
                ),
                color = NexusTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Choose File Button ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusTheme.colors.textPrimary)
                    .springBounceClick { onPickFile() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = NexusTheme.colors.background
                )
                Spacer(modifier = Modifier.width(8.dp))
                NexusText(
                    text = "Choose file",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.background
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Footnote: PDF only & Offline Privacy ─────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NexusText(
                    text = "PDF files only",
                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                    color = NexusTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(3.5.dp)
                        .background(NexusTheme.colors.textSecondary.copy(alpha = 0.5f), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                NexusText(
                    text = "No upload, 100% offline",
                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                    color = NexusTheme.colors.textSecondary
                )
            }
        }
    }
}

/**
 * 3-Paper Stacked Illustration with an accent upload badge matching the HTML design.
 */
@Composable
private fun StackedPaperIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowFloat"
    )

    Box(
        modifier = modifier
            .size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Paper 1 (Back left, rotated -12°)
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 96.dp)
                .graphicsLayer {
                    rotationZ = -12f
                    translationX = -14.dp.toPx()
                }
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFFD4D4D4).copy(alpha = 0.5f),
                        style = Stroke(width = 1.2.dp.toPx()),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                }
        )

        // Paper 2 (Back right, rotated 10°)
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 96.dp)
                .graphicsLayer {
                    rotationZ = 10f
                    translationX = 14.dp.toPx()
                }
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.85f))
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFFC4C4C4).copy(alpha = 0.6f),
                        style = Stroke(width = 1.2.dp.toPx()),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                }
        )

        // Paper 3 (Front center with mock lines and arrow badge)
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 98.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(NexusTheme.colors.surface)
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFF888888).copy(alpha = 0.5f),
                        style = Stroke(width = 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                }
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top header line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(NexusTheme.colors.divider.copy(alpha = 0.8f))
                )
                // Subline 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NexusTheme.colors.divider.copy(alpha = 0.45f))
                )
                // Subline 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(NexusTheme.colors.divider.copy(alpha = 0.45f))
                )
            }
        }

        // Circular Upload Arrow Badge (Bottom Right)
        Box(
            modifier = Modifier
                .size(34.dp)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    translationX = 4.dp.toPx()
                    translationY = floatOffset.dp.toPx()
                }
                .shadow(elevation = 8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(NexusTheme.colors.textPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = NexusTheme.colors.background
            )
        }
    }
}
