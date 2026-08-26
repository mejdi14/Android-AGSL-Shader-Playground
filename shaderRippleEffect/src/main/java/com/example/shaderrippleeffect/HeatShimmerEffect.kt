package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.internal.asContentRenderEffect

/**
 * Wavy thermal turbulence, like heat rising off hot asphalt: a layered
 * horizontal ripple that drifts upward over time. Inspired by the "Heat
 * Shimmer" effect from SwiftUIShaders.
 *
 * @param strength Horizontal displacement in pixels. Default: 8f
 * @param frequency How tight the heat waves are. Default: 14f
 * @param speed How fast the shimmer rises. Default: 1.2f
 * @param shimmerShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The composable content to apply the effect to.
 */
@Composable
fun HeatShimmerEffect(
    modifier: Modifier = Modifier,
    strength: Float = 8f,
    frequency: Float = 14f,
    speed: Float = 1.2f,
    shimmerShaderCode: String? = null,
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
        uniform float2 uSize;
        uniform float uTime;
        uniform float uStrength;
        uniform float uFrequency;
        uniform float uSpeed;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uSize;
            float t = uTime * uSpeed;

            float w = sin(uv.y * uFrequency + t)
                    + 0.5 * sin(uv.y * uFrequency * 2.7 + t * 1.5)
                    + 0.25 * sin(uv.x * uFrequency * 1.3 - t);

            float2 offset = float2(w * uStrength, 0.0);
            return inputShader.eval(fragCoord + offset);
        }
    """.trimIndent()

    val shaderCode = shimmerShaderCode ?: defaultShaderCode
    val heatShader = remember(shaderCode) { RuntimeShader(shaderCode) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                elapsedTime = (frameTimeNanos - startTime) / 1_000_000_000f
            }
        }
    }

    heatShader.setFloatUniform("uSize", floatArrayOf(screenWidth, screenHeight))
    heatShader.setFloatUniform("uTime", elapsedTime)
    heatShader.setFloatUniform("uStrength", strength)
    heatShader.setFloatUniform("uFrequency", frequency)
    heatShader.setFloatUniform("uSpeed", speed)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = heatShader.asContentRenderEffect() }
    ) {
        content()
    }
}
