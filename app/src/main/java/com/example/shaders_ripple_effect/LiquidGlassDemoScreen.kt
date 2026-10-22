package com.example.shaders_ripple_effect

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.GuideLiquidGlass
import com.example.shaderrippleeffect.glassBackdropSource
import com.example.shaderrippleeffect.rememberGlassBackdrop
import kotlin.math.roundToInt

private data class StickerPlacement(
    @DrawableRes val drawable: Int,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
)

private val StickerPlacements = listOf(
    StickerPlacement(R.drawable.sticker_sun, -0.02f, 0.06f, 0.48f, -13f),
    StickerPlacement(R.drawable.sticker_mushroom, 0.52f, 0.10f, 0.44f, 11f),
    StickerPlacement(R.drawable.sticker_skate, -0.04f, 0.27f, 0.57f, -9f),
    StickerPlacement(R.drawable.sticker_controller, 0.49f, 0.33f, 0.53f, 8f),
    StickerPlacement(R.drawable.sticker_pizza, 0.06f, 0.53f, 0.49f, 14f),
    StickerPlacement(R.drawable.sticker_camera, 0.47f, 0.58f, 0.55f, -8f),
)

@Composable
fun AppleLiquidGlassDemoScreen() {
    val backdrop = rememberGlassBackdrop()
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        StickerBackground(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop)
        )
        GuideLiquidGlass(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    IntOffset(
                        dragOffset.x.roundToInt(),
                        dragOffset.y.roundToInt()
                    )
                }
                .size(280.dp, 180.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                },
            chromaticAberration = dragging,
        )
    }
}

@Composable
private fun StickerBackground(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.background(Color(0xFFF1F0FA))
    ) {
        val referenceWidth = minOf(maxWidth, 420.dp)
        StickerPlacements.forEach { placement ->
            Sticker(
                drawable = placement.drawable,
                x = maxWidth * placement.x,
                y = maxHeight * placement.y,
                size = referenceWidth * placement.scale,
                rotation = placement.rotation,
            )
        }
    }
}

@Composable
private fun BoxScope.Sticker(
    @DrawableRes drawable: Int,
    x: Dp,
    y: Dp,
    size: Dp,
    rotation: Float,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .graphicsLayer { rotationZ = rotation },
    )
}
