package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class LinkingLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDrawing = false

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        strokeWidth = 16f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    var onDragStart: ((Float, Float) -> Boolean)? = null
    var onDragEnd: ((Float, Float) -> Unit)? = null

    fun startLine(x: Float, y: Float) {
        startX = x
        startY = y
        endX = x
        endY = y
        isDrawing = true
        invalidate()
    }

    fun updateLine(x: Float, y: Float) {
        endX = x
        endY = y
        invalidate()
    }

    fun clearLine() {
        isDrawing = false
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val started = onDragStart?.invoke(event.x, event.y) ?: false
                if (started) {
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    updateLine(event.x, event.y)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing) {
                    clearLine()
                    if (event.action == MotionEvent.ACTION_UP) {
                        onDragEnd?.invoke(event.x, event.y)
                    }
                    return true
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        if (isDrawing) {
            canvas.drawLine(startX, startY, endX, endY, linePaint)
        }
    }
}
