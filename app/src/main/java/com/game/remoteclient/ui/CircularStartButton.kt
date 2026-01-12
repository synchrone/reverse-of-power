package com.game.remoteclient.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class CircularStartButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E1E")
        style = Paint.Style.FILL
    }

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 48f
        isFakeBoldText = true
    }

    private val lightOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val lightOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B")
        style = Paint.Style.FILL
    }

    private val progressArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B")
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private var progress = 0f // 0 to 1
    private var isPressed = false
    private var progressAnimator: ValueAnimator? = null

    private val numLights = 16
    private val holdDurationMs = 2000L // 2 seconds to fill

    var onProgressComplete: (() -> Unit)? = null

    private val arcRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        textPaint.textSize = min(w, h) / 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - 20f
        val buttonRadius = radius * 0.7f
        val lightRadius = radius * 0.08f

        // Draw outer circle background
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Draw lights around the circle
        val lightCircleRadius = radius * 0.88f
        for (i in 0 until numLights) {
            val angle = Math.toRadians((i * 360.0 / numLights) - 90)
            val lightX = centerX + (lightCircleRadius * Math.cos(angle)).toFloat()
            val lightY = centerY + (lightCircleRadius * Math.sin(angle)).toFloat()

            val lightProgress = i.toFloat() / numLights
            val paint = if (lightProgress < progress) lightOnPaint else lightOffPaint
            canvas.drawCircle(lightX, lightY, lightRadius, paint)
        }

        // Draw progress arc
        if (progress > 0) {
            val arcRadius = radius * 0.78f
            arcRect.set(
                centerX - arcRadius,
                centerY - arcRadius,
                centerX + arcRadius,
                centerY + arcRadius
            )
            canvas.drawArc(arcRect, -90f, progress * 360f, false, progressArcPaint)
        }

        // Draw main button
        val pressedScale = if (isPressed) 0.95f else 1f
        canvas.drawCircle(centerX, centerY, buttonRadius * pressedScale, buttonPaint)

        // Draw "START" text
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText("START", centerX, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                startProgressAnimation()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                cancelProgressAnimation()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startProgressAnimation() {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(progress, 1f).apply {
            duration = ((1f - progress) * holdDurationMs).toLong()
            addUpdateListener { animator ->
                progress = animator.animatedValue as Float
                invalidate()

                if (progress >= 1f) {
                    onProgressComplete?.invoke()
                    // Reset after completion
                    post {
                        progress = 0f
                        invalidate()
                    }
                }
            }
            start()
        }
    }

    private fun cancelProgressAnimation() {
        progressAnimator?.cancel()
        // Animate back to 0
        progressAnimator = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 200
            addUpdateListener { animator ->
                progress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        progressAnimator?.cancel()
        progress = 0f
        invalidate()
    }
}
