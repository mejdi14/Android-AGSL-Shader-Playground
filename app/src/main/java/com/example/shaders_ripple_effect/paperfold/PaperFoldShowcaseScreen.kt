package com.example.shaders_ripple_effect.paperfold

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.shaders_ripple_effect.R
import kotlin.random.Random

private data class PaperChapter(
    val imageResource: Int,
    val title: String,
    val credit: String,
    val romanNumeral: String
)

private val chapters = listOf(
    PaperChapter(R.drawable.paper_fold_01, "Icons of Style", "Cue Studio", "I"),
    PaperChapter(R.drawable.paper_fold_02, "Ochre & Static", "Ines Marin", "II"),
    PaperChapter(R.drawable.paper_fold_03, "Long Shadow", "Reza Bakr", "III"),
    PaperChapter(R.drawable.paper_fold_04, "Room Nº 4", "Cue Studio", "IV"),
    PaperChapter(R.drawable.paper_fold_05, "North Wind", "Kaia Ono", "V"),
    PaperChapter(R.drawable.paper_fold_06, "Slow Burn", "Reza Bakr", "VI"),
    PaperChapter(R.drawable.paper_fold_07, "Salt & Copper", "Ines Marin", "VII")
)

private val WarmAccent = Color(0xFFD97757)
private val PaperWhite = Color.White

@Composable
fun PaperFoldShowcaseScreen() {
    val context = LocalContext.current
    var chapterIndex by remember { mutableIntStateOf(0) }
    var folding by remember { mutableStateOf(false) }
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    val chapter = chapters[chapterIndex]
    val surfaceView = remember(context) {
        PaperFoldSurfaceView(
            context = context,
            textureResources = chapters.map { it.imageResource }.toIntArray()
        )
    }

    surfaceView.onChapterChanged = { chapterIndex = it }
    surfaceView.onFoldingChanged = { folding = it }

    DisposableEffect(surfaceView) {
        surfaceView.onResume()
        onDispose { surfaceView.onPause() }
    }

    LaunchedEffect(cardBounds) {
        cardBounds?.let { bounds ->
            surfaceView.setCardBounds(bounds.left, bounds.top, bounds.width, bounds.height)
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (folding) 0f else 1f,
        animationSpec = tween(300),
        label = "paper labels"
    )
    val supportingAlpha by animateFloatAsState(
        targetValue = if (folding) 0.25f else 1f,
        animationSpec = tween(400),
        label = "paper supporting copy"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val proposedWidth = maxWidth * 0.58f
        val posterWidth = if (proposedWidth < 260.dp) proposedWidth else 260.dp
        val posterHeight = posterWidth * 4f / 3f

        AndroidView(
            factory = { surfaceView },
            modifier = Modifier.fillMaxSize()
        )

        StageGrain()
        ShowcaseTopBar(Modifier.align(Alignment.TopCenter))

        SideMetadata(
            label = "Chapter",
            value = (chapterIndex + 1).toString().padStart(2, '0'),
            alignment = Alignment.CenterStart,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .alpha(supportingAlpha)
        )
        SideMetadata(
            label = "Photograph",
            value = chapter.credit,
            alignment = Alignment.CenterEnd,
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
                .alpha(supportingAlpha)
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(posterWidth, posterHeight)
                .shadow(28.dp, RectangleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .onGloballyPositioned { cardBounds = it.boundsInRoot() }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 12.dp)
                    .alpha(overlayAlpha)
            ) {
                androidx.compose.material3.Text(
                    text = "FEATURED EDITION",
                    color = PaperWhite.copy(alpha = 0.9f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.8.sp
                )
                androidx.compose.material3.Text(
                    text = chapter.title,
                    color = PaperWhite,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            androidx.compose.material3.Text(
                text = chapter.romanNumeral,
                color = PaperWhite,
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 14.dp)
                    .alpha(overlayAlpha)
            )
        }

        FoldHint(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .alpha(supportingAlpha)
        )
        ChapterCounter(
            chapter = chapterIndex + 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 22.dp, bottom = 20.dp)
        )
    }
}

@Composable
private fun ShowcaseTopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 132.dp, top = 27.dp, end = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        androidx.compose.material3.Text(
            text = buildAnnotatedString {
                append("Cue")
                pushStyle(SpanStyle(color = WarmAccent))
                append(".")
                pop()
                append(" Exhibit")
            },
            color = PaperWhite,
            fontFamily = FontFamily.Serif,
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp
        )
        Column(horizontalAlignment = Alignment.End) {
            MetaChip("VOL. 001")
            MetaChip("PAPER FOLD STUDIES")
            MetaChip("2026")
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    androidx.compose.material3.Text(
        text = text,
        color = PaperWhite.copy(alpha = 0.7f),
        fontFamily = FontFamily.SansSerif,
        fontSize = 8.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.height(15.dp)
    )
}

@Composable
private fun SideMetadata(
    label: String,
    value: String,
    alignment: Alignment,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(76.dp),
        horizontalAlignment = if (alignment == Alignment.CenterEnd) Alignment.End else Alignment.Start
    ) {
        androidx.compose.material3.Text(
            text = label.uppercase(),
            color = PaperWhite.copy(alpha = 0.4f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 8.sp,
            letterSpacing = 2.sp,
            textAlign = textAlign
        )
        androidx.compose.material3.Text(
            text = value,
            color = PaperWhite,
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            textAlign = textAlign,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun FoldHint(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fold hint")
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fold hint pulse"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .padding(end = 9.dp)
                .size(8.dp)
        ) {
            drawCircle(
                color = WarmAccent.copy(alpha = pulse),
                radius = size.minDimension * 0.3f * (1f + pulse * 0.4f)
            )
        }
        androidx.compose.material3.Text(
            text = "CLICK THE POSTER TO FOLD",
            color = PaperWhite.copy(alpha = 0.55f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 8.sp,
            letterSpacing = 2.4.sp
        )
    }
}

@Composable
private fun ChapterCounter(chapter: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        androidx.compose.material3.Text(
            text = chapter.toString().padStart(2, '0'),
            color = PaperWhite,
            fontFamily = FontFamily.Serif,
            fontSize = 40.sp,
            lineHeight = 40.sp
        )
        androidx.compose.material3.Text(
            text = "OF 07",
            color = PaperWhite.copy(alpha = 0.7f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 9.sp,
            letterSpacing = 1.8.sp
        )
    }
}

@Composable
private fun StageGrain() {
    val flecks = remember {
        val random = Random(2026)
        List(850) { Offset(random.nextFloat(), random.nextFloat()) }
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .alpha(0.5f)
    ) {
        flecks.forEachIndexed { index, point ->
            drawCircle(
                color = Color.White.copy(alpha = if (index % 4 == 0) 0.055f else 0.025f),
                radius = if (index % 7 == 0) 0.75f else 0.4f,
                center = Offset(point.x * size.width, point.y * size.height)
            )
        }
    }
}
