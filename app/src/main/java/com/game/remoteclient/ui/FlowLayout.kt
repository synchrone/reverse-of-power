package com.game.remoteclient.ui

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup

class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var horizontalSpacing = 0
    var verticalSpacing = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val specWidth = MeasureSpec.getSize(widthMeasureSpec)
        val maxWidth = specWidth - paddingLeft - paddingRight
        Log.d("FlowLayout", "onMeasure: specWidth=$specWidth paddingL=$paddingLeft paddingR=$paddingRight maxWidth=$maxWidth childCount=$childCount")
        var x = 0
        var y = 0
        var rowHeight = 0

        val childWidthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, childWidthSpec, heightMeasureSpec)
            val w = child.measuredWidth + horizontalSpacing
            if (x + child.measuredWidth > maxWidth && x > 0) {
                x = 0
                y += rowHeight + verticalSpacing
                rowHeight = 0
            }
            rowHeight = maxOf(rowHeight, child.measuredHeight)
            x += w
        }
        y += rowHeight
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(y + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        Log.d("FlowLayout", "onLayout: l=$l r=$r width=${r-l} paddingL=$paddingLeft paddingR=$paddingRight maxWidth=$maxWidth")
        var x = paddingLeft
        var y = paddingTop
        var rowHeight = 0

        // First pass: compute row widths for centering
        val rows = mutableListOf<MutableList<Int>>() // child indices per row
        val rowWidths = mutableListOf<Int>()
        var currentRow = mutableListOf<Int>()
        var currentRowWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val w = child.measuredWidth + horizontalSpacing
            if (currentRowWidth + child.measuredWidth > maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                rowWidths.add(currentRowWidth - horizontalSpacing)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(i)
            currentRowWidth += w
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentRowWidth - horizontalSpacing)
        }

        // Second pass: layout centered
        for (rowIdx in rows.indices) {
            val row = rows[rowIdx]
            val offsetX = (maxWidth - rowWidths[rowIdx]) / 2 + paddingLeft
            x = offsetX
            rowHeight = 0
            for (childIdx in row) {
                val child = getChildAt(childIdx)
                child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                rowHeight = maxOf(rowHeight, child.measuredHeight)
                x += child.measuredWidth + horizontalSpacing
            }
            y += rowHeight + verticalSpacing
        }
    }
}
