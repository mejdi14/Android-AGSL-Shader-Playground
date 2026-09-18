package com.example.shaders_ripple_effect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.shaderrippleeffect.MetaballsEffect

/**
 * Metaballs demo: gooey coloured balls drifting and merging, filling the screen.
 */
@Composable
fun MetaballsDemoScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        MetaballsEffect(
            modifier = Modifier.fillMaxSize(),
            count = 10f,
            size = 0.83f,
            speed = 1f
        )

        TapHint("Gooey balls merging and drifting")
    }
}
