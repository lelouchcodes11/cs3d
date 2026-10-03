package androidx.palette.graphics

import android.graphics.Bitmap

class Palette {
    fun getDarkVibrantColor(defaultColor: Int): Int = defaultColor
    fun getVibrantColor(defaultColor: Int): Int = defaultColor
    fun getDominantColor(defaultColor: Int): Int = defaultColor
    fun getLightVibrantColor(defaultColor: Int): Int = defaultColor
    fun getDarkMutedColor(defaultColor: Int): Int = defaultColor
    fun getMutedColor(defaultColor: Int): Int = defaultColor
    fun getLightMutedColor(defaultColor: Int): Int = defaultColor

    class Builder(val bitmap: Bitmap) {
        fun generate(): Palette = Palette()
        fun generate(listener: (Palette?) -> Unit) {
            listener(Palette())
        }
    }

    companion object {
        @JvmStatic
        fun from(bitmap: Bitmap): Builder = Builder(bitmap)
    }
}
