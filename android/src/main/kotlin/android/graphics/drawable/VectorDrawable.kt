package android.graphics.drawable

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.util.TypedValue
import com.lagradost.desktop.runtime.res.ResourceSupport
import org.xmlpull.v1.XmlPullParser

/** Vector drawable parsed from <vector> XML and drawn with Skia */
open class VectorDrawable : Drawable() {
    internal class VPath(
        val name: String?,
        var pathData: String,
        var path: Path,
        val fillColor: ColorStateList?,
        val strokeColor: ColorStateList?,
        val strokeWidth: Float,
        var fillAlpha: Float,
        var strokeAlpha: Float,
        val evenOdd: Boolean,
        val cap: Paint.Cap,
        val join: Paint.Join,
        val clip: Boolean,
    )

    internal class Group(val name: String? = null, val children: MutableList<Any> = ArrayList()) {
        val matrix = Matrix()
        var pivotX = 0f
        var pivotY = 0f
        var scaleX = 1f
        var scaleY = 1f
        var rotation = 0f
        var translateX = 0f
        var translateY = 0f

        fun rebuild() {
            matrix.reset()
            matrix.postTranslate(-pivotX, -pivotY)
            matrix.postScale(scaleX, scaleY)
            matrix.postRotate(rotation, 0f, 0f)
            matrix.postTranslate(translateX + pivotX, translateY + pivotY)
        }
    }

    /** AnimatedVectorDrawable targets: named groups and paths */
    internal fun findGroup(name: String): Group? = findIn(root) { it is Group && it.name == name } as? Group
    internal fun findPath(name: String): VPath? = findIn(root) { it is VPath && it.name == name } as? VPath
    internal var rootAlpha: Float
        get() = baseAlpha
        set(v) {
            baseAlpha = v
        }

    private fun findIn(g: Group, match: (Any) -> Boolean): Any? {
        if (match(g)) return g
        for (c in g.children) {
            if (match(c)) return c
            if (c is Group) findIn(c, match)?.let { return it }
        }
        return null
    }

    private var root = Group()
    private var viewportWidth = 24f
    private var viewportHeight = 24f
    private var intrinsicW = -1
    private var intrinsicH = -1
    private var baseAlpha = 1f
    private var alpha = 255
    private var colorFilter: ColorFilter? = null
    private var autoMirror = false

