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
 * Edge-detection glow: darkens the content and traces its outlines with a
 * glowing, hue-cycling neon line. Inspired by the "Neon Edge" effect from
 * SwiftUIShaders.
 *
 * @param thickness Sampling offset (in px) used to detect edges. Default: 1.5f
 * @param intensity How bright/aggressive the edges glow. Default: 3f
 * @param cycleSpeed How fast the neon color cycles through the hue wheel. Default: 0.1f
 * @param neonShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The composable content to apply the effect to.
 */
@Composable
fun NeonEdgeEffect(
    modifier: Modifier = Modifier,
    thickness: Float = 1.5f,
    intensity: Float = 3f,
    cycleSpeed: Float = 0.1f,
    neonShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var elapsedTime by remember { mutableFloatStateOf(0f) }
    val startTime = remember { System.nanoTime() }

    val defaultShaderCode = """
        uniform shader inputShader;
        uniform float uTime;
        uniform float uThickness;
        uniform float uIntensity;
        uniform float uCycleSpeed;

        half3 hue(float h) {
            float3 k = float3(1.0, 0.6666667, 0.3333333);
            float3 p = abs(fract(float3(h, h, h) + k) * 6.0 - 3.0);
            return half3(clamp(p - 1.0, 0.0, 1.0));
        }

        float luma(float2 coord) {
            half4 c = inputShader.eval(coord);
            return dot(c.rgb, half3(0.299, 0.587, 0.114));
        }

        half4 main(float2 fragCoord) {
            float o = uThickness;
            float lx = luma(fragCoord + float2(o, 0.0)) - luma(fragCoord - float2(o, 0.0));
            float ly = luma(fragCoord + float2(0.0, o)) - luma(fragCoord - float2(0.0, o));
            float edge = clamp(sqrt(lx * lx + ly * ly) * uIntensity, 0.0, 1.0);

            half4 base = inputShader.eval(fragCoord);
            half3 glow = hue(fract(uTime * uCycleSpeed)) * edge;
            half3 outColor = base.rgb * 0.15 + glow;
            return half4(outColor * base.a, base.a);
        }
    """.trimIndent()

    val shaderCode = neonShaderCode ?: defaultShaderCode
    val neonShader = remember(shaderCode) { RuntimeShader(shaderCode) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                elapsedTime = (frameTimeNanos - startTime) / 1_000_000_000f
            }
        }
    }

    neonShader.setFloatUniform("uTime", elapsedTime)
    neonShader.setFloatUniform("uThickness", thickness)
    neonShader.setFloatUniform("uIntensity", intensity)
    neonShader.setFloatUniform("uCycleSpeed", cycleSpeed)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = neonShader.asContentRenderEffect() }
    ) {
        content()
    }
}
