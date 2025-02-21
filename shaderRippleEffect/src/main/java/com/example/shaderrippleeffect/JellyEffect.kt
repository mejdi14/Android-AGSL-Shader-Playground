package com.example.shaderrippleeffect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * A customizable wave effect that can be applied to any composable content.
 *
 * @param speed Controls how fast the waves move. Default: 0.5f
 * @param strength Controls the amplitude of the waves. Default: 18f
 * @param frequency Controls how many waves appear. Default: 10f
 * @param timeMultiplier Controls how time affects the animation. Default: 1f
 * @param waveShaderCode Custom shader code if you want to override the default. Default: null
 * @param modifier Additional modifiers to apply
 * @param content The composable content to apply the effect to
 */
@Composable
fun ComplexWaveEffect(
    modifier: Modifier = Modifier,
    speed: Float = 0.5f,
    strength: Float = 18f,
    frequency: Float = 10f,
    timeMultiplier: Float = 1f,
    waveShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var elapsedTime by remember { mutableFloatStateOf(0f) }
    val startTime = remember { System.nanoTime() }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val defaultShaderCode = """
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

    val shaderCode = waveShaderCode ?: defaultShaderCode
    val waveShader = remember { RuntimeShader(shaderCode) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                elapsedTime = (frameTimeNanos - startTime) / 1_000_000_000f * timeMultiplier
            }
        }
    }

    waveShader.setFloatUniform("uTime", elapsedTime)
    waveShader.setFloatUniform("uSize", floatArrayOf(screenWidth, screenHeight))
    waveShader.setFloatUniform("uSpeed", speed)
    waveShader.setFloatUniform("uStrength", strength)
    waveShader.setFloatUniform("uFrequency", frequency)

    val renderEffect = RenderEffect
        .createRuntimeShaderEffect(waveShader, "inputShader")
        .asComposeRenderEffect()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.renderEffect = renderEffect }
    ) {
        content()
    }
}