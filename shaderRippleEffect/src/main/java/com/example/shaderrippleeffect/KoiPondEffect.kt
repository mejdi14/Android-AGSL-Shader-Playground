package com.example.shaderrippleeffect

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pattern varieties for procedural koi in [KoiPondEffect].
 */
enum class KoiVariety(internal val code: Float) {
    /** Classic white body with vermilion-red hi markings. */
    Kohaku(0f),

    /** White base with vermilion-orange markings and ink-black sumi spots. */
    TaishoSanke(1f),

    /** Bold sumi-black and white marbled base with deep crimson patches. */
    ShowaSanshoku(2f),

    /** Shimmering platinum-white and golden-amber yamabuki markings. */
    YamabukiOgon(3f),

    /** Pure pearlescent white body with a prized crimson crown spot on the head. */
    Tancho(4f)
}

/**
 * Interactive procedural Koi Pond simulation powered by runtime mesh triangulation
 * (`Canvas.drawVertices`) and AGSL fragment shaders, inspired by Raymond Bian's
 * procedural WebGL Koi Pond (`raybb.dev/blog/hello-koi`).
 *
 * The simulation is built from four coordinated systems:
 * 1. **Procedural Mesh Generation ([KoiSkeleton])**: Each fish is defined by a spine of
 *    expanding and contracting circle nodes from snout to forked tail. Outer perimeter points
 *    are smoothed with Catmull-Rom splines and triangulated at runtime alongside pectoral,
 *    ventral, and dorsal fins.
 * 2. **Kinematics & Steering Behaviors**: Fish wander autonomously by sampling 2D value noise,
 *    steer smoothly inside pond boundaries, avoid crowding neighbours, and swim toward ripples
 *    or food pellets dropped on tap. Spine nodes follow the node ahead with phase-delayed
 *    sinusoidal undulation.
 * 3. **Shader-Based Texturing**: Triangulated UV coordinates stay anchored to each fish's skin
 *    as it bends. An AGSL [RuntimeShader] evaluates domain-warped fractional Brownian motion
 *    (fBm) Value Noise with `smoothstep` thresholding to paint crisp orange, ink-black, gold,
 *    and ivory splotches, scale shimmer, eyes, and striated translucent fin rays.
 * 4. **Water Surface, Lilypads, Shadows & Gesture Fluid Displacement**: An AGSL water shader
 *    renders a pond depth gradient and static caustic light webs, while each fish and floating
 *    lilypad renders an offset underwater shadow pass before its lit geometry. User gestures
 *    (touch/drag) drive [FluidDisplacementEffect] (`seedInitialImpulse = false`) so fluid
 *    distortion only appears on interaction, with no wave distortion by default.
 *
 * @param modifier Compose modifier applied to the pond container.
 * @param fishCount Number of koi swimming in the pond (`1..16`). Default: `7`.
 * @param swimSpeed Overall swimming speed multiplier (`0.2f..3f`). Default: `1f`.
 * @param waterColorDeep Deep-water base colour at the centre of the pond.
 * @param waterColorShallow Shallow-water tint blended toward the edges and caustics.
 * @param showLilypads Whether to render procedural floating lilypads and a lotus blossom.
 * @param displacementAmount Strength of the gesture-driven fluid displacement (`0f..1f`). Default: `0.15f`.
 * @param waterShaderCode Optional custom AGSL shader code for the water background.
 * @param koiShaderCode Optional custom AGSL shader code for the koi fish pattern.
 */
@Composable
fun KoiPondEffect(
    modifier: Modifier = Modifier,
    fishCount: Int = 7,
    swimSpeed: Float = 1f,
    waterColorDeep: Color = Color(0xFF5BB5DA),
    waterColorShallow: Color = Color(0xFF8BD3F0),
    showLilypads: Boolean = true,
    displacementAmount: Float = 0.15f,
    waterShaderCode: String? = null,
    koiShaderCode: String? = null
) {
    val clampedCount = fishCount.coerceIn(1, 16)
    val speedNow by rememberUpdatedState(swimSpeed.coerceIn(0.2f, 3f))
    val pond = remember(clampedCount) { KoiPondSimulation(clampedCount) }

    val waterShader = remember(waterShaderCode) {
        RuntimeShader(waterShaderCode ?: KOI_WATER_SHADER)
    }
    val waterBrush = remember(waterShader) { ShaderBrush(waterShader) }
    val koiShaders = remember(clampedCount, koiShaderCode) {
        val code = koiShaderCode ?: KOI_FISH_SHADER
        Array(clampedCount) { RuntimeShader(code) }
    }
    val koiPaints = remember(koiShaders) {
        Array(clampedCount) { idx ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = koiShaders[idx] }
        }
    }
    val shadowShader = remember { RuntimeShader(KOI_SHADOW_SHADER) }
    val shadowPaint = remember(shadowShader) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = shadowShader }
    }

    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pond) {
        var lastNanos = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (lastNanos == 0L) {
                1f / 60f
            } else {
                ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            }
            lastNanos = now
            pond.step(dt, speedNow)
            frameTick = now
        }
    }

    FluidDisplacementEffect(
        modifier = modifier,
        amount = displacementAmount,
        seedInitialImpulse = false
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pond) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pond.onTapOrTouchDown(down.position.x, down.position.y)
                        var lastDrag = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val pos = change.position
                            if ((pos - lastDrag).getDistance() > 18f) {
                                pond.onDragMove(pos.x, pos.y)
                                lastDrag = pos
                            }
                        }
                        pond.onTouchUp()
                    }
                }
        ) {
            frameTick // Observe frame updates

            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            pond.ensureBounds(w, h)

            // 1. Base Water & Caustics AGSL Shader (static, no continuous wave animation)
            waterShader.setFloatUniform("uResolution", w, h)
            waterShader.setFloatUniform(
                "uDeepColor",
                waterColorDeep.red,
                waterColorDeep.green,
                waterColorDeep.blue
            )
            waterShader.setFloatUniform(
                "uShallowColor",
                waterColorShallow.red,
                waterColorShallow.green,
                waterColorShallow.blue
            )
            drawRect(brush = waterBrush)

            // 2. Koi Shadows & Textured Koi Meshes via Canvas.drawVertices
            val densityScale = min(w, h) / 420f
            val shadowOffsetX = 18f * densityScale
            val shadowOffsetY = 24f * densityScale

            drawIntoCanvas { composeCanvas ->
                val native = composeCanvas.nativeCanvas

                // Pass A: Underwater fish shadows on the pond bed
                shadowShader.setFloatUniform("uShadowAlpha", 0.24f)
                shadowPaint.shader = shadowShader
                for (i in 0 until pond.koiList.size) {
                    val koi = pond.koiList[i]
                    koi.skeleton.buildShadowVertices(shadowOffsetX, shadowOffsetY)
                    native.drawVertices(
                        NativeCanvas.VertexMode.TRIANGLES,
                        koi.skeleton.vertexFloatCount,
                        koi.skeleton.shadowVerts,
                        0,
                        koi.skeleton.texs,
                        0,
                        null,
                        0,
                        koi.skeleton.indices,
                        0,
                        koi.skeleton.indexCount,
                        shadowPaint
                    )
                }

                // Pass B: Lit procedural koi meshes with per-fish AGSL pattern uniforms
                for (i in 0 until pond.koiList.size) {
                    val koi = pond.koiList[i]
                    val fishShader = koiShaders[i]
                    val fishPaint = koiPaints[i]
                    fishShader.setFloatUniform("uSeed", koi.seedX, koi.seedY)
                    fishShader.setFloatUniform("uVariety", koi.variety.code)
                    fishShader.setFloatUniform(
                        "uWaterTint",
                        waterColorDeep.red,
                        waterColorDeep.green,
                        waterColorDeep.blue
                    )
                    fishPaint.shader = fishShader
                    native.drawVertices(
                        NativeCanvas.VertexMode.TRIANGLES,
                        koi.skeleton.vertexFloatCount,
                        koi.skeleton.verts,
                        0,
                        koi.skeleton.texs,
                        0,
                        null,
                        0,
                        koi.skeleton.indices,
                        0,
                        koi.skeleton.indexCount,
                        fishPaint
                    )
                }
            }

            // 3. Floating food pellets
            for (i in 0 until pond.pellets.size) {
                val pellet = pond.pellets[i]
                if (!pellet.active) continue
                val r = pellet.radius * densityScale
                drawCircle(
                    color = Color(0x55FFF3C4),
                    radius = r * 2.0f,
                    center = Offset(pellet.x, pellet.y)
                )
                drawCircle(
                    color = Color(0xFFF4B961),
                    radius = r,
                    center = Offset(pellet.x, pellet.y)
                )
                drawCircle(
                    color = Color(0xFFFFF8E1),
                    radius = r * 0.45f,
                    center = Offset(pellet.x - r * 0.25f, pellet.y - r * 0.25f)
                )
            }

            // 4. Floating Lilypads & Lotus Blossoms (rendered above the fish so koi swim beneath them)
            if (showLilypads) {
                drawLilypads(pond, shadowOffsetX * 0.85f, shadowOffsetY * 0.85f)
            }
        }
    }
}

