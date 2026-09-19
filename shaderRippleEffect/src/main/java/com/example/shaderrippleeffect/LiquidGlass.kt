package com.example.shaderrippleeffect

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.internal.INPUT_SHADER
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlin.math.ceil
import kotlin.math.min

/** Geometry evaluated by the AGSL signed-distance field. */
enum class LiquidGlassShape {
    RoundedRectangle,
    Capsule,
    Circle
}

/** Optical and material parameters for a liquid-glass surface. */
@Immutable
data class LiquidGlassStyle(
    val blurRadius: Dp,
    val saturation: Float,
    val contrast: Float,
    val tint: Color,
    val tintOpacity: Float,
    val brightness: Float,
    val cornerRadius: Dp,
    val bevelRadius: Dp,
    val refraction: Dp,
    val dispersion: Float,
    val rimIntensity: Float,
    val shadowOpacity: Float
)

/** Calibrated starting points for Apple's regular and clear material families. */
object LiquidGlassDefaults {
    val Regular = LiquidGlassStyle(
        blurRadius = 17.dp,
        saturation = 1.36f,
        contrast = 1.06f,
        tint = Color(0xFFEAF3FF),
        tintOpacity = 0.17f,
        brightness = 0.025f,
        cornerRadius = 30.dp,
        bevelRadius = 25.dp,
        refraction = 29.dp,
        dispersion = 0.74f,
        rimIntensity = 1.08f,
        shadowOpacity = 0.24f
    )

    val Clear = LiquidGlassStyle(
        blurRadius = 7.dp,
        saturation = 1.28f,
        contrast = 1.055f,
        tint = Color(0xFFF4F8FF),
        tintOpacity = 0.105f,
        brightness = 0.018f,
        cornerRadius = 30.dp,
        bevelRadius = 27.dp,
        refraction = 32.dp,
        dispersion = 0.68f,
        rimIntensity = 1.16f,
        shadowOpacity = 0.22f
    )
}

/** Dynamic values supplied by an interactive glass control. Coordinates are local pixels. */
@Immutable
data class LiquidGlassInteraction(
    val pressProgress: Float = 0f,
    val touchPosition: Offset = Offset.Unspecified,
    val motion: Offset = Offset.Zero
)

/**
 * Draws a two-pass liquid-glass material behind this element's normal content.
 *
 * The provider layer is sampled twice: a platform-blurred pass supplies the body, while an
 * unblurred AGSL pass contributes only the curved bevel. Foreground content is drawn last and is
 * therefore never blurred or refracted.
 */
