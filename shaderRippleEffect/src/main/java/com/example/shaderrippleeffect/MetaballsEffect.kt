package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/** The palette used by [MetaballsEffect] when none is given. */
val DefaultMetaballColors: List<Color> = listOf(
    Color(0xFFCC3333),
    Color(0xFFCC9933),
    Color(0xFF99CC33),
    Color(0xFF33CC33),
    Color(0xFF33CC99)
)

/**
 * Coloured gooey balls drifting around the centre and merging into smooth
 * organic shapes.
 *
 * An AGSL port of the `Metaballs` shader from
 * [@paper-design/shaders](https://shaders.paper.design/metaballs). Unlike the
 * other effects here this one is *generative* — it paints its own image rather
 * than filtering content — so it draws through a `ShaderBrush` and makes a good
 * animated background.
 *
 * Two things the original relies on don't exist in AGSL, and are handled
 * differently:
 *  - `fwidth()` (no derivatives in AGSL) sized the anti-aliased rim; that's now
 *    the fixed [edgeSoftness] instead.
 *  - a pre-baked noise texture fed the randomiser; that's now a hash function.
 *
 * @param colors Ball colours, applied in order and cycled. Up to 5 are used.
 * @param colorBack Background colour behind the balls. Default: black
 * @param count How many balls, 1..20. Fractional values shrink the last one.
 *   Default: 10f
 * @param size Ball size, 0..1. Default: 0.83f
 * @param speed Animation speed, 0..1. Default: 1f
 * @param scale Zoom level. Default: 1f
 * @param edgeSoftness Width of the anti-aliased rim. Default: 0.02f
 * @param metaballsShaderCode Custom shader code to override the default.
 * @param modifier Additional modifiers to apply.
 */
@Composable
fun MetaballsEffect(
    modifier: Modifier = Modifier,
    colors: List<Color> = DefaultMetaballColors,
    colorBack: Color = Color.Black,
    count: Float = 10f,
    size: Float = 0.83f,
    speed: Float = 1f,
    scale: Float = 1f,
    edgeSoftness: Float = 0.02f,
    metaballsShaderCode: String? = null
) {
    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> elapsed = (now - start) / 1_000_000_000f }
        }
    }

    val shaderCode = metaballsShaderCode ?: METABALLS_SHADER
    val shader = remember(shaderCode) { RuntimeShader(shaderCode) }

    val palette = remember(colors) {
        colors.ifEmpty { DefaultMetaballColors }.take(MAX_METABALL_COLORS)
    }

    Canvas(modifier) {
        val w = this.size.width
        val h = this.size.height
        if (w <= 0f || h <= 0f) return@Canvas

        shader.setFloatUniform("uSize", w, h)
        shader.setFloatUniform("uTime", elapsed)
        shader.setFloatUniform("uSpeed", speed)
        shader.setFloatUniform("uScale", scale.coerceAtLeast(0.01f))
        shader.setFloatUniform("uCount", count.coerceIn(1f, MAX_METABALLS.toFloat()))
        shader.setFloatUniform("uBallSize", size.coerceIn(0f, 1f))
        shader.setFloatUniform("uEdge", edgeSoftness.coerceAtLeast(0.0005f))
        shader.setFloatUniform("uColorsCount", palette.size.toFloat())
        shader.setFloatUniform(
            "uColorBack",
            floatArrayOf(colorBack.red, colorBack.green, colorBack.blue)
        )
        for (i in 0 until MAX_METABALL_COLORS) {
            val c = palette[i % palette.size]
            shader.setFloatUniform("uColor$i", floatArrayOf(c.red, c.green, c.blue))
        }

        drawRect(brush = ShaderBrush(shader))
    }
}

private const val MAX_METABALLS = 20
private const val MAX_METABALL_COLORS = 5

private val METABALLS_SHADER = """
    uniform float2 uSize;
    uniform float uTime;
    uniform float uSpeed;
    uniform float uScale;
    uniform float uCount;
    uniform float uBallSize;
    uniform float uEdge;
    uniform float uColorsCount;
    uniform float3 uColorBack;
    uniform float3 uColor0;
    uniform float3 uColor1;
    uniform float3 uColor2;
    uniform float3 uColor3;
    uniform float3 uColor4;

    // Stands in for the original's pre-baked noise texture.
    float randomR(float2 p) {
        return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    }

    float noise1(float x) {
        float i = floor(x);
        float f = fract(x);
        float u = f * f * (3.0 - 2.0 * f);
        return mix(randomR(float2(i, 0.0)), randomR(float2(i + 1.0, 0.0)), u);
    }

    float3 colorAt(int idx) {
        if (idx == 0) return uColor0;
        if (idx == 1) return uColor1;
        if (idx == 2) return uColor2;
        if (idx == 3) return uColor3;
        return uColor4;
    }

    float getBallShape(float2 uv, float2 c, float p) {
        float s = 0.5 * length(uv - c);
        s = 1.0 - clamp(s, 0.0, 1.0);
        return pow(s, p);
    }

    half4 main(float2 fragCoord) {
        // Square, centred coordinates so the balls never stretch with the canvas.
        float minSide = min(uSize.x, uSize.y);
        float2 centred = (fragCoord - 0.5 * uSize) / minSide;
        float2 shapeUv = centred / uScale + 0.5;

        const float firstFrameOffset = 2503.4;
        float t = 0.2 * (uTime * uSpeed + firstFrameOffset);

        float3 totalColor = float3(0.0);
        float totalShape = 0.0;

        for (int i = 0; i < 20; i++) {
            float fi = float(i);
            // Masked rather than `break`: keeps the loop trip count constant.
            float active = step(fi + 0.5, ceil(uCount));

            float idxFract = fi / 20.0;
            float angle = 6.28318530718 * idxFract;
            float speed = 1.0 - 0.2 * idxFract;

            float noiseX = noise1(angle * 10.0 + fi + t * speed);
            float noiseY = noise1(angle * 20.0 + fi - t * speed);
            float2 pos = float2(0.5) + 1e-4 + 0.9 * (float2(noiseX, noiseY) - 0.5);

            float3 ballColor = colorAt(int(mod(fi, uColorsCount)));

            // A fractional count shrinks the final ball.
            float sizeFrac = 1.0;
            if (fi > floor(uCount - 1.0)) {
                sizeFrac = fract(uCount);
            }

            float shape = getBallShape(shapeUv, pos, 45.0 - 30.0 * uBallSize * sizeFrac);
            shape *= pow(uBallSize, 0.2);
            shape = smoothstep(0.0, 1.0, shape);
            shape *= active;

            totalColor += ballColor * shape;
            totalShape += shape;
        }

        totalColor /= max(totalShape, 1e-4);

        // The original sized this rim with fwidth(); AGSL has no derivatives.
        float finalShape = smoothstep(0.4, 0.4 + uEdge, totalShape);

        float3 color = totalColor * finalShape + uColorBack * (1.0 - finalShape);

        // Cheap dither to stop the soft gradients banding.
        color += (randomR(fragCoord) - 0.5) / 255.0;

        return half4(half3(clamp(color, float3(0.0), float3(1.0))), 1.0);
    }
""".trimIndent()
