package com.nexus.feature.dashboard.tools.xlsx_to_pdf.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText

/**
 * Bottom floating bar showing selected sheets count and quick export action.
 */
@Composable
fun XlsxFloatingBar(
    isVisible: Boolean,
    selectedCount: Int,
    isExporting: Boolean,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && selectedCount > 0,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .shadow(20.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0A0A0A))
                .padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexusText(
                text = "$selectedCount sheet${if (selectedCount == 1) "" else "s"} selected",
                style = NexusTheme.typography.body.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                color = Color.White
            )

            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable(enabled = !isExporting) { onExport() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = Color(0xFF0A0A0A),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFF0A0A0A),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        NexusText(
                            text = "Export PDF",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF0A0A0A)
                        )
                    }
                }
            }
        }
    }
}