/* ══════════════════════════════════════════════════════════════════════════
 * SYSTEM 1: Procedural Mesh Generation (KoiSkeleton & Catmull-Rom Triangulation)
 * ══════════════════════════════════════════════════════════════════════════ */

/**
 * Procedural fish skeleton and triangle mesh generator.
 *
 * The spine consists of [NODE_COUNT] circle nodes from snout (`0`) to tail tip (`NODE_COUNT - 1`).
 * Each frame, follow-the-leader kinematics update the node positions, and [triangulate] walks
 * the Catmull-Rom smoothed spine to populate [verts] and [texs] for `Canvas.drawVertices`.
 */
class KoiSkeleton(
    var baseScale: Float = 1f
) {
    companion object {
        const val NODE_COUNT = 12

        // Longitudinal Catmull-Rom slices along the body & tail
        private const val BODY_SLICES = 28

        // Lateral subdivisions across the fish width (v = 0.0, 0.25, 0.5, 0.75, 1.0)
        private const val BODY_COLS = 4
        private const val BODY_ROW_VERTS = BODY_COLS + 1

        // Fin strip segments (left/right pectoral, left/right ventral, and dorsal fin)
        private const val FIN_SEGS = 6

        // Normalized circle radii along the 12 spine nodes (head -> trunk -> peduncle -> caudal tail)
        private val NODE_RADII = floatArrayOf(
            0.36f, // 0: Snout
            0.78f, // 1: Head / Eyes
            0.98f, // 2: Gill cover
            1.00f, // 3: Shoulder (widest)
            0.91f, // 4: Anterior trunk
            0.78f, // 5: Mid trunk
            0.63f, // 6: Posterior trunk
            0.46f, // 7: Pre-caudal taper
            0.30f, // 8: Caudal peduncle (narrow waist)
            0.24f, // 9: Tail base
            0.58f, // 10: Mid caudal fin flare
            0.92f  // 11: Caudal fin tips
        )
    }

    val nodeX = FloatArray(NODE_COUNT)
    val nodeY = FloatArray(NODE_COUNT)
    val nodeAngle = FloatArray(NODE_COUNT)

    // Pre-allocated spline sample buffers
    private val splineX = FloatArray(BODY_SLICES + 1)
    private val splineY = FloatArray(BODY_SLICES + 1)
    private val splineNx = FloatArray(BODY_SLICES + 1)
    private val splineNy = FloatArray(BODY_SLICES + 1)
    private val splineTx = FloatArray(BODY_SLICES + 1)
    private val splineTy = FloatArray(BODY_SLICES + 1)
    private val splineRadius = FloatArray(BODY_SLICES + 1)

    // Vertex counts:
    // - Body mesh: (BODY_SLICES + 1) * BODY_ROW_VERTS
    // - 4 Paired fins (2 pectoral + 2 ventral): 4 * (FIN_SEGS + 1) * 2
    // - 1 Dorsal fin ribbon: (FIN_SEGS + 1) * 2
    private val bodyVertCount = (BODY_SLICES + 1) * BODY_ROW_VERTS
    private val finVertsPerFin = (FIN_SEGS + 1) * 2
    private val totalVertices = bodyVertCount + 5 * finVertsPerFin

    val vertexFloatCount: Int = totalVertices * 2
    val verts = FloatArray(vertexFloatCount)
    val shadowVerts = FloatArray(vertexFloatCount)
    val texs = FloatArray(vertexFloatCount)

    private val bodyTriCount = BODY_SLICES * BODY_COLS * 2
    private val finTriCount = 5 * FIN_SEGS * 2
    val indexCount: Int = (bodyTriCount + finTriCount) * 3
    val indices = ShortArray(indexCount)

    var swimPhase: Float = 0f
    var turnCurvature: Float = 0f

    init {
        buildStaticIndicesAndUVs()
    }

    /** Places all spine nodes in a gentle initial curve oriented along [heading]. */
    fun resetAt(startX: Float, startY: Float, heading: Float, segLen: Float) {
        val dx = -cos(heading)
        val dy = -sin(heading)
        for (i in 0 until NODE_COUNT) {
            nodeX[i] = startX + dx * segLen * i
            nodeY[i] = startY + dy * segLen * i
            nodeAngle[i] = heading
        }
        triangulate(baseRadius = segLen * 1.15f)
    }

    /**
     * Follow-the-leader kinematics with active sinusoidal swimming wave and bend limits.
     */
    fun updateKinematics(
        headX: Float,
        headY: Float,
        heading: Float,
        speed: Float,
        dt: Float,
        segLen: Float,
        baseRadius: Float
    ) {
        val swimFreq = 4.2f + (speed / max(segLen * 8f, 1f)) * 4.5f
        swimPhase += swimFreq * dt

        nodeX[0] = headX
        nodeY[0] = headY
        nodeAngle[0] = heading

        var accumTurn = 0f
        for (i in 1 until NODE_COUNT) {
            val prevX = nodeX[i - 1]
            val prevY = nodeY[i - 1]
            val prevHeading = nodeAngle[i - 1]

            // Travelling sinusoidal wave down the spine, stronger toward the tail
            val t = i.toFloat() / (NODE_COUNT - 1)
            val waveEnvelope = t * t * (0.22f + 0.26f * t)
            val waveAngleOffset = sin(swimPhase - i * 0.58f) * waveEnvelope

            val dx = prevX - nodeX[i]
            val dy = prevY - nodeY[i]
            val rawAngle = atan2(dy, dx)

            // Blend follow-the-leader angle with active swimming wave and clamp sharp bends
            var diff = normalizeAngle(rawAngle - prevHeading)
            val maxBend = if (i < 8) 0.34f else 0.46f
            diff = (diff * 0.86f + waveAngleOffset * 0.28f).coerceIn(-maxBend, maxBend)

            val segAngle = normalizeAngle(prevHeading + diff)
            nodeAngle[i] = segAngle
            if (i in 1..6) accumTurn += diff

            val spacing = if (i >= 9) segLen * 1.12f else segLen
            nodeX[i] = prevX - cos(segAngle) * spacing
            nodeY[i] = prevY - sin(segAngle) * spacing
        }
        turnCurvature = (accumTurn / 6f).coerceIn(-0.8f, 0.8f)

        triangulate(baseRadius)
    }

    /**
     * Walks the Catmull-Rom smoothed spine and generates vertex positions for the body,
     * forked tail, pectoral fins, ventral fins, and dorsal fin.
     */
    fun triangulate(baseRadius: Float) {
        val lastIdx = NODE_COUNT - 1

        // 1. Sample Catmull-Rom spline along the spine nodes
        for (s in 0..BODY_SLICES) {
            val u = s.toFloat() / BODY_SLICES
            val scaled = u * lastIdx
            val i1 = scaled.toInt().coerceIn(0, lastIdx)
            val f = scaled - i1
            val i0 = (i1 - 1).coerceAtLeast(0)
            val i2 = (i1 + 1).coerceAtMost(lastIdx)
            val i3 = (i1 + 2).coerceAtMost(lastIdx)

            splineX[s] = catmullRom(nodeX[i0], nodeX[i1], nodeX[i2], nodeX[i3], f)
            splineY[s] = catmullRom(nodeY[i0], nodeY[i1], nodeY[i2], nodeY[i3], f)

            val r = catmullRom(
                NODE_RADII[i0],
                NODE_RADII[i1],
                NODE_RADII[i2],
                NODE_RADII[i3],
                f
            ).coerceAtLeast(0.12f)
            splineRadius[s] = r * baseRadius
        }

        // 2. Compute smooth tangents and normals along the sampled spine
        for (s in 0..BODY_SLICES) {
            val sPrev = (s - 1).coerceAtLeast(0)
            val sNext = (s + 1).coerceAtMost(BODY_SLICES)
            var tx = splineX[sNext] - splineX[sPrev]
            var ty = splineY[sNext] - splineY[sPrev]
            val len = hypot(tx, ty)
            if (len > 1e-4f) {
                tx /= len
                ty /= len
            } else {
                tx = -cos(nodeAngle[0])
                ty = -sin(nodeAngle[0])
            }
            // Tangent points from snout toward tail; normal points from right to left flank
            splineTx[s] = tx
            splineTy[s] = ty
            splineNx[s] = -ty
            splineNy[s] = tx
        }

        // 3. Emit body & tail vertices across lateral columns (v = 0..1)
        // Extend snout slightly ahead of slice 0 so the head cap rounds smoothly
        val snoutLead = baseRadius * 0.35f
        for (s in 0..BODY_SLICES) {
            val u = s.toFloat() / BODY_SLICES
            val lead = if (s == 0) -snoutLead else if (s == 1) -snoutLead * 0.35f else 0f
            val cx = splineX[s] + splineTx[s] * lead
            val cy = splineY[s] + splineTy[s] * lead
            val nx = splineNx[s]
            val ny = splineNy[s]
            val rad = splineRadius[s]

            // Subtle tail flutter offset
            val tailSway = if (u > 0.72f) {
                val k = (u - 0.72f) / 0.28f
                sin(swimPhase * 1.15f - u * 6.5f) * baseRadius * 0.14f * k * k
            } else {
                0f
            }

            val rowBase = s * BODY_ROW_VERTS
            for (c in 0..BODY_COLS) {
                val v = c.toFloat() / BODY_COLS
                val side = (v - 0.5f) * 2f // -1 (left flank) .. +1 (right flank)
                val vIdx = (rowBase + c) * 2
                verts[vIdx] = cx + nx * (side * rad + tailSway)
                verts[vIdx + 1] = cy + ny * (side * rad + tailSway)
            }
        }

        // 4. Left & Right Pectoral Fins (attached near shoulder slice s = 6)
        val pecSlice = 6
        val pecOscL = sin(swimPhase - 0.6f) * 0.18f
        val pecOscR = -sin(swimPhase - 0.6f) * 0.18f
        buildPairedFin(
            vertStart = bodyVertCount,
            anchorSlice = pecSlice,
            sideSign = -1f,
            finLength = baseRadius * 1.55f,
            finWidth = baseRadius * 0.82f,
            sweepAngle = 0.92f + pecOscL - turnCurvature * 0.25f
        )
        buildPairedFin(
            vertStart = bodyVertCount + finVertsPerFin,
            anchorSlice = pecSlice,
            sideSign = 1f,
            finLength = baseRadius * 1.55f,
            finWidth = baseRadius * 0.82f,
            sweepAngle = 0.92f + pecOscR + turnCurvature * 0.25f
        )

        // 5. Left & Right Ventral / Pelvic Fins (attached near mid-body slice s = 13)
        val venSlice = 13
        val venOsc = sin(swimPhase - 1.7f) * 0.14f
        buildPairedFin(
            vertStart = bodyVertCount + 2 * finVertsPerFin,
            anchorSlice = venSlice,
            sideSign = -1f,
            finLength = baseRadius * 0.95f,
            finWidth = baseRadius * 0.52f,
            sweepAngle = 1.05f + venOsc
        )
        buildPairedFin(
            vertStart = bodyVertCount + 3 * finVertsPerFin,
            anchorSlice = venSlice,
            sideSign = 1f,
            finLength = baseRadius * 0.95f,
            finWidth = baseRadius * 0.52f,
            sweepAngle = 1.05f - venOsc
        )

        // 6. Dorsal Fin Ribbon (along slices 8..18)
        buildDorsalFin(
            vertStart = bodyVertCount + 4 * finVertsPerFin,
            startSlice = 8,
            endSlice = 18,
            crestSway = baseRadius * 0.52f
        )
    }

    private fun buildPairedFin(
        vertStart: Int,
        anchorSlice: Int,
        sideSign: Float,
        finLength: Float,
        finWidth: Float,
        sweepAngle: Float
    ) {
        val ax = splineX[anchorSlice]
        val ay = splineY[anchorSlice]
        val tx = splineTx[anchorSlice]
        val ty = splineTy[anchorSlice]
        val nx = splineNx[anchorSlice] * sideSign
        val ny = splineNy[anchorSlice] * sideSign
        val bodyR = splineRadius[anchorSlice] * 0.62f

        // Root of the fin sits slightly inside the body silhouette for a seamless join
        val rootX = ax + nx * bodyR
        val rootY = ay + ny * bodyR

        // Primary fin axis points outward along normal and backward along tangent
        val cosA = cos(sweepAngle)
        val sinA = sin(sweepAngle)
        val dirX = nx * sinA + tx * cosA
        val dirY = ny * sinA + ty * cosA
        // Perpendicular fan axis in the fin plane
        val perpX = -tx * sinA + nx * cosA
        val perpY = -ty * sinA + ny * cosA

        for (i in 0..FIN_SEGS) {
            val u = i.toFloat() / FIN_SEGS
            // Curved teardrop fan profile
            val reach = sin(u * PI.toFloat() * 0.5f) * finLength
            val spread = sin(u * PI.toFloat()) * finWidth
            val waveFlutter = sin(swimPhase - u * 2.4f) * finWidth * 0.14f * u

            val cx = rootX + dirX * reach + perpX * waveFlutter
            val cy = rootY + dirY * reach + perpY * waveFlutter

            val vIdx = (vertStart + i * 2) * 2
            // Leading edge (v = 0)
            verts[vIdx] = cx + perpX * (spread * 0.55f) - tx * (finWidth * 0.18f * u)
            verts[vIdx + 1] = cy + perpY * (spread * 0.55f) - ty * (finWidth * 0.18f * u)
            // Trailing edge (v = 1)
            verts[vIdx + 2] = cx - perpX * (spread * 0.65f) + tx * (finWidth * 0.28f * u)
            verts[vIdx + 3] = cy - perpY * (spread * 0.65f) + ty * (finWidth * 0.28f * u)
        }
    }

    private fun buildDorsalFin(
        vertStart: Int,
        startSlice: Int,
        endSlice: Int,
        crestSway: Float
    ) {
        for (i in 0..FIN_SEGS) {
            val t = i.toFloat() / FIN_SEGS
            val sliceF = startSlice + t * (endSlice - startSlice)
            val s0 = sliceF.toInt().coerceIn(0, BODY_SLICES - 1)
            val s1 = (s0 + 1).coerceAtMost(BODY_SLICES)
            val frac = sliceF - s0

            val bx = splineX[s0] + (splineX[s1] - splineX[s0]) * frac
            val by = splineY[s0] + (splineY[s1] - splineY[s0]) * frac
            val nx = splineNx[s0] + (splineNx[s1] - splineNx[s0]) * frac
            val ny = splineNy[s0] + (splineNy[s1] - splineNy[s0]) * frac
            val tx = splineTx[s0] + (splineTx[s1] - splineTx[s0]) * frac
            val ty = splineTy[s0] + (splineTy[s1] - splineTy[s0]) * frac

            val arch = sin(t * PI.toFloat())
            val sway = (sin(swimPhase - t * 3.2f) * 0.45f + turnCurvature * 0.9f) * crestSway * arch

            val vIdx = (vertStart + i * 2) * 2
            // Base along the dorsal spine (v = 0)
            verts[vIdx] = bx
            verts[vIdx + 1] = by
            // Flowing dorsal crest (v = 1)
            verts[vIdx + 2] = bx + nx * sway + tx * (crestSway * 0.35f * arch)
            verts[vIdx + 3] = by + ny * sway + ty * (crestSway * 0.35f * arch)
        }
    }

    fun buildShadowVertices(offsetX: Float, offsetY: Float) {
        var i = 0
        while (i < vertexFloatCount) {
            shadowVerts[i] = verts[i] + offsetX
            shadowVerts[i + 1] = verts[i + 1] + offsetY
            i += 2
        }
    }

    /**
     * Populates static triangle indices and UV coordinates once.
     *
     * UVs are scaled by `100f` and partitioned by region along `u`:
     * - `[0..100]`: Body & forked tail (`u` = head..tail, `v` = left..right flank)
     * - `[200..300]`: Paired pectoral & ventral fins
     * - `[400..500]`: Dorsal fin ribbon
     */
    private fun buildStaticIndicesAndUVs() {
        // 1. Body UVs & indices
        for (s in 0..BODY_SLICES) {
            val u = (s.toFloat() / BODY_SLICES) * 100f
            val rowBase = s * BODY_ROW_VERTS
            for (c in 0..BODY_COLS) {
                val v = (c.toFloat() / BODY_COLS) * 100f
                val tIdx = (rowBase + c) * 2
                texs[tIdx] = u
                texs[tIdx + 1] = v
            }
        }

        var idx = 0
        for (s in 0 until BODY_SLICES) {
            val r0 = s * BODY_ROW_VERTS
            val r1 = (s + 1) * BODY_ROW_VERTS
            for (c in 0 until BODY_COLS) {
                val v00 = (r0 + c).toShort()
                val v01 = (r0 + c + 1).toShort()
                val v10 = (r1 + c).toShort()
                val v11 = (r1 + c + 1).toShort()

                indices[idx++] = v00
                indices[idx++] = v10
                indices[idx++] = v01

                indices[idx++] = v01
                indices[idx++] = v10
                indices[idx++] = v11
            }
        }

        // 2. Paired fins (pectoral + ventral) UVs & indices
        for (fin in 0 until 4) {
            val baseVert = bodyVertCount + fin * finVertsPerFin
            for (i in 0..FIN_SEGS) {
                val u = 200f + (i.toFloat() / FIN_SEGS) * 100f
                val tIdx = (baseVert + i * 2) * 2
                texs[tIdx] = u
                texs[tIdx + 1] = 0f
                texs[tIdx + 2] = u
                texs[tIdx + 3] = 100f
            }
            for (i in 0 until FIN_SEGS) {
                val v00 = (baseVert + i * 2).toShort()
                val v01 = (baseVert + i * 2 + 1).toShort()
                val v10 = (baseVert + (i + 1) * 2).toShort()
                val v11 = (baseVert + (i + 1) * 2 + 1).toShort()

                indices[idx++] = v00
                indices[idx++] = v10
                indices[idx++] = v01

                indices[idx++] = v01
                indices[idx++] = v10
                indices[idx++] = v11
            }
        }

        // 3. Dorsal fin UVs & indices
        val dorsalBase = bodyVertCount + 4 * finVertsPerFin
        for (i in 0..FIN_SEGS) {
            val u = 400f + (i.toFloat() / FIN_SEGS) * 100f
            val tIdx = (dorsalBase + i * 2) * 2
            texs[tIdx] = u
            texs[tIdx + 1] = 0f
            texs[tIdx + 2] = u
            texs[tIdx + 3] = 100f
        }
        for (i in 0 until FIN_SEGS) {
            val v00 = (dorsalBase + i * 2).toShort()
            val v01 = (dorsalBase + i * 2 + 1).toShort()
            val v10 = (dorsalBase + (i + 1) * 2).toShort()
            val v11 = (dorsalBase + (i + 1) * 2 + 1).toShort()

            indices[idx++] = v00
            indices[idx++] = v10
            indices[idx++] = v01

            indices[idx++] = v01
            indices[idx++] = v10
            indices[idx++] = v11
        }
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            (2f * p1) +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }
}

