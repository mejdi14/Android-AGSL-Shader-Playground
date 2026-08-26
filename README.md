<h1 align="center">Android AGSL Shader Playground 🎨</h1>

<p align="center">
  A Jetpack Compose playground of <b>AGSL runtime-shader animations</b> —<br/>
  tap ripples, flowing waves, ripple reveal transitions, and drag-driven motion blur —<br/>
  each one a drop-in wrapper around any composable.
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.mejdi14/android-shader-effect">
    <img alt="Maven Central" src="https://img.shields.io/maven-central/v/io.github.mejdi14/android-shader-effect?style=flat&label=Maven%20Central&color=blue">
  </a>
  <img alt="API 33+" src="https://img.shields.io/badge/API-33%2B-brightgreen.svg?style=flat">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-✓-4285F4.svg?style=flat">
  <a href="LICENSE">
    <img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-yellow.svg?style=flat">
  </a>
  <a href="https://github.com/mejdi14/Android-AGSL-Shader-Playground/stargazers">
    <img alt="Stars" src="https://img.shields.io/github/stars/mejdi14/Android-AGSL-Shader-Playground?style=flat&color=yellow">
  </a>
</p>

## ✨ Demo

<div align="center">
  <img src="demo/output.gif" height="430" width="240" alt="Ripple reveal transition" />
  &nbsp;&nbsp;
  <img src="demo/output2.gif" height="430" width="240" alt="Reveal animation" />
  &nbsp;&nbsp;
  <img src="demo/output5.gif" height="430" width="240" alt="Motion blur effect" />
</div>

## 📑 Table of Contents

