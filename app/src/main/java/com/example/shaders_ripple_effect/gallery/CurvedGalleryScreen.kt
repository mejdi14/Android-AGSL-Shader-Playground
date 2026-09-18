package com.example.shaders_ripple_effect.gallery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.shaders_ripple_effect.R

/**
 * Demo screen for the circular (wheel) gallery. Hosts the OpenGL
 * [CurvedGalleryView] inside Compose and forces landscape while it is shown,
 * restoring the previous orientation on exit.
 */
@Composable
fun CurvedGalleryScreen() {
    val drawableIds = intArrayOf(
        R.drawable.violet,
        R.drawable.palace,
        R.drawable.swan,
        R.drawable.hedgehog,
        R.drawable.light_map
    )

    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D10))) {
        AndroidView(
            factory = { ctx -> CurvedGalleryView(ctx, drawableIds) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
