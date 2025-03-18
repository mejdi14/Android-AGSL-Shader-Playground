<h1 align="center">Welcome to Shader Ripple Effect Playground 👋</h1>
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
    height="430"
    width="300"
    style="margin-right: 20px;"
  />

</div>

## 🚀 Features

- ✅ Tap-reactive ripple effects with configurable properties
- ✅ Continuous wave animations with customizable parameters
- ✅ Content reveal transitions with ripple effect
- ✅ Fully compatible with any Jetpack Compose UI element
- ✅ Easy to integrate with minimal code required


## ⚙️ Requirements

- Android SDK 33+ (Android 13 Tiramisu or higher)
- Jetpack Compose 1.4.0+

## 📦 Installation

Add this to your root `build.gradle` file:

Then, add the dependency to your module's `build.gradle` file:

```gradle
dependencies {
    implementation("io.github.mejdi14:android-shader-effect:0.1.3")
}
```

## 🔥 How to Use

### Ripple Effect (Tap Responsive)

```kotlin
ShaderRippleEffect(
    amplitude = 15f, 
    frequency = 20f,  
    decay = 6f,      
    speed = 2000f,    
    animationDuration = 3.5f 
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
    speed = 0.8f,         
    strength = 25f,  
    frequency = 8f,   
    timeMultiplier = 1.2f 
) {
    // Your content here
    Text(
        text = "Wavy Text Effect",
        style = MaterialTheme.typography.displayLarge,
        modifier = Modifier.padding(32.dp)
    )
}
```

### Reveal Animation (Content Transition)

<div style="display: flex; justify-content: center; align-items: center;">
  <img 
    src="demo/output2.gif"
    height="430"
    width="300"
    style="margin-right: 20px;"
  />

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
    firstContent = {
        // First content to show/hide
        FirstContent()
    },
    secondContent = {
        // Second content to reveal
        SecondContent()
    }
)
```


### Motion Blur Effect

<div style="display: flex; justify-content: center; align-items: center;">
  <img 
    src="demo/output5.gif"
    height="430"
    width="300"
    style="margin-right: 20px;"
  />

</div>

```kotlin
MotionBlurEffect(
    modifier = Modifier,       // Modifier to adjust layout or styling
    intensity = 2f,            // Determines the strength of the blur effect
    falloffRadius = 390f,      // Defines the distance over which the blur decays
    content = {                // Composable content on which the effect is applied
        // Your composable content here
        YourContent()
    }
)
```

## 🎛️ Advanced Customization

All effects support complete shader customization by providing your own GLSL shader code:

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

### RevealShaderEffect

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| waveParams | WaveEffectParams | default | Configuration for the wave ripple effect |
| revealParams | RevealTransitionParams | default | Configuration for the reveal transition |
| firstContent | @Composable | required | The initial content to be replaced |
| secondContent | @Composable | required | The content to be revealed |
| modifier | Modifier | Modifier | Compose modifier |

#### WaveEffectParams

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| amplitude | Float | 40f | Controls wave height |
| frequency | Float | 20f | Controls ripple density |
| decay | Float | 5f | Controls ripple fade-out speed |
| speed | Float | 1000f | Controls propagation speed |
| duration | Float | 3f | Animation duration in seconds |

#### RevealTransitionParams

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| speed | Float | 1200f | Controls reveal animation speed |
| frequency | Float | 40f | Controls edge wave frequency |
| amplitude | Float | 30.5f | Controls edge wave height |
| edgeWidth | Float | 0f | Controls transition edge width |
| wiggleStrength | Float | 38.0f | Controls edge distortion intensity |
| duration | Float | 3f | Animation duration in seconds |
| transitionDelay | Long | 200 | Delay before transition starts (ms) |

## 🤝 Contributing

Contributions, issues, and feature requests are welcome!
Feel free to check the [issues page](https://github.com/mejdi14/ShaderRippleEffect/issues) if you want to contribute.

## 📝 License

Copyright © 2025 [Mejdi Hafiene](https://x.com/mejdi141).<br />
This project is [MIT](https://github.com/yourusername/ShaderRippleEffect/blob/master/LICENSE) licensed.
