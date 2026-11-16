package com.example.shaderrippleeffect

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.example.shaderrippleeffect.internal.asContentRenderEffect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How the fluid dye channels drive the displacement in [FluidDisplacementEffect].
 */
enum class FluidChannelMode(internal val code: Float) {
    /** Red drives horizontal displacement and Green drives vertical displacement (0.5 = neutral). */
    TwoAxis(0f),

    /** Luminance pushes content along a fixed [FluidDisplacementEffect] `angle`. */
    Directional(1f)
}

/**
 * How out-of-bounds UVs are handled when [FluidDisplacementEffect] pushes pixels past the edge.
 */
enum class FluidEdgeMode(internal val code: Float) {
    /** Clamp UVs to `[0, 1]`. */
    Stretch(0f),

    /** Clip out-of-bounds lookups to transparent. */
    Transparent(1f),

    /** Mirror-reflect UVs seamlessly back into `[0, 1]`. */
    Mirror(2f),

    /** Tile UVs via `fract(uv)`. */
    Wrap(3f)
}

/**
 * How the brush colour evolves as you drag through the fluid in [FluidDisplacementEffect].
 */
enum class FluidColorMode {
    /** Cycles smoothly around the hue wheel along the stroke and over time. */
    Rainbow,

    /** Uses a single fixed brush colour modulated by stroke velocity. */
    Single,

    /** Cycles smoothly across a custom palette of colours. */
    Custom
}

/** Default custom brush cycle stops matching Shaders' `InkFlow` (`#4338FF`, `#FF2D7E`, `#19E3FF`). */
val DefaultFluidCustomColors: List<Color> = listOf(
    Color(0xFF4338FF),
    Color(0xFFFF2D7E),
    Color(0xFF19E3FF)
)

/**
 * Interactive Navier-Stokes fluid displacement wrapper around any composable content.
 *
 * An Android AGSL port of the **Fluid Displacement** composition from
 * [Shaders](https://shaders.com/collection/fluid-displacement/8187fa41-52be-46c4-88a9-2d682c973592),
 * which pairs a 2D Stable-Fluids `InkFlow` simulation (`splat → curl → vorticity confinement →
 * divergence → Jacobi pressure solve → gradient subtract → semi-Lagrangian velocity & dye advection`)
 * with a `DisplacementMap` filter (`dmDisplaceRG` / `dmDisplaceLuminance` + mirror edge handling).
 *
 * Dragging across the composable injects a continuous ribbon of Gaussian velocity and dye impulses
 * into the fluid grid; the resulting swirling eddies warp the underlying content and continue
 * curling under their own momentum for several seconds after release.
 *
 * @param modifier Compose modifier applied to the wrapper box.
 * @param amount Overall displacement strength (`0f..1f`). Default: `0.15f`.
 * @param channelMode Whether Red/Green drive 2-axis displacement or luminance pushes along [angle].
 *   Default: [FluidChannelMode.TwoAxis].
 * @param angle Push angle in degrees when [channelMode] is [FluidChannelMode.Directional].
 *   Default: `0f`.
 * @param edges How out-of-bounds UVs are remapped at the border. Default: [FluidEdgeMode.Mirror].
 * @param colorMode How brush colours evolve along strokes. Default: [FluidColorMode.Rainbow].
 * @param colorSpeed Rate at which brush colours cycle (`0f..4f`). Default: `1f`.
 * @param brushColor Brush colour when [colorMode] is [FluidColorMode.Single].
 *   Default: `#FF2D7E`.
 * @param customColors Brush colour cycle when [colorMode] is [FluidColorMode.Custom].
 *   Default: [DefaultFluidCustomColors].
 * @param radius Brush splat size (`0.1f..5f`). Default: `0.3f`.
 * @param force How hard pointer motion kicks the fluid (`0f..3f`). Default: `1f`.
 * @param curl Vorticity confinement gain keeping eddies spinning (`0f..60f`). Default: `14f`.
 * @param decay How quickly fluid trails dissipate (`0.05f..4f`). Default: `0.5f`.
 * @param momentum How long fluid velocity carries on after a stroke (`0f..1f`). Default: `0.6f`.
 * @param seedInitialImpulse Whether to seed an initial swirling wave on first composition so the
 *   effect is immediately visible before the first touch. Default: `true`.
 * @param fluidShaderCode Optional custom AGSL shader code to override the built-in displacement pass.
 * @param content The composable content to displace.
 */