fun Modifier.liquidGlass(
    backdrop: BackdropProviderState,
    style: LiquidGlassStyle = LiquidGlassDefaults.Clear,
    shape: LiquidGlassShape = LiquidGlassShape.RoundedRectangle,
    interaction: LiquidGlassInteraction = LiquidGlassInteraction()
): Modifier = composed {
    val density = LocalDensity.current
    val densityScale = density.density
    val bodyShader = remember { RuntimeShader(LIQUID_GLASS_BODY_SHADER) }
    val bevelShader = remember { RuntimeShader(LIQUID_GLASS_BEVEL_SHADER) }
    val bodyLayer = rememberGraphicsLayer()
    val bevelLayer = rememberGraphicsLayer()
    val shadowLayer = rememberGraphicsLayer()

    val blurPx = with(density) { style.blurRadius.toPx() }
    val bevelPx = with(density) { style.bevelRadius.toPx() }
    val refractionPx = with(density) { style.refraction.toPx() }
    val configuredCornerPx = with(density) { style.cornerRadius.toPx() }
    val shadowBlurPx = 14f * densityScale
    val shadowOffsetPx = 5f * densityScale
    val paddingPx = ceil(
        2f * blurPx + refractionPx + shadowBlurPx + shadowOffsetPx
    )

    val bodyRenderEffect = remember(
        bodyShader,
        blurPx,
        style.saturation,
        style.contrast
    ) {
        val blur = AndroidRenderEffect.createBlurEffect(
            blurPx.coerceAtLeast(0.01f),
            blurPx.coerceAtLeast(0.01f),
            Shader.TileMode.MIRROR
        )
        val colorMatrix = createGlassColorMatrix(style.saturation, style.contrast)
        val color = AndroidRenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(colorMatrix),
            blur
        )
        val mask = AndroidRenderEffect.createRuntimeShaderEffect(bodyShader, INPUT_SHADER)
        AndroidRenderEffect.createChainEffect(mask, color).asComposeRenderEffect()
    }
    val bevelRenderEffect = remember(bevelShader) { bevelShader.asContentRenderEffect() }
    val shadowRenderEffect = remember(shadowBlurPx) {
        AndroidRenderEffect.createBlurEffect(
            shadowBlurPx,
            shadowBlurPx,
            Shader.TileMode.DECAL
        ).asComposeRenderEffect()
    }

    var glassPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    this
        .onGloballyPositioned { coordinates ->
            glassPositionInRoot = coordinates.positionInRoot()
        }
        .drawWithContent {
            if (!backdrop.isReady || size.width <= 0f || size.height <= 0f) {
                drawContent()
                return@drawWithContent
            }

            val glassWidth = size.width
            val glassHeight = size.height
            val minDimension = min(glassWidth, glassHeight)
            val cornerPx = when (shape) {
                LiquidGlassShape.Circle,
                LiquidGlassShape.Capsule -> minDimension * 0.5f
                LiquidGlassShape.RoundedRectangle -> configuredCornerPx.coerceAtMost(minDimension * 0.5f)
            }
            val shapeId = when (shape) {
                LiquidGlassShape.RoundedRectangle -> 0f
                LiquidGlassShape.Capsule -> 1f
                LiquidGlassShape.Circle -> 2f
            }
            val pad = paddingPx.coerceAtLeast(1f)
            val expandedSize = IntSize(
                width = ceil(glassWidth + 2f * pad).toInt().coerceAtLeast(1),
                height = ceil(glassHeight + 2f * pad).toInt().coerceAtLeast(1)
            )
            val providerOffset = backdrop.positionInRoot - glassPositionInRoot + Offset(pad, pad)
            val touch = if (interaction.touchPosition.isSpecified) {
                interaction.touchPosition
            } else {
                Offset(glassWidth * 0.5f, glassHeight * 0.5f)
            }

            bodyShader.setFloatUniform("uSize", glassWidth, glassHeight)
            bodyShader.setFloatUniform("uPadding", pad)
            bodyShader.setFloatUniform("uCornerRadius", cornerPx)
            bodyShader.setFloatUniform("uShape", shapeId)
            bodyShader.setFloatUniform(
                "uTint",
                style.tint.red,
                style.tint.green,
                style.tint.blue
            )
            bodyShader.setFloatUniform("uTintOpacity", style.tintOpacity)
            bodyShader.setFloatUniform("uBrightness", style.brightness)

            bevelShader.setFloatUniform("uSize", glassWidth, glassHeight)
            bevelShader.setFloatUniform("uPadding", pad)
            bevelShader.setFloatUniform("uCornerRadius", cornerPx)
            bevelShader.setFloatUniform("uShape", shapeId)
            bevelShader.setFloatUniform("uBevelRadius", bevelPx.coerceAtLeast(1f))
            bevelShader.setFloatUniform("uRefraction", refractionPx)
            bevelShader.setFloatUniform("uDispersion", style.dispersion)
            bevelShader.setFloatUniform("uRimIntensity", style.rimIntensity)
            bevelShader.setFloatUniform("uPress", interaction.pressProgress.coerceIn(0f, 1f))
            bevelShader.setFloatUniform("uTouch", touch.x, touch.y)
            bevelShader.setFloatUniform("uMotion", interaction.motion.x, interaction.motion.y)

            shadowLayer.record(size = expandedSize) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = style.shadowOpacity),
                    topLeft = Offset(pad, pad + shadowOffsetPx),
                    size = Size(glassWidth, glassHeight),
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
            }
            shadowLayer.renderEffect = shadowRenderEffect

            bodyLayer.record(size = expandedSize) {
                withTransform({ translate(providerOffset.x, providerOffset.y) }) {
                    drawLayer(backdrop.graphicsLayer)
                }
            }
            bodyLayer.renderEffect = bodyRenderEffect

            bevelLayer.record(size = expandedSize) {
                withTransform({ translate(providerOffset.x, providerOffset.y) }) {
                    drawLayer(backdrop.graphicsLayer)
                }
            }
            bevelLayer.renderEffect = bevelRenderEffect

            // Tight contact shadow; the opaque body covers its interior, leaving only the lower lip.
            drawRoundRect(
                color = Color.Black.copy(alpha = style.shadowOpacity * 0.24f),
                topLeft = Offset(0f, 1.5f * densityScale),
                size = size,
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )
            withTransform({ translate(-pad, -pad) }) {
                drawLayer(shadowLayer)
                drawLayer(bodyLayer)
                drawLayer(bevelLayer)
            }

            // A physical silhouette must remain legible even when the sampled backdrop is nearly
            // flat. The directional stroke behaves like a polished top-left highlight and a
            // restrained lower-right occlusion, rather than a uniform decorative border.
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = (0.52f * style.rimIntensity).coerceIn(0f, 1f)),
                        Color.White.copy(alpha = (0.10f * style.rimIntensity).coerceIn(0f, 1f)),
                        Color.Black.copy(alpha = (0.16f * style.rimIntensity).coerceIn(0f, 1f))
                    ),
                    start = Offset.Zero,
                    end = Offset(glassWidth, glassHeight)
                ),
                size = size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 1.15f * densityScale)
            )
            drawContent()
        }
}

