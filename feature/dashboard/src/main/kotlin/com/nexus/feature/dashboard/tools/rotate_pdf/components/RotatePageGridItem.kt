package com.nexus.feature.dashboard.tools.rotate_pdf.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.rotate_pdf.RotatePageItem

/**
 * Grid card item representing a single PDF page with animated rotation,
 * degree indicator badge, selection checkmark, and quick rotate action buttons.
 */
@Composable
fun RotatePageGridItem(
    item: RotatePageItem,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0A0A0A)
    val lineDefault = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)

    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else lineDefault,
        animationSpec = tween(180),
        label = "cardBorderColor"
    )

    val animatedRotation by animateFloatAsState(
        targetValue = item.rotationDelta.toFloat(),
        animationSpec = tween(320),
        label = "pageRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 4.dp else 1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFFFAFAFA) else NexusTheme.colors.surface)
            .border(2.dp, cardBorderColor, RoundedCornerShape(16.dp))
            .clickable { onToggleSelect() }
            .padding(10.dp)
    ) {
        // ── Square Thumbnail Viewport with Stacked Badges ────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.0f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
                .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Animated Rotated Page Thumbnail
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = "Page ${item.pageNumber}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .graphicsLayer {
                            rotationZ = animatedRotation
                        }
                        .shadow(2.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF0A0A0A),
                    strokeWidth = 2.dp
                )
            }

            // Checkmark Selection Badge at top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else Color(0xFF0A0A0A).copy(alpha = 0.28f))
                    .border(1.8.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Page Label & Degree Badge Row ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusText(
                text = "Page ${item.pageNumber}",
                style = NexusTheme.typography.body.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = NexusTheme.colors.textPrimary
            )

            // Degree Badge Pill when turned
            if (item.isTurned) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF0A0A0A))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    NexusText(
                        text = "${item.rotationDelta}°",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Bottom Per-Page Rotation Tool Buttons ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Rotate Left
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.2.dp, lineStrong, RoundedCornerShape(8.dp))
                    .clickable { onRotateLeft() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.RotateLeft,
                    contentDescription = "Rotate page ${item.pageNumber} left",
                    tint = NexusTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Rotate Right
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.2.dp, lineStrong, RoundedCornerShape(8.dp))
                    .clickable { onRotateRight() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                    contentDescription = "Rotate page ${item.pageNumber} right",
                    tint = NexusTheme.colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

