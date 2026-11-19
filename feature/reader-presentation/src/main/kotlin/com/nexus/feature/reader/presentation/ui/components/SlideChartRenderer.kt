package com.nexus.feature.reader.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.theme.NexusTheme
import com.nexus.core.ui.NexusSurface
import com.nexus.core.ui.NexusText
import com.nexus.feature.reader.presentation.engine.ChartSeries
import com.nexus.feature.reader.presentation.engine.ChartType
import kotlin.math.cos
import kotlin.math.sin

private val ChartColorPalette = listOf(
    Color(0xFF388AF6), // Nexus Blue
    Color(0xFF00C49F), // Vibrant Teal
    Color(0xFFFFBB28), // Golden Amber
    Color(0xFFFF8042), // Coral Orange
    Color(0xFF8884D8), // Lavender
    Color(0xFFEC4899), // Rose Pink
    Color(0xFF10B981)  // Emerald Green
)

/**
 * Hardware-accelerated presentation chart renderer for Column, Bar, Line, Pie, and Donut charts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlideChartRenderer(
    chartType: ChartType,
    categories: List<String>,
    series: List<ChartSeries>,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    NexusSurface(
        shape = NexusTheme.shapes.small,
        color = NexusTheme.colors.surfaceVariant.copy(alpha = 0.35f),
        borderWidth = 1.dp,
        borderColor = NexusTheme.colors.divider.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Chart Title
            if (!title.isNullOrBlank()) {
                NexusText(
                    text = title,
                    style = NexusTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = NexusTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Canvas Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                when (chartType) {
                    ChartType.COLUMN, ChartType.BAR -> {
                        ColumnBarChartCanvas(categories = categories, series = series)
                    }

                    ChartType.LINE, ChartType.AREA -> {
                        LineChartCanvas(categories = categories, series = series, isArea = (chartType == ChartType.AREA))
                    }

                    ChartType.PIE, ChartType.DONUT -> {
                        PieChartCanvas(series = series, isDonut = (chartType == ChartType.DONUT))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Legend
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                series.forEachIndexed { index, s ->
                    val color = s.colorArgb?.let { Color(it) } ?: ChartColorPalette[index % ChartColorPalette.size]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        NexusText(
                            text = s.name.ifBlank { "Series ${index + 1}" },
                            style = NexusTheme.typography.caption.copy(fontSize = 11.sp),
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnBarChartCanvas(
    categories: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier
) {
    val gridColor = NexusTheme.colors.divider.copy(alpha = 0.35f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bottomPadding = 20f
        val chartH = h - bottomPadding

        // Draw horizontal grid lines
        for (i in 0..4) {
            val y = (chartH / 4) * i
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val allValues = series.flatMap { it.values }
        // MEDIUM-26: Guard against divide-by-zero on empty series values
        if (allValues.isEmpty() || (allValues.maxOrNull() ?: 0f) <= 0f) return@Canvas
        val maxVal = (allValues.maxOrNull() ?: 100f).coerceAtLeast(1f)
        val catCount = categories.size.coerceAtLeast(1)
        val catWidth = w / catCount
        val seriesCount = series.size.coerceAtLeast(1)
        val barGroupWidth = catWidth * 0.7f
        val barWidth = (barGroupWidth / seriesCount).coerceAtLeast(4f)

        for (c in 0 until catCount) {
            val groupStartX = c * catWidth + (catWidth - barGroupWidth) * 0.5f

            for (s in 0 until seriesCount) {
                val valItem = series[s].values.getOrNull(c) ?: 0f
                val barH = (valItem / maxVal) * chartH
                val barX = groupStartX + s * barWidth
                val barY = chartH - barH

                val barColor = series[s].colorArgb?.let { Color(it) }
                    ?: ChartColorPalette[s % ChartColorPalette.size]

                drawRect(
                    color = barColor,
                    topLeft = Offset(barX, barY),
                    size = Size(barWidth * 0.85f, barH),
                    style = Fill
                )
            }
        }
    }
}

@Composable
private fun LineChartCanvas(
    categories: List<String>,
    series: List<ChartSeries>,
    isArea: Boolean,
    modifier: Modifier = Modifier
) {
    val gridColor = NexusTheme.colors.divider.copy(alpha = 0.35f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bottomPadding = 20f
        val chartH = h - bottomPadding

        // Grid lines
        for (i in 0..4) {
            val y = (chartH / 4) * i
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val allValues = series.flatMap { it.values }
        // MEDIUM-26: Guard against divide-by-zero on empty series values
        if (allValues.isEmpty() || (allValues.maxOrNull() ?: 0f) <= 0f) return@Canvas
        val maxVal = (allValues.maxOrNull() ?: 100f).coerceAtLeast(1f)
        val catCount = categories.size.coerceAtLeast(2)
        val stepX = w / (catCount - 1).coerceAtLeast(1)

        for ((sIndex, s) in series.withIndex()) {
            val color = s.colorArgb?.let { Color(it) } ?: ChartColorPalette[sIndex % ChartColorPalette.size]
            val path = Path()
            val areaPath = Path()

            var started = false
            for (c in 0 until s.values.size.coerceAtMost(catCount)) {
                val v = s.values[c]
                val x = c * stepX
                val y = chartH - (v / maxVal) * chartH

                if (!started) {
                    path.moveTo(x, y)
                    areaPath.moveTo(x, chartH)
                    areaPath.lineTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }

                // Point vertex dot
                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            if (started) {
                val lastX = (s.values.size - 1).coerceAtLeast(0) * stepX
                areaPath.lineTo(lastX, chartH)
                areaPath.close()

                if (isArea) {
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.05f))
                        )
                    )
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun PieChartCanvas(
    series: List<ChartSeries>,
    isDonut: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val d = size.minDimension * 0.85f
        val rect = Rect(
            offset = Offset((size.width - d) / 2f, (size.height - d) / 2f),
            size = Size(d, d)
        )

        val total = series.sumOf { (it.values.firstOrNull() ?: 0f).toDouble() }.toFloat()
        // MEDIUM-26: Guard against divide-by-zero on empty/zero total
        if (total <= 0f) return@Canvas
        var startAngle = -90f

        for ((index, s) in series.withIndex()) {
            val v = (s.values.firstOrNull() ?: 0f).coerceAtLeast(0f)
            val sweep = (v / total) * 360f
            val color = s.colorArgb?.let { Color(it) } ?: ChartColorPalette[index % ChartColorPalette.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Fill
            )

            startAngle += sweep
        }

        if (isDonut) {
            val innerD = d * 0.5f
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = innerD * 0.5f,
                center = rect.center
            )
        }
    }
}