/* ══════════════════════════════════════════════════════════════════════════
 * SYSTEM 2: Kinematics & Steering Behaviors (Boids + Noise Wandering)
 * ══════════════════════════════════════════════════════════════════════════ */

internal class KoiAgent(
    val variety: KoiVariety,
    val seedX: Float,
    val seedY: Float,
    val sizeScale: Float,
    val speedScale: Float,
    val wanderOffset: Float
) {
    val skeleton = KoiSkeleton(sizeScale)
    var x = 0f
    var y = 0f
    var heading = 0f
    var angularVel = 0f
    var speed = 90f
    var boostTimer = 0f
}

internal class FoodPellet(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 3.2f,
    var age: Float = 0f,
    var active: Boolean = false
)

internal class LilypadNode(
    val normX: Float,
    val normY: Float,
    val radiusRatio: Float,
    val baseRotation: Float,
    val notchAngle: Float,
    val hasLotus: Boolean,
    val phase: Float
) {
    var offsetX = 0f
    var offsetY = 0f
    var vx = 0f
    var vy = 0f
}

internal class KoiPondSimulation(private val count: Int) {
    companion object {
        const val MAX_PELLETS = 10
    }

    var width = 0f
    var height = 0f
    var time = 0f
    private var initialized = false

    val koiList = ArrayList<KoiAgent>(count)

