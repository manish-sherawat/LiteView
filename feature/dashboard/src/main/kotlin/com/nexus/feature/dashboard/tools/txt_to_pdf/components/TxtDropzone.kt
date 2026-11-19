package com.nexus.feature.dashboard.tools.txt_to_pdf.components

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
 * Interactive dropzone matching the reference specification with stacked document illustration.
 */
@Composable
fun TxtDropzone(
    onPickFile: () -> Unit,
    onTypeOrPaste: () -> Unit,
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
        // ── Illustration: Stacked 3 Documents with TXT Badge ────────────────
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(115.dp)
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left sheet (d1: -8 deg)
            DocumentIllustrationCard(
                modifier = Modifier
                    .offset(x = (-30).dp, y = 8.dp)
                    .rotate(-8f),
                borderColor = Color(0xFFCFCFCF),
                bgColor = Color(0xFFFAFAFA)
            )

            // Right sheet (d3: +8 deg)
            DocumentIllustrationCard(
                modifier = Modifier
                    .offset(x = 30.dp, y = 8.dp)
                    .rotate(8f),
                borderColor = Color(0xFFCFCFCF),
                bgColor = Color(0xFFFAFAFA)
            )

            // Center sheet (d2: primary)
            DocumentIllustrationCard(
                modifier = Modifier.offset(y = (-4).dp),
                borderColor = Color(0xFF0A0A0A),
                bgColor = Color.White
            )

            // TXT Pill Tag Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 8.dp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                NexusText(
                    text = "TXT",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Drop title & hint ────────────────────────────────────────────────
        NexusText(
            text = "Select your text file",
            style = NexusTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            ),
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        NexusText(
            text = "or browse files from your device",
            style = NexusTheme.typography.body.copy(
                fontSize = 14.sp,
                color = mutedColor
            )
        )

        Spacer(modifier = Modifier.height(22.dp))

        // ── Primary Action Button ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A0A0A))
                .springBounceClick(onClick = onPickFile)
                .padding(horizontal = 26.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                NexusText(
                    text = "Choose file",
                    style = NexusTheme.typography.buttonLabel.copy(
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── "or type or paste text instead" Link ─────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            NexusText(
                text = "or ",
                style = NexusTheme.typography.body.copy(fontSize = 13.5.sp, color = mutedColor)
            )
            NexusText(
                text = "type or paste text instead",
                style = NexusTheme.typography.body.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A0A0A)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onTypeOrPaste() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Error message feedback
        AnimatedVisibility(
            visible = !errorMessage.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                NexusText(
                    text = it,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFDC2626)
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Footnote ─────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            NexusText(
                text = ".txt, .md, .csv, .log and more",
                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, color = faintColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCFCFCF))
            )
            Spacer(modifier = Modifier.width(6.dp))
            NexusText(
                text = "No upload, no sign-up",
                style = NexusTheme.typography.caption.copy(fontSize = 11.5.sp, color = faintColor)
            )
        }
    }
}

/**
 * Miniature document card for illustration.
 */
@Composable
private fun DocumentIllustrationCard(
    borderColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 62.dp, height = 78.dp)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFFCFCFCF))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFFEBEBEB))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFFEBEBEB))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFFEBEBEB))
            )
        }
    }
}

