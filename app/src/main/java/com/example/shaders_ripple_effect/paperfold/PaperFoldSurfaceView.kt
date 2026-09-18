package com.example.shaders_ripple_effect.paperfold

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

internal class PaperFoldSurfaceView(
    context: Context,
    textureResources: IntArray
) : GLSurfaceView(context) {

    private val paperRenderer = PaperFoldRenderer(this, textureResources)
    private var trackingPaper = false
    private var downX = 0f
    private var downY = 0f

    var onChapterChanged: (Int) -> Unit = {}
        set(value) {
            field = value
            paperRenderer.onChapterChanged = value
        }

    var onFoldingChanged: (Boolean) -> Unit = {}
        set(value) {
            field = value
            paperRenderer.onFoldingChanged = value
        }

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 8)
        setRenderer(paperRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true

        setOnTouchListener { _, event -> handleTouch(event) }
        setOnGenericMotionListener { _, event -> handleHover(event) }
    }

    fun setCardBounds(left: Float, top: Float, width: Float, height: Float) {
        paperRenderer.setCardBounds(left, top, width, height)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!paperRenderer.isInsideCard(event.x, event.y)) return false
                trackingPaper = true
                downX = event.x
                downY = event.y
                paperRenderer.updatePointer(event.x, event.y, active = true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!trackingPaper) return false
                paperRenderer.updatePointer(event.x, event.y, active = true)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!trackingPaper) return false
                paperRenderer.updatePointer(event.x, event.y, active = false)
                val tapSlop = 20f * resources.displayMetrics.density
                if (hypot(event.x - downX, event.y - downY) < tapSlop &&
                    paperRenderer.isInsideCard(event.x, event.y)
                ) {
                    paperRenderer.requestFold()
                    performClick()
                }
                trackingPaper = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                paperRenderer.setPointerActive(false)
                trackingPaper = false
                return true
            }
        }
        return false
    }

    private fun handleHover(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE -> {
                val inside = paperRenderer.isInsideCard(event.x, event.y)
                paperRenderer.updatePointer(event.x, event.y, active = inside)
                inside
            }

            MotionEvent.ACTION_HOVER_EXIT -> {
                paperRenderer.setPointerActive(false)
                true
            }

            else -> false
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private class PaperFoldRenderer(
    private val owner: PaperFoldSurfaceView,
    private val textureResources: IntArray
) : GLSurfaceView.Renderer {

    var onChapterChanged: (Int) -> Unit = {}
    var onFoldingChanged: (Boolean) -> Unit = {}

    @Volatile private var cardLeft = 0f
    @Volatile private var cardTop = 0f
    @Volatile private var cardWidth = 0f
    @Volatile private var cardHeight = 0f
    @Volatile private var pointerTargetX = 0.5f
    @Volatile private var pointerTargetY = 0.5f
    @Volatile private var pointerActive = false
    @Volatile private var foldRequested = false

    private var viewportWidth = 1
    private var viewportHeight = 1
    private var program = 0
    private var vao = 0
    private var indexCount = 0
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer
    private val textureIds = IntArray(textureResources.size)
    private val textureWidths = IntArray(textureResources.size)
    private val textureHeights = IntArray(textureResources.size)
    private val uniformLocations = mutableMapOf<String, Int>()

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val viewModel = FloatArray(16)
    private val mvp = FloatArray(16)

    private var startNanos = 0L
    private var foldStartNanos = 0L
    private var currentChapter = 0
    private var folding = false
    private var pointerX = 0.5f
    private var pointerY = 0.5f
    private var amplitude = 0.08f
    private var pointerAmplitude = 0.6f
    private var halftoneStrength = 0f
    private var morphProgress = 1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        createPaperMesh()
        loadTextures()
        startNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        Matrix.perspectiveM(
            projection,
            0,
            CAMERA_FOV_DEGREES,
            viewportWidth.toFloat() / viewportHeight,
            0.1f,
            1000f
        )
        Matrix.setLookAtM(view, 0, 0f, 0f, CAMERA_Z, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (cardWidth <= 0f || cardHeight <= 0f || textureIds.isEmpty()) return

        val now = System.nanoTime()
        val time = (now - startNanos) / 1_000_000_000f

        if (foldRequested && !folding) {
            foldRequested = false
            folding = true
            foldStartNanos = now
            halftoneStrength = 1f
            owner.post { onFoldingChanged(true) }
        }

        var foldProgress = 0f
        if (folding) {
            val rawProgress = ((now - foldStartNanos) / 1_000_000_000f / FOLD_DURATION_SECONDS)
                .coerceIn(0f, 1f)
            foldProgress = ((1.0 - cos(PI * rawProgress)) * 0.5).toFloat()
            val bell = when {
                rawProgress < 0.4f -> 1f - (1f - rawProgress / 0.4f).pow(4)
                rawProgress <= 0.6f -> 1f
                else -> ((1f - rawProgress) / 0.4f).coerceAtLeast(0f).pow(4)
            }
            amplitude = 0.5f * bell
            halftoneStrength = (1f - rawProgress / 0.35f).coerceIn(0f, 1f)
            morphProgress = (rawProgress / 0.35f).coerceIn(0f, 1f)

            if (rawProgress >= 1f) {
                currentChapter = (currentChapter + 1) % textureIds.size
                folding = false
                foldProgress = 0f
                amplitude = 0.08f
                halftoneStrength = 0f
                morphProgress = 1f
                owner.post {
                    onChapterChanged(currentChapter)
                    onFoldingChanged(false)
                }
            }
        } else {
            val ambientTarget = 0.08f + sin(time * 0.6f) * 0.02f
            amplitude += (ambientTarget - amplitude) * 0.05f
            val halfTarget = if (pointerActive) 0.7f else 0f
            halftoneStrength += (halfTarget - halftoneStrength) * 0.06f
            val morphTarget = if (pointerActive) 0f else 1f
            morphProgress += (morphTarget - morphProgress) * 0.08f
            val pointerAmpTarget = if (pointerActive) 0.9f else 0.6f
            pointerAmplitude += (pointerAmpTarget - pointerAmplitude) * 0.08f
        }

        pointerX += (pointerTargetX - pointerX) * 0.08f
        pointerY += (pointerTargetY - pointerY) * 0.08f

        updateMvp()
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uniform("uMvp"), 1, false, mvp, 0)
        GLES30.glUniform1f(uniform("uTime"), time)
        GLES30.glUniform1f(uniform("uAmplitude"), amplitude)
        GLES30.glUniform1f(uniform("uFoldProgress"), foldProgress)
        GLES30.glUniform2f(uniform("uFoldOrigin"), 0f, 0f)
        GLES30.glUniform2f(uniform("uPointer"), pointerX, pointerY)
        GLES30.glUniform1f(uniform("uPointerStrength"), 1f)
        GLES30.glUniform1f(uniform("uPointerAmplitude"), pointerAmplitude)
        GLES30.glUniform2f(uniform("uHalftoneSize"), cardWidth * 0.06f, cardHeight * 0.06f)
        GLES30.glUniform1f(uniform("uHalftoneDotSize"), 6.5f)
        GLES30.glUniform1f(uniform("uHalftoneStrength"), halftoneStrength)
        GLES30.glUniform3f(uniform("uHalftoneColor"), 0.95f, 0.92f, 0.88f)
        GLES30.glUniform1f(uniform("uMorphProgress"), morphProgress)
        GLES30.glUniform1f(uniform("uDistortionStrength"), 0.6f)
        GLES30.glUniform1f(uniform("uPointerActive"), if (pointerActive) 1f else 0f)
        GLES30.glUniform1f(uniform("uOpacity"), 1f)

        val imageAspect = textureWidths[currentChapter].toFloat() / textureHeights[currentChapter]
        val cardAspect = cardWidth / cardHeight
        if (imageAspect > cardAspect) {
            GLES30.glUniform2f(uniform("uUvScale"), cardAspect / imageAspect, 1f)
        } else {
            GLES30.glUniform2f(uniform("uUvScale"), 1f, imageAspect / cardAspect)
        }

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[currentChapter])
        GLES30.glUniform1i(uniform("uTexture"), 0)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun setCardBounds(left: Float, top: Float, width: Float, height: Float) {
        cardLeft = left
        cardTop = top
        cardWidth = width
        cardHeight = height
    }

    fun isInsideCard(x: Float, y: Float): Boolean =
        x >= cardLeft && x <= cardLeft + cardWidth && y >= cardTop && y <= cardTop + cardHeight

    fun updatePointer(x: Float, y: Float, active: Boolean) {
        if (cardWidth > 0f && cardHeight > 0f) {
            pointerTargetX = ((x - cardLeft) / cardWidth).coerceIn(0f, 1f)
            pointerTargetY = (1f - (y - cardTop) / cardHeight).coerceIn(0f, 1f)
        }
        pointerActive = active
    }

    fun setPointerActive(active: Boolean) {
        pointerActive = active
    }

    fun requestFold() {
        foldRequested = true
    }

    private fun updateMvp() {
        val aspect = viewportWidth.toFloat() / viewportHeight
        val worldHeight = 2f * CAMERA_Z * tan(Math.toRadians(CAMERA_FOV_DEGREES / 2.0)).toFloat()
        val worldWidth = worldHeight * aspect
        val paperWorldWidth = worldWidth * cardWidth / viewportWidth
        val paperWorldHeight = worldHeight * cardHeight / viewportHeight
        val centerX = cardLeft + cardWidth * 0.5f
        val centerY = cardTop + cardHeight * 0.5f
        val worldX = (centerX / viewportWidth - 0.5f) * worldWidth
        val worldY = (0.5f - centerY / viewportHeight) * worldHeight

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, worldX, worldY, 0f)
        Matrix.scaleM(model, 0, paperWorldWidth, paperWorldHeight, 1f)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
    }

    private fun createPaperMesh() {
        val rowSize = MESH_SEGMENTS + 1
        val vertices = FloatArray(rowSize * rowSize * 5)
        var vertexOffset = 0
        for (y in 0..MESH_SEGMENTS) {
            val v = y.toFloat() / MESH_SEGMENTS
            for (x in 0..MESH_SEGMENTS) {
                val u = x.toFloat() / MESH_SEGMENTS
                vertices[vertexOffset++] = u - 0.5f
                vertices[vertexOffset++] = v - 0.5f
                vertices[vertexOffset++] = 0f
                vertices[vertexOffset++] = u
                vertices[vertexOffset++] = v
            }
        }

        val indices = ShortArray(MESH_SEGMENTS * MESH_SEGMENTS * 6)
        var indexOffset = 0
        for (y in 0 until MESH_SEGMENTS) {
            for (x in 0 until MESH_SEGMENTS) {
                val bottomLeft = y * rowSize + x
                val bottomRight = bottomLeft + 1
                val topLeft = bottomLeft + rowSize
                val topRight = topLeft + 1
                indices[indexOffset++] = bottomLeft.toShort()
                indices[indexOffset++] = bottomRight.toShort()
                indices[indexOffset++] = topRight.toShort()
                indices[indexOffset++] = bottomLeft.toShort()
                indices[indexOffset++] = topRight.toShort()
                indices[indexOffset++] = topLeft.toShort()
            }
        }
        indexCount = indices.size

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
            .apply { position(0) }
        indexBuffer = ByteBuffer.allocateDirect(indices.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(indices)
            .apply { position(0) }

        val vaoIds = IntArray(1)
        val bufferIds = IntArray(2)
        GLES30.glGenVertexArrays(1, vaoIds, 0)
        GLES30.glGenBuffers(2, bufferIds, 0)
        vao = vaoIds[0]

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufferIds[0])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * Float.SIZE_BYTES,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1,
            2,
            GLES30.GL_FLOAT,
            false,
            5 * Float.SIZE_BYTES,
            3 * Float.SIZE_BYTES
        )
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bufferIds[1])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indices.size * Short.SIZE_BYTES,
            indexBuffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)
    }

    private fun loadTextures() {
        GLES30.glGenTextures(textureResources.size, textureIds, 0)
        textureResources.forEachIndexed { index, resourceId ->
            val bitmap = BitmapFactory.decodeResource(owner.resources, resourceId)
            textureWidths[index] = bitmap.width
            textureHeights[index] = bitmap.height
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[index])
            GLES30.glTexParameteri(
                GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR_MIPMAP_LINEAR
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            bitmap.recycle()
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun uniform(name: String): Int =
        uniformLocations.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val linkedProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(linkedProgram, vertexShader)
        GLES30.glAttachShader(linkedProgram, fragmentShader)
        GLES30.glLinkProgram(linkedProgram)
        val status = IntArray(1)
        GLES30.glGetProgramiv(linkedProgram, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) {
            "Paper fold program link failed: ${GLES30.glGetProgramInfoLog(linkedProgram)}"
        }
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return linkedProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        // GLSL ES requires #version to be the very first token; Kotlin raw strings include the
        // formatting newline used to keep these long shader sources readable.
        GLES30.glShaderSource(shader, source.trimStart())
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES30.GL_TRUE) {
            "Paper fold shader compilation failed: ${GLES30.glGetShaderInfoLog(shader)}"
        }
        return shader
    }

    private companion object {
        const val MESH_SEGMENTS = 90
        const val FOLD_DURATION_SECONDS = 1.8f
        const val CAMERA_Z = 5f
        const val CAMERA_FOV_DEGREES = 50f
    }
}

