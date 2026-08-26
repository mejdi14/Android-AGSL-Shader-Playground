package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.shaderrippleeffect.internal.AgslShaders
import com.example.shaderrippleeffect.internal.animateElapsedSeconds
import com.example.shaderrippleeffect.internal.asContentRenderEffect

/**
 * A tap-reactive ripple effect that can be applied to any composable content.
 *
 * @param amplitude Controls the height of the ripple waves. Default: 12f
 * @param frequency Controls how many ripple waves appear. Default: 15f
 * @param decay Controls how quickly the ripple fades out. Default: 8f
 * @param speed Controls how fast the ripple propagates. Default: 1800f
 * @param animationDuration Duration of the ripple animation in seconds. Default: 3f
 * @param rippleShaderCode Custom shader code if you want to override the default. Default: null
 * @param modifier Additional modifiers to apply
 * @param content The composable content to apply the effect to
 */
@Composable
fun ShaderRippleEffect(
    modifier: Modifier = Modifier,
    amplitude: Float = 12f,
    frequency: Float = 15f,
    decay: Float = 8f,
    speed: Float = 1800f,
    animationDuration: Float = 3f,
    rippleShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var trigger by remember { mutableIntStateOf(0) }
    var elapsedTime by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(trigger) {
        animateElapsedSeconds(animationDuration) { elapsedTime = it }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val shaderCode = rippleShaderCode ?: AgslShaders.RIPPLE_DISPLACEMENT
    val runtimeShader = remember(shaderCode) { RuntimeShader(shaderCode) }
    runtimeShader.setFloatUniform("uResolution", floatArrayOf(screenWidth, screenHeight))
    runtimeShader.setFloatUniform("uOrigin", floatArrayOf(origin.x, origin.y))
    runtimeShader.setFloatUniform("uTime", elapsedTime)
    runtimeShader.setFloatUniform("uAmplitude", amplitude)
    runtimeShader.setFloatUniform("uFrequency", frequency)
    runtimeShader.setFloatUniform("uDecay", decay)
    runtimeShader.setFloatUniform("uSpeed", speed)

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { renderEffect = runtimeShader.asContentRenderEffect() }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    origin = tapOffset
                    trigger++
                }
            }
    ) {
        content()
    }
}
