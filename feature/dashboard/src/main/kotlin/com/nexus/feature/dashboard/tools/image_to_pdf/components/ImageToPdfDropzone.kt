package com.nexus.feature.dashboard.tools.image_to_pdf.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick

@Composable
fun ImageToPdfDropzone(
    onPickImages: () -> Unit,
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
            .clickable { onPickImages() }
            .padding(horizontal = 24.dp, vertical = 42.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── 3-Tile Image Illustration with Plus Badge ─────────────────────
            StackedImageTilesIllustration()

            Spacer(modifier = Modifier.height(24.dp))

            // ── Title & Subtitle ──────────────────────────────────────────────
            NexusText(
                text = "Drop your images here",
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
                text = "or browse photos from your device",
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.5.sp
                ),
                color = NexusTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Choose Images Button ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusTheme.colors.textPrimary)
                    .springBounceClick { onPickImages() }
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
                    text = "Choose images",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.background
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Footnote: Formats & Privacy ───────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NexusText(
                    text = "JPG, PNG, WEBP and more",
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
                    text = "No upload, no sign-up",
                    style = NexusTheme.typography.caption.copy(fontSize = 12.sp),
                    color = NexusTheme.colors.textSecondary
                )
            }
        }
    }
}

/**
 * 3-Tile Image Illustration matching the HTML design.
 */
@Composable
private fun StackedImageTilesIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tileFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "plusBadgeFloat"
    )

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Tile 1 (Back left, rotated -12°, translated -18px)
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    rotationZ = -12f
                    translationX = -18.dp.toPx()
                }
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.55f))
                .border(1.2.dp, Color(0xFFD4D4D4).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pdf_to_image),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = NexusTheme.colors.textSecondary.copy(alpha = 0.45f)
            )
        }

        // Tile 2 (Back right, rotated 10°, translated 18px)
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    rotationZ = 10f
                    translationX = 18.dp.toPx()
                }
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.85f))
                .border(1.2.dp, Color(0xFFC4C4C4).copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pdf_to_image),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = NexusTheme.colors.textSecondary.copy(alpha = 0.55f)
            )
        }

        // Tile 3 (Center foreground with plus badge)
        Box(
            modifier = Modifier
                .size(78.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(NexusTheme.colors.surface)
                .border(1.5.dp, NexusTheme.colors.textPrimary, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pdf_to_image),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = NexusTheme.colors.textPrimary
            )
        }

        // Plus Badge (Bottom Right)
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
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = NexusTheme.colors.background
            )
        }
    }
}