    val pellets = Array(MAX_PELLETS) { FoodPellet() }
    private var nextPelletIdx = 0

    private var touchActive = false
    private var touchX = 0f
    private var touchY = 0f

    val lilypads = listOf(
        LilypadNode(0.18f, 0.22f, 0.125f, 25f, 38f, hasLotus = true, phase = 0.4f),
        LilypadNode(0.28f, 0.27f, 0.082f, 140f, 155f, hasLotus = false, phase = 1.9f),
        LilypadNode(0.82f, 0.76f, 0.132f, 210f, 225f, hasLotus = true, phase = 3.1f),
        LilypadNode(0.73f, 0.82f, 0.088f, 315f, 330f, hasLotus = false, phase = 4.5f),
        LilypadNode(0.84f, 0.20f, 0.095f, 80f, 95f, hasLotus = false, phase = 2.6f)
    )

    init {
        val rnd = Random(42)
        val varieties = KoiVariety.entries
        for (i in 0 until count) {
            val variety = varieties[i % varieties.size]
            val scale = 0.84f + (i % 4) * 0.11f + rnd.nextFloat() * 0.06f
            val speedMul = 0.85f + rnd.nextFloat() * 0.35f
            koiList.add(
                KoiAgent(
                    variety = variety,
                    seedX = 11.7f + i * 23.4f + rnd.nextFloat() * 7f,
                    seedY = 5.3f + i * 17.9f + rnd.nextFloat() * 7f,
                    sizeScale = scale,
                    speedScale = speedMul,
                    wanderOffset = i * 13.7f
                )
            )
        }
    }

    fun ensureBounds(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val firstInit = !initialized
        width = w
        height = h
        if (firstInit) {
            initialized = true
            val minDim = min(w, h)
            val baseSeg = (minDim / 420f) * 14.5f
            for (i in 0 until koiList.size) {
                val koi = koiList[i]
                val angle = (i.toFloat() / koiList.size) * (2f * PI.toFloat())
                val rx = w * (0.28f + 0.44f * ((i * 37 % 100) / 100f))
                val ry = h * (0.24f + 0.52f * ((i * 61 % 100) / 100f))
                koi.x = rx
                koi.y = ry
                koi.heading = angle + 0.6f
                koi.skeleton.resetAt(koi.x, koi.y, koi.heading, baseSeg * koi.sizeScale)
            }
        }
    }

