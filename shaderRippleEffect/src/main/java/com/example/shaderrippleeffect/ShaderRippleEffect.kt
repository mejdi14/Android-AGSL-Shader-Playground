package com.example.shaderrippleeffect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
fun ShaderRippleEffect(content: @Composable () -> Unit) {
    // State for the ripple's origin, a trigger counter, and the elapsed time.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var trigger by remember { mutableStateOf(0) }
    var elapsedTime by remember { mutableStateOf(0f) }

    // Every time the trigger changes, launch a new ripple animation.
    LaunchedEffect(trigger) {
        // Reset the elapsed time.
        elapsedTime = 0f
        val startTime = withFrameNanos { it }
        // Animate for 3 seconds.
        do {
            val now = withFrameNanos { it }
            elapsedTime = (now - startTime) / 1_000_000_000f
            if (elapsedTime >= 3f) break
            awaitFrame()
        } while (true)
    }

    // Get screen dimensions.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Define our ripple shader.
    // This shader calculates a sine-based ripple offset for each pixel based on the distance from the tap point.
    // It then samples the original content (provided automatically as the “child”) at the offset position.
    val rippleShaderCode = """
        uniform shader inputShader; // Custom name for the child shader
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

    // Create the RuntimeShader and update its uniforms.
    val runtimeShader = remember { RuntimeShader(rippleShaderCode) }
    runtimeShader.setFloatUniform("uResolution", floatArrayOf(screenWidth, screenHeight))
    runtimeShader.setFloatUniform("uOrigin", floatArrayOf(origin.x, origin.y))
    runtimeShader.setFloatUniform("uTime", elapsedTime)
    runtimeShader.setFloatUniform("uAmplitude", 12f)
    runtimeShader.setFloatUniform("uFrequency", 15f)
    runtimeShader.setFloatUniform("uDecay", 8f)
    runtimeShader.setFloatUniform("uSpeed", 1800f)

    // Create a RenderEffect from the runtime shader.
    // When used as a layer effect, the view’s content is automatically passed as the shader’s input.
    val androidRenderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "inputShader")
    // Convert to Compose RenderEffect.
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

    Box(
        Modifier
            .fillMaxSize()
            // Apply the RenderEffect as a layer effect.
            .graphicsLayer { renderEffect = composeRenderEffect }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    origin = tapOffset
                    // Increment the trigger so that LaunchedEffect starts a new animation.
                    trigger++
                }
            }
    ) {
        content()
    }
}
