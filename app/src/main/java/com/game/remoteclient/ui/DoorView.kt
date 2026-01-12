package com.game.remoteclient.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class DoorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DoorStyle {
        POINTED_ARCH,    // Door 0 - pointed top with zigzag
        DOMED,           // Door 1 - dome top with triangles
        SCALLOPED,       // Door 2 - scalloped/keyhole arch
        SIMPLE_ARCH      // Door 3 - simple pointed arch
    }

    private var doorStyle = DoorStyle.POINTED_ARCH
    private var borderColor = Color.parseColor("#D4AF37") // Gold
    private var doorColor = Color.parseColor("#8B7355")   // Brown/taupe
    private var frameColor = Color.parseColor("#F5E6C8")  // Cream/beige

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val doorDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glitterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD700")
    }

    var doorIndex: Int = 0
        set(value) {
            field = value
            doorStyle = when (value % 4) {
                0 -> DoorStyle.POINTED_ARCH
                1 -> DoorStyle.DOMED
                2 -> DoorStyle.SCALLOPED
                else -> DoorStyle.SIMPLE_ARCH
            }
            invalidate()
        }

    fun setDoorColor(color: Int) {
        // Derive colors from the base color
        borderColor = Color.parseColor("#D4AF37") // Keep gold border
        doorColor = blendColors(color, Color.parseColor("#5C4033"), 0.5f)
        frameColor = blendColors(color, Color.WHITE, 0.7f)
        invalidate()
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val r = (Color.red(color1) * ratio + Color.red(color2) * inverseRatio).toInt()
        val g = (Color.green(color1) * ratio + Color.green(color2) * inverseRatio).toInt()
        val b = (Color.blue(color1) * ratio + Color.blue(color2) * inverseRatio).toInt()
        return Color.rgb(r, g, b)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = min(w, h) * 0.05f
        val borderWidth = min(w, h) * 0.06f

        when (doorStyle) {
            DoorStyle.POINTED_ARCH -> drawPointedArchDoor(canvas, w, h, padding, borderWidth)
            DoorStyle.DOMED -> drawDomedDoor(canvas, w, h, padding, borderWidth)
            DoorStyle.SCALLOPED -> drawScallopedDoor(canvas, w, h, padding, borderWidth)
            DoorStyle.SIMPLE_ARCH -> drawSimpleArchDoor(canvas, w, h, padding, borderWidth)
        }
    }

    private fun drawPointedArchDoor(canvas: Canvas, w: Float, h: Float, padding: Float, borderWidth: Float) {
        val path = Path()
        val centerX = w / 2

        // Outer border shape - pointed arch with house-like top
        borderPaint.color = borderColor
        path.moveTo(padding, h - padding)
        path.lineTo(padding, h * 0.4f)
        path.lineTo(centerX, padding + h * 0.05f)
        path.lineTo(w - padding, h * 0.4f)
        path.lineTo(w - padding, h - padding)
        path.close()
        canvas.drawPath(path, borderPaint)
        drawGlitterEffect(canvas, path)

        // Inner frame
        framePaint.color = frameColor
        val innerPath = Path()
        val inset = borderWidth
        innerPath.moveTo(padding + inset, h - padding - inset)
        innerPath.lineTo(padding + inset, h * 0.4f + inset)
        innerPath.lineTo(centerX, padding + h * 0.05f + inset * 1.5f)
        innerPath.lineTo(w - padding - inset, h * 0.4f + inset)
        innerPath.lineTo(w - padding - inset, h - padding - inset)
        innerPath.close()
        canvas.drawPath(innerPath, framePaint)

        // Door panel
        doorPaint.color = doorColor
        val doorInset = borderWidth * 2
        val doorPath = Path()
        doorPath.moveTo(padding + doorInset, h - padding - doorInset)
        doorPath.lineTo(padding + doorInset, h * 0.45f)
        doorPath.lineTo(centerX, padding + h * 0.12f)
        doorPath.lineTo(w - padding - doorInset, h * 0.45f)
        doorPath.lineTo(w - padding - doorInset, h - padding - doorInset)
        doorPath.close()
        canvas.drawPath(doorPath, doorPaint)

        // Zigzag decoration at top
        drawZigzagDecoration(canvas, centerX, h * 0.35f, w * 0.4f)

        // Door panels (4 rectangles)
        drawDoorPanels(canvas, padding + doorInset, h * 0.5f, w - 2 * (padding + doorInset), h * 0.4f)
    }

    private fun drawDomedDoor(canvas: Canvas, w: Float, h: Float, padding: Float, borderWidth: Float) {
        val path = Path()
        val centerX = w / 2

        // Outer border - domed top
        borderPaint.color = borderColor
        path.moveTo(padding, h - padding)
        path.lineTo(padding, h * 0.5f)
        path.quadTo(padding, h * 0.2f, centerX, padding + h * 0.05f)
        path.quadTo(w - padding, h * 0.2f, w - padding, h * 0.5f)
        path.lineTo(w - padding, h - padding)
        path.close()
        canvas.drawPath(path, borderPaint)
        drawGlitterEffect(canvas, path)

        // Inner frame
        framePaint.color = frameColor
        val inset = borderWidth
        val innerPath = Path()
        innerPath.moveTo(padding + inset, h - padding - inset)
        innerPath.lineTo(padding + inset, h * 0.5f)
        innerPath.quadTo(padding + inset, h * 0.22f, centerX, padding + h * 0.08f)
        innerPath.quadTo(w - padding - inset, h * 0.22f, w - padding - inset, h * 0.5f)
        innerPath.lineTo(w - padding - inset, h - padding - inset)
        innerPath.close()
        canvas.drawPath(innerPath, framePaint)

        // Door panel with dome
        doorPaint.color = doorColor
        val doorInset = borderWidth * 2
        val doorPath = Path()
        doorPath.moveTo(padding + doorInset, h - padding - doorInset)
        doorPath.lineTo(padding + doorInset, h * 0.55f)
        doorPath.quadTo(padding + doorInset, h * 0.3f, centerX, h * 0.2f)
        doorPath.quadTo(w - padding - doorInset, h * 0.3f, w - padding - doorInset, h * 0.55f)
        doorPath.lineTo(w - padding - doorInset, h - padding - doorInset)
        doorPath.close()
        canvas.drawPath(doorPath, doorPaint)

        // Triangle decorations
        drawTriangleDecoration(canvas, centerX, h * 0.35f, w * 0.35f)

        // Ornament at top
        drawTopOrnament(canvas, centerX, padding + h * 0.02f)
    }

    private fun drawScallopedDoor(canvas: Canvas, w: Float, h: Float, padding: Float, borderWidth: Float) {
        val path = Path()
        val centerX = w / 2

        // Outer border - scalloped/keyhole shape
        borderPaint.color = borderColor
        path.moveTo(padding, h - padding)
        path.lineTo(padding, h * 0.5f)
        path.quadTo(padding, h * 0.3f, centerX * 0.6f, h * 0.25f)
        path.quadTo(centerX * 0.7f, h * 0.1f, centerX, padding + h * 0.08f)
        path.quadTo(centerX * 1.3f, h * 0.1f, centerX * 1.4f, h * 0.25f)
        path.quadTo(w - padding, h * 0.3f, w - padding, h * 0.5f)
        path.lineTo(w - padding, h - padding)
        path.close()
        canvas.drawPath(path, borderPaint)
        drawGlitterEffect(canvas, path)

        // Inner frame
        framePaint.color = frameColor
        val inset = borderWidth
        val innerPath = Path()
        innerPath.moveTo(padding + inset, h - padding - inset)
        innerPath.lineTo(padding + inset, h * 0.5f)
        innerPath.quadTo(padding + inset, h * 0.32f, centerX * 0.65f, h * 0.27f)
        innerPath.quadTo(centerX * 0.72f, h * 0.13f, centerX, padding + h * 0.11f)
        innerPath.quadTo(centerX * 1.28f, h * 0.13f, centerX * 1.35f, h * 0.27f)
        innerPath.quadTo(w - padding - inset, h * 0.32f, w - padding - inset, h * 0.5f)
        innerPath.lineTo(w - padding - inset, h - padding - inset)
        innerPath.close()
        canvas.drawPath(innerPath, framePaint)

        // Door with arch
        doorPaint.color = doorColor
        val doorInset = borderWidth * 2.5f
        canvas.drawRoundRect(
            padding + doorInset,
            h * 0.4f,
            w - padding - doorInset,
            h - padding - doorInset,
            w * 0.15f,
            w * 0.15f,
            doorPaint
        )

        // Inner arch window
        drawDetailPaint(Color.parseColor("#6B5344"))
        val archPath = Path()
        archPath.addArc(
            centerX - w * 0.15f,
            h * 0.42f,
            centerX + w * 0.15f,
            h * 0.62f,
            180f, 180f
        )
        archPath.lineTo(centerX + w * 0.15f, h * 0.7f)
        archPath.lineTo(centerX - w * 0.15f, h * 0.7f)
        archPath.close()
        canvas.drawPath(archPath, doorDetailPaint)

        // Triangle in arch
        drawTriangleInArch(canvas, centerX, h * 0.45f, w * 0.12f)
    }

    private fun drawSimpleArchDoor(canvas: Canvas, w: Float, h: Float, padding: Float, borderWidth: Float) {
        val path = Path()
        val centerX = w / 2

        // Outer border - onion dome style
        borderPaint.color = borderColor
        path.moveTo(padding, h - padding)
        path.lineTo(padding, h * 0.45f)
        path.quadTo(padding, h * 0.25f, centerX, padding + h * 0.05f)
        path.quadTo(w - padding, h * 0.25f, w - padding, h * 0.45f)
        path.lineTo(w - padding, h - padding)
        path.close()
        canvas.drawPath(path, borderPaint)
        drawGlitterEffect(canvas, path)

        // Inner frame
        framePaint.color = frameColor
        val inset = borderWidth
        val innerPath = Path()
        innerPath.moveTo(padding + inset, h - padding - inset)
        innerPath.lineTo(padding + inset, h * 0.45f)
        innerPath.quadTo(padding + inset, h * 0.27f, centerX, padding + h * 0.08f)
        innerPath.quadTo(w - padding - inset, h * 0.27f, w - padding - inset, h * 0.45f)
        innerPath.lineTo(w - padding - inset, h - padding - inset)
        innerPath.close()
        canvas.drawPath(innerPath, framePaint)

        // Door panel
        doorPaint.color = doorColor
        val doorInset = borderWidth * 2
        val doorPath = Path()
        doorPath.moveTo(padding + doorInset, h - padding - doorInset)
        doorPath.lineTo(padding + doorInset, h * 0.5f)
        doorPath.quadTo(padding + doorInset, h * 0.32f, centerX, h * 0.18f)
        doorPath.quadTo(w - padding - doorInset, h * 0.32f, w - padding - doorInset, h * 0.5f)
        doorPath.lineTo(w - padding - doorInset, h - padding - doorInset)
        doorPath.close()
        canvas.drawPath(doorPath, doorPaint)

        // Inner arch detail
        drawDetailPaint(Color.parseColor("#6B5344"))
        val archPath = Path()
        val archInset = borderWidth * 3
        archPath.moveTo(padding + archInset, h - padding - archInset)
        archPath.lineTo(padding + archInset, h * 0.55f)
        archPath.quadTo(padding + archInset, h * 0.38f, centerX, h * 0.28f)
        archPath.quadTo(w - padding - archInset, h * 0.38f, w - padding - archInset, h * 0.55f)
        archPath.lineTo(w - padding - archInset, h - padding - archInset)
        archPath.close()
        canvas.drawPath(archPath, doorDetailPaint)

        // Ornaments at top
        drawTopOrnament(canvas, centerX, padding)
    }

    private fun drawGlitterEffect(canvas: Canvas, path: Path) {
        // Simulate glitter with small bright dots along the border
        val random = java.util.Random(doorIndex.toLong())
        glitterPaint.color = Color.parseColor("#FFFACD")

        val bounds = RectF()
        path.computeBounds(bounds, true)

        for (i in 0..30) {
            val x = bounds.left + random.nextFloat() * bounds.width()
            val y = bounds.top + random.nextFloat() * bounds.height()
            val radius = 1f + random.nextFloat() * 2f
            glitterPaint.alpha = 150 + random.nextInt(105)
            canvas.drawCircle(x, y, radius, glitterPaint)
        }
    }

    private fun drawZigzagDecoration(canvas: Canvas, centerX: Float, y: Float, width: Float) {
        doorDetailPaint.color = Color.parseColor("#7B6B5B")
        val path = Path()
        val teeth = 5
        val teethWidth = width / teeth
        val teethHeight = width * 0.15f

        path.moveTo(centerX - width / 2, y)
        for (i in 0 until teeth) {
            val x = centerX - width / 2 + i * teethWidth
            path.lineTo(x + teethWidth / 2, y + teethHeight)
            path.lineTo(x + teethWidth, y)
        }
        path.lineTo(centerX + width / 2, y + teethHeight * 2)
        path.lineTo(centerX - width / 2, y + teethHeight * 2)
        path.close()
        canvas.drawPath(path, doorDetailPaint)
    }

    private fun drawTriangleDecoration(canvas: Canvas, centerX: Float, y: Float, width: Float) {
        doorDetailPaint.color = Color.parseColor("#7B6B5B")
        val triangleWidth = width / 3
        val triangleHeight = width * 0.3f

        for (i in 0..2) {
            val path = Path()
            val tx = centerX - width / 2 + i * triangleWidth + triangleWidth / 2
            path.moveTo(tx, y)
            path.lineTo(tx - triangleWidth / 2.5f, y + triangleHeight)
            path.lineTo(tx + triangleWidth / 2.5f, y + triangleHeight)
            path.close()
            canvas.drawPath(path, doorDetailPaint)
        }
    }

    private fun drawTriangleInArch(canvas: Canvas, centerX: Float, y: Float, size: Float) {
        doorDetailPaint.color = Color.parseColor("#8B7B6B")
        val path = Path()
        path.moveTo(centerX, y)
        path.lineTo(centerX - size, y + size * 1.5f)
        path.lineTo(centerX + size, y + size * 1.5f)
        path.close()
        canvas.drawPath(path, doorDetailPaint)
    }

    private fun drawTopOrnament(canvas: Canvas, centerX: Float, y: Float) {
        doorDetailPaint.color = Color.parseColor("#5C4033")
        // Draw small circles as ornament
        canvas.drawCircle(centerX, y + 10f, 6f, doorDetailPaint)
        canvas.drawCircle(centerX, y + 25f, 5f, doorDetailPaint)
        canvas.drawCircle(centerX, y + 38f, 4f, doorDetailPaint)
    }

    private fun drawDoorPanels(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        doorDetailPaint.color = Color.parseColor("#6B5B4B")

        val panelWidth = width * 0.4f
        val panelHeight = height * 0.4f
        val gap = width * 0.1f
        val vGap = height * 0.1f

        // Top left panel
        canvas.drawRect(left + gap, top, left + gap + panelWidth, top + panelHeight, doorDetailPaint)
        // Top right panel
        canvas.drawRect(left + width - gap - panelWidth, top, left + width - gap, top + panelHeight, doorDetailPaint)
        // Bottom left panel
        canvas.drawRect(left + gap, top + panelHeight + vGap, left + gap + panelWidth, top + 2 * panelHeight + vGap, doorDetailPaint)
        // Bottom right panel
        canvas.drawRect(left + width - gap - panelWidth, top + panelHeight + vGap, left + width - gap, top + 2 * panelHeight + vGap, doorDetailPaint)
    }

    private fun drawDetailPaint(color: Int) {
        doorDetailPaint.color = color
    }
}
