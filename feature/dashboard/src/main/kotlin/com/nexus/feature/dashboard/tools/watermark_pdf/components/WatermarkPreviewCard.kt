package com.nexus.feature.dashboard.tools.watermark_pdf.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.watermark_pdf.PdfFileInfo
import com.nexus.feature.dashboard.tools.watermark_pdf.WatermarkConfig
import com.nexus.feature.dashboard.tools.watermark_pdf.WatermarkLayoutMode

@Composable
fun WatermarkPreviewCard(
    config: WatermarkConfig,
    fileInfo: PdfFileInfo?,
    previewBitmap: Bitmap? = null,
    pageIndex: Int = 0,
    onPrevPage: (() -> Unit)? = null,
    onNextPage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )

    val activeBmp = previewBitmap ?: fileInfo?.firstPageBitmap
    val totalPages = fileInfo?.pageCount ?: 1
    val pageAspect = activeBmp?.let {
        it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f)
    } ?: (595f / 842f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top Bar: Live indicator + Page Navigation / Count ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = NexusTheme.colors.primary.copy(alpha = livePulseAlpha),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusText(
                        text = "Live preview",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                }

                if (fileInfo != null && totalPages > 1 && onPrevPage != null && onNextPage != null) {
                    // Interactive page stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (pageIndex > 0) NexusTheme.colors.surfaceVariant else NexusTheme.colors.surfaceVariant.copy(alpha = 0.4f))
                                .clickable(enabled = pageIndex > 0) { onPrevPage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Previous page",
                                modifier = Modifier.size(14.dp),
                                tint = if (pageIndex > 0) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.4f)
                            )
                        }

                        NexusText(
                            text = "${pageIndex + 1} / $totalPages",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (pageIndex < totalPages - 1) NexusTheme.colors.surfaceVariant else NexusTheme.colors.surfaceVariant.copy(alpha = 0.4f))
                                .clickable(enabled = pageIndex < totalPages - 1) { onNextPage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Next page",
                                modifier = Modifier.size(14.dp),
                                tint = if (pageIndex < totalPages - 1) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    val pageCountText = fileInfo?.let { "${it.pageCount} page${if (it.pageCount == 1) "" else "s"}" } ?: "All pages"
                    NexusText(
                        text = pageCountText,
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NexusTheme.colors.divider.copy(alpha = 0.5f))
            )

            // ── Stage Background with Document Preview Sheet ───────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                // Dynamic Aspect Ratio Page Sheet
                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .fillMaxWidth(0.72f)
                        .aspectRatio(pageAspect)
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .border(0.8.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                ) {
                    // Underlay: Real PDF page bitmap or Mock document lines
                    if (activeBmp != null) {
                        Image(
                            bitmap = activeBmp.asImageBitmap(),
                            contentDescription = "PDF Page ${pageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    } else if (fileInfo != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = NexusTheme.colors.primary
                            )
                        }
                    } else {
                        MockDocumentContent(modifier = Modifier.fillMaxSize())
                    }

                    // Watermark Drawing Layer (Canvas)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val sheetWidth = size.width
                                val sheetHeight = size.height
                                val scale = sheetWidth / 595f

                                val fontSizePx = (config.sizePt * scale).coerceAtLeast(8f)
                                val opacity = (config.opacityPct / 100f).coerceIn(0.04f, 1f)
                                val gray = (config.ink.grayLevel * 255f).toInt().coerceIn(0, 255)
                                val watermarkText = config.text.trim().ifEmpty { "DRAFT" }

                                drawIntoCanvas { canvas ->
                                    val nativeCanvas = canvas.nativeCanvas
                                    val textPaint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        textSize = fontSizePx
                                        typeface = android.graphics.Typeface.create(
                                            android.graphics.Typeface.DEFAULT,
                                            android.graphics.Typeface.BOLD
                                        )
                                        color = android.graphics.Color.argb(
                                            (opacity * 255).toInt(),
                                            gray,
                                            gray,
                                            gray
                                        )
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }

                                    val tw = textPaint.measureText(watermarkText)
                                    val fontMetrics = textPaint.fontMetrics
                                    val textYOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f

                                    if (config.layoutMode == WatermarkLayoutMode.SINGLE) {
                                        val cx = sheetWidth / 2f
                                        val cy = sheetHeight / 2f
                                        nativeCanvas.save()
                                        nativeCanvas.rotate(-config.rotationDeg, cx, cy)
                                        nativeCanvas.drawText(watermarkText, cx, cy + textYOffset, textPaint)
                                        nativeCanvas.restore()
                                    } else {
                                        // Tiled repeating layout
                                        val stepX = tw + fontSizePx * 1.2f
                                        val stepY = fontSizePx * 3f
                                        var row = 0
                                        var y = -stepY
                                        while (y < sheetHeight + stepY) {
                                            val offset = if (row % 2 != 0) stepX / 2f else 0f
                                            var x = -stepX + offset
                                            while (x < sheetWidth + stepX) {
                                                nativeCanvas.save()
                                                nativeCanvas.rotate(-config.rotationDeg, x, y)
                                                nativeCanvas.drawText(watermarkText, x, y + textYOffset, textPaint)
                                                nativeCanvas.restore()
                                                x += stepX
                                            }
                                            y += stepY
                                            row++
                                        }
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}

/**
 * Mock document lines and illustrations matching the HTML preview card.
 */
@Composable
private fun MockDocumentContent(modifier: Modifier = Modifier) {
    val barColor = Color(0xFFE5E7EB)
    val titleBarColor = Color(0xFFCBD5E1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // Heading Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(titleBarColor)
            )
            // Subheading Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.32f)
                    .height(5.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Paragraph 1
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.78f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.82f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.60f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))

            Spacer(modifier = Modifier.height(8.dp))

            // Paragraph 2
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.74f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.48f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
        }

        // Mock Document Image Box
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(44.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF1F5F9))
        )

        // Bottom Paragraph
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.76f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.80f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Box(modifier = Modifier.fillMaxWidth(0.55f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
        }
    }
}
