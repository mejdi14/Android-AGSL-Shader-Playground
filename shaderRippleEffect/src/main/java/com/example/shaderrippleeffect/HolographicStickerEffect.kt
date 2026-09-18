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

/** Texture revealed inside the light sweep. */
enum class StickerPattern { Diamond, Square }

/** What drives the shine: tilting the device, or dragging across it. */
enum class StickerMotion { Tilt, Drag }

/**
 * A glossy foil card that catches the light as you tilt it.
 *
 * Rather than tinting the artwork, this keeps the card's own colours and lights
 * it: a broad diagonal sheen sweeps across as you tilt, and the foil's diamond
 * texture and glitter flakes only show up *inside* that sweep — the way a real
 * foil card only reveals its texture where light hits it. Everything added is
 * neutral white, so the art underneath stays true.
 *
 * Motion follows Benjamin Pisano's SwiftUI
 * [Sticker](https://github.com/bpisano/Sticker) library: tilt (or drag) moves a
 * transform that drives the highlight, and the card leans slightly in 3D.
 *
 * @param sheenWidth How broad the light sweep is. Default: 0.28f
 * @param sheenIntensity Brightness of the sweep. Default: 0.5f
 * @param foilTexture Strength of the diamond texture inside the sweep. Default: 0.22f
 * @param sparkle Strength of the glitter flakes inside the sweep. Default: 0.3f
 * @param checkerScale How fine the diamond texture is. Default: 26f
 * @param noiseScale How fine the glitter grain is. Default: 220f
 * @param glareIntensity A soft highlight that follows the tilt point. Default: 0.22f
 * @param glareRadius Size of that highlight, in fractions of the card. Default: 0.5f
 * @param pattern Texture shape. Default: [StickerPattern.Diamond]
 * @param motion What drives the shine. Default: [StickerMotion.Tilt]
 * @param tiltIntensity Multiplier on the raw device tilt. Default: 1f
 * @param tiltRangeDegrees The tilt at which the sweep crosses the whole card.
 *   Default: 16f
 * @param maxTiltDegrees How far the card itself leans at full tilt. Default: 10f
 * @param reverseMotion Flips which way the light travels. Default: true
 * @param foilShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The card or sticker.
 */
