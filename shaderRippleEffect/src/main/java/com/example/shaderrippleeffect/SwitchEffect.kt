package com.example.shaderrippleeffect

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.annotation.Size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun RippleContentTransition(
    firstContent: @Composable () -> Unit,
    secondContent: @Composable () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var trigger by remember { mutableStateOf(0) }
    var elapsedTime by remember { mutableStateOf(0f) }

    LaunchedEffect(trigger) {
        elapsedTime = 0f
        val startTime = withFrameNanos { it }
        do {
            val now = withFrameNanos { it }
            elapsedTime = (now - startTime) / 1_000_000_000f
            if (elapsedTime >= 3f) break
            awaitFrame()
        } while (true)
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
        runtimeShader.setFloatUniform("uTime", floatArrayOf(elapsedTime))
    }

    // Constant parameters
    runtimeShader.setFloatUniform("uSpeed", floatArrayOf(800f))
    runtimeShader.setFloatUniform("uFrequency", floatArrayOf(15f))  // Frequency of the wiggle waves
    runtimeShader.setFloatUniform("uAmplitude", floatArrayOf(0.5f)) // Subtle amplitude for smoother effect
    runtimeShader.setFloatUniform("uEdgeWidth", floatArrayOf(10f))  // Wider edge for smoother transition
    runtimeShader.setFloatUniform("uWiggleStrength", floatArrayOf(2.0f)) // Controls how much the edge wiggles

    val androidRenderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "inputShader")
    val composeRenderEffect = androidRenderEffect.asComposeRenderEffect()

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
