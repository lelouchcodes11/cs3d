package android.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import com.lagradost.desktop.runtime.res.ResourceSupport
import com.lagradost.desktop.runtime.ui.ViewAttributes
import kotlin.math.max

/**
 * AOSP GridLayout for the layouts that place children in cells (the download rows).
 * An undefined column count lays children out in one row. match_parent / 0 width takes the
 * remaining space; layout_gravity places a child inside its cell.
 */
open class GridLayout : ViewGroup {
    companion object {
        const val HORIZONTAL = 0
        const val VERTICAL = 1
        const val UNDEFINED = Int.MIN_VALUE
        const val ALIGN_BOUNDS = 0
        const val ALIGN_MARGINS = 1
    }

    private var orientation = HORIZONTAL
    private var columnCount = UNDEFINED
    private var rowCount = UNDEFINED
    private var colWidths = IntArray(0)
    private var rowHeights = IntArray(0)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) { read(attrs) }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { read(attrs) }
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr) { read(attrs) }

    private fun read(attrs: AttributeSet?) {
        if (attrs == null) return
        val r = ViewAttributes.reader(this, attrs)
        when (r.text("orientation")?.toString()) {
            "vertical", "1" -> orientation = VERTICAL
            "horizontal", "0" -> orientation = HORIZONTAL
        }
        r.int("columnCount")?.let { columnCount = it }
        r.int("rowCount")?.let { rowCount = it }
    }

    open fun setOrientation(orientation: Int) {
        this.orientation = orientation
        requestLayout()
    }

    open fun getOrientation(): Int = orientation
    open fun setColumnCount(columnCount: Int) {
        this.columnCount = columnCount
        requestLayout()
    }

    open fun getColumnCount(): Int = columnCount
    open fun setRowCount(rowCount: Int) {
        this.rowCount = rowCount
        requestLayout()
    }

    open fun getRowCount(): Int = rowCount

    open class LayoutParams : MarginLayoutParams {
        var row = UNDEFINED
        var column = UNDEFINED
        var rowSpan = 1
        var columnSpan = 1
        var gravity = -1

        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs) {
            if (attrs != null && c != null) {
                intAttr(c, attrs, "layout_row")?.let { row = it }
                intAttr(c, attrs, "layout_column")?.let { column = it }
                intAttr(c, attrs, "layout_rowSpan")?.let { rowSpan = it.coerceAtLeast(1) }
                intAttr(c, attrs, "layout_columnSpan")?.let { columnSpan = it.coerceAtLeast(1) }
                val tv = ResourceSupport.attr(c.resources, attrs, "layout_gravity")
                gravity = when {
                    tv == null -> -1
                    tv.type == TypedValue.TYPE_INT_DEC || tv.type == TypedValue.TYPE_INT_HEX -> tv.data
                    else -> ViewAttributes.parseGravity(tv.string?.toString()) ?: -1
                }
            }
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.LayoutParams) : super(source) {
            if (source is LayoutParams) copy(source)
        }

        constructor(source: MarginLayoutParams) : super(source) {
            if (source is LayoutParams) copy(source)
        }

        private fun copy(source: LayoutParams) {
            row = source.row
            column = source.column
            rowSpan = source.rowSpan
            columnSpan = source.columnSpan
            gravity = source.gravity
        }
    }

    private class Cell(val view: View, val row: Int, val column: Int, val rowSpan: Int, val columnSpan: Int)

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = LayoutParams(getContext(), attrs)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = when (p) {
        is LayoutParams -> LayoutParams(p)
        is MarginLayoutParams -> LayoutParams(p)
        else -> LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    private fun cells(): List<Cell> {
        val out = ArrayList<Cell>()
        var autoCol = 0
        var autoRow = 0
        val maxCols = if (columnCount != UNDEFINED && columnCount > 0) columnCount else Int.MAX_VALUE
        val horizontal = orientation != VERTICAL
        for (i in 0 until getChildCount()) {
            val child = getChildAt(i) ?: continue
            if (child.getVisibility() == GONE) continue
            val lp = child.getLayoutParams() as? LayoutParams ?: LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val rs = lp.rowSpan.coerceAtLeast(1)
            val cs = lp.columnSpan.coerceAtLeast(1)
            var row = lp.row
            var col = lp.column
            if (row == UNDEFINED || col == UNDEFINED) {
                if (horizontal) {
                    if (autoCol >= maxCols) {
                        autoCol = 0
                        autoRow++
                    }
                    if (col == UNDEFINED) col = autoCol
                    if (row == UNDEFINED) row = autoRow
                    autoCol = col + cs
                } else {
                    val maxRows = if (rowCount != UNDEFINED && rowCount > 0) rowCount else Int.MAX_VALUE
                    if (autoRow >= maxRows) {
                        autoRow = 0
                        autoCol++
                    }
                    if (row == UNDEFINED) row = autoRow
                    if (col == UNDEFINED) col = autoCol
                    autoRow = row + rs
                }
            }
            out.add(Cell(child, row, col, rs, cs))
        }
        return out
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val innerW = max(0, wSize - getPaddingLeft() - getPaddingRight())
        val placed = cells()
        val cols = max(1, placed.maxOfOrNull { it.column + it.columnSpan } ?: 1)
        val rows = max(1, placed.maxOfOrNull { it.row + it.rowSpan } ?: 1)
        val colW = IntArray(cols)
        val rowH = IntArray(rows)
        val flexCol = BooleanArray(cols)
        val flexRow = BooleanArray(rows)
        for (cell in placed) {
            val lp = cell.view.getLayoutParams() as? LayoutParams
            val flexW = lp != null && (lp.width == ViewGroup.LayoutParams.MATCH_PARENT || lp.width == 0)
            val flexH = lp != null && (lp.height == ViewGroup.LayoutParams.MATCH_PARENT || lp.height == 0)
            if (flexW) for (c in cell.column until minOf(cols, cell.column + cell.columnSpan)) flexCol[c] = true
            if (flexH) for (r in cell.row until minOf(rows, cell.row + cell.rowSpan)) flexRow[r] = true
            val childW = if (flexW) MeasureSpec.makeMeasureSpec(innerW, MeasureSpec.AT_MOST)
            else getChildMeasureSpec(widthMeasureSpec, getPaddingLeft() + getPaddingRight() + (lp?.leftMargin ?: 0) + (lp?.rightMargin ?: 0), lp?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT)
            val childH = getChildMeasureSpec(heightMeasureSpec, getPaddingTop() + getPaddingBottom(), lp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT)
            cell.view.measure(childW, childH)
            if (!flexW) {
                val span = cell.columnSpan.coerceAtLeast(1)
                val each = cell.view.getMeasuredWidth() / span
                for (c in cell.column until minOf(cols, cell.column + span)) colW[c] = max(colW[c], each)
            }
            if (!flexH) {
                val span = cell.rowSpan.coerceAtLeast(1)
                val each = cell.view.getMeasuredHeight() / span
                for (r in cell.row until minOf(rows, cell.row + span)) rowH[r] = max(rowH[r], each)
            }
        }
        if (flexCol.count { it } > 0 && wMode != MeasureSpec.UNSPECIFIED) {
            val share = max(0, innerW - colW.sum()) / flexCol.count { it }
            for (c in 0 until cols) if (flexCol[c]) colW[c] = max(colW[c], share)
        }
        if (hMode == MeasureSpec.EXACTLY) {
            val innerH = max(0, hSize - getPaddingTop() - getPaddingBottom())
            val flexRows = flexRow.count { it }
            if (flexRows > 0) {
                val share = max(0, innerH - rowH.sum()) / flexRows
                for (r in 0 until rows) if (flexRow[r]) rowH[r] = max(rowH[r], share)
            } else if (rows == 1) {
                rowH[0] = max(rowH[0], innerH)
            }
        }
        for (cell in placed) {
            val lp = cell.view.getLayoutParams() as? LayoutParams ?: continue
            val flexW = lp.width == ViewGroup.LayoutParams.MATCH_PARENT || lp.width == 0
            val flexH = lp.height == ViewGroup.LayoutParams.MATCH_PARENT || lp.height == 0
            if (!flexW && !flexH) continue
            var w = 0
            for (c in cell.column until minOf(cols, cell.column + cell.columnSpan)) w += colW.getOrElse(c) { 0 }
            w = max(0, w - lp.leftMargin - lp.rightMargin)
            var h = 0
            for (r in cell.row until minOf(rows, cell.row + cell.rowSpan)) h += rowH.getOrElse(r) { 0 }
            h = max(0, h - lp.topMargin - lp.bottomMargin)
            val ws = if (flexW) MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(cell.view.getMeasuredWidth(), MeasureSpec.EXACTLY)
            val hs = if (flexH) MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY) else MeasureSpec.makeMeasureSpec(cell.view.getMeasuredHeight(), MeasureSpec.EXACTLY)
            cell.view.measure(ws, hs)
            if (!flexH) {
                for (r in cell.row until minOf(rows, cell.row + cell.rowSpan)) rowH[r] = max(rowH[r], cell.view.getMeasuredHeight())
            }
        }
        colWidths = colW
        rowHeights = rowH
        setMeasuredDimension(
            View.resolveSize(getPaddingLeft() + getPaddingRight() + colW.sum(), widthMeasureSpec),
            View.resolveSize(getPaddingTop() + getPaddingBottom() + rowH.sum(), heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (colWidths.isEmpty()) return
        val placed = cells()
        val colX = IntArray(colWidths.size + 1)
        colX[0] = getPaddingLeft()
        for (i in colWidths.indices) colX[i + 1] = colX[i] + colWidths[i]
        val rowY = IntArray(rowHeights.size + 1)
        rowY[0] = getPaddingTop()
        for (i in rowHeights.indices) rowY[i + 1] = rowY[i] + rowHeights[i]
        for (cell in placed) {
            val lp = cell.view.getLayoutParams() as? LayoutParams
            val c0 = cell.column.coerceIn(0, colWidths.size)
            val c1 = (cell.column + cell.columnSpan).coerceIn(c0, colWidths.size)
            val r0 = cell.row.coerceIn(0, rowHeights.size)
            val r1 = (cell.row + cell.rowSpan).coerceIn(r0, rowHeights.size)
            if (c1 <= c0 || r1 <= r0) continue
            val cellL = colX[c0]
            val cellR = colX[c1]
            val cellT = rowY[r0]
            val cellB = rowY[r1]
            val w = cell.view.getMeasuredWidth()
            val h = cell.view.getMeasuredHeight()
            val g = lp?.gravity?.takeIf { it >= 0 } ?: (Gravity.START or Gravity.TOP)
            val childLeft = when (g and 0x07) {
                Gravity.CENTER_HORIZONTAL -> cellL + (cellR - cellL - w) / 2
                Gravity.RIGHT -> cellR - w - (lp?.rightMargin ?: 0)
                else -> cellL + (lp?.leftMargin ?: 0)
            }
            val childTop = when (g and Gravity.VERTICAL_GRAVITY_MASK) {
                Gravity.CENTER_VERTICAL -> cellT + (cellB - cellT - h) / 2
                Gravity.BOTTOM -> cellB - h - (lp?.bottomMargin ?: 0)
                else -> cellT + (lp?.topMargin ?: 0)
            }
            cell.view.layout(childLeft, childTop, childLeft + w, childTop + h)
        }
    }
}

private fun intAttr(c: Context, attrs: AttributeSet, name: String): Int? {
    val tv = ResourceSupport.attr(c.resources, attrs, name) ?: return null
    return when (tv.type) {
        TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> tv.data
        else -> tv.string?.toString()?.toIntOrNull()
    }
}
