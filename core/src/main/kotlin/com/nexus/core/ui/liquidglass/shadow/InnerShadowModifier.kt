package com.nexus.core.ui.liquidglass.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import com.nexus.core.ui.liquidglass.internal.ShapeProvider
import com.nexus.core.ui.liquidglass.internal.clipOutline
import com.nexus.core.ui.liquidglass.isRenderEffectSupported

internal class InnerShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> InnerShadow?
) : ModifierNodeElement<InnerShadowNode>() {

    override fun create(): InnerShadowNode = InnerShadowNode(shapeProvider, shadow)

    override fun update(node: InnerShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false
        return shapeProvider == other.shapeProvider && shadow == other.shadow
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

internal class InnerShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> InnerShadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private var clipPath: Path? = null
    private var prevRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return
        if (size.width <= 0f || size.height <= 0f) return

        val s = shadow() ?: return

        val sLayer = shadowLayer
        if (sLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val radius = s.radius.toPx()
            val offsetX = s.offset.x.toPx()
            val offsetY = s.offset.y.toPx()

            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
            val clipP =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            configurePaint(s)

            sLayer.alpha = s.alpha
            sLayer.blendMode = s.blendMode
            if (prevRadius != radius) {
                sLayer.renderEffect =
                    if (radius > 0f) {
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                prevRadius = radius
            }
            try {
                sLayer.record {
                    val canvas = drawContext.canvas
                    canvas.save()
                    canvas.clipOutline(outline, clipP)
                    canvas.drawOutline(outline, paint)
                    canvas.translate(offsetX, offsetY)
                    canvas.drawOutline(outline, ShadowMaskPaint)
                    canvas.translate(-offsetX, -offsetY)
                    canvas.restore()
                }

                val canvas = drawContext.canvas
                canvas.save()
                canvas.clipOutline(outline, clipP)
                drawLayer(sLayer)
                canvas.restore()
            } catch (_: Exception) {
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer =
            graphicsContext.createGraphicsLayer().apply {
                compositingStrategy = CompositingStrategy.Offscreen
            }
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
    }

    private fun DrawScope.configurePaint(shadow: InnerShadow) {
        paint.color = shadow.color
    }
}

private val ShadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}
