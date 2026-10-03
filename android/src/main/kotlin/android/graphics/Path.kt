package android.graphics

import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathFillMode

/**
 * android.graphics.Path. Contours are recorded as simple verbs so that transforms and copies are
 * exact; a Skia path is built on demand for drawing.
 */
open class Path {
    enum class Direction { CW, CCW }
    enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    enum class Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

    private sealed interface Verb
    private data class Move(val x: Float, val y: Float) : Verb
    private data class Line(val x: Float, val y: Float) : Verb
    private data class Quad(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Verb
    private data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float) : Verb
    private data class Arc(val l: Float, val t: Float, val r: Float, val b: Float, val start: Float, val sweep: Float, val forceMove: Boolean) : Verb
    private data object Close : Verb

    private val verbs = ArrayList<Verb>()
    private var fillType = FillType.WINDING
    private var lastX = 0f
    private var lastY = 0f
    private var moveX = 0f
    private var moveY = 0f

    constructor()
    constructor(src: Path?) {
        if (src != null) set(src)
    }

    open fun set(src: Path) {
        verbs.clear()
        verbs.addAll(src.verbs)
        fillType = src.fillType
        lastX = src.lastX; lastY = src.lastY
        moveX = src.moveX; moveY = src.moveY
    }

    open fun reset() {
        verbs.clear()
        lastX = 0f; lastY = 0f
        fillType = FillType.WINDING
    }

    open fun rewind() = reset()
    open fun isEmpty(): Boolean = verbs.isEmpty()
    open fun getFillType(): FillType = fillType
    open fun setFillType(ft: FillType?) {
        fillType = ft ?: FillType.WINDING
    }

    open fun isInverseFillType(): Boolean = fillType == FillType.INVERSE_WINDING || fillType == FillType.INVERSE_EVEN_ODD
    open fun toggleInverseFillType() {
        fillType = when (fillType) {
            FillType.WINDING -> FillType.INVERSE_WINDING
            FillType.EVEN_ODD -> FillType.INVERSE_EVEN_ODD
            FillType.INVERSE_WINDING -> FillType.WINDING
            FillType.INVERSE_EVEN_ODD -> FillType.EVEN_ODD
        }
    }

    open fun moveTo(x: Float, y: Float) {
        verbs.add(Move(x, y)); lastX = x; lastY = y; moveX = x; moveY = y
    }

    open fun rMoveTo(dx: Float, dy: Float) = moveTo(lastX + dx, lastY + dy)

    open fun lineTo(x: Float, y: Float) {
        ensureMove(); verbs.add(Line(x, y)); lastX = x; lastY = y
    }

    open fun rLineTo(dx: Float, dy: Float) = lineTo(lastX + dx, lastY + dy)

