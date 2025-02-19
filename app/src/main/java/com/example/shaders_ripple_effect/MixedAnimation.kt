package com.example.shaders_ripple_effect

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.android.awaitFrame

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity


@Composable
fun MixedAnimation() {
    Box(Modifier.fillMaxSize()) {
        var origin by remember { mutableStateOf(Offset.Zero) }
        var trigger by remember { mutableStateOf(0) }

        // Common tap detection at the top level
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        origin = tapOffset
                        trigger++
                    }
                }
        ) {
            ShaderRippleEffect2(origin = origin,
                trigger = trigger) {
                RippleContentTransition2 (
                    origin = origin,
                    trigger = trigger,
                    firstContent = {
                        Image(
                            painter = painterResource(id = R.drawable.violet),
                            modifier = Modifier
                                .fillMaxSize(),
                            contentDescription = "Ripple Effect",
                            contentScale = ContentScale.Crop
                        )
                    },
                    secondContent = {
                        Image(
                            painter = painterResource(id = R.drawable.palace),
                            modifier = Modifier
                                .fillMaxSize(),
                            contentDescription = "Ripple Effect",
                            contentScale = ContentScale.Crop
                        )
                    }
                )

            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ShaderRippleEffect2(
    origin: Offset,
    trigger: Int,
    content: @Composable () -> Unit
) {
    var elapsedTime by remember { mutableStateOf(0f) }

    // Respond to external trigger
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            elapsedTime = 0f
            val startTime = withFrameNanos { it }
            do {
                val now = withFrameNanos { it }
                elapsedTime = (now - startTime) / 1_000_000_000f
                if (elapsedTime >= 3f) break
                awaitFrame()
            } while (true)
        }
    }

    // Get screen dimensions
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Define ripple shader code
    val rippleShaderCode = """
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

    // Create and configure shader
    val runtimeShader = remember { RuntimeShader(rippleShaderCode) }

    // Safely set uniforms with null checks
    if (origin.x.isFinite() && origin.y.isFinite()) {
        runtimeShader.setFloatUniform("uOrigin", floatArrayOf(origin.x, origin.y))
    }
    if (screenWidth > 0 && screenHeight > 0) {
        runtimeShader.setFloatUniform("uResolution", floatArrayOf(screenWidth, screenHeight))
    }
    if (elapsedTime.isFinite()) {
        runtimeShader.setFloatUniform("uTime", elapsedTime)
    }

    runtimeShader.setFloatUniform("uAmplitude", 32f)
    runtimeShader.setFloatUniform("uFrequency", 15f)
    runtimeShader.setFloatUniform("uDecay", 5f)
    runtimeShader.setFloatUniform("uSpeed", 1800f)

    // Create render effect
    val androidRenderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "inputShader")
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = composeRenderEffect }
    ) {
        content()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun RippleContentTransition2(
    origin: Offset,
    trigger: Int,
    firstContent: @Composable () -> Unit,
    secondContent: @Composable () -> Unit
) {
    var elapsedTime by remember { mutableStateOf(0f) }

    // Respond to external trigger
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            elapsedTime = 0f
            val startTime = withFrameNanos { it }
            do {
                val now = withFrameNanos { it }
                elapsedTime = (now - startTime) / 1_000_000_000f
                if (elapsedTime >= 3f) break
                awaitFrame()
            } while (true)
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Updated shader with a subtle time-based wiggle effect
    val rippleMaskShaderCode = """
    uniform shader inputShader;
    uniform float2 uResolution;
    uniform float2 uOrigin;
    uniform float uTime;
    uniform float uSpeed;
    uniform float uFrequency;
    uniform float uAmplitude;
    uniform float uEdgeWidth;
    uniform float uWiggleStrength;
    
    half4 main(float2 fragCoord) {
        // Compute distance from the tap point
        float distance = length(fragCoord - uOrigin);
        
        // Compute the current ripple radius
        float radius = uTime * uSpeed;
        
        // Hard cut-off for complete transparency inside the ripple
        if (distance < radius - uEdgeWidth) {
            return half4(0.0, 0.0, 0.0, 0.0); // Completely transparent inside
        }
        
        // Full opacity outside the ripple's outer edge
        if (distance > radius + uEdgeWidth) {
            return inputShader.eval(fragCoord);
        }
        
        // Add a subtle wiggle effect at the edges based on time and distance
        float wiggle = sin(distance * uFrequency + uTime * 6.283) * uWiggleStrength;
        
        // Create a smooth transition at the edge with the wiggle applied
        float normDistance = (distance - (radius - uEdgeWidth)) / (2.0 * uEdgeWidth);
        float baseMask = smoothstep(0.0, 1.0, normDistance + wiggle);
        
        // Apply the mask to the input shader
        half4 color = inputShader.eval(fragCoord);
        color.a *= baseMask;
        
        return color;
    }
    """.trimIndent()

    val runtimeShader = remember { RuntimeShader(rippleMaskShaderCode) }

    // Safely set uniforms with null checks
    if (origin.x.isFinite() && origin.y.isFinite()) {
        runtimeShader.setFloatUniform("uOrigin", floatArrayOf(origin.x, origin.y))
    }
    if (screenWidth > 0 && screenHeight > 0) {
        runtimeShader.setFloatUniform("uResolution", floatArrayOf(screenWidth, screenHeight))
    }
    if (elapsedTime.isFinite()) {
        runtimeShader.setFloatUniform("uTime", elapsedTime)
    }

    // Constant parameters
    runtimeShader.setFloatUniform("uSpeed", 1800f)
    runtimeShader.setFloatUniform("uFrequency", 15f)
    runtimeShader.setFloatUniform("uAmplitude", 0.5f)
    runtimeShader.setFloatUniform("uEdgeWidth", 20f)
    runtimeShader.setFloatUniform("uWiggleStrength", 8.0f)

    val androidRenderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "inputShader")
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom layer: new content (secondContent)
        Box(modifier = Modifier.fillMaxSize()) {
            secondContent()
        }

        // Top layer: old content (firstContent) with shader applied
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = composeRenderEffect
                    // Set alpha to 0 once the ripple completes for full transparency
                    alpha = if (elapsedTime >= 3f) 0f else 1f
                }
        ) {
            firstContent()
        }
    }
}