private const val VERTEX_SHADER = """
    #version 300 es
    precision highp float;

    layout(location = 0) in vec3 aPosition;
    layout(location = 1) in vec2 aUv;

    uniform mat4 uMvp;
    uniform float uTime;
    uniform float uAmplitude;
    uniform float uFoldProgress;
    uniform vec2 uFoldOrigin;
    uniform vec2 uPointer;
    uniform float uPointerStrength;
    uniform float uPointerAmplitude;
    uniform vec2 uHalftoneSize;
    uniform float uHalftoneDotSize;
    uniform float uHalftoneStrength;
    uniform float uMorphProgress;
    uniform float uDistortionStrength;
    uniform float uPointerActive;

    out vec2 vUv;
    out float vSurfaceDepth;

    mat4 rotation3d(vec3 axis, float angle) {
        axis = normalize(axis);
        float s = sin(angle), c = cos(angle), oc = 1.0 - c;
        return mat4(
            oc*axis.x*axis.x + c,          oc*axis.x*axis.y - axis.z*s, oc*axis.z*axis.x + axis.y*s, 0.0,
            oc*axis.x*axis.y + axis.z*s,   oc*axis.y*axis.y + c,        oc*axis.y*axis.z - axis.x*s, 0.0,
            oc*axis.z*axis.x - axis.y*s,   oc*axis.y*axis.z + axis.x*s, oc*axis.z*axis.z + c,        0.0,
            0.0, 0.0, 0.0, 1.0
        );
    }

    void main() {
        vUv = aUv;
        vec3 newPosition = aPosition;

        float cornerDist = length(aUv - uFoldOrigin);
        float cornerAnchor = smoothstep(0.0, 0.35, cornerDist);
        float foldLine = uFoldProgress * 2.2;
        float distFromFold = cornerDist - foldLine;
        float foldWidth = 0.85;
        float inFoldRegion = smoothstep(-foldWidth, 0.0, distFromFold)
            * (1.0 - smoothstep(0.0, foldWidth * 0.7, distFromFold));
        float foldAngle = 0.0;
        float creaseIntensity = 0.0;
        if (distFromFold < 0.0) {
            float fp = smoothstep(-foldWidth * 1.2, 0.0, distFromFold);
            foldAngle = fp * uFoldProgress * 0.7;
            creaseIntensity = inFoldRegion * cornerAnchor;
        } else {
            creaseIntensity = inFoldRegion * cornerAnchor;
        }
        float crease = creaseIntensity * uAmplitude * 0.7
            * (1.0 + sin(distFromFold * 4.0 + uTime * 2.0) * 0.1);

        float foldRotation = foldAngle * uAmplitude * 0.5;
        mat2 foldR = mat2(cos(foldRotation), -sin(foldRotation),
                          sin(foldRotation),  cos(foldRotation));
        float rotationMask = smoothstep(0.0, -foldWidth * 1.5, distFromFold);
        vec2 rotatedPos = mix(aPosition.xy, foldR * aPosition.xy, rotationMask * cornerAnchor);
        newPosition.xy = mix(aPosition.xy, rotatedPos, uFoldProgress * 0.35);

        float ripple1 = smoothstep(-foldWidth * 2.5, -foldWidth * 0.9, distFromFold)
            * (1.0 - smoothstep(-foldWidth * 0.9, 0.0, distFromFold));
        float w1 = ripple1 * uAmplitude * 0.18
            * sin(distFromFold * 7.5 + uTime * 2.0) * cornerAnchor;
        float ripple2 = smoothstep(-foldWidth * 4.0, -foldWidth * 2.2, distFromFold)
            * (1.0 - smoothstep(-foldWidth * 2.2, -foldWidth * 1.3, distFromFold));
        float w2 = ripple2 * uAmplitude * 0.12
            * sin(distFromFold * 6.0 + uTime * 1.5 + 1.2) * cornerAnchor;
        float ripple3 = smoothstep(-foldWidth * 6.0, -foldWidth * 3.5, distFromFold)
            * (1.0 - smoothstep(-foldWidth * 3.5, -foldWidth * 2.5, distFromFold));
        float w3 = ripple3 * uAmplitude * 0.08
            * sin(distFromFold * 4.5 + uTime * 1.1 + 2.3) * cornerAnchor;
        float settlingWave = w1 + w2 + w3;

        float pointerDist = length(aUv - uPointer);
        float rippleFalloff = smoothstep(0.35, 0.0, pointerDist);
        float pointerSurfaceRipple = pow(rippleFalloff, 2.0)
            * (sin(pointerDist * 12.0 - uTime * 0.3) * 0.5 + 0.5)
            * uDistortionStrength * uPointerActive * 0.08;
        float pointerFalloff = smoothstep(0.6, 0.0, pointerDist);
        float pointerBend = pow(pointerFalloff, 1.8);
        float pointerInfluence = mix(0.025, 1.0, uPointerActive);
        float pointerRipple = (pointerBend * uPointerStrength * uPointerAmplitude * 0.7
            + pointerBend * uPointerStrength * uPointerAmplitude * 0.2
            * sin(pointerDist * 12.0 + uTime * 3.0)) * pointerInfluence;

        float wave1 = sin(aUv.x * 3.14159 * 3.2 + uTime * 1.1)
            * sin(aUv.y * 3.14159 * 2.8 + uTime * 0.9)
            * uAmplitude * 0.06 * (1.0 - uFoldProgress * 0.5);
        float wave2 = sin((aUv.x + aUv.y) * 3.14159 * 2.0 + uTime * 0.7)
            * sin((aUv.x - aUv.y) * 3.14159 * 1.8 + uTime * 0.5)
            * uAmplitude * 0.04 * (1.0 - uFoldProgress * 0.4);
        float gravityDrape = sin(aUv.y * 3.14159 * 1.5)
            * uAmplitude * 0.03 * (1.0 - uFoldProgress * 0.7);

        float halftoneAngle = 0.35;
        mat2 halftoneR = mat2(cos(halftoneAngle), -sin(halftoneAngle),
                              sin(halftoneAngle),  cos(halftoneAngle));
        vec2 halftoneCoords = (aUv - 0.5) * uHalftoneSize;
        vec2 halftoneUv = (halftoneR * halftoneCoords + 0.5 * uHalftoneSize)
            / uHalftoneDotSize;
        float dotDistance = length(fract(halftoneUv) - 0.5);
        float halftoneDot = smoothstep(0.48, 0.2, dotDistance);
        float halftoneDisplacement = uMorphProgress * uHalftoneStrength
            * (halftoneDot - 0.5) * 0.08;

        newPosition.z = pointerRipple + pointerSurfaceRipple + halftoneDisplacement
            + gravityDrape + settlingWave + wave1 + wave2 + crease;

        float flipProgress = smoothstep(
            aUv.x * 0.3,
            0.8 + aUv.x * 0.2,
            uFoldProgress
        );
        newPosition = (rotation3d(vec3(0.5 * (1.0 - flipProgress), 1.0, 0.0),
            3.14159 * flipProgress) * vec4(newPosition, 1.0)).xyz;

        vSurfaceDepth = newPosition.z;
        gl_Position = uMvp * vec4(newPosition, 1.0);
    }
"""

