package com.example.shaders_ripple_effect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.StickerPeelEffect

/** The surface the sticker is stuck to — what the peel reveals underneath. */
private val SurfaceColor = Color(0xFF35434E)

/**
 * Sticker peel demo: a die-cut sticker with a transparent surround folding off a
 * plain surface.
 */
@Composable
fun StickerPeelDemoScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceColor),
        contentAlignment = Alignment.Center
    ) {
        // The effect box is deliberately larger than the sticker so the folded
        // flap has room — the render effect clips to these bounds.
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
