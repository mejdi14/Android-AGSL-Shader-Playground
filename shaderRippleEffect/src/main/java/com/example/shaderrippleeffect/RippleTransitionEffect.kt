/*
 * AGSL port of the "Ripple Transition" component by Mick Cesanek
 * (https://github.com/m1ckc3s/ripple), itself recreated from Minsang's
 * (@radiofun8) "Ripple with Noise" Metal shader. Used under the MIT licence:
 *
 * MIT License. Copyright (c) 2026 Mick Cesanek.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 */
package com.example.shaderrippleeffect

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Tap to cross-fade between images behind an expanding, noisy ripple wavefront.
 *
 * An AGSL port of Mick Cesanek's WebGL "Ripple Transition". A ring of distortion
 * races out from the tap point: it pushes the image radially, splits its colour
 * channels at the crest, brightens with a glow, and reveals the next image in
 * its wake. The wavefront's distance is warped by two layers of fractal noise,
 * so the ring is organic rather than a clean circle.
 *
 * The effect needs two textures at once, so the images are bound as two child
 * shaders on the [RuntimeShader] and drawn through a `ShaderBrush` — AGSL's way
 * of doing a multi-texture pass. With more than two images it cycles through them
 * on each tap.
 *
 * @param images Two or more images to cycle through. The first is shown at rest.
 * @param waveSpeed How fast the wavefront travels. Default: 1.6f
 * @param sigma Thickness of the ripple band. Default: 0.15f
 * @param waveFreq How many ripples ride the band. Default: 5f
 * @param pushAmt How far the ripple shoves the image. Default: 0.145f
 * @param caStrength Colour-channel split at the crest. Default: 0.02f
 * @param glow How much the crest brightens. Default: 0.73f
 * @param noiseWarp How much noise distorts the wavefront. Default: 1f
 * @param durationMillis Length of one transition. Default: 1400
 * @param easing Easing of the wavefront. Default: [FastOutSlowInEasing]
 * @param rippleShaderCode Custom shader code to override the default.
 * @param modifier Additional modifiers to apply.
 */
