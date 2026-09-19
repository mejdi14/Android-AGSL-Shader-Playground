package com.example.shaderrippleeffect

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.shaderrippleeffect.internal.asContentRenderEffect

val GlassCornerRadius = 22.dp

@Stable
class LiquidGlassBackdrop internal constructor(
    internal val layer: GraphicsLayer
) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdrop(layer) }
}

fun Modifier.recordLiquidGlassBackdrop(
    backdrop: LiquidGlassBackdrop
): Modifier =
    onGloballyPositioned { backdrop.origin = it.positionOnScreen() }
        .drawWithContent {
            backdrop.layer.record(
                size = IntSize(
                    width = size.width.toInt().coerceAtLeast(1),
                    height = size.height.toInt().coerceAtLeast(1)
                )
            ) {
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.layer)
        }

@Composable
fun LiquidGlassSurface(
    backdrop: LiquidGlassBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassCornerRadius),
    cornerRadius: Dp = GlassCornerRadius,
    tint: Color = Color(0x1AFFFFFF),
    content: @Composable BoxScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val rimWidthPx = with(density) { 1.6.dp.toPx() }
    var surfacePosition by remember { mutableStateOf(Offset.Zero) }
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }

    val bodyEffect = remember { createBodyEffect() }
    val bevelShader = remember { RuntimeShader(BEVEL_SHADER) }
    val lightingShader = remember { RuntimeShader(LIGHTING_SHADER) }
    val bevelEffect = remember(bevelShader) { bevelShader.asContentRenderEffect() }
    val lightingEffect = remember(lightingShader) { lightingShader.asContentRenderEffect() }

    SideEffect {
        val width = surfaceSize.width.toFloat()
        val height = surfaceSize.height.toFloat()
        val bevelRadius = cornerRadiusPx * 1.4f

        bevelShader.setFloatUniform("uSize", width, height)
        bevelShader.setFloatUniform("uRadius", cornerRadiusPx)
        bevelShader.setFloatUniform("uBevel", bevelRadius)
        bevelShader.setFloatUniform("uRefractAmount", cornerRadiusPx * 0.65f)
        bevelShader.setFloatUniform("uDispersion", 0.3f)

        lightingShader.setFloatUniform("uSize", width, height)
        lightingShader.setFloatUniform("uRadius", cornerRadiusPx)
        lightingShader.setFloatUniform("uBevel", cornerRadiusPx * 0.8f)
        lightingShader.setFloatUniform("uRimWidth", rimWidthPx)
    }

    Box(
        modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.12f),
                spotColor = Color.Black.copy(alpha = 0.16f)
            )
            .onGloballyPositioned {
                surfacePosition = it.positionOnScreen()
                surfaceSize = it.size
            }
            .clip(shape)
    ) {
        BackdropSample(
            backdrop = backdrop,
            surfacePosition = surfacePosition,
            shape = shape,
            effect = bodyEffect
        )
        Box(Modifier.fillMaxSize().background(tint))
        BackdropSample(
            backdrop = backdrop,
            surfacePosition = surfacePosition,
            shape = shape,
            effect = bevelEffect
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { renderEffect = lightingEffect }
                .drawBehind { drawRect(Color.White) }
        )
        content()
    }
}

@Composable
private fun BackdropSample(
    backdrop: LiquidGlassBackdrop,
    surfacePosition: Offset,
    shape: Shape,
    effect: RenderEffect
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = true
                this.shape = shape
                renderEffect = effect
            }
            .drawBehind {
                val offset = backdrop.origin - surfacePosition
                withTransform({ translate(offset.x, offset.y) }) {
                    drawLayer(backdrop.layer)
                }
            }
    )
}

private fun createBodyEffect(): RenderEffect {
    val blur = AndroidRenderEffect.createBlurEffect(
        3f,
        3f,
        Shader.TileMode.MIRROR
    )
    val saturation = ColorMatrix().apply { setSaturation(1.4f) }
    return AndroidRenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(saturation),
        blur
    ).asComposeRenderEffect()
}

private const val BEVEL_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uRadius;
    uniform float uBevel;
    uniform float uRefractAmount;
    uniform float uDispersion;

    float sdRoundedRect(float2 p) {
        float2 halfSize = uSize * 0.5;
        float radius = min(uRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + float2(radius, radius);
        return min(max(q.x, q.y), 0.0)
            + length(max(q, float2(0.0, 0.0))) - radius;
    }

    float2 gradSdRoundedRect(float2 p) {
        float e = 1.0;
        float dx = sdRoundedRect(p + float2(e, 0.0))
            - sdRoundedRect(p - float2(e, 0.0));
        float dy = sdRoundedRect(p + float2(0.0, e))
            - sdRoundedRect(p - float2(0.0, e));
        return normalize(float2(dx, dy));
    }

    half4 main(float2 fragCoord) {
        float2 p = fragCoord - uSize * 0.5;
        float d = -sdRoundedRect(p);
        if (d <= 0.0 || d >= uBevel) {
            return half4(0.0);
        }

        float t = d / uBevel;
        float2 n = gradSdRoundedRect(p);
        float2 tangent = float2(-n.y, n.x);
        float amount = uRefractAmount * pow(1.0 - t, 1.3)
            * (1.0 + 0.45 * sin(t * 13.0));
        float2 base = -n * amount
            + tangent * amount * 0.4 * sin(t * 9.0 + 1.2);
        float dispersion = uDispersion * (1.0 - t);

        half red = inputShader.eval(fragCoord + base * (1.0 + dispersion)).r;
        half green = inputShader.eval(fragCoord + base).g;
        half blue = inputShader.eval(fragCoord + base * (1.0 - dispersion)).b;
        float weight = pow(1.0 - t, 0.55);
        return half4(half3(red, green, blue) * half(weight), half(weight));
    }
"""

private const val LIGHTING_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uRadius;
    uniform float uBevel;
    uniform float uRimWidth;

    float sdRoundedRect(float2 p) {
        float2 halfSize = uSize * 0.5;
        float radius = min(uRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + float2(radius, radius);
        return min(max(q.x, q.y), 0.0)
            + length(max(q, float2(0.0, 0.0))) - radius;
    }

    float2 gradSdRoundedRect(float2 p) {
        float e = 1.0;
        float dx = sdRoundedRect(p + float2(e, 0.0))
            - sdRoundedRect(p - float2(e, 0.0));
        float dy = sdRoundedRect(p + float2(0.0, e))
            - sdRoundedRect(p - float2(0.0, e));
        return normalize(float2(dx, dy));
    }

    half4 main(float2 fragCoord) {
        float2 p = fragCoord - uSize * 0.5;
        float d = -sdRoundedRect(p);
        if (d <= 0.0 || d >= uBevel) {
            return half4(0.0);
        }

        float2 n = gradSdRoundedRect(p);
        float facing = dot(n, normalize(float2(-0.55, -0.85)));
        float litFacing = max(facing, 0.0);
        float oppositeFacing = max(-facing, 0.0);
        float rim = (1.0 - smoothstep(0.0, uRimWidth, d))
            * (0.18 + 0.62 * litFacing + 0.16 * oppositeFacing);
        float fresnel = pow(1.0 - d / uBevel, 3.0);
        float innerGlow = fresnel * (0.04 + 0.18 * litFacing);
        float shade = fresnel * oppositeFacing * 0.26;
        float light = clamp(rim + innerGlow, 0.0, 0.9);
        float alpha = clamp(max(light, shade), 0.0, 0.9);
        half sourceAlpha = inputShader.eval(fragCoord).a;
        return half4(half3(light) * sourceAlpha, half(alpha) * sourceAlpha);
    }
"""
