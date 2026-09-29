package com.example.shaders_ripple_effect.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Hand-painted-looking cover art for each shader, drawn procedurally.
 *
 * Every effect gets a motif that hints at what it does — concentric rings for
 * the ripple, a spiral for the vortex, scan lines for the CRT glitch — rendered
 * over soft watercolour washes so the whole grid reads as one art-directed set.
 */

data class ArtPalette(
    val base: Color,
    val a1: Color,
    val a2: Color,
    val a3: Color,
    val ink: Color
)

private val palettes = listOf(
    // Garden — blush pinks over sage
    ArtPalette(Color(0xFFF6DDE4), Color(0xFFE46D96), Color(0xFFA7C39C), Color(0xFFF3C4D3), Color(0xFF7A3550)),
    // Dawn — lilac and mist
    ArtPalette(Color(0xFFF3DCE2), Color(0xFFC3A4D6), Color(0xFF8FB6D6), Color(0xFFEFC9C0), Color(0xFF5B4A70)),
    // Willow — deep water blues
    ArtPalette(Color(0xFF9FB7D3), Color(0xFF2F5D4F), Color(0xFFDCE8F0), Color(0xFF5E86AE), Color(0xFF223B52)),
    // Wisteria — citron and violet
    ArtPalette(Color(0xFFE9E4A6), Color(0xFF9A7AC3), Color(0xFFA7C39C), Color(0xFFD8CE86), Color(0xFF4F3F6B)),
    // Ember — terracotta dusk
    ArtPalette(Color(0xFFEFD2C0), Color(0xFFC4663F), Color(0xFF6B4E7A), Color(0xFFE0A984), Color(0xFF6B3826)),
    // Tide — teal and sand
    ArtPalette(Color(0xFFD9E6DE), Color(0xFF2E7D74), Color(0xFFE8C98D), Color(0xFF8FC0B5), Color(0xFF1F5049))
)

fun paletteFor(index: Int): ArtPalette = palettes[index % palettes.size]

@Composable
fun ShaderArtwork(route: String, index: Int, modifier: Modifier = Modifier) {
    val palette = paletteFor(index)
    Canvas(modifier) {
        painterlyBase(palette, route.hashCode())
        motif(route, palette)
    }
}

/** Soft overlapping washes so every tile feels painted rather than generated. */
private fun DrawScope.painterlyBase(p: ArtPalette, seed: Int) {
    drawRect(p.base)
    val rnd = Random(seed)
    val accents = listOf(p.a1, p.a2, p.a3)
    repeat(6) {
        val r = size.minDimension * (0.22f + rnd.nextFloat() * 0.38f)
        drawCircle(
            color = accents[rnd.nextInt(accents.size)].copy(alpha = 0.15f),
            radius = r,
            center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        )
    }
}

private fun DrawScope.motif(route: String, p: ArtPalette) = when (route) {
    "ripple" -> ripple(p)
    "complex_wave" -> waves(p)
    "reveal" -> reveal(p)
    "motion_blur" -> streaks(p)
    "kaleidoscope" -> kaleidoscope(p)
    "vortex" -> spiral(p)
    "dongxiao_wave" -> { waves(p); ripple(p) }
    "crt_glitch" -> glitch(p)
    "pixel_melt" -> melt(p)
    "noise_dissolve" -> dissolve(p)
    "apple_liquid_glass" -> glassLens(p)
    "curved_gallery" -> curvedBand(p)
    "paper_fold_showcase" -> paperFold(p)
    "holographic_sticker" -> holoSticker(p)
    "metaballs" -> metaballs(p)
    "liquid_glass_reveal" -> glassWave(p)
    else -> waves(p)
}

private fun DrawScope.ripple(p: ArtPalette) {
    val c = center
    for (i in 1..7) {
        val t = i / 7f
        drawCircle(
            color = p.a1.copy(alpha = 0.55f * (1f - t) + 0.12f),
            radius = size.minDimension * 0.08f * i,
            center = c,
            style = Stroke(width = size.minDimension * 0.022f * (1f - t * 0.6f))
        )
    }
}

