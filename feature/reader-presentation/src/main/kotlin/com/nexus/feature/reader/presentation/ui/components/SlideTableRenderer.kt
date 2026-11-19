package com.nexus.feature.reader.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.engine.CellVerticalAlignment
import com.nexus.feature.reader.presentation.engine.ParagraphInfo
import com.nexus.feature.reader.presentation.engine.TableCellInfo
import com.nexus.feature.reader.presentation.engine.TableRowInfo

/**
 * Advanced presentation table component supporting cell background shading,
 * custom borders, cell spans, and vertical text alignment.
 */
@Composable
fun SlideTableRenderer(
    tableRows: List<TableRowInfo>,
    fallbackRows: List<List<String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val defaultBorderColor = NexusTheme.colors.divider.copy(alpha = 0.5f)

    NexusSurface(
        shape = NexusTheme.shapes.small,
        color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f),
        borderWidth = 1.dp,
        borderColor = defaultBorderColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (tableRows.isNotEmpty()) {
                for ((rowIndex, row) in tableRows.withIndex()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        for (cell in row.cells) {
                            val cellWeight = cell.colSpan.toFloat().coerceAtLeast(1f)
                            // MEDIUM-25: Apply both colSpan (weight) and rowSpan (scaled minHeight and vertical distribution)
                            val cellMinHeight = 32.dp * cell.rowSpan.coerceAtLeast(1)
                            val cellVPadding = (6 + (cell.rowSpan.coerceAtLeast(1) - 1) * 4).dp
                            val alignment = when (cell.verticalAlignment) {
                                CellVerticalAlignment.TOP -> Alignment.TopStart
                                CellVerticalAlignment.CENTER -> Alignment.CenterStart
                                CellVerticalAlignment.BOTTOM -> Alignment.BottomStart
                            }
                            val cellBg = cell.backgroundColorArgb?.let { Color(it) } ?: Color.Transparent
                            val cellBorder = cell.borderColorArgb?.let { Color(it) } ?: defaultBorderColor

                            Box(
                                modifier = Modifier
                                    .weight(cellWeight)
                                    .defaultMinSize(minHeight = cellMinHeight)
                                    .background(cellBg)
                                    .border(width = (cell.borderWidth * 0.5f).dp, color = cellBorder)
                                    .padding(horizontal = 8.dp, vertical = cellVPadding),
                                contentAlignment = alignment
                            ) {
                                if (cell.spans.isNotEmpty()) {
                                    SlideRichText(
                                        paragraphs = listOf(ParagraphInfo(spans = cell.spans))
                                    )
                                } else {
                                    NexusText(
                                        text = cell.text,
                                        style = NexusTheme.typography.caption.copy(
                                            fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (fallbackRows.isNotEmpty()) {
                // Backward compatibility fallback
                for ((rowIndex, row) in fallbackRows.withIndex()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, defaultBorderColor)
                    ) {
                        for (cellText in row) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(0.5.dp, defaultBorderColor)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                NexusText(
                                    text = cellText,
                                    style = NexusTheme.typography.caption.copy(
                                        fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    ),
                                    color = NexusTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
