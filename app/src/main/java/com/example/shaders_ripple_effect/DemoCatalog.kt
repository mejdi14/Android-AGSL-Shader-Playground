package com.example.shaders_ripple_effect

import androidx.compose.runtime.Composable

/**
 * One entry per shader demo. Drives both the home menu list and the navigation
 * graph, so adding a new effect is a single addition here plus its screen.
 */
data class ShaderDemo(
    val route: String,
    val title: String,
    val subtitle: String,
    val screen: @Composable () -> Unit
)

val shaderDemos: List<ShaderDemo> = listOf(
    ShaderDemo(
        route = "ripple",
        title = "Ripple Effect",
        subtitle = "Tap-reactive ripple radiating from the touch point",
        screen = { RippleDemoScreen() }
    ),
    ShaderDemo(
        route = "complex_wave",
        title = "Complex Wave",
        subtitle = "Continuous flowing wave distortion",
        screen = { ComplexWaveDemoScreen() }
    ),
    ShaderDemo(
        route = "reveal",
        title = "Reveal Transition",
        subtitle = "Ripple-masked transition between two screens",
        screen = { RevealDemoScreen() }
    ),
    ShaderDemo(
        route = "motion_blur",
        title = "Motion Blur",
        subtitle = "Directional blur that trails your drag",
        screen = { MotionBlurDemoScreen() }
    ),
    ShaderDemo(
        route = "kaleidoscope",
        title = "Kaleidoscope",
        subtitle = "Mirrored mandala that slowly rotates",
        screen = { KaleidoscopeDemoScreen() }
    ),
    ShaderDemo(
        route = "liquid_chrome",
        title = "Liquid Chrome",
        subtitle = "Flowing molten-metal domain warp",
        screen = { LiquidChromeDemoScreen() }
    ),
    ShaderDemo(
        route = "vortex",
        title = "Vortex Spiral",
        subtitle = "Twisting spiral — tap to move the center",
        screen = { VortexDemoScreen() }
    ),
    ShaderDemo(
        route = "heat_shimmer",
        title = "Heat Shimmer",
        subtitle = "Wavy thermal turbulence rising upward",
        screen = { HeatShimmerDemoScreen() }
    ),
    ShaderDemo(
        route = "neon_edge",
        title = "Neon Edge",
        subtitle = "Glowing, hue-cycling edge outlines",
        screen = { NeonEdgeDemoScreen() }
    ),
)
