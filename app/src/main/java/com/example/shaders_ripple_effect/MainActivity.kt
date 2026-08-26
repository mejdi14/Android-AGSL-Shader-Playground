package com.example.shaders_ripple_effect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.shaders_ripple_effect.ui.theme.ShadersRippleEffectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadersRippleEffectTheme {
                ShaderPlaygroundApp()
            }
        }
    }
}

private const val HOME_ROUTE = "home"

@Composable
fun ShaderPlaygroundApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            Surface(modifier = Modifier.fillMaxSize()) {
                HomeScreen(
                    demos = shaderDemos,
                    onOpenDemo = { navController.navigate(it.route) },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }
        shaderDemos.forEach { demo ->
            composable(demo.route) {
                DemoContainer(onBack = { navController.popBackStack() }) {
                    demo.screen()
                }
            }
        }
    }
}

/** Hosts a full-screen demo and floats a back button over it. */
@Composable
private fun DemoContainer(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        BackChip(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp)
        )
    }
}

@Composable
private fun BackChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "‹ Back", color = Color.White, fontSize = 15.sp)
    }
}