@Composable
fun RippleTransitionEffect(
    images: List<ImageBitmap>,
    modifier: Modifier = Modifier,
    waveSpeed: Float = 1.6f,
    sigma: Float = 0.15f,
    waveFreq: Float = 5f,
    pushAmt: Float = 0.145f,
    caStrength: Float = 0.02f,
    glow: Float = 0.73f,
    noiseWarp: Float = 1f,
    durationMillis: Int = 1400,
    easing: Easing = FastOutSlowInEasing,
    rippleShaderCode: String? = null
) {
    if (images.isEmpty()) return

    val code = rippleShaderCode ?: RIPPLE_TRANSITION_SHADER
    val shader = remember(code) { RuntimeShader(code) }
    val scope = rememberCoroutineScope()

    var index by remember(images) { mutableIntStateOf(0) }
    var center by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var animating by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    // Current image at rest; the next is revealed by the wavefront.
    val bitmapA = remember(images, index) { images[index].asAndroidBitmap() }
    val bitmapB = remember(images, index) { images[(index + 1) % images.size].asAndroidBitmap() }
    val texA = remember(bitmapA) { BitmapShader(bitmapA, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    val texB = remember(bitmapB) { BitmapShader(bitmapB, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }

    Canvas(
        modifier.pointerInput(images, durationMillis, easing) {
            detectTapGestures { tap ->
                if (animating || images.size < 2) return@detectTapGestures
                center = Offset(tap.x / size.width, tap.y / size.height)
                scope.launch {
                    animating = true
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(durationMillis, easing = easing))
                    index = (index + 1) % images.size
                    progress.snapTo(0f)
                    animating = false
                }
            }
        }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Scale each image to cover the canvas (centre-crop), like ContentScale.Crop.
        texA.setLocalMatrix(coverMatrix(bitmapA.width, bitmapA.height, w, h))
        texB.setLocalMatrix(coverMatrix(bitmapB.width, bitmapB.height, w, h))

        shader.setInputShader("uTexA", texA)
        shader.setInputShader("uTexB", texB)
        shader.setFloatUniform("uResolution", w, h)
        shader.setFloatUniform("uCenter", center.x, center.y)
        shader.setFloatUniform("uProgress", progress.value)
        shader.setFloatUniform("uWaveSpeed", waveSpeed)
        shader.setFloatUniform("uSigma", sigma)
        shader.setFloatUniform("uWaveFreq", waveFreq)
        shader.setFloatUniform("uPushAmt", pushAmt)
        shader.setFloatUniform("uCaStrength", caStrength)
        shader.setFloatUniform("uGlow", glow)
        shader.setFloatUniform("uNoiseWarp", noiseWarp)

        drawRect(brush = ShaderBrush(shader))
    }
}

private fun coverMatrix(bw: Int, bh: Int, w: Float, h: Float): Matrix {
    val scale = max(w / bw, h / bh)
    return Matrix().apply {
        setScale(scale, scale)
        postTranslate((w - bw * scale) / 2f, (h - bh * scale) / 2f)
    }
}

private val RIPPLE_TRANSITION_SHADER = """
    uniform shader uTexA;
    uniform shader uTexB;
    uniform float2 uResolution;
    uniform float2 uCenter;
    uniform float uProgress;
    uniform float uWaveSpeed;
    uniform float uSigma;
    uniform float uWaveFreq;
    uniform float uPushAmt;
    uniform float uCaStrength;
    uniform float uGlow;
    uniform float uNoiseWarp;

    float hash21(float2 p) {
        float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float vnoise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        float a = hash21(i);
        float b = hash21(i + float2(1.0, 0.0));
        float c = hash21(i + float2(0.0, 1.0));
        float d = hash21(i + float2(1.0, 1.0));
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    float fbm(float2 p, int octaves) {
        float val = 0.0;
        float amp = 0.5;
        float freq = 1.0;
        for (int i = 0; i < 8; i++) {
            if (i >= octaves) break;
            val += amp * vnoise(p * freq);
            freq *= 2.0;
            amp *= 0.5;
        }
        return val;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        float2 center = uCenter;

        float2 p = uv - center;
        float aspect = uResolution.x / uResolution.y;
        p.x *= aspect;

        float dist = length(p);
        float maxDist = length(float2(0.5 * aspect, 0.5));
        float normDist = clamp(dist / maxDist, 0.0, 1.0);

        float noiseLarge = fbm(p * 4.0 + float2(uProgress * 1.0, uProgress * 0.5), 4);
        float noiseSmall = fbm(p * 12.0 + float2(uProgress * 2.0, -uProgress * 1.5), 3);

        float waveFront = uProgress * uWaveSpeed;

        float warpScale = smoothstep(0.0, 0.05, uProgress);
        float warpedDist = normDist
            + (noiseLarge - 0.5) * uNoiseWarp * warpScale
            + (noiseSmall - 0.5) * (uNoiseWarp * 0.9) * warpScale;

        float delta = warpedDist - waveFront;
        float baseEnvelope = exp(-delta * delta / (2.0 * uSigma * uSigma));
        float ripples = max(0.0, cos(delta * uWaveFreq));
        float envelope = baseEnvelope * ripples;

        float gate = smoothstep(0.0, 0.05, uProgress)
                   * (1.0 - smoothstep(0.85, 1.0, uProgress));
        envelope *= gate;

        float2 dir = (dist > 0.001) ? normalize(p) : float2(0.0);
        float push = envelope * uPushAmt;

        float2 uvOffset = dir * push;
        uvOffset.x /= aspect;

        float ca = envelope * uCaStrength;
        float2 caOffset = dir * ca;
        caOffset.x /= aspect;

        float2 uvR = uv - uvOffset - caOffset;
        float2 uvG = uv - uvOffset;
        float2 uvB = uv - uvOffset + caOffset;

        float3 colorA = float3(
            uTexA.eval(uvR * uResolution).r,
            uTexA.eval(uvG * uResolution).g,
            uTexA.eval(uvB * uResolution).b
        );
        float3 colorB = float3(
            uTexB.eval(uvR * uResolution).r,
            uTexB.eval(uvG * uResolution).g,
            uTexB.eval(uvB * uResolution).b
        );

        float feather = 0.04 + 0.05 * noiseLarge;
        float reveal = smoothstep(waveFront + feather, waveFront - feather, warpedDist);
        reveal *= smoothstep(0.0, 0.05, uProgress);

        float3 color = mix(colorA, colorB, reveal);

        float g = envelope * uGlow;
        color = clamp(color / max(1.0 - g, 0.01), 0.0, 1.0);

        return half4(half3(color), 1.0);
    }
""".trimIndent()