    fun onTapOrTouchDown(px: Float, py: Float) {
        touchActive = true
        touchX = px
        touchY = py

        val minDim = min(width, height).coerceAtLeast(1f)
        val scareRadius = minDim * 0.16f
        var startledAny = false
        for (i in 0 until koiList.size) {
            val koi = koiList[i]
            val dx = koi.x - px
            val dy = koi.y - py
            val d = hypot(dx, dy)
            if (d < scareRadius) {
                // Tapped right near a koi: lively tail-flick dart away
                val awayAngle = atan2(dy, dx)
                val diff = normalizeAngle(awayAngle - koi.heading)
                koi.angularVel += diff * 3.5f
                koi.boostTimer = 1.15f
                startledAny = true
            }
        }

        if (!startledAny) {
            // Drop 2 floating food pellets around the tap so koi swim over to feed
            for (k in 0 until 2) {
                val a = (time * 5f + k * 2.4f)
                val r = (k * 10f)
                val p = pellets[nextPelletIdx]
                nextPelletIdx = (nextPelletIdx + 1) % MAX_PELLETS
                p.x = px + cos(a) * r
                p.y = py + sin(a) * r
                p.radius = 2.6f + (k % 2) * 0.8f
                p.age = 0f
                p.active = true
            }
        }
    }

    fun onDragMove(px: Float, py: Float) {
        touchActive = true
        touchX = px
        touchY = py

        // Push nearby lilypads gently when dragging through them
        val minDim = min(width, height).coerceAtLeast(1f)
        for (pad in lilypads) {
            val lx = pad.normX * width + pad.offsetX
            val ly = pad.normY * height + pad.offsetY
            val dx = lx - px
            val dy = ly - py
            val dist = hypot(dx, dy)
            val padR = pad.radiusRatio * minDim * 1.4f
            if (dist < padR && dist > 1f) {
                val push = (1f - dist / padR) * 14f
                pad.vx += (dx / dist) * push
                pad.vy += (dy / dist) * push
            }
        }
    }

    fun onTouchUp() {
        touchActive = false
    }

    fun step(dt: Float, speedMultiplier: Float) {
        time += dt
        if (!initialized || width <= 0f || height <= 0f) return

        // Update floating pellets
        for (i in 0 until MAX_PELLETS) {
            val p = pellets[i]
            if (p.active) {
                p.age += dt
                if (p.age > 14f) p.active = false
            }
        }

        val minDim = min(width, height)
        val scaleUnit = minDim / 420f
        val baseSegLen = 14.2f * scaleUnit
        val baseCruiseSpeed = 74f * scaleUnit * speedMultiplier

        // Update lilypads spring physics
        for (pad in lilypads) {
            pad.vx = (pad.vx - pad.offsetX * 3.5f * dt) * (1f - 2.4f * dt).coerceAtLeast(0.8f)
            pad.vy = (pad.vy - pad.offsetY * 3.5f * dt) * (1f - 2.4f * dt).coerceAtLeast(0.8f)
            pad.offsetX += pad.vx * dt * 12f
            pad.offsetY += pad.vy * dt * 12f
        }

        // Update each koi's steering & spine kinematics
        for (i in 0 until koiList.size) {
            val koi = koiList[i]
            if (koi.boostTimer > 0f) {
                koi.boostTimer = (koi.boostTimer - dt).coerceAtLeast(0f)
            }

            // 1. 2D Noise wandering force
            val noiseSample = sampleNoise1D(time * 0.42f + koi.wanderOffset)
            var steerTorque = noiseSample * 1.65f

            // 2. Seeking nearest active food pellet
            var nearestPellet: FoodPellet? = null
            var nearestPelletDist = minDim * 0.65f
            for (pIdx in 0 until MAX_PELLETS) {
                val p = pellets[pIdx]
                if (!p.active) continue
                val d = hypot(p.x - koi.x, p.y - koi.y)
                if (d < nearestPelletDist) {
                    nearestPelletDist = d
                    nearestPellet = p
                }
            }
            var targetSpeedMul = 1f
            if (nearestPellet != null) {
                val desiredAngle = atan2(nearestPellet.y - koi.y, nearestPellet.x - koi.x)
                val diff = normalizeAngle(desiredAngle - koi.heading)
                steerTorque += diff * 2.8f
                targetSpeedMul = 1.32f
                // Eat the pellet if head reaches it
                if (nearestPelletDist < 22f * scaleUnit) {
                    nearestPellet.active = false
                    koi.boostTimer = 0.45f
                }
            }

            // 3. Gentle interaction with active drag finger
            if (touchActive) {
                val dx = touchX - koi.x
                val dy = touchY - koi.y
                val d = hypot(dx, dy)
                if (d < minDim * 0.12f && d > 1f) {
                    // Gently part around finger when very close
                    val away = atan2(-dy, -dx)
                    steerTorque += normalizeAngle(away - koi.heading) * 3.2f
                } else if (d < minDim * 0.42f) {
                    // Curiously orbit toward finger current at medium range
                    val toward = atan2(dy, dx)
                    steerTorque += normalizeAngle(toward - koi.heading) * 1.4f
                }
            }

            // 4. Boids separation from neighbouring koi
            val sepRadius = 90f * scaleUnit
            for (j in 0 until koiList.size) {
                if (i == j) continue
                val other = koiList[j]
                val dx = koi.x - other.x
                val dy = koi.y - other.y
                val d = hypot(dx, dy)
                if (d < sepRadius && d > 1f) {
                    val awayAngle = atan2(dy, dx)
                    val weight = (1f - d / sepRadius)
                    steerTorque += normalizeAngle(awayAngle - koi.heading) * weight * 2.6f
                }
            }

            // 5. Soft pond boundary containment (steers smoothly back toward center)
            val marginX = width * 0.13f
            val marginY = height * 0.13f
            if (koi.x < marginX || koi.x > width - marginX ||
                koi.y < marginY || koi.y > height - marginY
            ) {
                val centerAngle = atan2(height * 0.5f - koi.y, width * 0.5f - koi.x)
                val diff = normalizeAngle(centerAngle - koi.heading)
                val urgencyX = max(0f, max(marginX - koi.x, koi.x - (width - marginX))) / marginX
                val urgencyY = max(0f, max(marginY - koi.y, koi.y - (height - marginY))) / marginY
                val urgency = (urgencyX + urgencyY).coerceIn(0.25f, 2.2f)
                steerTorque += diff * 3.4f * urgency
            }

            // Integrate angular velocity and heading
            koi.angularVel = (koi.angularVel + steerTorque * dt * 4.5f) * (1f - 3.6f * dt).coerceAtLeast(0.6f)
            koi.angularVel = koi.angularVel.coerceIn(-3.2f, 3.2f)
            koi.heading = normalizeAngle(koi.heading + koi.angularVel * dt)

            // Integrate linear speed (faster during bursts or turns)
            val boostMul = 1f + koi.boostTimer * 1.15f
            val desiredSpeed = baseCruiseSpeed * koi.speedScale * targetSpeedMul * boostMul
            koi.speed += (desiredSpeed - koi.speed) * (dt * 4.0f).coerceAtMost(1f)

            koi.x += cos(koi.heading) * koi.speed * dt
            koi.y += sin(koi.heading) * koi.speed * dt

            // Safety wrap if screen resized dramatically
            koi.x = koi.x.coerceIn(-width * 0.15f, width * 1.15f)
            koi.y = koi.y.coerceIn(-height * 0.15f, height * 1.15f)

            val segLen = baseSegLen * koi.sizeScale
            val bodyRadius = segLen * 1.24f
            koi.skeleton.updateKinematics(
                headX = koi.x,
                headY = koi.y,
                heading = koi.heading,
                speed = koi.speed,
                dt = dt,
                segLen = segLen,
                baseRadius = bodyRadius
            )
        }
    }

    /** Smooth multi-sine 1D value noise in `[-1, 1]` for organic meandering. */
    private fun sampleNoise1D(t: Float): Float {
        return (sin(t * 1.3f) * 0.5f + sin(t * 2.7f + 1.4f) * 0.32f + cos(t * 0.7f - 0.8f) * 0.18f)
    }
}

