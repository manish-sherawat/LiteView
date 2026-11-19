package com.nexus.core.ui.liquidglass.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.nexus.core.ui.liquidglass.Backdrop
import com.nexus.core.ui.liquidglass.internal.InverseLayerScope

private val DefaultOnDraw: ContentDrawScope.() -> Unit = { drawContent() }

@Composable
fun rememberLayerBackdrop(
    graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
    onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw
): LayerBackdrop {
    return remember(graphicsLayer, onDraw) {
        LayerBackdrop(graphicsLayer, onDraw)
    }
}

@Stable
class LayerBackdrop internal constructor(
    val graphicsLayer: GraphicsLayer,
    internal val onDraw: ContentDrawScope.() -> Unit
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    internal var layerCoordinates: LayoutCoordinates? by mutableStateOf(null)

    private var inverseLayerScope: InverseLayerScope? = null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val coords = coordinates ?: return
        val lCoords = layerCoordinates ?: return
        if (!coords.isAttached || !lCoords.isAttached) return
        try {
            withTransform({
                if (layerBlock != null) {
                    with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
                }
                val offset =
                    try {
                        if (coords.isAttached && lCoords.isAttached) {
                            lCoords.localPositionOf(coords)
                        } else {
                            androidx.compose.ui.geometry.Offset.Zero
                        }
                    } catch (_: Exception) {
                        try {
                            if (coords.isAttached && lCoords.isAttached) {
                                coords.positionInWindow() - lCoords.positionInWindow()
                            } else {
                                androidx.compose.ui.geometry.Offset.Zero
                            }
                        } catch (_: Exception) {
                            androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                translate(-offset.x, -offset.y)
            }) {
                try {
                    drawLayer(graphicsLayer)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}
