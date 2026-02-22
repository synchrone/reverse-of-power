package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class RetroTvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val eggshell = Color.parseColor("#F0EAD6")
    private var fillColor = Color.TRANSPARENT

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = eggshell
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = eggshell
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = eggshell
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    var text: String = "Look at the\nTV"
        set(value) {
            field = value
            invalidate()
        }

    fun setFillColor(color: Int) {
        fillColor = color
        fillPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val size = min(width, height) * 0.6f

        // Rotate and stretch the entire drawing
        canvas.save()
        canvas.rotate(-10f, centerX, centerY)
        canvas.scale(1.2f, 1f, centerX, centerY)

        // TV screen dimensions
        val tvWidth = size * 0.7f
        val tvHeight = size * 0.5f
        val tvLeft = centerX - tvWidth / 2
        val tvTop = centerY - tvHeight / 2 + size * 0.1f
        val tvRight = centerX + tvWidth / 2
        val tvBottom = tvTop + tvHeight

        // Draw antenna
        val antennaBaseY = tvTop - size * 0.02f
        val antennaTopY = tvTop - size * 0.25f

        // Vertical antenna pole
        strokePaint.strokeWidth = size * 0.02f
        canvas.drawLine(centerX, antennaBaseY, centerX, antennaTopY, strokePaint)

        // Antenna top horizontal bar
        val antennaWidth = size * 0.2f
        canvas.drawLine(
            centerX - antennaWidth,
            antennaTopY,
            centerX + antennaWidth,
            antennaTopY,
            strokePaint
        )

        // Antenna balls at ends
        val ballRadius = size * 0.025f
        canvas.drawCircle(centerX - antennaWidth, antennaTopY, ballRadius, paint)
        canvas.drawCircle(centerX + antennaWidth, antennaTopY, ballRadius, paint)

        // Antenna base
        val baseWidth = size * 0.06f
        canvas.drawCircle(centerX, antennaBaseY, baseWidth * 0.5f, paint)

        // Draw scalloped border around TV
        drawScallopedRect(canvas, tvLeft, tvTop, tvRight, tvBottom, size * 0.04f)

        // Word-wrap text to fit within the TV width
        textPaint.textSize = size * 0.1f
        val maxTextWidth = tvWidth * 0.85f
        val wrappedLines = mutableListOf<String>()
        for (line in text.split("\n")) {
            val words = line.split(" ")
            var current = ""
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (textPaint.measureText(test) > maxTextWidth && current.isNotEmpty()) {
                    wrappedLines.add(current)
                    current = word
                } else {
                    current = test
                }
            }
            if (current.isNotEmpty()) wrappedLines.add(current)
        }

        val lineHeight = textPaint.fontSpacing
        val totalTextHeight = lineHeight * wrappedLines.size
        val tvCenterY = (tvTop + tvBottom) / 2
        val startY = tvCenterY - totalTextHeight / 2 + lineHeight * 0.7f

        for ((index, line) in wrappedLines.withIndex()) {
            canvas.drawText(line, centerX, startY + index * lineHeight, textPaint)
        }

        canvas.restore()
    }

    private fun drawScallopedRect(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        scallop: Float
    ) {
        val width = right - left
        val height = bottom - top
        val cornerRadius = scallop * 1.5f

        // Fill the TV screen background
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

        // Draw rounded rectangle border (stroke)
        val borderPaint = Paint(strokePaint).apply {
            strokeWidth = scallop * 0.6f
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        // Draw scallops around the border
        val scallopRadius = scallop * 0.8f

        // Calculate number of scallops for each side
        val horizontalScallops = ((width - 2 * cornerRadius) / (scallopRadius * 2)).toInt()
        val verticalScallops = ((height - 2 * cornerRadius) / (scallopRadius * 2)).toInt()

        // Top edge scallops
        val topStartX = left + cornerRadius
        val topSpacing = (width - 2 * cornerRadius) / (horizontalScallops + 1)
        for (i in 0..horizontalScallops) {
            canvas.drawCircle(topStartX + i * topSpacing, top, scallopRadius, paint)
        }

        // Bottom edge scallops
        for (i in 0..horizontalScallops) {
            canvas.drawCircle(topStartX + i * topSpacing, bottom, scallopRadius, paint)
        }

        // Left edge scallops
        val leftStartY = top + cornerRadius
        val leftSpacing = (height - 2 * cornerRadius) / (verticalScallops + 1)
        for (i in 0..verticalScallops) {
            canvas.drawCircle(left, leftStartY + i * leftSpacing, scallopRadius, paint)
        }

        // Right edge scallops
        for (i in 0..verticalScallops) {
            canvas.drawCircle(right, leftStartY + i * leftSpacing, scallopRadius, paint)
        }

        // Corner scallops
        canvas.drawCircle(left + cornerRadius * 0.7f, top + cornerRadius * 0.7f, scallopRadius, paint)
        canvas.drawCircle(right - cornerRadius * 0.7f, top + cornerRadius * 0.7f, scallopRadius, paint)
        canvas.drawCircle(left + cornerRadius * 0.7f, bottom - cornerRadius * 0.7f, scallopRadius, paint)
        canvas.drawCircle(right - cornerRadius * 0.7f, bottom - cornerRadius * 0.7f, scallopRadius, paint)
    }
}
