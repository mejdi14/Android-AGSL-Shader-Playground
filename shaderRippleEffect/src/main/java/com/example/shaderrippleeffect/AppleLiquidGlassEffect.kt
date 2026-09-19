package com.example.shaderrippleeffect

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun AppleLiquidGlassEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val backdrop = rememberLiquidGlassBackdrop()
    val glassWidth = 252.dp
    val glassHeight = 176.dp
    val glassWidthPx = with(density) { glassWidth.toPx() }
    val glassHeightPx = with(density) { glassHeight.toPx() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var glassPosition by remember { mutableStateOf(Offset.Unspecified) }

    fun constrained(position: Offset): Offset = Offset(
        x = position.x.coerceIn(
            0f,
            (viewportSize.width - glassWidthPx).coerceAtLeast(0f)
        ),
        y = position.y.coerceIn(
            0f,
            (viewportSize.height - glassHeightPx).coerceAtLeast(0f)
        )
    )

    LaunchedEffect(viewportSize, glassWidthPx, glassHeightPx) {
        if (viewportSize == IntSize.Zero) return@LaunchedEffect
        glassPosition = if (glassPosition.isSpecified) {
            constrained(glassPosition)
        } else {
            constrained(
                Offset(
                    x = (viewportSize.width - glassWidthPx) * 0.5f,
                    y = (viewportSize.height - glassHeightPx) * 0.42f
                )
            )
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .recordLiquidGlassBackdrop(backdrop)
        ) {
            content()
        }

        if (glassPosition.isSpecified) {
            LiquidGlassSurface(
                backdrop = backdrop,
                shape = RoundedCornerShape(GlassCornerRadius),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            glassPosition.x.roundToInt(),
                            glassPosition.y.roundToInt()
                        )
                    }
                    .size(glassWidth, glassHeight)
                    .pointerInput(viewportSize, glassWidthPx, glassHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            glassPosition = constrained(glassPosition + dragAmount)
                        }
                    }
            )
        }
    }
}
