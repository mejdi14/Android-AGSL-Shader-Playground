package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.internal.asContentRenderEffect

/**
 * A twisting spiral distortion that swirls the content toward a center point,
 * strongest at the center and fading out by [radius]. Tap to move the center.
 * Inspired by the "Vortex Spiral" / "Wormhole" effects from SwiftUIShaders.
 *
 * @param strength Maximum twist in radians at the center. Default: 3.5f
 * @param radius Falloff radius in pixels. Default: 700f
 * @param speed How fast the spiral churns over time. Default: 0.5f
 * @param vortexShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The composable content to apply the effect to.
 */
@Composable
fun VortexEffect(
    modifier: Modifier = Modifier,
    strength: Float = 3.5f,
    radius: Float = 700f,
    speed: Float = 0.5f,
    vortexShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var elapsedTime by remember { mutableFloatStateOf(0f) }
    val startTime = remember { System.nanoTime() }
    // Zero == "not set yet"; the shader falls back to screen center in that case.
    var center by remember { mutableStateOf(Offset.Zero) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val defaultShaderCode = """
        uniform shader inputShader;
        uniform float2 uSize;
        uniform float2 uCenter;
        uniform float uTime;
        uniform float uStrength;
        uniform float uRadius;

        half4 main(float2 fragCoord) {
            float2 p = fragCoord - uCenter;
            float r = length(p);
            float falloff = 1.0 - clamp(r / uRadius, 0.0, 1.0);
            float angle = uStrength * falloff * falloff + uTime * uStrength * 0.15 * falloff;
            float s = sin(angle);
            float c = cos(angle);
            float2 rotated = float2(p.x * c - p.y * s, p.x * s + p.y * c);
            float2 sampleCoord = clamp(uCenter + rotated, float2(0.0), uSize);
            return inputShader.eval(sampleCoord);
        }
    """.trimIndent()

    val shaderCode = vortexShaderCode ?: defaultShaderCode
    val vortexShader = remember(shaderCode) { RuntimeShader(shaderCode) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                elapsedTime = (frameTimeNanos - startTime) / 1_000_000_000f
            }
        }
    }

    val activeCenter = if (center == Offset.Zero) {
        Offset(screenWidth / 2f, screenHeight / 2f)
    } else {
        center
    }

    vortexShader.setFloatUniform("uSize", floatArrayOf(screenWidth, screenHeight))
    vortexShader.setFloatUniform("uCenter", floatArrayOf(activeCenter.x, activeCenter.y))
    vortexShader.setFloatUniform("uTime", elapsedTime)
    vortexShader.setFloatUniform("uStrength", strength)
    vortexShader.setFloatUniform("uRadius", radius)

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = vortexShader.asContentRenderEffect() }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset -> center = tapOffset }
            }
    ) {
        content()
    }
}
