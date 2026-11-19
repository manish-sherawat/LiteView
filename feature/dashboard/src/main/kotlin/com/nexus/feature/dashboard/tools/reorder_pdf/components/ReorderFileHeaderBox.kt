package com.nexus.feature.dashboard.tools.reorder_pdf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText

@Composable
fun ReorderFileHeaderBox(
    fileName: String,
    pageCount: Int,
    fileSizeBytes: Long,
    formattedSize: String,
    canUndo: Boolean,
    canRedo: Boolean,
    canReset: Boolean,
    isOriginalOrder: Boolean,
    onChangeFile: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onReverseOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFAFAFA))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // ── Row 1: File row with PDF badge, title, and Change link ───────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF Badge with folded top-right corner
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .drawBehind {
                        // Folded top-right triangle
                        val foldSize = 11.dp.toPx()
                        val path = Path().apply {
                            moveTo(size.width - foldSize, 0f)
                            lineTo(size.width, foldSize)
                            lineTo(size.width - foldSize, foldSize)
                            close()
                        }
                        drawPath(path, Color(0xFF555555))
                    },
                contentAlignment = Alignment.Center
            ) {
                NexusText(
                    text = "PDF",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Name & Meta
            Column(
                modifier = Modifier.weight(1f)
            ) {
                NexusText(
                    text = fileName,
                    style = NexusTheme.typography.body.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = Color(0xFF0A0A0A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                NexusText(
                    text = "$pageCount page${if (pageCount == 1) "" else "s"} · $formattedSize",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = Color(0xFF707070)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // "Change" Link Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onChangeFile() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                NexusText(
                    text = "Change",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF707070)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Row 2: History Buttons (Undo, Redo, Reset) ───────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HistoryButton(
                text = "Undo",
                enabled = canUndo,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (canUndo) Color(0xFF0A0A0A) else lineStrong
                    )
                },
                onClick = onUndo,
                modifier = Modifier.weight(1f)
            )

            HistoryButton(
                text = "Redo",
                enabled = canRedo,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (canRedo) Color(0xFF0A0A0A) else lineStrong
                    )
                },
                onClick = onRedo,
                modifier = Modifier.weight(1f)
            )

            HistoryButton(
                text = "Reset",
                enabled = canReset,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (canReset) Color(0xFF0A0A0A) else lineStrong
                    )
                },
                onClick = onReset,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Row 3: Status Summary & Reverse Order ────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusString = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0A0A0A))) {
                    append("$pageCount page${if (pageCount == 1) "" else "s"}")
                }
                append(" · ")
                append(if (isOriginalOrder) "original order" else "new order")
            }

            NexusText(
                text = statusString.text,
                style = NexusTheme.typography.caption.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF707070)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = pageCount > 1) { onReverseOrder() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                NexusText(
                    text = "Reverse order",
                    style = NexusTheme.typography.label.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (pageCount > 1) Color(0xFF0A0A0A) else lineStrong
                )
            }
        }
    }
}

@Composable
private fun HistoryButton(
    text: String,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (enabled) Color(0xFFCFCFCF) else Color(0xFFEBEBEB)
    val textColor = if (enabled) Color(0xFF0A0A0A) else Color(0xFFCFCFCF)

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        NexusText(
            text = text,
            style = NexusTheme.typography.buttonLabel.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = textColor
        )
    }
}
