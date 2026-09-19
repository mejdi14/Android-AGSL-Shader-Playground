# Home Gallery — implementation spec

A complete specification for the landing screen of the Android AGSL Shader
Playground demo app. Following this should reproduce the existing design exactly.

**Target:** Jetpack Compose, Material 3, minSdk 33, Kotlin.
**Reference implementation:** `app/src/main/java/com/example/shaders_ripple_effect/`
— `HomeScreen.kt`, `DemoCatalog.kt`, `ui/home/ShaderArtwork.kt`, `MainActivity.kt`.

---

## 1. Design intent

The app is a shader library's shop window. A plain settings-style list makes good
work look cheap, so the landing screen is an **editorial gallery**: a centred
serif masthead over a two-column grid of painted cover tiles, on warm paper
rather than default Material white.

Three rules drive every decision below:

1. **Every tile depicts its effect.** Covers are procedural artwork whose motif
   hints at what the shader does — rings for a ripple, a spiral for a vortex,
   scan lines for a glitch. Never generic stock photos, never a colour swatch.
2. **One art-directed set.** A fixed six-palette rotation and a shared painterly
   base make fifteen different motifs read as one coherent collection.
3. **No new dependencies or assets.** All artwork is drawn with Compose `Canvas`
   primitives. No image files, no icon libraries, no downloadable fonts.

---

## 2. Colour tokens

Define a private `HomeColors` data class with four fields and two instances.
These are exact — do not substitute Material theme colours.

```kotlin
private data class HomeColors(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color
)

private val LightHome = HomeColors(
    background = Color(0xFFF4F1E8),  // warm paper
    ink        = Color(0xFF1B1A16),  // near-black, warm
    muted      = Color(0xFF6E6A61),  // secondary text
    hairline   = Color(0x141B1A16)   // 8% ink, tile placeholder
)

private val DarkHome = HomeColors(
    background = Color(0xFF16150F),  // warm dark, NOT pure black
    ink        = Color(0xFFF2EFE6),
    muted      = Color(0xFF9A958A),
    hairline   = Color(0x1AF2EFE6)
)
```

The warmth matters. Pure `#FFFFFF` / `#000000` kills the editorial feel.

---

## 3. Screen structure

```
Box(fillMaxSize, background = colors.background)
├── LazyVerticalGrid(columns = Fixed(2))
│   ├── item(span = maxLineSpan) → Masthead
│   └── itemsIndexed(demos, key = { _, d -> d.route }) → ArtTile
└── ThemeToggle (aligned TopEnd)
```

### Grid

| Property | Value |
|---|---|
| columns | `GridCells.Fixed(2)` |
| contentPadding start/end | `20.dp` |
| contentPadding top | `insets.calculateTopPadding() + 8.dp` |
| contentPadding bottom | `insets.calculateBottomPadding() + 40.dp` |
| horizontalArrangement | `Arrangement.spacedBy(16.dp)` |
| verticalArrangement | `Arrangement.spacedBy(28.dp)` |

The generous **28dp** vertical gap versus **16dp** horizontal is deliberate — it
groups each tile with its own caption instead of with its neighbour.

### Insets

The screen paints **edge to edge**. It reads
`WindowInsets.safeDrawing.asPaddingValues()` itself and feeds those into the
grid's `contentPadding`. Do **not** wrap it in a `Surface` or apply
`Modifier.safeDrawingPadding()` from the caller — the warm background must run
behind the status bar, and only the content is inset. The nav host calls it bare:

```kotlin
composable(HOME_ROUTE) {
    HomeScreen(demos = shaderDemos, onOpenDemo = { navController.navigate(it.route) })
}
```

---

## 4. Masthead

Spans the full grid width via `item(span = { GridItemSpan(maxLineSpan) })`.
Centred column, `padding(top = 40.dp, bottom = 28.dp)`.

| Element | Spec |
|---|---|
| Headline | `"Something to feel\nin every pixel."` — `FontFamily.Serif`, `40.sp`, lineHeight `46.sp`, `FontWeight.Normal`, `colors.ink`, centred |
| Subtitle | `"$count shaders for Jetpack Compose. Each one carries its own\nmood — tap to watch it move, or take it home in code."` — `FontFamily.SansSerif`, `13.sp`, lineHeight `20.sp`, `colors.muted`, centred |
| Spacer between | `16.dp` |

