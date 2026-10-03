package androidx.core.graphics

import android.graphics.Color
import androidx.annotation.ColorInt

@ColorInt
inline fun String.toColorInt(): Int = Color.parseColor(this)

inline val @receiver:ColorInt Int.alpha: Int get() = (this ushr 24) and 0xff
inline val @receiver:ColorInt Int.red: Int get() = (this ushr 16) and 0xff
inline val @receiver:ColorInt Int.green: Int get() = (this ushr 8) and 0xff
inline val @receiver:ColorInt Int.blue: Int get() = this and 0xff

