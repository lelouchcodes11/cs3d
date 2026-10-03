package android.graphics.drawable

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

class Icon private constructor(private val type: Int) {
    private var resId = 0
    private var bitmap: Bitmap? = null
    private var uri: Uri? = null
    private var data: ByteArray? = null

    companion object {
        const val TYPE_BITMAP = 1
        const val TYPE_RESOURCE = 2
        const val TYPE_DATA = 3
        const val TYPE_URI = 4

        @JvmStatic
        fun createWithResource(context: Context?, resId: Int): Icon = Icon(TYPE_RESOURCE).also { it.resId = resId }

        @JvmStatic
        fun createWithResource(resPackage: String?, resId: Int): Icon = Icon(TYPE_RESOURCE).also { it.resId = resId }

        @JvmStatic
        fun createWithBitmap(bits: Bitmap?): Icon = Icon(TYPE_BITMAP).also { it.bitmap = bits }

        @JvmStatic
        fun createWithData(data: ByteArray, offset: Int, length: Int): Icon = Icon(TYPE_DATA).also { it.data = data.copyOfRange(offset, offset + length) }

        @JvmStatic
        fun createWithContentUri(uri: String?): Icon = Icon(TYPE_URI).also { it.uri = uri?.let { u -> Uri.parse(u) } }

        @JvmStatic
        fun createWithContentUri(uri: Uri?): Icon = Icon(TYPE_URI).also { it.uri = uri }
    }

    fun getType(): Int = type
    fun getResId(): Int = resId
    fun getUri(): Uri? = uri

    fun loadDrawable(context: Context?): Drawable? = when (type) {
        TYPE_RESOURCE -> context?.getDrawable(resId)
        TYPE_BITMAP -> bitmap?.let { BitmapDrawable(context?.resources, it) }
        TYPE_DATA -> data?.let { d -> android.graphics.BitmapFactory.decodeByteArray(d, 0, d.size)?.let { BitmapDrawable(context?.resources, it) } }
        TYPE_URI -> android.content.ContentResolver.resolveToFile(uri)?.let { Drawable.createFromPath(it.absolutePath) }
        else -> null
    }

    fun setTint(tint: Int): Icon = this
}
