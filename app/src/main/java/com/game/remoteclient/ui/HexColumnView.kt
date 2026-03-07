package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.AttributeSet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class HexColumnView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    companion object {
        private const val FACE_COUNT = 6
        private const val FACE_RAD = (2.0 * Math.PI / FACE_COUNT).toFloat()
        private const val SUBDIV_H = 4
        private const val SUBDIV_V = 6

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform float uShade;
            varying vec2 vTexCoord;
            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);
                gl_FragColor = vec4(color.rgb * uShade, color.a);
            }
        """
    }

    // Public state
    private var answers = listOf<String>()
    private var currentIndex = 0
    private var bodyColor = Color.parseColor("#C2185B")
    private var pressedFace = -1 // -1 = none pressed

    // Rotation animation
    private var currentRotation = 0f
    private var targetRotation = 0f
    private var animating = false

    // GL handles
    private var program = 0
    private var mvpMatrixHandle = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureHandle = 0
    private var shadeHandle = 0

    // Geometry — subdivided screen and body strips, plus caps
    private lateinit var screenVertBuf: FloatBuffer
    private lateinit var screenTexBuf: FloatBuffer
    private lateinit var screenIdxBuf: ShortBuffer
    private var screenIdxCount = 0

    private lateinit var bodyVertBuf: FloatBuffer
    private lateinit var bodyTexBuf: FloatBuffer
    private lateinit var bodyIdxBuf: ShortBuffer
    private var bodyIdxCount = 0

    private lateinit var topCapVertBuf: FloatBuffer
    private lateinit var topCapIdxBuf: ShortBuffer
    private lateinit var botCapVertBuf: FloatBuffer
    private lateinit var botCapIdxBuf: ShortBuffer

    // Per-face draw info
    private val vertsPerFace = (SUBDIV_H + 1) * (SUBDIV_V + 1)
    private val indicesPerFace = SUBDIV_H * SUBDIV_V * 6

    // Textures
    private val faceTextures = IntArray(FACE_COUNT)
    private val bodyTextures = IntArray(FACE_COUNT)
    private var topCapTexture = 0
    private var bottomCapTexture = 0
    @Volatile private var texturesDirty = true

    // Viewport size (for hit-testing)
    private var viewportW = 0
    private var viewportH = 0

    // Matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    // Column dimensions (GL units)
    private val columnRadius = 1.2f
    private val screenHeight = 1.1f
    private val bodyHeight = 0.45f
    private val totalHeight = screenHeight + bodyHeight
    private val screenY = totalHeight / 2f
    private val bodyY = screenY - screenHeight
    private val bottomY = -totalHeight / 2f

    // Face colors
    private val faceColors = intArrayOf(
        Color.parseColor("#66BB6A"),
        Color.parseColor("#42A5F5"),
        Color.parseColor("#FF8A65"),
        Color.parseColor("#AB47BC"),
        Color.parseColor("#FFCA28"),
        Color.parseColor("#EC407A"),
    )
    private val screenColor = Color.parseColor("#F5F0E0")
    private val bezelColor = Color.parseColor("#8D6E63")

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // ---- Public API ----

    fun setAnswerList(list: List<String>) {
        answers = list
        currentIndex = 0
        currentRotation = 0f
        targetRotation = 0f
        animating = false
        pressedFace = -1
        texturesDirty = true
    }

    fun getCurrentAnswer(): String {
        if (answers.isEmpty()) return ""
        return answers[currentIndex % answers.size]
    }

    fun setIndex(index: Int) {
        currentIndex = index.coerceIn(0, FACE_COUNT - 1)
        currentRotation = -currentIndex * FACE_RAD
        targetRotation = currentRotation
        animating = false
    }

    fun next() {
        if (answers.isEmpty()) return
        if (animating) currentRotation = targetRotation
        currentIndex = (currentIndex + 1) % FACE_COUNT
        targetRotation -= FACE_RAD
        animating = true
    }

    fun prev() {
        if (answers.isEmpty()) return
        if (animating) currentRotation = targetRotation
        currentIndex = (currentIndex - 1 + FACE_COUNT) % FACE_COUNT
        targetRotation += FACE_RAD
        animating = true
    }

    fun setBodyColor(color: Int) {
        bodyColor = color
        texturesDirty = true
    }

    fun pressCurrentFace() {
        pressedFace = currentIndex
        texturesDirty = true
    }

    /** Check if a tap (in this view's coordinate space) hits the front face's screen area. */
    fun isTapOnFrontFace(touchX: Float, touchY: Float): Boolean {
        if (answers.isEmpty() || viewportW == 0) return false

        // Use targetRotation (set on UI thread) for stable hit-testing
        val rot = targetRotation
        val m = FloatArray(16)
        val v = FloatArray(16)
        val p = FloatArray(16)
        val mv = FloatArray(16)
        val mvp = FloatArray(16)

        android.opengl.Matrix.setLookAtM(v, 0, 0f, 0.6f, 4.2f, 0f, 0f, 0f, 0f, 1f, 0f)
        android.opengl.Matrix.perspectiveM(p, 0, 38f, viewportW.toFloat() / viewportH, 1f, 20f)
        android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.rotateM(m, 0, Math.toDegrees(rot.toDouble()).toFloat(), 0f, 1f, 0f)
        android.opengl.Matrix.multiplyMM(mv, 0, v, 0, m, 0)
        android.opengl.Matrix.multiplyMM(mvp, 0, p, 0, mv, 0)

        // Front face screen-section corners in model space
        val fi = currentIndex
        val a0 = fi * FACE_RAD - FACE_RAD / 2f
        val a1 = fi * FACE_RAD + FACE_RAD / 2f
        val corners = arrayOf(
            floatArrayOf(columnRadius * sin(a0), screenY, columnRadius * cos(a0), 1f),
            floatArrayOf(columnRadius * sin(a1), screenY, columnRadius * cos(a1), 1f),
            floatArrayOf(columnRadius * sin(a1), bodyY,   columnRadius * cos(a1), 1f),
            floatArrayOf(columnRadius * sin(a0), bodyY,   columnRadius * cos(a0), 1f),
        )

        val screenPts = corners.map { pt ->
            val clip = FloatArray(4)
            android.opengl.Matrix.multiplyMV(clip, 0, mvp, 0, pt, 0)
            val ndcX = clip[0] / clip[3]
            val ndcY = clip[1] / clip[3]
            floatArrayOf(
                (ndcX + 1f) / 2f * viewportW,
                (1f - ndcY) / 2f * viewportH
            )
        }

        return pointInConvexQuad(touchX, touchY, screenPts)
    }

    private fun pointInConvexQuad(px: Float, py: Float, quad: List<FloatArray>): Boolean {
        var sign = 0
        for (i in quad.indices) {
            val j = (i + 1) % quad.size
            val ex = quad[j][0] - quad[i][0]
            val ey = quad[j][1] - quad[i][1]
            val cross = ex * (py - quad[i][1]) - ey * (px - quad[i][0])
            if (cross > 0f) { if (sign < 0) return false; sign = 1 }
            else if (cross < 0f) { if (sign > 0) return false; sign = -1 }
        }
        return true
    }

    // ---- GLSurfaceView.Renderer ----

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        shadeHandle = GLES20.glGetUniformLocation(program, "uShade")

        buildGeometry()
        texturesDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width
        viewportH = height
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height
        android.opengl.Matrix.perspectiveM(projMatrix, 0, 38f, aspect, 1f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (animating) {
            val diff = targetRotation - currentRotation
            if (abs(diff) < 0.01f) {
                currentRotation = targetRotation
                animating = false
            } else {
                currentRotation += diff * 0.25f
            }
        }

        if (texturesDirty) {
            rebuildTextures()
            texturesDirty = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        android.opengl.Matrix.setLookAtM(viewMatrix, 0,
            0f, 0.6f, 4.2f,
            0f, 0.0f, 0f,
            0f, 1f, 0f)

        android.opengl.Matrix.setIdentityM(modelMatrix, 0)
        android.opengl.Matrix.rotateM(modelMatrix, 0,
            Math.toDegrees(currentRotation.toDouble()).toFloat(), 0f, 1f, 0f)

        android.opengl.Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tempMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        for (i in 0 until FACE_COUNT) {
            val faceAngle = currentRotation + i * FACE_RAD
            val faceDot = cos(faceAngle.toDouble()).toFloat()
            if (faceDot < -0.15f) continue

            val shade = 0.5f + 0.5f * faceDot.coerceIn(0f, 1f)
            GLES20.glUniform1f(shadeHandle, shade)

            bindTexture(faceTextures[i])
            drawFaceStrip(i, screenVertBuf, screenTexBuf, screenIdxBuf)

            bindTexture(bodyTextures[i])
            drawFaceStrip(i, bodyVertBuf, bodyTexBuf, bodyIdxBuf)
        }

        GLES20.glUniform1f(shadeHandle, 0.9f)
        bindTexture(topCapTexture)
        drawCap(topCapVertBuf, topCapIdxBuf)

        GLES20.glUniform1f(shadeHandle, 0.4f)
        bindTexture(bottomCapTexture)
        drawCap(botCapVertBuf, botCapIdxBuf)
    }

    // ---- Geometry ----

    private fun buildGeometry() {
        buildStrip(screenY, bodyY).let { (v, t, i) ->
            screenVertBuf = floatBuffer(v)
            screenTexBuf = floatBuffer(t)
            screenIdxBuf = shortBuffer(i)
            screenIdxCount = i.size
        }
        buildStrip(bodyY, bottomY).let { (v, t, i) ->
            bodyVertBuf = floatBuffer(v)
            bodyTexBuf = floatBuffer(t)
            bodyIdxBuf = shortBuffer(i)
            bodyIdxCount = i.size
        }
        buildCap(screenY, true)
        buildCap(bottomY, false)
    }

    private fun buildStrip(topY: Float, botY: Float): Triple<FloatArray, FloatArray, ShortArray> {
        val sh = SUBDIV_H
        val sv = SUBDIV_V
        val vpf = vertsPerFace
        val ipf = indicesPerFace

        val verts = FloatArray(FACE_COUNT * vpf * 3)
        val texCs = FloatArray(FACE_COUNT * vpf * 2)
        val idxs = ShortArray(FACE_COUNT * ipf)

        for (face in 0 until FACE_COUNT) {
            val a0 = face * FACE_RAD - FACE_RAD / 2f
            val a1 = face * FACE_RAD + FACE_RAD / 2f
            val vo = face * vpf * 3
            val to = face * vpf * 2
            val baseV = face * vpf

            for (row in 0..sv) {
                val v = row.toFloat() / sv
                val y = topY + (botY - topY) * v
                for (col in 0..sh) {
                    val u = col.toFloat() / sh
                    val a = a0 + (a1 - a0) * u
                    val vi = vo + (row * (sh + 1) + col) * 3
                    verts[vi] = columnRadius * sin(a)
                    verts[vi + 1] = y
                    verts[vi + 2] = columnRadius * cos(a)

                    val ti = to + (row * (sh + 1) + col) * 2
                    texCs[ti] = u; texCs[ti + 1] = v
                }
            }

            var idx = face * ipf
            for (row in 0 until sv) {
                for (col in 0 until sh) {
                    val tl = baseV + row * (sh + 1) + col
                    val tr = tl + 1
                    val bl = tl + (sh + 1)
                    val br = bl + 1
                    // CCW from outside
                    idxs[idx++] = tl.toShort(); idxs[idx++] = bl.toShort(); idxs[idx++] = br.toShort()
                    idxs[idx++] = tl.toShort(); idxs[idx++] = br.toShort(); idxs[idx++] = tr.toShort()
                }
            }
        }
        return Triple(verts, texCs, idxs)
    }

    private fun buildCap(y: Float, isTop: Boolean) {
        val verts = FloatArray((FACE_COUNT + 1) * 3)
        verts[0] = 0f; verts[1] = y; verts[2] = 0f
        for (i in 0 until FACE_COUNT) {
            val a = i * FACE_RAD - FACE_RAD / 2f
            val vi = (i + 1) * 3
            verts[vi] = columnRadius * sin(a)
            verts[vi + 1] = y
            verts[vi + 2] = columnRadius * cos(a)
        }
        val idxs = ShortArray(FACE_COUNT * 3)
        for (i in 0 until FACE_COUNT) {
            val ii = i * 3
            idxs[ii] = 0
            if (isTop) {
                idxs[ii + 1] = (i + 1).toShort()
                idxs[ii + 2] = ((i + 1) % FACE_COUNT + 1).toShort()
            } else {
                idxs[ii + 1] = ((i + 1) % FACE_COUNT + 1).toShort()
                idxs[ii + 2] = (i + 1).toShort()
            }
        }
        if (isTop) {
            topCapVertBuf = floatBuffer(verts)
            topCapIdxBuf = shortBuffer(idxs)
        } else {
            botCapVertBuf = floatBuffer(verts)
            botCapIdxBuf = shortBuffer(idxs)
        }
    }

    // ---- Textures ----

    private fun rebuildTextures() {
        val allTex = faceTextures + bodyTextures + intArrayOf(topCapTexture, bottomCapTexture)
        val nonZero = allTex.filter { it != 0 }.toIntArray()
        if (nonZero.isNotEmpty()) GLES20.glDeleteTextures(nonZero.size, nonZero, 0)

        val texSize = 512

        for (i in 0 until FACE_COUNT) {
            val answerIdx = i % answers.size.coerceAtLeast(1)
            val answer = if (answers.isNotEmpty()) answers[answerIdx] else ""
            val pressed = (i == pressedFace)
            faceTextures[i] = createFaceTexture(texSize, answer, faceColors[i], pressed)
            bodyTextures[i] = createBodyTexture(texSize)
        }

        topCapTexture = createCapTexture(texSize, shade(bezelColor, 0.8f))
        bottomCapTexture = createCapTexture(texSize, shade(bodyColor, 0.6f))
    }

    private fun createFaceTexture(size: Int, text: String, accentColor: Int, pressed: Boolean): Int {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dark background
        c.drawColor(shade(bezelColor, 0.5f))

        val margin = size * 0.06f
        val cornerR = size * 0.10f

        if (pressed) {
            // Pressed: inset shadow at top, no drop shadow, darker colors
            val btnRect = RectF(margin, margin + size * 0.02f, size - margin, size - margin)
            paint.color = shade(accentColor, 0.65f)
            c.drawRoundRect(btnRect, cornerR, cornerR, paint)

            // Inner shadow at top (pressed-in look)
            val innerShadow = RectF(margin, margin + size * 0.02f, size - margin, margin + size * 0.10f)
            paint.color = Color.parseColor("#40000000")
            c.drawRoundRect(innerShadow, cornerR, cornerR, paint)

            // Screen area (slightly smaller, shifted down)
            val screenMargin = size * 0.13f
            val screenRect = RectF(screenMargin, screenMargin + size * 0.02f,
                size - screenMargin, size - screenMargin)
            val screenR = cornerR * 0.6f
            paint.color = shade(screenColor, 0.85f)
            c.drawRoundRect(screenRect, screenR, screenR, paint)

            // Border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.012f
            paint.color = shade(accentColor, 0.5f)
            c.drawRoundRect(screenRect, screenR, screenR, paint)
            paint.style = Paint.Style.FILL

            // Text (slightly shifted down)
            drawWrappedText(c, text, size, screenMargin, Color.parseColor("#555555"), size * 0.01f)
        } else {
            // Raised: drop shadow, highlight, full color
            val shadowOff = size * 0.02f
            val shadowRect = RectF(margin + shadowOff, margin + shadowOff * 1.5f,
                size - margin + shadowOff, size - margin + shadowOff * 1.5f)
            paint.color = Color.parseColor("#60000000")
            c.drawRoundRect(shadowRect, cornerR, cornerR, paint)

            val btnRect = RectF(margin, margin, size - margin, size - margin)
            paint.color = accentColor
            c.drawRoundRect(btnRect, cornerR, cornerR, paint)

            // Top highlight
            val highlightRect = RectF(margin, margin, size - margin, margin + size * 0.08f)
            paint.color = Color.parseColor("#40FFFFFF")
            c.drawRoundRect(highlightRect, cornerR, cornerR, paint)

            // Bottom shadow
            val bottomRect = RectF(margin, size - margin - size * 0.06f, size - margin, size - margin)
            paint.color = Color.parseColor("#30000000")
            c.drawRoundRect(bottomRect, cornerR, cornerR, paint)

            // Screen area
            val screenMargin = size * 0.12f
            val screenRect = RectF(screenMargin, screenMargin, size - screenMargin, size - screenMargin)
            val screenR = cornerR * 0.6f
            paint.color = screenColor
            c.drawRoundRect(screenRect, screenR, screenR, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.012f
            paint.color = shade(accentColor, 0.7f)
            c.drawRoundRect(screenRect, screenR, screenR, paint)
            paint.style = Paint.Style.FILL

            // Text
            drawWrappedText(c, text, size, screenMargin, Color.parseColor("#333333"), 0f)
        }

        return uploadTexture(bmp)
    }

    private fun drawWrappedText(c: Canvas, text: String, size: Int, screenMargin: Float, color: Int, yOffset: Float) {
        val maxWidth = (size - screenMargin * 2.5f).toInt()
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.22f
        }

        // Shrink font until text fits within 3 lines max
        var layout = buildStaticLayout(text, tp, maxWidth)
        while (layout.lineCount > 3 && tp.textSize > size * 0.1f) {
            tp.textSize -= 2f
            layout = buildStaticLayout(text, tp, maxWidth)
        }

        val textHeight = layout.height.toFloat()
        val centerY = size / 2f + yOffset
        c.save()
        c.translate(size / 2f, centerY - textHeight / 2f)
        layout.draw(c)
        c.restore()
    }

    private fun buildStaticLayout(text: String, tp: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()

    private fun createBodyTexture(size: Int): Int {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bodyColor)

        val ribPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        ribPaint.color = shade(bodyColor, 0.75f)
        ribPaint.style = Paint.Style.STROKE
        ribPaint.strokeWidth = 3f
        val ribCount = 6
        for (i in 1..ribCount) {
            val y = size * i.toFloat() / (ribCount + 1)
            c.drawLine(4f, y, size - 4f, y, ribPaint)
        }

        return uploadTexture(bmp)
    }

    private fun createCapTexture(size: Int, color: Int): Int {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return uploadTexture(bmp)
    }

    private fun uploadTexture(bmp: Bitmap): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return texIds[0]
    }

    // ---- Drawing ----

    private fun drawFaceStrip(faceIndex: Int, vertBuf: FloatBuffer, texBuf: FloatBuffer, idxBuf: ShortBuffer) {
        val vertOffset = faceIndex * vertsPerFace * 3
        val texOffset = faceIndex * vertsPerFace * 2
        val idxOffset = faceIndex * indicesPerFace

        vertBuf.position(vertOffset)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertBuf)
        GLES20.glEnableVertexAttribArray(positionHandle)

        texBuf.position(texOffset)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuf)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        idxBuf.position(idxOffset)
        // Indices reference absolute vertex positions; we offset the buffer but GL sees indices
        // from 0, so we need local indices. Since we set vertBuf.position, vertex 0 in the
        // attrib pointer is the first vertex of this face. Indices must be relative.
        // We built them as absolute, so we need to draw differently.
        // Use glDrawArrays with the index buffer rewritten as local, OR use a VBO approach.
        // Simplest: draw with absolute indices by NOT offsetting vertex buffers.

        // Reset to start, use absolute positioning
        vertBuf.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertBuf)
        texBuf.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuf)

        idxBuf.position(idxOffset)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indicesPerFace, GLES20.GL_UNSIGNED_SHORT, idxBuf)
    }

    private fun drawCap(vertBuf: FloatBuffer, idxBuf: ShortBuffer) {
        vertBuf.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertBuf)
        GLES20.glEnableVertexAttribArray(positionHandle)

        // Cap UVs: all map to texture center
        val dummyUV = FloatArray((FACE_COUNT + 1) * 2) { 0.5f }
        val uvBuf = floatBuffer(dummyUV)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, uvBuf)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        idxBuf.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, FACE_COUNT * 3, GLES20.GL_UNSIGNED_SHORT, idxBuf)
    }

    private fun bindTexture(texId: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(textureHandle, 0)
    }

    // ---- Shaders ----

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vert = loadShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val frag = loadShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vert)
        GLES20.glAttachShader(prog, frag)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun loadShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    // ---- Buffers ----

    private fun floatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
            .apply { position(0) }

    private fun shortBuffer(data: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(data)
            .apply { position(0) }

    private fun shade(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )
}
