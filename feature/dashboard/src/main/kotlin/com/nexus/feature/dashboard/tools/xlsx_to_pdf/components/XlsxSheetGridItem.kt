package com.nexus.feature.dashboard.tools.xlsx_to_pdf.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusText
import com.nexus.core.ui.animations.springBounceClick
import com.nexus.feature.dashboard.tools.xlsx_to_pdf.SheetData
import kotlin.math.min

@Composable
fun XlsxSheetGridItem(
    item: SheetData,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(0xFF0A0A0A)
    val lineDefault = Color(0xFFEBEBEB)
    val lineStrong = Color(0xFFCFCFCF)

    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else lineDefault,
        animationSpec = tween(150),
        label = "xlsxCardBorder"
    )

    val cardBgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFAFAFA) else NexusTheme.colors.surface,
        animationSpec = tween(150),
        label = "xlsxCardBg"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(150),
        label = "xlsxCheckScale"
    )

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // ── Main Sheet Card Button ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (isSelected) 4.dp else 1.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(cardBgColor)
                .border(2.dp, cardBorderColor, RoundedCornerShape(16.dp))
                .clickable { onToggleSelect() }
                .padding(10.dp)
        ) {
            // Miniature Spreadsheet View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, lineDefault, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (item.rows.isNotEmpty()) {
                    MiniTableView(
                        rows = item.rows,
                        maxR = 6,
                        maxC = 4
                    )
                } else {
                    NexusText(
                        text = "Empty sheet",
                        style = NexusTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            color = Color(0xFFA3A3A3)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sheet Label with Dimension Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NexusText(
                    text = item.name,
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = NexusTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                NexusText(
                    text = "${item.rows.size} × ${item.colsCount}",
                    style = NexusTheme.typography.caption.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = NexusTheme.colors.textSecondary
                )
            }
        }

        // ── Top-Left: Selection Check Circle ─────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 18.dp)
                .scale(checkScale)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentColor else Color(0x470A0A0A))
                .border(2.dp, Color.White, CircleShape)
                .clickable { onToggleSelect() },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── Top-Right: Eye Preview Button ────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-18).dp, y = 18.dp)
                .size(30.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xF0FFFFFF))
                .border(1.dp, lineStrong.copy(alpha = 0.5f), CircleShape)
                .springBounceClick { onOpenPreview() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = "Preview sheet ${item.name}",
                tint = Color(0xFF0A0A0A),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MiniTableView(
    rows: List<List<String>>,
    maxR: Int = 6,
    maxC: Int = 4
) {
    val displayRows = rows.take(maxR)
    val totalCols = displayRows.maxOfOrNull { it.size } ?: 0
    val displayCols = min(totalCols, maxC)

    if (displayCols == 0) return

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        displayRows.forEachIndexed { rIdx, row ->
            val isHeader = rIdx == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (isHeader) Color(0xFFF1F5F9) else Color.Transparent)
                    .border(0.5.dp, Color(0xFFE2E8F0)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (cIdx in 0 until displayCols) {
                    val cellText = row.getOrNull(cIdx) ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.5.dp, Color(0xFFE2E8F0))
                            .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        NexusText(
                            text = cellText,
                            style = NexusTheme.typography.caption.copy(
                                fontSize = 9.sp,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isHeader) Color(0xFF0F172A) else Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

