package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlinx.coroutines.launch

private const val DONGXIAO_SHADER = """
    uniform shader inputShader;
    uniform float2 uResolution;
    uniform float2 uPointer;
    uniform float uWaveTime;
    uniform float uHover;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        float2 pointerUV = uPointer / uResolution;
        float2 ratio = float2(uResolution.x / max(uResolution.y, 0.0001), 1.0);
        float2 delta = (uv - pointerUV) * ratio;
        float dist = length(delta);
        float2 dir = delta / max(dist, 0.001) / ratio;
        
        float wave = sin(dist * 23.0 - uWaveTime * 8.0) * exp(-dist * 1.55) * uHover;
        float echo = sin(dist * 36.0 - uWaveTime * 10.0) * exp(-dist * 3.0) * uHover;
        
        float2 warped = clamp(uv + dir * (wave * 0.024 + echo * 0.009), float2(0.002, 0.002), float2(0.998, 0.998));
        
        half4 tex = inputShader.eval(warped * uResolution);
        
        tex.rgb += half3(exp(-dist * 5.5) * 0.16 * uHover);
        return tex;
    }
"""

@Composable
fun DongxiaoWaveEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var origin by remember { mutableStateOf(Offset(0f, 0f)) }
    var waveTime by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    
    val hover = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            waveTime = (withFrameNanos { it } - start) / 1_000_000_000f
        }
    }

    val runtimeShader = remember { RuntimeShader(DONGXIAO_SHADER) }
    
    if (width > 0 && height > 0) {
        runtimeShader.setFloatUniform("uResolution", width, height)
        runtimeShader.setFloatUniform("uPointer", origin.x, origin.y)
        runtimeShader.setFloatUniform("uWaveTime", waveTime)
        runtimeShader.setFloatUniform("uHover", hover.value)
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
                if (origin.x == 0f && origin.y == 0f) {
                    origin = Offset(width / 2, height / 2)
                }
            }
            .graphicsLayer {
                if (width > 0 && height > 0) {
                    renderEffect = runtimeShader.asContentRenderEffect()
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    origin = down.position
                    scope.launch { hover.animateTo(1f, tween(300)) }
                    
                    do {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position
                        if (pos != null) {
                            origin = pos
                        }
                    } while (event.changes.any { it.pressed })
                    
                    scope.launch { hover.animateTo(0f, tween(300)) }
                }
            }
    ) {
        content()
    }
}
