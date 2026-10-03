package com.lagradost.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.UiImage

/** Image request with CloudStream's default user agent plus extension provided headers */
@Composable
fun rememberImageRequest(url: String?, headers: Map<String, String>?): ImageRequest? {
    val context = coil3.compose.LocalPlatformContext.current
    return remember(url, headers) {
        if (url.isNullOrBlank()) return@remember null
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(160)
            .httpHeaders(NetworkHeaders.Builder().also { builder ->
                builder["User-Agent"] = USER_AGENT
                headers?.forEach { (key, value) -> builder[key] = value }
            }.build())
            .build()
    }
}

/** Remote poster with headers; shows nothing while loading or on error (callers draw a background) */
@Composable
fun RemoteImage(
    url: String?,
    headers: Map<String, String>? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center,
) {
    val request = rememberImageRequest(url, headers) ?: return
    AsyncImage(
        model = request,
        alignment = alignment,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        // bicubic: small icons and downscaled posters stay crisp
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
    )
}

/**
 * A poster as a soft backdrop. It is decoded at a tiny size and stretched with a smooth filter, which looks like a blur
 * but costs nothing per frame (a real blur of a window-sized image made every redraw of those screens slow).
 */
@Composable
fun SoftImage(url: String?, headers: Map<String, String>? = null, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val context = coil3.compose.LocalPlatformContext.current
    val request = remember(url, headers) {
        if (url.isNullOrBlank()) return@remember null
        ImageRequest.Builder(context)
            .data(url)
            .size(coil3.size.Size(40, 60))
            .precision(coil3.size.Precision.INEXACT)
            .crossfade(160)
            .httpHeaders(NetworkHeaders.Builder().also { builder ->
                builder["User-Agent"] = USER_AGENT
                headers?.forEach { (key, value) -> builder[key] = value }
            }.build())
            .build()
    } ?: return
    AsyncImage(model = request, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop, alpha = alpha, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
}

/** Any [UiImage] (url, drawable resource or bitmap) */
@Composable
fun UiImageView(
    image: UiImage?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    when (image) {
        is UiImage.Image -> RemoteImage(image.url, image.headers, contentDescription, modifier, contentScale)
        is UiImage.Drawable -> Image(painterResource(image.resId), contentDescription, modifier, contentScale = contentScale)
        is UiImage.Bitmap -> {
            val bitmap = remember(image.bitmap) { image.bitmap.skia.asComposeImageBitmap() }
            Image(bitmap, contentDescription, modifier, contentScale = contentScale)
        }
        null -> {}
    }
}

