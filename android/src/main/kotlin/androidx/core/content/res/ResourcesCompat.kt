package androidx.core.content.res

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler

class ResourcesCompat private constructor() {
    abstract class FontCallback {
        abstract fun onFontRetrieved(typeface: Typeface)
        abstract fun onFontRetrievalFailed(reason: Int)

        fun callbackSuccessAsync(typeface: Typeface, handler: Handler?) {
            (handler ?: Handler(android.os.Looper.getMainLooper())).post { onFontRetrieved(typeface) }
        }

        fun callbackFailAsync(reason: Int, handler: Handler?) {
            (handler ?: Handler(android.os.Looper.getMainLooper())).post { onFontRetrievalFailed(reason) }
        }
    }

    companion object {
        const val ID_NULL = 0

        @JvmStatic
        fun getDrawable(res: Resources, id: Int, theme: Resources.Theme?): Drawable? = res.getDrawable(id, theme)

        @JvmStatic
        fun getDrawableForDensity(res: Resources, id: Int, density: Int, theme: Resources.Theme?): Drawable? =
            res.getDrawable(id, theme)

        @JvmStatic
        fun getColor(res: Resources, id: Int, theme: Resources.Theme?): Int = res.getColor(id, theme)

        @JvmStatic
        fun getColorStateList(res: Resources, id: Int, theme: Resources.Theme?): ColorStateList? =
            res.getColorStateList(id, theme)

        @JvmStatic
        fun getFloat(res: Resources, id: Int): Float = res.getFloat(id)

        @JvmStatic
        fun getFont(context: Context, id: Int): Typeface? = context.resources.getFont(id)

        @JvmStatic
        fun getFont(context: Context, id: Int, callback: FontCallback, handler: Handler?) {
            val tf = try {
                context.resources.getFont(id)
            } catch (_: Throwable) {
                null
            }
            if (tf != null) callback.callbackSuccessAsync(tf, handler) else callback.callbackFailAsync(-3, handler)
        }

        @JvmStatic
        fun getCachedFont(context: Context, id: Int): Typeface? = try {
            context.resources.getFont(id)
        } catch (_: Throwable) {
            null
        }
    }
}