private fun normalizeAngle(a: Float): Float {
    var angle = a % (2f * PI.toFloat())
    if (angle > PI.toFloat()) angle -= 2f * PI.toFloat()
    if (angle < -PI.toFloat()) angle += 2f * PI.toFloat()
    return angle
}

/* ══════════════════════════════════════════════════════════════════════════
 * FLOATING LILYPADS & LOTUS BLOSSOMS
 * ══════════════════════════════════════════════════════════════════════════ */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLilypads(
    pond: KoiPondSimulation,
    shadowDx: Float,
    shadowDy: Float
) {
    val w = size.width
    val h = size.height
    val minDim = min(w, h)

    for (pad in pond.lilypads) {
        val cx = pad.normX * w + pad.offsetX
        val cy = pad.normY * h + pad.offsetY
        val r = pad.radiusRatio * minDim
        val rot = pad.baseRotation
        val notchStart = (pad.notchAngle + rot) % 360f
        val sweep = 332f

        // 1. Underwater shadow cast by the lilypad onto the water/koi below
        drawArc(
            color = Color(0x361B526B),
            startAngle = notchStart + 14f,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(cx + shadowDx - r, cy + shadowDy - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f)
        )

        // 2. Outer rim & leaf body
        drawArc(
            color = Color(0xFF285E48),
            startAngle = notchStart + 14f,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(cx - r, cy - r),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f)
        )
        val innerR = r * 0.92f
        drawArc(
            color = Color(0xFF3A7D5C),
            startAngle = notchStart + 15f,
            sweepAngle = sweep - 2f,
            useCenter = true,
            topLeft = Offset(cx - innerR, cy - innerR),
            size = androidx.compose.ui.geometry.Size(innerR * 2f, innerR * 2f)
        )

        // 3. Radial botanical veins
        val veinCount = 9
        for (v in 0 until veinCount) {
            val angleDeg = notchStart + 28f + v * ((sweep - 28f) / (veinCount - 1))
            val rad = angleDeg * (PI.toFloat() / 180f)
            drawLine(
                color = Color(0x5568B088),
                start = Offset(cx, cy),
                end = Offset(cx + cos(rad) * (r * 0.86f), cy + sin(rad) * (r * 0.86f)),
                strokeWidth = (r * 0.032f).coerceAtLeast(1.2f)
            )
        }
        drawCircle(
            color = Color(0xFF539B73),
            radius = r * 0.08f,
            center = Offset(cx, cy)
        )

        // 4. Blooming lotus flower on selected pads
        if (pad.hasLotus) {
            val lotusX = cx + r * 0.14f
            val lotusY = cy - r * 0.1f
            val petalLen = r * 0.42f
            val petalCount = 8

            // Soft lotus drop shadow
            drawCircle(
                color = Color(0x33071E26),
                radius = petalLen * 1.05f,
                center = Offset(lotusX + 4f, lotusY + 5f)
            )

            // Outer blush-pink petals
            for (p in 0 until petalCount) {
                val a = (p * (360f / petalCount) + rot * 0.5f) * (PI.toFloat() / 180f)
                val px = lotusX + cos(a) * (petalLen * 0.52f)
                val py = lotusY + sin(a) * (petalLen * 0.52f)
                drawCircle(
                    color = Color(0xFFF39BB2),
                    radius = petalLen * 0.36f,
                    center = Offset(px, py)
                )
            }
            // Inner ivory-pink petals
            for (p in 0 until petalCount) {
                val a = ((p + 0.5f) * (360f / petalCount) + rot * 0.5f) * (PI.toFloat() / 180f)
                val px = lotusX + cos(a) * (petalLen * 0.32f)
                val py = lotusY + sin(a) * (petalLen * 0.32f)
                drawCircle(
                    color = Color(0xFFFCE4EC),
                    radius = petalLen * 0.26f,
                    center = Offset(px, py)
                )
            }
            // Golden stamen center
            drawCircle(
                color = Color(0xFFF5C044),
                radius = petalLen * 0.19f,
                center = Offset(lotusX, lotusY)
            )
        }
    }
}

/* ══════════════════════════════════════════════════════════════════════════
 * SYSTEM 3: AGSL Koi Pattern & Fin Fragment Shaders
 * ══════════════════════════════════════════════════════════════════════════ */

