package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlin.math.min

/** Checker pattern used by the foil pass. */
enum class StickerPattern { Diamond, Square }

/**
 * A Pokémon-card style holographic foil sticker, driven by dragging.
 *
 * This is an AGSL port of Benjamin Pisano's SwiftUI
 * [Sticker](https://github.com/bpisano/Sticker) library, which stacks two
 * stitchable Metal shaders. Both passes are folded into one AGSL shader here,
 * applied in the same order:
 *
 *  1. **Reflection** — a soft white glare centred on the touch point.
 *  2. **Foil** — a metallic RGB shift, mixed into the artwork by brightness,
 *     then contrast-boosted twice: once by a diamond/square checker and once by
 *     value noise.
 *
 * The result is masked by the content's alpha, matching the original's
 * `.mask(content)`, so the foil never spills outside a die-cut sticker. Dragging
 * also tilts the sticker in 3D, as the library's `dragGesture` motion effect does.
 *
 * @param scale Foil pattern scale. Default: 3f
 * @param colorIntensity How strongly the foil tints the artwork. Default: 0.8f
 * @param contrast Base level of the metallic RGB shift. Default: 0.9f
 * @param checkerScale Size of the checker cells. Default: 5f
 * @param checkerIntensity Contrast boost from the checker. Default: 1.2f
 * @param noiseScale Size of the value-noise cells. Default: 100f
 * @param noiseIntensity Contrast boost from the noise. Default: 1.2f
 * @param lightIntensity Strength of the glare while dragging. Default: 0.3f
 * @param pattern Checker pattern shape. Default: [StickerPattern.Diamond]
 * @param tiltIntensity How far the sticker tilts as you drag. Default: 1f
 * @param foilShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The sticker — ideally with a transparent surround.
 */
@Composable
fun HolographicStickerEffect(
    modifier: Modifier = Modifier,
    scale: Float = 3f,
    colorIntensity: Float = 0.8f,
    contrast: Float = 0.9f,
    checkerScale: Float = 5f,
    checkerIntensity: Float = 1.2f,
    noiseScale: Float = 100f,
    noiseIntensity: Float = 1.2f,
    lightIntensity: Float = 0.3f,
    pattern: StickerPattern = StickerPattern.Diamond,
    tiltIntensity: Float = 1f,
    foilShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }

    val width = layoutSize.width.toFloat()
    val height = layoutSize.height.toFloat()

    // The library's transform is the touch point relative to the centre, in px,
    // springing back to neutral when the drag ends.
    val targetX = if (active && width > 0f) touch.x - width / 2f else 0f
    val targetY = if (active && height > 0f) touch.y - height / 2f else 0f

    val tx by animateFloatAsState(targetX, spring(), label = "stickerTx")
    val ty by animateFloatAsState(targetY, spring(), label = "stickerTy")
    val glare by animateFloatAsState(
        if (active) lightIntensity else 0f,
        spring(),
        label = "stickerGlare"
    )

    val shaderCode = foilShaderCode ?: HOLOGRAPHIC_STICKER_SHADER
    val shader = remember(shaderCode) { RuntimeShader(shaderCode) }

    if (width > 0f && height > 0f) {
        shader.setFloatUniform("uSize", floatArrayOf(width, height))
        // Foil offset: transform * -150, as in StickerEffect.swift.
        shader.setFloatUniform("uOffset", floatArrayOf(tx * -150f, ty * -150f))
        shader.setFloatUniform(
            "uReflectionPos",
            floatArrayOf((tx + width / 2f) / width, (ty + height / 2f) / height)
        )
        shader.setFloatUniform("uReflectionSize", min(width, height) / 2f)
        shader.setFloatUniform("uReflectionIntensity", glare)
        shader.setFloatUniform("uScale", scale)
        shader.setFloatUniform("uIntensity", colorIntensity)
        shader.setFloatUniform("uContrast", contrast)
        shader.setFloatUniform("uCheckerScale", checkerScale)
        shader.setFloatUniform("uCheckerIntensity", checkerIntensity)
        shader.setFloatUniform("uNoiseScale", noiseScale)
        shader.setFloatUniform("uNoiseIntensity", noiseIntensity)
        shader.setFloatUniform("uPatternType", if (pattern == StickerPattern.Diamond) 0f else 1f)
    }

    // Matches the library's rotation3DEffect: radians on screen, so convert.
    val radToDeg = 57.29578f
    val rotY = if (width > 0f) (tx / width) * tiltIntensity * radToDeg else 0f
    val rotX = if (height > 0f) -(ty / height) * tiltIntensity * radToDeg else 0f

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize = it }
            .graphicsLayer {
                rotationY = rotY
                rotationX = rotX
                if (width > 0f && height > 0f) {
                    renderEffect = shader.asContentRenderEffect()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touch = offset
                        active = true
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        touch = change.position
                    },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false }
                )
            }
    ) {
        content()
    }
}

