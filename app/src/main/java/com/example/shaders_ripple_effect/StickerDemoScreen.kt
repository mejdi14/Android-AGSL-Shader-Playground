package com.example.shaders_ripple_effect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.HolographicStickerEffect
import com.example.shaderrippleeffect.StickerMotion

/**
 * Holographic card demo: tilt the phone and the foil sweeps across the card as
 * it catches the light, the way a real holo card does.
 */
@Composable
fun StickerDemoScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF14161C)),
        contentAlignment = Alignment.Center
    ) {
        HolographicStickerEffect(
            modifier = Modifier
                .width(300.dp)
                .aspectRatio(580f / 810f),
            motion = StickerMotion.Tilt
        ) {
            Image(
                painter = painterResource(id = R.drawable.charizard_card),
                contentDescription = "Holographic Charizard card",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
            )
        }

        TapHint("Tilt your phone")
    }
}
