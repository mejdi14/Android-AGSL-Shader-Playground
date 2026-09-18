package com.example.shaders_ripple_effect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.StickerPeelEffect

/**
 * Sticker peel demo: a procedural concentric background (its own pass, kept
 * independent of the peel) with a holographic sticker lifting off it.
 */
@Composable
fun StickerPeelDemoScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E13)),
        contentAlignment = Alignment.Center
    ) {
        ConcentricBackground(Modifier.fillMaxSize())

        // The effect box is deliberately larger than the sticker so the lift and
        // its shadow have room — the render effect is clipped to these bounds.
        StickerPeelEffect(modifier = Modifier.size(340.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(230.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.neon_geometric),
                        contentDescription = "Holographic sticker",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                    )
                }
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
                color = Color.White.copy(alpha = 0.055f + (i % 3) * 0.02f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.6f)
            )
            radius *= 1.13f
            i++
        }
    }
}
