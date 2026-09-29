package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * A tap-triggered transition where a ring of liquid glass swells out from the
 * centre and sweeps the new content in behind it.
 *
 * The motion is the [RevealShaderEffect] wave; the material is the refraction
 * from [LiquidGlass] — the same bevel falloff and colour split, at the same
 * strength — minus its ripple and swirl, which wobble when the glass moves.
 * Nothing else: no rim light, no shadow, no tint.
 *
 * Each half of the ring is one of LiquidGlass's bevels. The distortion fades to
 * zero at the ring's inner and outer boundaries and at its centre line, and the
 * two photos cross-fade across that centre line, so the wave has no visible
 * edge anywhere — you only see it by what it bends.
 *
 * Tap again once it finishes to wave back to the first content.
 *
 * @param durationMillis Length of one sweep. Default: 1600
 * @param bandWidth Full thickness of the glass ring. Default: 180.dp
 * @param refraction Strength relative to LiquidGlass; 1 matches it exactly.
 *   Default: 1f
 * @param dispersion Colour split, as in LiquidGlass. Default: 0.3f
 * @param fromTapPoint Start the wave where you tap instead of at the centre.
 *   Default: false
 * @param revealShaderCode Custom shader code to override the default.
 * @param modifier Additional modifiers to apply.
 * @param firstContent Shown first.
 * @param secondContent Revealed by the first tap.
 */
@Composable
fun LiquidGlassRevealEffect(
    modifier: Modifier = Modifier,
    durationMillis: Int = 1600,
    bandWidth: Dp = 180.dp,
    refraction: Float = 1f,
    dispersion: Float = 0.3f,
    fromTapPoint: Boolean = false,
    revealShaderCode: String? = null,
    firstContent: @Composable () -> Unit,
    secondContent: @Composable () -> Unit
) {
    var showingSecond by remember { mutableStateOf(false) }
    var animating by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val code = revealShaderCode ?: LIQUID_GLASS_REVEAL_SHADER
    val outgoingShader = remember(code) { RuntimeShader(code) }
    val incomingShader = remember(code) { RuntimeShader(code) }

    val current = if (showingSecond) secondContent else firstContent
    val next = if (showingSecond) firstContent else secondContent

    Box(
        modifier = modifier.pointerInput(fromTapPoint, durationMillis) {
            detectTapGestures { tap ->
                if (animating) return@detectTapGestures
                origin = if (fromTapPoint) tap else Offset.Unspecified
                animating = true
                scope.launch {
                    progress.snapTo(0f)
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis, easing = WaveEasing)
                    )
                    showingSecond = !showingSecond
                    progress.snapTo(0f)
                    animating = false
                }
            }
        }
    ) {
        // Outgoing photo: refracted through the outer half of the ring.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (animating && size.width > 0f && size.height > 0f) {
                        outgoingShader.configure(
                            size = size,
                            origin = origin,
                            progress = progress.value,
                            fullBand = bandWidth.toPx(),
                            refraction = refraction,
                            dispersion = dispersion,
                            incoming = false
                        )
                        renderEffect = outgoingShader.asContentRenderEffect()
                    } else {
                        renderEffect = null
                    }
                }
        ) {
            current()
        }

        // Incoming photo: refracted through the inner half, clear beyond it.
        if (animating) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (size.width > 0f && size.height > 0f) {
                            incomingShader.configure(
                                size = size,
                                origin = origin,
                                progress = progress.value,
                                fullBand = bandWidth.toPx(),
                                refraction = refraction,
                                dispersion = dispersion,
                                    incoming = true
                            )
                            renderEffect = incomingShader.asContentRenderEffect()
                        }
                    }
            ) {
                next()
            }
        }
    }
}

/** Slow to leave the centre, then rolls out and settles. */
private val WaveEasing = CubicBezierEasing(0.5f, 0f, 0.15f, 1f)

/** refractPx / bevelPx in [LiquidGlass]: 0.65r / 1.4r. */
private const val LIQUID_GLASS_REFRACT_RATIO = 0.65f / 1.4f

/** Half-width of the cross-fade across the ring's centre line, in band units. */
private const val SEAM_BLEND = 0.3f

private fun RuntimeShader.configure(
    size: Size,
    origin: Offset,
    progress: Float,
    fullBand: Float,
    refraction: Float,
    dispersion: Float,
    incoming: Boolean
) {
    val center = if (origin.isSpecified) origin else Offset(size.width / 2f, size.height / 2f)

    // The ring grows out of a droplet rather than popping in at full width.
    val halfBandFull = fullBand / 2f
    val halfBand = max(halfBandFull * (progress / 0.12f).coerceIn(0f, 1f), 1f)

    // Travel far enough that the whole ring clears the farthest corner.
    val farthest = maxOf(
        hypot(center.x, center.y),
        hypot(size.width - center.x, center.y),
        hypot(center.x, size.height - center.y),
        hypot(size.width - center.x, size.height - center.y)
    )
    val radius = progress * (farthest + halfBandFull)

    setFloatUniform("uSize", size.width, size.height)
    setFloatUniform("uOrigin", center.x, center.y)
    setFloatUniform("uRadius", radius)
    setFloatUniform("uBand", halfBand)
    // LiquidGlass bends by 0.65r over a bevel 1.4r deep; keep that ratio.
    setFloatUniform("uRefract", halfBand * LIQUID_GLASS_REFRACT_RATIO * refraction)
    setFloatUniform("uDispersion", dispersion)
    setFloatUniform("uBlend", SEAM_BLEND)
    setFloatUniform("uIncoming", if (incoming) 1f else 0f)
}

private val LIQUID_GLASS_REVEAL_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float2 uOrigin;
    uniform float uRadius;
    uniform float uBand;
    uniform float uRefract;
    uniform float uDispersion;
    uniform float uBlend;
    uniform float uIncoming;

    half4 main(float2 p) {
        float2 v = p - uOrigin;
        float d = length(v);
        float2 dir = d > 0.001 ? v / d : float2(1.0, 0.0);

        // Signed position across the ring: -1 inner edge, 0 centre line, +1 outer edge.
        float s = (d - uRadius) / uBand;

        // The incoming photo covers the inside and cross-fades out across the
        // centre line, so the two photos never meet on a hard edge.
        float coverage = 1.0;
        if (uIncoming > 0.5) {
            coverage = 1.0 - smoothstep(-uBlend, uBlend, s);
            if (coverage <= 0.0) return half4(0.0);
        }

        if (abs(s) >= 1.0) {
            return inputShader.eval(p) * half(coverage);
        }

        // Each half of the ring is one LiquidGlass bevel: t runs from 0 at the
        // ring's boundary to 1 at its centre line, n is that edge's outward normal.
        float t = 1.0 - abs(s);
        float2 n = s < 0.0 ? -dir : dir;

        // LiquidGlass's bevel falloff. Its ripple and swirl terms are left out:
        // on a still slab they read as texture, but on a ring sweeping outward
        // the bands slide across the photo and look like a wiggle.
        float falloff = pow(1.0 - t, 1.3);
        float amount = uRefract * falloff;

        // LiquidGlass is strongest right at its edge, which is what draws a
        // border. Ease it in from the boundary so the ring has none.
        amount *= smoothstep(0.0, 0.3, t);

        float2 base = -n * amount;
        float disp = uDispersion * (1.0 - t);

        half r = inputShader.eval(p + base * (1.0 + disp)).r;
        half4 g4 = inputShader.eval(p + base);
        half b = inputShader.eval(p + base * (1.0 - disp)).b;

        return half4(r, g4.g, b, g4.a) * half(coverage);
    }
""".trimIndent()
