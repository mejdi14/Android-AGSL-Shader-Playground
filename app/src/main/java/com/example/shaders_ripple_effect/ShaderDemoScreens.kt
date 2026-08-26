package com.example.shaders_ripple_effect

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shaderrippleeffect.ComplexWaveEffect
import com.example.shaderrippleeffect.HeatShimmerEffect
import com.example.shaderrippleeffect.KaleidoscopeEffect
import com.example.shaderrippleeffect.LiquidChromeEffect
import com.example.shaderrippleeffect.MotionBlurEffect
import com.example.shaderrippleeffect.NeonEdgeEffect
import com.example.shaderrippleeffect.RevealShaderEffect
import com.example.shaderrippleeffect.ShaderRippleEffect
import com.example.shaderrippleeffect.VortexEffect

// ---------------------------------------------------------------------------
// Original effects
// ---------------------------------------------------------------------------

@Composable
fun RevealDemoScreen() {
    RevealShaderEffect(
        firstContent = { MapScreen(isDarkTheme = true) },
        secondContent = { MapScreen(isDarkTheme = false) }
    )
}

@Composable
fun RippleDemoScreen() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.violet),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = RenderEffect
                        .createBlurEffect(66f, 66f, Shader.TileMode.MIRROR)
                        .asComposeRenderEffect()
                },
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )
        ShaderRippleEffect {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.Center)
                    .graphicsLayer { translationY = -300f }
                    .background(color = Color.Red, shape = RoundedCornerShape(8.dp)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.violet),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentDescription = "Ripple Effect",
                    contentScale = ContentScale.Crop
                )
            }
        }
        TapHint("Tap the image to ripple")
    }
}

@Composable
fun ComplexWaveDemoScreen() {
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.palace),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = RenderEffect
                        .createBlurEffect(46f, 46f, Shader.TileMode.MIRROR)
                        .asComposeRenderEffect()
                },
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )
        ComplexWaveEffect {
            Box(
                Modifier
                    .size(200.dp)
                    .align(Alignment.Center)
                    .graphicsLayer { translationY = -300f }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.palace),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentDescription = "Complex Wave Effect",
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun MotionBlurDemoScreen() {
    MotionBlurEffect {
        Column(Modifier.padding(top = 40.dp, bottom = 20.dp)) {
            Text(
                "Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs! Hey There Android Devs!",
                fontSize = 33.sp,
                color = Color.White,
                style = TextStyle(letterSpacing = 2.sp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Effects inspired by SwiftUIShaders
// ---------------------------------------------------------------------------

@Composable
fun KaleidoscopeDemoScreen() {
    KaleidoscopeEffect {
        Image(
            painter = painterResource(id = R.drawable.violet),
            contentDescription = "Kaleidoscope",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun LiquidChromeDemoScreen() {
    LiquidChromeEffect {
        Image(
            painter = painterResource(id = R.drawable.palace),
            contentDescription = "Liquid Chrome",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VortexDemoScreen() {
    Box(Modifier.fillMaxSize()) {
        VortexEffect {
            Image(
                painter = painterResource(id = R.drawable.swan),
                contentDescription = "Vortex",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        TapHint("Tap to move the vortex center")
    }
}

@Composable
fun HeatShimmerDemoScreen() {
    HeatShimmerEffect {
        Image(
            painter = painterResource(id = R.drawable.hedgehog),
            contentDescription = "Heat Shimmer",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun NeonEdgeDemoScreen() {
    NeonEdgeEffect {
        Image(
            painter = painterResource(id = R.drawable.swan),
            contentDescription = "Neon Edge",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun BoxScope.TapHint(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 48.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}
