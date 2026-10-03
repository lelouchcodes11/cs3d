package android.graphics

import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.SurfaceProps

/**
 * android.graphics.Canvas drawing into a Skia canvas: either a Bitmap's pixels or, on desktop,
 * directly into a Compose draw scope (see [Canvas.wrap]).
 */
open class Canvas {
    companion object {
        const val ALL_SAVE_FLAG = 0x1F
        const val CLIP_SAVE_FLAG = 0x02
        const val MATRIX_SAVE_FLAG = 0x01

        /** Desktop: wrap an existing Skia canvas of the given size */
        @JvmStatic
        fun wrap(canvas: org.jetbrains.skia.Canvas, width: Int, height: Int): Canvas = Canvas().also {
            it.native = canvas
            it.mWidth = width
            it.mHeight = height
        }
    }

    enum class EdgeType { BW, AA }
    enum class VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }

    /** The underlying skia canvas, null when the canvas has no backing bitmap */
    var native: org.jetbrains.skia.Canvas? = null
        private set
    private var bitmap: Bitmap? = null
    private var mWidth = 0
    private var mHeight = 0
    open val width: Int get() = mWidth
    open val height: Int get() = mHeight
    private var density = 0

    constructor()

    constructor(bitmap: Bitmap) {
        setBitmap(bitmap)
    }

    open fun setBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
        if (bitmap == null) {
            native = null
            mWidth = 0
            mHeight = 0
        } else {
            native = org.jetbrains.skia.Canvas(bitmap.skia, SurfaceProps())
            mWidth = bitmap.getWidth()
            mHeight = bitmap.getHeight()
        }
    }

    private fun afterDraw() {
        bitmap?.skia?.notifyPixelsChanged()
    }

    private inline fun draw(block: (org.jetbrains.skia.Canvas) -> Unit) {
        val c = native ?: return
        block(c)
        afterDraw()
    }

    open fun isHardwareAccelerated(): Boolean = false
    open fun isOpaque(): Boolean = false
    open fun getDensity(): Int = density
    open fun setDensity(density: Int) {
        this.density = density
    }

    open fun getMaximumBitmapWidth(): Int = 32766
    open fun getMaximumBitmapHeight(): Int = 32766

    open fun save(): Int = native?.save() ?: 0
    open fun save(saveFlags: Int): Int = save()

    open fun saveLayer(bounds: RectF?, paint: Paint?): Int {
        val c = native ?: return 0
        return if (bounds != null) c.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, paint?.skia)
        else c.saveLayer(null, paint?.skia)
    }

    open fun saveLayer(bounds: RectF?, paint: Paint?, saveFlags: Int): Int = saveLayer(bounds, paint)
    open fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?): Int = saveLayer(RectF(left, top, right, bottom), paint)
    open fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?, saveFlags: Int): Int = saveLayer(left, top, right, bottom, paint)

    open fun saveLayerAlpha(bounds: RectF?, alpha: Int): Int = saveLayer(bounds, Paint().apply { setAlpha(alpha) })
    open fun saveLayerAlpha(bounds: RectF?, alpha: Int, saveFlags: Int): Int = saveLayerAlpha(bounds, alpha)
    open fun saveLayerAlpha(left: Float, top: Float, right: Float, bottom: Float, alpha: Int): Int = saveLayerAlpha(RectF(left, top, right, bottom), alpha)

    open fun restore() {
        native?.restore()
    }

    open fun getSaveCount(): Int = native?.saveCount ?: 1
    open fun restoreToCount(saveCount: Int) {
        native?.restoreToCount(saveCount)
    }

    open fun translate(dx: Float, dy: Float) {
        native?.translate(dx, dy)
    }

    open fun scale(sx: Float, sy: Float) {
        native?.scale(sx, sy)
    }

    fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        translate(px, py)
        scale(sx, sy)
        translate(-px, -py)
    }

    open fun rotate(degrees: Float) {
        native?.rotate(degrees)
    }

    fun rotate(degrees: Float, px: Float, py: Float) {
        translate(px, py)
        rotate(degrees)
        translate(-px, -py)
    }

    open fun skew(sx: Float, sy: Float) {
        native?.skew(sx, sy)
    }

    open fun concat(matrix: Matrix?) {
        if (matrix != null) native?.concat(skMatrix(matrix))
    }

    open fun setMatrix(matrix: Matrix?) {
        val c = native ?: return
        c.resetMatrix()
        if (matrix != null) c.concat(skMatrix(matrix))
    }

    open fun getMatrix(ctm: Matrix) {
        val c = native ?: return ctm.reset()
        val m44 = c.localToDevice.mat
        ctm.setValues(floatArrayOf(m44[0], m44[1], m44[3], m44[4], m44[5], m44[7], m44[12], m44[13], m44[15]))
    }

    open fun getMatrix(): Matrix = Matrix().also { getMatrix(it) }

    open fun clipRect(rect: RectF): Boolean = clipRect(rect.left, rect.top, rect.right, rect.bottom)
    open fun clipRect(rect: Rect): Boolean = clipRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
    open fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean = clipRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    open fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        native?.clipRect(org.jetbrains.skia.Rect.makeLTRB(left, top, right, bottom), ClipMode.INTERSECT, true)
        return true
    }

    open fun clipOutRect(rect: RectF): Boolean = clipOutRect(rect.left, rect.top, rect.right, rect.bottom)
    open fun clipOutRect(rect: Rect): Boolean = clipOutRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
    open fun clipOutRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        native?.clipRect(org.jetbrains.skia.Rect.makeLTRB(left, top, right, bottom), ClipMode.DIFFERENCE, true)
        return true
    }

    open fun clipPath(path: Path): Boolean {
        val p = path.toSkia()
        native?.clipPath(p, ClipMode.INTERSECT, true)
        return true
    }

    open fun clipOutPath(path: Path): Boolean {
        val p = path.toSkia()
        native?.clipPath(p, ClipMode.DIFFERENCE, true)
        return true
    }

    open fun getClipBounds(bounds: Rect): Boolean {
        bounds.set(0, 0, width, height)
        return width > 0 && height > 0
    }

    fun getClipBounds(): Rect = Rect().also { getClipBounds(it) }

    open fun quickReject(rect: RectF): Boolean = false
    open fun quickReject(left: Float, top: Float, right: Float, bottom: Float): Boolean = false

    open fun drawColor(color: Int) = draw { it.drawPaint(org.jetbrains.skia.Paint().apply { this.color = color }) }
    open fun drawColor(color: Long) = drawColor(color.toInt())
    open fun drawColor(color: Int, mode: PorterDuff.Mode) = draw {
        it.drawPaint(org.jetbrains.skia.Paint().apply { this.color = color; blendMode = mode.toSkia() })
    }

    open fun drawColor(color: Int, mode: BlendMode) = draw {
        it.drawPaint(org.jetbrains.skia.Paint().apply { this.color = color; blendMode = mode.toSkia() })
    }

    open fun drawARGB(a: Int, r: Int, g: Int, b: Int) = drawColor(Color.argb(a, r, g, b))
    open fun drawRGB(r: Int, g: Int, b: Int) = drawColor(Color.rgb(r, g, b))
    open fun drawPaint(paint: Paint) = draw { it.drawPaint(paint.skia) }

    open fun drawPoint(x: Float, y: Float, paint: Paint) = draw { it.drawPoint(x, y, paint.skia) }
    open fun drawPoints(pts: FloatArray, paint: Paint) = draw { it.drawPoints(pts, paint.skia) }
    open fun drawPoints(pts: FloatArray, offset: Int, count: Int, paint: Paint) = drawPoints(pts.copyOfRange(offset, offset + count), paint)

    open fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) =
        draw { it.drawLine(startX, startY, stopX, stopY, paint.skia) }

    open fun drawLines(pts: FloatArray, paint: Paint) = draw { it.drawLines(pts, paint.skia) }
    open fun drawLines(pts: FloatArray, offset: Int, count: Int, paint: Paint) = drawLines(pts.copyOfRange(offset, offset + count), paint)

    open fun drawRect(rect: RectF, paint: Paint) = drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
    open fun drawRect(r: Rect, paint: Paint) = drawRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), paint)
    open fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        draw { it.drawRect(left, top, right, bottom, paint.skia) }

    open fun drawOval(oval: RectF, paint: Paint) = drawOval(oval.left, oval.top, oval.right, oval.bottom, paint)
    open fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        draw { it.drawOval(left, top, right, bottom, paint.skia) }

    open fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = draw { it.drawCircle(cx, cy, radius, paint.skia) }

    open fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) =
        drawArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, useCenter, paint)

    open fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) =
        draw { it.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint.skia) }

    open fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) = drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint)
    open fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) =
        draw { it.drawRRect(left, top, right, bottom, floatArrayOf(rx, ry), paint.skia) }

    open fun drawDoubleRoundRect(outer: RectF, outerRx: Float, outerRy: Float, inner: RectF, innerRx: Float, innerRy: Float, paint: Paint) = draw {
        it.drawDRRect(
            org.jetbrains.skia.RRect.makeLTRB(outer.left, outer.top, outer.right, outer.bottom, outerRx, outerRy),
            org.jetbrains.skia.RRect.makeLTRB(inner.left, inner.top, inner.right, inner.bottom, innerRx, innerRy), paint.skia
        )
    }

    open fun drawPath(path: Path, paint: Paint) = draw {
        val p = path.toSkia()
        it.drawPath(p, paint.skia)
        p.close()
    }

    private fun sampling(paint: Paint?): SamplingMode =
        if (paint == null || paint.isFilterBitmap()) FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE) else FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)

    open fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) = draw {
        it.drawImage(bitmap.toImage(), left, top, paint?.skia)
    }

    open fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) = draw {
        val s = src?.let { r -> org.jetbrains.skia.Rect.makeLTRB(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()) }
            ?: org.jetbrains.skia.Rect.makeWH(bitmap.getWidth().toFloat(), bitmap.getHeight().toFloat())
        it.drawImageRect(bitmap.toImage(), s, org.jetbrains.skia.Rect.makeLTRB(dst.left, dst.top, dst.right, dst.bottom), sampling(paint), paint?.skia, true)
    }

    open fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) = drawBitmap(bitmap, src, RectF(dst), paint)

    open fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        save()
        concat(matrix)
        drawBitmap(bitmap, 0f, 0f, paint)
        restore()
    }

    open fun drawBitmap(colors: IntArray, offset: Int, stride: Int, x: Float, y: Float, width: Int, height: Int, hasAlpha: Boolean, paint: Paint?) {
        drawBitmap(Bitmap.createBitmap(colors, offset, stride, width, height, Bitmap.Config.ARGB_8888), x, y, paint)
    }

    private fun alignX(text: String, x: Float, paint: Paint): Float = when (paint.getTextAlign()) {
        Paint.Align.LEFT -> x
        Paint.Align.CENTER -> x - paint.measureText(text) / 2
        Paint.Align.RIGHT -> x - paint.measureText(text)
    }

    open fun drawText(text: String, x: Float, y: Float, paint: Paint) = draw {
        it.drawString(text, alignX(text, x, paint), y, paint.font(), paint.skia)
    }

    open fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) = drawText(text.substring(start, end), x, y, paint)
    open fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) = drawText(text.subSequence(start, end).toString(), x, y, paint)
    open fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) = drawText(String(text, index, count), x, y, paint)

    open fun drawTextOnPath(text: String, path: Path, hOffset: Float, vOffset: Float, paint: Paint) = drawText(text, hOffset, vOffset, paint)

    open fun drawPicture(picture: Any?) {}
}

internal fun skMatrix(m: Matrix): Matrix33 {
    val v = m.values()
    return Matrix33(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8])
}
