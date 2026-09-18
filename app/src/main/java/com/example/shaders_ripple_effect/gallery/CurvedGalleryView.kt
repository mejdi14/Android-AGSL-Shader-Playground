package com.example.shaders_ripple_effect.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent

/**
 * A [GLSurfaceView] that hosts the [CurvedGalleryRenderer] and forwards
 * horizontal finger drags to it. The renderer turns those drags into a free,
 * continuous scroll with inertia — no per-card snapping.
 */
@SuppressLint("ViewConstructor")
class CurvedGalleryView(
    context: Context,
    drawableIds: IntArray
) : GLSurfaceView(context) {

    private val renderer = CurvedGalleryRenderer(context, drawableIds)
    private var lastX = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                lastX = event.x
                renderer.addDrag(dx)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
