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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A sticker peeling off a surface, with holographic foil.
 *
 * A softened front travels across the content along [peelAngleDegrees], splitting
 * it into attached, bending and lifted regions. The lifted plateau supplies
 * height; the transition supplies curvature. Height drives everything else — an
 * inverse UV warp, a pseudo-normal for diffuse and specular shading, the
 * holographic sheen, and a blurred shadow that only appears under lifted areas
 * and collapses on reattachment.
 *
 * AGSL is fragment-only, so this is the inverse-UV-warp form of the effect: for
 * each output pixel it works out where that pixel came from on the flat sticker,
 * which avoids the holes a forward push would leave. Gradients are derived
 * analytically because AGSL has no `dFdx`/`dFdy`.
 *
 * Give this a box larger than the artwork inside it — the shadow and the lift
 * need room, and the render effect is clipped to the layer bounds.
 *
 * @param peelAngleDegrees Direction the peel front travels. Default: 35f
 * @param peelWidth Width of the transition, as a fraction of the span. Default: 0.45f
 * @param maxLift Peak height, as a fraction of the span. Default: 0.09f
 * @param liftScale How much the lifted area magnifies about the fold line. This
 *   is what actually makes the peel visible in a fragment-only port: lifting
 *   toward the camera reads as the lifted region growing. Default: 2.2f
 * @param perspectiveAmount How far lifted pixels drift along the peel. Default: 0.05f
 * @param lightAngleDegrees Direction the light comes from. Default: 135f
 * @param foilAmount How strongly the spectral ramp tints the artwork. Default: 0.22f
 * @param shineWidth Width of the bright glare band. Default: 0.22f
 * @param shineSpeed How fast the foil phase drifts. Default: 0.25f
 * @param flakeBrightness Brightness of the fine glitter grain. Default: 0.12f
 * @param shadowOpacity Shadow strength at full lift. Default: 0.35f
 * @param shadowDistance Shadow offset, as a fraction of the span. Default: 0.06f
 * @param shadowBlur Shadow blur radius, as a fraction of the span. Default: 0.02f
 * @param loopDurationSeconds Length of one 0 -> 1 -> 0 peel cycle. Default: 4.8f
 * @param animated Set false for a reduced-motion fallback held at [staticProgress].
 * @param staticProgress Peel progress used when [animated] is false. Default: 0.5f
 * @param peelShaderCode Custom shader code to override the default. Default: null
 * @param modifier Additional modifiers to apply.
 * @param content The composable to peel — ideally with a transparent surround.
 */