private const val FRAGMENT_SHADER = """
    #version 300 es
    precision highp float;

    uniform sampler2D uTexture;
    uniform vec2 uUvScale;
    uniform float uOpacity;
    uniform vec2 uPointer;
    uniform vec2 uHalftoneSize;
    uniform float uHalftoneDotSize;
    uniform float uHalftoneStrength;
    uniform vec3 uHalftoneColor;
    uniform float uMorphProgress;
    uniform float uTime;

    in vec2 vUv;
    in float vSurfaceDepth;
    out vec4 outColor;

    float hash(vec2 p) {
        return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    }

    void main() {
        vec2 paperUv = vUv;
        if (!gl_FrontFacing) paperUv.x = 1.0 - paperUv.x;
        paperUv.y = 1.0 - paperUv.y;
        vec2 uv = (paperUv - 0.5) * uUvScale + 0.5;
        vec4 color = texture(uTexture, uv);
        float luma = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));

        float halftoneAngle = 0.35;
        mat2 halftoneR = mat2(cos(halftoneAngle), -sin(halftoneAngle),
                              sin(halftoneAngle),  cos(halftoneAngle));
        vec2 halftoneCoords = (paperUv - 0.5) * uHalftoneSize;
        vec2 halftoneUv = (halftoneR * halftoneCoords + 0.5 * uHalftoneSize)
            / uHalftoneDotSize;
        float dotDistance = length(fract(halftoneUv) - 0.5);
        float dotRadius = mix(0.26, 0.68, 1.0 - luma);
        float dotMask = smoothstep(dotRadius, dotRadius - 0.02, dotDistance);

        float pointerDist = distance(vUv, uPointer);
        float revealEasing = uHalftoneStrength < 0.5
            ? 2.0 * uHalftoneStrength * uHalftoneStrength
            : 1.0 - 2.0 * (1.0 - uHalftoneStrength) * (1.0 - uHalftoneStrength);
        float revealRadius = revealEasing * 1.4;
        float revealNoise = (hash((vUv - uPointer) * 60.0) - 0.5) * 0.04;
        float revealMask = 1.0 - smoothstep(
            revealRadius - 0.4,
            revealRadius,
            pointerDist + revealNoise
        );

        float halftoneBlend = clamp(
            smoothstep(0.0, 1.0, uHalftoneStrength) * (1.0 - uMorphProgress) * 1.2,
            0.0,
            1.0
        ) * revealMask;
        vec3 paperColor = mix(color.rgb, uHalftoneColor, 0.85);
        vec3 halftoneDots = mix(paperColor, color.rgb, dotMask);
        vec3 finalColor = mix(color.rgb, halftoneDots, halftoneBlend);
        finalColor = mix(finalColor, color.rgb, uMorphProgress);

        float fiber1 = hash(uv * 900.0);
        float fiber2 = hash(uv * 240.0 + 5.7);
        float grain = (fiber1 - 0.5) * 0.06 + (fiber2 - 0.5) * 0.035;
        float streak = (hash(vec2(floor(uv.y * 600.0), 3.1)) - 0.5) * 0.02;
        finalColor += grain + streak;

        vec3 warmPaper = vec3(1.02, 0.98, 0.92);
        finalColor = mix(finalColor, finalColor * warmPaper, halftoneBlend * 0.4);
        finalColor *= 0.96 + clamp(vSurfaceDepth * 0.12, -0.08, 0.12);
        if (!gl_FrontFacing) finalColor = mix(finalColor, vec3(0.90, 0.87, 0.82), 0.16);

        outColor = vec4(finalColor, color.a * uOpacity);
    }
"""
