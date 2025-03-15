package com.example.shaders_ripple_effect

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shaderrippleeffect.ComplexWaveEffect
import com.example.shaderrippleeffect.MotionBlurEffect
import com.example.shaderrippleeffect.RevealShaderEffect
import com.example.shaderrippleeffect.ShaderRippleEffect
import com.example.shaders_ripple_effect.ui.theme.ShadersRippleEffectTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadersRippleEffectTheme {
                MotionBlurEffect(){
                    Column {
                        Text("Hello there my friend", fontSize = 30.sp, color = Color.White)
                        Text("Hello there my friend", fontSize = 30.sp, color = Color.White)
                        Text("Hello there my friend", fontSize = 30.sp, color = Color.White)
                        Text("Hello there my friend", fontSize = 30.sp, color = Color.White)
                        Text("Hello there my friend", fontSize = 30.sp, color = Color.White)
                        Text("Hello there my friend", fontSize = 30.sp, color = Color.White)
                        Text("Hello there my friend", fontSize = 30.sp, color = Color.White)
                    }
                }
                val pagerState = rememberPagerState(pageCount = { 3 })
               // PagerDemo(pagerState)
            }


        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerDemo(pagerState: PagerState) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> {
                RevealShaderEffect(
                    firstContent = {
                        MapScreen(isDarkTheme = true)
                    },
                    secondContent = {
                        MapScreen(isDarkTheme = false)
                    }
                )
            }

            1 -> {
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
                    ShaderRippleEffect() {
                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    translationY = -300f
                                }
                                .background(
                                    color = Color.Red,
                                    shape = RoundedCornerShape(8.dp)
                                ),
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
                }
            }

            2 -> {
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
                    ComplexWaveEffect(
                        content = {
                            Box(
                                Modifier
                                    .size(200.dp)
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        translationY = -300f
                                    }

                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.palace),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentDescription = "Ripple Effect",
                                    contentScale = ContentScale.Crop
                                )
                            }

                        }
                    )

                }
            }
        }
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShadersRippleEffectTheme {
        Greeting("Android")
    }
}