private fun DrawScope.waves(p: ArtPalette) {
    val colors = listOf(p.a1, p.a2, p.a3)
    for (band in 0..4) {
        val path = Path()
        val yBase = size.height * (0.22f + band * 0.15f)
        val amp = size.height * 0.055f
        path.moveTo(0f, yBase)
        var x = 0f
        while (x <= size.width) {
            val y = yBase + sin((x / size.width) * 6.28f * 1.4f + band * 0.7f) * amp
            path.lineTo(x, y)
            x += 4f
        }
        drawPath(
            path = path,
            color = colors[band % colors.size].copy(alpha = 0.7f),
            style = Stroke(width = size.minDimension * 0.026f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.reveal(p: ArtPalette) {
    drawRect(p.a2.copy(alpha = 0.55f), size = Size(size.width * 0.5f, size.height))
    drawCircle(p.a1.copy(alpha = 0.85f), size.minDimension * 0.3f, center)
    drawCircle(
        color = p.ink.copy(alpha = 0.5f),
        radius = size.minDimension * 0.42f,
        center = center,
        style = Stroke(width = size.minDimension * 0.015f)
    )
}

private fun DrawScope.streaks(p: ArtPalette) {
    for (i in 0..5) {
        val y = size.height * (0.2f + i * 0.12f)
        val w = size.width * (0.45f + (i % 3) * 0.18f)
        drawLine(
            color = p.a1.copy(alpha = 0.55f - i * 0.06f),
            start = Offset(size.width * 0.08f, y),
            end = Offset(size.width * 0.08f + w, y),
            strokeWidth = size.minDimension * 0.05f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.kaleidoscope(p: ArtPalette) {
    val colors = listOf(p.a1, p.a2, p.a3)
    val r = size.minDimension * 0.42f
    val topLeft = Offset(center.x - r, center.y - r)
    for (i in 0 until 10) {
        drawArc(
            color = colors[i % colors.size].copy(alpha = 0.6f),
            startAngle = i * 36f,
            sweepAngle = 22f,
            useCenter = true,
            topLeft = topLeft,
            size = Size(r * 2, r * 2)
        )
    }
    drawCircle(p.ink.copy(alpha = 0.35f), r, center, style = Stroke(size.minDimension * 0.012f))
}

private fun DrawScope.spiral(p: ArtPalette) {
    val path = Path()
    val step = size.minDimension * 0.021f
    var t = 0f
    path.moveTo(center.x, center.y)
    while (t < 8.5f) {
        val r = t * step
        path.lineTo(center.x + cos(t) * r, center.y + sin(t) * r)
        t += 0.08f
    }
    drawPath(path, p.a1.copy(alpha = 0.85f), style = Stroke(size.minDimension * 0.028f, cap = StrokeCap.Round))
}

private fun DrawScope.glitch(p: ArtPalette) {
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = p.ink.copy(alpha = 0.12f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5f
        )
        y += size.height / 26f
    }
    val rnd = Random(21)
    repeat(4) {
        val h = size.height * (0.04f + rnd.nextFloat() * 0.07f)
        val ty = rnd.nextFloat() * size.height * 0.85f
        val off = size.width * (rnd.nextFloat() * 0.18f - 0.09f)
        drawRect(p.a1.copy(alpha = 0.6f), Offset(off, ty), Size(size.width * 0.75f, h))
        drawRect(p.a2.copy(alpha = 0.45f), Offset(off + size.width * 0.05f, ty + h * 0.35f), Size(size.width * 0.6f, h * 0.5f))
    }
}

private fun DrawScope.melt(p: ArtPalette) {
    val rnd = Random(13)
    val cols = 9
    val w = size.width / cols
    for (i in 0 until cols) {
        val h = size.height * (0.25f + rnd.nextFloat() * 0.6f)
        drawRect(
            color = (if (i % 2 == 0) p.a1 else p.a2).copy(alpha = 0.55f),
            topLeft = Offset(i * w, 0f),
            size = Size(w * 0.82f, h)
        )
        drawCircle((if (i % 2 == 0) p.a1 else p.a2).copy(alpha = 0.55f), w * 0.41f, Offset(i * w + w * 0.41f, h))
    }
}

private fun DrawScope.dissolve(p: ArtPalette) {
    val rnd = Random(5)
    val cell = size.minDimension / 16f
    var x = 0f
    while (x < size.width) {
        var y = 0f
        while (y < size.height) {
            val keep = (x / size.width) + rnd.nextFloat() * 0.5f
            if (keep < 0.85f) {
                drawRect(p.a1.copy(alpha = 0.5f * (1f - x / size.width) + 0.1f), Offset(x, y), Size(cell * 0.9f, cell * 0.9f))
            }
            y += cell
        }
        x += cell
    }
}

private fun DrawScope.glassLens(p: ArtPalette) {
    val r = size.minDimension * 0.32f
    drawCircle(Color.White.copy(alpha = 0.35f), r, center)
    drawCircle(p.a1.copy(alpha = 0.3f), r, center, style = Stroke(size.minDimension * 0.03f))
    drawCircle(p.a2.copy(alpha = 0.35f), r * 0.92f, center, style = Stroke(size.minDimension * 0.012f))
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = r * 0.3f,
        center = Offset(center.x - r * 0.32f, center.y - r * 0.34f)
    )
}

private fun DrawScope.curvedBand(p: ArtPalette) {
    val colors = listOf(p.a1, p.a3, p.a2)
    for (i in 0..2) {
        val cx = size.width * (0.24f + i * 0.26f)
        val lean = (i - 1) * size.height * 0.1f
        val halfW = size.width * 0.11f
        val halfH = size.height * 0.3f
        val path = Path().apply {
            moveTo(cx - halfW, center.y - halfH + lean)
            lineTo(cx + halfW, center.y - halfH - lean)
            lineTo(cx + halfW, center.y + halfH - lean)
            lineTo(cx - halfW, center.y + halfH + lean)
            close()
        }
        drawPath(path, colors[i].copy(alpha = if (i == 1) 0.8f else 0.5f))
    }
}

private fun DrawScope.glassWave(p: ArtPalette) {
    val r = size.minDimension * 0.3f
    val band = size.minDimension * 0.12f
    val box = Size(r * 2, r * 2)
    val topLeft = Offset(center.x - r, center.y - r)
    // The new photo already revealed inside the ring.
    drawCircle(p.a2.copy(alpha = 0.75f), r, center)
    // The glass band itself, and its faint outer lip.
    drawCircle(Color.White.copy(alpha = 0.3f), r, center, style = Stroke(band))
    drawCircle(p.ink.copy(alpha = 0.25f), r + band / 2f, center, style = Stroke(size.minDimension * 0.008f))
    // Rim light catching the top left of the ring.
    drawArc(
        color = Color.White.copy(alpha = 0.85f),
        startAngle = 195f,
        sweepAngle = 75f,
        useCenter = false,
        topLeft = topLeft,
        size = box,
        style = Stroke(size.minDimension * 0.02f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.metaballs(p: ArtPalette) {
    // Overlapping soft blobs that read as merging gooey shapes.
    val blobs = listOf(
        Triple(0.34f, 0.40f, 0.26f),
        Triple(0.58f, 0.36f, 0.20f),
        Triple(0.47f, 0.62f, 0.30f),
        Triple(0.70f, 0.64f, 0.18f),
        Triple(0.26f, 0.68f, 0.16f)
    )
    val colors = listOf(p.a1, p.a2, p.a3, p.a1, p.a2)
    blobs.forEachIndexed { i, (cx, cy, r) ->
        drawCircle(
            color = colors[i].copy(alpha = 0.72f),
            radius = size.minDimension * r,
            center = Offset(size.width * cx, size.height * cy)
        )
    }
    // A brighter core where they pile up.
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = size.minDimension * 0.14f,
        center = Offset(size.width * 0.46f, size.height * 0.5f)
    )
}

private fun DrawScope.holoSticker(p: ArtPalette) {
    val inset = size.minDimension * 0.17f
    drawRoundRect(
        color = Color.White.copy(alpha = 0.92f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(size.minDimension * 0.09f)
    )
    // Diagonal foil bands sweeping across the face.
    val colors = listOf(p.a1, p.a2, p.a3, p.a1)
    for (i in colors.indices) {
        val t = i / colors.size.toFloat()
        drawLine(
            color = colors[i].copy(alpha = 0.6f),
            start = Offset(inset, size.height * (0.85f - t * 0.55f)),
            end = Offset(size.width - inset, size.height * (0.5f - t * 0.55f)),
            strokeWidth = size.minDimension * 0.09f,
            cap = StrokeCap.Round
        )
    }
    // Diamond checker glint in the middle.
    val cell = size.minDimension * 0.07f
    var row = 0
    var y = size.height * 0.36f
    while (y < size.height * 0.64f) {
        var x = size.width * 0.3f + (if (row % 2 == 0) 0f else cell)
        while (x < size.width * 0.7f) {
            drawCircle(Color.White.copy(alpha = 0.35f), cell * 0.22f, Offset(x, y))
            x += cell * 2
        }
        y += cell
        row++
    }
    drawCircle(Color.White.copy(alpha = 0.3f), size.minDimension * 0.16f, center)
}

private fun DrawScope.paperFold(p: ArtPalette) {
    rotate(degrees = -18f) {
        var i = 0
        var x = -size.width * 0.3f
        while (x < size.width * 1.3f) {
            val w = size.width * 0.13f
            drawRect(
                color = (if (i % 2 == 0) p.a1 else p.a3).copy(alpha = if (i % 2 == 0) 0.55f else 0.35f),
                topLeft = Offset(x, -size.height * 0.3f),
                size = Size(w, size.height * 1.6f)
            )
            x += w
            i++
        }
    }
}
