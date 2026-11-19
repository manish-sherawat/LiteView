package com.nexus.feature.dashboard.tools.extract_pages.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText

@Composable
fun ExtractPageGridItem(
    pageNumber: Int,
    isSelected: Boolean,
    thumbnail: Bitmap?,
    onToggleSelect: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Color(0xFF0A0A0A) else Color(0xFFEBEBEB)
    val cardBg = if (isSelected) Color(0xFFFAFAFA) else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onToggleSelect() }
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Page Thumbnail Image with Shimmer ────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Page $pageNumber",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    ExtractShimmerSkeleton()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Page Label ───────────────────────────────────────────────────
            NexusText(
                text = "Page $pageNumber",
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                color = Color(0xFF0A0A0A),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
            )
        }

        // ── Top-Left Checkmark Circle Badge ──────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 6.dp)
                .size(26.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF0A0A0A) else Color(0x470A0A0A))
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
            }
        }

        // ── Top-Right Eye Preview Button ─────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = 6.dp)
                .size(30.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xF0FFFFFF))
                .clickable { onPreview() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = "Preview page $pageNumber",
                modifier = Modifier.size(15.dp),
                tint = Color(0xFF0A0A0A)
            )
        }
    }
}

@Composable
private fun ExtractShimmerSkeleton() {
    val transition = rememberInfiniteTransition(label = "extractShimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -200f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val shimmerColors = listOf(
        Color(0xFFF5F5F5),
        Color(0xFFEBEBEB),
        Color(0xFFF5F5F5)
    )

    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateAnim, 0f),
                    end = Offset(translateAnim + 160f, 160f)
                )
            )
    )
}
