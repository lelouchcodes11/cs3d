@file:JvmName("AndroidResourcesDesktopKt")

package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lagradost.desktop.ui.AndroidResourceImages
import org.jetbrains.skia.Image

/*
 * Android compose resource functions (androidx.compose.ui.res) for the upstream compose screens,
 * backed by the app's Android resources (strings in every language, vector drawables, bitmaps).
 */

@Composable
@ReadOnlyComposable
fun stringResource(id: Int): String {
    return LocalContext.current.resources.getString(id)
}

@Composable
@ReadOnlyComposable
fun stringResource(id: Int, vararg formatArgs: Any): String {
    return LocalContext.current.resources.getString(id, *formatArgs)
}

@Composable
@ReadOnlyComposable
fun pluralStringResource(id: Int, count: Int): String {
    return LocalContext.current.resources.getQuantityString(id, count)
}

@Composable
@ReadOnlyComposable
fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String {
    return LocalContext.current.resources.getQuantityString(id, count, *formatArgs)
}

@Composable
@ReadOnlyComposable
fun stringArrayResource(id: Int): Array<String> {
    return LocalContext.current.resources.getStringArray(id)
}

@Composable
@ReadOnlyComposable
fun integerArrayResource(id: Int): IntArray {
    return LocalContext.current.resources.getIntArray(id)
}

@Composable
@ReadOnlyComposable
fun integerResource(id: Int): Int {
    return LocalContext.current.resources.getInteger(id)
}

@Composable
@ReadOnlyComposable
fun booleanResource(id: Int): Boolean {
    return LocalContext.current.resources.getBoolean(id)
}

@Composable
@ReadOnlyComposable
fun dimensionResource(id: Int): androidx.compose.ui.unit.Dp {
    val px = LocalContext.current.resources.getDimension(id)
    return androidx.compose.ui.unit.Dp(px / LocalContext.current.resources.displayMetrics.density)
}

@Composable
@ReadOnlyComposable
fun colorResource(id: Int): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(LocalContext.current.resources.getColor(id, null))
}

/** Painter for an Android drawable resource: vector XML or bitmap */
@Composable
fun painterResource(id: Int): Painter {
    val density = LocalDensity.current
    val image = remember(id) { AndroidResourceImages.load(id, Density(1f)) }
    return when (image) {
        is ImageVector -> rememberVectorPainter(image)
        is Image -> remember(image) { BitmapPainter(image.toComposeImageBitmap()) }
        else -> remember { BitmapPainter(androidx.compose.ui.graphics.ImageBitmap(1, 1)) }
    }.also { density.density }
}

/** ImageVector for an Android vector drawable resource */
@Composable
fun vectorResource(id: Int): ImageVector {
    return remember(id) {
        AndroidResourceImages.load(id, Density(1f)) as? ImageVector
            ?: ImageVector.Builder(defaultWidth = androidx.compose.ui.unit.Dp(24f), defaultHeight = androidx.compose.ui.unit.Dp(24f), viewportWidth = 24f, viewportHeight = 24f).build()
    }
}