    companion object {
        @JvmStatic
        fun inflate(res: Resources, parser: XmlPullParser, theme: Resources.Theme?): VectorDrawable {
            val d = VectorDrawable()
            val set = android.util.Xml.asAttributeSet(parser)
            fun dim(s: AttributeSet, n: String) = ResourceSupport.attr(res, s, n)?.let { ResourceSupport.dimensionOf(res, it) }
            fun flt(s: AttributeSet, n: String): Float? = ResourceSupport.attr(res, s, n)?.let {
                when (it.type) {
                    TypedValue.TYPE_FLOAT -> java.lang.Float.intBitsToFloat(it.data)
                    TypedValue.TYPE_INT_DEC -> it.data.toFloat()
                    TypedValue.TYPE_STRING -> it.string?.toString()?.toFloatOrNull()
                    TypedValue.TYPE_DIMENSION -> TypedValue.complexToFloat(it.data)
                    else -> null
                }
            }

            fun str(s: AttributeSet, n: String): String? = ResourceSupport.attr(res, s, n)?.let {
                if (it.type == TypedValue.TYPE_REFERENCE) try {
                    res.getString(it.data)
                } catch (e: Exception) {
                    null
                } else it.string?.toString()
            }

            fun csl(s: AttributeSet, n: String): ColorStateList? = ResourceSupport.attr(res, s, n)?.let { ResourceSupport.colorStateListOf(res, it, theme) }

            d.intrinsicW = dim(set, "width")?.toInt() ?: -1
            d.intrinsicH = dim(set, "height")?.toInt() ?: -1
            d.viewportWidth = flt(set, "viewportWidth") ?: d.intrinsicW.toFloat()
            d.viewportHeight = flt(set, "viewportHeight") ?: d.intrinsicH.toFloat()
            d.baseAlpha = flt(set, "alpha") ?: 1f
            d.autoMirror = str(set, "autoMirrored") == "true"
            csl(set, "tint")?.let { d.setTintList(it) }

            val stack = ArrayDeque<Group>()
            stack.addLast(d.root)
            val depth = parser.depth
            while (true) {
                val e = parser.next()
                if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
                if (e == XmlPullParser.END_TAG && parser.name == "group") {
                    if (stack.size > 1) stack.removeLast()
                    continue
                }
                if (e != XmlPullParser.START_TAG) continue
                val s = android.util.Xml.asAttributeSet(parser)
                when (parser.name) {
                    "group" -> {
                        val g = Group(str(s, "name"))
                        g.pivotX = flt(s, "pivotX") ?: 0f
                        g.pivotY = flt(s, "pivotY") ?: 0f
                        g.scaleX = flt(s, "scaleX") ?: 1f
                        g.scaleY = flt(s, "scaleY") ?: 1f
                        g.rotation = flt(s, "rotation") ?: 0f
                        g.translateX = flt(s, "translateX") ?: 0f
                        g.translateY = flt(s, "translateY") ?: 0f
                        g.rebuild()
                        stack.last().children.add(g)
                        stack.addLast(g)
                    }
                    "path", "clip-path" -> {
                        val data = str(s, "pathData") ?: continue
                        val path = try {
                            PathParser.createPathFromPathData(data)
                        } catch (t: Throwable) {
                            continue
                        }
                        val clip = parser.name == "clip-path"
                        stack.last().children.add(
                            VPath(
                                str(s, "name"), data, path, if (clip) null else csl(s, "fillColor"), if (clip) null else csl(s, "strokeColor"),
                                dim(s, "strokeWidth") ?: flt(s, "strokeWidth") ?: 0f, flt(s, "fillAlpha") ?: 1f, flt(s, "strokeAlpha") ?: 1f,
                                (str(s, "fillType") ?: ResourceSupport.attr(res, s, "fillType")?.data?.toString()) in setOf("evenOdd", "1"),
                                when (str(s, "strokeLineCap")) {
                                    "round" -> Paint.Cap.ROUND
                                    "square" -> Paint.Cap.SQUARE
                                    else -> Paint.Cap.BUTT
                                },
                                when (str(s, "strokeLineJoin")) {
                                    "round" -> Paint.Join.ROUND
                                    "bevel" -> Paint.Join.BEVEL
                                    else -> Paint.Join.MITER
                                },
                                clip
                            )
                        )
                    }
                }
            }
            return d
        }
    }

    override fun getIntrinsicWidth(): Int = intrinsicW
    override fun getIntrinsicHeight(): Int = intrinsicH
    override fun getAlpha(): Int = alpha
    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getColorFilter(): ColorFilter? = colorFilter
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun isStateful(): Boolean = true
    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun setAutoMirrored(mirrored: Boolean) {
        autoMirror = mirrored
    }

    override fun isAutoMirrored(): Boolean = autoMirror

    override fun draw(canvas: Canvas) {
        val b = getBounds()
        if (b.isEmpty || viewportWidth <= 0 || viewportHeight <= 0) return
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / viewportWidth, b.height() / viewportHeight)
        drawGroup(canvas, root, colorFilter ?: tintFilter())
        canvas.restore()
    }

    private fun drawGroup(canvas: Canvas, g: Group, filter: ColorFilter?) {
        canvas.save()
        canvas.concat(g.matrix)
        for (c in g.children) {
            when (c) {
                is Group -> drawGroup(canvas, c, filter)
                is VPath -> {
                    if (c.clip) {
                        canvas.clipPath(c.path)
                        continue
                    }
                    val state = getState()
                    c.fillColor?.let { fc ->
                        val p = Paint(Paint.ANTI_ALIAS_FLAG)
                        p.setStyle(Paint.Style.FILL)
                        p.setColor(fc.getColorForState(state, fc.defaultColor))
                        p.setAlpha((p.getAlpha() * c.fillAlpha * baseAlpha * alpha / 255f).toInt().coerceIn(0, 255))
                        p.setColorFilter(filter)
                        val path = if (c.evenOdd) Path(c.path).also { it.setFillType(Path.FillType.EVEN_ODD) } else c.path
                        canvas.drawPath(path, p)
                    }
                    c.strokeColor?.let { sc ->
                        if (c.strokeWidth <= 0f) return@let
                        val p = Paint(Paint.ANTI_ALIAS_FLAG)
                        p.setStyle(Paint.Style.STROKE)
                        p.setStrokeWidth(c.strokeWidth)
                        p.setStrokeCap(c.cap)
                        p.setStrokeJoin(c.join)
                        p.setColor(sc.getColorForState(state, sc.defaultColor))
                        p.setAlpha((p.getAlpha() * c.strokeAlpha * baseAlpha * alpha / 255f).toInt().coerceIn(0, 255))
                        p.setColorFilter(filter)
                        canvas.drawPath(c.path, p)
                    }
                }
            }
        }
        canvas.restore()
    }
}

