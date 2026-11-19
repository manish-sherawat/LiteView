package com.nexus.core.ui.liquidglass.internal

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize

internal fun DrawScope.recordLayer(
    node: DelegatableNode,
    layer: GraphicsLayer,
    size: IntSize = this.size.toIntSize(),
    block: DrawScope.() -> Unit
) {
    if (size.width <= 0 || size.height <= 0) return
    val density = try { node.requireDensity() } catch (_: Exception) { drawContext.density }
    try {
        layer.record(size) {
            val prevDensity = drawContext.density
            drawContext.density = density
            try {
                this.block()
            } finally {
                drawContext.density = prevDensity
            }
        }
    } catch (_: Exception) {
    }
}
