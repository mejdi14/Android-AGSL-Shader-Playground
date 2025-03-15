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
    intensity: Float = 1.5f,
    falloffRadius: Float = 190f,
    content: @Composable () -> Unit
) {
    // État pour suivre la position actuelle du doigt et la vélocité
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }
    var velocity by remember { mutableStateOf(Offset.Zero) }
    // État pour suivre si un glissement est actif
    var isDragging by remember { mutableStateOf(false) }

    // Animation pour réduire progressivement la vélocité lorsque le glissement s'arrête
    LaunchedEffect(isDragging) {
        if (!isDragging && velocity != Offset.Zero) {
            // Réduire progressivement la vélocité
            while (velocity.getDistance() > 0.1f) {
                velocity = velocity * 0.9f
                delay(16) // ~60fps
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
            // Reproduction exacte du code Swift original
            float2 p = fragCoord;
            float2 l = uPointerPosition;
            float2 v = uVelocity;
            
            // Compute the motion vector with a falloff based on distance
            float2 m = -v * pow(clamp(1.0 - length(l - p) / uFalloffRadius, 0.0, 1.0), 2.0) * uIntensity;
            
            half3 c = half3(0.0);
            
            // Loop to sample colors and accumulate
            for (int i = 0; i < 10; i++) {
                float s = 0.175 + 0.005 * float(i);  // Increasing spread factor
                
                // Accumulate sampled colors from texture with RGB channel separation
                c.r += inputShader.eval(p + s * m).r;
                c.g += inputShader.eval(p + (s + 0.025) * m).g;
                c.b += inputShader.eval(p + (s + 0.05) * m).b;
            }
            
            // Return the average of the sampled colors with an alpha of 1
            return half4(c / 10.0, 1.0);
        }
    """.trimIndent()

    val runtimeShader = remember { RuntimeShader(shaderCode) }

    // Mise à jour des uniformes du shader chaque fois que les valeurs changent
    SideEffect {
        runtimeShader.setFloatUniform("uPointerPosition", floatArrayOf(pointerPosition.x, pointerPosition.y))
        runtimeShader.setFloatUniform("uVelocity", floatArrayOf(velocity.x, velocity.y))
        runtimeShader.setFloatUniform("uIntensity", intensity)
        runtimeShader.setFloatUniform("uFalloffRadius", falloffRadius)
    }

    val androidRenderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "inputShader")
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

    // Variables pour suivre le mouvement précédent et calculer la vélocité
    val previousPosition = remember { mutableStateOf(Offset.Zero) }
    val currentTime = remember { mutableStateOf(0L) }
    val previousTime = remember { mutableStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                renderEffect = composeRenderEffect
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        previousPosition.value = offset
                        pointerPosition = offset
                        isDragging = true
                        previousTime.value = System.currentTimeMillis()
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        currentTime.value = System.currentTimeMillis()
                        val currentPos = change.position
                        pointerPosition = currentPos

                        // Calculer la vélocité avec lissage temporel pour éviter les changements brusques
                        val deltaTime = (currentTime.value - previousTime.value).coerceAtLeast(1)
                        val rawVelocityX = (currentPos.x - previousPosition.value.x) / deltaTime * 200 // Amplification pour rendre l'effet visible
                        val rawVelocityY = (currentPos.y - previousPosition.value.y) / deltaTime * 200

                        // Filtrer la vélocité pour éviter les grands sauts
                        velocity = Offset(
                            velocity.x * 0.7f + rawVelocityX * 0.3f,
                            velocity.y * 0.7f + rawVelocityY * 0.3f
                        )

                        previousPosition.value = currentPos
                        previousTime.value = currentTime.value
                    }
                )
            }
    ) {
        content()
    }
}