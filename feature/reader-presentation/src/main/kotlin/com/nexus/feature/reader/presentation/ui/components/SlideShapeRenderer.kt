package com.nexus.feature.reader.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.nexus.core.theme.NexusTheme
import com.nexus.feature.reader.presentation.engine.ParagraphInfo
import com.nexus.feature.reader.presentation.engine.ShapeFillInfo
import com.nexus.feature.reader.presentation.engine.SlideShapeType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders vector AutoShapes natively using Jetpack Compose Path and Canvas.
 */
@Composable
fun SlideShapeRenderer(
    shapeType: SlideShapeType,
    fillInfo: ShapeFillInfo,
    paragraphs: List<ParagraphInfo> = emptyList(),
    modifier: Modifier = Modifier,
    fontScale: Float = 1f
) {
    val fillColor = remember(fillInfo.fillColor) {
        fillInfo.fillColor?.let { Color(it) }
    }

    val gradientBrush = remember(fillInfo.gradientColors) {
        fillInfo.gradientColors?.takeIf { it.size >= 2 }?.let { colors ->
            Brush.linearGradient(colors.map { Color(it) })
        }
    }

    val strokeColor = remember(fillInfo.strokeColor) {
        fillInfo.strokeColor?.let { Color(it) }
    }

    // HIGH-24: For non-RECTANGLE shapes, always draw outline stroke in a subtle border if no strokeColor was parsed
    val effectiveStrokeColor = strokeColor ?: if (shapeType != SlideShapeType.RECTANGLE) {
        Color(0xFF94A3B8).copy(alpha = 0.7f)
    } else null

    val hasVisibleShape = (fillColor != null || gradientBrush != null || effectiveStrokeColor != null || shapeType == SlideShapeType.LINE)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (hasVisibleShape) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val drawBlock: DrawScope.() -> Unit = {
                    val strokeWidthPx = (fillInfo.strokeWidth.coerceAtLeast(1f)).dp.toPx()
                    val halfStroke = if (effectiveStrokeColor != null || shapeType == SlideShapeType.LINE) strokeWidthPx / 2f else 0f
                    val insetSize = Size(
                        (size.width - 2 * halfStroke).coerceAtLeast(1f),
                        (size.height - 2 * halfStroke).coerceAtLeast(1f)
                    )

                    translate(left = halfStroke, top = halfStroke) {
                        val path = createShapePath(shapeType, insetSize)

                        // Draw shape fill (except for pure LINE)
                        if (shapeType != SlideShapeType.LINE) {
                            if (gradientBrush != null) {
                                drawPath(path = path, brush = gradientBrush, style = Fill)
                            } else if (fillColor != null) {
                                drawPath(path = path, color = fillColor, style = Fill)
                            }
                        }

                        // Draw outline stroke or line
                        val lineOrStrokeColor = effectiveStrokeColor ?: fillColor ?: Color(0xFF94A3B8)
                        val pathEffect = if (fillInfo.isDashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null

                        if (shapeType == SlideShapeType.LINE || effectiveStrokeColor != null) {
                            drawPath(
                                path = path,
                                color = lineOrStrokeColor,
                                style = Stroke(width = strokeWidthPx, pathEffect = pathEffect)
                            )
                        }
                    }
                }

                if (fillInfo.rotation != 0f) {
                    rotate(fillInfo.rotation) {
                        drawBlock()
                    }
                } else {
                    drawBlock()
                }
            }
        }

        // Render inner text if shape contains text
        if (paragraphs.isNotEmpty()) {
            val bgLuminance = fillColor?.let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue }
            val defaultTextColor = if (bgLuminance != null && bgLuminance < 0.45f) Color.White else NexusTheme.colors.textPrimary

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                SlideRichText(
                    paragraphs = paragraphs,
                    defaultColor = defaultTextColor,
                    fontScale = fontScale
                )
            }
        }
    }
}

/**
 * Maps standard PowerPoint preset geometries to Compose Path.
 */
