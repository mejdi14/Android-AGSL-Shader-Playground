package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.shaderrippleeffect.internal.asContentRenderEffect

private const val CRT_GLITCH_SHADER = """
    uniform shader inputShader;
    uniform float2 uResolution;
    uniform float uTime;
    uniform float uIntensity;

    float hash12(float2 p) {
        float3 p3  = fract(float3(p.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        
        // Curvature (Barrel distortion)
        float2 crtUV = uv * 2.0 - 1.0;
        float offset = crtUV.y * crtUV.y * 0.03 + crtUV.x * crtUV.x * 0.03;
        crtUV += crtUV * offset;
        crtUV = crtUV * 0.5 + 0.5;
        
        // Bounds check
        if (crtUV.x < 0.0 || crtUV.x > 1.0 || crtUV.y < 0.0 || crtUV.y > 1.0) {
            return half4(0.0);
        }
        
        // Glitch
        float glitchTime = floor(uTime * 15.0);
        float glitchLine = step(0.95, sin(crtUV.y * 10.0 + uTime * 5.0));
        float glitchOffset = (hash12(float2(glitchTime, floor(crtUV.y * 20.0))) - 0.5) * 0.15 * uIntensity * glitchLine;
        
        float2 rUv = clamp(crtUV + float2(glitchOffset + 0.005 * uIntensity, 0.0), float2(0.0), float2(1.0));
        float2 gUv = clamp(crtUV + float2(glitchOffset, 0.0), float2(0.0), float2(1.0));
        float2 bUv = clamp(crtUV + float2(glitchOffset - 0.005 * uIntensity, 0.0), float2(0.0), float2(1.0));
        
        half r = inputShader.eval(rUv * uResolution).r;
        half g = inputShader.eval(gUv * uResolution).g;
        half b = inputShader.eval(bUv * uResolution).b;
        
        // Scanlines
        float scanline = sin(crtUV.y * uResolution.y * 0.5) * 0.04;
        
        // Noise
        float noise = (hash12(crtUV + uTime) - 0.5) * 0.1 * uIntensity;
        
        half3 color = half3(r, g, b);
        color -= scanline;
        color += noise;
        
        // Vignette
        float vig = length(uv - 0.5);
        color *= smoothstep(0.75, 0.35, vig);

        return half4(color, 1.0);
    }
"""

@Composable
fun CrtGlitchEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var time by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var isInteracting by remember { mutableStateOf(false) }
    
    val intensity by animateFloatAsState(targetValue = if (isInteracting) 1.5f else 0.2f)

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            time = (withFrameNanos { it } - start) / 1_000_000_000f
        }
    }

    val runtimeShader = remember { RuntimeShader(CRT_GLITCH_SHADER) }
    
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
                detectTapGestures(
                    onPress = {
                        isInteracting = true
                        tryAwaitRelease()
                        isInteracting = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isInteracting = true },
                    onDragEnd = { isInteracting = false },
                    onDragCancel = { isInteracting = false },
                    onDrag = {change, dragAmount -> }
                )
            }
    ) {
        content()
    }
}
