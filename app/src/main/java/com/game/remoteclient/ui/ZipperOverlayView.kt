package com.game.remoteclient.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class ZipperOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onZipperOpened: (() -> Unit)? = null

    private var active = false
    private var openFraction = 0f // 0 = closed, 1 = fully open
    private var dragging = false
    private var dragStartX = 0f
    private var seed = 0

    // Denim
    private val denimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val denimStitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3080C0E0")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Zipper track
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8A830")
        style = Paint.Style.FILL
    }
    private val trackEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A08020")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Zipper teeth
    private val toothPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4B840")
        style = Paint.Style.FILL
    }
    private val toothShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A08020")
        style = Paint.Style.FILL
    }

    // Zipper pull
    private val pullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0C850")
        style = Paint.Style.FILL
    }
    private val pullHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0E080")
        style = Paint.Style.FILL
    }
    private val pullEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#907018")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Opening gap
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F0E0")
        style = Paint.Style.FILL
    }

    fun activate(count: Int, viewSeed: Int) {
        seed = viewSeed
        openFraction = 0f
        active = true
        dragging = false
        visibility = VISIBLE
        alpha = 1f
        invalidate()
    }

    fun reset() {
        active = false
        openFraction = 0f
        visibility = GONE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false

        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f
        val pullX = getPullX()
        val pullRadius = h * 0.18f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check if touch is near the pull tab
                val dx = event.x - pullX
                val dy = event.y - midY
                if (dx * dx + dy * dy < (pullRadius * 2.5f) * (pullRadius * 2.5f)) {
                    dragging = true
                    dragStartX = event.x
                    return true
                }
                return true // consume but don't drag
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val deltaX = event.x - dragStartX
                    val trackWidth = w * 0.75f
                    openFraction = (openFraction + deltaX / trackWidth).coerceIn(0f, 1f)
                    dragStartX = event.x
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                if (openFraction > 0.85f) {
                    openFully()
                }
                return true
            }
        }
        return true
    }

    private fun getPullX(): Float {
        val w = width.toFloat()
        val margin = w * 0.12f
        return margin + openFraction * (w - margin * 2)
    }

    private fun openFully() {
        active = false
        ValueAnimator.ofFloat(alpha, 0f).apply {
            duration = 200
            addUpdateListener { anim ->
                alpha = anim.animatedValue as Float
                if (alpha <= 0f) {
                    visibility = GONE
                    onZipperOpened?.invoke()
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = resources.displayMetrics.density * 16f
        val midY = h / 2f
        val trackHeight = h * 0.06f
        val toothHeight = h * 0.1f
        val pullX = getPullX()

        // Clip to rounded rect
        val clipPath = Path()
        clipPath.addRoundRect(RectF(0f, 0f, w, h), cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // Draw opening gap (visible area behind zipper)
        if (openFraction > 0.01f) {
            val gapLeft = w * 0.12f
            val gapRight = pullX
            val gapTop = midY - h * 0.35f * openFraction
            val gapBottom = midY + h * 0.35f * openFraction
            canvas.drawRoundRect(
                RectF(gapLeft, gapTop, gapRight, gapBottom),
                8f, 8f, gapPaint
            )
        }

        // Denim top half
        drawDenim(canvas, 0f, 0f, w, midY - trackHeight - toothHeight, seed)
        // Denim bottom half
        drawDenim(canvas, 0f, midY + trackHeight + toothHeight, w, h, seed + 99)

        // If partially open, draw denim flaps curling away from the gap
        if (openFraction > 0.01f) {
            val gapLeft = w * 0.12f
            val gapRight = pullX
            val flapHeight = h * 0.35f * openFraction

            // Top flap fold (darker denim strip along gap edge)
            val topFlapRect = RectF(gapLeft, midY - trackHeight - toothHeight - flapHeight * 0.15f,
                gapRight, midY - trackHeight - toothHeight)
            denimPaint.color = Color.parseColor("#2878A8")
            canvas.drawRect(topFlapRect, denimPaint)

            // Bottom flap fold
            val bottomFlapRect = RectF(gapLeft, midY + trackHeight + toothHeight,
                gapRight, midY + trackHeight + toothHeight + flapHeight * 0.15f)
            canvas.drawRect(bottomFlapRect, denimPaint)
        }

        // Zipper track (central metal rail)
        val trackRect = RectF(0f, midY - trackHeight, w, midY + trackHeight)
        canvas.drawRect(trackRect, trackPaint)
        canvas.drawLine(0f, midY - trackHeight, w, midY - trackHeight, trackEdgePaint)
        canvas.drawLine(0f, midY + trackHeight, w, midY + trackHeight, trackEdgePaint)

        // Teeth along the track — only on closed section (right of pull)
        val toothWidth = w * 0.018f
        val toothSpacing = toothWidth * 2.2f
        var tx = pullX + toothSpacing
        while (tx < w - toothWidth) {
            // Top tooth
            val topToothRect = RectF(tx, midY - trackHeight - toothHeight, tx + toothWidth, midY - trackHeight)
            canvas.drawRoundRect(topToothRect, 2f, 2f, toothPaint)
            canvas.drawRoundRect(RectF(tx + toothWidth * 0.6f, topToothRect.top, tx + toothWidth, topToothRect.bottom),
                1f, 1f, toothShadowPaint)

            // Bottom tooth (offset by half spacing for interlocking look)
            val btx = tx + toothSpacing * 0.5f
            if (btx < w - toothWidth) {
                val bottomToothRect = RectF(btx, midY + trackHeight, btx + toothWidth, midY + trackHeight + toothHeight)
                canvas.drawRoundRect(bottomToothRect, 2f, 2f, toothPaint)
                canvas.drawRoundRect(RectF(btx + toothWidth * 0.6f, bottomToothRect.top, btx + toothWidth, bottomToothRect.bottom),
                    1f, 1f, toothShadowPaint)
            }

            tx += toothSpacing
        }

        // Zipper pull tab
        drawPullTab(canvas, pullX, midY, h * 0.16f)

        canvas.restore()
    }

    private fun drawDenim(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, denimSeed: Int) {
        if (bottom <= top) return

        // Base denim gradient
        denimPaint.shader = LinearGradient(
            left, top, left, bottom,
            Color.parseColor("#3088B8"), Color.parseColor("#2878A8"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, top, right, bottom, denimPaint)
        denimPaint.shader = null

        // Horizontal stitching lines near edges of zipper
        val density = resources.displayMetrics.density
        denimStitchPaint.strokeWidth = 1.5f * density
        val stitchLen = 4f * density
        val stitchGap = 3f * density

        // Stitch line near bottom edge (or top edge depending on which half)
        val stitchY = if (top < bottom / 2) bottom - 4f * density else top + 4f * density
        var sx = left + 8f * density
        while (sx < right - 8f * density) {
            canvas.drawLine(sx, stitchY, sx + stitchLen, stitchY, denimStitchPaint)
            sx += stitchLen + stitchGap
        }
    }

    private fun drawPullTab(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // Pull body (teardrop/shield shape)
        val bodyWidth = size * 0.7f
        val bodyHeight = size * 1.3f

        val bodyPath = Path()
        bodyPath.moveTo(cx, cy - bodyHeight * 0.4f)
        bodyPath.quadTo(cx + bodyWidth, cy - bodyHeight * 0.2f, cx + bodyWidth * 0.5f, cy + bodyHeight * 0.5f)
        bodyPath.lineTo(cx - bodyWidth * 0.5f, cy + bodyHeight * 0.5f)
        bodyPath.quadTo(cx - bodyWidth, cy - bodyHeight * 0.2f, cx, cy - bodyHeight * 0.4f)
        bodyPath.close()

        canvas.drawPath(bodyPath, pullPaint)
        canvas.drawPath(bodyPath, pullEdgePaint)

        // Highlight arc
        canvas.drawCircle(cx - bodyWidth * 0.15f, cy - bodyHeight * 0.1f, size * 0.15f, pullHighlightPaint)

        // Ring at top of pull
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D4B840")
            style = Paint.Style.STROKE
            strokeWidth = size * 0.12f
        }
        canvas.drawCircle(cx, cy - bodyHeight * 0.4f - size * 0.2f, size * 0.18f, ringPaint)
    }
}
