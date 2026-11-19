package com.nexus.feature.dashboard.tools.ppt_to_pdf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
fun PptDropzone(
    onPickFile: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = NexusTheme.colors.divider.copy(alpha = 0.75f)
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
            .padding(horizontal = 24.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── Stacked 3 Slides Illustration with PDF Tag ──────────────────
            StackedSlidesIllustration()

            Spacer(modifier = Modifier.height(24.dp))

            // ── Title & Subtitle ──────────────────────────────────────────────
            NexusText(
                text = "Drop your PowerPoint here",
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
                    fontSize = 15.sp
                ),
                color = NexusTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Choose File Button ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .springBounceClick { onPickFile() }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0A0A0A))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                NexusText(
                    text = "Choose file",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }

            // ── Error Message ────────────────────────────────────────────────
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                NexusText(
                    text = errorMessage,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFFEF4444),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Footer Badges ────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NexusText(
                    text = ".pptx files only",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.divider)
                )
                Spacer(modifier = Modifier.width(8.dp))
                NexusText(
                    text = "No upload, no sign-up",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Matching the exact HTML slide illustration:
 * .illustration with .slide.s1 (rot -8deg), .slide.s2 (center), .slide.s3 (rot +8deg) and .pdf-tag
 */
@Composable
private fun StackedSlidesIllustration() {
    val lineStrong = Color(0xFFCFCFCF)
    val lineSoft = Color(0xFFEBEBEB)
    val accent = Color(0xFF0A0A0A)

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(115.dp),
        contentAlignment = Alignment.Center
    ) {
        // Slide 1 (Left - tilted -8 degrees)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 6.dp, y = 14.dp)
                .graphicsLayer { rotationZ = -8f }
                .size(width = 84.dp, height = 54.dp)
                .shadow(3.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.5.dp, lineStrong, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.width(28.dp).height(4.dp).background(lineStrong, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(42.dp).height(3.dp).background(lineSoft, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(36.dp).height(3.dp).background(lineSoft, RoundedCornerShape(2.dp)))
            }
        }

        // Slide 3 (Right - tilted +8 degrees)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-6).dp, y = 18.dp)
                .graphicsLayer { rotationZ = 8f }
                .size(width = 84.dp, height = 54.dp)
                .shadow(3.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.5.dp, lineStrong, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.width(28.dp).height(4.dp).background(lineStrong, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(42.dp).height(3.dp).background(lineSoft, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(36.dp).height(3.dp).background(lineSoft, RoundedCornerShape(2.dp)))
            }
        }

        // Slide 2 (Center - foreground)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 2.dp)
                .size(width = 92.dp, height = 60.dp)
                .shadow(6.dp, RoundedCornerShape(9.dp))
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
                .border(1.8.dp, accent, RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.width(32.dp).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(48.dp).height(3.dp).background(lineSoft, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(40.dp).height(3.dp).background(lineSoft, RoundedCornerShape(2.dp)))
            }

            // PDF Tag badge in bottom-right corner of center slide
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 14.dp, y = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent)
                    .padding(horizontal = 8.dp, vertical = 2.5.dp)
            ) {
                NexusText(
                    text = "PDF",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}
