package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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

/**
 * Tap to send a ring of liquid glass expanding out across the content.
 *
 * The ring is the refraction from [LiquidGlass] — the same bevel falloff and
 * colour split, at the same strength — and nothing else: no rim light, no
 * shadow, no tint, no ripple. Each half of the ring is one of LiquidGlass's
 * bevels, with the distortion fading to zero at the ring's inner edge, outer
 * edge and centre line, so the ring has no visible border; you only see it by
 * what it bends.
 *
 * It expands at a constant speed from just inside the origin until it has fully
 * cleared the farthest corner.
 *
 * @param durationMillis How long the ring takes to cross. Default: 1600
 * @param bandWidth Full thickness of the glass ring. Default: 180.dp
 * @param refraction Strength relative to LiquidGlass; 1 matches it exactly.
 *   Default: 1f
 * @param dispersion Colour split, as in LiquidGlass. Default: 0.3f
 * @param fromTapPoint Start the ring where you tap instead of at the centre.
 *   Default: false
 * @param ringShaderCode Custom shader code to override the default.
 * @param modifier Additional modifiers to apply.
 * @param content The content the ring passes over.
 */
@Composable
fun LiquidGlassRingEffect(
    modifier: Modifier = Modifier,
    durationMillis: Int = 1600,
    bandWidth: Dp = 180.dp,
    refraction: Float = 1f,
    dispersion: Float = 0.3f,
    fromTapPoint: Boolean = false,
    ringShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var animating by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val code = ringShaderCode ?: LIQUID_GLASS_RING_SHADER
    val shader = remember(code) { RuntimeShader(code) }

    Box(
        modifier = modifier
            .pointerInput(fromTapPoint, durationMillis) {
                detectTapGestures { tap ->
                    if (animating) return@detectTapGestures
                    origin = if (fromTapPoint) tap else Offset.Unspecified
                    animating = true
                    scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
                        animating = false
                    }
                }
            }
            .graphicsLayer {
                if (animating && size.width > 0f && size.height > 0f) {
                    shader.configure(
                        size = size,
                        origin = origin,
                        progress = progress.value,
                        halfBand = bandWidth.toPx() / 2f,
                        refraction = refraction,
                        dispersion = dispersion
                    )
                    renderEffect = shader.asContentRenderEffect()
                } else {
                    renderEffect = null
                }
            }
    ) {
        content()
    }
}

/** refractPx / bevelPx in [LiquidGlass]: 0.65r / 1.4r. */
private const val LIQUID_GLASS_REFRACT_RATIO = 0.65f / 1.4f

private fun RuntimeShader.configure(
    size: Size,
    origin: Offset,
    progress: Float,
    halfBand: Float,
    refraction: Float,
    dispersion: Float
) {
    val center = if (origin.isSpecified) origin else Offset(size.width / 2f, size.height / 2f)
    val farthest = maxOf(
        hypot(center.x, center.y),
        hypot(size.width - center.x, center.y),
        hypot(center.x, size.height - center.y),
        hypot(size.width - center.x, size.height - center.y)
    )
    // From entirely inside the origin to entirely past the farthest corner, so
    // the ring enters and leaves on its own with nothing popping in or out.
    val radius = -halfBand + progress * (farthest + 2f * halfBand)

    setFloatUniform("uOrigin", center.x, center.y)
    setFloatUniform("uRadius", radius)
    setFloatUniform("uBand", halfBand)
    setFloatUniform("uRefract", halfBand * LIQUID_GLASS_REFRACT_RATIO * refraction)
    setFloatUniform("uDispersion", dispersion)
}

private val LIQUID_GLASS_RING_SHADER = """
    uniform shader inputShader;
    uniform float2 uOrigin;
    uniform float uRadius;
    uniform float uBand;
    uniform float uRefract;
    uniform float uDispersion;

    half4 main(float2 p) {
        float2 v = p - uOrigin;
        float d = length(v);
        float2 dir = d > 0.001 ? v / d : float2(1.0, 0.0);

        // Signed position across the ring: -1 inner edge, 0 centre line, +1 outer edge.
        float s = (d - uRadius) / uBand;
        if (abs(s) >= 1.0) {
            return inputShader.eval(p);
        }

        // Each half of the ring is one LiquidGlass bevel: t runs from 0 at the
        // ring's boundary to 1 at its centre line, n is that edge's outward normal.
        float t = 1.0 - abs(s);
        float2 n = s < 0.0 ? -dir : dir;

        // LiquidGlass's bevel falloff, eased in from the boundary so the ring
        // has no visible edge.
        float amount = uRefract * pow(1.0 - t, 1.3) * smoothstep(0.0, 0.3, t);
        float2 base = -n * amount;
        float disp = uDispersion * (1.0 - t);

        half r = inputShader.eval(p + base * (1.0 + disp)).r;
        half4 g4 = inputShader.eval(p + base);
        half b = inputShader.eval(p + base * (1.0 - disp)).b;
        return half4(r, g4.g, b, g4.a);
    }
""".trimIndent()
