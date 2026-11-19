package com.nexus.feature.dashboard.tools.compress_pdf.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.compress_pdf.CompressPdfEngine

@Composable
fun CompressPreviewDialog(
    currentPage: Int,
    totalPages: Int,
    pageBitmap: Bitmap?,
    isLoading: Boolean,
    originalSizeBytes: Long,
    estimatedSizeBytes: Long,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NexusTheme.colors.surface)
                    .shadow(24.dp, shape = RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Head ───────────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusText(
                            text = "Preview",
                            style = NexusTheme.typography.title.copy(
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Navigation Tools Capsule: < 1 / N >
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                                    .background(NexusTheme.colors.surface)
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Previous Page
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable(enabled = currentPage > 1) { onPageChange(currentPage - 1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = "Previous page",
                                        tint = if (currentPage > 1) NexusTheme.colors.textPrimary else NexusTheme.colors.divider,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                NexusText(
                                    text = "$currentPage / $totalPages",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Next Page
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable(enabled = currentPage < totalPages) { onPageChange(currentPage + 1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Next page",
                                        tint = if (currentPage < totalPages) NexusTheme.colors.textPrimary else NexusTheme.colors.divider,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Close Button Circle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.2.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), CircleShape)
                                    .springBounceClick { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close preview",
                                    tint = NexusTheme.colors.textPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // ── Viewport Canvas ────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f))
                            .padding(18.dp)
                            .transformable(state = transformState),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .shadow(16.dp, shape = RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                            ) {
                                Image(
                                    bitmap = pageBitmap.asImageBitmap(),
                                    contentDescription = "Preview Page $currentPage",
                                    modifier = Modifier
                                        .fillMaxWidth(0.95f)
                                        .fillMaxHeight(0.95f),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        // Loading State Overlay
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = NexusTheme.colors.textPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    NexusText(
                                        text = "Rendering preview…",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = NexusTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // ── Foot ───────────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pct = if (originalSizeBytes > 0) {
                            (((originalSizeBytes - estimatedSizeBytes).toDouble() / originalSizeBytes) * 100).toInt()
                        } else 0

                        NexusText(
                            text = "~${CompressPdfEngine.formatFileSize(estimatedSizeBytes)} · ${if (pct >= 0) "−$pct%" else "+${kotlin.math.abs(pct)}%"} vs original",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Adjust (Ghost)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.2.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                    .springBounceClick { onDismiss() }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "Adjust",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )
                            }

                            // Looks good (Primary)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NexusTheme.colors.textPrimary)
                                    .springBounceClick {
                                        onDismiss()
                                        onConfirm()
                                    }
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NexusText(
                                    text = "Looks good",
                                    style = NexusTheme.typography.caption.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = NexusTheme.colors.background
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