private const val KOI_FISH_SHADER = """
    uniform float2 uSeed;
    uniform float uVariety;
    uniform float3 uWaterTint;

    float hash21(float2 p) {
        float3 p3 = fract(float3(p.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float valueNoise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        float a = hash21(i);
        float b = hash21(i + float2(1.0, 0.0));
        float c = hash21(i + float2(0.0, 1.0));
        float d = hash21(i + float2(1.0, 1.0));
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    float fbm(float2 p) {
        float v = 0.0;
        float amp = 0.5;
        float2 shift = float2(37.2, 19.8);
        for (int i = 0; i < 3; i++) {
            v += amp * valueNoise(p);
            p = p * 2.03 + shift;
            amp *= 0.5;
        }
        return v;
    }

    half4 main(float2 coord) {
        // Region 2: Paired Pectoral & Ventral Fins (coord.x in [200..300])
        if (coord.x >= 150.0 && coord.x < 350.0) {
            float u = clamp((coord.x - 200.0) * 0.01, 0.0, 1.0);
            float v = clamp(coord.y * 0.01, 0.0, 1.0);

            // Rounded outer fin contour
            float tipMask = smoothstep(1.0, 0.72, u);
            float sideMask = smoothstep(0.0, 0.18, v) * smoothstep(1.0, 0.78, v);
            float rootMask = smoothstep(0.0, 0.08, u);
            float rays = 0.78 + 0.22 * sin(v * 38.0 + u * 6.0);
            float alpha = tipMask * sideMask * rootMask * (0.68 - 0.32 * u) * rays;

            float3 finColor = mix(float3(0.98, 0.95, 0.91), uWaterTint + float3(0.35, 0.38, 0.40), 0.26 * u);
            if (uVariety > 2.5 && uVariety < 3.5) {
                // Golden tint for Yamabuki Ogon fins
                finColor = mix(finColor, float3(0.98, 0.82, 0.36), 0.45 * (1.0 - u));
            }
            return half4(half3(finColor * alpha), half(alpha));
        }

        // Region 3: Dorsal Fin Ribbon (coord.x >= 350)
        if (coord.x >= 350.0) {
            float u = clamp((coord.x - 400.0) * 0.01, 0.0, 1.0);
            float v = clamp(coord.y * 0.01, 0.0, 1.0);

            float endMask = smoothstep(0.0, 0.14, u) * smoothstep(1.0, 0.84, u);
            float crestMask = smoothstep(1.0, 0.55, v);
            float rays = 0.80 + 0.20 * sin(u * 44.0);
            float alpha = endMask * crestMask * 0.58 * rays;
            float3 dorsalCol = mix(float3(0.96, 0.93, 0.89), float3(0.92, 0.45, 0.22), 0.25 * (1.0 - v));
            return half4(half3(dorsalCol * alpha), half(alpha));
        }

        // Region 1: Koi Body & Forked Caudal Tail (coord.x in [0..100], coord.y in [0..100])
        float u = clamp(coord.x * 0.01, 0.0, 1.0);
        float v = clamp(coord.y * 0.01, 0.0, 1.0);
        float distFromSpine = abs(v - 0.5) * 2.0; // 0 at dorsal spine, 1 at outer flanks

        // Rounded snout cap at u -> 0
        float snoutCap = 1.0;
        if (u < 0.09) {
            float nu = (0.09 - u) / 0.09;
            float maxLat = sqrt(max(0.0, 1.0 - nu * nu));
            snoutCap = 1.0 - smoothstep(maxLat - 0.14, maxLat + 0.02, distFromSpine);
        }

        // Smooth anti-aliased lateral silhouette
        float edgeAA = (1.0 - smoothstep(0.86, 1.0, distFromSpine)) * snoutCap;
        if (edgeAA <= 0.001) {
            return half4(0.0);
        }

        // Caudal tail transition (u > 0.72): forked bilobed tail & translucent fin rays
        if (u > 0.72) {
            float tu = (u - 0.72) / 0.28; // 0 at peduncle .. 1 at tail tip
            // Fork notch in the center (distFromSpine near 0) deepens as tu approaches 1
            float notchWidth = smoothstep(0.32, 1.0, tu) * 0.54;
            float lobeOuter = 1.0 - 0.18 * tu * tu;
            float inNotch = smoothstep(notchWidth + 0.12, max(0.0, notchWidth - 0.04), distFromSpine);
            float outerTrim = 1.0 - smoothstep(lobeOuter - 0.12, lobeOuter, distFromSpine);
            float tipFade = 1.0 - smoothstep(0.75, 1.0, tu);

            float rayPattern = 0.76 + 0.24 * sin(distFromSpine * 46.0 - tu * 8.0);
            float tailAlpha = edgeAA * (1.0 - inNotch) * outerTrim * (0.85 - 0.55 * tu) * (0.4 + 0.6 * tipFade) * rayPattern;

            float3 tailBase = float3(0.97, 0.94, 0.90);
            if (uVariety > 2.5 && uVariety < 3.5) {
                tailBase = mix(tailBase, float3(0.96, 0.78, 0.28), 0.55 * (1.0 - tu));
            } else {
                tailBase = mix(tailBase, float3(0.92, 0.36, 0.18), 0.30 * (1.0 - tu));
            }
            float3 tailCol = mix(tailBase, uWaterTint + float3(0.2, 0.25, 0.28), 0.22);
            return half4(half3(tailCol * tailAlpha), half(tailAlpha));
        }

        // Body coordinates normalized along trunk uBody in [0..1]
        float uBody = u / 0.72;

        // Domain-warped Value Noise / fBm for authentic organic Koi splotches
        float2 pNoise = float2(uBody * 4.4, v * 2.4) + uSeed;
        float2 warp = float2(
            fbm(pNoise + float2(1.7, 9.2)),
            fbm(pNoise + float2(8.3, 2.8))
        );
        float nPrimary = fbm(pNoise + (warp - 0.5) * 1.35);
        float nSecondary = fbm(pNoise * 1.65 - warp * 0.9 + float2(14.2, 6.7));

        // Base Koi palette colours
        float3 ivoryWhite = float3(0.97, 0.95, 0.91);
        float3 vermilion = mix(float3(0.91, 0.26, 0.12), float3(0.96, 0.43, 0.15), warp.x);
        float3 sumiBlack = float3(0.11, 0.13, 0.16);
        float3 yamabukiGold = mix(float3(0.94, 0.65, 0.14), float3(0.98, 0.82, 0.28), warp.y);

        float3 skinColor = ivoryWhite;

        if (uVariety < 0.5) {
            // 0: Kohaku (White + Vermilion-Red patches)
            float hiMask = smoothstep(0.47, 0.51, nPrimary + 0.08 * (1.0 - distFromSpine));
            skinColor = mix(ivoryWhite, vermilion, hiMask);
        } else if (uVariety < 1.5) {
            // 1: Taisho Sanke (White + Vermilion + Sumi Black spots)
            float hiMask = smoothstep(0.48, 0.52, nPrimary + 0.06 * (1.0 - distFromSpine));
            float sumiMask = smoothstep(0.61, 0.65, nSecondary);
            skinColor = mix(ivoryWhite, vermilion, hiMask);
            skinColor = mix(skinColor, sumiBlack, sumiMask);
        } else if (uVariety < 2.5) {
            // 2: Showa Sanshoku (Black & White base with bold Crimson patches)
            float hiMask = smoothstep(0.50, 0.54, nPrimary);
            float sumiMask = smoothstep(0.46, 0.50, nSecondary);
            skinColor = mix(ivoryWhite, vermilion, hiMask);
            skinColor = mix(skinColor, sumiBlack, sumiMask * (1.0 - 0.35 * hiMask));
        } else if (uVariety < 3.5) {
            // 3: Yamabuki Ogon (Platinum White & Golden-Amber patches)
            float goldMask = smoothstep(0.44, 0.49, nPrimary + 0.12 * (1.0 - distFromSpine));
            skinColor = mix(ivoryWhite, yamabukiGold, goldMask);
        } else {
            // 4: Tancho (Pure White body with prized Crimson crown circle on head)
            float2 crownDelta = float2((uBody - 0.15) * 2.5, (v - 0.5) * 1.45);
            float crownDist = length(crownDelta) + (nPrimary - 0.5) * 0.08;
            float crownMask = 1.0 - smoothstep(0.21, 0.25, crownDist);
            skinColor = mix(ivoryWhite, float3(0.89, 0.18, 0.12), crownMask);
        }

        // Subtle diamond scale lattice along the trunk (behind the gill line uBody > 0.22)
        if (uBody > 0.20) {
            float headBlend = smoothstep(0.20, 0.28, uBody);
            float2 scaleUV = float2(uBody * 26.0, (v - 0.5) * 14.0);
            float row = floor(scaleUV.y);
            scaleUV.x += fract(row * 0.5);
            float2 cell = fract(scaleUV) - 0.5;
            float scaleRim = smoothstep(0.36, 0.48, length(cell));
            skinColor *= 1.0 - 0.055 * scaleRim * headBlend * (1.0 - 0.5 * distFromSpine);
        }

        // Glossy eyes on the left and right sides of the head
        float eyeU = 0.12;
        float2 eyeDelta = float2((uBody - eyeU) * 3.6, (distFromSpine - 0.68) * 1.9);
        float eyeDist = length(eyeDelta);
        if (eyeDist < 0.14) {
            float iris = 1.0 - smoothstep(0.09, 0.13, eyeDist);
            float pupil = 1.0 - smoothstep(0.055, 0.085, eyeDist);
            skinColor = mix(skinColor, float3(0.85, 0.68, 0.25), iris);
            skinColor = mix(skinColor, float3(0.06, 0.07, 0.09), pupil);
        }

        // 3D cylindrical cross-section lighting across the fish back & flanks
        float cylinderNz = sqrt(max(0.0, 1.0 - distFromSpine * distFromSpine));
        float dorsalRidge = exp(-distFromSpine * distFromSpine * 7.5);
        float lighting = 0.74 + 0.28 * cylinderNz + 0.09 * dorsalRidge;
        skinColor *= lighting;

        // Subtle underwater caustic shimmer & water depth tinting
        float causticGlint = valueNoise(float2(uBody * 6.0, v * 4.0) + uSeed * 0.25);
        skinColor += float3(0.05, 0.07, 0.07) * smoothstep(0.62, 0.88, causticGlint) * cylinderNz;
        skinColor = mix(skinColor, uWaterTint + float3(0.18, 0.26, 0.28), 0.12);

        // Gently blend the posterior body into the translucent tail base
        float tailRootFade = 1.0 - 0.15 * smoothstep(0.85, 1.0, uBody);
        float finalAlpha = edgeAA * tailRootFade;

        return half4(half3(skinColor * finalAlpha), half(finalAlpha));
    }
"""

private const val KOI_SHADOW_SHADER = """
    uniform float uShadowAlpha;

    half4 main(float2 coord) {
        // Fin shadows
        if (coord.x >= 150.0 && coord.x < 350.0) {
            float u = clamp((coord.x - 200.0) * 0.01, 0.0, 1.0);
            float v = clamp(coord.y * 0.01, 0.0, 1.0);
            float mask = smoothstep(1.0, 0.5, u) * smoothstep(0.0, 0.25, v) * smoothstep(1.0, 0.75, v);
            float a = mask * uShadowAlpha * 0.45;
            return half4(half3(0.14, 0.36, 0.48) * half(a), half(a));
        }
        if (coord.x >= 350.0) {
            return half4(0.0);
        }

        // Body & tail soft blurred shadow silhouette
        float u = clamp(coord.x * 0.01, 0.0, 1.0);
        float v = clamp(coord.y * 0.01, 0.0, 1.0);
        float distFromSpine = abs(v - 0.5) * 2.0;

        float softEdge = 1.0 - smoothstep(0.25, 1.0, distFromSpine);
        float snoutFade = smoothstep(0.0, 0.08, u);
        float tailFade = 1.0 - 0.65 * smoothstep(0.68, 1.0, u);
        float a = softEdge * snoutFade * tailFade * uShadowAlpha;

        return half4(half3(0.14, 0.36, 0.48) * half(a), half(a));
    }
"""