/** SVG path data parser (subset of android.util.PathParser) */
object PathParser {
    /** PathDataEvaluator: morphs between two path strings with the same command structure (null if they differ) */
    @JvmStatic
    fun interpolatePathData(from: String, to: String, fraction: Float): String? {
        val a = tokenize(from)
        val b = tokenize(to)
        if (a.size != b.size) return null
        val sb = StringBuilder()
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            when {
                x is Char && y is Char -> {
                    if (x != y) return null
                    sb.append(x).append(' ')
                }
                x is Float && y is Float -> sb.append(x + (y - x) * fraction).append(' ')
                else -> return null
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun createPathFromPathData(pathData: String): Path {
        val path = Path()
        var cx = 0f
        var cy = 0f
        var sx = 0f
        var sy = 0f
        var lastCtrlX = 0f
        var lastCtrlY = 0f
        var prevCmd = ' '
        val tokens = tokenize(pathData)
        var i = 0
        var cmd = ' '
        fun num(): Float = (tokens[i++] as Float)
        fun hasNum() = i < tokens.size && tokens[i] is Float
        while (i < tokens.size) {
            val t = tokens[i]
            if (t is Char) {
                cmd = t
                i++
            } else if (cmd == ' ') {
                i++
                continue
            }
            when (cmd) {
                'M', 'm' -> {
                    var first = true
                    do {
                        val x = num()
                        val y = num()
                        if (cmd == 'm') {
                            cx += x; cy += y
                        } else {
                            cx = x; cy = y
                        }
                        if (first) {
                            path.moveTo(cx, cy); sx = cx; sy = cy; first = false
                        } else path.lineTo(cx, cy)
                    } while (hasNum())
                }
                'L', 'l' -> do {
                    val x = num()
                    val y = num()
                    if (cmd == 'l') {
                        cx += x; cy += y
                    } else {
                        cx = x; cy = y
                    }
                    path.lineTo(cx, cy)
                } while (hasNum())
                'H', 'h' -> do {
                    val x = num()
                    cx = if (cmd == 'h') cx + x else x
                    path.lineTo(cx, cy)
                } while (hasNum())
                'V', 'v' -> do {
                    val y = num()
                    cy = if (cmd == 'v') cy + y else y
                    path.lineTo(cx, cy)
                } while (hasNum())
                'C', 'c' -> do {
                    var x1 = num(); var y1 = num(); var x2 = num(); var y2 = num(); var x = num(); var y = num()
                    if (cmd == 'c') {
                        x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy
                    }
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y
                    prevCmd = 'C'
                } while (hasNum())
                'S', 's' -> do {
                    var x2 = num(); var y2 = num(); var x = num(); var y = num()
                    if (cmd == 's') {
                        x2 += cx; y2 += cy; x += cx; y += cy
                    }
                    val x1 = if (prevCmd == 'C') 2 * cx - lastCtrlX else cx
                    val y1 = if (prevCmd == 'C') 2 * cy - lastCtrlY else cy
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y
                    prevCmd = 'C'
                } while (hasNum())
                'Q', 'q' -> do {
                    var x1 = num(); var y1 = num(); var x = num(); var y = num()
                    if (cmd == 'q') {
                        x1 += cx; y1 += cy; x += cx; y += cy
                    }
                    path.quadTo(x1, y1, x, y)
                    lastCtrlX = x1; lastCtrlY = y1; cx = x; cy = y
                    prevCmd = 'Q'
                } while (hasNum())
                'T', 't' -> do {
                    var x = num(); var y = num()
                    if (cmd == 't') {
                        x += cx; y += cy
                    }
                    val x1 = if (prevCmd == 'Q') 2 * cx - lastCtrlX else cx
                    val y1 = if (prevCmd == 'Q') 2 * cy - lastCtrlY else cy
                    path.quadTo(x1, y1, x, y)
                    lastCtrlX = x1; lastCtrlY = y1; cx = x; cy = y
                    prevCmd = 'Q'
                } while (hasNum())
                'A', 'a' -> do {
                    val rx = num(); val ry = num(); val rot = num(); val large = num() != 0f; val sweep = num() != 0f
                    var x = num(); var y = num()
                    if (cmd == 'a') {
                        x += cx; y += cy
                    }
                    arcTo(path, cx, cy, x, y, rx, ry, rot, large, sweep)
                    cx = x; cy = y
                } while (hasNum())
                'Z', 'z' -> {
                    path.close()
                    cx = sx; cy = sy
                }
                else -> i++
            }
            if (cmd != 'C' && cmd != 'c' && cmd != 'S' && cmd != 's' && cmd != 'Q' && cmd != 'q' && cmd != 'T' && cmd != 't') prevCmd = cmd
        }
        return path
    }

    private fun tokenize(s: String): List<Any> {
        val out = ArrayList<Any>()
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c.isLetter() && c != 'e' && c != 'E' -> {
                    out.add(c); i++
                }
                c == '-' || c == '+' || c == '.' || c.isDigit() -> {
                    val start = i
                    i++
                    var seenDot = c == '.'
                    var seenExp = false
                    while (i < n) {
                        val d = s[i]
                        if (d.isDigit()) i++
                        else if (d == '.' && !seenDot && !seenExp) {
                            seenDot = true; i++
                        } else if ((d == 'e' || d == 'E') && !seenExp) {
                            seenExp = true; i++
                            if (i < n && (s[i] == '-' || s[i] == '+')) i++
                        } else break
                    }
                    out.add(s.substring(start, i).toFloat())
                }
                else -> i++
            }
        }
        return out
    }

