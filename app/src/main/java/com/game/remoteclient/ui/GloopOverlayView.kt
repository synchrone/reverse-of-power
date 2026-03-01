package com.game.remoteclient.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class GloopOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onGloopCleared: (() -> Unit)? = null

    private var gloopBitmap: Bitmap? = null
    private var gloopCanvas: Canvas? = null
    private var active = false
    private var cleared = false
    private var seed = 0L
    private var layerCount = 1

    private var lastX = 0f
    private var lastY = 0f

    private val bitmapPaint = Paint()

    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Coverage tracking grid
    private val gridCols = 10
    private val gridRows = 6
    private val grid = BooleanArray(gridCols * gridRows)

    fun activate(count: Int, viewSeed: Int) {
        layerCount = count.coerceAtLeast(1)
        seed = viewSeed.toLong() + 500 // offset from ice seed
        active = true
        cleared = false
        grid.fill(false)
        visibility = VISIBLE
        alpha = 1f

        if (width > 0 && height > 0) {
            generateGloop()
        }
        invalidate()
    }

    fun reset() {
        active = false
        cleared = false
        gloopBitmap?.recycle()
        gloopBitmap = null
        gloopCanvas = null
        visibility = GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (active && !cleared) {
            generateGloop()
        }
    }

    private fun generateGloop() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        gloopBitmap?.recycle()
        gloopBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        gloopCanvas = Canvas(gloopBitmap!!)

        val rng = Random(seed)
        val canvas = gloopCanvas!!
        val wf = w.toFloat()
        val hf = h.toFloat()

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EE4CAF50")
            style = Paint.Style.FILL
        }
        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AA81C784")
            style = Paint.Style.FILL
        }
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DD388E3C")
            style = Paint.Style.FILL
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5500E676")
            style = Paint.Style.FILL
        }

        // Main coverage: overlapping large ovals to create blob shape
        for (i in 0 until 18) {
            val cx = rng.nextFloat() * wf
            val cy = rng.nextFloat() * hf
            val rx = wf * 0.2f + rng.nextFloat() * wf * 0.35f
            val ry = hf * 0.2f + rng.nextFloat() * hf * 0.35f
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, basePaint)
        }

        // Lighter texture patches
        for (i in 0 until 14) {
            val cx = rng.nextFloat() * wf
            val cy = rng.nextFloat() * hf
            val rx = 12f + rng.nextFloat() * wf * 0.12f
            val ry = 8f + rng.nextFloat() * hf * 0.1f
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, lightPaint)
        }

        // Darker depth patches
        for (i in 0 until 10) {
            val cx = rng.nextFloat() * wf
            val cy = rng.nextFloat() * hf
            val rx = 10f + rng.nextFloat() * wf * 0.1f
            val ry = 6f + rng.nextFloat() * hf * 0.08f
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, darkPaint)
        }

        // Bright highlight spots (slime sheen)
        for (i in 0 until 8) {
            val cx = rng.nextFloat() * wf
            val cy = rng.nextFloat() * hf
            val r = 4f + rng.nextFloat() * 10f
            canvas.drawCircle(cx, cy, r, highlightPaint)
        }

        // Edge splatters
        for (i in 0 until 10) {
            val edge = rng.nextInt(4)
            val cx: Float
            val cy: Float
            when (edge) {
                0 -> { cx = rng.nextFloat() * wf; cy = rng.nextFloat() * hf * 0.15f }
                1 -> { cx = wf - rng.nextFloat() * wf * 0.15f; cy = rng.nextFloat() * hf }
                2 -> { cx = rng.nextFloat() * wf; cy = hf - rng.nextFloat() * hf * 0.15f }
                else -> { cx = rng.nextFloat() * wf * 0.15f; cy = rng.nextFloat() * hf }
            }
            val r = 8f + rng.nextFloat() * 20f
            canvas.drawCircle(cx, cy, r, basePaint)
        }

        // Eraser width: inversely proportional to count (thicker gloop = smaller brush)
        // 1.2x multiplier so the wipe feels wider than the finger contact area
        eraserPaint.strokeWidth = min(wf, hf) * 0.18f * 1.2f / layerCount
    }

    // Touch is dispatched by the full-screen gloopTouchInterceptor in the fragment.
    override fun onTouchEvent(event: MotionEvent): Boolean = false

    /** Called by the touch interceptor with coordinates in this view's local space. */
    fun handleSwipe(x: Float, y: Float, isNewContact: Boolean) {
        if (!active || cleared) return

        if (isNewContact) {
            lastX = x
            lastY = y
            eraseAt(x, y)
        } else {
            gloopCanvas?.drawLine(lastX, lastY, x, y, eraserPaint)
            markAlongLine(lastX, lastY, x, y)
            lastX = x
            lastY = y
            invalidate()
            checkCoverage()
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        val r = eraserPaint.strokeWidth / 2
        gloopCanvas?.drawCircle(x, y, r, eraserPaint)
        markCells(x, y, r)
        invalidate()
        checkCoverage()
    }

    private fun markAlongLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val r = eraserPaint.strokeWidth / 2
        val dx = x2 - x1
        val dy = y2 - y1
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val steps = (dist / (r * 0.5f)).toInt().coerceAtLeast(1)
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            markCells(x1 + dx * t, y1 + dy * t, r)
        }
    }

    private fun markCells(x: Float, y: Float, radius: Float) {
        val cellW = width.toFloat() / gridCols
        val cellH = height.toFloat() / gridRows
        val minCol = ((x - radius) / cellW).toInt().coerceIn(0, gridCols - 1)
        val maxCol = ((x + radius) / cellW).toInt().coerceIn(0, gridCols - 1)
        val minRow = ((y - radius) / cellH).toInt().coerceIn(0, gridRows - 1)
        val maxRow = ((y + radius) / cellH).toInt().coerceIn(0, gridRows - 1)
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                grid[row * gridCols + col] = true
            }
        }
    }

    private fun checkCoverage() {
        val clearedCells = grid.count { it }
        if (clearedCells >= grid.size * 0.65f) {
            cleared = true
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 200
                addUpdateListener { anim ->
                    alpha = anim.animatedValue as Float
                    if (alpha <= 0f) {
                        visibility = GONE
                        onGloopCleared?.invoke()
                    }
                }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        gloopBitmap?.let {
            val cornerRadius = resources.displayMetrics.density * 16f
            val clipPath = Path()
            clipPath.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(it, 0f, 0f, bitmapPaint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gloopBitmap?.recycle()
        gloopBitmap = null
        gloopCanvas = null
    }
}
