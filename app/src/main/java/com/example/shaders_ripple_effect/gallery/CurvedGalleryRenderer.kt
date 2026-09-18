package com.example.shaders_ripple_effect.gallery

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/**
 * Images coiled around a full vertical-axis cylinder. Each card is a subdivided
 * [GRID] x [GRID] plane whose vertices are placed on the cylinder in a real GLSL
 * vertex shader, so the card facing the camera is flat/front-on while its
 * neighbours curve away and recede into depth around the barrel.
 *
 * The cards tile the whole 360° of the cylinder, so dragging spins it in an
 * endless loop — cards leaving one side come back around the other. Scrolling is
 * free and continuous with inertia (no snapping, no wobble).
 */
class CurvedGalleryRenderer(
    private val context: Context,
    private val drawableIds: IntArray
) : GLSurfaceView.Renderer {

    // --- Tunables ---
    @Volatile var radius = 3.0f          // cylinder radius
    @Volatile var cardCount = 10         // cards tiled around the full circle
    @Volatile var cameraDistance = 4.0f
    @Volatile var fovDegrees = 45f
    @Volatile var anglePerPixel = 0.004f // drag sensitivity (radians per pixel)
    @Volatile var friction = 0.94f       // inertia decay after release

    private val cardHalfHeight = 1.1f    // world units (image height)

    // --- Rotation state (GL thread owns angle/velocity) ---
    private val scrollLock = Any()
    private var queuedDeltaPixels = 0f
    private var scrollAngle = 0f
    private var angularVelocity = 0f

    // --- Program handles ---
    private var program = 0
    private var aPosition = 0
    private var aUv = 0
    private var uProjection = 0
    private var uView = 0
    private var uCardAngle = 0
    private var uHalfAngle = 0
    private var uRadius = 0
    private var uTexture = 0

    private var vbo = 0
    private var ibo = 0
    private var indexCount = 0

    private lateinit var textures: IntArray
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    /** Accumulate a horizontal finger delta (pixels) from the UI thread. */
    fun addDrag(dxPixels: Float) {
        synchronized(scrollLock) { queuedDeltaPixels += dxPixels }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.05f, 0.05f, 0.06f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aUv = GLES20.glGetAttribLocation(program, "aUv")
        uProjection = GLES20.glGetUniformLocation(program, "uProjection")
        uView = GLES20.glGetUniformLocation(program, "uView")
        uCardAngle = GLES20.glGetUniformLocation(program, "uCardAngle")
        uHalfAngle = GLES20.glGetUniformLocation(program, "uHalfAngle")
        uRadius = GLES20.glGetUniformLocation(program, "uRadius")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")

        buildMesh()
        loadTextures()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, fovDegrees, aspect, 0.1f, 100f)
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, cameraDistance,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        updateScroll()

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uProjection, 1, false, projectionMatrix, 0)
        GLES20.glUniformMatrix4fv(uView, 1, false, viewMatrix, 0)
        GLES20.glUniform1f(uRadius, radius)

        val count = cardCount.coerceAtLeast(1)
        val angleStep = (2.0 * Math.PI / count).toFloat()
        GLES20.glUniform1f(uHalfAngle, angleStep * 0.5f)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, STRIDE, 0)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, STRIDE, 2 * 4)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(uTexture, 0)

        for (k in 0 until count) {
            val cardAngle = k * angleStep + scrollAngle
            GLES20.glUniform1f(uCardAngle, cardAngle)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[k % textures.size])
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        }

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aUv)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun updateScroll() {
        val delta: Float
        synchronized(scrollLock) {
            delta = queuedDeltaPixels
            queuedDeltaPixels = 0f
        }

        if (delta != 0f) {
            val dAngle = delta * anglePerPixel
            scrollAngle += dAngle
            angularVelocity = dAngle
        } else {
            scrollAngle += angularVelocity
            angularVelocity *= friction
            if (abs(angularVelocity) < 1e-6f) angularVelocity = 0f
        }
        // Infinite loop: keep the angle bounded without any visible jump.
        val twoPi = (2.0 * Math.PI).toFloat()
        if (scrollAngle > twoPi) scrollAngle -= twoPi
        if (scrollAngle < -twoPi) scrollAngle += twoPi
    }

    private fun buildMesh() {
        // aPosition.x is normalised width in [-1, 1] (mapped to an angular span in
        // the shader); aPosition.y is world-space height.
        val cols = GRID + 1
        val vertexData = FloatArray(cols * cols * 4)
        var v = 0
        for (j in 0..GRID) {
            val ty = j.toFloat() / GRID
            val y = -cardHalfHeight + ty * (2f * cardHalfHeight)
            for (i in 0..GRID) {
                val tx = i.toFloat() / GRID
                val nx = tx * 2f - 1f
                vertexData[v++] = nx
                vertexData[v++] = y
                vertexData[v++] = tx
                vertexData[v++] = ty
            }
        }

        val indices = ShortArray(GRID * GRID * 6)
        var k = 0
        for (j in 0 until GRID) {
            for (i in 0 until GRID) {
                val topLeft = (j * cols + i).toShort()
                val topRight = (j * cols + i + 1).toShort()
                val bottomLeft = ((j + 1) * cols + i).toShort()
                val bottomRight = ((j + 1) * cols + i + 1).toShort()
                indices[k++] = topLeft
                indices[k++] = bottomLeft
                indices[k++] = topRight
                indices[k++] = topRight
                indices[k++] = bottomLeft
                indices[k++] = bottomRight
            }
        }
        indexCount = indices.size

        val vBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
        vBuffer.position(0)

        val iBuffer: ShortBuffer = ByteBuffer
            .allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
        iBuffer.position(0)

        val buffers = IntArray(2)
        GLES20.glGenBuffers(2, buffers, 0)
        vbo = buffers[0]
        ibo = buffers[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.size * 4, vBuffer, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, iBuffer, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun loadTextures() {
        textures = IntArray(drawableIds.size)
        GLES20.glGenTextures(textures.size, textures, 0)
        val options = BitmapFactory.Options().apply { inScaled = false }
        for (i in drawableIds.indices) {
            val bitmap = BitmapFactory.decodeResource(context.resources, drawableIds[i], options)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Program link failed: " + GLES20.glGetProgramInfoLog(prog) }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private companion object {
        const val GRID = 32
        const val STRIDE = 4 * 4 // 4 floats per vertex

        val VERTEX_SHADER = """
            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform float uCardAngle;
            uniform float uHalfAngle;
            uniform float uRadius;

            attribute vec2 aPosition; // x: normalised width [-1,1], y: world height
            attribute vec2 aUv;

            varying vec2 vUv;
            varying float vShade;

            void main() {
                // Angular position of this vertex around the cylinder.
                float angle = uCardAngle + aPosition.x * uHalfAngle;
                float s = sin(angle);
                float c = cos(angle);

                // Cylinder: front (angle 0) sits at z = 0 facing the camera;
                // cards curve away and recede as the angle grows.
                vec3 pos = vec3(uRadius * s, aPosition.y, uRadius * c - uRadius);

                // Front cards are brightest; cards turning away fall into shadow.
                vShade = mix(0.25, 1.0, clamp(c, 0.0, 1.0));

                gl_Position = uProjection * uView * vec4(pos, 1.0);
                vUv = aUv;
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vUv;
            varying float vShade;
            void main() {
                vec4 tex = texture2D(uTexture, vec2(vUv.x, 1.0 - vUv.y));
                gl_FragColor = vec4(tex.rgb * vShade, tex.a);
            }
        """.trimIndent()
    }
}