/* ══════════════════════════════════════════════════════════════════════════
 * SYSTEM 4: Seabed Pebbles, Sand Ripples & Voronoi Caustics AGSL Shader
 * ══════════════════════════════════════════════════════════════════════════ */

private const val KOI_WATER_SHADER = """
    uniform float2 uResolution;
    uniform float3 uDeepColor;
    uniform float3 uShallowColor;

    float hash21(float2 p) {
        float3 p3 = fract(float3(p.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float2 hash22(float2 p) {
        float3 p3 = fract(float3(p.xyx) * float3(0.1031, 0.1030, 0.0973));
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.xx + p3.yz) * p3.zy);
    }

    float valueNoise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        float a = hash21(i);
        float b = hash21(i + float2(1.0, 0.0));
        float c = hash21(i + float2(0.0, 1.0));
        float d = hash21(i + float2(1.0, 1.0));
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    float fbm(float2 p) {
        float v = 0.0;
        float amp = 0.5;
        float2 rot = float2(1.6, 1.2);
        for (int i = 0; i < 3; i++) {
            v += amp * valueNoise(p);
            p = float2(p.x * rot.x - p.y * rot.y, p.x * rot.y + p.y * rot.x) + float2(13.5, 7.1);
            amp *= 0.5;
        }
        return v;
    }

    // Evaluates smooth river pebbles via 2nd-order Voronoi distance (F2 - F1)
    // Returns: x = stone height (0..1), y = cell ID (0..1), zw = 2D dome normal
    float4 pebbleLayer(float2 p) {
        float2 ip = floor(p);
        float2 fp = fract(p);

        float d1 = 8.0;
        float d2 = 8.0;
        float2 bestVec = float2(0.0);
        float bestId = 0.0;

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float2 offset = float2(float(x), float(y));
                float2 cell = ip + offset;
                float2 rnd = hash22(cell);
                float2 center = offset + 0.18 + 0.64 * rnd;
                float2 diff = fp - center;
                // Slight elliptical variation per stone
                float2 stretch = float2(1.0 + (rnd.x - 0.5) * 0.28, 1.0 + (rnd.y - 0.5) * 0.28);
                float dist = length(diff * stretch);

                if (dist < d1) {
                    d2 = d1;
                    d1 = dist;
                    bestVec = diff;
                    bestId = fract(rnd.x * 7.13 + rnd.y * 13.37);
                } else if (dist < d2) {
                    d2 = dist;
                }
            }
        }

        // Smooth rounded border between neighbouring stones
        float edge = clamp((d2 - d1) * 2.4, 0.0, 1.0);
        float height = smoothstep(0.06, 0.58, edge) * clamp(1.0 - d1 * 1.15, 0.0, 1.0);
        float2 normal2D = bestVec / max(d1, 0.001);
        return float4(height, bestId, normal2D.x, normal2D.y);
    }

    // Voronoi F2 - F1 cellular caustic web
    float causticWeb(float2 p) {
        float2 ip = floor(p);
        float2 fp = fract(p);
        float d1 = 8.0;
        float d2 = 8.0;

        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float2 offset = float2(float(x), float(y));
                float2 rnd = hash22(ip + offset);
                float2 pt = offset + 0.15 + 0.70 * rnd;
                float d = length(fp - pt);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
        float edge = d2 - d1;
        return smoothstep(0.16, 0.0, edge);
    }

    half4 main(float2 fragCoord) {
        float minDim = min(uResolution.x, uResolution.y);
        float2 uv = fragCoord / uResolution;
        float2 p = (fragCoord - 0.5 * uResolution) / minDim;

        // 1. Sculpted sandy lagoon floor with soft sand-bar ripples
        float warp = fbm(p * 2.6);
        float sandWave = sin((p.x * 0.8 + p.y * 0.6) * 26.0 + warp * 5.2);
        float sandSlope = cos((p.x * 0.8 + p.y * 0.6) * 26.0 + warp * 5.2);
        float fineGrain = valueNoise(p * 55.0) * 0.04;

        float3 sandLight = float3(0.62, 0.86, 0.92);
        float3 sandShade = float3(0.42, 0.72, 0.83);
        float3 sandFloor = mix(sandShade, sandLight, 0.5 + 0.32 * sandWave + 0.18 * sandSlope) + fineGrain;

        // 2. Smooth underwater river pebbles / cobbles nestled into the sand bed
        // Mask pebbles so patches of rippled sand peek through naturally
        float pebblePatch = smoothstep(0.30, 0.68, fbm(p * 1.8 + float2(4.2, 8.7)));
        float4 stone = pebbleLayer(p * 8.5 + warp * 0.35);
        float stoneH = stone.x * pebblePatch;
        float stoneId = stone.y;
        float2 stoneN = stone.zw;

        // Natural mineral palette for submerged river stones
        float3 stoneColA = float3(0.36, 0.63, 0.74); // Cool slate-aqua
        float3 stoneColB = float3(0.44, 0.71, 0.72); // River jade
        float3 stoneColC = float3(0.58, 0.74, 0.76); // Warm sandstone-grey
        float3 stoneColD = float3(0.30, 0.54, 0.66); // Deep basalt-blue

        float3 stoneBase = mix(
            mix(stoneColA, stoneColB, smoothstep(0.0, 0.5, stoneId)),
            mix(stoneColC, stoneColD, smoothstep(0.5, 1.0, stoneId)),
            step(0.5, stoneId)
        );
        // Subtle mineral mottling on each pebble
        stoneBase *= 0.92 + 0.16 * valueNoise(p * 32.0 + stoneId * 17.0);

        // 3D top-left sunlight on the rounded pebble dome (matching fish shadow direction)
        float2 lightDir = normalize(float2(-0.6, -0.8));
        float diffuse = clamp(0.65 + 0.45 * dot(stoneN, lightDir) * (1.0 - stoneH * 0.3), 0.35, 1.15);
        float spec = smoothstep(0.55, 0.92, dot(stoneN, lightDir)) * stoneH * 0.14;
        float3 litStone = stoneBase * diffuse + float3(0.85, 0.96, 1.0) * spec;

        // Soft crevice shadow where pebbles meet the sand
        float creviceShadow = 1.0 - 0.22 * smoothstep(0.0, 0.18, stoneH) * (1.0 - smoothstep(0.18, 0.48, stoneH));
        float stoneMask = smoothstep(0.08, 0.32, stoneH);
        float3 seabed = mix(sandFloor * creviceShadow, litStone, stoneMask);

        // 3. Clear turquoise water column depth absorption
        float radialDepth = clamp(length(p) * 1.1, 0.0, 1.0);
        float3 waterColumn = mix(uDeepColor, uShallowColor, radialDepth * 0.7 + warp * 0.3);
        float3 waterColor = mix(seabed, waterColumn, 0.38);

        // 4. Crisp Voronoi cellular caustic light web projected onto the seabed
        float2 causticP = p * 5.2 + stoneN * stoneMask * 0.08 + float2(warp * 0.45);
        float cPrimary = causticWeb(causticP);
        float cSecondary = causticWeb(causticP * 1.75 + float2(3.7, 9.1)) * 0.45;
        float caustic = clamp(cPrimary + cSecondary, 0.0, 1.0);

        float3 causticLight = float3(0.88, 0.98, 1.0);
        waterColor += causticLight * caustic * 0.20;

        // Gentle perimeter shading
        float vignette = 1.0 - 0.07 * smoothstep(0.50, 0.98, length(uv - 0.5) * 1.4);
        waterColor *= vignette;

        return half4(half3(waterColor), 1.0);
    }
"""
