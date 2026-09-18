package com.example.shaderrippleeffect

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlin.math.min

/**
 * A clear glass surface with a thick, refractive perimeter. The middle remains close to the
 * original image while the curved edge bends, softens and spectrally separates the background.
 */
private const val APPLE_LIQUID_GLASS_SHADER = """
    uniform shader inputShader;
    uniform float2 uPointer;
    uniform float2 uMotion;
    uniform float uRadius;
    uniform float uIsActive;

    half4 main(float2 fragCoord) {
        half4 original = inputShader.eval(fragCoord);

        float radius = uRadius * (1.0 + uIsActive * 0.025);
        float2 local = fragCoord - uPointer;

        // Give quick drags a restrained, directional liquid stretch. uMotion is the distance
        // between the finger and the spring-following lens, so it naturally returns to zero.
        float motionLength = length(uMotion);
        float motionAmount = clamp(motionLength / max(radius, 1.0), 0.0, 1.0);
        float2 axis = motionLength > 0.001
            ? uMotion / motionLength
            : float2(1.0, 0.0);
        float2 perpendicular = float2(-axis.y, axis.x);
        float alongAxis = dot(local, axis) / (1.0 + motionAmount * 0.12);
        float acrossAxis = dot(local, perpendicular) / (1.0 - motionAmount * 0.035);
        float2 lensLocal = axis * alongAxis + perpendicular * acrossAxis;

        float radialDistance = length(lensLocal);
        float normalizedRadius = radialDistance / max(radius, 1.0);
        float signedDistance = radialDistance - radius;

        // A slightly offset ambient shadow makes the lens read as a separate glass surface.
        float2 shadowLocal = local - float2(radius * 0.025, radius * 0.045);
        float shadowDistance = length(shadowLocal) - radius * (1.0 + motionAmount * 0.035);
        float shadow = (1.0 - smoothstep(0.0, radius * 0.13, shadowDistance))
            * smoothstep(-1.5, 1.5, signedDistance) * 0.18;

        if (signedDistance > radius * 0.15) {
            return original;
        }

        if (signedDistance > 1.5) {
            return half4(original.rgb * half(1.0 - shadow), original.a);
        }

        float2 radialDirection = lensLocal / max(radialDistance, 0.001);
        // Refraction belongs to the curved wall of the glass, not its flat centre. The second
        // smoothstep brings the sample back to its real position at the outer silhouette so the
        // image remains continuous outside the lens.
        float edgeRise = smoothstep(0.63, 0.86, normalizedRadius);
        float boundaryFade = 1.0 - smoothstep(0.945, 1.0, normalizedRadius);
        float refractionBand = edgeRise * boundaryFade;
        float refractionDistance = radius * 0.145 * refractionBand;
        float2 samplePosition = fragCoord - radialDirection * refractionDistance;

        // Pull the image a little in the direction of travel. This is most visible at the rim and
        // gives the settling lens the soft, inertial quality seen in the reference.
        samplePosition -= uMotion * (0.028 * refractionBand);

        // Slight tangential softness inside the refractive band imitates the thickness of the
        // perimeter without blurring the clear central surface.
        float2 tangent = float2(-radialDirection.y, radialDirection.x);
        float edgeBlur = radius * 0.008 * refractionBand;
        half3 centreSample = inputShader.eval(samplePosition).rgb;
        half3 softSample = (
            inputShader.eval(samplePosition + tangent * edgeBlur).rgb
            + inputShader.eval(samplePosition - tangent * edgeBlur).rgb
        ) * 0.5;
        half3 glassColor = mix(centreSample, softSample, refractionBand * 0.32);

        // Separate the channels only around the highly curved perimeter. On high-contrast content
        // this produces the narrow warm/cool glints of thick optical glass.
        float spectralOffset = radius * 0.015 * refractionBand;
        half red = inputShader.eval(samplePosition + radialDirection * spectralOffset).r;
        half blue = inputShader.eval(samplePosition - radialDirection * spectralOffset).b;
        glassColor = half3(red, glassColor.g, blue);

        // Approximate the normal of a spherical cap for the specular and Fresnel response.
        float capRadius = min(normalizedRadius, 0.999);
        float capHeight = sqrt(max(1.0 - capRadius * capRadius, 0.001));
        float3 normal = normalize(float3(radialDirection * capRadius, capHeight * 0.72));
        float3 lightDirection = normalize(float3(-0.48, -0.62, 0.62));
        float reflectedLight = max(dot(normal, lightDirection), 0.0);
        float broadHighlight = pow(reflectedLight, 9.0) * 0.15;
        float sharpHighlight = pow(reflectedLight, 48.0) * 0.72;
        float fresnel = pow(1.0 - max(normal.z, 0.0), 2.2);

        // A very light milky body plus a bright edge keeps the material visible over both light
        // and dark imagery without washing out the magnified content.
        float bodyTint = 0.018 + fresnel * 0.085;
        glassColor = mix(glassColor, half3(1.0), bodyTint);
        glassColor += half3(broadHighlight + sharpHighlight + fresnel * 0.17);

        // Fine inner and outer rings sell the polished rim. The darker inner line also preserves
        // the silhouette when the content beneath the lens is white.
        float innerRing = smoothstep(0.900, 0.965, normalizedRadius)
            * (1.0 - smoothstep(0.965, 0.997, normalizedRadius));
        float outerRing = smoothstep(0.965, 0.995, normalizedRadius);
        glassColor *= half(1.0 - innerRing * 0.055);
        glassColor += half3(outerRing * 0.20);

        float coverage = 1.0 - smoothstep(-1.5, 1.5, signedDistance);
        half4 glass = half4(glassColor, original.a);
        half4 result = mix(original, glass, coverage);
        result.rgb *= half(1.0 - shadow * (1.0 - coverage));
        return result;
    }
"""

