package com.example.shaderrippleeffect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

/**
 * Holds the single live display-list recording used by every liquid-glass surface in a scene.
 * The layer remains on the GPU; it is never converted to a bitmap or read back to the CPU.
 */
@Stable
class BackdropProviderState internal constructor(
    internal val graphicsLayer: GraphicsLayer
) {
    internal var positionInRoot by mutableStateOf(Offset.Zero)
    internal var size by mutableStateOf(IntSize.Zero)

    val isReady: Boolean
        get() = size.width > 0 && size.height > 0
}

/** Creates and owns a reusable backdrop layer for the current composition. */
@Composable
fun rememberBackdropProviderState(): BackdropProviderState {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { BackdropProviderState(graphicsLayer) }
}

/**
 * Records this element once into [state] and draws that recording as the normal element content.
 * Glass surfaces should be siblings placed above this provider so they are not recursively captured.
 */
fun Modifier.liquidGlassBackdropProvider(
    state: BackdropProviderState
): Modifier =
    this
        .onGloballyPositioned { coordinates ->
            state.positionInRoot = coordinates.positionInRoot()
            state.size = coordinates.size
        }
        .drawWithContent {
            val layerSize = IntSize(
                width = size.width.toInt().coerceAtLeast(1),
                height = size.height.toInt().coerceAtLeast(1)
            )
            state.graphicsLayer.record(size = layerSize) {
                this@drawWithContent.drawContent()
            }
            drawLayer(state.graphicsLayer)
        }
