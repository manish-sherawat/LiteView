package com.nexus.feature.dashboard.tools.pdf_to_image.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.pdf_to_image.ExportMode
import com.nexus.feature.dashboard.tools.pdf_to_image.PdfToImageConfig
import kotlin.math.roundToInt

@Composable
fun PdfToImagePreviewCard(
    config: PdfToImageConfig,
    currentBitmap: Bitmap?,
    currentIndex: Int,
    totalItems: Int,
    currentWidthPx: Int,
    currentHeightPx: Int,
    zoomScale: Float,
    isLoading: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isImagesMode = config.mode == ExportMode.IMAGES
    val isPng = config.format.isLossless

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top Toolbar: Page Nav & Zoom Controls ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Navigation Toolgroup
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarIconButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        enabled = currentIndex > 0,
                        onClick = onPrev
                    )

                    val navLabel = if (totalItems > 0) "${currentIndex + 1} / $totalItems" else "0 / 0"
                    NexusText(
                        text = navLabel,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    ToolbarIconButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        enabled = currentIndex < totalItems - 1,
                        onClick = onNext
                    )
                }

                // Zoom Toolgroup
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarIconButton(
                        icon = Icons.Rounded.Remove,
                        enabled = zoomScale > 0.35f,
                        onClick = onZoomOut
                    )

                    NexusText(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    ToolbarIconButton(
                        icon = Icons.Rounded.Add,
                        enabled = zoomScale < 3.5f,
                        onClick = onZoomIn
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(1.dp)
                            .height(18.dp)
                            .background(NexusTheme.colors.divider.copy(alpha = 0.7f))
                    )

                    ToolbarIconButton(
                        icon = Icons.Rounded.RestartAlt,
                        enabled = true,
                        onClick = onZoomReset
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
            )

            // ── Viewport Stage: Dot grid backdrop with zoomable sheet ─────────
            val hScroll = rememberScrollState()
            val vScroll = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.25f))
                    .drawBehind {
                        val dotColor = Color(0xFFD4D4D4).copy(alpha = 0.45f)
                        val step = 18.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            var y = 0f
                            while (y < size.height) {
                                drawCircle(color = dotColor, radius = 1.2.dp.toPx(), center = Offset(x, y))
                                y += step
                            }
                            x += step
                        }
                    }
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                            .verticalScroll(vScroll),
                        contentAlignment = Alignment.Center
                    ) {
                        val baseWidth = 230.dp
                        val baseHeight = if (currentWidthPx > 0) {
                            baseWidth * (currentHeightPx.toFloat() / currentWidthPx.toFloat())
                        } else {
                            320.dp
                        }

                        Box(
                            modifier = Modifier
                                .size(width = baseWidth * zoomScale, height = baseHeight * zoomScale)
                                .shadow(elevation = 8.dp, shape = RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        ) {
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = "Page Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else if (!isLoading && totalItems == 0 && isImagesMode) {
                    NexusText(
                        text = "No embedded images found in this PDF.",
                        style = NexusTheme.typography.body.copy(fontSize = 13.5.sp),
                        color = NexusTheme.colors.textSecondary
                    )
                }

                // Loading overlay
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NexusTheme.colors.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = NexusTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            NexusText(
                                text = "Rendering preview...",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
            )

            // ── Bottom Toolbar: Dimensions & Summary Pill ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val dimsText = if (currentWidthPx > 0 && currentHeightPx > 0) {
                    if (isImagesMode) {
                        "$currentWidthPx × $currentHeightPx px · original size"
                    } else {
                        "$currentWidthPx × $currentHeightPx px"
                    }
                } else {
                    "—"
                }

                NexusText(
                    text = dimsText,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                // Format summary pill
                val summaryText = buildString {
                    append(config.format.label)
                    if (!isPng) {
                        append(" · ${config.qualityPct}%")
                    }
                    if (!isImagesMode) {
                        append(String.format(" · %.2f×", config.resolutionScale))
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    NexusText(
                        text = summaryText,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.3f)
        )
    }
}
