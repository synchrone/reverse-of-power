package com.game.remoteclient.ui

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class RewardScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isOpened: Boolean = false
        set(value) { field = value; invalidate() }

    var factText: String = ""
        set(value) { field = value; invalidate() }

    private val parchmentColor = Color.parseColor("#F5E6C8")
    private val parchmentDark = Color.parseColor("#D4B896")
    private val parchmentEdge = Color.parseColor("#C4A876")
    private val textColor = Color.parseColor("#4A3728")
    private val sealRed = Color.parseColor("#8B0000")
    private val sealRedLight = Color.parseColor("#B22222")
    private val sealGold = Color.parseColor("#DAA520")

    private val parchmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = parchmentColor
        style = Paint.Style.FILL
    }

    private val curlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = parchmentEdge
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sealRed
        style = Paint.Style.FILL
    }

    private val ornamentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = parchmentDark
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isOpened) drawOpenedScroll(canvas) else drawUnopenedScroll(canvas)
    }

    private fun drawOpenedScroll(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val scrollW = w * 0.6f
        val scrollH = h * 0.85f
        val left = (w - scrollW) / 2f
        val top = (h - scrollH) / 2f
        val right = left + scrollW
        val bottom = top + scrollH
        val curlH = scrollH * 0.08f

        // Parchment body (slightly narrower than curls for scroll shape)
        val inset = 10f * resources.displayMetrics.density
        canvas.drawRoundRect(left + inset, top + curlH, right - inset, bottom - curlH, 4f, 4f, parchmentPaint)

        // Top curl
        drawCurl(canvas, left, top, scrollW, curlH, isTop = true)

        // Bottom curl
        drawCurl(canvas, left, bottom - curlH, scrollW, curlH, isTop = false)

        // Ornamental swirls
        val swirlY1 = top + curlH + scrollH * 0.12f
        val swirlY2 = bottom - curlH - scrollH * 0.12f
        drawOrnamentalSwirl(canvas, w / 2f, swirlY1, scrollW * 0.5f)
        drawOrnamentalSwirl(canvas, w / 2f, swirlY2, scrollW * 0.5f)

        // Factoid text
        val textSize = min(w, h) * 0.045f
        textPaint.textSize = textSize
        val textAreaW = scrollW * 0.75f
        val textAreaTop = swirlY1 + textSize
        val textAreaBottom = swirlY2 - textSize

        val layout = StaticLayout.Builder.obtain(factText, 0, factText.length, textPaint, textAreaW.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(textSize * 0.3f, 1f)
            .build()

        val textTotalH = layout.height.toFloat()
        val textStartY = textAreaTop + (textAreaBottom - textAreaTop - textTotalH) / 2f

        canvas.save()
        canvas.translate(w / 2f, textStartY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawCurl(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, isTop: Boolean) {
        val right = left + width
        val rect = RectF(left, top, right, top + height)

        // Gradient to give 3D rolled appearance
        val colors = if (isTop) {
            intArrayOf(parchmentEdge, parchmentDark, parchmentColor, parchmentDark, parchmentEdge)
        } else {
            intArrayOf(parchmentEdge, parchmentDark, parchmentColor, parchmentDark, parchmentEdge)
        }
        val positions = floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f)

        curlPaint.shader = LinearGradient(
            left, top, left, top + height,
            colors, positions, Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(rect, height / 2f, height / 2f, curlPaint)
        curlPaint.shader = null

        // Shadow line
        val shadowY = if (isTop) top + height else top
        edgePaint.alpha = 60
        canvas.drawLine(left + 8f, shadowY, right - 8f, shadowY, edgePaint)
        edgePaint.alpha = 255
    }

    private fun drawOrnamentalSwirl(canvas: Canvas, centerX: Float, y: Float, width: Float) {
        val halfW = width / 2f
        val curlSize = halfW * 0.25f

        val path = Path()

        // Left side S-curve with curl
        path.moveTo(centerX - halfW, y)
        path.cubicTo(
            centerX - halfW * 0.6f, y - curlSize,
            centerX - halfW * 0.3f, y + curlSize,
            centerX, y
        )

        // Right side S-curve with curl (mirrored)
        path.cubicTo(
            centerX + halfW * 0.3f, y - curlSize,
            centerX + halfW * 0.6f, y + curlSize,
            centerX + halfW, y
        )

        canvas.drawPath(path, ornamentPaint)

        // End curls - small spirals at each end
        val curlR = curlSize * 0.4f
        val leftCurl = Path()
        leftCurl.moveTo(centerX - halfW, y)
        leftCurl.cubicTo(
            centerX - halfW - curlR, y - curlR * 2,
            centerX - halfW + curlR * 2, y - curlR * 2,
            centerX - halfW + curlR, y
        )
        canvas.drawPath(leftCurl, ornamentPaint)

        val rightCurl = Path()
        rightCurl.moveTo(centerX + halfW, y)
        rightCurl.cubicTo(
            centerX + halfW + curlR, y - curlR * 2,
            centerX + halfW - curlR * 2, y - curlR * 2,
            centerX + halfW - curlR, y
        )
        canvas.drawPath(rightCurl, ornamentPaint)

        // Center diamond
        val dSize = curlSize * 0.3f
        val diamond = Path()
        diamond.moveTo(centerX, y - dSize)
        diamond.lineTo(centerX + dSize, y)
        diamond.lineTo(centerX, y + dSize)
        diamond.lineTo(centerX - dSize, y)
        diamond.close()
        ornamentPaint.style = Paint.Style.FILL
        canvas.drawPath(diamond, ornamentPaint)
        ornamentPaint.style = Paint.Style.STROKE
    }

    private fun drawUnopenedScroll(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val scrollW = w * 0.45f
        val scrollH = h * 0.35f
        val left = (w - scrollW) / 2f
        val top = (h - scrollH) / 2f
        val right = left + scrollW
        val bottom = top + scrollH
        val endCapW = scrollH * 0.15f

        // Main rolled body with gradient
        curlPaint.shader = LinearGradient(
            left, top, left, bottom,
            intArrayOf(parchmentEdge, parchmentColor, parchmentDark, parchmentColor, parchmentEdge),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        val bodyRect = RectF(left + endCapW, top, right - endCapW, bottom)
        canvas.drawRoundRect(bodyRect, 4f, 4f, curlPaint)
        curlPaint.shader = null

        // Left end cap
        curlPaint.shader = LinearGradient(
            left, top, left, bottom,
            intArrayOf(parchmentDark, parchmentEdge, parchmentDark),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val leftCap = RectF(left, top - scrollH * 0.05f, left + endCapW * 2, bottom + scrollH * 0.05f)
        canvas.drawRoundRect(leftCap, endCapW, endCapW, curlPaint)
        curlPaint.shader = null

        // Right end cap
        curlPaint.shader = LinearGradient(
            right, top, right, bottom,
            intArrayOf(parchmentDark, parchmentEdge, parchmentDark),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val rightCap = RectF(right - endCapW * 2, top - scrollH * 0.05f, right, bottom + scrollH * 0.05f)
        canvas.drawRoundRect(rightCap, endCapW, endCapW, curlPaint)
        curlPaint.shader = null

        // Wax seal
        val sealR = scrollH * 0.3f
        val cx = w / 2f
        val cy = h / 2f

        // Seal outer (wavy edge)
        sealPaint.color = sealRed
        val sealPath = Path()
        val bumps = 16
        for (i in 0 until bumps) {
            val angle = (i.toFloat() / bumps) * Math.PI.toFloat() * 2
            val r = if (i % 2 == 0) sealR else sealR * 0.88f
            val x = cx + r * kotlin.math.cos(angle.toDouble()).toFloat()
            val y = cy + r * kotlin.math.sin(angle.toDouble()).toFloat()
            if (i == 0) sealPath.moveTo(x, y) else sealPath.lineTo(x, y)
        }
        sealPath.close()
        canvas.drawPath(sealPath, sealPaint)

        // Seal inner circle
        sealPaint.color = sealRedLight
        canvas.drawCircle(cx, cy, sealR * 0.7f, sealPaint)

        // Seal emblem - star
        sealPaint.color = sealGold
        val starR = sealR * 0.35f
        val starInnerR = starR * 0.45f
        val starPath = Path()
        for (i in 0 until 10) {
            val angle = (i.toFloat() / 10f) * Math.PI.toFloat() * 2 - Math.PI.toFloat() / 2
            val r = if (i % 2 == 0) starR else starInnerR
            val x = cx + r * kotlin.math.cos(angle.toDouble()).toFloat()
            val y = cy + r * kotlin.math.sin(angle.toDouble()).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        canvas.drawPath(starPath, sealPaint)
    }
}
