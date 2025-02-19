<h1 align="center">Welcome to Shader Ripple Effect Library 👋</h1>
<p align="center">
  <a href="https://github.com/frinyvonnick/gitmoji-changelog">
    <img src="https://img.shields.io/badge/API-33%2B-blue.svg?style=flat" alt="API Level">
  </a>
  <a href="https://github.com/yourusername/ShaderRippleEffect/blob/master/LICENSE">
    <img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-yellow.svg" target="_blank" />
  </a>
</p>

## ✨ Demo

<div style="display: flex; justify-content: center; align-items: center;">
  <img 
    src="demo/output.gif"
    height="400"
    width="300"
    style="margin-right: 20px;"
  />

</div>

## 🚀 Features

- ✅ Tap-reactive ripple effects with configurable properties
- ✅ Continuous wave animations with customizable parameters
- ✅ Fully compatible with any Jetpack Compose UI element
- ✅ Easy to integrate with minimal code required
- ✅ Highly customizable shader parameters

## ⚙️ Requirements

- Android SDK 33+ (Android 13 Tiramisu or higher)
- Jetpack Compose 1.4.0+

## 📦 Installation

Add this to your root `build.gradle` file:

Then, add the dependency to your module's `build.gradle` file:

```gradle
dependencies {
    implementation("io.github.mejdi14:ShaderRippleEffect:0.1.2")
}
```

## 🔥 How to Use

### Ripple Effect (Tap Responsive)

```kotlin
ShaderRippleEffect(
    amplitude = 15f,         // Controls wave height
    frequency = 20f,         // Controls wave density
    decay = 6f,              // Controls fade out speed
    speed = 2000f,           // Controls propagation speed
    animationDuration = 3.5f // Animation duration in seconds
) {
    // Your content here
    Image(
        painter = painterResource(id = R.drawable.your_image),
        contentDescription = null,
        modifier = Modifier.fillMaxSize()
    )
}
```

### Complex Wave Effect (Continuous Animation)

```kotlin
ComplexWaveEffect(
    speed = 0.8f,         // Animation speed
    strength = 25f,        // Wave amplitude
    frequency = 8f,        // Wave frequency
    timeMultiplier = 1.2f  // Time progression multiplier
) {
    // Your content here
    Text(
        text = "Wavy Text Effect",
        style = MaterialTheme.typography.displayLarge,
        modifier = Modifier.padding(32.dp)
    )
}
```

## 🎛️ Advanced Customization

Both effects support complete shader customization by providing your own GLSL shader code:

```kotlin
ShaderRippleEffect(
    rippleShaderCode = """
        uniform shader inputShader;
        uniform float2 uOrigin;
        uniform float uTime;
        // Your custom shader code here
    """.trimIndent(),
    // Other parameters...
) {
    // Your content
}
```

## 👨‍💻 Full API Reference

### ShaderRippleEffect

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| amplitude | Float | 12f | Controls the height of the ripple waves |
| frequency | Float | 15f | Controls how many ripple waves appear |
| decay | Float | 8f | Controls how quickly the ripple fades out |
| speed | Float | 1800f | Controls how fast the ripple propagates |
| animationDuration | Float | 3f | Duration of the ripple animation in seconds |
| rippleShaderCode | String? | null | Custom shader code |
| modifier | Modifier | Modifier | Compose modifier |

### ComplexWaveEffect

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| speed | Float | 0.5f | Controls how fast the waves move |
| strength | Float | 18f | Controls the amplitude of the waves |
| frequency | Float | 10f | Controls how many waves appear |
| timeMultiplier | Float | 1f | Controls how time affects the animation |
| waveShaderCode | String? | null | Custom shader code |
| modifier | Modifier | Modifier | Compose modifier |

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!
Feel free to check the [issues page](https://github.com/yourusername/ShaderRippleEffect/issues) if you want to contribute.

## 🧪 Sample App

Check out our [sample app](https://github.com/yourusername/ShaderRippleEffect/tree/main/sample) for comprehensive examples and usage patterns.

## 📝 License

Copyright © 2025 [Your Name](https://github.com/yourusername).<br />
This project is [MIT](https://github.com/yourusername/ShaderRippleEffect/blob/master/LICENSE) licensed.
