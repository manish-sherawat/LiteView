package com.nexus.feature.dashboard.tools.reorder_pdf.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
fun ReorderPageRow(
    pageNumber: Int,
    currentIndex: Int,
    totalCount: Int,
    thumbnail: Bitmap?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMoved = pageNumber != currentIndex + 1
    val borderColor = Color(0xFFEBEBEB)
    val accentColor = Color(0xFF0A0A0A)
    val lineStrong = Color(0xFFCFCFCF)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .drawBehind {
                if (isMoved) {
                    // 4dp left border accent when moved
                    drawLine(
                        color = accentColor,
                        start = Offset(2.dp.toPx(), 0f),
                        end = Offset(2.dp.toPx(), size.height),
                        strokeWidth = 4.dp.toPx()
                    )
                }
            }
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Grip Icon (6 dots) ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(18.dp, 24.dp)) {
                val dotRadius = 1.6.dp.toPx()
                val col1 = size.width * 0.3f
                val col2 = size.width * 0.7f
                val row1 = size.height * 0.22f
                val row2 = size.height * 0.5f
                val row3 = size.height * 0.78f

                drawCircle(Color(0xFFA3A3A3), dotRadius, Offset(col1, row1))
                drawCircle(Color(0xFFA3A3A3), dotRadius, Offset(col2, row1))
                drawCircle(Color(0xFFA3A3A3), dotRadius, Offset(col1, row2))
                drawCircle(Color(0xFFA3A3A3), dotRadius, Offset(col2, row2))
                drawCircle(Color(0xFFA3A3A3), dotRadius, Offset(col1, row3))
                drawCircle(Color(0xFFA3A3A3), dotRadius, Offset(col2, row3))
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // ── Page Thumbnail with Eye Preview Overlay ──────────────────────────
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 86.dp)
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F5F5))
                .border(1.5.dp, lineStrong, RoundedCornerShape(10.dp))
                .clickable { onPreview() },
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
                // Shimmer Skeleton
                ShimmerSkeleton()
            }

            // Eye preview icon badge at bottom-right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xEEFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = "Preview",
                    modifier = Modifier.size(13.dp),
                    tint = Color(0xFF0A0A0A)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ── Page Info Column ─────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            NexusText(
                text = "Page $pageNumber",
                style = NexusTheme.typography.body.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = Color(0xFF0A0A0A)
            )

            Spacer(modifier = Modifier.height(2.dp))

            NexusText(
                text = if (!isMoved) "Original position" else "Now at position ${currentIndex + 1}",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color(0xFF707070)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ── Chevron Up and Down Action Buttons ───────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Up Chevron
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = currentIndex > 0) { onMoveUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Move page up",
                    modifier = Modifier.size(22.dp),
                    tint = if (currentIndex > 0) Color(0xFF0A0A0A) else lineStrong
                )
            }

            // Down Chevron
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = currentIndex < totalCount - 1) { onMoveDown() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Move page down",
                    modifier = Modifier.size(22.dp),
                    tint = if (currentIndex < totalCount - 1) Color(0xFF0A0A0A) else lineStrong
                )
            }
        }
    }
}

@Composable
private fun ShimmerSkeleton() {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
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
