package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class WhirlpoolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#30FFFFFF")
    }

    private val arm1Path = Path()
    private val arm2Path = Path()
    private val rotations = 3.5f
    private val pointsPerRotation = 120

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildSpiral(w, h)
    }

    private fun buildSpiral(w: Int, h: Int) {
        arm1Path.reset()
        arm2Path.reset()

        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = min(w, h) * 0.38f
        val maxHalfWidth = maxRadius * 0.15f

        val totalPoints = (rotations * pointsPerRotation).toInt()
        val a = maxRadius / (rotations * 2.0 * Math.PI)

        for (arm in 0..1) {
            val path = if (arm == 0) arm1Path else arm2Path
            val phase = arm * Math.PI

            val leftEdge = FloatArray((totalPoints + 1) * 2)
            val rightEdge = FloatArray((totalPoints + 1) * 2)

            for (i in 0..totalPoints) {
                val theta = i.toDouble() / totalPoints * rotations * 2.0 * Math.PI + phase
                val r = a * (theta - phase)

                // Tangent vector: dr/dθ in Cartesian
                val dx = (a * cos(theta) - r * sin(theta)).toFloat()
                val dy = (a * sin(theta) + r * cos(theta)).toFloat()
                val len = sqrt(dx * dx + dy * dy)

                // Perpendicular (normalized)
                val px = if (len > 0f) -dy / len else 0f
                val py = if (len > 0f) dx / len else 0f

                // Width tapers from 0 at center to max at outer edge
                val fraction = i.toFloat() / totalPoints
                val halfW = fraction * maxHalfWidth

                val baseX = cx + (r * cos(theta)).toFloat()
                val baseY = cy + (r * sin(theta)).toFloat()

                leftEdge[i * 2] = baseX + px * halfW
                leftEdge[i * 2 + 1] = baseY + py * halfW
                rightEdge[i * 2] = baseX - px * halfW
                rightEdge[i * 2 + 1] = baseY - py * halfW
            }

            // Trace left edge forward, right edge backward
            path.moveTo(leftEdge[0], leftEdge[1])
            for (i in 1..totalPoints) {
                path.lineTo(leftEdge[i * 2], leftEdge[i * 2 + 1])
            }
            for (i in totalPoints downTo 0) {
                path.lineTo(rightEdge[i * 2], rightEdge[i * 2 + 1])
            }
            path.close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(arm1Path, armPaint)
        canvas.drawPath(arm2Path, armPaint)
    }
}
