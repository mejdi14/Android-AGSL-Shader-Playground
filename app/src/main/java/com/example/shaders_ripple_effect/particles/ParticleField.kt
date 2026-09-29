/*
 * Ported to Jetpack Compose from the "Particles" block by Lorenzo Cabra
 * (https://bencho.dev/?c=particles), used under the MIT licence:
 *
 * MIT License
 *
 * Copyright (c) 2026 Lorenzo Cabra
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.example.shaders_ripple_effect.particles

import android.graphics.Paint
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * A shape made of dots. Drag over it and the dots are pushed away from your
 * finger and spring home behind it; tap and a shockwave throws them out, and
 * they gather again into the next shape — circle, square, flower, round again.
 *
 * The dots never change, only where home is. Every shape is sampled to the same
 * number of points, and a change of shape pairs each dot with a new home by
 * angle round the middle, so the swarm sweeps round into the new outline rather
 * than crossing itself. A dot at rest is round; a moving one is drawn long along
 * its path, which makes a burst read as a splash rather than a scatter.
 *
 * Not a shader: every dot carries a position and velocity from frame to frame,
 * and AGSL keeps no state between frames, so the physics runs here on the CPU.
 */

enum class ParticleShape { Circle, Square, Flower }

enum class ParticleGrain(val radius: Float) { Fine(1.9f), Coarse(2.7f) }

// The shape is laid out in a fixed 480 x 400 space, then scaled to fit.
private const val W = 480f
private const val H = 400f
private const val CX = W / 2f
private const val CY = H / 2f

/** How many dots every shape is sampled to. */
private const val N = 720

@Composable
fun ParticleField(
    modifier: Modifier = Modifier,
    shape: ParticleShape = ParticleShape.Circle,
    /** How far the finger's push reaches, in layout units. */
    reach: Float = 80f,
    /** How hard it pushes, 0..100. */
    force: Float = 60f,
    grain: ParticleGrain = ParticleGrain.Fine,
    color: Color = Color(0xFF151515)
) {
    val context = LocalContext.current
    // Respect "remove animations": dots jump to place and ignore the finger.
    val still = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    val swarm = remember { Swarm().also { it.become(shape, still = true) } }
    val reachNow by rememberUpdatedState(reach)
    val forceNow by rememberUpdatedState(force)

    var frame by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var hand by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(shape) {
        if (swarm.shape != shape) {
            swarm.become(shape, still)
            frame++
            running = true
        }
    }

    // The loop parks itself once every dot has settled.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var prev = 0L
        while (true) {
            val now = withFrameNanos { it }
            // In sixtieths of a second, so a dropped frame decays the same energy.
            val dt = if (prev == 0L) 1f else ((now - prev) / 16_666_667f).coerceIn(0f, 2.5f)
            prev = now
            val busy = swarm.step(dt, if (still) null else hand, reachNow, forceNow)
            frame++
            if (!busy) break
        }
        running = false
    }

    val dotPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }
    val streakPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }
    val points = remember { FloatArray(N * 2) }

    Canvas(
        modifier.pointerInput(still) {
            fun toLayout(p: Offset): Offset {
                val k = min(size.width / W, size.height / H)
                val ox = (size.width - W * k) / 2f
                val oy = (size.height - H * k) / 2f
                return Offset((p.x - ox) / k, (p.y - oy) / k)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                var moved = false
                if (!still) hand = toLayout(start)
                running = true

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // A tap, not a drag: the shockwave, then the next shape.
                        if (!moved) {
                            if (!still) {
                                val p = toLayout(change.position)
                                swarm.shockwave(p.x, p.y, forceNow)
                            }
                            swarm.become(swarm.shape.next(), still)
                        }
                        break
                    }
                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                    }
                    if (!still) hand = toLayout(change.position)
                    running = true
                    change.consume()
                }
                hand = null
                frame++
                running = true
            }
        }
    ) {
        frame // redraw whenever the simulation steps

        val k = min(size.width / W, size.height / H)
        val ox = (size.width - W * k) / 2f
        val oy = (size.height - H * k) / 2f
        val r = grain.radius * k
        val argb = color.toArgb()
        dotPaint.color = argb
        dotPaint.strokeWidth = r * 2f
        streakPaint.color = argb

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            var n = 0
            for (i in 0 until N) {
                val px = ox + swarm.x[i] * k
                val py = oy + swarm.y[i] * k
                val speed = hypot(swarm.vx[i], swarm.vy[i])
                if (speed < 0.3f) {
                    points[n++] = px
                    points[n++] = py
                } else {
                    // Long along its path, a little thin across it: a capsule
                    // whose length and width match the original's ellipse.
                    val s = min(2.6f, 1f + speed * 0.12f)
                    val width = 2f * r / sqrt(s)
                    val half = max(r * s - width / 2f, 0f)
                    val ux = swarm.vx[i] / speed
                    val uy = swarm.vy[i] / speed
                    streakPaint.strokeWidth = width
                    native.drawLine(px - ux * half, py - uy * half, px + ux * half, py + uy * half, streakPaint)
                }
            }
            if (n > 0) native.drawPoints(points, 0, n, dotPaint)
        }
    }
}

private fun ParticleShape.next(): ParticleShape =
    ParticleShape.entries[(ordinal + 1) % ParticleShape.entries.size]

private class Swarm {
    val hx = FloatArray(N)
    val hy = FloatArray(N)
    val x = FloatArray(N)
    val y = FloatArray(N)
    val vx = FloatArray(N)
    val vy = FloatArray(N)
    var shape = ParticleShape.Circle
    private var initialised = false