@Composable
fun FluidDisplacementEffect(
    modifier: Modifier = Modifier,
    amount: Float = 0.15f,
    channelMode: FluidChannelMode = FluidChannelMode.TwoAxis,
    angle: Float = 0f,
    edges: FluidEdgeMode = FluidEdgeMode.Mirror,
    colorMode: FluidColorMode = FluidColorMode.Rainbow,
    colorSpeed: Float = 1f,
    brushColor: Color = Color(0xFFFF2D7E),
    customColors: List<Color> = DefaultFluidCustomColors,
    radius: Float = 0.3f,
    force: Float = 1f,
    curl: Float = 14f,
    decay: Float = 0.5f,
    momentum: Float = 0.6f,
    seedInitialImpulse: Boolean = true,
    fluidShaderCode: String? = null,
    content: @Composable () -> Unit
) {
    val solver = remember {
        FluidFieldSolver(n = FLUID_GRID_SIZE).apply {
            if (seedInitialImpulse) {
                seedOpeningSwirl(
                    colorMode = colorMode,
                    colorSpeed = colorSpeed,
                    brushColor = brushColor,
                    customColors = customColors,
                    radius = radius,
                    force = force,
                    curl = curl,
                    decay = decay,
                    momentum = momentum
                )
            }
        }
    }

    val shaderCode = fluidShaderCode ?: FLUID_DISPLACEMENT_SHADER
    val runtimeShader = remember(shaderCode) { RuntimeShader(shaderCode) }

    var frameTick by remember { mutableIntStateOf(0) }
    var isSimActive by remember { mutableStateOf(seedInitialImpulse) }
    var isPointerDown by remember { mutableStateOf(false) }

    LaunchedEffect(isSimActive, curl, decay, momentum, colorSpeed) {
        if (!isSimActive) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, MAX_DELTA_SECONDS)
            lastNanos = nowNanos

            solver.advanceClock(dt, colorSpeed)
            solver.step(
                dt = dt,
                curlStrength = curl.coerceIn(0f, 60f),
                decay = decay.coerceIn(0.05f, 4f),
                momentum = momentum.coerceIn(0f, 1f)
            )
            frameTick++

            if (!isPointerDown && solver.maxEnergy < IDLE_ENERGY_EPSILON) {
                solver.clear()
                frameTick++
                isSimActive = false
                break
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(colorMode, colorSpeed, brushColor, customColors, radius, force) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.coerceAtLeast(1).toFloat()
                    val h = size.height.coerceAtLeast(1).toFloat()

                    var prevUvX = (down.position.x / w).coerceIn(0f, 1f)
                    var prevUvY = (down.position.y / h).coerceIn(0f, 1f)
                    var prevTimeMillis = down.uptimeMillis

                    isPointerDown = true
                    isSimActive = true

                    // Gentle initial swirl on touch-down so taps also stir the fluid.
                    solver.stampRibbon(
                        fromUvX = (prevUvX - 0.012f).coerceIn(0f, 1f),
                        fromUvY = (prevUvY - 0.008f).coerceIn(0f, 1f),
                        toUvX = prevUvX,
                        toUvY = prevUvY,
                        dt = 0.016f,
                        radius = radius,
                        force = force * 0.65f,
                        colorMode = colorMode,
                        colorSpeed = colorSpeed,
                        brushColor = brushColor,
                        customColors = customColors
                    )

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val curUvX = (change.position.x / w).coerceIn(0f, 1f)
                            val curUvY = (change.position.y / h).coerceIn(0f, 1f)
                            val dtMillis = (change.uptimeMillis - prevTimeMillis).coerceAtLeast(4L)
                            val dt = (dtMillis / 1000f).coerceIn(0.004f, MAX_DELTA_SECONDS)

                            val dx = curUvX - prevUvX
                            val dy = curUvY - prevUvY
                            val dist = hypot(dx, dy)
                            if (dist > 0.0005f && dist < TELEPORT_GUARD_UV) {
                                solver.stampRibbon(
                                    fromUvX = prevUvX,
                                    fromUvY = prevUvY,
                                    toUvX = curUvX,
                                    toUvY = curUvY,
                                    dt = dt,
                                    radius = radius,
                                    force = force,
                                    colorMode = colorMode,
                                    colorSpeed = colorSpeed,
                                    brushColor = brushColor,
                                    customColors = customColors
                                )
                                isSimActive = true
                            }
                            prevUvX = curUvX
                            prevUvY = curUvY
                            prevTimeMillis = change.uptimeMillis
                        }
                    } while (event.changes.any { it.pressed })

                    isPointerDown = false
                }
            }
            .graphicsLayer {
                // Reading frameTick invalidates the graphicsLayer each simulation step.
                val tick = frameTick
                if (isSimActive && tick >= 0 && size.width > 0f && size.height > 0f) {
                    val fluidShader = BitmapShader(
                        solver.bitmap,
                        Shader.TileMode.CLAMP,
                        Shader.TileMode.CLAMP
                    )
                    runtimeShader.setInputShader("uFluidMap", fluidShader)
                    runtimeShader.setFloatUniform("uResolution", size.width, size.height)
                    runtimeShader.setFloatUniform("uGridSize", solver.n.toFloat())
                    runtimeShader.setFloatUniform("uAmount", amount.coerceIn(0f, 1f))
                    runtimeShader.setFloatUniform("uAngle", angle)
                    runtimeShader.setFloatUniform("uChannelMode", channelMode.code)
                    runtimeShader.setFloatUniform("uEdgeMode", edges.code)
                    renderEffect = runtimeShader.asContentRenderEffect()
                } else {
                    renderEffect = null
                }
            }
    ) {
        content()
    }
}

