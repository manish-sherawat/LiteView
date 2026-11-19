package com.nexus.feature.dashboard.tools.repair_pdf.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick

/**
 * Dropzone for Repair PDF with stacked documents illustration and repair tool badge.
 */
@Composable
fun RepairDropzone(
    onPickFile: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFC4C4C4)
    val textPrimary = NexusTheme.colors.textPrimary
    val mutedColor = Color(0xFF707070)
    val faintColor = Color(0xFFA3A3A3)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.5.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable { onPickFile() }
            .padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Illustration: Stacked 3 Documents with Repair Wrench Badge ───────
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(115.dp)
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left sheet (m1: -8 deg)
            MiniIllustrationCard(
                modifier = Modifier
                    .offset(x = (-30).dp, y = 8.dp)
                    .rotate(-8f),
                borderColor = Color(0xFFCFCFCF),
                bgColor = Color(0xFFFAFAFA)
            )

            // Right sheet (m3: +8 deg)
            MiniIllustrationCard(
                modifier = Modifier
                    .offset(x = 30.dp, y = 8.dp)
                    .rotate(8f),
                borderColor = Color(0xFFCFCFCF),
                bgColor = Color(0xFFFAFAFA)
            )

            // Center sheet (m2: with primary border)
            MiniIllustrationCard(
                modifier = Modifier.offset(y = (-4).dp),
                borderColor = Color(0xFF0A0A0A),
                bgColor = Color.White
            )

            // Wrench Tick Badge at bottom right of center sheet
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 6.dp)
                    .size(28.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Build,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Dropzone Title & Subtitle ────────────────────────────────────────
        NexusText(
            text = "Drop your damaged PDF here",
            style = NexusTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            ),
            color = textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        NexusText(
            text = "or browse files from your device",
            style = NexusTheme.typography.bodySmall.copy(
                fontSize = 13.5.sp,
                color = mutedColor
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Choose File Button ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .springBounceClick(onClick = onPickFile)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 26.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
                NexusText(
                    text = "Choose file",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White
                )
            }
        }

        // ── Error Message ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = !errorMessage.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            NexusText(
                text = errorMessage.orEmpty(),
                style = NexusTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFFE5484D),
                modifier = Modifier.padding(top = 14.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Bottom Meta Pill ─────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NexusText(
                text = "PDF files only",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 11.5.sp,
                    color = faintColor
                )
            )
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCFCFCF))
            )
            NexusText(
                text = "No upload, no sign-up",
                style = NexusTheme.typography.caption.copy(
                    fontSize = 11.5.sp,
                    color = faintColor
                )
            )
        }
    }
}

/**
 * Miniature document card for stacked illustration.
 */
@Composable
private fun MiniIllustrationCard(
    borderColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 54.dp, height = 70.dp)
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 11.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFCFCFCF))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFEBEBEB))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFEBEBEB))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFEBEBEB))
            )
        }
    }
}

