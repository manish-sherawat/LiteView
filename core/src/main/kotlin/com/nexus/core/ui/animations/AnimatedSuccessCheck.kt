package com.nexus.core.ui.animations

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedSuccessCheck(
    modifier: Modifier = Modifier,
    color: Color = Color.Green,
    strokeWidth: Float = 12f
) {
    val transition = updateTransition(targetState = true, label = "SuccessCheck")

    val alpha by transition.animateFloat(
        transitionSpec = { tween(500, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)) },
        label = "alpha"
    ) { state -> if (state) 1f else 0f }

    val rotation by transition.animateFloat(
        transitionSpec = { tween(500, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)) },
        label = "rotation"
    ) { state -> if (state) 0f else 80f }

    val translateY by transition.animateFloat(
        transitionSpec = { tween(500, easing = CubicBezierEasing(0.34f, 1.35f, 0.64f, 1f)) },
        label = "translateY"
    ) { state -> if (state) 0f else 40f }
    
    val blurRadius by transition.animateFloat(
        transitionSpec = { tween(500, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)) },
        label = "blur"
    ) { state -> if (state) 0f else 10f }

    val pathProgress by transition.animateFloat(
        transitionSpec = { 
            tween(500, delayMillis = 80, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)) 
        },
        label = "pathProgress"
    ) { state -> if (state) 1f else 0f }

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                this.rotationZ = rotation
                this.translationY = translateY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.01f) {
                    this.renderEffect = android.graphics.RenderEffect.createBlurEffect(
                        blurRadius, 
                        blurRadius, 
                        android.graphics.Shader.TileMode.DECAL
                    ).asComposeRenderEffect()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val path = Path().apply {
                moveTo(14.dp.toPx(), 24.dp.toPx())
                lineTo(22.dp.toPx(), 32.dp.toPx())
                lineTo(36.dp.toPx(), 14.dp.toPx())
            }
            
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(path, false)
            val length = pathMeasure.length
            
            val dashEffect = PathEffect.dashPathEffect(
                floatArrayOf(length, length),
                phase = length - (length * pathProgress)
            )
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = dashEffect
                )
            )
        }
    }
}
