package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BomblesOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onBombleTouched: (() -> Unit)? = null

    private val bombles = mutableListOf<Bomble>()
    private var animating = false
    private var density = 1f

    private data class Bomble(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var rotation: Float,
        var rotationSpeed: Float,
        val size: Float,
        val spikeOffsets: FloatArray
    )

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.FILL
    }

    private val bodyHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D3D3D")
        style = Paint.Style.FILL
    }

    private val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
    }

    private val spikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val spikeTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107")
        style = Paint.Style.FILL
    }

    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    fun activate(count: Int) {
        density = resources.displayMetrics.density
        bombles.clear()
        val rng = Random(System.currentTimeMillis())
        val w = if (width > 0) width.toFloat() else 800f
        val h = if (height > 0) height.toFloat() else 1200f
        val numBombles = (count * 2 + 4).coerceIn(6, 10)
        val baseSize = 50f * density

        for (i in 0 until numBombles) {
            val size = baseSize * (0.85f + rng.nextFloat() * 0.3f)
            val edge = rng.nextInt(4)
            val (startX, startY) = when (edge) {
                0 -> rng.nextFloat() * w to size
                1 -> w - size to rng.nextFloat() * h
                2 -> rng.nextFloat() * w to h - size
                else -> size to rng.nextFloat() * h
            }

            val speed = (1.5f + rng.nextFloat() * 2f) * density
            val angle = rng.nextFloat() * 360f
            val rad = Math.toRadians(angle.toDouble())
            val spikeOffsets = FloatArray(8) { 0.9f + rng.nextFloat() * 0.25f }

            bombles.add(Bomble(
                x = startX, y = startY,
                vx = (cos(rad) * speed).toFloat(),
                vy = (sin(rad) * speed).toFloat(),
                rotation = rng.nextFloat() * 360f,
                rotationSpeed = -1.5f + rng.nextFloat() * 3f,
                size = size,
                spikeOffsets = spikeOffsets
            ))
        }

        visibility = VISIBLE
        animating = true
        postOnAnimation(animationRunnable)
    }

    fun deactivate() {
        animating = false
        bombles.clear()
        visibility = GONE
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animating || !isAttachedToWindow) return
            updatePositions()
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun updatePositions() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        for (b in bombles) {
            b.x += b.vx
            b.y += b.vy
            b.rotation += b.rotationSpeed

            val margin = b.size * 0.4f
            if (b.x < margin) { b.x = margin; b.vx = -b.vx }
            if (b.x > w - margin) { b.x = w - margin; b.vx = -b.vx }
            if (b.y < margin) { b.y = margin; b.vy = -b.vy }
            if (b.y > h - margin) { b.y = h - margin; b.vy = -b.vy }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            for (b in bombles) {
                val dx = event.x - b.x
                val dy = event.y - b.y
                val touchRadius = b.size * 0.8f
                if (dx * dx + dy * dy < touchRadius * touchRadius) {
                    onBombleTouched?.invoke()
                    return true
                }
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        for (b in bombles) {
            drawBomble(canvas, b)
        }
    }

    private fun drawBomble(canvas: Canvas, b: Bomble) {
        canvas.save()
        canvas.translate(b.x, b.y)
        canvas.rotate(b.rotation)

        val bodyRadius = b.size * 0.45f
        val spikeCount = b.spikeOffsets.size

        // Spikes
        spikePaint.strokeWidth = b.size * 0.1f
        for (i in 0 until spikeCount) {
            val angle = i * 360f / spikeCount
            val rad = Math.toRadians(angle.toDouble())
            val innerR = bodyRadius * 0.8f
            val outerR = bodyRadius * 1.45f * b.spikeOffsets[i]
            val x1 = (cos(rad) * innerR).toFloat()
            val y1 = (sin(rad) * innerR).toFloat()
            val x2 = (cos(rad) * outerR).toFloat()
            val y2 = (sin(rad) * outerR).toFloat()
            canvas.drawLine(x1, y1, x2, y2, spikePaint)
            canvas.drawCircle(x2, y2, b.size * 0.06f, spikeTipPaint)
        }

        // Body
        canvas.drawCircle(0f, 0f, bodyRadius, bodyPaint)

        // Highlight for 3D effect
        canvas.drawCircle(
            -bodyRadius * 0.15f, -bodyRadius * 0.15f,
            bodyRadius * 0.65f, bodyHighlightPaint
        )

        // Seam lines
        seamPaint.strokeWidth = b.size * 0.025f
        val seamRect1 = RectF(
            -bodyRadius * 0.6f, -bodyRadius * 0.95f,
            bodyRadius * 0.6f, bodyRadius * 0.95f
        )
        canvas.drawArc(seamRect1, -90f, 180f, false, seamPaint)
        val seamRect2 = RectF(
            -bodyRadius * 0.95f, -bodyRadius * 0.4f,
            bodyRadius * 0.95f, bodyRadius * 0.4f
        )
        canvas.drawArc(seamRect2, 0f, 180f, false, seamPaint)

        // Left eye (larger)
        val eyeR = bodyRadius * 0.26f
        canvas.drawCircle(-bodyRadius * 0.22f, -bodyRadius * 0.12f, eyeR, eyeWhitePaint)
        canvas.drawCircle(-bodyRadius * 0.17f, -bodyRadius * 0.07f, eyeR * 0.45f, pupilPaint)

        // Right eye (slightly smaller, offset)
        val eyeR2 = bodyRadius * 0.2f
        canvas.drawCircle(bodyRadius * 0.18f, -bodyRadius * 0.18f, eyeR2, eyeWhitePaint)
        canvas.drawCircle(bodyRadius * 0.21f, -bodyRadius * 0.14f, eyeR2 * 0.45f, pupilPaint)

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animating = false
    }
}
