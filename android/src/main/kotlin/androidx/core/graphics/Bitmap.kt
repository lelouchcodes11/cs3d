package androidx.core.graphics

import android.graphics.Bitmap

inline fun createBitmap(
    width: Int,
    height: Int,
    config: Bitmap.Config = Bitmap.Config.ARGB_8888
): Bitmap = Bitmap.createBitmap(if (width > 0) width else 1, if (height > 0) height else 1, config)

inline fun Bitmap.scale(
    width: Int,
    height: Int,
    filter: Boolean = true
): Bitmap = Bitmap.createScaledBitmap(this, width, height, filter)

