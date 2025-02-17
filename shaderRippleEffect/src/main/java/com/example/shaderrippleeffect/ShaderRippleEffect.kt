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
    // State for the ripple's origin and animation.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var isRippling by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableStateOf(0f) }

    // When a tap occurs, we start animating elapsedTime from 0 to ~3 seconds.
    LaunchedEffect(isRippling) {
        if (isRippling) {
            val startTime = withFrameNanos { it }
            do {
                val now = withFrameNanos { it }
                elapsedTime = (now - startTime) / 1_000_000_000f
                // End the ripple after 3 seconds.
                if (elapsedTime >= 3f) {
                    isRippling = false
                    break
                }
                awaitFrame()
            } while (true)
        }
    }

    // Get screen dimensions.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Define our ripple shader.
    // This shader calculates a sine-based ripple offset for each pixel based on the distance from a tap point.
    // It then samples the original content (provided automatically as the “child”) at the offset position.
    val rippleShaderCode = """
    uniform shader inputShader; // Give it a custom name
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
        return inputShader.eval(newPos); // Use the custom name
    }
""".trimIndent()

// And then when creating the effect:

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
// Convert to Compose RenderEffect
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

    Box(
        Modifier
            .fillMaxSize()
            // Apply the RenderEffect as a layer effect.
            .graphicsLayer { renderEffect = composeRenderEffect }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    origin = tapOffset
                    isRippling = true
                }
            }
    ) {
        content()

    }
}