    open fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        ensureMove(); verbs.add(Quad(x1, y1, x2, y2)); lastX = x2; lastY = y2
    }

    open fun rQuadTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) = quadTo(lastX + dx1, lastY + dy1, lastX + dx2, lastY + dy2)

    open fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        ensureMove(); verbs.add(Cubic(x1, y1, x2, y2, x3, y3)); lastX = x3; lastY = y3
    }

    open fun rCubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(lastX + x1, lastY + y1, lastX + x2, lastY + y2, lastX + x3, lastY + y3)

    open fun arcTo(oval: RectF, startAngle: Float, sweepAngle: Float, forceMoveTo: Boolean) =
        arcTo(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, forceMoveTo)

    open fun arcTo(oval: RectF, startAngle: Float, sweepAngle: Float) = arcTo(oval, startAngle, sweepAngle, false)

    open fun arcTo(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, forceMoveTo: Boolean) {
        verbs.add(Arc(left, top, right, bottom, startAngle, sweepAngle, forceMoveTo))
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val end = Math.toRadians((startAngle + sweepAngle).toDouble())
        lastX = (cx + (right - left) / 2 * Math.cos(end)).toFloat()
        lastY = (cy + (bottom - top) / 2 * Math.sin(end)).toFloat()
    }

    open fun close() {
        if (verbs.isNotEmpty()) {
            verbs.add(Close); lastX = moveX; lastY = moveY
        }
    }

    private fun ensureMove() {
        if (verbs.isEmpty() || verbs.last() == Close) verbs.add(Move(lastX, lastY))
    }

    open fun addRect(left: Float, top: Float, right: Float, bottom: Float, dir: Direction?) {
        moveTo(left, top)
        if (dir == Direction.CCW) {
            lineTo(left, bottom); lineTo(right, bottom); lineTo(right, top)
        } else {
            lineTo(right, top); lineTo(right, bottom); lineTo(left, bottom)
        }
        close()
    }

    open fun addRect(rect: RectF, dir: Direction?) = addRect(rect.left, rect.top, rect.right, rect.bottom, dir)

    open fun addOval(left: Float, top: Float, right: Float, bottom: Float, dir: Direction?) {
        val sweep = if (dir == Direction.CCW) -360f else 360f
        verbs.add(Arc(left, top, right, bottom, 0f, sweep, true))
        verbs.add(Close)
    }

    open fun addOval(oval: RectF, dir: Direction?) = addOval(oval.left, oval.top, oval.right, oval.bottom, dir)

    open fun addCircle(x: Float, y: Float, radius: Float, dir: Direction?) = addOval(x - radius, y - radius, x + radius, y + radius, dir)

    open fun addArc(oval: RectF, startAngle: Float, sweepAngle: Float) = addArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle)

    open fun addArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float) {
        verbs.add(Arc(left, top, right, bottom, startAngle, sweepAngle, true))
    }

    open fun addRoundRect(rect: RectF, rx: Float, ry: Float, dir: Direction?) =
        addRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, dir)

    open fun addRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, dir: Direction?) =
        addRoundRect(left, top, right, bottom, floatArrayOf(rx, ry, rx, ry, rx, ry, rx, ry), dir)

    open fun addRoundRect(rect: RectF, radii: FloatArray, dir: Direction?) =
        addRoundRect(rect.left, rect.top, rect.right, rect.bottom, radii, dir)

    open fun addRoundRect(left: Float, top: Float, right: Float, bottom: Float, radii: FloatArray, dir: Direction?) {
        val w = right - left
        val h = bottom - top
        fun r(i: Int, max: Float) = radii.getOrElse(i) { 0f }.coerceIn(0f, max / 2)
        val tlx = r(0, w); val tly = r(1, h); val trx = r(2, w); val try_ = r(3, h)
        val brx = r(4, w); val bry = r(5, h); val blx = r(6, w); val bly = r(7, h)
        moveTo(left + tlx, top)
        lineTo(right - trx, top)
        if (trx > 0 || try_ > 0) arcTo(right - 2 * trx, top, right, top + 2 * try_, 270f, 90f, false)
        lineTo(right, bottom - bry)
        if (brx > 0 || bry > 0) arcTo(right - 2 * brx, bottom - 2 * bry, right, bottom, 0f, 90f, false)
        lineTo(left + blx, bottom)
        if (blx > 0 || bly > 0) arcTo(left, bottom - 2 * bly, left + 2 * blx, bottom, 90f, 90f, false)
        lineTo(left, top + tly)
        if (tlx > 0 || tly > 0) arcTo(left, top, left + 2 * tlx, top + 2 * tly, 180f, 90f, false)
        close()
    }

    open fun addPath(src: Path) {
        verbs.addAll(src.verbs)
    }

    open fun addPath(src: Path, dx: Float, dy: Float) {
        val p = Path(src)
        p.offset(dx, dy)
        addPath(p)
    }

    open fun addPath(src: Path, matrix: Matrix) {
        val p = Path(src)
        p.transform(matrix)
        addPath(p)
    }

    open fun offset(dx: Float, dy: Float) {
        val m = Matrix()
        m.setTranslate(dx, dy)
        transform(m)
    }

    open fun offset(dx: Float, dy: Float, dst: Path?) {
        if (dst == null) offset(dx, dy) else {
            dst.set(this); dst.offset(dx, dy)
        }
    }

    open fun transform(matrix: Matrix, dst: Path?) {
        if (dst != null) {
            dst.set(this)
            dst.transform(matrix)
        } else transform(matrix)
    }

    /** Apply a matrix to every point. Arcs are converted to cubic curves first so the result is exact. */
    open fun transform(matrix: Matrix) {
        val out = ArrayList<Verb>(verbs.size)
        val pts = FloatArray(6)
        fun map(n: Int) = matrix.mapPoints(pts, 0, pts, 0, n)
        for (v in expandArcs()) {
            when (v) {
                is Move -> { pts[0] = v.x; pts[1] = v.y; map(1); out.add(Move(pts[0], pts[1])) }
                is Line -> { pts[0] = v.x; pts[1] = v.y; map(1); out.add(Line(pts[0], pts[1])) }
                is Quad -> { pts[0] = v.x1; pts[1] = v.y1; pts[2] = v.x2; pts[3] = v.y2; map(2); out.add(Quad(pts[0], pts[1], pts[2], pts[3])) }
                is Cubic -> {
                    pts[0] = v.x1; pts[1] = v.y1; pts[2] = v.x2; pts[3] = v.y2; pts[4] = v.x3; pts[5] = v.y3
                    map(3); out.add(Cubic(pts[0], pts[1], pts[2], pts[3], pts[4], pts[5]))
                }
                is Close -> out.add(Close)
                is Arc -> {}
            }
        }
        verbs.clear()
        verbs.addAll(out)
        pts[0] = lastX; pts[1] = lastY; map(1); lastX = pts[0]; lastY = pts[1]
    }

    /** Arcs as cubic bezier segments (max 90 degrees each) */
    private fun expandArcs(): List<Verb> {
        if (verbs.none { it is Arc }) return verbs
        val out = ArrayList<Verb>()
        var hasCurrent = false
        for (v in verbs) {
            if (v !is Arc) {
                out.add(v)
                hasCurrent = v !is Close
                continue
            }
            val cx = (v.l + v.r) / 2.0
            val cy = (v.t + v.b) / 2.0
            val rx = (v.r - v.l) / 2.0
            val ry = (v.b - v.t) / 2.0
            val segments = kotlin.math.max(1, kotlin.math.ceil(kotlin.math.abs(v.sweep) / 90.0).toInt())
            val step = Math.toRadians(v.sweep.toDouble()) / segments
            var a = Math.toRadians(v.start.toDouble())
            val sx = (cx + rx * Math.cos(a)).toFloat()
            val sy = (cy + ry * Math.sin(a)).toFloat()
            if (v.forceMove || !hasCurrent) out.add(Move(sx, sy)) else out.add(Line(sx, sy))
            val k = 4.0 / 3.0 * Math.tan(step / 4)
            repeat(segments) {
                val a2 = a + step
                val x1 = cx + rx * (Math.cos(a) - k * Math.sin(a))
                val y1 = cy + ry * (Math.sin(a) + k * Math.cos(a))
                val x2 = cx + rx * (Math.cos(a2) + k * Math.sin(a2))
                val y2 = cy + ry * (Math.sin(a2) - k * Math.cos(a2))
                val x3 = cx + rx * Math.cos(a2)
                val y3 = cy + ry * Math.sin(a2)
                out.add(Cubic(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat()))
                a = a2
            }
            hasCurrent = true
        }
        return out
    }

    open fun computeBounds(bounds: RectF, exact: Boolean) {
        val p = toSkia()
        val b = p.bounds
        bounds.set(b.left, b.top, b.right, b.bottom)
        p.close()
    }

    open fun op(path: Path, op: Op): Boolean = op(this, path, op)

    open fun op(path1: Path, path2: Path, op: Op): Boolean {
        val a = path1.toSkia()
        val b = path2.toSkia()
        val result = org.jetbrains.skia.Path.makeCombining(
            a, b, when (op) {
                Op.DIFFERENCE -> org.jetbrains.skia.PathOp.DIFFERENCE
                Op.INTERSECT -> org.jetbrains.skia.PathOp.INTERSECT
                Op.UNION -> org.jetbrains.skia.PathOp.UNION
                Op.XOR -> org.jetbrains.skia.PathOp.XOR
                Op.REVERSE_DIFFERENCE -> org.jetbrains.skia.PathOp.REVERSE_DIFFERENCE
            }
        ) ?: return false
        reset()
        // Record the combined result as line/curve segments
        for (seg in result) {
            if (seg == null) continue
            val p0 = seg.p0
            val p1 = seg.p1
            val p2 = seg.p2
            val p3 = seg.p3
            when (seg.verb) {
                org.jetbrains.skia.PathVerb.MOVE -> if (p0 != null) moveTo(p0.x, p0.y)
                org.jetbrains.skia.PathVerb.LINE -> if (p1 != null) lineTo(p1.x, p1.y)
                org.jetbrains.skia.PathVerb.QUAD -> if (p1 != null && p2 != null) quadTo(p1.x, p1.y, p2.x, p2.y)
                org.jetbrains.skia.PathVerb.CUBIC -> if (p1 != null && p2 != null && p3 != null) cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                org.jetbrains.skia.PathVerb.CLOSE -> close()
                else -> {}
            }
        }
        return true
    }

    /** Build a Skia path (caller owns it) */
    fun toSkia(): org.jetbrains.skia.Path {
        val b = org.jetbrains.skia.PathBuilder(
            when (fillType) {
                FillType.WINDING -> PathFillMode.WINDING
                FillType.EVEN_ODD -> PathFillMode.EVEN_ODD
                FillType.INVERSE_WINDING -> PathFillMode.INVERSE_WINDING
                FillType.INVERSE_EVEN_ODD -> PathFillMode.INVERSE_EVEN_ODD
            }
        )
        for (v in expandArcs()) {
            when (v) {
                is Move -> b.moveTo(v.x, v.y)
                is Line -> b.lineTo(v.x, v.y)
                is Quad -> b.quadTo(v.x1, v.y1, v.x2, v.y2)
                is Cubic -> b.cubicTo(v.x1, v.y1, v.x2, v.y2, v.x3, v.y3)
                is Close -> b.closePath()
                is Arc -> {}
            }
        }
        val p = b.detach()
        b.close()
        return p
    }

    @Suppress("unused")
    private fun direction(dir: Direction?) = if (dir == Direction.CCW) PathDirection.COUNTER_CLOCKWISE else PathDirection.CLOCKWISE
}
