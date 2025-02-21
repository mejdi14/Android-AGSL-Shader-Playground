package com.example.shaderrippleeffect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame

/**
 * A tap-reactive ripple effect that can be applied to any composable content.
 *
 * @param amplitude Controls the height of the ripple waves. Default: 12f
 * @param frequency Controls how many ripple waves appear. Default: 15f
 * @param decay Controls how quickly the ripple fades out. Default: 8f
 * @param speed Controls how fast the ripple propagates. Default: 1800f
 * @param animationDuration Duration of the ripple animation in seconds. Default: 3f
 * @param rippleShaderCode Custom shader code if you want to override the default. Default: null
 * @param modifier Additional modifiers to apply
 * @param content The composable content to apply the effect to
 */
@Composable
fun ShaderRippleEffect(
    modifier: Modifier = Modifier,
    amplitude: Float = 12f,
    frequency: Float = 15f,
    decay: Float = 8f,
    speed: Float = 1800f,
    animationDuration: Float = 3f,
    rippleShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var trigger by remember { mutableIntStateOf(0) }
    var elapsedTime by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(trigger) {
        elapsedTime = 0f
        val startTime = withFrameNanos { it }
        do {
            val now = withFrameNanos { it }
            elapsedTime = (now - startTime) / 1_000_000_000f
            if (elapsedTime >= animationDuration) break
            awaitFrame()
        } while (true)
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val defaultShaderCode = """
        uniform shader inputShader;
        uniform float2 uResolution;
        uniform float2 uOrigin;
        uniform float uTime;
        uniform float uAmplitude;
        uniform float uFrequency;
        uniform float uDecay;
        uniform float uSpeed;
        
        half4 main(float2 fragCoord) {
            float2 pos = fragCoord;
            float distance = length(pos - uOrigin);
            float delay = distance / uSpeed;
            float time = max(0.0, uTime - delay);
            float rippleAmount = uAmplitude * sin(uFrequency * time) * exp(-uDecay * time);
            float2 n = normalize(pos - uOrigin);
            float2 newPos = pos + rippleAmount * n;
            return inputShader.eval(newPos);
        }
    """.trimIndent()

    val shaderCode = rippleShaderCode ?: defaultShaderCode
    val runtimeShader = remember { RuntimeShader(shaderCode) }
    runtimeShader.setFloatUniform("uResolution", floatArrayOf(screenWidth, screenHeight))
    runtimeShader.setFloatUniform("uOrigin", floatArrayOf(origin.x, origin.y))
    runtimeShader.setFloatUniform("uTime", elapsedTime)
    runtimeShader.setFloatUniform("uAmplitude", amplitude)
    runtimeShader.setFloatUniform("uFrequency", frequency)
    runtimeShader.setFloatUniform("uDecay", decay)
    runtimeShader.setFloatUniform("uSpeed", speed)

    val androidRenderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "inputShader")
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = composeRenderEffect }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    origin = tapOffset
                    trigger++
                }
            }
    ) {
        content()
    }
}