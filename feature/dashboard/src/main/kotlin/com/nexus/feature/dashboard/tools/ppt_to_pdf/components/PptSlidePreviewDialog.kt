package com.nexus.feature.dashboard.tools.ppt_to_pdf.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick

@Composable
fun PptSlidePreviewDialog(
    slideNumber: Int,
    totalSlides: Int,
    bitmap: Bitmap?,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 780.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NexusTheme.colors.surface)
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {} // Prevent click-through dismiss
            ) {
                // ── Header: Title & Close Button ─────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "Slide $slideNumber of $totalSlides",
                        style = NexusTheme.typography.title.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = NexusTheme.colors.textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, Color(0xFFCFCFCF), CircleShape)
                            .background(NexusTheme.colors.surface)
                            .springBounceClick { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close preview",
                            modifier = Modifier.size(16.dp),
                            tint = NexusTheme.colors.textPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEBEBEB))
                )

                // ── Body: High Quality Slide Canvas Image ────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 220.dp, max = 520.dp)
                        .background(Color(0xFFFAFAFA))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Slide $slideNumber Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF0A0A0A),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            NexusText(
                                text = "Loading slide preview…",
                                style = NexusTheme.typography.caption.copy(fontSize = 13.sp),
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFEBEBEB))
                )

                // ── Footer: Close and Toggle Selection Buttons ───────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close Ghost Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFCFCFCF), RoundedCornerShape(12.dp))
                            .background(NexusTheme.colors.surface)
                            .springBounceClick { onDismiss() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        NexusText(
                            text = "Close",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }

                    // Toggle Selection Primary Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0A0A))
                            .springBounceClick { onToggleSelect() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        NexusText(
                            text = if (isSelected) "Deselect this slide" else "Select this slide",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
