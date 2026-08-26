package com.example.shaderrippleeffect.internal

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlinx.coroutines.android.awaitFrame
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/**
 * Shared building blocks used by the shader effects in this module. Keeping the
 * shader sources and the render-effect / frame-clock boilerplate in one place
 * avoids the copy-paste drift that several effects had previously.
 */

/** GLSL/AGSL shader sources shared across effects. */
internal object AgslShaders {

    /**
     * Radial ripple displacement: pushes each pixel outward from [uOrigin] by a
     * decaying sine wave. Shared by [com.example.shaderrippleeffect.ShaderRippleEffect]
     * and the expanding-wave stage of `RevealShaderEffect`.
     */
    val RIPPLE_DISPLACEMENT = """
        uniform shader inputShader;
        uniform float2 uResolution;
        uniform float2 uOrigin;
        uniform float uTime;
        uniform float uAmplitude;
        uniform float uFrequency;
        uniform float uDecay;
        uniform float uSpeed;

        half4 main(float2 fragCoord) {
            float2 pos = fragCoord;
            float distance = length(pos - uOrigin);
            float delay = distance / uSpeed;
            float time = max(0.0, uTime - delay);
            float rippleAmount = uAmplitude * sin(uFrequency * time) * exp(-uDecay * time);
            float2 n = normalize(pos - uOrigin);
            float2 newPos = pos + rippleAmount * n;
            return inputShader.eval(newPos);
        }
    """.trimIndent()
}

/** The child-shader name every effect in this module binds its content to. */
internal const val INPUT_SHADER = "inputShader"

/** Wraps this [RuntimeShader] as a Compose [RenderEffect] bound to [INPUT_SHADER]. */
internal fun RuntimeShader.asContentRenderEffect(): ComposeRenderEffect =
    RenderEffect.createRuntimeShaderEffect(this, INPUT_SHADER).asComposeRenderEffect()

/**
 * Drives a wall-clock animation using the Compose frame clock, reporting the
 * seconds elapsed since the first frame on every frame until [durationSeconds]
 * is reached. Replaces the hand-rolled `withFrameNanos` loops that were repeated
 * across the effects.
 */
internal suspend fun animateElapsedSeconds(
    durationSeconds: Float,
    onElapsed: (Float) -> Unit
) {
    onElapsed(0f)
    val startNanos = withFrameNanos { it }
    while (true) {
        val nowNanos = withFrameNanos { it }
        val elapsed = (nowNanos - startNanos) / 1_000_000_000f
        onElapsed(elapsed)
        if (elapsed >= durationSeconds) break
        awaitFrame()
    }
}
