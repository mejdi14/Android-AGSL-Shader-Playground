package com.example.shaders_ripple_effect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.StickerPeelEffect

/** The surface the sticker is stuck to — what the peel reveals underneath. */
private val SurfaceColor = Color(0xFF35434E)

/**
 * Sticker peel demo: a die-cut sticker with a transparent surround lifting off a
 * surface. The background is its own pass, independent of the peel, so what the
 * peel exposes reads as the surface rather than a hole.
 */
@Composable
fun StickerPeelDemoScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceColor),
        contentAlignment = Alignment.Center
    ) {
        ConcentricBackground(Modifier.fillMaxSize())

        // The effect box is deliberately larger than the sticker so the folded
        // flap and its shadow have room — the render effect clips to these bounds.
        StickerPeelEffect(modifier = Modifier.size(340.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.moon_sticker),
                    contentDescription = "Moon and star sticker",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(240.dp)
                )
            }
        }
    }
}

/** Static concentric contour rings, drawn behind the sticker. */
@Composable
private fun ConcentricBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val maxRadius = size.maxDimension * 0.8f
        var radius = size.minDimension * 0.10f
        var i = 0
        while (radius < maxRadius) {
            drawCircle(
                color = Color.White.copy(alpha = 0.05f + (i % 3) * 0.018f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.6f)
            )
            radius *= 1.13f
            i++
        }
    }
}