private fun createGlassColorMatrix(saturation: Float, contrast: Float): ColorMatrix {
    val saturationMatrix = ColorMatrix().apply { setSaturation(saturation) }
    val translate = 128f * (1f - contrast)
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    saturationMatrix.postConcat(contrastMatrix)
    return saturationMatrix
}

private const val LIQUID_GLASS_BODY_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uPadding;
    uniform float uCornerRadius;
    uniform float uShape;
    uniform float3 uTint;
    uniform float uTintOpacity;
    uniform float uBrightness;

    float roundedRectSdf(float2 p, float2 halfSize, float radius) {
        float2 q = abs(p) - halfSize + float2(radius, radius);
        return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - radius;
    }

    float glassSdf(float2 p) {
        float2 halfSize = uSize * 0.5;
        if (uShape > 1.5) {
            return length(p) - min(halfSize.x, halfSize.y);
        }
        float radius = uShape > 0.5
            ? min(halfSize.x, halfSize.y)
            : min(uCornerRadius, min(halfSize.x, halfSize.y));
        return roundedRectSdf(p, halfSize, radius);
    }

    float luminance(float3 color) {
        return dot(color, float3(0.2126, 0.7152, 0.0722));
    }

    float hash(float2 p) {
        return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
    }

    half4 main(float2 fragCoord) {
        float2 localCoord = fragCoord - float2(uPadding, uPadding);
        float2 p = localCoord - uSize * 0.5;
        float sdf = glassSdf(p);
        float coverage = 1.0 - smoothstep(-0.75, 0.75, sdf);
        if (coverage <= 0.0) {
            return half4(0.0);
        }

        float2 probe = max(uSize * 0.19, float2(8.0, 8.0));
        float3 center = float3(inputShader.eval(fragCoord).rgb);
        float localLuminance = (
            luminance(center)
            + luminance(float3(inputShader.eval(fragCoord + float2(probe.x, 0.0)).rgb))
            + luminance(float3(inputShader.eval(fragCoord - float2(probe.x, 0.0)).rgb))
            + luminance(float3(inputShader.eval(fragCoord + float2(0.0, probe.y)).rgb))
            + luminance(float3(inputShader.eval(fragCoord - float2(0.0, probe.y)).rgb))
        ) * 0.2;

        // Dark content receives a little lift; bright content is gently restrained.
        float adaptiveBrightness = mix(1.075, 0.955, smoothstep(0.12, 0.88, localLuminance));
        float adaptiveTint = uTintOpacity * mix(1.28, 0.72, smoothstep(0.18, 0.82, localLuminance));
        float3 color = center * (adaptiveBrightness + uBrightness);
        color = mix(color, uTint, adaptiveTint);

        float inside = max(-sdf, 0.0);
        float innerShadow = 1.0 - smoothstep(0.0, 11.0, inside);
        color *= 1.0 - innerShadow * mix(0.025, 0.075, localLuminance);

        // Sub-LSB dither prevents broad frosted areas from banding without looking textured.
        float grain = (hash(fragCoord) - 0.5) / 255.0;
        color += float3(grain, grain, grain) * 0.38;
        return half4(half3(color * coverage), half(coverage));
    }
