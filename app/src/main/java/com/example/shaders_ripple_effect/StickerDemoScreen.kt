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
import com.example.shaderrippleeffect.HolographicStickerEffect

/**
 * Holographic sticker demo: drag across the sticker to sweep the foil and tilt
 * it, the way a real foil sticker catches the light.
 */
@Composable
fun StickerDemoScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF14161C)),
        contentAlignment = Alignment.Center
    ) {
        HolographicStickerEffect(modifier = Modifier.size(300.dp)) {
            Image(
                painter = painterResource(id = R.drawable.moon_sticker),
                contentDescription = "Moon and star sticker",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        TapHint("Drag across the sticker")
    }
}