private val HOLOGRAPHIC_STICKER_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float2 uOffset;
    uniform float2 uReflectionPos;
    uniform float uReflectionSize;
    uniform float uReflectionIntensity;
    uniform float uScale;
    uniform float uIntensity;
    uniform float uContrast;
    uniform float uCheckerScale;
    uniform float uCheckerIntensity;
    uniform float uNoiseScale;
    uniform float uNoiseIntensity;
    uniform float uPatternType;

    float random(float2 uv) {
        return fract(sin(dot(uv, float2(12.9898, 78.233))) * 43758.5453);
    }

    float brightnessOf(half4 color) {
        return float(color.r) * 0.299 + float(color.g) * 0.587 + float(color.b) * 0.114;
    }

    float noisePattern(float2 uv) {
        float2 i = floor(uv);
        float2 f = fract(uv);
        float a = random(i);
        float b = random(i + float2(1.0, 0.0));
        float c = random(i + float2(0.0, 1.0));
        float d = random(i + float2(1.0, 1.0));
        float2 u = smoothstep(0.0, 1.0, f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    // Mix harder on lighter colours, with a baseline so darks still catch light.
    half4 lightnessMix(half4 baseColor, half4 overlayColor, float intensity, float baseline) {
        float b = brightnessOf(baseColor);
        float adjusted = max(smoothstep(0.2, 1.0, b) * intensity, baseline);
        return mix(baseColor, overlayColor, half(adjusted));
    }

    half4 increaseContrast(half4 source, float pattern, float intensity) {
        float b = brightnessOf(source);
        float contrastFactor = mix(1.0, intensity, pattern * b);
        return (source - half4(0.5)) * half(contrastFactor) + half4(0.5);
    }

    float squarePattern(float2 uv, float scale, float degreesAngle) {
        float rad = degreesAngle * 3.14159265 / 180.0;
        float2 scaled = uv * scale;
        float ca = cos(rad);
        float sa = sin(rad);
        float2 r = float2(ca * scaled.x - sa * scaled.y, sa * scaled.x + ca * scaled.y);
        return mod(floor(r.x) + floor(r.y), 2.0) < 0.5 ? 0.0 : 1.0;
    }

    half4 main(float2 fragCoord) {
        half4 src = inputShader.eval(fragCoord);
        float alpha = float(src.a);
        if (alpha <= 0.001) {
            return half4(0.0);
        }

        // SwiftUI's colorEffect hands the shader straight colour, so un-premultiply.
        half4 color = half4(src.rgb / half(alpha), 1.0);

        // --- Pass 1: reflection glare ---
        float dist = distance(fragCoord / uSize, uReflectionPos);
        float blurFactor = smoothstep(uReflectionSize / uSize.x, 0.0, dist);
        half4 reflectionColor = half4(1.0, 1.0, 1.0, half(uReflectionIntensity * blurFactor));
        color = mix(color, reflectionColor, reflectionColor.a);

        // --- Pass 2: foil ---
        float aspectRatio = uSize.x / uSize.y;
        float2 normalizedOffset = (uOffset + uSize * 250.0) / (uSize * uScale) * 0.01;
        float2 normalizedPosition = float2(fragCoord.x * aspectRatio, fragCoord.y);
        float2 uv = (fragCoord / (uSize * uScale)) + normalizedOffset;

        float gradientNoise = random(fragCoord) * 0.1;
        float2 patternUv = normalizedPosition / uSize * uCheckerScale;
        float pattern = uPatternType < 0.5
            ? squarePattern(patternUv, uCheckerScale, 45.0)
            : squarePattern(patternUv, uCheckerScale, 0.0);
        float noise = noisePattern(fragCoord / uSize * uNoiseScale);

        half r = half(uContrast + 0.25 * sin(uv.x * 10.0 + gradientNoise));
        half g = half(uContrast + 0.25 * cos(uv.y * 10.0 + gradientNoise));
        half b = half(uContrast + 0.25 * sin((uv.x + uv.y) * 10.0 - gradientNoise));

        half4 foilColor = half4(r, g, b, 1.0);
        half4 mixedFoil = lightnessMix(color, foilColor, uIntensity, 0.3);
        half4 checkerFoil = increaseContrast(mixedFoil, pattern, uCheckerIntensity);
        half4 noiseCheckerFoil = increaseContrast(checkerFoil, noise, uNoiseIntensity);

        // Mask by the sticker's own alpha, back to premultiplied.
        half3 outRgb = clamp(noiseCheckerFoil.rgb, half3(0.0), half3(1.0)) * half(alpha);
        return half4(outRgb, half(alpha));
    }
""".trimIndent()
