package com.lagradost.desktop.ui

import android.util.Log
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.lagradost.cloudstream3.R
import com.lagradost.desktop.runtime.AndroidRuntime
import org.jetbrains.skia.Image
import org.xml.sax.InputSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads app drawables (res/drawable*, res/mipmap*) for compose: vector XML through the compose
 * Android-vector parser, bitmaps through Skia. Theme attributes (?attr/...) in vectors are resolved
 * to the current theme colors first.
 */
object AndroidResourceImages {
    private const val TAG = "AndroidResourceImages"
    private val cache = ConcurrentHashMap<Int, Any>()
    private val MISSING = Any()

    /** @return an [ImageVector], a Skia [Image] or null */
    fun load(id: Int, density: Density): Any? {
        val cached = cache[id]
        if (cached != null) return cached.takeIf { it !== MISSING }
        val result = try {
            loadUncached(id, density)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not load drawable ${AndroidRuntime.context.resources.getResourceName(id)}: $t")
            null
        }
        cache[id] = result ?: MISSING
        return result
    }

    private fun loadUncached(id: Int, density: Density): Any? {
        val res = AndroidRuntime.context.resources
        val tv = android.util.TypedValue()
        res.getValue(id, tv, true)
        val path = tv.string?.toString() ?: return null
        val stream = res.assets.let { _ -> AndroidResourceImages::class.java.classLoader.getResourceAsStream("android-res/$path") }
            ?: return null
        return stream.use { input ->
            if (path.endsWith(".xml")) {
                val text = resolveAttributes(input.readBytes().decodeToString())
                if (!text.contains("<vector")) return null
                loadXmlImageVector(InputSource(text.reader()), density)
            } else {
                Image.makeFromEncoded(input.readBytes())
            }
        }
    }

    /** Replace ?attr/x and @color/x references that the compose parser can not resolve */
    private fun resolveAttributes(xml: String): String {
        val res = AndroidRuntime.context.resources
        val theme = AndroidRuntime.context.theme
        return Regex("\"(\\?(?:attr/)?([A-Za-z0-9_]+)|@color/([A-Za-z0-9_]+)|@android:color/([A-Za-z0-9_]+))\"").replace(xml) { m ->
            val color: Int? = try {
                when {
                    m.groupValues[2].isNotEmpty() -> {
                        val attr = res.getIdentifier(m.groupValues[2], "attr", AndroidRuntime.PACKAGE_NAME)
                            .takeIf { it != 0 }
                            ?: res.getIdentifier(m.groupValues[2], "attr", "android")
                        val a = theme.obtainStyledAttributes(intArrayOf(attr))
                        try {
                            if (a.hasValue(0)) a.getColor(0, 0) else null
                        } finally {
                            a.recycle()
                        }
                    }

                    m.groupValues[3].isNotEmpty() -> res.getColor(
                        res.getIdentifier(m.groupValues[3], "color", AndroidRuntime.PACKAGE_NAME), theme
                    )

                    else -> when (m.groupValues[4]) {
                        "white" -> 0xFFFFFFFF.toInt()
                        "black" -> 0xFF000000.toInt()
                        "transparent" -> 0
                        else -> null
                    }
                }
            } catch (_: Throwable) {
                null
            }
            if (color == null) "\"#FFFFFFFF\"" else "\"#%08X\"".format(color)
        }
    }

    @Suppress("unused")
    private val unusedR = R::class.java
}