`$count` is `demos.size`, so the copy stays true as effects are added or removed.

The serif/sans pairing is the whole typographic idea: an editorial serif headline
over quiet sans support text. Use the **built-in** `FontFamily.Serif` — adding a
Google Fonts dependency is explicitly out of scope.

---

## 5. Tile anatomy (`ArtTile`)

A `Column`, `fillMaxWidth`, `clip(RoundedCornerShape(14.dp))`, `clickable`.

1. **Artwork** — `Box` with `fillMaxWidth().aspectRatio(1f)`,
   `clip(RoundedCornerShape(12.dp))`, `background(colors.hairline)`, containing
   `ShaderArtwork(route = demo.route, index = index, modifier = fillMaxSize())`.
   The square ratio is what makes the grid read as a gallery.
2. `Spacer(12.dp)`
3. **Label** — `"${demo.category} · ${demo.title}"`, `FontFamily.SansSerif`,
   `13.sp`, `FontWeight.Medium`, `colors.ink`, `maxLines = 2`, ellipsised.
   The middle dot separator is required; it's what gives the collection-catalogue
   feel.
4. `Spacer(4.dp)`
5. **Subtitle** — `demo.subtitle`, `FontFamily.SansSerif`, `11.sp`, lineHeight
   `15.sp`, `colors.muted`, `maxLines = 2`, ellipsised.

The tile passes its **grid index** to `ShaderArtwork`, which is how palettes
rotate (§7).

---

## 6. Theme toggle

A 36dp circular clickable at `Alignment.TopEnd`, padded
`top = insets.calculateTopPadding() + 14.dp, end = 20.dp`, holding an 18dp
`Canvas`. State is `rememberSaveable { mutableStateOf(false) }` local to the
screen — it only restyles the gallery and must not touch the app theme or the
demo screens.

- **Light mode → crescent:** draw a filled circle in `colors.ink`, then punch it
  out with a circle of `colors.background` at radius `r * 0.88f`, offset
  `(+r * 0.42f, -r * 0.28f)`.
- **Dark mode → sun:** filled circle at `r * 0.6f` plus 8 rays drawn as lines
  from `r * 0.78f` to `r`, at `i * π/4`, stroke `size.minDimension * 0.08f`.

Drawn rather than using an icon font, per the no-dependencies rule.

---

## 7. Cover artwork system (`ui/home/ShaderArtwork.kt`)

The core of the design. Public surface:

```kotlin
@Composable
fun ShaderArtwork(route: String, index: Int, modifier: Modifier = Modifier) {
    val palette = paletteFor(index)
    Canvas(modifier) {
        painterlyBase(palette, route.hashCode())
        motif(route, palette)
    }
}
```

### 7.1 Palettes

Six palettes, selected by `palettes[index % palettes.size]` so colours rotate
down the grid. Each has `base` (fill), three accents, and an `ink`.

```kotlin
data class ArtPalette(val base: Color, val a1: Color, val a2: Color, val a3: Color, val ink: Color)

// Garden   — blush pinks over sage
ArtPalette(0xFFF6DDE4, 0xFFE46D96, 0xFFA7C39C, 0xFFF3C4D3, 0xFF7A3550)
// Dawn     — lilac and mist
ArtPalette(0xFFF3DCE2, 0xFFC3A4D6, 0xFF8FB6D6, 0xFFEFC9C0, 0xFF5B4A70)
// Willow   — deep water blues
ArtPalette(0xFF9FB7D3, 0xFF2F5D4F, 0xFFDCE8F0, 0xFF5E86AE, 0xFF223B52)
// Wisteria — citron and violet
ArtPalette(0xFFE9E4A6, 0xFF9A7AC3, 0xFFA7C39C, 0xFFD8CE86, 0xFF4F3F6B)
// Ember    — terracotta dusk
ArtPalette(0xFFEFD2C0, 0xFFC4663F, 0xFF6B4E7A, 0xFFE0A984, 0xFF6B3826)
// Tide     — teal and sand
ArtPalette(0xFFD9E6DE, 0xFF2E7D74, 0xFFE8C98D, 0xFF8FC0B5, 0xFF1F5049)
```

