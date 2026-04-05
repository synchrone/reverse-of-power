package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * Per-button overlay that renders answer text with each letter dancing up and down.
 * Optionally applies nibbler bite holes when both effects are active.
 * Uses a whimsical rounded bold font.
 */
class LetterScatterOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var active = false
    private var text = ""
    private var nibbleCount = 0
    private var seed = 0L

    // Per-character layout info
    private data class CharInfo(
        val char: Char,
        val x: Float,       // horizontal position
        val baseY: Float,   // vertical center baseline
        val yOffset: Float, // fixed vertical shift from baseline
        val rotation: Float // random rotation in degrees
    )
    private val chars = mutableListOf<CharInfo>()

    // Nibble holes
    private data class Hole(val cx: Float, val cy: Float, val r: Float)
    private val holes = mutableListOf<Hole>()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }

    private val bitmapPaint = Paint()

    private var blockTypeface: Typeface? = null

    private fun getBlockTypeface(): Typeface {
        if (blockTypeface == null) {
            blockTypeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        return blockTypeface!!
    }

    fun activate(text: String, viewSeed: Int, nibbleStacks: Int = 0) {
        this.text = text
        this.nibbleCount = nibbleStacks
        this.seed = System.currentTimeMillis() + viewSeed * 31L
        active = true
        visibility = VISIBLE

        textPaint.typeface = getBlockTypeface()
        textPaint.textSize = 42f * resources.displayMetrics.scaledDensity
        textPaint.letterSpacing = 0.05f

        post {
            layoutChars()
            if (nibbleCount > 0) generateHoles()
            invalidate()
        }
    }

    fun reset() {
        active = false
        chars.clear()
        holes.clear()
        visibility = GONE
    }

    private fun layoutChars() {
        chars.clear()
        if (text.isEmpty() || width <= 0 || height <= 0) return

        val rng = Random(seed + 777)
        val density = resources.displayMetrics.density
        val hPad = 58f * density
        val availW = width - hPad * 2
        val centerY = height / 2f

        val charWidths = text.map { textPaint.measureText(it.toString()) }
        val totalWidth = charWidths.sum()

        if (totalWidth > availW) {
            textPaint.textSize *= (availW / totalWidth) * 0.95f
        }

        val scaledWidths = text.map { textPaint.measureText(it.toString()) }
        val scaledTotal = scaledWidths.sum()
        var x = (width - scaledTotal) / 2f
        val maxShift = textPaint.textSize * 0.25f

        for (i in text.indices) {
            val charW = scaledWidths[i]
            chars.add(CharInfo(
                char = text[i],
                x = x + charW / 2f,
                baseY = centerY + textPaint.textSize * 0.35f,
                yOffset = (rng.nextFloat() - 0.5f) * 2f * maxShift,
                rotation = (rng.nextFloat() - 0.5f) * 14f // +-7 degrees
            ))
            x += charW
        }
    }

    private fun generateHoles() {
        holes.clear()
        if (chars.isEmpty()) return

        val rng = Random(seed)
        val density = resources.displayMetrics.density
        val holeCount = 2 + nibbleCount * 2
        val charHeight = textPaint.textSize

        for (i in 0 until holeCount) {
            val charInfo = chars[rng.nextInt(chars.size)]
            val cx = charInfo.x + (rng.nextFloat() - 0.5f) * textPaint.measureText(charInfo.char.toString()) * 1.5f
            val cy = charInfo.baseY + (rng.nextFloat() - 0.5f) * charHeight * 0.6f
            val baseR = charHeight * (0.12f + rng.nextFloat() * 0.1f)

            val clusterCount = 2 + rng.nextInt(3)
            for (j in 0 until clusterCount) {
                holes.add(Hole(
                    cx + (rng.nextFloat() - 0.5f) * baseR * 1.3f,
                    cy + (rng.nextFloat() - 0.5f) * baseR * 1.3f,
                    baseR * (0.5f + rng.nextFloat() * 0.6f)
                ))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!active || chars.isEmpty()) return

        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        if (holes.isNotEmpty()) {
            // Need offscreen bitmap for nibble punch-through
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val offscreen = Canvas(bmp)
            drawDancingText(offscreen)
            for (hole in holes) {
                offscreen.drawCircle(hole.cx, hole.cy, hole.r, eraserPaint)
            }
            canvas.drawBitmap(bmp, 0f, 0f, bitmapPaint)
            bmp.recycle()
        } else {
            drawDancingText(canvas)
        }
    }

    private fun drawDancingText(canvas: Canvas) {
        for (info in chars) {
            canvas.save()
            canvas.rotate(info.rotation, info.x, info.baseY + info.yOffset - textPaint.textSize * 0.35f)
            canvas.drawText(
                info.char.toString(),
                info.x,
                info.baseY + info.yOffset,
                textPaint
            )
            canvas.restore()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (active) {
            layoutChars()
            if (nibbleCount > 0) generateHoles()
        }
    }
}