@Composable
fun AppleLiquidGlassEffect(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var targetPointer by remember { mutableStateOf(Offset.Zero) }
    var pointerInitialized by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(false) }

    val animatedPointer = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val activeState by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquid glass press"
    )

    LaunchedEffect(width, height) {
        if (!pointerInitialized && width > 0f && height > 0f) {
            targetPointer = Offset(width / 2f, height / 2f)
            animatedPointer.snapTo(targetPointer)
            pointerInitialized = true
        }
    }

    LaunchedEffect(targetPointer, pointerInitialized) {
        if (pointerInitialized) {
            animatedPointer.animateTo(
                targetValue = targetPointer,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 520f
                )
            )
        }
    }

    val runtimeShader = remember { RuntimeShader(APPLE_LIQUID_GLASS_SHADER) }
    val renderedPointer = animatedPointer.value

    if (width > 0f && height > 0f && pointerInitialized) {
        val motion = targetPointer - renderedPointer
        runtimeShader.setFloatUniform("uPointer", renderedPointer.x, renderedPointer.y)
        runtimeShader.setFloatUniform("uMotion", motion.x, motion.y)
        runtimeShader.setFloatUniform("uRadius", min(width, height) * 0.16f)
        runtimeShader.setFloatUniform("uIsActive", activeState)
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged {
                width = it.width.toFloat()
                height = it.height.toFloat()
            }
            .graphicsLayer {
                if (width > 0f && height > 0f && pointerInitialized) {
                    renderEffect = runtimeShader.asContentRenderEffect()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isActive = true
                        targetPointer = offset
                    },
                    onDragEnd = { isActive = false },
                    onDragCancel = { isActive = false },
                    onDrag = { change, _ ->
                        targetPointer = change.position
                        change.consume()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isActive = true
                        targetPointer = offset
                        tryAwaitRelease()
                        isActive = false
                    }
                )
            }
    ) {
        content()
    }
}
