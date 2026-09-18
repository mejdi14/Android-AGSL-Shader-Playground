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
import com.example.shaderrippleeffect.internal.rememberDeviceTilt
import kotlin.math.min

/** Checker pattern used by the foil pass. */
enum class StickerPattern { Diamond, Square }

/** What drives the foil: tilting the device, or dragging across it. */
enum class StickerMotion { Tilt, Drag }

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
 * @param foilBaseline Minimum foil mix, even on dark pixels. Lower this for
 *   detailed artwork you don't want washed out. Default: 0.3f
 * @param contrast Base level of the metallic RGB shift. Default: 0.9f
 * @param checkerScale Size of the checker cells. Default: 5f
 * @param checkerIntensity Contrast boost from the checker. Default: 1.2f
 * @param noiseScale Size of the value-noise cells. Default: 100f
 * @param noiseIntensity Contrast boost from the noise. Default: 1.2f
 * @param lightIntensity Strength of the glare while dragging. Default: 0.3f
 * @param pattern Checker pattern shape. Default: [StickerPattern.Diamond]
 * @param motion What drives the foil. Default: [StickerMotion.Tilt]
 * @param tiltIntensity Multiplier on the raw device tilt. Default: 1f
 * @param tiltRangeDegrees The tilt at which the shine reaches the card's edge.
 *   Smaller means the highlight sweeps with less wrist movement. Default: 20f
 * @param maxTiltDegrees How far the card itself rotates at full tilt. Keep it
 *   well under 90 — at 90 the card turns edge-on and vanishes. Default: 14f
 * @param foilShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The sticker — ideally with a transparent surround.
 */
@Composable
fun HolographicStickerEffect(
    modifier: Modifier = Modifier,
    scale: Float = 3f,
    colorIntensity: Float = 0.8f,
    foilBaseline: Float = 0.3f,
    contrast: Float = 0.9f,
    checkerScale: Float = 5f,
    checkerIntensity: Float = 1.2f,
    noiseScale: Float = 100f,
    noiseIntensity: Float = 1.2f,
    lightIntensity: Float = 0.3f,
    pattern: StickerPattern = StickerPattern.Diamond,
    motion: StickerMotion = StickerMotion.Tilt,
    tiltIntensity: Float = 1f,
    tiltRangeDegrees: Float = 20f,
    maxTiltDegrees: Float = 14f,
    foilShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    val width = layoutSize.width.toFloat()
    val height = layoutSize.height.toFloat()

    val deviceTilt by rememberDeviceTilt(enabled = motion == StickerMotion.Tilt)

    // Rotation applied to the card, in radians, plus the shader transform in px.
    val rotYRad: Float
    val rotXRad: Float
    val targetX: Float
    val targetY: Float

    if (motion == StickerMotion.Tilt) {
        // Normalise the tilt to -1..1 over `tiltRangeDegrees`, then drive the
        // shine across the full card while rotating it only gently. Feeding the
        // raw radians straight in (as the Swift original does) barely moves the
        // highlight, because a hand-sized tilt is a small fraction of a radian.
        val rangeRad = Math.toRadians(tiltRangeDegrees.toDouble()).toFloat()
        val maxRad = Math.toRadians(maxTiltDegrees.toDouble()).toFloat()
        val nx = (deviceTilt.x * tiltIntensity / rangeRad).coerceIn(-1f, 1f)
        val ny = (deviceTilt.y * tiltIntensity / rangeRad).coerceIn(-1f, 1f)
        targetX = nx * width / 2f
        targetY = ny * height / 2f
        rotYRad = nx * maxRad
        rotXRad = ny * maxRad
    } else {
        targetX = if (dragging && width > 0f) touch.x - width / 2f else 0f
        targetY = if (dragging && height > 0f) touch.y - height / 2f else 0f
        rotYRad = if (width > 0f) (targetX / width) * tiltIntensity else 0f
        rotXRad = if (height > 0f) (targetY / height) * tiltIntensity else 0f
    }

    val active = motion == StickerMotion.Tilt || dragging

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
        shader.setFloatUniform("uBaseline", foilBaseline)
        shader.setFloatUniform("uContrast", contrast)
        shader.setFloatUniform("uCheckerScale", checkerScale)
        shader.setFloatUniform("uCheckerIntensity", checkerIntensity)
        shader.setFloatUniform("uNoiseScale", noiseScale)
        shader.setFloatUniform("uNoiseIntensity", noiseIntensity)
        shader.setFloatUniform("uPatternType", if (pattern == StickerPattern.Diamond) 0f else 1f)
    }

    // The library rotates in radians; Compose wants degrees.
    val radToDeg = 57.29578f
    val animatedRotY by animateFloatAsState(rotYRad * radToDeg, spring(), label = "stickerRotY")
    val animatedRotX by animateFloatAsState(-rotXRad * radToDeg, spring(), label = "stickerRotX")

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize = it }
            .graphicsLayer {
                rotationY = animatedRotY
                rotationX = animatedRotX
                clip = true
                if (width > 0f && height > 0f) {
                    renderEffect = shader.asContentRenderEffect()
                }
            }
            .then(
                if (motion == StickerMotion.Drag) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touch = offset
                                dragging = true
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                touch = change.position
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false }
                        )
                    }
                } else {
                    Modifier
                }
            )
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
    uniform float uBaseline;
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
        // Sampling past the layer clamps to the edge pixel (alpha 1), not to
        // transparent — without this the foil smears across the whole screen.
        if (fragCoord.x < 0.0 || fragCoord.y < 0.0 ||
            fragCoord.x >= uSize.x || fragCoord.y >= uSize.y) {
            return half4(0.0);
        }

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
        half4 mixedFoil = lightnessMix(color, foilColor, uIntensity, uBaseline);
        half4 checkerFoil = increaseContrast(mixedFoil, pattern, uCheckerIntensity);
        half4 noiseCheckerFoil = increaseContrast(checkerFoil, noise, uNoiseIntensity);

        // Mask by the sticker's own alpha, back to premultiplied.
        half3 outRgb = clamp(noiseCheckerFoil.rgb, half3(0.0), half3(1.0)) * half(alpha);
        return half4(outRgb, half(alpha));
    }
""".trimIndent()
