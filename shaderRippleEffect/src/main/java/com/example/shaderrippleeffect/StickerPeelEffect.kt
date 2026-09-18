package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sticker peeling off a surface, folding back on itself.
 *
 * A fold line travels in from the far corner along [peelAngleDegrees]. Three
 * regions result:
 *
 *  - **past the fold** — the sticker has been peeled away, so the surface below
 *    shows through, with the flap's shadow falling across it;
 *  - **under the flap** — the peeled part has folded back over the sticker, so
 *    what you see here is its grey [backColor] backing, darkest in the crease;
 *  - **still attached** — the untouched holographic face.
 *
 * The flap is a mirror of the source about the fold line, which is what makes
 * this read as folding rather than stretching: material that was at `front + d`
 * is drawn at `front - d`, reversed.
 *
 * Give this a box larger than the artwork inside it — the flap and its shadow
 * need room, and the render effect is clipped to the layer bounds.
 *
 * @param peelAngleDegrees Direction the fold line travels. Default: 40f
 * @param peelExtent How far across the sticker the peel runs, 0..1. The flap
 *   covers twice this, so values above ~0.5 fold the whole sticker over.
 *   Default: 0.4f
 * @param backColor The colour of the sticker's backing. Default: light grey
 * @param lightAngleDegrees Direction the light comes from, steering the foil.
 * @param foilAmount How strongly the spectral ramp tints the face. Default: 0.22f
 * @param shineWidth Width of the glare band. Default: 0.22f
 * @param shineSpeed How fast the foil phase drifts. Default: 0.25f
 * @param flakeBrightness Brightness of the fine glitter grain. Default: 0.12f
 * @param shadowOpacity Strength of the flap's shadow. Default: 0.45f
 * @param shadowSpread How far the shadow reaches from the fold. Default: 0.12f
 * @param creaseSoftness How quickly the crease shading eases out. Default: 0.18f
 * @param loopDurationSeconds Length of one 0 -> 1 -> 0 peel cycle. Default: 4.8f
 * @param animated Set false for a reduced-motion fallback held at [staticProgress].
 * @param staticProgress Peel progress used when [animated] is false. Default: 0.6f
 * @param peelShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The composable to peel — ideally with a transparent surround.
 */
@Composable
fun StickerPeelEffect(
    modifier: Modifier = Modifier,
    peelAngleDegrees: Float = 40f,
    peelExtent: Float = 0.4f,
    backColor: Color = Color(0xFFC6C4BD),
    lightAngleDegrees: Float = 135f,
    foilAmount: Float = 0.22f,
    shineWidth: Float = 0.22f,
    shineSpeed: Float = 0.25f,
    flakeBrightness: Float = 0.12f,
    shadowOpacity: Float = 0.45f,
    shadowSpread: Float = 0.12f,
    creaseSoftness: Float = 0.18f,
    loopDurationSeconds: Float = 4.8f,
    animated: Boolean = true,
    staticProgress: Float = 0.6f,
    peelShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
        }
    }

    val progress = if (animated) {
        peelProgressAt((elapsed / loopDurationSeconds.coerceAtLeast(0.1f)) % 1f)
    } else {
        staticProgress.coerceIn(0f, 1f)
    }

    val shaderCode = peelShaderCode ?: STICKER_PEEL_SHADER
    val shader = remember(shaderCode) { RuntimeShader(shaderCode) }

    val width = layoutSize.width.toFloat()
    val height = layoutSize.height.toFloat()

    if (width > 0f && height > 0f) {
        val peelRad = Math.toRadians(peelAngleDegrees.toDouble())
        val lightRad = Math.toRadians(lightAngleDegrees.toDouble())

        shader.setFloatUniform("uSize", floatArrayOf(width, height))
        shader.setFloatUniform("uProgress", progress)
        shader.setFloatUniform("uPeelDir", floatArrayOf(cos(peelRad).toFloat(), sin(peelRad).toFloat()))
        shader.setFloatUniform("uPeelExtent", peelExtent.coerceIn(0.05f, 1f))
        shader.setFloatUniform(
            "uBackColor",
            floatArrayOf(backColor.red, backColor.green, backColor.blue)
        )
        shader.setFloatUniform("uLightDir", floatArrayOf(cos(lightRad).toFloat(), sin(lightRad).toFloat()))
        shader.setFloatUniform("uFoilAmount", foilAmount)
        shader.setFloatUniform("uShineWidth", shineWidth.coerceAtLeast(0.01f))
        shader.setFloatUniform("uShineSpeed", shineSpeed)
        shader.setFloatUniform("uTime", elapsed)
        shader.setFloatUniform("uFlake", flakeBrightness)
        shader.setFloatUniform("uShadowOpacity", shadowOpacity)
        shader.setFloatUniform("uShadowSpread", shadowSpread.coerceAtLeast(0.001f))
        shader.setFloatUniform("uCrease", creaseSoftness.coerceAtLeast(0.001f))
    }

    Box(
        modifier = modifier
            .onSizeChanged { layoutSize = it }
            .graphicsLayer {
                if (width > 0f && height > 0f) {
                    renderEffect = shader.asContentRenderEffect()
                }
            }
    ) {
        content()
    }
}