### 7.2 Painterly base

Applied under **every** motif — this shared wash is what unifies the set:

```kotlin
private fun DrawScope.painterlyBase(p: ArtPalette, seed: Int) {
    drawRect(p.base)
    val rnd = Random(seed)                       // seeded by route.hashCode()
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
```

The `Random(route.hashCode())` seed is **required**: it makes each tile's wash
distinct but *stable* across recompositions and scrolls. An unseeded `Random`
would shimmer as you scroll.

### 7.3 Motif dispatch

A `when (route)` returning `Unit`, with `else -> waves(p)` so a new demo always
renders something rather than a blank tile.

| Route | Motif | Drawn as |
|---|---|---|
| `ripple` | concentric rings | 7 stroked circles, alpha and width decaying outward |
| `complex_wave` | sine bands | 5 stroked sine paths |
| `reveal` | split + circle | half-rect, filled circle, stroked ring |
| `motion_blur` | streaks | 6 round-cap lines, decreasing alpha |
| `kaleidoscope` | mandala | 10 `drawArc` wedges + bounding ring |
| `vortex` | spiral | parametric `Path`, `r = t * step` |
| `dongxiao_wave` | waves + rings | `waves(p)` then `ripple(p)` |
| `crt_glitch` | scanlines + offset blocks | hairlines + seeded displaced rects |
| `pixel_melt` | drips | columns with rounded bottoms |
| `noise_dissolve` | speckle | grid of squares fading along x |
| `apple_liquid_glass` | lens | disc, rim strokes, specular dot |
| `curved_gallery` | perspective panels | 3 leaning trapezoid paths |
| `paper_fold_showcase` | folded bands | `rotate(-18f)` + alternating strips |
| `holographic_sticker` | foil card | rounded rect + diagonal colour bands + glint dots |
| `metaballs` | merging blobs | 5 overlapping soft circles + bright core |

**Motif conventions:** size everything relative to `size.minDimension` /
`size.width` / `size.height` so tiles scale to any density; keep alpha in the
`0.5–0.95` band so the painterly wash stays visible underneath; seed any `Random`
with a constant so the tile is stable.

---

## 8. Catalog (`DemoCatalog.kt`)

Single source of truth for **both** the gallery and the nav graph.

```kotlin
data class ShaderDemo(
    val route: String,      // nav route AND the artwork motif key
    val category: String,   // shown before the title
    val title: String,
    val subtitle: String,   // one line, ≤ ~60 chars
    val screen: @Composable () -> Unit
)
```

Categories in use: `Generative`, `Material`, `Showcase`, `Spatial`, `Distortion`,
`Transition`, `Motion`, `Pattern`, `Interactive`, `Retro`, `Glitch`,
`Refraction`. Reuse an existing one where it fits rather than inventing a synonym
— the vocabulary staying small is what makes the labels feel curated.

Order is presentation order: newest and most impressive first.

---

## 9. Adding a new demo

1. Add a `ShaderDemo` entry to `shaderDemos`.
2. Add a `when` branch in `motif()` keyed on the same `route`, plus a
   `private fun DrawScope.<name>(p: ArtPalette)`.
3. Nothing else — the grid, nav route and palette assignment all follow.

---

## 10. Acceptance checklist

- [ ] Warm paper background runs behind the status bar; content is inset.
- [ ] Two columns, square covers, 16dp horizontal / 28dp vertical gaps.
- [ ] Centred serif masthead; count in the subtitle matches the real demo count.
- [ ] Every tile has a distinct motif that suggests its effect — none blank, no
      two adjacent tiles sharing a palette.
- [ ] Labels read `Category · Title` with the middle dot.
- [ ] Toggle flips crescent ↔ sun and swaps the whole gallery palette, leaving
      the demo screens untouched.
- [ ] Tile artwork does **not** change while scrolling (seeded randomness).
- [ ] No new Gradle dependencies, no new drawable assets, no downloaded fonts.
- [ ] `./gradlew :app:assembleDebug` passes.
