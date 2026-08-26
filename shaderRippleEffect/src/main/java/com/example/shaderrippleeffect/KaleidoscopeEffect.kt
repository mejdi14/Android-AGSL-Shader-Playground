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
 * Mirrors the content into radial wedges around the center, forming a slowly
 * rotating mandala. Inspired by the "Kaleidoscope" effect from SwiftUIShaders.
 *
 * @param segments Number of mirrored wedges. Default: 6f
 * @param rotationSpeed How fast the mandala rotates (radians/sec). Default: 0.3f
 * @param kaleidoscopeShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The composable content to apply the effect to.
 */
@Composable
fun KaleidoscopeEffect(
    modifier: Modifier = Modifier,
    segments: Float = 6f,
    rotationSpeed: Float = 0.3f,
    kaleidoscopeShaderCode: String? = null,
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
        uniform float uSegments;
        uniform float uRotationSpeed;

        half4 main(float2 fragCoord) {
            float2 center = uSize * 0.5;
            float2 p = fragCoord - center;
            float r = length(p);
            float a = atan(p.y, p.x) + uTime * uRotationSpeed;
            float seg = 6.28318530718 / uSegments;
            a = mod(a, seg);
            a = abs(a - seg * 0.5);
            float2 sampleCoord = center + float2(cos(a), sin(a)) * r;
            sampleCoord = clamp(sampleCoord, float2(0.0), uSize);
            return inputShader.eval(sampleCoord);
        }
    """.trimIndent()

    val shaderCode = kaleidoscopeShaderCode ?: defaultShaderCode
    val kaleidoscopeShader = remember(shaderCode) { RuntimeShader(shaderCode) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                elapsedTime = (frameTimeNanos - startTime) / 1_000_000_000f
            }
        }
    }

    kaleidoscopeShader.setFloatUniform("uSize", floatArrayOf(screenWidth, screenHeight))
    kaleidoscopeShader.setFloatUniform("uTime", elapsedTime)
    kaleidoscopeShader.setFloatUniform("uSegments", segments)
    kaleidoscopeShader.setFloatUniform("uRotationSpeed", rotationSpeed)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = kaleidoscopeShader.asContentRenderEffect() }
    ) {
        content()
    }
}
