package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class NibblersOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var textBitmap: Bitmap? = null
    private var active = false
    private var seed = 0L
    private var layerCount = 1
    private var text: String = ""

    private val bitmapPaint = Paint()

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }

    fun activate(count: Int, viewSeed: Int, text: String) {
        layerCount = count.coerceAtLeast(1)
        seed = System.currentTimeMillis() + viewSeed * 31L
        this.text = text
        active = true
        visibility = VISIBLE

        if (width > 0 && height > 0) {
            generateNibbledText()
        }
        invalidate()
    }

    fun reset() {
        active = false
        text = ""
        textBitmap?.recycle()
        textBitmap = null
        visibility = GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (active) {
            generateNibbledText()
        }
    }

    private fun generateNibbledText() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0 || text.isEmpty()) return

        textBitmap?.recycle()
        textBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(textBitmap!!)

        // Draw text matching the button's style (18sp bold white centered)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f * resources.displayMetrics.scaledDensity
            typeface = Typeface.DEFAULT_BOLD
        }

        val hPad = (58f * resources.displayMetrics.density).toInt()
        val textWidth = w - hPad * 2

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        // Center vertically like the button does, offset horizontally for padding
        val yOffset = (h - layout.height) / 2f
        canvas.save()
        canvas.translate(hPad.toFloat(), yOffset)
        layout.draw(canvas)
        canvas.restore()

        // Punch bite-shaped holes concentrated along the text lines
        val rng = Random(seed)
        val biteCount = 2 + layerCount * 2

        for (i in 0 until biteCount) {
            // Pick a random line to bite
            val line = rng.nextInt(layout.lineCount)
            val lineTop = yOffset + layout.getLineTop(line)
            val lineBottom = yOffset + layout.getLineBottom(line)
            val lineLeft = layout.getLineLeft(line)
            val lineRight = layout.getLineRight(line)
            val lineHeight = lineBottom - lineTop

            // Position bite within the text line bounds
            val cx = lineLeft + rng.nextFloat() * (lineRight - lineLeft)
            val cy = lineTop + rng.nextFloat() * lineHeight

            // Bite = cluster of overlapping circles
            val baseRadius = lineHeight * (0.17f + rng.nextFloat() * 0.14f)
            val clusterCount = 2 + rng.nextInt(3)
            for (j in 0 until clusterCount) {
                val ox = (rng.nextFloat() - 0.5f) * baseRadius * 1.35f
                val oy = (rng.nextFloat() - 0.5f) * baseRadius * 1.35f
                val r = baseRadius * (0.59f + rng.nextFloat() * 0.59f)
                canvas.drawCircle(cx + ox, cy + oy, r, eraserPaint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        textBitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        textBitmap?.recycle()
        textBitmap = null
    }
}
