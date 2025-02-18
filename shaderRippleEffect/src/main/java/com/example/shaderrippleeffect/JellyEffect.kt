package com.example.shaderrippleeffect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.android.awaitFrame

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ComplexWaveEffect(content: @Composable () -> Unit) {
    // Track elapsed time for animation
    var elapsedTime by remember { mutableStateOf(0f) }
    val startTime = remember { System.nanoTime() }

    // Get screen dimensions
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Animation parameters - similar to the SwiftUI example
    val speed = 0.5f
    val strength = 18f
    val frequency = 10f

    // Define the complex wave shader
    val waveShaderCode = """
        uniform shader inputShader;
        uniform float uTime;
        uniform float2 uSize;
        uniform float uSpeed;
        uniform float uStrength;
        uniform float uFrequency;
        
        half4 main(float2 fragCoord) {
            float2 normalizedPosition = fragCoord / uSize;
            float moveAmount = uTime * uSpeed;
            
            float2 newPosition = fragCoord;
            newPosition.x += sin((normalizedPosition.x + moveAmount) * uFrequency) * uStrength;
            newPosition.y += cos((normalizedPosition.y + moveAmount) * uFrequency) * uStrength;
            
            return inputShader.eval(newPosition);
        }
    """.trimIndent()

    // Create and configure the RuntimeShader
    val waveShader = remember { RuntimeShader(waveShaderCode) }

    // Update animation time in a continuous way
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                elapsedTime = (frameTimeNanos - startTime) / 1_000_000_000f
            }
        }
    }

    // Update shader uniforms
    waveShader.setFloatUniform("uTime", elapsedTime)
    waveShader.setFloatUniform("uSize", floatArrayOf(screenWidth, screenHeight))
    waveShader.setFloatUniform("uSpeed", speed)
    waveShader.setFloatUniform("uStrength", strength)
    waveShader.setFloatUniform("uFrequency", frequency)

    // Create render effect from the shader
    val renderEffect = RenderEffect
        .createRuntimeShaderEffect(waveShader, "inputShader")
        .asComposeRenderEffect()

    // Apply effect to content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.renderEffect = renderEffect }
    ) {
        content()
    }
}