private const val FLUID_GRID_SIZE = 96
private const val JACOBI_ITERS = 10
private const val MAX_STEPS = 16
private const val IMPULSE_K = 0.16f
private const val CYCLE_PER_SPLAT = 0.012f
private const val CYCLE_TIME_RATE = 0.06f
private const val VEL_FADE_MAX = 4.0f
private const val VEL_FADE_MIN = 0.05f
private const val GAIN = 1.35f
private const val PRESSURE_DECAY = 0.8f
private const val MAX_DELTA_SECONDS = 0.033f
private const val TELEPORT_GUARD_UV = 0.35f
private const val IDLE_ENERGY_EPSILON = 0.0015f

/**
 * CPU Stable-Fluids Navier-Stokes solver ported from `@coreroot/gpu/scaffolds/fluids.ts` and
 * `@coreroot/shaders/InkFlow/index.ts`.
 *
 * Maintains a flat `N × N` grid of velocity (`velAX/Y`), vorticity (`curlZ`), pressure
 * (`pressureA/B`), divergence, and RGB dye (`dyeAR/G/B`), publishing each frame into an
 * `ARGB_8888` [Bitmap] consumed by the AGSL displacement shader.
 */
private class FluidFieldSolver(val n: Int) {
    private val count = n * n

    private var velAX = FloatArray(count)
    private var velAY = FloatArray(count)
    private var velBX = FloatArray(count)
    private var velBY = FloatArray(count)
    private val curlZ = FloatArray(count)

    private var dyeAR = FloatArray(count)
    private var dyeAG = FloatArray(count)
    private var dyeAB = FloatArray(count)
    private var dyeBR = FloatArray(count)
    private var dyeBG = FloatArray(count)
    private var dyeBB = FloatArray(count)

    private var pressureA = FloatArray(count)
    private var pressureB = FloatArray(count)
    private val divergence = FloatArray(count)

