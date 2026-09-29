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
 * Sits between [RevealShaderEffect] and [LiquidGlass]: the motion is the reveal
 * wave, the material is the glass. The ring is a lens with a round cross
 * section — it magnifies and bends what's under it, splits colour at its edges,
 * and catches a rim light from the top left.
 *
 * Both photos sit in their own layer running the same shader. The outgoing one
 * draws the *outer* half of the ring, the incoming one the *inner* half and is
 * transparent beyond it, so the seam between the two photos runs straight down
 * the crest of the glass and both halves share identical lighting.
 *
 * Tap again once it finishes to wave back to the first content.
 *
 * @param durationMillis Length of one sweep. Default: 1600
 * @param bandWidth Full thickness of the glass ring. Default: 96.dp
 * @param refraction How strongly the ring bends what's behind it, 0..1.
 *   Default: 0.6f
 * @param dispersion Colour split at the ring's edges. Default: 0.22f
 * @param wobble How liquid the ring's outline is, as a fraction of its width.
 *   Default: 0.14f
 * @param highlight Brightness of the rim light and specular. Default: 1f
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
    bandWidth: Dp = 96.dp,
    refraction: Float = 0.6f,
    dispersion: Float = 0.22f,
    wobble: Float = 0.14f,
    highlight: Float = 1f,
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
                            wobble = wobble,
                            highlight = highlight,
                            seconds = progress.value * durationMillis / 1000f,
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
                                wobble = wobble,
                                highlight = highlight,
                                seconds = progress.value * durationMillis / 1000f,
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

private fun RuntimeShader.configure(
    size: Size,
    origin: Offset,
    progress: Float,
    fullBand: Float,
    refraction: Float,
    dispersion: Float,
    wobble: Float,
    highlight: Float,
    seconds: Float,
    incoming: Boolean
) {
    val center = if (origin.isSpecified) origin else Offset(size.width / 2f, size.height / 2f)

    // The ring grows out of a droplet rather than popping in at full width.
    val halfBandFull = fullBand / 2f
    val halfBand = max(halfBandFull * (progress / 0.12f).coerceIn(0f, 1f), 1f)
    val wobblePx = halfBand * wobble

    // Travel far enough that the whole ring clears the farthest corner.
    val farthest = maxOf(
        hypot(center.x, center.y),
        hypot(size.width - center.x, center.y),
        hypot(center.x, size.height - center.y),
        hypot(size.width - center.x, size.height - center.y)
    )
    val radius = progress * (farthest + halfBandFull + wobblePx * 2f)

    setFloatUniform("uSize", size.width, size.height)
    setFloatUniform("uOrigin", center.x, center.y)
    setFloatUniform("uRadius", radius)
    setFloatUniform("uBand", halfBand)
    setFloatUniform("uRefract", refraction)
    setFloatUniform("uDispersion", dispersion)
    setFloatUniform("uWobble", wobblePx)
    setFloatUniform("uTime", seconds)
    setFloatUniform("uHighlight", highlight)
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
    uniform float uWobble;
    uniform float uTime;
    uniform float uHighlight;
    uniform float uIncoming;

    half4 main(float2 p) {
        float2 v = p - uOrigin;
        float d = length(v);
        float2 dir = d > 0.001 ? v / d : float2(1.0, 0.0);

        // A liquid outline: two slow travelling waves around the ring.
        float ang = atan(v.y, v.x);
        float r = uRadius + uWobble * (0.6 * sin(ang * 5.0 + uTime * 2.3)
                                     + 0.4 * sin(ang * 9.0 - uTime * 3.1));

        // Signed position across the ring: -1 inner edge, 0 crest, +1 outer edge.
        float s = (d - r) / uBand;

        // The incoming layer is only what's inside the crest, with a 1.5px
        // anti-aliased seam so the two photos meet cleanly.
        float coverage = 1.0;
        if (uIncoming > 0.5) {
            coverage = 1.0 - smoothstep(-0.75, 0.75, d - r);
            if (coverage <= 0.0) return half4(0.0);
        }

        // Off the glass: plain content, plus a soft contact shadow the ring
        // casts onto the outgoing photo just ahead of it.
        if (abs(s) >= 1.0) {
            half4 c = inputShader.eval(p);
            if (uIncoming < 0.5 && s > 1.0) {
                float sh = exp(-(s - 1.0) * 2.2) * 0.2;
                c.rgb *= half(1.0 - sh);
            }
            return c * half(coverage);
        }

        // Round cross section: height and its outward-leaning normal.
        float hgt = sqrt(max(1.0 - s * s, 0.0));
        float3 n = normalize(float3(dir * s, hgt + 0.0001));

        // Lens: pull samples toward the crest so the glass magnifies. Zero at
        // the crest and at both edges, so the ring meets the photo seamlessly.
        float bend = s * hgt;
        float2 off = -dir * bend * 2.0 * uRefract * uBand;

        // Chromatic split, strongest toward the edges where glass is steepest.
        float disp = uDispersion * abs(s);
        half4 g4 = inputShader.eval(p + off);
        half rr = inputShader.eval(p + off * (1.0 + disp)).r;
        half bb = inputShader.eval(p + off * (1.0 - disp)).b;
        float a = float(g4.a);
        float3 col = float3(float(rr), float(g4.g), float(bb));

        // Lighting from the top left, matching LiquidGlass.
        float3 L = normalize(float3(-0.55, -0.85, 0.9));
        float3 H = normalize(L + float3(0.0, 0.0, 1.0));
        float spec = pow(max(dot(n, H), 0.0), 48.0);
        float edge = abs(s);
        float fres = pow(edge, 4.0);
        float rim = smoothstep(0.82, 0.97, edge) * (1.0 - smoothstep(0.97, 1.0, edge));
        float facing = dot(dir, normalize(float2(-0.55, -0.85)));

        col *= 1.04;
        col *= 1.0 - 0.14 * fres * max(-facing, 0.0);
        col += (spec * 0.85 + fres * 0.16 + rim * (0.3 + 0.4 * max(facing, 0.0)))
               * uHighlight * a;

        col = clamp(col, float3(0.0), float3(a));
        return half4(half3(col * coverage), half(a * coverage));
    }
""".trimIndent()
