package coil3

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.target.Target
import java.util.Collections
import java.util.WeakHashMap

// desktop: coil's Android-only ImageView/Drawable API (coil3.load, dispose, asDrawable, asImage) on the JVM artifact

private val activeRequests: MutableMap<ImageView, Disposable> = Collections.synchronizedMap(WeakHashMap())

private fun onMain(block: () -> Unit) {
    val main = Looper.getMainLooper()
    if (Looper.myLooper() == main) block() else Handler(main).post(block)
}

private class ImageViewTarget(private val view: ImageView) : Target {
    override fun onStart(placeholder: Image?) = onMain { view.setImageDrawable(placeholder?.asDrawable(view.getResources())) }
    override fun onError(error: Image?) = onMain { if (error != null) view.setImageDrawable(error.asDrawable(view.getResources())) }
    override fun onSuccess(result: Image) = onMain { view.setImageDrawable(result.asDrawable(view.getResources())) }
}

fun ImageView.load(
    data: Any?,
    imageLoader: ImageLoader = SingletonImageLoader.get(PlatformContext.INSTANCE),
    builder: ImageRequest.Builder.() -> Unit = {},
): Disposable {
    val request = ImageRequest.Builder(PlatformContext.INSTANCE)
        .data(data)
        .target(ImageViewTarget(this))
        .apply(builder)
        .build()
    val disposable = imageLoader.enqueue(request)
    activeRequests.put(this, disposable)?.dispose()
    return disposable
}

fun ImageView.dispose() {
    activeRequests.remove(this)?.dispose()
}

fun ImageLoader(context: Context): ImageLoader =
    SingletonImageLoader.get(PlatformContext.INSTANCE)

fun SingletonImageLoader.get(context: Context): ImageLoader = get(PlatformContext.INSTANCE)

fun Image.asDrawable(resources: Resources): Drawable =
    BitmapDrawable(resources, android.graphics.Bitmap.fromSkia(toBitmap()))

fun Drawable.asImage(): Image {
    val bitmap = (this as? BitmapDrawable)?.bitmap ?: Drawable.toBitmap(this, 0, 0)
    return bitmap.skia.asImage()
}

fun android.graphics.Bitmap.asImage(): Image = skia.asImage()

/** Android's ResourceIntMapper + ResourceUriFetcher: an Int request is a drawable resource */
class ResourceIntFetcher(private val context: Context, private val resId: Int) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val drawable = context.getDrawable(resId) ?: return null
        return ImageFetchResult(drawable.asImage(), false, DataSource.MEMORY)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Int> {
        override fun create(data: Int, options: Options, imageLoader: ImageLoader): Fetcher =
            ResourceIntFetcher(context, data)
    }
}
