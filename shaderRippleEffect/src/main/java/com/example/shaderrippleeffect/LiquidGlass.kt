package com.example.shaderrippleeffect

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val RoundedRectSDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private val BevelShader = """
uniform shader content;
uniform float2 size;
uniform float4 cornerRadii;
uniform float bevel;
uniform float refractAmount;
uniform float dispersion;
$RoundedRectSDF
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(p, halfSize, radius);
    float d = -sd;
    if (d <= 0.0 || d >= bevel) return half4(0.0);

    float R = bevel;
    float t = d / R;
    float falloff = pow(1.0 - t, 1.3);
    float ripple = 1.0 + 0.45 * sin(t * 13.0);
    float amount = refractAmount * falloff * ripple;

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 n = gradSdRoundedRect(p, halfSize, gradRadius);
    float2 tangent = float2(-n.y, n.x);
    float swirl = 0.4 * sin(t * 9.0 + 1.2);
    float2 base = -n * amount + tangent * amount * swirl;
    float disp = dispersion * (1.0 - t);

    half r = content.eval(coord + base * (1.0 + disp)).r;
    half g = content.eval(coord + base).g;
    half b = content.eval(coord + base * (1.0 - disp)).b;

    float weight = pow(1.0 - t, 0.55);
    half3 rgb = half3(r, g, b);
    return half4(rgb * weight, weight);
}
"""

private val LightingShader = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float bevel;
uniform float rimWidth;
$RoundedRectSDF
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(p, halfSize, radius);
    float d = -sd;
    if (d <= 0.0 || d >= bevel) return half4(0.0);

    float steep = 1.0 - d / bevel;
    float fres = pow(steep, 3.0);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 n = gradSdRoundedRect(p, halfSize, gradRadius);
    float facing = dot(n, normalize(float2(-0.55, -0.85)));

    float rim = (1.0 - smoothstep(0.0, rimWidth, d)) * smoothstep(0.0, 0.6, d);
    float rimStrength = 0.3 + 0.7 * max(facing, 0.0) + 0.45 * max(-facing, 0.0);

    float glow = fres * (0.22 + 0.3 * max(facing, 0.0));
    float shade = fres * 0.26 * (1.0 - abs(facing));

    float light = clamp(rim * rimStrength + glow, 0.0, 1.0);
    float a = clamp(light + shade, 0.0, 1.0);
    return half4(half3(light), a);
}
"""

private val FallbackRim = Brush.linearGradient(
    colors = listOf(Color(0xCCFFFFFF), Color(0x1AFFFFFF), Color(0x80FFFFFF)),
)

private fun cornerFloats(radiusPx: Float): FloatArray =
    floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx)

private fun buildBodyEffect(blurPx: Float, saturation: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val blur = AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    val colorFilter = AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    return AndroidRenderEffect.createChainEffect(colorFilter, blur).asComposeRenderEffect()
}

private fun buildBevelEffect(
    size: Size,
    radiusPx: Float,
    bevelPx: Float,
    refractPx: Float,
    dispersion: Float,
): RenderEffect? {
    if (size.width <= 0f || size.height <= 0f) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val shader = RuntimeShader(BevelShader).apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("cornerRadii", cornerFloats(radiusPx))
        setFloatUniform("bevel", bevelPx)
        setFloatUniform("refractAmount", refractPx)
        setFloatUniform("dispersion", dispersion)
    }
    return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionOnScreen() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

private fun Modifier.sampleBackdrop(
    shape: Shape,
    backdrop: GlassBackdrop,
    effect: (size: Size) -> RenderEffect?,
): Modifier = composed {
    var position by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { position = it.positionOnScreen() }
        .graphicsLayer {
            clip = true
            this.shape = shape
            renderEffect = effect(size)
        }
        .drawBehind {
            val offset = position - backdrop.origin
            translate(-offset.x, -offset.y) {
                drawLayer(backdrop.layer)
            }
        }
}

private fun Modifier.glassBody(shape: Shape, backdrop: GlassBackdrop): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return sampleBackdrop(shape, backdrop) { _ ->
        buildBodyEffect(blurPx = 3f, saturation = 1.4f)
    }
}

private fun Modifier.glassBevel(
    shape: Shape,
    backdrop: GlassBackdrop,
    cornerRadius: Dp,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return composed {
        val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { cornerRadius.toPx() }
        sampleBackdrop(shape, backdrop) { size ->
            buildBevelEffect(
                size = size,
                radiusPx = radiusPx,
                bevelPx = radiusPx * 1.4f,
                refractPx = radiusPx * 0.65f,
                dispersion = 0.3f,
            )
        }
    }
}

private fun Modifier.glassLighting(shape: Shape, cornerRadius: Dp): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return this.border(1.5.dp, FallbackRim, shape)
    }
    return this
        .clip(shape)
        .drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val shader = RuntimeShader(LightingShader).apply {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("cornerRadii", cornerFloats(radiusPx))
                setFloatUniform("bevel", radiusPx * 0.8f)
                setFloatUniform("rimWidth", 1.6.dp.toPx())
            }
            val brush = ShaderBrush(shader)
            onDrawBehind {
                drawRect(brush = brush)
            }
        }
}

@Composable
fun LiquidGlass(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    tint: Color = Color(0x1AFFFFFF),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x40000000),
            )
            .clip(shape)
    ) {
        Box(Modifier.matchParentSize().glassBody(shape, backdrop))
        Box(Modifier.matchParentSize().background(tint))
        Box(Modifier.matchParentSize().glassBevel(shape, backdrop, cornerRadius))
        Box(Modifier.matchParentSize().glassLighting(shape, cornerRadius))
        content()
    }
}

@Composable
fun DraggableLiquidGlassDemo(background: Painter, modifier: Modifier = Modifier) {
    val backdrop = rememberGlassBackdrop()
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier.fillMaxSize()) {
        Image(
            painter = background,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
        )
        LiquidGlass(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .size(280.dp, 180.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                    }
                },
        )
    }
}
