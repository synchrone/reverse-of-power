package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class SunburstBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val numRays = 24

    // Default beige/taupe colors from the screenshot
    private var primaryColor = Color.parseColor("#C4B8A8")   // Darker beige
    private var secondaryColor = Color.parseColor("#D4C8B8") // Lighter beige

    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }

    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = secondaryColor
    }

    private val rayPath = Path()

    private val darkenPaint = Paint().apply {
        color = Color.parseColor("#55000000")
        style = Paint.Style.FILL
    }

    fun setColors(primary: Int, secondary: Int) {
        primaryColor = primary
        secondaryColor = secondary
        primaryPaint.color = primaryColor
        secondaryPaint.color = secondaryColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = max(width, height) * 1.5f

        val anglePerRay = 360f / numRays

        for (i in 0 until numRays) {
            val startAngle = Math.toRadians((i * anglePerRay - 90).toDouble())
            val endAngle = Math.toRadians(((i + 1) * anglePerRay - 90).toDouble())

            rayPath.reset()
            rayPath.moveTo(centerX, centerY)
            rayPath.lineTo(
                centerX + (maxRadius * cos(startAngle)).toFloat(),
                centerY + (maxRadius * sin(startAngle)).toFloat()
            )
            rayPath.lineTo(
                centerX + (maxRadius * cos(endAngle)).toFloat(),
                centerY + (maxRadius * sin(endAngle)).toFloat()
            )
            rayPath.close()

            val paint = if (i % 2 == 0) primaryPaint else secondaryPaint
            canvas.drawPath(rayPath, paint)
        }

        // Darken overlay to subdue colors and keep white text readable
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), darkenPaint)
    }
}
