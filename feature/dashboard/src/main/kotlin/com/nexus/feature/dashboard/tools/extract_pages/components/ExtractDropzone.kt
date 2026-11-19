package com.nexus.feature.dashboard.tools.extract_pages.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
fun ExtractDropzone(
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
            // ── 5 Mini Pages Illustration with Check Tick ────────────────────
            StackedExtractPagesIllustration()

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
private fun StackedExtractPagesIllustration() {
    val paperBorder = Color(0xFFCFCFCF)
    val accentColor = Color(0xFF0A0A0A)
    val lineMuted = Color(0xFFCFCFCF)
    val lineSoft = Color(0xFFEBEBEB)

    Box(
        modifier = Modifier.size(width = 132.dp, height = 120.dp)
    ) {
        // Mini Page Helper Composable
        @Composable
        fun MiniPaper(
            modifier: Modifier = Modifier,
            bgColor: Color = Color.White,
            borderColor: Color = paperBorder
        ) {
            Box(
                modifier = modifier
                    .size(width = 50.dp, height = 66.dp)
                    .shadow(3.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lineMuted)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lineSoft)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lineSoft)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(lineSoft)
                    )
                }
            }
        }

        // m1: Left top rotated -8 deg
        MiniPaper(
            modifier = Modifier
                .offset(x = 4.dp, y = 8.dp)
                .graphicsLayer { rotationZ = -8f },
            bgColor = Color(0xFFFAFAFA)
        )

        // m3: Right top rotated +8 deg
        MiniPaper(
            modifier = Modifier
                .offset(x = 78.dp, y = 8.dp)
                .graphicsLayer { rotationZ = 8f },
            bgColor = Color(0xFFFAFAFA)
        )

        // m4: Left bottom rotated -4 deg
        MiniPaper(
            modifier = Modifier
                .offset(x = 22.dp, y = 52.dp)
                .graphicsLayer { rotationZ = -4f },
            bgColor = Color(0xFFF5F5F5)
        )

        // m5: Right bottom rotated +4 deg
        MiniPaper(
            modifier = Modifier
                .offset(x = 60.dp, y = 52.dp)
                .graphicsLayer { rotationZ = 4f },
            bgColor = Color(0xFFF5F5F5)
        )

        // m2: Center front with tick badge
        Box(
            modifier = Modifier.offset(x = 41.dp, y = 0.dp)
        ) {
            MiniPaper(
                bgColor = Color.White,
                borderColor = accentColor
            )

            // Tick badge at top-right (right -10, top -10)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(26.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
            }
        }
    }
}
