package com.example.shaderrippleeffect

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.internal.GuideHighlightShader
import com.example.shaderrippleeffect.internal.GuideRefractionShader
import com.example.shaderrippleeffect.internal.GuideRefractionWithDispersionShader
import kotlin.math.PI

/**
 * The linked guide's Card recipe with its refraction band widened from 16dp to 32dp
 * for visibility: 1.5x vibrancy, no blur, and the same 32dp refraction amount.
 * Uses its upstream quarter-circle refraction and directional highlight shaders.
 * Geometry, tint, and shadow defaults match [LiquidGlass] for demo comparison.
 * Enable [chromaticAberration] during dragging; the resting lens has no dispersion.
 */
@Composable
fun GuideLiquidGlass(
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    tint: Color = Color(0x1AFFFFFF),
    chromaticAberration: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(cornerRadius)
    // Compile each variant once, never in the position-dependent graphicsLayer block.
    val lens = remember { RuntimeShader(GuideRefractionShader) }
    val dispersiveLens = remember { RuntimeShader(GuideRefractionWithDispersionShader) }
    val vibrancy = remember {
        val matrix = ColorMatrix().apply { setSaturation(1.5f) }
        AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    }
    var position by remember { mutableStateOf(Offset.Zero) }

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
        Box(
            Modifier.matchParentSize()
                .onGloballyPositioned { position = it.positionOnScreen() }
                .graphicsLayer {
                    clip = true
                    this.shape = shape
                    if (size.width > 0f && size.height > 0f) {
                        val shader = if (chromaticAberration) dispersiveLens else lens
                        val radius = cornerRadius.toPx().coerceIn(0f, size.minDimension / 2f)
                        shader.setFloatUniform("size", size.width, size.height)
                        shader.setFloatUniform("offset", 0f, 0f)
                        shader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
                        shader.setFloatUniform("refractionHeight", 32.dp.toPx())
                        // Lens.kt negates the amount so samples bend toward the interior.
                        shader.setFloatUniform("refractionAmount", -32.dp.toPx())
                        shader.setFloatUniform("depthEffect", 0f)
                        if (chromaticAberration) shader.setFloatUniform("chromaticAberration", 1f)
                        val refraction = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                        renderEffect = AndroidRenderEffect.createChainEffect(refraction, vibrancy)
                            .asComposeRenderEffect()
                    }
                }
                .drawBehind {
                    val offset = position - backdrop.origin
                    translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                }
        )
        Box(Modifier.matchParentSize().background(tint))
        Box(
            Modifier.matchParentSize().drawWithCache {
                val radius = cornerRadius.toPx().coerceIn(0f, size.minDimension / 2f)
                val highlight = RuntimeShader(GuideHighlightShader).apply {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("cornerRadii", radius, radius, radius, radius)
                    setColorUniform("color", android.graphics.Color.WHITE)
                    setFloatUniform("angle", (PI / 4).toFloat())
                    setFloatUniform("falloff", 1f)
                }
                val brush = ShaderBrush(highlight)
                // Upstream uses a double-width stroke clipped to the shape's inner edge.
                val stroke = Stroke(width = 0.5.dp.toPx() * 2f)
                onDrawBehind {
                    drawRoundRect(
                        brush = brush,
                        cornerRadius = CornerRadius(radius),
                        alpha = 0.5f,
                        style = stroke,
                        blendMode = BlendMode.Plus,
                    )
                }
            }
        )
        content()
    }
}
