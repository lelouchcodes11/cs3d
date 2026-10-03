@file:JvmName("FiltersKt")

package android.graphics

import org.jetbrains.skia.BlendMode as SkBlendMode
import org.jetbrains.skia.ColorFilter as SkColorFilter
import org.jetbrains.skia.ColorMatrix as SkColorMatrix

open class ColorFilter {
    open fun toSkia(): SkColorFilter? = null
}

class PorterDuff {
    enum class Mode(@JvmField val nativeInt: Int) {
        CLEAR(0), SRC(1), DST(2), SRC_OVER(3), DST_OVER(4), SRC_IN(5), DST_IN(6), SRC_OUT(7), DST_OUT(8),
        SRC_ATOP(9), DST_ATOP(10), XOR(11), DARKEN(16), LIGHTEN(17), MULTIPLY(13), SCREEN(14), ADD(12), OVERLAY(15);

        fun toSkia(): SkBlendMode = when (this) {
            CLEAR -> SkBlendMode.CLEAR
            SRC -> SkBlendMode.SRC
            DST -> SkBlendMode.DST
            SRC_OVER -> SkBlendMode.SRC_OVER
            DST_OVER -> SkBlendMode.DST_OVER
            SRC_IN -> SkBlendMode.SRC_IN
            DST_IN -> SkBlendMode.DST_IN
            SRC_OUT -> SkBlendMode.SRC_OUT
            DST_OUT -> SkBlendMode.DST_OUT
            SRC_ATOP -> SkBlendMode.SRC_ATOP
            DST_ATOP -> SkBlendMode.DST_ATOP
            XOR -> SkBlendMode.XOR
            DARKEN -> SkBlendMode.DARKEN
            LIGHTEN -> SkBlendMode.LIGHTEN
            MULTIPLY -> SkBlendMode.MULTIPLY
            SCREEN -> SkBlendMode.SCREEN
            ADD -> SkBlendMode.PLUS
            OVERLAY -> SkBlendMode.OVERLAY
        }
    }
}

enum class BlendMode {
    CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, PLUS,
    MODULATE, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE,
    EXCLUSION, MULTIPLY, HUE, SATURATION, COLOR, LUMINOSITY;

    fun toSkia(): SkBlendMode = SkBlendMode.valueOf(name)
}

open class PorterDuffColorFilter(@JvmField val color: Int, @JvmField val mode: PorterDuff.Mode) : ColorFilter() {
    override fun toSkia(): SkColorFilter = SkColorFilter.makeBlend(color, mode.toSkia())
}

open class BlendModeColorFilter(@JvmField val color: Int, @JvmField val mode: BlendMode) : ColorFilter() {
    override fun toSkia(): SkColorFilter = SkColorFilter.makeBlend(color, mode.toSkia())
}

open class LightingColorFilter(@JvmField val mul: Int, @JvmField val add: Int) : ColorFilter() {
    override fun toSkia(): SkColorFilter = SkColorFilter.makeLighting(mul, add)
}

open class ColorMatrix {
    @JvmField
    val array = FloatArray(20).also { it[0] = 1f; it[6] = 1f; it[12] = 1f; it[18] = 1f }

    constructor()
    constructor(src: FloatArray) {
        src.copyInto(array)
    }

    fun getArray(): FloatArray = array
    fun set(src: FloatArray) = src.copyInto(array)
    fun reset() {
        array.fill(0f)
        array[0] = 1f; array[6] = 1f; array[12] = 1f; array[18] = 1f
    }

    fun setSaturation(sat: Float) {
        reset()
        val invSat = 1 - sat
        val r = 0.213f * invSat
        val g = 0.715f * invSat
        val b = 0.072f * invSat
        array[0] = r + sat; array[1] = g; array[2] = b
        array[5] = r; array[6] = g + sat; array[7] = b
        array[10] = r; array[11] = g; array[12] = b + sat
    }
}

open class ColorMatrixColorFilter(matrix: FloatArray) : ColorFilter() {
    private val values = matrix.copyOf()

    constructor(matrix: ColorMatrix) : this(matrix.array)

