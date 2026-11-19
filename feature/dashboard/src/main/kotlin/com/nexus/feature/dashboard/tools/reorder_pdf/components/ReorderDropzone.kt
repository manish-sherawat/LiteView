package com.nexus.feature.dashboard.tools.reorder_pdf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
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
fun ReorderDropzone(
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
            .padding(horizontal = 24.dp, vertical = 44.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ── 3 Horizontal Papers Stack Illustration ───────────────────────
            StackedReorderPaperIllustration()

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
                    fontSize = 15.sp
                ),
                color = NexusTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ── Choose File Button ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .springBounceClick { onPickFile() }
                    .clip(RoundedCornerShape(14.dp))
                    .background(NexusTheme.colors.textPrimary)
                    .padding(horizontal = 28.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = NexusTheme.colors.background
                )
                Spacer(modifier = Modifier.width(10.dp))
                NexusText(
                    text = "Choose file",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.background
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                NexusText(
                    text = errorMessage,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NexusTheme.colors.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ── Footer Badge Row ─────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NexusText(
                    text = "PDF files only",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.divider)
                )

                Spacer(modifier = Modifier.width(8.dp))

                NexusText(
                    text = "No upload, no sign-up",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = NexusTheme.colors.textSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StackedReorderPaperIllustration() {
    val paperBorder = Color(0xFFCFCFCF)
    val accentColor = Color(0xFF0A0A0A)
    val lineMuted = Color(0xFFCFCFCF)
    val lineSoft = Color(0xFFEBEBEB)

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Paper 1: Top (top 14%, scale 0.9) ────────────────────────────────
        Box(
            modifier = Modifier
                .offset(y = (-30).dp)
                .graphicsLayer {
                    scaleX = 0.9f
                    scaleY = 0.9f
                }
                .size(width = 70.dp, height = 46.dp)
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.5.dp, paperBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFF5F5F5))
                        .border(1.dp, lineSoft, RoundedCornerShape(3.dp))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(lineSoft)
                )
            }
        }

        // ── Paper 3: Bottom (top 62%, scale 0.9) ─────────────────────────────
        Box(
            modifier = Modifier
                .offset(y = 30.dp)
                .graphicsLayer {
                    scaleX = 0.9f
                    scaleY = 0.9f
                }
                .size(width = 70.dp, height = 46.dp)
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F5F5))
                .border(1.5.dp, paperBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, lineSoft, RoundedCornerShape(3.dp))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(lineSoft)
                )
            }
        }

        // ── Paper 2: Center with Downward Badge (width 82dp, border accent) ──
        Box(
            modifier = Modifier
                .size(width = 82.dp, height = 46.dp)
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.5.dp, accentColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFF5F5F5))
                        .border(1.dp, lineSoft, RoundedCornerShape(3.dp))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(lineMuted)
                )
            }
        }

        // ── Circular Downward Badge on Paper 2 Right ─────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 42.dp)
                .size(34.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(accentColor)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = Color.White
            )
        }
    }
}