@Composable
fun StickerPeelEffect(
    modifier: Modifier = Modifier,
    peelAngleDegrees: Float = 35f,
    peelWidth: Float = 0.45f,
    maxLift: Float = 0.09f,
    liftScale: Float = 2.2f,
    perspectiveAmount: Float = 0.05f,
    lightAngleDegrees: Float = 135f,
    foilAmount: Float = 0.22f,
    shineWidth: Float = 0.22f,
    shineSpeed: Float = 0.25f,
    flakeBrightness: Float = 0.12f,
    shadowOpacity: Float = 0.35f,
    shadowDistance: Float = 0.06f,
    shadowBlur: Float = 0.02f,
    loopDurationSeconds: Float = 4.8f,
    animated: Boolean = true,
    staticProgress: Float = 0.5f,
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
        val cycle = (elapsed / loopDurationSeconds.coerceAtLeast(0.1f)) % 1f
        peelProgressAt(cycle)
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
        val lx = cos(lightRad).toFloat()
        val ly = sin(lightRad).toFloat()
        val lz = 0.75f
        val lLen = sqrt(lx * lx + ly * ly + lz * lz)

        shader.setFloatUniform("uSize", floatArrayOf(width, height))
        shader.setFloatUniform("uProgress", progress)
        shader.setFloatUniform("uPeelDir", floatArrayOf(cos(peelRad).toFloat(), sin(peelRad).toFloat()))
        shader.setFloatUniform("uPeelWidth", peelWidth.coerceAtLeast(0.01f))
        shader.setFloatUniform("uMaxLift", maxLift)
        shader.setFloatUniform("uLiftScale", liftScale)
        shader.setFloatUniform("uPerspective", perspectiveAmount)
        shader.setFloatUniform("uLightDir", floatArrayOf(lx / lLen, ly / lLen, lz / lLen))
        shader.setFloatUniform("uFoilAmount", foilAmount)
        shader.setFloatUniform("uShineWidth", shineWidth.coerceAtLeast(0.01f))
        shader.setFloatUniform("uShineSpeed", shineSpeed)
        shader.setFloatUniform("uTime", elapsed)
        shader.setFloatUniform("uFlake", flakeBrightness)
        shader.setFloatUniform("uShadowOpacity", shadowOpacity)
        shader.setFloatUniform("uShadowDistance", shadowDistance)
        shader.setFloatUniform("uShadowBlur", shadowBlur)
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
 * The peel timeline: travel out, a brief hold at full lift, reattach, settle.
 * Returns exactly 0 at both ends so the loop closes without a jump.
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
    uniform float uPeelWidth;
    uniform float uMaxLift;
    uniform float uLiftScale;
    uniform float uPerspective;
    uniform float3 uLightDir;
    uniform float uFoilAmount;
    uniform float uShineWidth;
    uniform float uShineSpeed;
    uniform float uTime;
    uniform float uFlake;
    uniform float uShadowOpacity;
    uniform float uShadowDistance;
    uniform float uShadowBlur;

    float hash21(float2 p) {
        p = fract(p * float2(123.34, 456.21));
        p += dot(p, p + 45.32);
        return fract(p.x * p.y);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uSize;

        // Travelling peel front. Everything behind it (axis < front) is lifted.
        float axis = dot(uv - 0.5, uPeelDir) + 0.5;
        float front = mix(-uPeelWidth, 1.0, uProgress);
        float q = clamp((axis - front) / uPeelWidth, 0.0, 1.0);
        float eased = q * q * q * (q * (q * 6.0 - 15.0) + 10.0);
        float dEased = 30.0 * q * q * (q - 1.0) * (q - 1.0);
        float height = uMaxLift * (1.0 - eased);
        float grad = -uMaxLift * dEased / uPeelWidth;

        // Inverse warp: where did this output pixel come from on the flat sticker?
        // Lifting toward the camera reads, in 2D, as the lifted region growing
        // about the fold line — that magnification is what makes the peel
        // visible at all. A plain sideways drift is sub-pixel and invisible.
        float scale = 1.0 + height * uLiftScale;
        float behind = max(front - axis, 0.0);
        float2 srcUv = uv + uPeelDir * (behind * (1.0 - 1.0 / scale) + height * uPerspective);
        half4 src = inputShader.eval(srcUv * uSize);

        // Pseudo-normal straight from the height gradient.
        float2 g = uPeelDir * grad;
        float3 N = normalize(float3(-g.x, -g.y, 1.0));

        float diffuse = 0.65 + 0.35 * max(dot(N, uLightDir), 0.0);
        float3 halfVec = normalize(uLightDir + float3(0.0, 0.0, 1.0));
        float spec = pow(max(dot(N, halfVec), 0.0), 24.0);

        // Foil phase follows the bend, not just the clock.
        float foilPhase = dot(uv, uPeelDir) + 0.35 * dot(N.xy, uPeelDir) + uTime * uShineSpeed;
        float fp = fract(foilPhase);
        float3 rainbow = 0.5 + 0.5 * cos(6.28318 * (fp + float3(0.0, 0.33, 0.67)));
        float bd = (fp - 0.5) / uShineWidth;
        float band = exp(-bd * bd);
        float grain = hash21(uv * 80.0);

        // Occlusion along the crease, so the bend reads even with foil at zero.
        float cd = (q - 0.5) / 0.22;
        float crease = exp(-cd * cd);

        half3 shaded = src.rgb * half(diffuse * (1.0 - 0.3 * crease));
        shaded = mix(shaded, shaded * half3(rainbow) * half(1.5), half(uFoilAmount));
        shaded += half3(band * spec) * src.a;
        shaded += half3(step(0.9, grain) * uFlake * band) * src.a;
        shaded = clamp(shaded, half3(0.0), half3(src.a));

        // Shadow: blurred alpha pushed away from the light, masked by local lift.
        float2 sOff = uLightDir.xy * height * uShadowDistance;
        float r = uShadowBlur * (0.25 + 0.75 * height / max(uMaxLift, 0.0001));
        float sh = 0.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7854;
            float2 p = uv - sOff + float2(cos(a), sin(a)) * r;
            sh += float(inputShader.eval(p * uSize).a);
        }
        sh = sh / 8.0;
        sh *= smoothstep(0.0, uMaxLift, height) * uShadowOpacity;

        float outA = float(src.a) + sh * (1.0 - float(src.a));
        return half4(shaded, half(outA));
    }
""".trimIndent()
