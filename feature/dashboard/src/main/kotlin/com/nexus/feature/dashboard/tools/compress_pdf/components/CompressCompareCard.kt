package com.nexus.feature.dashboard.tools.compress_pdf.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.compress_pdf.CompressPdfEngine
import com.nexus.feature.dashboard.tools.compress_pdf.CompressResult
import kotlin.math.abs

@Composable
fun CompressCompareCard(
    originalSizeBytes: Long,
    estimatedSizeBytes: Long,
    firstPageBitmap: Bitmap?,
    result: CompressResult?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isResultReady = result != null
    val targetSizeBytes = if (isResultReady) result!!.sizeBytes else estimatedSizeBytes

    val pct = if (originalSizeBytes > 0) {
        (((originalSizeBytes - targetSizeBytes).toDouble() / originalSizeBytes) * 100).toInt()
    } else 0
    val isWorse = pct < 0

    val barFraction = if (originalSizeBytes > 0) {
        (targetSizeBytes.toFloat() / originalSizeBytes.toFloat()).coerceIn(0.05f, 1.0f)
    } else 0.5f

    val animatedBarWidth by animateFloatAsState(
        targetValue = barFraction,
        animationSpec = tween(500),
        label = "barWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(NexusTheme.colors.surface)
            .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = NexusTheme.colors.divider.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                NexusText(
                    text = "Size comparison",
                    style = NexusTheme.typography.title.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = NexusTheme.colors.textPrimary
                )
            }

            // ── Body ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // ── Comparison Bars ───────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Original Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusText(
                            text = "Original",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textSecondary,
                            modifier = Modifier.width(76.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NexusTheme.colors.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NexusTheme.colors.divider.copy(alpha = 0.85f))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        NexusText(
                            text = CompressPdfEngine.formatFileSize(originalSizeBytes),
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }

                    // Estimated / Compressed Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusText(
                            text = if (isResultReady) "Compressed" else "Estimated",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = NexusTheme.colors.textSecondary,
                            modifier = Modifier.width(76.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NexusTheme.colors.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedBarWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NexusTheme.colors.textPrimary)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        NexusText(
                            text = (if (isResultReady) "" else "~") + CompressPdfEngine.formatFileSize(targetSizeBytes),
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Saved Banner ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NexusText(
                        text = (if (isWorse) "+" else "−") + "${abs(pct)}%",
                        style = NexusTheme.typography.h1.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        ),
                        color = NexusTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    NexusText(
                        text = if (isWorse) {
                            "This setting would make the file bigger. Try Low quality or turn off “Keep text selectable”."
                        } else {
                            val savedBytes = (originalSizeBytes - targetSizeBytes).coerceAtLeast(0L)
                            "You’d save about ${CompressPdfEngine.formatFileSize(savedBytes)}."
                        },
                        style = NexusTheme.typography.bodySmall.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NexusTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // ── Thumbnail Stage ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, NexusTheme.colors.divider.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (firstPageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            ) {
                                Image(
                                    bitmap = firstPageBitmap.asImageBitmap(),
                                    contentDescription = "First page thumbnail",
                                    modifier = Modifier
                                        .heightIn(max = 240.dp)
                                        .fillMaxWidth(0.75f),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(width = 140.dp, height = 180.dp)
                                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        NexusText(
                            text = "First page of your PDF",
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 12.sp,
                                color = NexusTheme.colors.textSecondary
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Result Card (When compression finishes) ───────────────────
                AnimatedVisibility(
                    visible = isResultReady,
                    enter = fadeIn() + expandVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NexusTheme.colors.surface)
                            .border(1.8.dp, NexusTheme.colors.textPrimary, RoundedCornerShape(16.dp))
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (pct >= 0) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = NexusTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            NexusText(
                                text = if (pct >= 0) "Compressed" else "Not smaller",
                                style = NexusTheme.typography.title.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NexusTheme.colors.textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        NexusText(
                            text = if (pct >= 0) {
                                "${CompressPdfEngine.formatFileSize(originalSizeBytes)} → ${CompressPdfEngine.formatFileSize(result!!.sizeBytes)} (−${pct}%)."
                            } else {
                                "The result (${CompressPdfEngine.formatFileSize(result!!.sizeBytes)}) is larger than your original. Your original is already well optimized."
                            },
                            style = NexusTheme.typography.bodySmall.copy(
                                fontSize = 13.5.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Download Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NexusTheme.colors.textPrimary)
                                .springBounceClick { onDownload() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = NexusTheme.colors.background
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            NexusText(
                                text = if (pct >= 0) "Download PDF" else "Download anyway",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 14.sp,
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
