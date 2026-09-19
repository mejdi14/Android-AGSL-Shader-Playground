package com.example.shaders_ripple_effect

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shaderrippleeffect.ComplexWaveEffect
import com.example.shaderrippleeffect.KaleidoscopeEffect
import com.example.shaderrippleeffect.MotionBlurEffect
import com.example.shaderrippleeffect.RevealShaderEffect
import com.example.shaderrippleeffect.ShaderRippleEffect
import com.example.shaderrippleeffect.VortexEffect
import com.example.shaderrippleeffect.DongxiaoWaveEffect
import com.example.shaderrippleeffect.CrtGlitchEffect
import com.example.shaderrippleeffect.PixelMeltEffect
import com.example.shaderrippleeffect.NoiseDissolveEffect
import com.example.shaderrippleeffect.AppleLiquidGlassEffect
import com.example.shaderrippleeffect.LiquidGlassDefaults

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
fun AppleLiquidGlassDemoScreen() {
    var regularMaterial by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AppleLiquidGlassEffect(
            style = if (regularMaterial) {
                LiquidGlassDefaults.Regular
            } else {
                LiquidGlassDefaults.Clear
            },
            glassContent = {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (regularMaterial) "REGULAR MATERIAL" else "CLEAR MATERIAL",
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        text = "LIQUID GLASS",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.palace),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x24255BFF),
                                0.5f to Color.Transparent,
                                1f to Color(0xB8121020)
                            )
                        )
                )
                Canvas(Modifier.fillMaxSize()) {
                    val spacing = size.width / 9f
                    repeat(10) { index ->
                        val x = index * spacing
                        drawLine(
                            color = Color.White.copy(alpha = if (index % 2 == 0) 0.24f else 0.1f),
                            start = Offset(x, size.height * 0.18f),
                            end = Offset(x, size.height * 0.86f),
                            strokeWidth = if (index % 2 == 0) 2f else 1f
                        )
                    }
                    drawCircle(
                        color = Color(0xAAFF5A7A),
                        radius = size.minDimension * 0.13f,
                        center = Offset(size.width * 0.22f, size.height * 0.34f)
                    )
                    drawCircle(
                        color = Color(0xAA39D9FF),
                        radius = size.minDimension * 0.16f,
                        center = Offset(size.width * 0.82f, size.height * 0.67f)
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SHARP TYPE  /  COLOR  /  LIGHT",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "BEND\nTHE WORLD",
                        color = Color.White,
                        fontSize = 48.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1.2).sp
                    )
                }
            }
        }

        Text(
            text = if (regularMaterial) "REGULAR · TAP FOR CLEAR" else "CLEAR · TAP FOR REGULAR",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.44f))
                .clickable { regularMaterial = !regularMaterial }
                .padding(horizontal = 15.dp, vertical = 10.dp)
        )

        TapHint("Drag the glass · press to energize the rim")
    }
}
