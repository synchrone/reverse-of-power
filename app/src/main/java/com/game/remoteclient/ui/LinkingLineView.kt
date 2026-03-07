package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class LinkingLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dragPath = Path()
    private var isDrawing = false

    // Persistent chain lines (drawn paths between connected pairs)
    private val chainPaths = mutableListOf<Path>()

    private val stripeShader: BitmapShader by lazy { createStripeShader() }

    // Outline paint (drawn first, slightly thicker, gives border)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2299AA")
        strokeWidth = 24f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Striped fill paint
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 18f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var onDragStart: ((Float, Float) -> Boolean)? = null
    var onDragMove: ((Float, Float) -> Unit)? = null
    var onDragEnd: ((Float, Float) -> Unit)? = null

    private fun createStripeShader(): BitmapShader {
        val stripeWidth = 10
        val size = stripeWidth * 2
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val colorA = Color.WHITE
        val colorB = Color.parseColor("#66CCDD")

        for (x in 0 until size) {
            for (y in 0 until size) {
                val stripeIndex = (x + y) / stripeWidth
                bitmap.setPixel(x, y, if (stripeIndex % 2 == 0) colorA else colorB)
            }
        }

        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    fun startLine(x: Float, y: Float) {
        dragPath.reset()
        dragPath.moveTo(x, y)
        isDrawing = true
        invalidate()
    }

    fun updateLine(x: Float, y: Float) {
        if (!isDrawing) return
        dragPath.lineTo(x, y)
        invalidate()
    }

    fun clearLine() {
        isDrawing = false
        dragPath.reset()
        invalidate()
    }

    fun commitDragAsChain(endX: Float, endY: Float) {
        dragPath.lineTo(endX, endY)
        chainPaths.add(Path(dragPath))
        dragPath.reset()
        invalidate()
    }

    fun clearChainLines() {
        chainPaths.clear()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                return onDragStart?.invoke(event.x, event.y) ?: false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    onDragMove?.invoke(event.x, event.y)
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
        stripePaint.shader = stripeShader

        // Draw persistent chain paths
        for (path in chainPaths) {
            canvas.drawPath(path, outlinePaint)
            canvas.drawPath(path, stripePaint)
        }

        // Draw active drag path
        if (isDrawing) {
            canvas.drawPath(dragPath, outlinePaint)
            canvas.drawPath(dragPath, stripePaint)
        }
    }
}
