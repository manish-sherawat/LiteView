package com.nexus.feature.dashboard.tools.image_to_pdf.components

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
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.R
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.image_to_pdf.ImagePageItem
import com.nexus.feature.dashboard.tools.image_to_pdf.ImageToPdfConfig
import com.nexus.feature.dashboard.tools.image_to_pdf.ImageToPdfEngine
import kotlin.math.roundToInt

@Composable
fun ImageToPdfEditorCard(
    page: ImagePageItem?,
    pageIndex: Int,
    config: ImageToPdfConfig,
    zoomScale: Float,
    onRotateImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onAddOrReplaceImage: () -> Unit,
    onDeletePage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top Bar: Page Chip, Add/Replace, Delete ───────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.5.dp, NexusTheme.colors.divider, RoundedCornerShape(999.dp))
                        .background(NexusTheme.colors.surface)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NexusTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    NexusText(
                        text = "Page ${pageIndex + 1}",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Add / Replace Image Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NexusTheme.colors.surface)
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .springBounceClick { onAddOrReplaceImage() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (page?.bitmap != null) Icons.Rounded.Sync else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NexusTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    NexusText(
                        text = if (page?.bitmap != null) "Replace image" else "Add image",
                        style = NexusTheme.typography.body.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Delete Page Circular Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NexusTheme.colors.surface)
                        .border(1.5.dp, NexusTheme.colors.divider, CircleShape)
                        .clickable { onDeletePage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete page",
                        modifier = Modifier.size(18.dp),
                        tint = NexusTheme.colors.textPrimary
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

            // ── Viewport: Dot Pattern + Zoomable Sheet ────────────────────────
            val hScroll = rememberScrollState()
            val vScroll = rememberScrollState()

            val geom = if (page != null) {
                ImageToPdfEngine.calculateGeometry(page, config.pageSize, config.marginPt)
            } else {
                com.nexus.feature.dashboard.tools.image_to_pdf.PageGeometry(595f, 842f, 0f, 0f, 0f, 0f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
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
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll)
                        .verticalScroll(vScroll),
                    contentAlignment = Alignment.Center
                ) {
                    val baseWidthDp = 220.dp
                    val sheetAspectRatio = if (geom.pageHeightPt > 0) geom.pageWidthPt / geom.pageHeightPt else 0.7f
                    val baseHeightDp = baseWidthDp / sheetAspectRatio

                    // The White Paper Sheet
                    Box(
                        modifier = Modifier
                            .size(width = baseWidthDp * zoomScale, height = baseHeightDp * zoomScale)
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(3.dp))
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (page?.bitmap != null) {
                            // Calculate image proportion inside sheet
                            val imgWidthFraction = (geom.imageWidthPt / geom.pageWidthPt).coerceIn(0.01f, 1f)
                            val imgHeightFraction = (geom.imageHeightPt / geom.pageHeightPt).coerceIn(0.01f, 1f)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(imgWidthFraction)
                                    .fillMaxHeight(imgHeightFraction),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = page.bitmap.asImageBitmap(),
                                    contentDescription = "Page Image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            rotationZ = page.rotationDeg.toFloat()
                                        }
                                )
                            }

                            // Floating Action Buttons Overlay (Rotate & Remove)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Rotate Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(elevation = 8.dp, shape = CircleShape)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.textPrimary)
                                        .clickable { onRotateImage() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RotateRight,
                                        contentDescription = "Rotate image",
                                        tint = NexusTheme.colors.background,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Remove Image Button
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(elevation = 8.dp, shape = CircleShape)
                                        .clip(CircleShape)
                                        .background(NexusTheme.colors.surface)
                                        .border(1.2.dp, NexusTheme.colors.textPrimary, CircleShape)
                                        .clickable { onRemoveImage() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove image",
                                        tint = NexusTheme.colors.textPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        } else {
                            // Blank Page Placeholder
                            val dashColor = NexusTheme.colors.divider
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.85f)
                                    .drawBehind {
                                        drawRoundRect(
                                            color = dashColor,
                                            style = Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                                            ),
                                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                                        )
                                    }
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pdf_to_image),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = NexusTheme.colors.textSecondary.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    NexusText(
                                        text = "Blank page\nTap “Add image” to place a photo here",
                                        style = NexusTheme.typography.caption.copy(
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp
                                        ),
                                        color = NexusTheme.colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
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

            // ── Editor Footnote: Dimensions & Summary Pill ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NexusText(
                    text = "${geom.pageWidthPt.roundToInt()} × ${geom.pageHeightPt.roundToInt()} pt",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = NexusTheme.colors.textSecondary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    NexusText(
                        text = "${config.pageSize.label} · ${config.marginPt} pt margin",
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
