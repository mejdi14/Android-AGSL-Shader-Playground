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
 * Flowing molten-metal distortion: a layered domain warp pushes the content
 * around while a moving sheen sweeps across it. Inspired by the "Liquid Chrome"
 * effect from SwiftUIShaders.
 *
 * @param strength Displacement amount in pixels. Default: 14f
 * @param scale Spatial frequency of the flow. Default: 8f
 * @param speed How fast the metal flows. Default: 0.6f
 * @param liquidShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The composable content to apply the effect to.
 */
@Composable
fun LiquidChromeEffect(
    modifier: Modifier = Modifier,
    strength: Float = 14f,
    scale: Float = 8f,
    speed: Float = 0.6f,
    liquidShaderCode: String? = null,
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
        uniform float uScale;
        uniform float uSpeed;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uSize;
            float t = uTime * uSpeed;

            float wx = sin(uv.y * uScale + t) + 0.5 * sin(uv.y * uScale * 2.3 - t * 1.7);
            float wy = cos(uv.x * uScale - t) + 0.5 * cos(uv.x * uScale * 1.9 + t * 1.3);
            float2 offset = float2(wx, wy) * uStrength;

            half4 color = inputShader.eval(fragCoord + offset);

            float sheen = 0.5 + 0.5 * sin((uv.x + uv.y) * uScale + t * 2.0);
            color.rgb += half3(0.18 * sheen) * color.a;
            return color;
        }
    """.trimIndent()

    val shaderCode = liquidShaderCode ?: defaultShaderCode
    val liquidShader = remember(shaderCode) { RuntimeShader(shaderCode) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                elapsedTime = (frameTimeNanos - startTime) / 1_000_000_000f
            }
        }
    }

    liquidShader.setFloatUniform("uSize", floatArrayOf(screenWidth, screenHeight))
    liquidShader.setFloatUniform("uTime", elapsedTime)
    liquidShader.setFloatUniform("uStrength", strength)
    liquidShader.setFloatUniform("uScale", scale)
    liquidShader.setFloatUniform("uSpeed", speed)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = liquidShader.asContentRenderEffect() }
    ) {
        content()
    }
}
