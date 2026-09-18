package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.shaderrippleeffect.internal.asContentRenderEffect

private const val NOISE_DISSOLVE_SHADER = """
    uniform shader inputShader;
    uniform float2 uResolution;
    uniform float uProgress; // 0.0 to 1.0

    float hash(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        float a = hash(i);
        float b = hash(i + float2(1.0, 0.0));
        float c = hash(i + float2(0.0, 1.0));
        float d = hash(i + float2(1.0, 1.0));
        return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    }

    float fbm(float2 p) {
        float v = 0.0;
        float a = 0.5;
        for (int i=0; i<5; i++) {
            v += a * noise(p);
            p *= 2.0;
            a *= 0.5;
        }
        return v;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        
        // Fractal noise map
        float n = fbm(uv * 8.0);
        
        // Calculate dissolve threshold
        // Scale progress so the noise range covers it completely
        float threshold = uProgress * 1.2 - 0.1; 
        
        // Edge glow color (burn effect)
        half3 burnColor = half3(1.0, 0.4, 0.05); 
        
        // Is the pixel dissolved?
        float isDissolved = step(n, threshold);
        
        // Is the pixel on the burning edge?
        float isEdge = smoothstep(threshold - 0.15, threshold, n) * (1.0 - isDissolved);
        
        half4 texColor = inputShader.eval(fragCoord);
        
        half4 finalColor = mix(texColor, half4(burnColor, 1.0) * texColor.a, isEdge);
        
        // Multiply alpha by 1.0 - isDissolved to make it transparent
        return finalColor * (1.0 - isDissolved);
    }
"""

@Composable
fun NoiseDissolveEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var isDissolved by remember { mutableStateOf(false) }
    
    val progress by animateFloatAsState(
        targetValue = if (isDissolved) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 1500)
    )

    val runtimeShader = remember { RuntimeShader(NOISE_DISSOLVE_SHADER) }
    
    if (width > 0 && height > 0) {
        runtimeShader.setFloatUniform("uResolution", width, height)
        runtimeShader.setFloatUniform("uProgress", progress)
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
                    onTap = { isDissolved = !isDissolved }
                )
            }
    ) {
        content()
    }
}