    /** Give every dot a home in [s], paired by angle so the swarm sweeps round. */
    fun become(s: ParticleShape, still: Boolean) {
        shape = s
        val next = homesFor(s)
        if (!initialised) {
            for (i in 0 until N) {
                hx[i] = next[i * 2]; hy[i] = next[i * 2 + 1]
                x[i] = hx[i]; y[i] = hy[i]
            }
            initialised = true
            return
        }
        val byAngleNow = (0 until N).sortedBy { atan2(y[it] - CY, x[it] - CX) }
        val byAngleNext = (0 until N).sortedBy { atan2(next[it * 2 + 1] - CY, next[it * 2] - CX) }
        for (k in 0 until N) {
            val d = byAngleNow[k]
            val h = byAngleNext[k]
            hx[d] = next[h * 2]
            hy[d] = next[h * 2 + 1]
            if (still) {
                x[d] = hx[d]; y[d] = hy[d]; vx[d] = 0f; vy[d] = 0f
            }
        }
    }

    /** One step of the spring; returns true while anything is still moving. */
    fun step(dt: Float, hand: Offset?, reach: Float, force: Float): Boolean {
        val reachClamped = reach.coerceIn(30f, 160f)
        val strength = force.coerceIn(0f, 100f) / 100f * 11f
        val damping = 0.84f.pow(dt)
        var busy = hand != null
        for (i in 0 until N) {
            if (hand != null) {
                val dx = x[i] - hand.x
                val dy = y[i] - hand.y
                val dd = hypot(dx, dy)
                if (dd < reachClamped && dd > 0.01f) {
                    val falloff = 1f - dd / reachClamped
                    val f = falloff * falloff * strength
                    vx[i] += dx / dd * f * dt
                    vy[i] += dy / dd * f * dt
                }
            }
            vx[i] = (vx[i] + (hx[i] - x[i]) * 0.05f * dt) * damping
            vy[i] = (vy[i] + (hy[i] - y[i]) * 0.05f * dt) * damping
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (abs(hx[i] - x[i]) < 0.05f && abs(hy[i] - y[i]) < 0.05f &&
                abs(vx[i]) < 0.02f && abs(vy[i]) < 0.02f
            ) {
                x[i] = hx[i]; y[i] = hy[i]; vx[i] = 0f; vy[i] = 0f
            } else {
                busy = true
            }
        }
        return busy
    }

    fun shockwave(px: Float, py: Float, force: Float) {
        val strength = 0.4f + force.coerceIn(0f, 100f) / 100f * 1.2f
        for (i in 0 until N) {
            val dx = x[i] - px
            val dy = y[i] - py
            val dd = hypot(dx, dy)
            if (dd > 200f || dd < 0.01f) continue
            val f = (1f - dd / 200f) * 30f * strength
            vx[i] += dx / dd * f
            vy[i] += dy / dd * f
        }
    }
}

/* ── N points spread evenly over the inside of a shape ──────────────────
   A hex grid spaced so the shape holds about N of them — its area over N —
   then trimmed or topped up to exactly N, evenly. */

private val homeCache = HashMap<ParticleShape, FloatArray>()

private fun homesFor(s: ParticleShape): FloatArray = homeCache.getOrPut(s) { sampleHomes(s) }

private fun sampleHomes(s: ParticleShape): FloatArray {
    var area = 0f
    var sy = 1f
    while (sy < H) {
        var sx = 1f
        while (sx < W) {
            if (inside(s, sx, sy)) area += 4f
            sx += 2f
        }
        sy += 2f
    }
    val spacing = sqrt(area / N * (2f / sqrt(3f)))
    val rowHeight = spacing * sqrt(3f) / 2f

    val pts = ArrayList<Float>(N * 3)
    var y = rowHeight / 2f
    var row = 0
    while (y < H) {
        var x = spacing / 2f + (row % 2) * (spacing / 2f)
        while (x < W) {
            if (inside(s, x, y)) { pts.add(x); pts.add(y) }
            x += spacing
        }
        y += rowHeight
        row++
    }

    val count = pts.size / 2
    val out = FloatArray(N * 2)
    for (i in 0 until N) {
        val j = (i.toLong() * count / N).toInt()
        out[i * 2] = pts[j * 2]
        out[i * 2 + 1] = pts[j * 2 + 1]
    }
    return out
}

/** Five round petals round a smaller middle, petals just touching. */
private val petals: List<Offset> = (0 until 5).map { i ->
    val a = -Math.PI.toFloat() / 2f + i * 2f * Math.PI.toFloat() / 5f
    Offset(CX + cos(a) * 104f, CY + 8f + sin(a) * 104f)
}

private fun inside(s: ParticleShape, px: Float, py: Float): Boolean = when (s) {
    ParticleShape.Circle -> {
        val dx = px - CX
        val dy = py - CY
        dx * dx + dy * dy < 150f * 150f
    }
    ParticleShape.Square -> {
        // 280 x 280, corner radius 36, as a rounded-box distance.
        val qx = abs(px - CX) - (140f - 36f)
        val qy = abs(py - CY) - (140f - 36f)
        hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - 36f < 0f
    }
    ParticleShape.Flower -> {
        petals.any { (it.x - px).let { dx -> dx * dx } + (it.y - py).let { dy -> dy * dy } < 60f * 60f } ||
            (px - CX).let { it * it } + (py - CY - 8f).let { it * it } < 58f * 58f
    }
}
