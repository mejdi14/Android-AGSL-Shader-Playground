package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.shaderrippleeffect.internal.asContentRenderEffect

private const val PIXEL_MELT_SHADER = """
    uniform shader inputShader;
    uniform float2 uResolution;
    uniform float uTime;
    uniform float uIntensity;

    float hash(float n) {
        return fract(sin(n) * 43758.5453123);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        
        // Blocky noise for pixel sorting aesthetic
        float blocks = 40.0; // resolution of melt blocks
        float blockX = floor(uv.x * blocks) / blocks;
        
        float noise = hash(blockX * 12.34);
        
        // Melt downward based on noise and intensity
        float melt = noise * uIntensity * 0.8 * (0.5 + 0.5 * sin(uTime * 3.0 + blockX * 20.0));
        
        float2 meltUv = uv;
        // Sample from higher up to pull pixels down
        meltUv.y = meltUv.y - melt; 
        
        // Clamp to avoid sampling out of bounds
        meltUv.y = clamp(meltUv.y, 0.0, 1.0);
        
        // Main color
        half4 color = inputShader.eval(meltUv * uResolution);
        
        // Add some RGB separation at the melt boundaries for a glitchy feel
        float2 rUv = clamp(float2(meltUv.x, meltUv.y - melt * 0.05), float2(0.0), float2(1.0));
        float2 bUv = clamp(float2(meltUv.x, meltUv.y + melt * 0.05), float2(0.0), float2(1.0));
        
        half r = inputShader.eval(rUv * uResolution).r;
        half b = inputShader.eval(bUv * uResolution).b;
        
        // Only apply separation where it's actively melting
        float isMelting = smoothstep(0.01, 0.1, melt);
        color.r = mix(color.r, r, isMelting);
        color.b = mix(color.b, b, isMelting);
        
        return color;
    }
"""

@Composable
fun PixelMeltEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var time by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    val intensity by animateFloatAsState(targetValue = if (isDragging) 1.0f else 0.0f)

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            time = (withFrameNanos { it } - start) / 1_000_000_000f
        }
    }

    val runtimeShader = remember { RuntimeShader(PIXEL_MELT_SHADER) }
    
    if (width > 0 && height > 0) {
        runtimeShader.setFloatUniform("uResolution", width, height)
        runtimeShader.setFloatUniform("uTime", time)
        runtimeShader.setFloatUniform("uIntensity", intensity)
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
            .graphicsLayer {
                if (width > 0 && height > 0) {
                    renderEffect = runtimeShader.asContentRenderEffect()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                            onDrag = {change, dragAmount -> }
                )
            }
    ) {
        content()
    }
}