    private fun arcTo(path: Path, x0: Float, y0: Float, x1: Float, y1: Float, rx0: Float, ry0: Float, angle: Float, large: Boolean, sweep: Boolean) {
        if (rx0 == 0f || ry0 == 0f) {
            path.lineTo(x1, y1)
            return
        }
        val phi = Math.toRadians(angle.toDouble())
        val cosPhi = Math.cos(phi)
        val sinPhi = Math.sin(phi)
        val dx = (x0 - x1) / 2.0
        val dy = (y0 - y1) / 2.0
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        var rx = Math.abs(rx0.toDouble())
        var ry = Math.abs(ry0.toDouble())
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) {
            rx *= Math.sqrt(lambda); ry *= Math.sqrt(lambda)
        }
        val sign = if (large == sweep) -1.0 else 1.0
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = sign * Math.sqrt(Math.max(0.0, num / den))
        val cxp = coef * (rx * y1p / ry)
        val cyp = coef * -(ry * x1p / rx)
        val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2.0
        fun ang(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
            return a
        }
        val theta1 = ang(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dtheta = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && dtheta > 0) dtheta -= 2 * Math.PI else if (sweep && dtheta < 0) dtheta += 2 * Math.PI
        val segments = Math.ceil(Math.abs(dtheta) / (Math.PI / 2)).toInt().coerceAtLeast(1)
        val delta = dtheta / segments
        val t = 4.0 / 3.0 * Math.tan(delta / 4)
        var th = theta1
        repeat(segments) {
            val cos1 = Math.cos(th); val sin1 = Math.sin(th)
            val th2 = th + delta
            val cos2 = Math.cos(th2); val sin2 = Math.sin(th2)
            val e1x = cos1 - t * sin1; val e1y = sin1 + t * cos1
            val e2x = cos2 + t * sin2; val e2y = sin2 - t * cos2
            fun mapX(px: Double, py: Double) = cosPhi * rx * px - sinPhi * ry * py + cx
            fun mapY(px: Double, py: Double) = sinPhi * rx * px + cosPhi * ry * py + cy
            path.cubicTo(
                mapX(e1x, e1y).toFloat(), mapY(e1x, e1y).toFloat(),
                mapX(e2x, e2y).toFloat(), mapY(e2x, e2y).toFloat(),
                mapX(cos2, sin2).toFloat(), mapY(cos2, sin2).toFloat()
            )
            th = th2
        }
    }
}