/**
 * The peel timeline: fold back, a brief hold, reattach, settle. Returns exactly
 * 0 at both ends so the loop closes without a jump.
 */
internal fun peelProgressAt(t: Float): Float = when {
    t < 0.45f -> smootherStep(t / 0.45f)
    t < 0.55f -> 1f
    t < 0.95f -> 1f - smootherStep((t - 0.55f) / 0.40f)
    else -> 0f
}

private fun smootherStep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * c * (c * (c * 6f - 15f) + 10f)
}

private val STICKER_PEEL_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uProgress;
    uniform float2 uPeelDir;
    uniform float uPeelExtent;
    uniform float3 uBackColor;
    uniform float2 uLightDir;
    uniform float uFoilAmount;
    uniform float uShineWidth;
    uniform float uShineSpeed;
    uniform float uTime;
    uniform float uFlake;
    uniform float uShadowOpacity;
    uniform float uShadowSpread;
    uniform float uCrease;

    float hash21(float2 p) {
        p = fract(p * float2(123.34, 456.21));
        p += dot(p, p + 45.32);
        return fract(p.x * p.y);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uSize;
        float axis = dot(uv - 0.5, uPeelDir) + 0.5;

        // The fold line travels in from the far edge (axis = 1).
        float front = 1.0 - uProgress * uPeelExtent;

        // 1. Past the fold the sticker is gone — only the flap's shadow is left.
        if (axis > front) {
            float sd = (axis - front) / uShadowSpread;
            float sh = exp(-sd * sd) * uShadowOpacity;
            sh *= float(inputShader.eval(fragCoord).a);
            return half4(0.0, 0.0, 0.0, half(sh));
        }

        float behind = front - axis;

        // 2. Under the flap: material from `front + behind`, mirrored back over
        //    the fold, seen from behind — so we draw the grey backing.
        if (front + behind <= 1.0) {
            float2 flapUv = uv + uPeelDir * (2.0 * behind);
            float flapA = float(inputShader.eval(flapUv * uSize).a);

            float t = clamp(behind / uCrease, 0.0, 1.0);
            float shade = mix(0.55, 1.0, t);
            float lipD = behind / (uCrease * 0.35);
            float lip = exp(-lipD * lipD) * 0.25;
            float3 back = uBackColor * shade + float3(lip, lip, lip);

            return half4(half3(back) * half(flapA), half(flapA));
        }

        // 3. Still attached: the holographic face.
        half4 src = inputShader.eval(fragCoord);

        float2 shineDir = normalize(uPeelDir + uLightDir * 0.5);
        float fp = fract(dot(uv, shineDir) + uTime * uShineSpeed);
        float3 rainbow = 0.5 + 0.5 * cos(6.28318 * (fp + float3(0.0, 0.33, 0.67)));
        float bd = (fp - 0.5) / uShineWidth;
        float band = exp(-bd * bd);
        float grain = hash21(uv * 80.0);

        half3 shaded = src.rgb;
        shaded = mix(shaded, shaded * half3(rainbow) * half(1.5), half(uFoilAmount));
        shaded += half3(band * 0.22) * src.a;
        shaded += half3(step(0.9, grain) * uFlake * band) * src.a;

        // Contact shadow where the flap's tip lies over the face.
        float tipDist = (front + behind) - 1.0;
        float ts = tipDist / (uShadowSpread * 0.7);
        shaded *= half(1.0 - 0.45 * exp(-ts * ts) * uShadowOpacity);

        shaded = clamp(shaded, half3(0.0), half3(src.a));
        return half4(shaded, src.a);
    }
""".trimIndent()
