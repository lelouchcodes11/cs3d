package coil3.request

import android.content.Context
import coil3.PlatformContext

fun ImageRequest.Builder.allowHardware(enable: Boolean): ImageRequest.Builder = this

fun coil3.ImageLoader.Builder.allowHardware(enable: Boolean): coil3.ImageLoader.Builder = this

val ImageRequest.allowHardware: Boolean get() = false

fun ImageRequest.Builder.listener(
    onStart: ((request: ImageRequest) -> Unit)? = null,
    onCancel: ((request: ImageRequest) -> Unit)? = null,
    onError: ((request: ImageRequest, result: ErrorResult) -> Unit)? = null,
    onSuccess: ((request: ImageRequest, result: SuccessResult) -> Unit)? = null,
): ImageRequest.Builder {
    return listener(object : ImageRequest.Listener {
        override fun onStart(request: ImageRequest) { onStart?.invoke(request) }
        override fun onCancel(request: ImageRequest) { onCancel?.invoke(request) }
        override fun onError(request: ImageRequest, result: ErrorResult) { onError?.invoke(request, result) }
        override fun onSuccess(request: ImageRequest, result: SuccessResult) { onSuccess?.invoke(request, result) }
    })
}
