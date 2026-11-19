package com.nexus.feature.dashboard.tools.sign_pdf.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.OpenWith
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.sign_pdf.PlacedStamp
import com.nexus.feature.dashboard.tools.sign_pdf.SignatureItem
import kotlin.math.roundToInt

@Composable
fun SignDocumentViewer(
    fileName: String,
    currentPage: Int,
    totalPages: Int,
    zoomFactor: Float,
    pageBitmap: Bitmap?,
    pageAspectRatio: Float,
    isLoadingPage: Boolean,
    placedStamps: List<PlacedStamp>,
    signatureItems: Map<Long, SignatureItem>,
    selectedStampId: Long?,
    onSelectStamp: (Long?) -> Unit,
    onStampMoved: (stampId: Long, newX: Float, newY: Float) -> Unit,
    onStampResized: (stampId: Long, newWidthRatio: Float) -> Unit,
    onRemoveStamp: (stampId: Long) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onChangeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Viewer Shell Card ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            // ── Viewer Header ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Row 1: File name + "Change" link and Pager Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // File name and Change link
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusText(
                            text = fileName,
                            style = NexusTheme.typography.body.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF0A0A0A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onChangeFile() }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            NexusText(
                                text = "Change",
                                style = NexusTheme.typography.label.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF707070)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Pager Pill: [ < ] [ Page X / Y ] [ > ]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Previous page
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = currentPage > 1) { onPrevPage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous page",
                                modifier = Modifier.size(20.dp),
                                tint = if (currentPage > 1) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)
                            )
                        }

                        // Page Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF5F5F5))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            NexusText(
                                text = "Page $currentPage / $totalPages",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF0A0A0A)
                            )
                        }

                        // Next page
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = currentPage < totalPages) { onNextPage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next page",
                                modifier = Modifier.size(20.dp),
                                tint = if (currentPage < totalPages) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Zoom Controls (- | 100% | + | Reset)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = zoomFactor > 0.5f) { onZoomOut() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Zoom out",
                            modifier = Modifier.size(17.dp),
                            tint = if (zoomFactor > 0.5f) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)
                        )
                    }

                    NexusText(
                        text = "${(zoomFactor * 100).roundToInt()}%",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF0A0A0A),
                        modifier = Modifier.widthIn(min = 48.dp),
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = zoomFactor < 2.5f) { onZoomIn() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Zoom in",
                            modifier = Modifier.size(17.dp),
                            tint = if (zoomFactor < 2.5f) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .background(lineStrong)
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onZoomReset() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reset zoom",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF0A0A0A)
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

            // ── Viewport Area ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 400.dp, max = 580.dp)
                    .background(Color(0xFFFAFAFA))
                    .clickable { onSelectStamp(null) } // Deselect if clicking outside stamps
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background subtle dotted pattern
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

                // ── Page Box ─────────────────────────────────────────────────
                var pageBoxSize by remember { mutableStateOf(IntSize.Zero) }
                val currentStampsOnPage = placedStamps.filter { it.page == currentPage }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f * zoomFactor.coerceIn(0.5f, 1.3f))
                        .aspectRatio(pageAspectRatio.coerceIn(0.3f, 2.5f))
                        .shadow(12.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .onSizeChanged { pageBoxSize = it }
                ) {
                    if (pageBitmap != null) {
                        Image(
                            bitmap = pageBitmap.asImageBitmap(),
                            contentDescription = "PDF Page",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    // ── Stamp Layer: Placed Signatures on Current Page ────────
                    if (pageBoxSize.width > 0 && pageBoxSize.height > 0) {
                        for (stamp in currentStampsOnPage) {
                            val sig = signatureItems[stamp.sigId] ?: continue
                            val isSelected = stamp.id == selectedStampId

                            val boxWidthPx = pageBoxSize.width.toFloat()
                            val boxHeightPx = pageBoxSize.height.toFloat()

                            val stampWidthPx = stamp.widthRatio * boxWidthPx
                            val stampHeightPx = stampWidthPx / stamp.aspectRatio
                            val stampLeftPx = stamp.x * boxWidthPx
                            val stampTopPx = stamp.y * boxHeightPx

                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (stampLeftPx / (pageBoxSize.width.toFloat() / sizeModifier(boxWidthPx))).dp,
                                        y = (stampTopPx / (pageBoxSize.height.toFloat() / sizeModifier(boxHeightPx))).dp
                                    )
                                    .size(
                                        width = (stampWidthPx / (pageBoxSize.width.toFloat() / sizeModifier(boxWidthPx))).dp,
                                        height = (stampHeightPx / (pageBoxSize.height.toFloat() / sizeModifier(boxHeightPx))).dp
                                    )
                                    .pointerInput(stamp.id) {
                                        detectDragGestures(
                                            onDragStart = { onSelectStamp(stamp.id) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val deltaX = dragAmount.x / boxWidthPx
                                                val deltaY = dragAmount.y / boxHeightPx
                                                val newX = (stamp.x + deltaX).coerceIn(0f, 1f - stamp.widthRatio)
                                                val maxHFrac = stampHeightPx / boxHeightPx
                                                val newY = (stamp.y + deltaY).coerceIn(0f, 1f - maxHFrac)
                                                onStampMoved(stamp.id, newX, newY)
                                            }
                                        )
                                    }
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF0A0A0A) else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .background(if (isSelected) Color(0x080A0A0A) else Color.Transparent)
                            ) {
                                // Signature Image
                                Image(
                                    bitmap = sig.bitmap.asImageBitmap(),
                                    contentDescription = "Placed signature",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                // Controls when selected
                                if (isSelected) {
                                    // Top-right Delete Button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 10.dp, y = (-10).dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0A0A0A))
                                            .border(1.5.dp, Color.White, CircleShape)
                                            .clickable { onRemoveStamp(stamp.id) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(14.dp),
                                            tint = Color.White
                                        )
                                    }

                                    // Bottom-right Resize Handle
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 10.dp, y = 10.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0A0A0A))
                                            .border(1.5.dp, Color.White, CircleShape)
                                            .pointerInput(stamp.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val deltaWidth = dragAmount.x / boxWidthPx
                                                    val newW = (stamp.widthRatio + deltaWidth).coerceIn(0.06f, 0.9f)
                                                    onStampResized(stamp.id, newW)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenWith,
                                            contentDescription = "Resize",
                                            modifier = Modifier.size(12.dp),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Loading overlay
                    if (isLoadingPage) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.75f)),
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
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Helper Hint ──────────────────────────────────────────────────────
        val hintText = if (placedStamps.isNotEmpty()) {
            "Drag a signature to move it. Use the corner dot to resize and × to remove."
        } else {
            "Tap “Add signature”, then drag it into place. Drag the corner dot to resize."
        }

        NexusText(
            text = hintText,
            style = NexusTheme.typography.caption.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF707070),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Helper for pixel-to-dp ratio
private fun sizeModifier(sizePx: Float): Float {
    return (sizePx / 2.75f).coerceAtLeast(1f)
}
