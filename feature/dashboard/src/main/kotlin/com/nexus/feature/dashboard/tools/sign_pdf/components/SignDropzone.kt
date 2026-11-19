package com.nexus.feature.dashboard.tools.sign_pdf.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
fun SignDropzone(
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
            // ── Stacked Signature Paper Illustration ─────────────────────────
            StackedSignPaperIllustration()

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
private fun StackedSignPaperIllustration() {
    val paperBorder = Color(0xFFCFCFCF)
    val accentColor = NexusTheme.colors.textPrimary
    val lineMuted = Color(0xFFCFCFCF)
    val lineSoft = Color(0xFFEBEBEB)

    Box(
        modifier = Modifier
            .size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Paper 1: Left Rotated (-12 deg, shifted -14dp) ───────────────────
        Box(
            modifier = Modifier
                .offset(x = (-14).dp)
                .graphicsLayer { rotationZ = -12f }
                .size(width = 76.dp, height = 98.dp)
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.5.dp, paperBorder, RoundedCornerShape(10.dp))
        )

        // ── Paper 2: Right Rotated (+10 deg, shifted +14dp) ──────────────────
        Box(
            modifier = Modifier
                .offset(x = 14.dp)
                .graphicsLayer { rotationZ = 10f }
                .size(width = 76.dp, height = 98.dp)
                .shadow(3.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF5F5F5))
                .border(1.5.dp, paperBorder, RoundedCornerShape(10.dp))
        )

        // ── Paper 3: Center Front Paper with Document Lines & Signature ──────
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 98.dp)
                .shadow(6.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.5.dp, accentColor, RoundedCornerShape(10.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top document lines
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lineMuted)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lineSoft)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lineSoft)
                    )
                }

                // Bottom signature scribble path:
                // M2 18c6-14 10-14 8-4s4 8 8-2 6 4 10-4 6 8 14 2 8-2 12-6
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                ) {
                    val scaleX = size.width / 60f
                    val scaleY = size.height / 26f

                    val path = Path().apply {
                        moveTo(2f * scaleX, 18f * scaleY)
                        cubicTo(
                            8f * scaleX, 4f * scaleY,
                            12f * scaleX, 4f * scaleY,
                            10f * scaleX, 14f * scaleY
                        )
                        cubicTo(
                            14f * scaleX, 22f * scaleY,
                            18f * scaleX, 12f * scaleY,
                            18f * scaleX, 12f * scaleY
                        )
                        cubicTo(
                            24f * scaleX, 16f * scaleY,
                            28f * scaleX, 8f * scaleY,
                            28f * scaleX, 8f * scaleY
                        )
                        cubicTo(
                            34f * scaleX, 16f * scaleY,
                            42f * scaleX, 10f * scaleY,
                            42f * scaleX, 10f * scaleY
                        )
                        cubicTo(
                            50f * scaleX, 8f * scaleY,
                            54f * scaleX, 4f * scaleY,
                            54f * scaleX, 4f * scaleY
                        )
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF0A0A0A),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        // ── Circular Plus Badge on Paper 3 Bottom-Right ───────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 38.dp, y = 44.dp)
                .size(34.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(accentColor)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
            )
        }
    }
}