- [Features](#-features)
- [Requirements](#️-requirements)
- [Installation](#-installation)
- [Usage](#-usage)
  - [Ripple Effect (tap-reactive)](#ripple-effect-tap-reactive)
  - [Complex Wave Effect (continuous)](#complex-wave-effect-continuous)
  - [Reveal Animation (content transition)](#reveal-animation-content-transition)
  - [Motion Blur Effect (drag-driven)](#motion-blur-effect-drag-driven)
  - [Kaleidoscope (continuous)](#kaleidoscope-continuous)
  - [Liquid Chrome (continuous)](#liquid-chrome-continuous)
  - [Vortex Spiral (tap to re-center)](#vortex-spiral-tap-to-re-center)
  - [Heat Shimmer (continuous)](#heat-shimmer-continuous)
  - [Neon Edge (continuous)](#neon-edge-continuous)
- [Advanced Customization](#️-advanced-customization)
- [API Reference](#-api-reference)
- [Contributing](#-contributing)
- [License](#-license)

## 🚀 Features

- ✅ Tap-reactive ripple effects with configurable amplitude, frequency, decay & speed
- ✅ Continuous wave animations with customizable parameters
- ✅ Content reveal transitions driven by a rippling shader mask
- ✅ Drag-driven directional motion blur
- ✅ Kaleidoscope — mirrored, slowly rotating mandala
- ✅ Liquid Chrome — flowing molten-metal domain warp
- ✅ Vortex spiral — twisting distortion you can re-center with a tap
- ✅ Heat shimmer — wavy thermal turbulence
- ✅ Neon Edge — glowing, hue-cycling edge outlines
- ✅ Works with **any** Jetpack Compose content — wrap it and go
- ✅ Every effect accepts your own custom AGSL shader code

## ⚙️ Requirements

| Requirement | Minimum |
|-------------|---------|
| Android SDK | **33+** (Android 13 “Tiramisu”) — AGSL runtime shaders are an Android 13 API |
| Jetpack Compose | 1.4.0+ |
| Language | Kotlin |

## 📦 Installation

Make sure `mavenCentral()` is in your repositories (Gradle’s default in `settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Then add the dependency to your module’s `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.mejdi14:android-shader-effect:0.1.3")
}
```

## 🔥 Usage

### Ripple Effect (tap-reactive)

Wrap any content; a ripple radiates from wherever the user taps.

```kotlin
ShaderRippleEffect(
    amplitude = 15f,           // height of the ripple waves
    frequency = 20f,           // how many waves appear
    decay = 6f,                // how quickly the ripple fades
    speed = 2000f,             // how fast the ripple propagates
    animationDuration = 3.5f   // duration in seconds
) {
    Image(
        painter = painterResource(id = R.drawable.your_image),
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
    )
}
```

### Complex Wave Effect (continuous)

A continuous, flowing wave distortion — great for water, flags, or living backgrounds.

```kotlin
ComplexWaveEffect(
    speed = 0.8f,          // how fast the waves move
    strength = 25f,        // wave amplitude
    frequency = 8f,        // how many waves appear
    timeMultiplier = 1.2f  // how time scales the animation
) {
    Text(
        text = "Wavy Text Effect",
        style = MaterialTheme.typography.displayLarge,
        modifier = Modifier.padding(32.dp)
    )
}
```

### Reveal Animation (content transition)

Tap to transition from one composable to another behind an expanding, wiggling ripple front.

<div align="center">
  <img src="demo/output2.gif" height="430" width="240" alt="Reveal animation" />
</div>

```kotlin
RevealShaderEffect(
    waveParams = WaveEffectParams(
        amplitude = 40f,
        frequency = 20f,
        decay = 5f,
        speed = 1000f,
        duration = 3f
    ),
    revealParams = RevealTransitionParams(
        speed = 1200f,
        frequency = 40f,
        wiggleStrength = 38f,
        edgeWidth = 0f,
        duration = 3f,
        transitionDelay = 200
    ),
    firstContent = { FirstContent() },   // content to hide
    secondContent = { SecondContent() }  // content to reveal
)
```

### Motion Blur Effect (drag-driven)

Directional blur that trails the user’s drag and eases out on release. Inspired by [Daniel Kuntz](https://x.com/dankuntz)’ work.

<div align="center">
  <img src="demo/output5.gif" height="430" width="240" alt="Motion blur effect" />
</div>

```kotlin
MotionBlurEffect(
    modifier = Modifier,
    intensity = 2f,        // strength of the blur
    falloffRadius = 390f   // distance over which the blur decays
) {
    YourContent()
}
```

### Kaleidoscope (continuous)

Mirrors your content into radial wedges that form a slowly rotating mandala.

```kotlin
KaleidoscopeEffect(
    segments = 6f,        // number of mirrored wedges
    rotationSpeed = 0.3f  // radians per second
) {
    YourContent()
}
```

### Liquid Chrome (continuous)

A layered domain warp that makes content flow like molten metal, with a moving sheen.

```kotlin
LiquidChromeEffect(
    strength = 14f,  // displacement in pixels
    scale = 8f,      // spatial frequency of the flow
    speed = 0.6f     // how fast the metal flows
) {
    YourContent()
}
```

### Vortex Spiral (tap to re-center)

A twisting spiral, strongest at the center and fading out by `radius`. Tap to move the center.

```kotlin
VortexEffect(
    strength = 3.5f,  // max twist (radians) at the center
    radius = 700f,    // falloff radius in pixels
    speed = 0.5f      // how fast the spiral churns
) {
    YourContent()
}
```

### Heat Shimmer (continuous)

Wavy thermal turbulence, like heat rising off hot asphalt.

```kotlin
HeatShimmerEffect(
    strength = 8f,    // horizontal displacement in pixels
    frequency = 14f,  // how tight the heat waves are
    speed = 1.2f      // how fast the shimmer rises
) {
    YourContent()
}
```

### Neon Edge (continuous)

Darkens the content and traces its outlines with a glowing, hue-cycling neon line.

```kotlin
NeonEdgeEffect(
    thickness = 1.5f,  // edge sampling offset in pixels
    intensity = 3f,    // how bright the edges glow
    cycleSpeed = 0.1f  // how fast the neon color cycles
) {
    YourContent()
}
```

## 🎛️ Advanced Customization

Every effect accepts your own AGSL shader source, so you can override the built-in look entirely:

```kotlin
ShaderRippleEffect(
    rippleShaderCode = """
        uniform shader inputShader;
        uniform float2 uOrigin;
        uniform float uTime;
        // ...your custom AGSL here...
        half4 main(float2 fragCoord) {
            return inputShader.eval(fragCoord);
        }
    """.trimIndent(),
    // other parameters...
) {
    // Your content
}
```

## 👨‍💻 API Reference

### `ShaderRippleEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| amplitude | Float | 12f | Height of the ripple waves |
| frequency | Float | 15f | How many ripple waves appear |
| decay | Float | 8f | How quickly the ripple fades out |
| speed | Float | 1800f | How fast the ripple propagates |
| animationDuration | Float | 3f | Duration of the ripple animation (seconds) |
| rippleShaderCode | String? | null | Custom AGSL shader code |

### `ComplexWaveEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| speed | Float | 0.5f | How fast the waves move |
| strength | Float | 18f | Amplitude of the waves |
| frequency | Float | 10f | How many waves appear |
| timeMultiplier | Float | 1f | How time affects the animation |
| waveShaderCode | String? | null | Custom AGSL shader code |

### `RevealShaderEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| waveParams | WaveEffectParams | default | Configuration for the expanding wave |
| revealParams | RevealTransitionParams | default | Configuration for the reveal transition |
| modifier | Modifier | Modifier | Compose modifier |
| firstContent | @Composable | required | The initial content to replace |
| secondContent | @Composable | required | The content to reveal |

#### `WaveEffectParams`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| amplitude | Float | 40f | Wave height |
| frequency | Float | 20f | Ripple density |
| decay | Float | 5f | Ripple fade-out speed |
| speed | Float | 1000f | Propagation speed |
| duration | Float | 3f | Animation duration (seconds) |

#### `RevealTransitionParams`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| speed | Float | 1200f | Reveal animation speed |
| frequency | Float | 40f | Edge wave frequency |
| amplitude | Float | 30.5f | Edge wave height |
| edgeWidth | Float | 0f | Transition edge width |
| wiggleStrength | Float | 38.0f | Edge distortion intensity |
| duration | Float | 3f | Animation duration (seconds) |
| transitionDelay | Long | 200 | Delay before the transition starts (ms) |

### `MotionBlurEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| intensity | Float | 2f | Strength of the blur effect |
| falloffRadius | Float | 390f | Distance over which the blur decays |

### `KaleidoscopeEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| segments | Float | 6f | Number of mirrored wedges |
| rotationSpeed | Float | 0.3f | Rotation speed (radians/sec) |
| kaleidoscopeShaderCode | String? | null | Custom AGSL shader code |

### `LiquidChromeEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| strength | Float | 14f | Displacement in pixels |
| scale | Float | 8f | Spatial frequency of the flow |
| speed | Float | 0.6f | How fast the metal flows |
| liquidShaderCode | String? | null | Custom AGSL shader code |

### `VortexEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| strength | Float | 3.5f | Max twist (radians) at the center |
| radius | Float | 700f | Falloff radius in pixels |
| speed | Float | 0.5f | How fast the spiral churns |
| vortexShaderCode | String? | null | Custom AGSL shader code |

### `HeatShimmerEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| strength | Float | 8f | Horizontal displacement in pixels |
| frequency | Float | 14f | How tight the heat waves are |
| speed | Float | 1.2f | How fast the shimmer rises |
| shimmerShaderCode | String? | null | Custom AGSL shader code |

### `NeonEdgeEffect`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| modifier | Modifier | Modifier | Compose modifier |
| thickness | Float | 1.5f | Edge sampling offset in pixels |
| intensity | Float | 3f | How bright the edges glow |
| cycleSpeed | Float | 0.1f | How fast the neon color cycles |
| neonShaderCode | String? | null | Custom AGSL shader code |

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/mejdi14/Android-AGSL-Shader-Playground/issues).

1. Fork the repo
2. Create your feature branch (`git checkout -b feature/amazing-effect`)
3. Commit your changes
4. Open a Pull Request

## 🙏 Acknowledgements

- The Motion Blur effect is inspired by [Daniel Kuntz](https://x.com/dankuntz)’ work.
- The Kaleidoscope, Liquid Chrome, Vortex, Heat Shimmer, and Neon Edge effects are AGSL reinterpretations of ideas from [krispuckett/SwiftUIShaders](https://github.com/krispuckett/SwiftUIShaders) (a Metal shader library for SwiftUI).

## 📝 License

Copyright © 2025 [Mejdi Hafiene](https://x.com/mejdi141).<br />
This project is [MIT](LICENSE) licensed.
