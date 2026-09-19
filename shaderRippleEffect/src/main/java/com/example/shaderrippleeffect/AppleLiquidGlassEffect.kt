package com.example.shaderrippleeffect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Full-screen showcase wrapper for a draggable liquid-glass capsule.
 *
 * Unlike the previous content-filter implementation, this records [content] into one live
 * backdrop layer and renders the capsule as a sibling. The glass can therefore use a separately
 * blurred body and sharp refractive bevel while [glassContent] stays perfectly crisp.
 */
@Composable
fun AppleLiquidGlassEffect(
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassDefaults.Clear,
    glassShape: LiquidGlassShape = LiquidGlassShape.Capsule,
    glassContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val backdrop = rememberBackdropProviderState()
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var targetPointer by remember { mutableStateOf(Offset.Zero) }
    var pointerInitialized by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(false) }

    val maxLensWidth = with(density) { 330.dp.toPx() }
    val maxLensHeight = with(density) { 132.dp.toPx() }
    val lensWidth = min(width * 0.76f, maxLensWidth)
    val lensHeight = min(height * 0.18f, maxLensHeight)

    val animatedPointer = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val activeState by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquid glass press"
    )

    fun constrainPointer(pointer: Offset): Offset {
        val halfWidth = lensWidth * 0.5f
        val halfHeight = lensHeight * 0.5f
        return Offset(
            x = pointer.x.coerceIn(halfWidth, (width - halfWidth).coerceAtLeast(halfWidth)),
            y = pointer.y.coerceIn(halfHeight, (height - halfHeight).coerceAtLeast(halfHeight))
        )
    }

    LaunchedEffect(width, height, lensWidth, lensHeight) {
        if (!pointerInitialized && width > 0f && height > 0f) {
            targetPointer = Offset(width * 0.5f, height * 0.63f)
            animatedPointer.snapTo(targetPointer)
            pointerInitialized = true
        } else if (pointerInitialized) {
            targetPointer = constrainPointer(targetPointer)
        }
    }

    LaunchedEffect(targetPointer, pointerInitialized) {
        if (pointerInitialized) {
            animatedPointer.animateTo(
                targetValue = targetPointer,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = 460f
                )
            )
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
            .pointerInput(width, height, lensWidth, lensHeight) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isActive = true
                        targetPointer = constrainPointer(offset)
                    },
                    onDragEnd = { isActive = false },
                    onDragCancel = { isActive = false },
                    onDrag = { change, _ ->
                        targetPointer = constrainPointer(change.position)
                        change.consume()
                    }
                )
            }
            .pointerInput(width, height, lensWidth, lensHeight) {
                detectTapGestures(
                    onPress = { offset ->
                        isActive = true
                        targetPointer = constrainPointer(offset)
                        tryAwaitRelease()
                        isActive = false
                    }
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .liquidGlassBackdropProvider(backdrop)
        ) {
            content()
        }

        if (pointerInitialized && lensWidth > 0f && lensHeight > 0f) {
            val renderedPointer = animatedPointer.value
            val motion = targetPointer - renderedPointer
            val horizontalStretch = abs(motion.x) / lensWidth.coerceAtLeast(1f)
            val verticalStretch = abs(motion.y) / lensHeight.coerceAtLeast(1f)
            val baseScale = 1f + activeState * 0.032f
            val touchPosition = Offset(
                x = lensWidth * 0.5f + motion.x,
                y = lensHeight * 0.5f + motion.y
            )

            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (renderedPointer.x - lensWidth * 0.5f).roundToInt(),
                            y = (renderedPointer.y - lensHeight * 0.5f).roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { lensWidth.toDp() },
                        height = with(density) { lensHeight.toDp() }
                    )
                    .graphicsLayer {
                        scaleX = baseScale + horizontalStretch.coerceAtMost(0.07f)
                        scaleY = baseScale + verticalStretch.coerceAtMost(0.07f)
                    }
                    .liquidGlass(
                        backdrop = backdrop,
                        style = style,
                        shape = glassShape,
                        interaction = LiquidGlassInteraction(
                            pressProgress = activeState,
                            touchPosition = touchPosition,
                            motion = motion
                        )
                    ),
                content = glassContent
            )
        }
    }
}
