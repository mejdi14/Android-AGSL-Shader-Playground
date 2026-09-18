package com.example.shaders_ripple_effect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shaders_ripple_effect.ui.home.ShaderArtwork

private data class HomeColors(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color
)

private val LightHome = HomeColors(
    background = Color(0xFFF4F1E8),
    ink = Color(0xFF1B1A16),
    muted = Color(0xFF6E6A61),
    hairline = Color(0x141B1A16)
)

private val DarkHome = HomeColors(
    background = Color(0xFF16150F),
    ink = Color(0xFFF2EFE6),
    muted = Color(0xFF9A958A),
    hairline = Color(0x1AF2EFE6)
)

/**
 * The landing gallery: an editorial grid where every shader gets its own
 * painted cover. Tapping a tile opens that effect's full-screen demo.
 */
@Composable
fun HomeScreen(
    demos: List<ShaderDemo>,
    onOpenDemo: (ShaderDemo) -> Unit,
    modifier: Modifier = Modifier
) {
    var dark by rememberSaveable { mutableStateOf(false) }
    val colors = if (dark) DarkHome else LightHome
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = insets.calculateTopPadding() + 8.dp,
                bottom = insets.calculateBottomPadding() + 40.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Masthead(count = demos.size, colors = colors)
            }
            itemsIndexed(demos, key = { _, d -> d.route }) { index, demo ->
                ArtTile(
                    demo = demo,
                    index = index,
                    colors = colors,
                    onClick = { onOpenDemo(demo) }
                )
            }
        }

        ThemeToggle(
            dark = dark,
            colors = colors,
            onToggle = { dark = !dark },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = insets.calculateTopPadding() + 14.dp, end = 20.dp)
        )
    }
}

@Composable
private fun Masthead(count: Int, colors: HomeColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Something to feel\nin every pixel.",
            fontFamily = FontFamily.Serif,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Normal,
            color = colors.ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "$count shaders for Jetpack Compose. Each one carries its own\nmood — tap to watch it move, or take it home in code.",
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ArtTile(
    demo: ShaderDemo,
    index: Int,
    colors: HomeColors,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.hairline)
        ) {
            ShaderArtwork(
                route = demo.route,
                index = index,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "${demo.category} · ${demo.title}",
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = demo.subtitle,
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = colors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Small crescent that flips the gallery between the cream and ink themes. */
@Composable
private fun ThemeToggle(
    dark: Boolean,
    colors: HomeColors,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(18.dp)) {
            val r = size.minDimension * 0.5f
            if (dark) {
                // Sun: solid disc with rays.
                drawCircle(colors.ink, r * 0.6f, center)
                repeat(8) { i ->
                    val a = i * (Math.PI / 4).toFloat()
                    val inner = r * 0.78f
                    val outer = r
                    drawLine(
                        color = colors.ink,
                        start = Offset(center.x + kotlin.math.cos(a) * inner, center.y + kotlin.math.sin(a) * inner),
                        end = Offset(center.x + kotlin.math.cos(a) * outer, center.y + kotlin.math.sin(a) * outer),
                        strokeWidth = size.minDimension * 0.08f
                    )
                }
            } else {
                // Crescent: a disc with an offset disc punched out in the page colour.
                drawCircle(colors.ink, r, center)
                drawCircle(
                    color = colors.background,
                    radius = r * 0.88f,
                    center = Offset(center.x + r * 0.42f, center.y - r * 0.28f)
                )
            }
        }
    }
}
