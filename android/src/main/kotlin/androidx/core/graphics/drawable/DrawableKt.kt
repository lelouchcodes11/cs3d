package androidx.core.graphics.drawable

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

fun Drawable.toBitmap(
    width: Int = getIntrinsicWidth(),
    height: Int = getIntrinsicHeight(),
    config: Bitmap.Config? = null,
): Bitmap {
    if (this is BitmapDrawable) {
        val bitmap = this.bitmap
        if (bitmap != null && (config == null || bitmap.getConfig() == config)) {
            if (width == bitmap.getWidth() && height == bitmap.getHeight()) return bitmap
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
    }
    return Drawable.toBitmap(this, width, height)
}

fun Drawable.toBitmapOrNull(width: Int = getIntrinsicWidth(), height: Int = getIntrinsicHeight(), config: Bitmap.Config? = null): Bitmap? =
    if (this is BitmapDrawable && this.bitmap == null) null else toBitmap(width, height, config)

fun Bitmap.toDrawable(resources: Resources): BitmapDrawable = BitmapDrawable(resources, this)
