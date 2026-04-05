package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BugOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onBugCleared: (() -> Unit)? = null

    private data class Bug(
        var x: Float,
        var y: Float,
        var wobblePhase: Float,
        var tapsRemaining: Int = 2
    )

    private val bugs = mutableListOf<Bug>()
    private var bugRadius = 0f
    private var active = false
    private var density = 1f
    private var animating = false
    private var vortexRotation = 0f
    private val rng = Random(System.currentTimeMillis())

    private data class ScrambledRect(
        val x: Float, val y: Float,
        val width: Float, val height: Float,
        val rotation: Float,
        val color: Int,
        val text: String
    )
    private val scrambledRects = mutableListOf<ScrambledRect>()

    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fuzzPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val antennaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80E0FF")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val antennaTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80E0FF")
        style = Paint.Style.FILL
    }

    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rectTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#BB3A1560")
        style = Paint.Style.FILL
    }

    fun activate(count: Int) {
        density = resources.displayMetrics.density
        active = true
        visibility = VISIBLE

        post {
            bugRadius = width * 0.12f
            bugs.clear()
            val w = width.toFloat()
            val h = height.toFloat()
            val margin = bugRadius * 1.5f
            for (i in 0 until count.coerceIn(1, 6)) {
                bugs.add(Bug(
                    x = margin + rng.nextFloat() * (w - margin * 2),
                    y = margin + rng.nextFloat() * (h - margin * 2),
                    wobblePhase = rng.nextFloat() * 6.28f
                ))
            }
            generateScrambledRects()
            animating = true
            postOnAnimation(animationRunnable)
        }
    }

    fun reset() {
        active = false
        animating = false
        bugs.clear()
        scrambledRects.clear()
        visibility = GONE
    }

    private fun generateScrambledRects() {
        scrambledRects.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val fragments = listOf("Sna", "Fli", "res", "OV", "Pl", "Air", "gh", "he")
        val colors = listOf(
            Color.parseColor("#5A3D7A"), Color.parseColor("#7A5A9A"),
            Color.parseColor("#4A2D6A"), Color.parseColor("#6A4A8A"),
            Color.parseColor("#3A1D5A"), Color.parseColor("#8A6AAA")
        )

        for (i in 0 until 40) {
            scrambledRects.add(ScrambledRect(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                width = 40f * density + rng.nextFloat() * 80f * density,
                height = 25f * density + rng.nextFloat() * 40f * density,
                rotation = -30f + rng.nextFloat() * 60f,
                color = colors[rng.nextInt(colors.size)],
                text = if (rng.nextFloat() > 0.4f) fragments[rng.nextInt(fragments.size)] else ""
            ))
        }
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animating || !isAttachedToWindow) return
            for (bug in bugs) {
                bug.wobblePhase += 0.08f
            }
            vortexRotation += 0.8f
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchRadius = bugRadius * 1.5f
            val iterator = bugs.iterator()
            while (iterator.hasNext()) {
                val bug = iterator.next()
                val dx = event.x - bug.x
                val dy = event.y - bug.y
                if (dx * dx + dy * dy < touchRadius * touchRadius) {
                    bug.tapsRemaining--
                    if (bug.tapsRemaining <= 0) {
                        iterator.remove()
                        if (bugs.isEmpty()) {
                            active = false
                            animating = false
                            visibility = GONE
                            onBugCleared?.invoke()
                        }
                    } else {
                        // Move to a distant position — pick a random far corner quadrant
                        val margin = bugRadius * 1.5f
                        val w = width.toFloat()
                        val h = height.toFloat()
                        val midX = w / 2
                        val midY = h / 2
                        // Pick a quadrant on the opposite side from the current position
                        val farRight = bug.x < midX
                        val farBottom = bug.y < midY
                        val qx0 = if (farRight) midX else margin
                        val qx1 = if (farRight) w - margin else midX
                        val qy0 = if (farBottom) midY else margin
                        val qy1 = if (farBottom) h - margin else midY
                        bug.x = qx0 + rng.nextFloat() * (qx1 - qx0)
                        bug.y = qy0 + rng.nextFloat() * (qy1 - qy0)
                    }
                    invalidate()
                    return true
                }
            }
        }
        return true // consume all touches while bugs are active
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Dim background
        canvas.drawRect(0f, 0f, w, h, dimPaint)

        // Scrambled answer rectangles
        rectTextPaint.textSize = 14f * density
        for (rect in scrambledRects) {
            canvas.save()
            canvas.translate(rect.x, rect.y)
            canvas.rotate(rect.rotation)
            rectPaint.color = rect.color
            canvas.drawRoundRect(
                -rect.width / 2, -rect.height / 2,
                rect.width / 2, rect.height / 2,
                4f * density, 4f * density, rectPaint
            )
            if (rect.text.isNotEmpty()) {
                canvas.drawText(rect.text, 0f, rect.height * 0.15f, rectTextPaint)
            }
            canvas.restore()
        }

        for (bug in bugs) {
            drawBug(canvas, bug)
        }
    }

    private fun drawBug(canvas: Canvas, bug: Bug) {
        val wobbleAngle = sin(bug.wobblePhase.toDouble()).toFloat() * 5f
        val wobbleScale = 1f + sin((bug.wobblePhase * 1.5f).toDouble()).toFloat() * 0.03f

        // Vortex rays behind the bug
        canvas.save()
        canvas.translate(bug.x, bug.y)
        canvas.rotate(vortexRotation)
        val rayCount = 12
        val rayLength = bugRadius * 4f
        for (i in 0 until rayCount) {
            val angle = i * 360f / rayCount
            val rad = Math.toRadians(angle.toDouble())
            val halfWidth = Math.toRadians(8.0)

            rayPaint.color = if (i % 2 == 0) Color.parseColor("#60AA44CC") else Color.parseColor("#408833AA")

            val x1 = (cos(rad - halfWidth) * bugRadius * 0.8f).toFloat()
            val y1 = (sin(rad - halfWidth) * bugRadius * 0.8f).toFloat()
            val x2 = (cos(rad) * rayLength).toFloat()
            val y2 = (sin(rad) * rayLength).toFloat()
            val x3 = (cos(rad + halfWidth) * bugRadius * 0.8f).toFloat()
            val y3 = (sin(rad + halfWidth) * bugRadius * 0.8f).toFloat()

            val path = android.graphics.Path()
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            path.lineTo(x3, y3)
            path.close()
            canvas.drawPath(path, rayPaint)
        }
        canvas.restore()

        // Bug body
        canvas.save()
        canvas.translate(bug.x, bug.y)
        canvas.rotate(wobbleAngle)
        canvas.scale(wobbleScale, wobbleScale)

        // Outer glow
        glowPaint.shader = RadialGradient(
            0f, 0f, bugRadius * 1.6f,
            intArrayOf(Color.parseColor("#8040E0FF"), Color.TRANSPARENT),
            floatArrayOf(0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, bugRadius * 1.6f, glowPaint)

        // Fuzzy body
        val bodyColor = Color.parseColor("#40C8E8")
        val fuzzLayers = 8
        for (layer in fuzzLayers downTo 0) {
            val r = bugRadius * (0.3f + layer * 0.08f)
            val alpha = (255 - layer * 20).coerceIn(100, 255)
            fuzzPaint.color = Color.argb(alpha, Color.red(bodyColor), Color.green(bodyColor), Color.blue(bodyColor))
            fuzzPaint.strokeWidth = bugRadius * 0.08f

            val strokes = 20 + layer * 4
            for (s in 0 until strokes) {
                val a = (s * 360f / strokes) + layer * 15f
                val rad = Math.toRadians(a.toDouble())
                val innerR = r - bugRadius * 0.06f
                val outerR = r + bugRadius * 0.06f
                canvas.drawLine(
                    (cos(rad) * innerR).toFloat(), (sin(rad) * innerR).toFloat(),
                    (cos(rad) * outerR).toFloat(), (sin(rad) * outerR).toFloat(),
                    fuzzPaint
                )
            }
        }

        // Solid core
        bodyPaint.color = Color.parseColor("#50D0F0")
        canvas.drawCircle(0f, 0f, bugRadius * 0.5f, bodyPaint)

        // Antennae
        antennaPaint.strokeWidth = bugRadius * 0.06f
        val antennaLen = bugRadius * 0.7f
        canvas.drawLine(-bugRadius * 0.15f, -bugRadius * 0.4f, -bugRadius * 0.4f, -bugRadius - antennaLen * 0.3f, antennaPaint)
        canvas.drawCircle(-bugRadius * 0.4f, -bugRadius - antennaLen * 0.3f, bugRadius * 0.08f, antennaTipPaint)
        canvas.drawLine(bugRadius * 0.15f, -bugRadius * 0.4f, bugRadius * 0.4f, -bugRadius - antennaLen * 0.3f, antennaPaint)
        canvas.drawCircle(bugRadius * 0.4f, -bugRadius - antennaLen * 0.3f, bugRadius * 0.08f, antennaTipPaint)

        // Eyes
        val eyeR = bugRadius * 0.22f
        canvas.drawCircle(-bugRadius * 0.2f, -bugRadius * 0.1f, eyeR, eyeWhitePaint)
        canvas.drawCircle(-bugRadius * 0.15f, -bugRadius * 0.05f, eyeR * 0.5f, pupilPaint)
        canvas.drawCircle(bugRadius * 0.2f, -bugRadius * 0.1f, eyeR, eyeWhitePaint)
        canvas.drawCircle(bugRadius * 0.25f, -bugRadius * 0.05f, eyeR * 0.5f, pupilPaint)

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animating = false
    }
}
