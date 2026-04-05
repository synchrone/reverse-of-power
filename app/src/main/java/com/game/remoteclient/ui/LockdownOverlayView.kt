package com.game.remoteclient.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class LockdownOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onLockdownCleared: (() -> Unit)? = null

    private data class Lock(
        val x: Float, val y: Float,
        val chainRuns: List<ChainRun>
    )

    private data class ChainLink(
        val cx: Float, val cy: Float,
        val width: Float, val height: Float,
        val rotation: Float
    )

    private data class ChainRun(val links: List<ChainLink>)

    private val locks = mutableListOf<Lock>()
    private var active = false
    private var density = 1f

    // Chains: lighter gold
    private val chainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0C850")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val chainShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60000000")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Lock: darker, more saturated for contrast
    private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A07010")
        style = Paint.Style.FILL
    }

    private val lockHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C89820")
        style = Paint.Style.FILL
    }

    private val lockEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#604008")
        style = Paint.Style.STROKE
    }

    private val shacklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#887010")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val keyholePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#302008")
        style = Paint.Style.FILL
    }

    fun activate(count: Int) {
        density = resources.displayMetrics.density
        active = true
        visibility = VISIBLE
        locks.clear()

        post {
            generateLocks(count.coerceIn(1, 6))
            invalidate()
        }
    }

    fun reset() {
        active = false
        locks.clear()
        visibility = GONE
    }

    private fun generateLocks(count: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val rng = Random(count.toLong() * 7 + 13)
        val linkW = 60f * density
        val linkH = 36f * density

        // Place locks randomly with margin from edges and minimum spacing between them
        val margin = w * 0.15f
        val marginY = h * 0.15f
        val minSpacing = w * 0.3f
        val placed = mutableListOf<Pair<Float, Float>>()

        for (i in 0 until count) {
            var lockX: Float
            var lockY: Float
            var attempts = 0
            do {
                lockX = margin + rng.nextFloat() * (w - margin * 2)
                lockY = marginY + rng.nextFloat() * (h - marginY * 2)
                attempts++
            } while (attempts < 50 && placed.any { (px, py) ->
                val dx = lockX - px; val dy = lockY - py
                dx * dx + dy * dy < minSpacing * minSpacing
            })
            placed.add(lockX to lockY)

            val chains = generateChainsForLock(rng, lockX, lockY, w, h, linkW, linkH)
            locks.add(Lock(lockX, lockY, chains))
        }
    }

    private fun randomDiagonalAngle(rng: Random): Float {
        val base = 30f + rng.nextFloat() * 30f // 30-60 degrees
        return base + 90f * rng.nextInt(4)
    }

    /** Line through a point at a given angle, extending to screen edges and beyond */
    private fun lineThroughPoint(
        px: Float, py: Float, angle: Float, w: Float, h: Float
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val rad = Math.toRadians(angle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val extent = w + h
        return (px - dx * extent to py - dy * extent) to (px + dx * extent to py + dy * extent)
    }

    private fun generateChainsForLock(
        rng: Random, lockX: Float, lockY: Float,
        w: Float, h: Float, linkW: Float, linkH: Float
    ): List<ChainRun> {
        val runs = mutableListOf<ChainRun>()

        val chainCount = 3 + rng.nextInt(3)
        for (c in 0 until chainCount) {
            val angle = randomDiagonalAngle(rng)
            // Small perpendicular offset so chains don't all cross at exactly the same pixel
            val offsetDist = (rng.nextFloat() - 0.5f) * linkW * 2f
            val perpRad = Math.toRadians((angle + 90.0))
            val px = lockX + (cos(perpRad) * offsetDist).toFloat()
            val py = lockY + (sin(perpRad) * offsetDist).toFloat()
            val (start, end) = lineThroughPoint(px, py, angle, w, h)

            runs.add(buildStraightChain(start.first, start.second, end.first, end.second,
                w, h, angle, linkW, linkH))
        }

        return runs
    }

    private fun buildStraightChain(
        sx: Float, sy: Float, ex: Float, ey: Float,
        screenW: Float, screenH: Float,
        angle: Float, linkW: Float, linkH: Float
    ): ChainRun {
        val links = mutableListOf<ChainLink>()
        val dx = ex - sx
        val dy = ey - sy
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        val linkSpacing = linkW * 0.8f
        val linkCount = (dist / linkSpacing).toInt()

        for (i in 0 until linkCount) {
            val t = i.toFloat() / linkCount
            val bx = sx + dx * t
            val by = sy + dy * t

            if (bx < -linkW * 2 || bx > screenW + linkW * 2 ||
                by < -linkH * 2 || by > screenH + linkH * 2) continue

            val rot = angle + if (i % 2 == 0) 0f else 90f
            links.add(ChainLink(bx, by, linkW, linkH, rot))
        }
        return ChainRun(links)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (active && locks.isNotEmpty()) {
            val count = locks.size
            locks.clear()
            generateLocks(count)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!active) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            val lockSize = width * 0.0675f
            val touchRadius = lockSize * 2f

            val iterator = locks.iterator()
            while (iterator.hasNext()) {
                val lock = iterator.next()
                val dx = event.x - lock.x
                val dy = event.y - lock.y
                if (dx * dx + dy * dy < touchRadius * touchRadius) {
                    iterator.remove()
                    if (locks.isEmpty()) {
                        active = false
                        visibility = GONE
                        onLockdownCleared?.invoke()
                    }
                    invalidate()
                    return true
                }
            }
        }
        return true
    }

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        if (!active) return

        // Dim everything below to draw attention to the chains
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        chainPaint.strokeWidth = 6f * density
        chainShadowPaint.strokeWidth = 8f * density

        for (lock in locks) {
            for (run in lock.chainRuns) {
                for (link in run.links) {
                    drawChainLink(canvas, link)
                }
            }
        }

        val lockSize = width * 0.0675f
        lockEdgePaint.strokeWidth = 3f * density
        for (lock in locks) {
            drawPadlock(canvas, lock.x, lock.y, lockSize)
        }
    }

    private fun drawChainLink(canvas: Canvas, link: ChainLink) {
        canvas.save()
        canvas.translate(link.cx, link.cy)
        canvas.rotate(link.rotation)

        val hw = link.width / 2f
        val hh = link.height / 2f
        val rect = RectF(-hw, -hh, hw, hh)

        canvas.save()
        canvas.translate(1.5f * density, 2f * density)
        canvas.drawRoundRect(rect, hh, hh, chainShadowPaint)
        canvas.restore()

        canvas.drawRoundRect(rect, hh, hh, chainPaint)

        canvas.restore()
    }

    private fun drawPadlock(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // Shackle
        val shackleWidth = size * 0.6f
        val shackleHeight = size * 0.55f
        shacklePaint.strokeWidth = size * 0.16f
        val shackleRect = RectF(
            cx - shackleWidth / 2, cy - size * 0.55f - shackleHeight,
            cx + shackleWidth / 2, cy - size * 0.3f
        )
        canvas.drawArc(shackleRect, 180f, 180f, false, shacklePaint)
        canvas.drawLine(cx - shackleWidth / 2, shackleRect.centerY(),
            cx - shackleWidth / 2, cy - size * 0.25f, shacklePaint)
        canvas.drawLine(cx + shackleWidth / 2, shackleRect.centerY(),
            cx + shackleWidth / 2, cy - size * 0.25f, shacklePaint)

        // Body
        val bodyW = size * 1.0f
        val bodyH = size * 0.85f
        val bodyRect = RectF(cx - bodyW / 2, cy - size * 0.25f, cx + bodyW / 2, cy - size * 0.25f + bodyH)
        canvas.drawRoundRect(bodyRect, size * 0.12f, size * 0.12f, lockBodyPaint)
        canvas.drawRoundRect(bodyRect, size * 0.12f, size * 0.12f, lockEdgePaint)

        // Highlight
        val hlRect = RectF(bodyRect.left + bodyW * 0.08f, bodyRect.top + bodyH * 0.06f,
            bodyRect.left + bodyW * 0.42f, bodyRect.top + bodyH * 0.38f)
        canvas.drawRoundRect(hlRect, size * 0.06f, size * 0.06f, lockHighlightPaint)

        // Keyhole
        val keyCy = bodyRect.top + bodyH * 0.48f
        val khRadius = size * 0.12f
        canvas.drawCircle(cx, keyCy, khRadius, keyholePaint)
        val slotPath = Path()
        slotPath.moveTo(cx - khRadius * 0.45f, keyCy + khRadius * 0.5f)
        slotPath.lineTo(cx, keyCy + khRadius * 2.5f)
        slotPath.lineTo(cx + khRadius * 0.45f, keyCy + khRadius * 0.5f)
        slotPath.close()
        canvas.drawPath(slotPath, keyholePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        active = false
    }
}
