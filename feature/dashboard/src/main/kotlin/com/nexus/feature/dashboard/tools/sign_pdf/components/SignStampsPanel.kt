package com.nexus.feature.dashboard.tools.sign_pdf.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.feature.dashboard.tools.sign_pdf.PlacedStamp
import com.nexus.feature.dashboard.tools.sign_pdf.SignatureItem

@Composable
fun SignStampsPanel(
    placedStamps: List<PlacedStamp>,
    signatureItems: Map<Long, SignatureItem>,
    totalPages: Int,
    onShowStamp: (PlacedStamp) -> Unit,
    onDuplicateToAllPages: (PlacedStamp) -> Unit,
    onRemoveStamp: (stampId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (placedStamps.isEmpty()) {
        EmptyStampsCard(modifier = modifier)
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            placedStamps.forEachIndexed { index, stamp ->
                val sig = signatureItems[stamp.sigId]
                StampCardRow(
                    index = index + 1,
                    stamp = stamp,
                    sig = sig,
                    totalPages = totalPages,
                    onShow = { onShowStamp(stamp) },
                    onAllPages = { onDuplicateToAllPages(stamp) },
                    onRemove = { onRemoveStamp(stamp.id) }
                )
            }
        }
    }
}

@Composable
private fun EmptyStampsCard(modifier: Modifier = Modifier) {
    val borderColor = Color(0xFFCFCFCF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f), 0f)
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            }
            .padding(horizontal = 20.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = Color(0xFFA3A3A3)
            )

            Spacer(modifier = Modifier.height(12.dp))

            NexusText(
                text = "No stamps yet.\nAdd a signature to place it on your document.",
                style = NexusTheme.typography.body.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF707070),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StampCardRow(
    index: Int,
    stamp: PlacedStamp,
    sig: SignatureItem?,
    totalPages: Int,
    onShow: () -> Unit,
    onAllPages: () -> Unit,
    onRemove: () -> Unit
) {
    val borderColor = Color(0xFFEBEBEB)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Row: Checkered thumbnail + Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkered thumbnail
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Checkered pattern background
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tileSize = 8.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        var y = 0f
                        while (y < size.height) {
                            val col = (x / tileSize).toInt()
                            val row = (y / tileSize).toInt()
                            if ((col + row) % 2 == 0) {
                                drawRect(
                                    color = Color(0xFFF2F2F2),
                                    topLeft = Offset(x, y),
                                    size = Size(tileSize, tileSize)
                                )
                            }
                            y += tileSize
                        }
                        x += tileSize
                    }
                }

                if (sig != null) {
                    Image(
                        bitmap = sig.bitmap.asImageBitmap(),
                        contentDescription = "Signature thumbnail",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info column
            Column {
                NexusText(
                    text = "Signature $index",
                    style = NexusTheme.typography.body.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = Color(0xFF0A0A0A)
                )

                Spacer(modifier = Modifier.height(2.dp))

                NexusText(
                    text = "Page ${stamp.page} of $totalPages",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF707070)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3 Action Buttons: Show | All pages | Remove
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Show" button
            ActionButton(
                text = "Show",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF0A0A0A)
                    )
                },
                onClick = onShow,
                modifier = Modifier.weight(1f)
            )

            // "All pages" button (disabled if totalPages < 2)
            ActionButton(
                text = "All pages",
                enabled = totalPages > 1,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (totalPages > 1) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)
                    )
                },
                onClick = onAllPages,
                modifier = Modifier.weight(1f)
            )

            // "Remove" button
            ActionButton(
                text = "Remove",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF0A0A0A)
                    )
                },
                onClick = onRemove,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val borderCol = if (enabled) Color(0xFFCFCFCF) else Color(0xFFEBEBEB)
    val textCol = if (enabled) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)

    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        NexusText(
            text = text,
            style = NexusTheme.typography.caption.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = textCol
        )
    }
}
