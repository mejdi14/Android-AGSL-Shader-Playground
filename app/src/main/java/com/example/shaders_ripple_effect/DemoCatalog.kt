package com.example.shaders_ripple_effect

import androidx.compose.runtime.Composable
import com.example.shaders_ripple_effect.gallery.CurvedGalleryScreen
import com.example.shaders_ripple_effect.paperfold.PaperFoldShowcaseScreen

/**
 * One entry per shader demo. Drives both the home gallery and the navigation
 * graph, so adding a new effect is a single addition here plus its screen.
 *
 * [category] is the family the effect belongs to; the gallery shows it beside
 * the title as "Category · Title".
 */
data class ShaderDemo(
    val route: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val screen: @Composable () -> Unit
)

val shaderDemos: List<ShaderDemo> = listOf(
    ShaderDemo(
        route = "fluid_displacement",
        category = "Distortion",
        title = "Fluid Displacement",
        subtitle = "Drag to stir ink-like fluid eddies that warp the photo",
        screen = { FluidDisplacementDemoScreen() }
    ),
    ShaderDemo(
        route = "ripple_transition",
        category = "Transition",
        title = "Ripple Transition",
        subtitle = "A noisy ripple wavefront crossfades between photos on tap",
        screen = { RippleTransitionDemoScreen() }
    ),
    ShaderDemo(
        route = "particles",
        category = "Interactive",
        title = "Particles",
        subtitle = "Dots that part around your finger — tap to reshape",
        screen = { ParticlesDemoScreen() }
    ),
    ShaderDemo(
        route = "liquid_glass_ring",
        category = "Refraction",
        title = "Liquid Glass Ring",
        subtitle = "Tap to send a ring of liquid glass across the photo",
        screen = { LiquidGlassRingDemoScreen() }
    ),
    ShaderDemo(
        route = "metaballs",
        category = "Generative",
        title = "Metaballs",
        subtitle = "Gooey coloured balls drifting and merging",
        screen = { MetaballsDemoScreen() }
    ),
    ShaderDemo(
        route = "holographic_sticker",
        category = "Material",
        title = "Holographic Sticker",
        subtitle = "Pokémon-card foil that sweeps and tilts as you drag",
        screen = { StickerDemoScreen() }
    ),
    ShaderDemo(
        route = "paper_fold_showcase",
        category = "Showcase",
        title = "Paper Fold",
        subtitle = "Diagonal cloth fold, halftone morph, and seven chapters",
        screen = { PaperFoldShowcaseScreen() }
    ),
    ShaderDemo(
        route = "curved_gallery",
        category = "Spatial",
        title = "Curved 3D Gallery",
        subtitle = "Subdivided-mesh image ribbon — drag to scroll",
        screen = { CurvedGalleryScreen() }
    ),
    ShaderDemo(
        route = "ripple",
        category = "Distortion",
        title = "Ripple Effect",
        subtitle = "Tap-reactive ripple radiating from the touch point",
        screen = { RippleDemoScreen() }
    ),
    ShaderDemo(
        route = "complex_wave",
        category = "Distortion",
        title = "Complex Wave",
        subtitle = "Continuous flowing wave distortion",
        screen = { ComplexWaveDemoScreen() }
    ),
    ShaderDemo(
        route = "reveal",
        category = "Transition",
        title = "Reveal Transition",
        subtitle = "Ripple-masked transition between two screens",
        screen = { RevealDemoScreen() }
    ),
    ShaderDemo(
        route = "motion_blur",
        category = "Motion",
        title = "Motion Blur",
        subtitle = "Directional blur that trails your drag",
        screen = { MotionBlurDemoScreen() }
    ),
    ShaderDemo(
        route = "kaleidoscope",
        category = "Pattern",
        title = "Kaleidoscope",
        subtitle = "Mirrored mandala that slowly rotates",
        screen = { KaleidoscopeDemoScreen() }
    ),
    ShaderDemo(
        route = "vortex",
        category = "Distortion",
        title = "Vortex Spiral",
        subtitle = "Twisting spiral — tap to move the center",
        screen = { VortexDemoScreen() }
    ),
    ShaderDemo(
        route = "dongxiao_wave",
        category = "Interactive",
        title = "Interactive Wave",
        subtitle = "Dongxiao inspired ripple and hover wave effect",
        screen = { DongxiaoDemoScreen() }
    ),
    ShaderDemo(
        route = "crt_glitch",
        category = "Retro",
        title = "Retro CRT Glitch",
        subtitle = "Curvature, chromatic aberration, and noise (Drag/Tap)",
        screen = { CrtGlitchDemoScreen() }
    ),
    ShaderDemo(
        route = "pixel_melt",
        category = "Glitch",
        title = "Pixel Sorting Melt",
        subtitle = "Downward glitchy melting effect (Drag)",
        screen = { PixelMeltDemoScreen() }
    ),
    ShaderDemo(
        route = "noise_dissolve",
        category = "Transition",
        title = "Organic Noise Dissolve",
        subtitle = "Burn away transition using fractal noise (Tap)",
        screen = { NoiseDissolveDemoScreen() }
    ),
    ShaderDemo(
        route = "apple_liquid_glass",
        category = "Refraction",
        title = "Apple Liquid Glass",
        subtitle = "Draggable glass rectangle with edge refraction",
        screen = { AppleLiquidGlassDemoScreen() }
    ),
)
