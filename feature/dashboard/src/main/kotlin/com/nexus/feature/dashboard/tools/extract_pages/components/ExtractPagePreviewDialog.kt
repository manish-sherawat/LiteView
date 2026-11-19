package com.nexus.feature.dashboard.tools.extract_pages.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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

@Composable
fun ExtractPagePreviewDialog(
    isOpen: Boolean,
    pageNumber: Int,
    totalPages: Int,
    previewBitmap: Bitmap?,
    isSelected: Boolean,
    isLoading: Boolean,
    onToggleSelect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val borderColor = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Dialog Head ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = "Page $pageNumber of $totalPages",
                        style = NexusTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color(0xFF0A0A0A)
                    )

                    // Circular Close Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, lineStrong, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close preview",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF0A0A0A)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(borderColor)
                )

                // ── Dialog Viewport ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp, max = 540.dp)
                        .background(Color(0xFFFAFAFA))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Dotted background pattern
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 18.dp.toPx()
                        val dotRadius = 1.dp.toPx()
                        var x = step / 2f
                        while (x < size.width) {
                            var y = step / 2f
                            while (y < size.height) {
                                drawCircle(
                                    color = Color(0xFFDCDCDC),
                                    radius = dotRadius,
                                    center = Offset(x, y)
                                )
                                y += step
                            }
                            x += step
                        }
                    }

                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Page $pageNumber high-res preview",
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .shadow(14.dp, RoundedCornerShape(3.dp))
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xD9FAFAFA)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color(0xFF0A0A0A),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(borderColor)
                )

                // ── Dialog Foot ──────────────────────────────────────────────
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
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, lineStrong, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = "Close",
                            style = NexusTheme.typography.buttonLabel.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF0A0A0A)
                        )
                    }

                    // Select / Remove Primary Button
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0A0A))
                            .clickable { onToggleSelect() }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        NexusText(
                            text = if (isSelected) "Remove from selection" else "Select this page",
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
