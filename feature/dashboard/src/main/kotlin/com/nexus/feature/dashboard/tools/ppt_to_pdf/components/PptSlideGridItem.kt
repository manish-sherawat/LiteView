package com.nexus.feature.dashboard.tools.ppt_to_pdf.components

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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.ppt_to_pdf.PptSlideItem

@Composable
fun PptSlideGridItem(
    item: PptSlideItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0A0A0A)
    val lineDefault = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)

    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else lineDefault,
        animationSpec = tween(150),
        label = "pptCardBorder"
    )

    val cardBgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFAFAFA) else NexusTheme.colors.surface,
        animationSpec = tween(150),
        label = "pptCardBg"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(150),
        label = "pptCheckScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // ── Main Slide Card Button ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (isSelected) 4.dp else 1.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(cardBgColor)
                .border(2.dp, cardBorderColor, RoundedCornerShape(16.dp))
                .clickable { onToggleSelect() }
                .padding(10.dp)
        ) {
            // Slide Thumbnail View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(item.aspectRatio.coerceIn(1.2f, 2.0f))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .border(1.dp, lineDefault, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val thumb = item.thumbnail ?: item.fullBitmap
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "Slide ${item.slideNumber}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "${item.slideNumber}",
                            style = NexusTheme.typography.titleLarge.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Slide Label
            NexusText(
                text = "Slide ${item.slideNumber}",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                ),
                color = NexusTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // ── Top-Left: Selection Check Circle ─────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 18.dp)
                .scale(checkScale)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentColor else Color(0x470A0A0A))
                .border(2.dp, Color.White, CircleShape)
                .clickable { onToggleSelect() },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── Top-Right: Eye Preview Button ────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-18).dp, y = 18.dp)
                .size(30.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xF0FFFFFF))
                .border(1.dp, lineStrong.copy(alpha = 0.5f), CircleShape)
                .springBounceClick { onOpenPreview() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = "Preview slide ${item.slideNumber}",
                tint = Color(0xFF0A0A0A),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