    override fun toSkia(): SkColorFilter {
        // Android uses 0..255 for the translation column while Skia uses 0..1
        val m = values.copyOf()
        m[4] /= 255f; m[9] /= 255f; m[14] /= 255f; m[19] /= 255f
        return SkColorFilter.makeMatrix(SkColorMatrix(m))
    }
}

open class Xfermode
open class PorterDuffXfermode(@JvmField val mode: PorterDuff.Mode) : Xfermode()

open class Shader {
    private var localMatrix: Matrix? = null
    open fun toSkia(): org.jetbrains.skia.Shader? = null
    open fun setLocalMatrix(localM: Matrix?) {
        localMatrix = localM
    }

    open fun getLocalMatrix(localM: Matrix): Boolean {
        localMatrix?.let { localM.set(it); return true }
        return false
    }

    enum class TileMode {
        CLAMP, REPEAT, MIRROR, DECAL;

        fun toSkia(): org.jetbrains.skia.FilterTileMode = org.jetbrains.skia.FilterTileMode.valueOf(name)
    }
}

open class LinearGradient : Shader {
    private val x0: Float
    private val y0: Float
    private val x1: Float
    private val y1: Float
    private val colors: IntArray
    private val positions: FloatArray?
    private val tile: TileMode

    constructor(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, positions: FloatArray?, tile: TileMode) {
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1
        this.colors = colors
        this.positions = positions
        this.tile = tile
    }

    constructor(x0: Float, y0: Float, x1: Float, y1: Float, color0: Int, color1: Int, tile: TileMode) :
            this(x0, y0, x1, y1, intArrayOf(color0, color1), null, tile)

    override fun toSkia(): org.jetbrains.skia.Shader = org.jetbrains.skia.Shader.makeLinearGradient(
        x0, y0, x1, y1, skGradient(colors, positions, tile), null
    )
}

open class RadialGradient : Shader {
    private val cx: Float
    private val cy: Float
    private val radius: Float
    private val colors: IntArray
    private val positions: FloatArray?
    private val tile: TileMode

    constructor(centerX: Float, centerY: Float, radius: Float, colors: IntArray, stops: FloatArray?, tileMode: TileMode) {
        cx = centerX; cy = centerY; this.radius = radius
        this.colors = colors
        positions = stops
        tile = tileMode
    }

    constructor(centerX: Float, centerY: Float, radius: Float, centerColor: Int, edgeColor: Int, tileMode: TileMode) :
            this(centerX, centerY, radius, intArrayOf(centerColor, edgeColor), null, tileMode)

    override fun toSkia(): org.jetbrains.skia.Shader = org.jetbrains.skia.Shader.makeRadialGradient(
        cx, cy, radius, skGradient(colors, positions, tile), null
    )
}

open class SweepGradient : Shader {
    private val cx: Float
    private val cy: Float
    private val colors: IntArray
    private val positions: FloatArray?

    constructor(cx: Float, cy: Float, colors: IntArray, positions: FloatArray?) {
        this.cx = cx; this.cy = cy
        this.colors = colors
        this.positions = positions
    }

    constructor(cx: Float, cy: Float, color0: Int, color1: Int) : this(cx, cy, intArrayOf(color0, color1), null)

    override fun toSkia(): org.jetbrains.skia.Shader = org.jetbrains.skia.Shader.makeSweepGradient(cx, cy, skGradient(colors, positions, TileMode.CLAMP), null)
}

open class BitmapShader(@JvmField val bitmap: Bitmap, tileX: TileMode, tileY: TileMode) : Shader() {
    private val tx = tileX
    private val ty = tileY
    override fun toSkia(): org.jetbrains.skia.Shader = bitmap.skia.makeShader(tx.toSkia(), ty.toSkia())
}

internal fun skGradient(colors: IntArray, positions: FloatArray?, tile: Shader.TileMode): org.jetbrains.skia.Gradient =
    org.jetbrains.skia.Gradient(
        org.jetbrains.skia.Gradient.Colors(
            colors.map { org.jetbrains.skia.Color4f(it) }.toTypedArray(),
            positions,
            tile.toSkia(),
            null
        ),
        org.jetbrains.skia.Gradient.Interpolation()
    )
