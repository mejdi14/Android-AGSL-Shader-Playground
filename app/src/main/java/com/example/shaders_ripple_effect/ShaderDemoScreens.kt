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
import com.example.shaderrippleeffect.KaleidoscopeEffect
import com.example.shaderrippleeffect.RippleTransitionEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.example.shaders_ripple_effect.particles.ParticleField
import com.example.shaderrippleeffect.LiquidGlassRingEffect
import com.example.shaderrippleeffect.MotionBlurEffect
import com.example.shaderrippleeffect.RevealShaderEffect
import com.example.shaderrippleeffect.ShaderRippleEffect
import com.example.shaderrippleeffect.VortexEffect
import com.example.shaderrippleeffect.DongxiaoWaveEffect
import com.example.shaderrippleeffect.CrtGlitchEffect
import com.example.shaderrippleeffect.FluidDisplacementEffect
import com.example.shaderrippleeffect.KoiPondEffect
import com.example.shaderrippleeffect.PixelMeltEffect
import com.example.shaderrippleeffect.NoiseDissolveEffect

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

@Composable
fun DongxiaoDemoScreen() {
    DongxiaoWaveEffect {
        Image(
            painter = painterResource(id = R.drawable.light_map),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun CrtGlitchDemoScreen() {
    CrtGlitchEffect {
        Image(
            painter = painterResource(id = R.drawable.palace),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun PixelMeltDemoScreen() {
    PixelMeltEffect {
        Image(
            painter = painterResource(id = R.drawable.hedgehog),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun NoiseDissolveDemoScreen() {
    NoiseDissolveEffect {
        Image(
            painter = painterResource(id = R.drawable.swan),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun LiquidGlassRingDemoScreen() {
    Box(Modifier.fillMaxSize()) {
        LiquidGlassRingEffect(
            modifier = Modifier.fillMaxSize(),
            // Deliberately slow while we tune the look.
            durationMillis = 6000
        ) {
            Image(
                painter = painterResource(id = R.drawable.palace),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        TapHint("Tap to send a ring of glass")
    }
}

@Composable
fun ParticlesDemoScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFEDEDEB))
    ) {
        ParticleField(modifier = Modifier.fillMaxSize())
        TapHint("Drag through the dots · tap to change shape")
    }
}

@Composable
fun RippleTransitionDemoScreen() {
    val images = listOf(
        ImageBitmap.imageResource(R.drawable.desert_dune),
        ImageBitmap.imageResource(R.drawable.greenhouse),
        ImageBitmap.imageResource(R.drawable.japan_street),
        ImageBitmap.imageResource(R.drawable.city_rooftops)
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RippleTransitionEffect(images = images, modifier = Modifier.fillMaxSize())
        TapHint("Tap to ripple between photos")
    }
}

@Composable
fun FluidDisplacementDemoScreen() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FluidDisplacementEffect(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.city_rooftops),
                contentDescription = "Fluid Displacement",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        TapHint("Drag to stir the fluid")
    }
}

@Composable
fun KoiPondDemoScreen() {
    Box(Modifier.fillMaxSize().background(Color(0xFF0A2E38))) {
        KoiPondEffect(modifier = Modifier.fillMaxSize())
        TapHint("Drag to stir fluid · tap to feed the koi")
    }
}
