package com.example.shaderrippleeffect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeConsumed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun MotionBlurEffect(
    modifier: Modifier = Modifier,
    intensity: Float = 2f,
    falloffRadius: Float = 390f,
    content: @Composable () -> Unit
) {
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }
    var velocity by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val positionBuffer = remember { mutableStateListOf<Pair<Offset, Long>>() }

    LaunchedEffect(isDragging) {
        if (!isDragging && velocity != Offset.Zero) {
            while (velocity.getDistance() > 0.1f) {
                velocity = velocity * 0.92f
                delay(16)
            }
            velocity = Offset.Zero
        }
    }

    val shaderCode = """
        uniform shader inputShader;
        uniform float2 uPointerPosition;
        uniform float2 uVelocity;
        uniform float uIntensity;
        uniform float uFalloffRadius;
        
        half4 main(float2 fragCoord) {
            float2 p = fragCoord;
            float2 l = uPointerPosition;
            float2 v = uVelocity;
            
            float2 m = -v * pow(clamp(1.0 - length(l - p) / uFalloffRadius, 0.0, 1.0), 2.0) * uIntensity;
            
            half3 c = half3(0.0);
            
            for (int i = 0; i < 10; i++) {
                float s = 0.175 + 0.005 * float(i);
                
                c.r += inputShader.eval(p + s * m).r;
                c.g += inputShader.eval(p + (s + 0.025) * m).g;
                c.b += inputShader.eval(p + (s + 0.05) * m).b;
            }
            
            return half4(c / 10.0, 1.0);
        }
    """.trimIndent()

    val runtimeShader = remember { RuntimeShader(shaderCode) }

    LaunchedEffect(velocity, pointerPosition) {
        runtimeShader.setFloatUniform(
            "uPointerPosition",
            floatArrayOf(pointerPosition.x, pointerPosition.y)
        )
        runtimeShader.setFloatUniform("uVelocity", floatArrayOf(velocity.x, velocity.y))
        runtimeShader.setFloatUniform("uIntensity", intensity)
        runtimeShader.setFloatUniform("uFalloffRadius", falloffRadius)
    }

    val androidRenderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "inputShader")
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                renderEffect = composeRenderEffect
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        pointerPosition = offset
                        positionBuffer.clear()
                        positionBuffer.add(Pair(offset, System.currentTimeMillis()))
                    },
                    onDragEnd = {
                        isDragging = false
                        if (positionBuffer.size >= 2) {
                            val oldest = positionBuffer.first()
                            val newest = positionBuffer.last()
                            val timeElapsed = (newest.second - oldest.second).coerceAtLeast(1)
                            val distanceX = newest.first.x - oldest.first.x
                            val distanceY = newest.first.y - oldest.first.y

                            velocity = Offset(
                                distanceX / timeElapsed * 300,
                                distanceY / timeElapsed * 300
                            )
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentPos = change.position
                        val currentTime = System.currentTimeMillis()

                        pointerPosition = currentPos

                        positionBuffer.add(Pair(currentPos, currentTime))
                        while (positionBuffer.size > 5) {
                            positionBuffer.removeAt(0)
                        }

                        if (positionBuffer.size >= 2) {
                            val oldest = positionBuffer.first()
                            val newest = positionBuffer.last()
                            val timeElapsed = (newest.second - oldest.second).coerceAtLeast(1)
                            val distanceX = newest.first.x - oldest.first.x
                            val distanceY = newest.first.y - oldest.first.y

                            val newVelocityX = distanceX / timeElapsed * 300
                            val newVelocityY = distanceY / timeElapsed * 300

                            velocity = Offset(
                                newVelocityX * 0.8f + velocity.x * 0.2f,
                                newVelocityY * 0.8f + velocity.y * 0.2f
                            )
                        }
                    }
                )
            }
    ) {
        content()
    }
}