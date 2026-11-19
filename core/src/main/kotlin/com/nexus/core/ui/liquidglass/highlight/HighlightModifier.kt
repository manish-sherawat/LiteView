package com.nexus.core.ui.liquidglass.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.nexus.core.ui.liquidglass.RuntimeShaderCacheImpl
import com.nexus.core.ui.liquidglass.internal.ShapeProvider
import com.nexus.core.ui.liquidglass.internal.blur
import com.nexus.core.ui.liquidglass.internal.clipOutline
import com.nexus.core.ui.liquidglass.internal.setRuntimeShader
import com.nexus.core.ui.liquidglass.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false
        return shapeProvider == other.shapeProvider && highlight == other.highlight
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    override fun ContentDrawScope.draw() {
        if (size.width <= 0f || size.height <= 0f) return drawContent()
        val hl = highlight()
        if (hl == null || hl.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val hLayer = highlightLayer
        if (hLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val safeW = ceil(size.width).toInt() + 2
            val safeH = ceil(size.height).toInt() + 2
            if (safeW <= 0 || safeH <= 0) return
            val safeSize = IntSize(safeW, safeH)

            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
            val clipP =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            configurePaint(hl)

            hLayer.alpha = hl.alpha
            hLayer.blendMode = hl.style.blendMode
            try {
                hLayer.record(safeSize) {
                    translate(1f, 1f) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipP)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }

                translate(-1f, -1f) {
                    drawLayer(hLayer)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        runtimeShaderCache.clear()
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        paint.color = highlight.style.color
        paint.strokeWidth = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        paint.blur(highlight.blurRadius.toPx())
        if (isRuntimeShaderSupported()) {
            val shader =
                with(highlight.style) {
                    createShader(
                        shape = shapeProvider.shape,
                        runtimeShaderCache = runtimeShaderCache
                    )
                }
            paint.setRuntimeShader(shader)
        }
    }
}
