package com.example.shaders_ripple_effect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.ComplexWaveEffect
import com.example.shaderrippleeffect.RippleContentTransition
import com.example.shaderrippleeffect.ShaderRippleEffect
import com.example.shaders_ripple_effect.ui.theme.ShadersRippleEffectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadersRippleEffectTheme {
                Box(Modifier.fillMaxSize()){
                    /*ShaderRippleEffect() {
                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .align(Alignment.Center)
                                .background(
                                    color = Color.Red,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.hedgehog),
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                contentDescription = "Ripple Effect",
                                contentScale = ContentScale.Crop
                            )
                        }
                    }*/

                    ShaderRippleEffect (){
                    RippleContentTransition(
                            firstContent = {
                                Image(
                                    painter = painterResource(id = R.drawable.hedgehog),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentDescription = "Ripple Effect",
                                    contentScale = ContentScale.Crop
                                )
                            },
                            secondContent = {
                                Image(
                                    painter = painterResource(id = R.drawable.flowers),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentDescription = "Ripple Effect",
                                    contentScale = ContentScale.Crop
                                )
                            }

                        )
                    }
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