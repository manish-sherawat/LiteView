package com.nexus.feature.dashboard.tools.xlsx_to_pdf.components

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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.xlsx_to_pdf.SheetData
import kotlin.math.min

@Composable
fun XlsxSheetPreviewDialog(
    sheet: SheetData,
    sheetNumber: Int,
    totalSheets: Int,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxRows = 60
    val maxCols = 30
    val displayRows = sheet.rows.take(maxRows)
    val displayCols = min(sheet.colsCount, maxCols)
    val hasTruncation = sheet.rows.size > maxRows || sheet.colsCount > maxCols

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
                    .fillMaxWidth(0.96f)
                    .widthIn(max = 840.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(NexusTheme.colors.surface)
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFFEBEBEB), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}
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
                        text = "${sheet.name} ($sheetNumber of $totalSheets)",
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

                // ── Body: Scrollable Table View ──────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 200.dp, max = 480.dp)
                        .background(Color(0xFFFAFAFA))
                        .padding(16.dp)
                ) {
                    val verticalScrollState = rememberScrollState()
                    val horizontalScrollState = rememberScrollState()

                    if (sheet.rows.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .background(Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(verticalScrollState)
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                displayRows.forEachIndexed { rIdx, row ->
                                    val isHeader = rIdx == 0
                                    Row(
                                        modifier = Modifier
                                            .background(if (isHeader) Color(0xFFF1F5F9) else if (rIdx % 2 == 0) Color(0xFFFAFAFA) else Color.White)
                                    ) {
                                        for (cIdx in 0 until displayCols) {
                                            val cellText = row.getOrNull(cIdx) ?: ""
                                            Box(
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .border(0.5.dp, Color(0xFFCBD5E1))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                NexusText(
                                                    text = cellText,
                                                    style = NexusTheme.typography.caption.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    color = if (isHeader) Color(0xFF0F172A) else Color(0xFF334155),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (hasTruncation) {
                            Spacer(modifier = Modifier.height(8.dp))
                            NexusText(
                                text = "Showing the first ${min(maxRows, sheet.rows.size)} rows and ${min(maxCols, sheet.colsCount)} columns. The PDF includes everything.",
                                style = NexusTheme.typography.caption.copy(
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF707070)
                                )
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            NexusText(
                                text = "This sheet is empty.",
                                style = NexusTheme.typography.body.copy(color = Color(0xFFA3A3A3))
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
                            text = if (isSelected) "Deselect this sheet" else "Select this sheet",
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