"""

private const val LIQUID_GLASS_BEVEL_SHADER = """
    uniform shader inputShader;
    uniform float2 uSize;
    uniform float uPadding;
    uniform float uCornerRadius;
    uniform float uShape;
    uniform float uBevelRadius;
    uniform float uRefraction;
    uniform float uDispersion;
    uniform float uRimIntensity;
    uniform float uPress;
    uniform float2 uTouch;
    uniform float2 uMotion;

    float roundedRectSdf(float2 p, float2 halfSize, float radius) {
        float2 q = abs(p) - halfSize + float2(radius, radius);
        return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - radius;
    }

    float glassSdf(float2 p) {
        float2 halfSize = uSize * 0.5;
        if (uShape > 1.5) {
            return length(p) - min(halfSize.x, halfSize.y);
        }
        float radius = uShape > 0.5
            ? min(halfSize.x, halfSize.y)
            : min(uCornerRadius, min(halfSize.x, halfSize.y));
        return roundedRectSdf(p, halfSize, radius);
    }

    float bevelHeight(float distanceInside) {
        float d = clamp(distanceInside, 0.0, uBevelRadius);
        if (d >= uBevelRadius) {
            return uBevelRadius;
        }
        return sqrt(max(d * (2.0 * uBevelRadius - d), 0.0));
    }

    half4 main(float2 fragCoord) {
        float2 localCoord = fragCoord - float2(uPadding, uPadding);
        float2 p = localCoord - uSize * 0.5;
        float sdf = glassSdf(p);
        float coverage = 1.0 - smoothstep(-0.75, 0.75, sdf);
        if (coverage <= 0.0) {
            return half4(0.0);
        }

        float inside = max(-sdf, 0.0);
        float bevelT = clamp(inside / max(uBevelRadius, 1.0), 0.0, 1.0);
        float bevelWeight = 1.0 - smoothstep(0.50, 1.0, bevelT);
        if (bevelWeight <= 0.001) {
            return half4(0.0);
        }

        float e = 1.25;
        float sdR = glassSdf(p + float2(e, 0.0));
        float sdL = glassSdf(p - float2(e, 0.0));
        float sdD = glassSdf(p + float2(0.0, e));
        float sdU = glassSdf(p - float2(0.0, e));
        float2 contourGradient = float2(sdR - sdL, sdD - sdU) / (2.0 * e);
        float2 contourNormal = contourGradient / max(length(contourGradient), 0.0001);

        float hC = bevelHeight(inside);
        float hR = bevelHeight(max(-sdR, 0.0));
        float hL = bevelHeight(max(-sdL, 0.0));
        float hD = bevelHeight(max(-sdD, 0.0));
        float hU = bevelHeight(max(-sdU, 0.0));
        float2 heightGradient = float2(hR - hL, hD - hU) / (2.0 * e);
        float3 normal = normalize(float3(-heightGradient * 0.88, 1.0));

        // A strong perimeter lens: the bend rises immediately inside the silhouette, peaks near
        // the outer wall, then settles continuously into the flat centre. Keeping this driven by
        // the SDF normal makes rounded rectangles and capsules refract consistently.
        float ior = 1.5168;
        float refractivePower = 1.0 - 1.0 / ior;
        float thickness = hC * 2.0;
        float thicknessWeight = thickness / max(uBevelRadius * 2.0, 1.0);
        float boundaryGate = smoothstep(0.0, 0.075, bevelT);
        float lensProfile = boundaryGate * pow(1.0 - bevelT, 1.45);
        float2 offset = -contourNormal * uRefraction * lensProfile;
        offset += heightGradient * refractivePower * uRefraction
            * (0.22 + thicknessWeight * 0.12) * bevelWeight;
        float maxOffset = uRefraction * 1.32;
        float offsetLength = length(offset);
        if (offsetLength > maxOffset) {
            offset *= maxOffset / offsetLength;
        }

        float2 touchP = uTouch - uSize * 0.5;
        float2 touchDelta = p - touchP;
        float touchRadius = max(min(uSize.x, uSize.y) * 0.34, 1.0);
        float touchGlow = exp(-dot(touchDelta, touchDelta) / (2.0 * touchRadius * touchRadius))
            * uPress;
        float2 touchDirection = touchDelta / max(length(touchDelta), 1.0);
        offset += touchDirection * touchGlow * uRefraction * 0.14;
        offset -= uMotion * (0.045 * bevelWeight);

        // BK7-like RGB indices with a controlled UI-scale exaggeration.
        float redScale = 1.0 + ((1.5143 - ior) / (ior - 1.0)) * uDispersion * 3.0;
        float blueScale = 1.0 + ((1.5224 - ior) / (ior - 1.0)) * uDispersion * 3.0;
        float3 refracted = float3(
            inputShader.eval(fragCoord + offset * redScale).r,
            inputShader.eval(fragCoord + offset).g,
            inputShader.eval(fragCoord + offset * blueScale).b
        );

        float cosTheta = clamp(normal.z, 0.0, 1.0);
        float f0 = pow((ior - 1.0) / (ior + 1.0), 2.0);
        float fresnel = f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0);

        float verticalEdge = abs(contourNormal.y);
        float sideEdge = abs(contourNormal.x);
        float cornerLight = 2.0 * verticalEdge * sideEdge;
        float outerLine = (1.0 - smoothstep(0.0, 1.8, inside)) * coverage;
        float innerLine = smoothstep(2.0, 4.5, inside)
            * (1.0 - smoothstep(4.5, 8.0, inside));
        float2 rimLightDirection = normalize(float2(-0.52, -0.86));
        float2 rimShadowDirection = -rimLightDirection;
        float lightFacing = max(dot(contourNormal, rimLightDirection), 0.0);
        float shadowFacing = max(dot(contourNormal, rimShadowDirection), 0.0);
        float directionalRim = (outerLine * (0.16 + lightFacing * 0.38 + cornerLight * 0.08)
            + innerLine * lightFacing * 0.10) * uRimIntensity;
        float edgeOcclusion = (outerLine * shadowFacing * 0.16
            + innerLine * (0.035 + sideEdge * 0.045)) * uRimIntensity;

        float3 viewDirection = float3(0.0, 0.0, 1.0);
        float3 lightDirection = normalize(float3(-0.34, -0.68, 0.73));
        float3 halfDirection = normalize(viewDirection + lightDirection);
        float specular = pow(max(dot(normal, halfDirection), 0.0), 54.0)
            * 0.24 * uRimIntensity * bevelWeight;
        float reflection = fresnel * (0.11 + 0.07 * verticalEdge) * uRimIntensity;
        float localGlow = touchGlow * (0.035 + 0.045 * bevelWeight);

        float3 color = refracted * (1.0 - edgeOcclusion);
        color += float3(directionalRim + specular + reflection + localGlow);
        float alpha = coverage * bevelWeight;
        return half4(half3(color * alpha), half(alpha));
    }
"""
