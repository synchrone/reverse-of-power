package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class DiscoOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var active = false
    private var animating = false
    private var frameCount = 0L

    private val cols = 9
    private val rows = 6

    // Each cell has a hue that drifts over time
    private val cellHues = FloatArray(cols * rows)
    private val cellSpeeds = FloatArray(cols * rows)

    private val cellPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val discoColors = intArrayOf(
        Color.parseColor("#40FF0080"), // pink
        Color.parseColor("#4000FFFF"), // cyan
        Color.parseColor("#40FFFF00"), // yellow
        Color.parseColor("#40FF00FF"), // magenta
        Color.parseColor("#4000FF80"), // green
        Color.parseColor("#408000FF"), // purple
        Color.parseColor("#40FF8000"), // orange
        Color.parseColor("#400080FF"), // blue
    )

    fun activate() {
        active = true
        visibility = VISIBLE
        frameCount = 0

        val rng = Random(System.currentTimeMillis())
        for (i in cellHues.indices) {
            cellHues[i] = rng.nextFloat() * 360f
            cellSpeeds[i] = 0.5f + rng.nextFloat() * 1.5f
            if (rng.nextBoolean()) cellSpeeds[i] = -cellSpeeds[i]
        }

        animating = true
        postOnAnimation(animationRunnable)
    }

    fun reset() {
        active = false
        animating = false
        visibility = GONE
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animating || !isAttachedToWindow) return
            frameCount++

            for (i in cellHues.indices) {
                cellHues[i] = (cellHues[i] + cellSpeeds[i]) % 360f
                if (cellHues[i] < 0) cellHues[i] += 360f
            }

            // Every few frames, randomly spike some cells for a flash effect
            if (frameCount % 20 == 0L) {
                val rng = Random(frameCount)
                val flashCount = 2 + rng.nextInt(4)
                for (f in 0 until flashCount) {
                    val idx = rng.nextInt(cellHues.size)
                    cellHues[idx] = rng.nextFloat() * 360f
                    cellSpeeds[idx] = -cellSpeeds[idx]
                }
            }

            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) return

        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 5f * resources.displayMetrics.density
        val cellW = (w - gap * (cols + 1)) / cols
        val cellH = (h - gap * (rows + 1)) / rows
        val hsv = floatArrayOf(0f, 1f, 1f)

        // Black grid background
        gridPaint.color = Color.parseColor("#40000000")
        canvas.drawRect(0f, 0f, w, h, gridPaint)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val idx = row * cols + col
                hsv[0] = cellHues[idx]

                val phase = (cellHues[idx] / 60f).toInt() % 3
                val alpha = when (phase) {
                    0 -> 0x60
                    1 -> 0x48
                    else -> 0x54
                }

                val rgb = Color.HSVToColor(alpha, hsv)
                cellPaint.color = rgb

                val left = gap + col * (cellW + gap)
                val top = gap + row * (cellH + gap)
                canvas.drawRect(left, top, left + cellW, top + cellH, cellPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animating = false
    }
}