    private val pixels = IntArray(count)
    val bitmap: Bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)

    private var hueCursor = 0.85f
    var maxEnergy = 0f
        private set

    fun advanceClock(dt: Float, colorSpeed: Float) {
        hueCursor = (hueCursor + dt * CYCLE_TIME_RATE * colorSpeed) % 1f
    }

    fun clear() {
        velAX.fill(0f)
        velAY.fill(0f)
        velBX.fill(0f)
        velBY.fill(0f)
        curlZ.fill(0f)
        dyeAR.fill(0f)
        dyeAG.fill(0f)
        dyeAB.fill(0f)
        dyeBR.fill(0f)
        dyeBG.fill(0f)
        dyeBB.fill(0f)
        pressureA.fill(0f)
        pressureB.fill(0f)
        divergence.fill(0f)
        pixels.fill(0)
        bitmap.setPixels(pixels, 0, n, 0, 0, n, n)
        maxEnergy = 0f
    }

    fun seedOpeningSwirl(
        colorMode: FluidColorMode,
        colorSpeed: Float,
        brushColor: Color,
        customColors: List<Color>,
        radius: Float,
        force: Float,
        curl: Float,
        decay: Float,
        momentum: Float
    ) {
        val waypoints = arrayOf(
            Offset(0.22f, 0.56f),
            Offset(0.36f, 0.44f),
            Offset(0.52f, 0.54f),
            Offset(0.66f, 0.42f),
            Offset(0.78f, 0.50f)
        )
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            stampRibbon(
                fromUvX = a.x,
                fromUvY = a.y,
                toUvX = b.x,
                toUvY = b.y,
                dt = 0.024f,
                radius = max(radius, 0.45f),
                force = max(force, 1.15f),
                colorMode = colorMode,
                colorSpeed = colorSpeed,
                brushColor = brushColor,
                customColors = customColors
            )
            step(
                dt = 0.024f,
                curlStrength = max(curl, 14f),
                decay = decay,
                momentum = momentum
            )
        }
    }

    fun stampRibbon(
        fromUvX: Float,
        fromUvY: Float,
        toUvX: Float,
        toUvY: Float,
        dt: Float,
        radius: Float,
        force: Float,
        colorMode: FluidColorMode,
        colorSpeed: Float,
        brushColor: Color,
        customColors: List<Color>
    ) {
        val dx = toUvX - fromUvX
        val dy = toUvY - fromUvY
        val dragDist = hypot(dx, dy)
        val radiusGrid = max(radius.coerceIn(0.1f, 5f) * 0.1f * n, 1.5f)
        val stepSize = max(0.004f, (radiusGrid / n) * 0.55f)
        val steps = ceil(dragDist / stepSize).toInt().coerceIn(1, MAX_STEPS)

        val safeDt = dt.coerceAtLeast(0.004f)
        val ptrVelX = (dx / safeDt).coerceIn(-14f, 14f)
        val ptrVelY = (dy / safeDt).coerceIn(-14f, 14f)
        val impVelX = ptrVelX * n * force * IMPULSE_K
        val impVelY = ptrVelY * n * force * IMPULSE_K

        val dirLen = max(hypot(dx, dy), 1e-5f)
        val dirX = dx / dirLen
        val dirY = dy / dirLen
        // Velocity-aligned RGB in [0, 1] so the stroke direction immediately participates in 2D warp.
        val velR = (0.5f + 0.5f * dirX).coerceIn(0f, 1f)
        val velG = (0.5f + 0.5f * dirY).coerceIn(0f, 1f)
        val velB = 0.65f

        val reach = ceil(radiusGrid * 3.0f).toInt()
        val invRadSq = 1f / max(radiusGrid * radiusGrid, 1f)

        for (s in 1..steps) {
            val t = s.toFloat() / steps.toFloat()
            val posX = (fromUvX + dx * t) * n
            val posY = (fromUvY + dy * t) * n

            hueCursor = (hueCursor + CYCLE_PER_SPLAT * colorSpeed) % 1f
            if (hueCursor < 0f) hueCursor += 1f

            val baseColor = resolveBrushColor(
                hue = hueCursor,
                colorMode = colorMode,
                brushColor = brushColor,
                customColors = customColors
            )
            val velBlend = if (colorMode == FluidColorMode.Single) 0.55f else 0.3f
            val colR = baseColor[0] * (1f - velBlend) + velR * velBlend
            val colG = baseColor[1] * (1f - velBlend) + velG * velBlend
            val colB = max(baseColor[2] * (1f - velBlend) + velB * velBlend, 0.25f)

            val minX = (posX.toInt() - reach).coerceAtLeast(0)
            val maxX = (posX.toInt() + reach).coerceAtMost(n - 1)
            val minY = (posY.toInt() - reach).coerceAtLeast(0)
            val maxY = (posY.toInt() + reach).coerceAtMost(n - 1)

            for (cy in minY..maxY) {
                val row = cy * n
                val ddy = (cy + 0.5f) - posY
                val ddySq = ddy * ddy
                for (cx in minX..maxX) {
                    val ddx = (cx + 0.5f) - posX
                    val distSq = ddx * ddx + ddySq
                    val inf = exp(-distSq * invRadSq)
                    if (inf < 1e-3f) continue
                    val w = inf.coerceIn(0f, 1f)
                    val idx = row + cx
                    dyeAR[idx] += (colR - dyeAR[idx]) * w
                    dyeAG[idx] += (colG - dyeAG[idx]) * w
                    dyeAB[idx] += (colB - dyeAB[idx]) * w
                    velAX[idx] += impVelX * inf
                    velAY[idx] += impVelY * inf
                }
            }
        }
    }

    fun step(
        dt: Float,
        curlStrength: Float,
        decay: Float,
        momentum: Float
    ) {
        val last = n - 1

        // 1. Curl pass: z-component of ∇ × v
        for (cy in 0 until n) {
            val row = cy * n
            val rowU = if (cy > 0) (cy - 1) * n else 0
            val rowD = if (cy < last) (cy + 1) * n else last * n
            for (cx in 0 until n) {
                val colL = if (cx > 0) cx - 1 else 0
                val colR = if (cx < last) cx + 1 else last
                val vLy = velAY[row + colL]
                val vRy = velAY[row + colR]
                val vUx = velAX[rowU + cx]
                val vDx = velAX[rowD + cx]
                curlZ[row + cx] = (vRy - vLy - (vDx - vUx)) * 0.5f
            }
        }

        // 2. Vorticity confinement: push velocity along gradient of |curl|
        if (curlStrength > 0.001f) {
            val vortGain = curlStrength * dt
            for (cy in 0 until n) {
                val row = cy * n
                val rowU = if (cy > 0) (cy - 1) * n else 0
                val rowD = if (cy < last) (cy + 1) * n else last * n
                for (cx in 0 until n) {
                    val colL = if (cx > 0) cx - 1 else 0
                    val colR = if (cx < last) cx + 1 else last
                    val cL = abs(curlZ[row + colL])
                    val cR = abs(curlZ[row + colR])
                    val cU = abs(curlZ[rowU + cx])
                    val cD = abs(curlZ[rowD + cx])
                    val cc = curlZ[row + cx]
                    val gx = (cR - cL) * 0.5f
                    val gy = (cD - cU) * 0.5f
                    val gl = max(sqrt(gx * gx + gy * gy), 1e-5f)
                    val idx = row + cx
                    velAX[idx] += (gy / gl) * cc * vortGain
                    velAY[idx] += (-gx / gl) * cc * vortGain
                }
            }
        }

        // 3. Divergence + pressure warm start (free-slip box boundary)
        for (cy in 0 until n) {
            val row = cy * n
            val rowU = if (cy > 0) (cy - 1) * n else 0
            val rowD = if (cy < last) (cy + 1) * n else last * n
            for (cx in 0 until n) {
                val idx = row + cx
                val selfVx = velAX[idx]
                val selfVy = velAY[idx]
                val vL = if (cx == 0) -selfVx else velAX[row + cx - 1]
                val vR = if (cx == last) -selfVx else velAX[row + cx + 1]
                val vU = if (cy == 0) -selfVy else velAY[rowU + cx]
                val vD = if (cy == last) -selfVy else velAY[rowD + cx]
                divergence[idx] = (vR - vL + (vD - vU)) * 0.5f
                pressureA[idx] *= PRESSURE_DECAY
            }
        }

        // 4. Jacobi pressure relaxation (10 iterations)
        repeat(JACOBI_ITERS) {
            for (cy in 0 until n) {
                val row = cy * n
                val rowU = if (cy > 0) (cy - 1) * n else 0
                val rowD = if (cy < last) (cy + 1) * n else last * n
                for (cx in 0 until n) {
                    val colL = if (cx > 0) cx - 1 else 0
                    val colR = if (cx < last) cx + 1 else last
                    val pL = pressureA[row + colL]
                    val pR = pressureA[row + colR]
                    val pU = pressureA[rowU + cx]
                    val pD = pressureA[rowD + cx]
                    val idx = row + cx
                    pressureB[idx] = (pL + pR + pU + pD - divergence[idx]) * 0.25f
                }
            }
            val tmpP = pressureA
            pressureA = pressureB
            pressureB = tmpP
        }

        // 5. Subtract pressure gradient: v <- v - ∇p
        for (cy in 0 until n) {
            val row = cy * n
            val rowU = if (cy > 0) (cy - 1) * n else 0
            val rowD = if (cy < last) (cy + 1) * n else last * n
            for (cx in 0 until n) {
                val colL = if (cx > 0) cx - 1 else 0
                val colR = if (cx < last) cx + 1 else last
                val idx = row + cx
                velAX[idx] -= (pressureA[row + colR] - pressureA[row + colL]) * 0.5f
                velAY[idx] -= (pressureA[rowD + cx] - pressureA[rowU + cx]) * 0.5f
            }
        }

        // 6. Semi-Lagrangian velocity advection
        val velFade = VEL_FADE_MAX * (VEL_FADE_MIN / VEL_FADE_MAX).pow(momentum)
        val invVelDecay = 1f / (1f + velFade * dt)
        val maxCoord = last.toFloat()
        val maxFloor = n - 2

        for (cy in 0 until n) {
            val row = cy * n
            for (cx in 0 until n) {
                val idx = row + cx
                val srcX = (cx.toFloat() - velAX[idx] * dt).coerceIn(0f, maxCoord)
                val srcY = (cy.toFloat() - velAY[idx] * dt).coerceIn(0f, maxCoord)
                val ix0 = srcX.toInt().coerceIn(0, maxFloor)
                val iy0 = srcY.toInt().coerceIn(0, maxFloor)
                val sx = srcX - ix0
                val sy = srcY - iy0
                val i00 = iy0 * n + ix0
                val i10 = i00 + 1
                val i01 = i00 + n
                val i11 = i01 + 1

                val vx0 = velAX[i00] + (velAX[i10] - velAX[i00]) * sx
                val vx1 = velAX[i01] + (velAX[i11] - velAX[i01]) * sx
                val vy0 = velAY[i00] + (velAY[i10] - velAY[i00]) * sx
                val vy1 = velAY[i01] + (velAY[i11] - velAY[i01]) * sx

                velBX[idx] = (vx0 + (vx1 - vx0) * sy) * invVelDecay
                velBY[idx] = (vy0 + (vy1 - vy0) * sy) * invVelDecay
            }
        }
        val tmpVX = velAX; velAX = velBX; velBX = tmpVX
        val tmpVY = velAY; velAY = velBY; velBY = tmpVY

        // 7. Semi-Lagrangian RGB dye advection
        val invDyeDecay = 1f / (1f + decay * dt)
        var peakLum = 0f

        for (cy in 0 until n) {
            val row = cy * n
            for (cx in 0 until n) {
                val idx = row + cx
                val srcX = (cx.toFloat() - velAX[idx] * dt).coerceIn(0f, maxCoord)
                val srcY = (cy.toFloat() - velAY[idx] * dt).coerceIn(0f, maxCoord)
                val ix0 = srcX.toInt().coerceIn(0, maxFloor)
                val iy0 = srcY.toInt().coerceIn(0, maxFloor)
                val sx = srcX - ix0
                val sy = srcY - iy0
                val i00 = iy0 * n + ix0
                val i10 = i00 + 1
                val i01 = i00 + n
                val i11 = i01 + 1

                val r0 = dyeAR[i00] + (dyeAR[i10] - dyeAR[i00]) * sx
                val r1 = dyeAR[i01] + (dyeAR[i11] - dyeAR[i01]) * sx
                val g0 = dyeAG[i00] + (dyeAG[i10] - dyeAG[i00]) * sx
                val g1 = dyeAG[i01] + (dyeAG[i11] - dyeAG[i01]) * sx
                val b0 = dyeAB[i00] + (dyeAB[i10] - dyeAB[i00]) * sx
                val b1 = dyeAB[i01] + (dyeAB[i11] - dyeAB[i01]) * sx

                val r = max((r0 + (r1 - r0) * sy) * invDyeDecay, 0f)
                val g = max((g0 + (g1 - g0) * sy) * invDyeDecay, 0f)
                val b = max((b0 + (b1 - b0) * sy) * invDyeDecay, 0f)

                dyeBR[idx] = r
                dyeBG[idx] = g
                dyeBB[idx] = b

                val lum = max(r, max(g, b))
                if (lum > peakLum) peakLum = lum
            }
        }
        val tmpDR = dyeAR; dyeAR = dyeBR; dyeBR = tmpDR
        val tmpDG = dyeAG; dyeAG = dyeBG; dyeBG = tmpDG
        val tmpDB = dyeAB; dyeAB = dyeBB; dyeBB = tmpDB
        maxEnergy = peakLum

        // 8. Output pass: gentle 5-point spatial smoothing + premultiplied ARGB_8888 pack
        for (cy in 0 until n) {
            val row = cy * n
            val rowU = if (cy > 0) (cy - 1) * n else 0
            val rowD = if (cy < last) (cy + 1) * n else last * n
            for (cx in 0 until n) {
                val colL = if (cx > 0) cx - 1 else 0
                val colR = if (cx < last) cx + 1 else last
                val idx = row + cx

                val r = dyeAR[idx] * 0.52f +
                    (dyeAR[row + colL] + dyeAR[row + colR] + dyeAR[rowU + cx] + dyeAR[rowD + cx]) * 0.12f
                val g = dyeAG[idx] * 0.52f +
                    (dyeAG[row + colL] + dyeAG[row + colR] + dyeAG[rowU + cx] + dyeAG[rowD + cx]) * 0.12f
                val b = dyeAB[idx] * 0.52f +
                    (dyeAB[row + colL] + dyeAB[row + colR] + dyeAB[rowU + cx] + dyeAB[rowD + cx]) * 0.12f

                val lum = max(r, max(g, b))
                if (lum <= 1e-5f) {
                    pixels[idx] = 0
                } else {
                    val a = min(lum * GAIN, 1f)
                    val scale = a / lum
                    val aByte = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val rByte = (r * scale * 255f + 0.5f).toInt().coerceIn(0, aByte)
                    val gByte = (g * scale * 255f + 0.5f).toInt().coerceIn(0, aByte)
                    val bByte = (b * scale * 255f + 0.5f).toInt().coerceIn(0, aByte)
                    pixels[idx] = (aByte shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
                }
            }
        }
        bitmap.setPixels(pixels, 0, n, 0, 0, n, n)
    }

    private fun resolveBrushColor(
        hue: Float,
        colorMode: FluidColorMode,
        brushColor: Color,
        customColors: List<Color>
    ): FloatArray = when (colorMode) {
        FluidColorMode.Single -> floatArrayOf(brushColor.red, brushColor.green, brushColor.blue)
        FluidColorMode.Custom -> {
            val stops = if (customColors.size >= 2) customColors else DefaultFluidCustomColors
            val h = ((hue % 1f) + 1f) % 1f
            val scaled = h * stops.size
            val idxA = floor(scaled).toInt().coerceIn(0, stops.size - 1)
            val idxB = (idxA + 1) % stops.size
            val frac = scaled - idxA
            val a = stops[idxA]
            val b = stops[idxB]
            floatArrayOf(
                a.red + (b.red - a.red) * frac,
                a.green + (b.green - a.green) * frac,
                a.blue + (b.blue - a.blue) * frac
            )
        }
        FluidColorMode.Rainbow -> hueToLinearRgb(hue)
    }

    private fun hueToLinearRgb(hue: Float): FloatArray {
        val h = ((hue % 1f) + 1f) % 1f
        val i = floor(h * 6f).toInt()
        val f = h * 6f - i
        val q = 1f - f
        var r = 0f
        var g = 0f
        var b = 0f
        when (i % 6) {
            0 -> { r = 1f; g = f; b = 0f }
            1 -> { r = q; g = 1f; b = 0f }
            2 -> { r = 0f; g = 1f; b = f }
            3 -> { r = 0f; g = q; b = 1f }
            4 -> { r = f; g = 0f; b = 1f }
            else -> { r = 1f; g = 0f; b = q }
        }
        return floatArrayOf(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
    }

    private fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

/**
 * AGSL port of Shaders' `DisplacementMap` (`dmDisplaceRG`, `dmDisplaceLuminance`, and
 * `edgeMirrorUV` / `edgeClampUV` / `edgeWrapUV` / `edgeTransparentMask`) sampling the live
 * `InkFlow` fluid texture with C1 Hermite interpolation across grid cells.
 */
private val FLUID_DISPLACEMENT_SHADER = """
    uniform shader inputShader;
    uniform shader uFluidMap;
    uniform float2 uResolution;
    uniform float uGridSize;
    uniform float uAmount;
    uniform float uAngle;
    uniform float uChannelMode;
    uniform float uEdgeMode;

    // C1-continuous Hermite interpolation across the N×N fluid dye grid so spatial
    // derivatives (which control image magnification/compression) are seam-free.
    float4 sampleFluidField(float2 uv) {
        float maxIdx = max(uGridSize - 1.0, 1.0);
        float2 gridPos = clamp(uv, float2(0.0), float2(1.0)) * maxIdx;
        float2 i = floor(gridPos);
        float2 f = fract(gridPos);
        float2 u = f * f * (3.0 - 2.0 * f);

        float2 p00 = min(i, float2(maxIdx - 1.0)) + float2(0.5);
        float4 c00 = float4(uFluidMap.eval(p00));
        float4 c10 = float4(uFluidMap.eval(p00 + float2(1.0, 0.0)));
        float4 c01 = float4(uFluidMap.eval(p00 + float2(0.0, 1.0)));
        float4 c11 = float4(uFluidMap.eval(p00 + float2(1.0, 1.0)));

        return mix(mix(c00, c10, u.x), mix(c01, c11, u.x), u.y);
    }

    // twoAxis — source RED drives horizontal, GREEN drives vertical (0.5 = neutral).
    // For premultiplied src: (src.r / a - 0.5) * 2.0 * a == 2.0 * src.r - src.a.
    float2 dmDisplaceRG(float2 uv, float4 src, float aspect, float amount) {
        float dx = (2.0 * src.r - src.a) * amount / aspect;
        float dy = (2.0 * src.g - src.a) * amount;
        return float2(uv.x + dx, uv.y + dy);
    }

    // directional — source LUMINANCE pushes along a fixed angle (0.5 = neutral).
    float2 dmDisplaceLuminance(float2 uv, float4 src, float aspect, float amount, float angleDeg) {
        float lumPremul = dot(src.rgb, float3(0.2126, 0.7152, 0.0722));
        float scalar = (2.0 * lumPremul - src.a) * amount;
        float angleRad = radians(angleDeg);
        float dx = scalar * cos(angleRad) / aspect;
        float dy = scalar * sin(angleRad);
        return float2(uv.x + dx, uv.y + dy);
    }

    float2 edgeClampUV(float2 uv) {
        return clamp(uv, float2(0.0), float2(1.0));
    }

    float2 edgeMirrorUV(float2 uv) {
        float2 m = mod(abs(uv), 2.0);
        return float2(
            m.x >= 1.0 ? 2.0 - m.x : m.x,
            m.y >= 1.0 ? 2.0 - m.y : m.y
        );
    }

    float2 edgeWrapUV(float2 uv) {
        return fract(uv);
    }

    float edgeTransparentMask(float2 uv) {
        float inX = step(0.0, uv.x) * step(uv.x, 1.0);
        float inY = step(0.0, uv.y) * step(uv.y, 1.0);
        return inX * inY;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        float aspect = uResolution.x / max(uResolution.y, 1.0);

        float4 src = sampleFluidField(uv);
        float2 distortedUV = (uChannelMode > 0.5)
            ? dmDisplaceLuminance(uv, src, aspect, uAmount, uAngle)
            : dmDisplaceRG(uv, src, aspect, uAmount);

        if (uEdgeMode > 2.5) {
            return inputShader.eval(edgeWrapUV(distortedUV) * uResolution);
        } else if (uEdgeMode > 1.5) {
            return inputShader.eval(edgeMirrorUV(distortedUV) * uResolution);
        } else if (uEdgeMode > 0.5) {
            half4 sampled = inputShader.eval(edgeClampUV(distortedUV) * uResolution);
            return sampled * half(edgeTransparentMask(distortedUV));
        } else {
            return inputShader.eval(edgeClampUV(distortedUV) * uResolution);
        }
    }
""".trimIndent()