private fun createShapePath(shapeType: SlideShapeType, size: Size): Path {
    val w = size.width
    val h = size.height
    val path = Path()

    when (shapeType) {
        SlideShapeType.RECTANGLE -> {
            path.addRect(Rect(0f, 0f, w, h))
        }

        SlideShapeType.ROUNDED_RECTANGLE -> {
            val radius = min(w, h) * 0.15f
            path.addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, w, h),
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }

        SlideShapeType.ELLIPSE -> {
            path.addOval(Rect(0f, 0f, w, h))
        }

        SlideShapeType.TRIANGLE -> {
            path.moveTo(w * 0.5f, 0f)
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
        }

        SlideShapeType.RIGHT_TRIANGLE -> {
            path.moveTo(0f, 0f)
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
        }

        SlideShapeType.DIAMOND -> {
            path.moveTo(w * 0.5f, 0f)
            path.lineTo(w, h * 0.5f)
            path.lineTo(w * 0.5f, h)
            path.lineTo(0f, h * 0.5f)
            path.close()
        }

        SlideShapeType.PARALLELOGRAM -> {
            val skew = w * 0.2f
            path.moveTo(skew, 0f)
            path.lineTo(w, 0f)
            path.lineTo(w - skew, h)
            path.lineTo(0f, h)
            path.close()
        }

        SlideShapeType.TRAPEZOID -> {
            val inset = w * 0.2f
            path.moveTo(inset, 0f)
            path.lineTo(w - inset, 0f)
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
        }

        SlideShapeType.PENTAGON -> {
            buildRegularPolygonPath(path, cx = w * 0.5f, cy = h * 0.5f, radiusX = w * 0.5f, radiusY = h * 0.5f, sides = 5)
        }

        SlideShapeType.HEXAGON -> {
            buildRegularPolygonPath(path, cx = w * 0.5f, cy = h * 0.5f, radiusX = w * 0.5f, radiusY = h * 0.5f, sides = 6)
        }

        SlideShapeType.OCTAGON -> {
            buildRegularPolygonPath(path, cx = w * 0.5f, cy = h * 0.5f, radiusX = w * 0.5f, radiusY = h * 0.5f, sides = 8)
        }

        SlideShapeType.ARROW_RIGHT -> {
            val shaftH = h * 0.4f
            val shaftY = (h - shaftH) * 0.5f
            val headW = w * 0.35f
            path.moveTo(0f, shaftY)
            path.lineTo(w - headW, shaftY)
            path.lineTo(w - headW, 0f)
            path.lineTo(w, h * 0.5f)
            path.lineTo(w - headW, h)
            path.lineTo(w - headW, shaftY + shaftH)
            path.lineTo(0f, shaftY + shaftH)
            path.close()
        }

        SlideShapeType.ARROW_LEFT -> {
            val shaftH = h * 0.4f
            val shaftY = (h - shaftH) * 0.5f
            val headW = w * 0.35f
            path.moveTo(headW, 0f)
            path.lineTo(headW, shaftY)
            path.lineTo(w, shaftY)
            path.lineTo(w, shaftY + shaftH)
            path.lineTo(headW, shaftY + shaftH)
            path.lineTo(headW, h)
            path.lineTo(0f, h * 0.5f)
            path.close()
        }

        SlideShapeType.ARROW_UP -> {
            val shaftW = w * 0.4f
            val shaftX = (w - shaftW) * 0.5f
            val headH = h * 0.35f
            path.moveTo(w * 0.5f, 0f)
            path.lineTo(w, headH)
            path.lineTo(shaftX + shaftW, headH)
            path.lineTo(shaftX + shaftW, h)
            path.lineTo(shaftX, h)
            path.lineTo(shaftX, headH)
            path.lineTo(0f, headH)
            path.close()
        }

        SlideShapeType.ARROW_DOWN -> {
            val shaftW = w * 0.4f
            val shaftX = (w - shaftW) * 0.5f
            val headH = h * 0.35f
            path.moveTo(shaftX, 0f)
            path.lineTo(shaftX + shaftW, 0f)
            path.lineTo(shaftX + shaftW, h - headH)
            path.lineTo(w, h - headH)
            path.lineTo(w * 0.5f, h)
            path.lineTo(0f, h - headH)
            path.lineTo(shaftX, h - headH)
            path.close()
        }

        SlideShapeType.CHEVRON -> {
            val pointW = w * 0.25f
            path.moveTo(0f, 0f)
            path.lineTo(w - pointW, 0f)
            path.lineTo(w, h * 0.5f)
            path.lineTo(w - pointW, h)
            path.lineTo(0f, h)
            path.lineTo(pointW, h * 0.5f)
            path.close()
        }

        SlideShapeType.STAR_4 -> {
            buildStarPath(path, cx = w * 0.5f, cy = h * 0.5f, outerRx = w * 0.5f, outerRy = h * 0.5f, innerRx = w * 0.22f, innerRy = h * 0.22f, points = 4)
        }

        SlideShapeType.STAR_5 -> {
            buildStarPath(path, cx = w * 0.5f, cy = h * 0.5f, outerRx = w * 0.5f, outerRy = h * 0.5f, innerRx = w * 0.22f, innerRy = h * 0.22f, points = 5)
        }

        SlideShapeType.STAR_6 -> {
            buildStarPath(path, cx = w * 0.5f, cy = h * 0.5f, outerRx = w * 0.5f, outerRy = h * 0.5f, innerRx = w * 0.25f, innerRy = h * 0.25f, points = 6)
        }

        SlideShapeType.STAR_8 -> {
            buildStarPath(path, cx = w * 0.5f, cy = h * 0.5f, outerRx = w * 0.5f, outerRy = h * 0.5f, innerRx = w * 0.25f, innerRy = h * 0.25f, points = 8)
        }

        SlideShapeType.CALLOUT_BUBBLE -> {
            val bubbleH = h * 0.8f
            val radius = min(w, bubbleH) * 0.15f
            path.addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, w, bubbleH),
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
            // Speech tail pointer
            val tailStart = w * 0.25f
            val tailEnd = w * 0.45f
            path.moveTo(tailStart, bubbleH)
            path.lineTo(w * 0.15f, h)
            path.lineTo(tailEnd, bubbleH)
            path.close()
        }

        SlideShapeType.CLOUD_CALLOUT, SlideShapeType.CLOUD -> {
            val r = min(w, h) * 0.2f
            path.addRoundRect(
                RoundRect(
                    rect = Rect(w * 0.1f, h * 0.15f, w * 0.9f, h * 0.85f),
                    cornerRadius = CornerRadius(r, r)
                )
            )
        }

        SlideShapeType.FLOWCHART_PROCESS -> {
            path.addRect(Rect(0f, 0f, w, h))
        }

        SlideShapeType.FLOWCHART_DECISION -> {
            path.moveTo(w * 0.5f, 0f)
            path.lineTo(w, h * 0.5f)
            path.lineTo(w * 0.5f, h)
            path.lineTo(0f, h * 0.5f)
            path.close()
        }

        SlideShapeType.FLOWCHART_TERMINATOR -> {
            val pillRadius = h * 0.5f
            path.addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, w, h),
                    cornerRadius = CornerRadius(pillRadius, pillRadius)
                )
            )
        }

        SlideShapeType.FLOWCHART_DOCUMENT -> {
            val waveH = h * 0.12f
            path.moveTo(0f, 0f)
            path.lineTo(w, 0f)
            path.lineTo(w, h - waveH)
            path.quadraticTo(w * 0.75f, h, w * 0.5f, h - waveH)
            path.quadraticTo(w * 0.25f, h - 2 * waveH, 0f, h - waveH)
            path.close()
        }

        SlideShapeType.FLOWCHART_INPUT_OUTPUT -> {
            val skew = w * 0.18f
            path.moveTo(skew, 0f)
            path.lineTo(w, 0f)
            path.lineTo(w - skew, h)
            path.lineTo(0f, h)
            path.close()
        }

        SlideShapeType.HEART -> {
            path.moveTo(w * 0.5f, h * 0.8f)
            path.cubicTo(w * 0.15f, h * 0.5f, 0f, h * 0.25f, w * 0.25f, h * 0.1f)
            path.cubicTo(w * 0.45f, 0f, w * 0.5f, h * 0.25f, w * 0.5f, h * 0.25f)
            path.cubicTo(w * 0.5f, h * 0.25f, w * 0.55f, 0f, w * 0.75f, h * 0.1f)
            path.cubicTo(w, h * 0.25f, w * 0.85f, h * 0.5f, w * 0.5f, h * 0.8f)
            path.close()
        }

        SlideShapeType.LINE -> {
            path.moveTo(0f, h * 0.5f)
            path.lineTo(w, h * 0.5f)
        }

        SlideShapeType.CROSS -> {
            val armW = w * 0.33f
            val armH = h * 0.33f
            val left = (w - armW) * 0.5f
            val top = (h - armH) * 0.5f
            path.moveTo(left, 0f)
            path.lineTo(left + armW, 0f)
            path.lineTo(left + armW, top)
            path.lineTo(w, top)
            path.lineTo(w, top + armH)
            path.lineTo(left + armW, top + armH)
            path.lineTo(left + armW, h)
            path.lineTo(left, h)
            path.lineTo(left, top + armH)
            path.lineTo(0f, top + armH)
            path.lineTo(0f, top)
            path.lineTo(left, top)
            path.close()
        }

        SlideShapeType.DONUT -> {
            path.addOval(Rect(0f, 0f, w, h))
            // Inner cutout
            val innerR = min(w, h) * 0.25f
            path.addOval(Rect(w * 0.5f - innerR, h * 0.5f - innerR, w * 0.5f + innerR, h * 0.5f + innerR))
        }

        SlideShapeType.CUBE -> {
            val depth = min(w, h) * 0.2f
            // Front face
            path.moveTo(0f, depth)
            path.lineTo(w - depth, depth)
            path.lineTo(w - depth, h)
            path.lineTo(0f, h)
            path.close()
            // Top face
            path.moveTo(0f, depth)
            path.lineTo(depth, 0f)
            path.lineTo(w, 0f)
            path.lineTo(w - depth, depth)
            path.close()
            // Right face
            path.moveTo(w - depth, depth)
            path.lineTo(w, 0f)
            path.lineTo(w, h - depth)
            path.lineTo(w - depth, h)
            path.close()
        }

        SlideShapeType.CYLINDER -> {
            val capH = h * 0.15f
            path.moveTo(0f, capH)
            path.lineTo(0f, h - capH)
            path.quadraticTo(w * 0.5f, h, w, h - capH)
            path.lineTo(w, capH)
            path.quadraticTo(w * 0.5f, 0f, 0f, capH)
            path.close()
        }

        SlideShapeType.PLAQUE -> {
            val r = min(w, h) * 0.2f
            path.moveTo(r, 0f)
            path.lineTo(w - r, 0f)
            path.quadraticTo(w, r * 0.5f, w - r * 0.2f, r)
            path.lineTo(w - r * 0.2f, h - r)
            path.quadraticTo(w, h - r * 0.5f, w - r, h)
            path.lineTo(r, h)
            path.quadraticTo(0f, h - r * 0.5f, r * 0.2f, h - r)
            path.lineTo(r * 0.2f, r)
            path.quadraticTo(0f, r * 0.5f, r, 0f)
            path.close()
        }

        SlideShapeType.FOLDED_CORNER -> {
            val fold = min(w, h) * 0.2f
            path.moveTo(0f, 0f)
            path.lineTo(w - fold, 0f)
            path.lineTo(w, fold)
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            // Fold line
            path.moveTo(w - fold, 0f)
            path.lineTo(w - fold, fold)
            path.lineTo(w, fold)
        }

        SlideShapeType.LEFT_RIGHT_ARROW -> {
            val headW = w * 0.25f
            val shaftH = h * 0.4f
            val topShaft = (h - shaftH) * 0.5f
            val bottomShaft = topShaft + shaftH
            path.moveTo(0f, h * 0.5f)
            path.lineTo(headW, 0f)
            path.lineTo(headW, topShaft)
            path.lineTo(w - headW, topShaft)
            path.lineTo(w - headW, 0f)
            path.lineTo(w, h * 0.5f)
            path.lineTo(w - headW, h)
            path.lineTo(w - headW, bottomShaft)
            path.lineTo(headW, bottomShaft)
            path.lineTo(headW, h)
            path.close()
        }

        SlideShapeType.UP_DOWN_ARROW -> {
            val headH = h * 0.25f
            val shaftW = w * 0.4f
            val leftShaft = (w - shaftW) * 0.5f
            val rightShaft = leftShaft + shaftW
            path.moveTo(w * 0.5f, 0f)
            path.lineTo(w, headH)
            path.lineTo(rightShaft, headH)
            path.lineTo(rightShaft, h - headH)
            path.lineTo(w, h - headH)
            path.lineTo(w * 0.5f, h)
            path.lineTo(0f, h - headH)
            path.lineTo(leftShaft, h - headH)
            path.lineTo(leftShaft, headH)
            path.lineTo(0f, headH)
            path.close()
        }

        SlideShapeType.SUN, SlideShapeType.LIGHTNING_BOLT, SlideShapeType.UNKNOWN -> {
            val radius = min(w, h) * 0.1f
            path.addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, w, h),
                    cornerRadius = CornerRadius(radius, radius)
                )
            )
        }
    }

    return path
}

private fun buildRegularPolygonPath(
    path: Path,
    cx: Float,
    cy: Float,
    radiusX: Float,
    radiusY: Float,
    sides: Int
) {
    val angleStep = (2 * PI) / sides
    val startAngle = -PI / 2 // Point top vertex upward
    for (i in 0 until sides) {
        val angle = startAngle + i * angleStep
        val x = cx + radiusX * cos(angle).toFloat()
        val y = cy + radiusY * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

private fun buildStarPath(
    path: Path,
    cx: Float,
    cy: Float,
    outerRx: Float,
    outerRy: Float,
    innerRx: Float,
    innerRy: Float,
    points: Int
) {
    val totalVertices = points * 2
    val angleStep = (2 * PI) / totalVertices
    val startAngle = -PI / 2
    for (i in 0 until totalVertices) {
        val angle = startAngle + i * angleStep
        val rx = if (i % 2 == 0) outerRx else innerRx
        val ry = if (i % 2 == 0) outerRy else innerRy
        val x = cx + rx * cos(angle).toFloat()
        val y = cy + ry * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}
