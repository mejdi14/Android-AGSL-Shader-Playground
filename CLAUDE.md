# Android AGSL Shader Playground

A Jetpack Compose library of AGSL runtime-shader animations, plus a demo app that
showcases them.

- `shaderRippleEffect/` — the published library (`io.github.mejdi14:android-shader-effect`).
  Each effect is a composable that wraps arbitrary content and applies a
  `RuntimeShader` via `graphicsLayer { renderEffect = ... }`.
- `app/` — the demo app. `DemoCatalog.kt` is the single source of truth: one
  `ShaderDemo` entry drives both the home gallery and the navigation graph.
- `app/.../ui/home/ShaderArtwork.kt` — procedural cover art for each demo tile.

## Committing

After finishing a change, `git add` the relevant files and commit it.

Commit messages are short, lowercase, and plain — describe the change the way a
developer would in a hurry. Match the existing history:

```
clean up and refactoring
upgrade gradle plugin version
add sticker peel shader
fix ripple origin on rotation
```

Rules:
- One line. No body unless the change genuinely needs explaining.
- No "feat:"/"chore:" prefixes, no emoji, no trailing punctuation.
- **No AI attribution or co-author trailers.** Commits should read as if written
  by the repo owner.
- Don't push unless asked.

## Adding a new effect

1. Add the effect composable in `shaderRippleEffect/.../<Name>Effect.kt`.
2. Add a demo screen in `app/.../ShaderDemoScreens.kt`.
3. Add a `ShaderDemo` entry in `DemoCatalog.kt` (route, category, title, subtitle).
4. Add a cover-art motif branch in `ShaderArtwork.kt`.
5. Document it in `README.md` (TOC, feature bullet, usage snippet, API table).

## Notes

- AGSL is **fragment-only** — no vertex shaders, no real geometry. Anything
  needing true 3D mesh deformation uses OpenGL ES instead (see
  `app/.../gallery/`, which has a real GLSL vertex shader).
- AGSL has no `dFdx`/`dFdy`; derive gradients analytically.
- Effects size their uniforms from `LocalConfiguration` (screen size), not the
  composable's own size. Keep that in mind for small/previewed content.
- Build with the Android Studio JDK:
  `JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Credentials live in `~/.gradle/gradle.properties`, never in the repo.

## Agent workflow

- **Clarify the prompt first.** Before planning or acting on each user request, organize it into a concise working brief. Capture the goal, relevant context, constraints, and expected deliverable. Preserve the user's intent and include only details needed to do the work. Use this brief when delegating.
- **Delegate straightforward work.** When running a higher-capability model, delegate self-contained tasks that need little reasoning to a lighter model when the client supports model selection for subagents. Give the subagent a clear scope and completion criteria. Keep complex decisions with the main agent and verify delegated results before relying on them.
- **Commit every logical change.** In a Git repository, verify and commit each completed logical change before handing work back. Stage only the task's changes, keep unrelated work out of the commit, and follow the project's commit conventions.
