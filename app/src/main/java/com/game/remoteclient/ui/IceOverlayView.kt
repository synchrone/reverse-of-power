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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class IceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { FROZEN, CRACKING, SHATTERED }

    var state = State.SHATTERED
        private set

    var onIceShattered: (() -> Unit)? = null

    private var tapsNeeded = 5
    private var tapCount = 0
    private var seed = 0L

    // Frost dendrites (branching veins from edges)
    private val frostVeins = mutableListOf<Path>()
    // Small crystal star shapes
    private val crystals = mutableListOf<Crystal>()

    // Crack lines accumulated from taps
    private val crackSegments = mutableListOf<CrackSegment>()

    private val iceFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88CADFEA")
        style = Paint.Style.FILL
    }

    private val edgeFrostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val veinGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val crystalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.ROUND
    }

    private val crystalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }

    private val iceEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66D0ECF4")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val crackGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private data class Crystal(val cx: Float, val cy: Float, val size: Float, val rotation: Float)
    private data class CrackSegment(val path: Path)

    fun activate(count: Int, viewSeed: Int) {
        tapsNeeded = 5 * count.coerceAtLeast(1)
        tapCount = 0
        seed = viewSeed.toLong()
        state = State.FROZEN
        crackSegments.clear()
        frostVeins.clear()
        crystals.clear()
        visibility = VISIBLE
        alpha = 1f
        generateFrost()
        invalidate()
    }

    fun reset() {
        state = State.SHATTERED
        crackSegments.clear()
        frostVeins.clear()
        crystals.clear()
        visibility = GONE
    }

    private fun generateFrost() {
        val rng = Random(seed)
        val w = if (width > 0) width.toFloat() else 400f
        val h = if (height > 0) height.toFloat() else 200f

        // Frost dendrites growing inward from edges
        generateDendrites(rng, w, h)

        // Small ice crystal stars scattered across the surface
        for (i in 0 until 15) {
            crystals.add(Crystal(
                cx = rng.nextFloat() * w,
                cy = rng.nextFloat() * h,
                size = 4f + rng.nextFloat() * 10f,
                rotation = rng.nextFloat() * 360f
            ))
        }
    }

    private fun generateDendrites(rng: Random, w: Float, h: Float) {
        // Spawn dendrites from each edge growing inward
        val edgeCount = 8 + rng.nextInt(6)
        for (i in 0 until edgeCount) {
            val path = Path()
            val edge = rng.nextInt(4) // 0=top, 1=right, 2=bottom, 3=left
            var x: Float
            var y: Float
            var baseAngle: Float

            when (edge) {
                0 -> { x = rng.nextFloat() * w; y = 0f; baseAngle = 90f }
                1 -> { x = w; y = rng.nextFloat() * h; baseAngle = 180f }
                2 -> { x = rng.nextFloat() * w; y = h; baseAngle = 270f }
                else -> { x = 0f; y = rng.nextFloat() * h; baseAngle = 0f }
            }

            path.moveTo(x, y)
            growBranch(rng, path, x, y, baseAngle, w, h, depth = 0, maxDepth = 3)
            frostVeins.add(path)
        }
    }

    private fun growBranch(
        rng: Random, path: Path,
        startX: Float, startY: Float,
        baseAngle: Float, w: Float, h: Float,
        depth: Int, maxDepth: Int
    ) {
        var x = startX
        var y = startY
        var angle = baseAngle
        val segments = 3 + rng.nextInt(4)
        val lenScale = 1f / (1 + depth * 0.6f)

        for (s in 0 until segments) {
            angle += -30f + rng.nextFloat() * 60f
            val rad = Math.toRadians(angle.toDouble())
            val len = (12f + rng.nextFloat() * 20f) * lenScale
            x += (cos(rad) * len).toFloat()
            y += (sin(rad) * len).toFloat()
            x = x.coerceIn(0f, w)
            y = y.coerceIn(0f, h)
            path.lineTo(x, y)

            // Spawn sub-branches
            if (depth < maxDepth && rng.nextFloat() < 0.45f) {
                val branchPath = Path()
                branchPath.moveTo(x, y)
                val branchAngle = angle + if (rng.nextBoolean()) 40f + rng.nextFloat() * 30f
                                          else -(40f + rng.nextFloat() * 30f)
                growBranch(rng, branchPath, x, y, branchAngle, w, h, depth + 1, maxDepth)
                frostVeins.add(branchPath)
            }
        }
    }

    private fun generateCracks(tapX: Float, tapY: Float) {
        val rng = Random(seed + tapCount * 31L)
        val w = width.toFloat()
        val h = height.toFloat()
        val branchCount = 3 + rng.nextInt(3)

        for (b in 0 until branchCount) {
            val path = Path()
            var x = tapX
            var y = tapY
            path.moveTo(x, y)

            var angle = rng.nextFloat() * 360f
            val segmentCount = 4 + rng.nextInt(5)

            for (s in 0 until segmentCount) {
                angle += -40f + rng.nextFloat() * 80f
                val rad = Math.toRadians(angle.toDouble())
                val len = 15f + rng.nextFloat() * 30f
                x += (cos(rad) * len).toFloat()
                y += (sin(rad) * len).toFloat()
                x = x.coerceIn(0f, w)
                y = y.coerceIn(0f, h)
                path.lineTo(x, y)

                // Sub-branches
                if (rng.nextFloat() < 0.4f) {
                    val subPath = Path()
                    subPath.moveTo(x, y)
                    val subAngle = angle + if (rng.nextBoolean()) 45f else -45f
                    val subRad = Math.toRadians(subAngle.toDouble())
                    val subLen = 10f + rng.nextFloat() * 20f
                    subPath.lineTo(
                        (x + cos(subRad) * subLen).toFloat().coerceIn(0f, w),
                        (y + sin(subRad) * subLen).toFloat().coerceIn(0f, h)
                    )
                    crackSegments.add(CrackSegment(subPath))
                }
            }
            crackSegments.add(CrackSegment(path))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (state != State.SHATTERED) {
            frostVeins.clear()
            crystals.clear()
            generateFrost()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == State.SHATTERED) return false

        if (event.action == MotionEvent.ACTION_DOWN) {
            tapCount++
            generateCracks(event.x, event.y)
            state = State.CRACKING

            if (tapCount >= tapsNeeded) {
                shatter()
            } else {
                invalidate()
            }
            return true
        }
        return true // consume all touches while frozen
    }

    private fun shatter() {
        state = State.SHATTERED
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            addUpdateListener { anim ->
                alpha = anim.animatedValue as Float
                if (alpha <= 0f) {
                    visibility = GONE
                    onIceShattered?.invoke()
                }
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (state == State.SHATTERED) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cornerRadius = resources.displayMetrics.density * 16f

        // Clip to rounded rect
        val clipPath = Path()
        clipPath.addRoundRect(RectF(0f, 0f, w, h), cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)

        // Base ice fill — subtle translucent blue
        canvas.drawRect(0f, 0f, w, h, iceFillPaint)

        // Frosted edge buildup — thicker white frost near all edges
        drawEdgeFrost(canvas, w, h)

        // Frost dendrite veins
        for (vein in frostVeins) {
            canvas.drawPath(vein, veinGlowPaint)
            canvas.drawPath(vein, veinPaint)
        }

        // Ice crystal stars
        for (crystal in crystals) {
            drawCrystal(canvas, crystal)
        }

        // Edge highlight stroke
        val edgeRect = RectF(1f, 1f, w - 1f, h - 1f)
        canvas.drawRoundRect(edgeRect, cornerRadius, cornerRadius, iceEdgePaint)

        // Crack lines
        for (crack in crackSegments) {
            canvas.drawPath(crack.path, crackGlowPaint)
            canvas.drawPath(crack.path, crackPaint)
        }

        canvas.restore()
    }

    private fun drawEdgeFrost(canvas: Canvas, w: Float, h: Float) {
        val edgeWidth = min(w, h) * 0.18f
        val white = Color.parseColor("#88FFFFFF")
        val clear = Color.TRANSPARENT

        // Top edge
        edgeFrostPaint.shader = LinearGradient(0f, 0f, 0f, edgeWidth, white, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, edgeWidth, edgeFrostPaint)

        // Bottom edge
        edgeFrostPaint.shader = LinearGradient(0f, h, 0f, h - edgeWidth, white, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - edgeWidth, w, h, edgeFrostPaint)

        // Left edge
        edgeFrostPaint.shader = LinearGradient(0f, 0f, edgeWidth, 0f, white, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, edgeWidth, h, edgeFrostPaint)

        // Right edge
        edgeFrostPaint.shader = LinearGradient(w, 0f, w - edgeWidth, 0f, white, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(w - edgeWidth, 0f, w, h, edgeFrostPaint)

        edgeFrostPaint.shader = null
    }

    private fun drawCrystal(canvas: Canvas, crystal: Crystal) {
        val spokes = 6
        val angleStep = 360f / spokes
        val path = Path()

        for (i in 0 until spokes) {
            val angle = crystal.rotation + i * angleStep
            val rad = Math.toRadians(angle.toDouble())
            val endX = crystal.cx + (cos(rad) * crystal.size).toFloat()
            val endY = crystal.cy + (sin(rad) * crystal.size).toFloat()
            path.moveTo(crystal.cx, crystal.cy)
            path.lineTo(endX, endY)

            // Small barbs on each spoke
            if (crystal.size > 6f) {
                val midX = crystal.cx + (cos(rad) * crystal.size * 0.55f).toFloat()
                val midY = crystal.cy + (sin(rad) * crystal.size * 0.55f).toFloat()
                val barbLen = crystal.size * 0.35f
                for (side in listOf(-45f, 45f)) {
                    val barbRad = Math.toRadians((angle + side).toDouble())
                    path.moveTo(midX, midY)
                    path.lineTo(
                        midX + (cos(barbRad) * barbLen).toFloat(),
                        midY + (sin(barbRad) * barbLen).toFloat()
                    )
                }
            }
        }

        canvas.drawPath(path, crystalPaint)
    }
}