@Composable
fun HolographicStickerEffect(
    modifier: Modifier = Modifier,
    sheenWidth: Float = 0.28f,
    sheenIntensity: Float = 0.5f,
    foilTexture: Float = 0.22f,
    sparkle: Float = 0.3f,
    checkerScale: Float = 26f,
    noiseScale: Float = 220f,
    glareIntensity: Float = 0.22f,
    glareRadius: Float = 0.5f,
    pattern: StickerPattern = StickerPattern.Diamond,
    motion: StickerMotion = StickerMotion.Tilt,
    tiltIntensity: Float = 1f,
    tiltRangeDegrees: Float = 16f,
    maxTiltDegrees: Float = 10f,
    reverseMotion: Boolean = true,
    foilShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    val width = layoutSize.width.toFloat()
    val height = layoutSize.height.toFloat()

    val deviceTilt by rememberDeviceTilt(enabled = motion == StickerMotion.Tilt)

    // -1..1 on each axis, whichever input is driving it.
    val nx: Float
    val ny: Float
    if (motion == StickerMotion.Tilt) {
        val rangeRad = Math.toRadians(tiltRangeDegrees.toDouble()).toFloat()
        nx = (deviceTilt.x * tiltIntensity / rangeRad).coerceIn(-1f, 1f)
        ny = (deviceTilt.y * tiltIntensity / rangeRad).coerceIn(-1f, 1f)
    } else {
        nx = if (dragging && width > 0f) ((touch.x / width) * 2f - 1f).coerceIn(-1f, 1f) else 0f
        ny = if (dragging && height > 0f) ((touch.y / height) * 2f - 1f).coerceIn(-1f, 1f) else 0f
    }

    val active = motion == StickerMotion.Tilt || dragging
    val direction = if (reverseMotion) -1f else 1f

    val animX by animateFloatAsState(nx, spring(), label = "stickerNx")
    val animY by animateFloatAsState(ny, spring(), label = "stickerNy")
    val glare by animateFloatAsState(
        if (active) glareIntensity else 0f,
        spring(),
        label = "stickerGlare"
    )

    val shaderCode = foilShaderCode ?: FOIL_SHINE_SHADER
    val shader = remember(shaderCode) { RuntimeShader(shaderCode) }

    if (width > 0f && height > 0f) {
        val sx = animX * direction
        val sy = animY * direction
        // The sweep rides a diagonal axis that spans roughly 0..1 across the
        // card; push the centre past both ends so it can travel fully off.
        val sweep = ((sx + sy) * 0.5f)
        val sheenCenter = 0.5f + sweep * 0.9f

        shader.setFloatUniform("uSize", floatArrayOf(width, height))
        shader.setFloatUniform("uSheenCenter", sheenCenter)
        shader.setFloatUniform("uSheenWidth", sheenWidth.coerceAtLeast(0.01f))
        shader.setFloatUniform("uSheenIntensity", sheenIntensity)
        shader.setFloatUniform("uFoilTexture", foilTexture)
        shader.setFloatUniform("uSparkle", sparkle)
        shader.setFloatUniform("uCheckerScale", checkerScale)
        shader.setFloatUniform("uNoiseScale", noiseScale)
        shader.setFloatUniform("uGlarePos", floatArrayOf(0.5f + sx * 0.5f, 0.5f + sy * 0.5f))
        shader.setFloatUniform("uGlareRadius", glareRadius.coerceAtLeast(0.01f))
        shader.setFloatUniform("uGlareIntensity", glare)
        shader.setFloatUniform("uPatternType", if (pattern == StickerPattern.Diamond) 0f else 1f)
    }

    val radToDeg = 57.29578f
    val maxRad = Math.toRadians(maxTiltDegrees.toDouble()).toFloat()
    val rotY by animateFloatAsState(animX * maxRad * radToDeg, spring(), label = "stickerRotY")
    val rotX by animateFloatAsState(-animY * maxRad * radToDeg, spring(), label = "stickerRotX")

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize = it }
            .graphicsLayer {
                rotationY = rotY
                rotationX = rotX
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

private val FOIL_SHINE_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uSheenCenter;
    uniform float uSheenWidth;
    uniform float uSheenIntensity;
    uniform float uFoilTexture;
    uniform float uSparkle;
    uniform float uCheckerScale;
    uniform float uNoiseScale;
    uniform float2 uGlarePos;
    uniform float uGlareRadius;
    uniform float uGlareIntensity;
    uniform float uPatternType;

    float hash21(float2 p) {
        p = fract(p * float2(123.34, 456.21));
        p += dot(p, p + 45.32);
        return fract(p.x * p.y);
    }

    float valueNoise(float2 uv) {
        float2 i = floor(uv);
        float2 f = fract(uv);
        float a = hash21(i);
        float b = hash21(i + float2(1.0, 0.0));
        float c = hash21(i + float2(0.0, 1.0));
        float d = hash21(i + float2(1.0, 1.0));
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    float checkerAt(float2 uv, float scale, float degrees, float aspect) {
        float rad = degrees * 3.14159265 / 180.0;
        float2 p = float2(uv.x * aspect, uv.y) * scale;
        float ca = cos(rad);
        float sa = sin(rad);
        float2 r = float2(ca * p.x - sa * p.y, sa * p.x + ca * p.y);
        return mod(floor(r.x) + floor(r.y), 2.0) < 0.5 ? 0.0 : 1.0;
    }

    half4 main(float2 fragCoord) {
        // Sampling past the layer clamps to the edge pixel, not to transparent.
        if (fragCoord.x < 0.0 || fragCoord.y < 0.0 ||
            fragCoord.x >= uSize.x || fragCoord.y >= uSize.y) {
            return half4(0.0);
        }

        half4 src = inputShader.eval(fragCoord);
        float alpha = float(src.a);
        if (alpha <= 0.001) {
            return half4(0.0);
        }

        half3 base = src.rgb / half(alpha);
        float2 uv = fragCoord / uSize;
        float aspect = uSize.x / uSize.y;

        // Broad diagonal sweep of light, plus a tighter bright core.
        float axis = (uv.x + uv.y) * 0.5;
        float d = (axis - uSheenCenter) / uSheenWidth;
        float band = exp(-d * d);
        float core = exp(-d * d * 5.0);

        // Foil texture and glitter, only where the light actually falls.
        float texture = uPatternType < 0.5
            ? checkerAt(uv, uCheckerScale, 45.0, aspect)
            : checkerAt(uv, uCheckerScale, 0.0, aspect);
        float grain = valueNoise(uv * uNoiseScale);
        float flakes = smoothstep(0.70, 0.95, grain);

        // Soft highlight that follows the tilt point.
        float glare = smoothstep(uGlareRadius, 0.0, distance(uv, uGlarePos)) * uGlareIntensity;

        half3 lit = base;
        lit += half3(half(band * uSheenIntensity));
        lit += half3(half(core * uSheenIntensity * 0.5));
        lit += half3(half(band * texture * uFoilTexture));
        lit += half3(half(band * flakes * uSparkle));
        lit += half3(half(glare));

        lit = clamp(lit, half3(0.0), half3(1.0));
        return half4(lit * half(alpha), half(alpha));
    }
""".trimIndent()
