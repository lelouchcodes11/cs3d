package com.lagradost.desktop.runtime.res

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.VectorDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import org.xmlpull.v1.XmlPullParser

/** Inflates XML drawables (shape, selector, layer-list, vector, inset, ripple, bitmap, color) */
object DrawableInflater {
    private val stateAttrs = mapOf(
        "state_pressed" to View.STATE_PRESSED,
        "state_focused" to View.STATE_FOCUSED,
        "state_selected" to View.STATE_SELECTED,
        "state_checked" to View.STATE_CHECKED,
        "state_enabled" to View.STATE_ENABLED,
        "state_activated" to View.STATE_ACTIVATED,
        "state_window_focused" to View.STATE_WINDOW_FOCUSED,
        "state_checkable" to View.STATE_CHECKABLE,
        "state_hovered" to View.STATE_HOVERED,
    )

    private fun attr(res: Resources, set: AttributeSet, name: String): TypedValue? = ResourceSupport.attr(res, set, name)
    private fun color(res: Resources, set: AttributeSet, name: String, theme: Resources.Theme?): Int? =
        attr(res, set, name)?.let { ResourceSupport.colorOf(res, it, theme) }

    private fun dimen(res: Resources, set: AttributeSet, name: String): Float? = attr(res, set, name)?.let { ResourceSupport.dimensionOf(res, it) }
    private fun float(res: Resources, set: AttributeSet, name: String): Float? = attr(res, set, name)?.let {
        when (it.type) {
            TypedValue.TYPE_FLOAT -> java.lang.Float.intBitsToFloat(it.data)
            TypedValue.TYPE_INT_DEC -> it.data.toFloat()
            TypedValue.TYPE_DIMENSION -> TypedValue.complexToFloat(it.data)
            TypedValue.TYPE_STRING -> it.string?.toString()?.toFloatOrNull()
            else -> null
        }
    }

    private fun string(res: Resources, set: AttributeSet, name: String): String? = attr(res, set, name)?.let {
        when (it.type) {
            TypedValue.TYPE_STRING -> it.string?.toString()
            TypedValue.TYPE_REFERENCE -> try {
                res.getString(it.data)
            } catch (e: Exception) {
                null
            }
            else -> it.string?.toString() ?: it.coerceToString().toString()
        }
    }

    private fun drawableRef(res: Resources, set: AttributeSet, name: String, theme: Resources.Theme?): Drawable? {
        val tv = attr(res, set, name) ?: return null
        return when (tv.type) {
            TypedValue.TYPE_REFERENCE -> try {
                res.getDrawable(tv.data, theme)
            } catch (e: Exception) {
                null
            }
            TypedValue.TYPE_ATTRIBUTE -> ResourceSupport.colorOf(res, tv, theme)?.let { ColorDrawable(it) }
            in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> ColorDrawable(tv.data)
            else -> null
        }
    }

    private fun skipToStart(parser: XmlPullParser) {
        var t = parser.eventType
        while (t != XmlPullParser.START_TAG && t != XmlPullParser.END_DOCUMENT) t = parser.next()
    }

    fun inflate(res: Resources, parser: XmlPullParser, theme: Resources.Theme?): Drawable? {
        skipToStart(parser)
        if (parser.eventType != XmlPullParser.START_TAG) return null
        return inflateElement(res, parser, theme)
    }

    /** Framework drawables (@android:drawable/...) the runtime provides */
    fun frameworkDrawable(path: String): Drawable? = when (path.substringAfterLast('/')) {
        // ?attr/selectableItemBackground(Borderless): a ripple over nothing, like the Material theme
        "item_background_material" -> android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf((com.lagradost.desktop.runtime.ui.ThemeBridge.textColorPrimary and 0x00FFFFFF) or 0x1F000000),
            null, ColorDrawable(0xFFFFFFFF.toInt()),
        )
        "item_background_borderless_material" -> android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf((com.lagradost.desktop.runtime.ui.ThemeBridge.textColorPrimary and 0x00FFFFFF) or 0x1F000000),
            null, null,
        )
        else -> ColorDrawable(0)
    }

    /** Parser positioned on a START_TAG; consumes the element */
    private fun inflateElement(res: Resources, parser: XmlPullParser, theme: Resources.Theme?): Drawable? {
        val set = android.util.Xml.asAttributeSet(parser)
        return when (parser.name) {
            "shape" -> inflateShape(res, parser, set, theme)
            "selector", "animated-selector" -> inflateSelector(res, parser, theme)
            "layer-list", "transition" -> inflateLayers(res, parser, theme)
            "ripple" -> inflateLayers(res, parser, theme, ripple = true)
            "vector" -> VectorDrawable.inflate(res, parser, theme)
            "animated-vector" -> inflateAnimatedVector(res, parser, set, theme)
            "inset" -> {
                val inset = dimen(res, set, "inset")?.toInt() ?: 0
                val l = dimen(res, set, "insetLeft")?.toInt() ?: inset
                val t = dimen(res, set, "insetTop")?.toInt() ?: inset
                val r = dimen(res, set, "insetRight")?.toInt() ?: inset
                val b = dimen(res, set, "insetBottom")?.toInt() ?: inset
                var inner = drawableRef(res, set, "drawable", theme)
                val depth = parser.depth
                while (true) {
                    val e = parser.next()
                    if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
                    if (e == XmlPullParser.START_TAG && inner == null) inner = inflateElement(res, parser, theme)
                }
                InsetDrawable(inner, l, t, r, b)
            }
            "bitmap", "nine-patch" -> {
                val d = drawableRef(res, set, "src", theme)
                skipElement(parser)
                d as? BitmapDrawable ?: d
            }
            "color" -> {
                val c = color(res, set, "color", theme) ?: 0
                skipElement(parser)
                ColorDrawable(c)
            }
            "clip", "scale" -> {
                val clipScale = parser.name
                val gravity = string(res, set, if (clipScale == "clip") "gravity" else "scaleGravity")
                    ?.let { com.lagradost.desktop.runtime.ui.ViewAttributes.parseGravity(it) }
                    ?: if (clipScale == "clip") android.view.Gravity.LEFT else android.view.Gravity.LEFT
                val ref = drawableRef(res, set, "drawable", theme)
                // the wrapped drawable is the element's first child (a shape, vector, ...); consume the element
                var nested: Drawable? = null
                val depth = parser.depth
                while (true) {
                    val e = parser.next()
                    if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
                    if (e == XmlPullParser.START_TAG && nested == null) nested = inflateElement(res, parser, theme)
                }
                val d = ref ?: nested
                if (clipScale == "clip") {
                    val orientation = when (string(res, set, "clipOrientation")) {
                        "vertical", "2" -> android.graphics.drawable.ClipDrawable.VERTICAL
                        else -> android.graphics.drawable.ClipDrawable.HORIZONTAL
                    }
                    android.graphics.drawable.ClipDrawable(d, gravity, orientation)
                } else {
                    fun pct(n: String) = string(res, set, n)?.trim()?.let { s ->
                        if (s.endsWith("%")) s.removeSuffix("%").toFloatOrNull()?.div(100f) else s.toFloatOrNull()
                    } ?: -1f
                    android.graphics.drawable.ScaleDrawable(d, gravity, pct("scaleWidth"), pct("scaleHeight"))
                }
            }
            "level-list", "rotate" -> {
                val d = drawableRef(res, set, "drawable", theme)
                val nested = inflateLayers(res, parser, theme)
                d ?: nested
            }
            else -> {
                skipElement(parser)
                null
            }
        }
    }

    // ------------------------------------------------------------------ animated-vector

    private fun rawAttr(parser: XmlPullParser, name: String): String? {
        parser.getAttributeValue("http://schemas.android.com/apk/res/android", name)?.let { return it }
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).substringAfter(':') == name) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun resolvedAttr(res: Resources, parser: XmlPullParser, name: String): String? {
        val raw = rawAttr(parser, name) ?: return null
        if (!raw.startsWith("@") || raw.startsWith("@android:interpolator") || raw.startsWith("@interpolator")) return raw
        val id = res.getIdentifier(raw.removePrefix("@"), null, null)
        if (id == 0) return raw
        return runCatching { res.getString(id) }.recoverCatching { res.getInteger(id).toString() }.getOrDefault(raw)
    }

    /** Inline (aapt:attr) or referenced vector plus the targets' animators */
    private fun inflateAnimatedVector(res: Resources, parser: XmlPullParser, set: AttributeSet, theme: Resources.Theme?): Drawable? {
        var vector = drawableRef(res, set, "drawable", theme)
        val animations = ArrayList<AnimatedVectorDrawable.PropertyAnimation>()
        val depth = parser.depth
        var target: String? = null
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (e == XmlPullParser.END_TAG && parser.name == "target") target = null
            if (e != XmlPullParser.START_TAG) continue
            when (parser.name.substringAfter(':')) {
                "target" -> {
                    target = rawAttr(parser, "name")
                    val animation = rawAttr(parser, "animation")
                    val id = if (animation?.startsWith("@") == true) res.getIdentifier(animation.removePrefix("@"), null, null) else 0
                    if (id != 0 && target != null) runCatching {
                        val xml = res.getXml(id)
                        try {
                            while (xml.next() != XmlPullParser.START_TAG) Unit
                            parseAnimator(res, xml, target!!, 0L, animations)
                        } finally {
                            xml.close()
                        }
                    }
                }
                "attr" -> {
                    val name = parser.getAttributeValue(null, "name") ?: rawAttr(parser, "name")
                    val attrDepth = parser.depth
                    var ev = parser.next()
                    while (ev != XmlPullParser.START_TAG && !(ev == XmlPullParser.END_TAG && parser.depth == attrDepth) && ev != XmlPullParser.END_DOCUMENT) ev = parser.next()
                    if (ev == XmlPullParser.START_TAG) when (name?.substringAfter(':')) {
                        "drawable" -> vector = inflateElement(res, parser, theme)
                        "animation" -> target?.let { parseAnimator(res, parser, it, 0L, animations) } ?: skipElement(parser)
                        else -> skipElement(parser)
                    }
                }
            }
        }
        val v = vector as? VectorDrawable ?: return vector
        return AnimatedVectorDrawable(v, animations)
    }

    /** Consumes an animator element (set, objectAnimator, animator); returns its length */
    private fun parseAnimator(res: Resources, parser: XmlPullParser, target: String, offset: Long, out: MutableList<AnimatedVectorDrawable.PropertyAnimation>): Long {
        val depth = parser.depth
        when (parser.name) {
            "set" -> {
                val sequential = rawAttr(parser, "ordering") in setOf("sequentially", "1")
                var t = offset
                var end = offset
                while (true) {
                    val e = parser.next()
                    if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
                    if (e != XmlPullParser.START_TAG) continue
                    val d = parseAnimator(res, parser, target, if (sequential) t else offset, out)
                    if (sequential) t += d
                    end = maxOf(end, if (sequential) t else offset + d)
                }
                return end - offset
            }
            "objectAnimator", "animator" -> {
                val duration = resolvedAttr(res, parser, "duration")?.toLongOrNull() ?: 300L
                val startOffset = resolvedAttr(res, parser, "startOffset")?.toLongOrNull() ?: 0L
                val repeat = when (val r = rawAttr(parser, "repeatCount")) {
                    null -> 0
                    "infinite", "-1" -> -1
                    else -> r.toIntOrNull() ?: 0
                }
                val reverse = rawAttr(parser, "repeatMode") in setOf("reverse", "2")
                val interpolator = AnimatedVectorDrawable.interpolator(rawAttr(parser, "interpolator"))
                fun add(property: String?, from: String?, to: String?) {
                    if (property != null) out.add(AnimatedVectorDrawable.PropertyAnimation(target, property, from, to, duration, offset + startOffset, repeat, reverse, interpolator))
                }
                add(rawAttr(parser, "propertyName"), resolvedAttr(res, parser, "valueFrom"), resolvedAttr(res, parser, "valueTo"))
                while (true) {
                    val e = parser.next()
                    if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
                    if (e == XmlPullParser.START_TAG && parser.name == "propertyValuesHolder") {
                        add(rawAttr(parser, "propertyName"), resolvedAttr(res, parser, "valueFrom"), resolvedAttr(res, parser, "valueTo"))
                    }
                }
                return startOffset + if (repeat < 0) duration else duration * (repeat + 1)
            }
            else -> {
                skipElement(parser)
                return 0
            }
        }
    }

    private fun skipElement(parser: XmlPullParser) {
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) return
        }
    }

    private fun inflateShape(res: Resources, parser: XmlPullParser, set: AttributeSet, theme: Resources.Theme?): Drawable {
        val d = GradientDrawable()
        when (string(res, set, "shape")?.lowercase() ?: attr(res, set, "shape")?.data?.toString()) {
            "oval", "1" -> d.setShape(GradientDrawable.OVAL)
            "line", "2" -> d.setShape(GradientDrawable.LINE)
            "ring", "3" -> d.setShape(GradientDrawable.RING)
            else -> d.setShape(GradientDrawable.RECTANGLE)
        }
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (e != XmlPullParser.START_TAG) continue
            val s = android.util.Xml.asAttributeSet(parser)
            when (parser.name) {
                "solid" -> {
                    val tv = attr(res, s, "color")
                    val csl = tv?.let { ResourceSupport.colorStateListOf(res, it, theme) }
                    if (csl != null) d.setColor(csl)
                }
                "stroke" -> {
                    val w = dimen(res, s, "width")?.toInt() ?: 0
                    val tv = attr(res, s, "color")
                    val csl = tv?.let { ResourceSupport.colorStateListOf(res, it, theme) } ?: ColorStateList.valueOf(0)
                    d.setStroke(w, csl, dimen(res, s, "dashWidth") ?: 0f, dimen(res, s, "dashGap") ?: 0f)
                }
                "corners" -> {
                    val radius = dimen(res, s, "radius") ?: 0f
                    val tl = dimen(res, s, "topLeftRadius") ?: radius
                    val tr = dimen(res, s, "topRightRadius") ?: radius
                    val br = dimen(res, s, "bottomRightRadius") ?: radius
                    val bl = dimen(res, s, "bottomLeftRadius") ?: radius
                    if (tl == tr && tr == br && br == bl) d.setCornerRadius(radius)
                    else d.setCornerRadii(floatArrayOf(tl, tl, tr, tr, br, br, bl, bl))
                }
                "gradient" -> {
                    val start = color(res, s, "startColor", theme)
                    val center = color(res, s, "centerColor", theme)
                    val end = color(res, s, "endColor", theme)
                    val colors = listOfNotNull(start, center, end).toIntArray()
                    if (colors.isNotEmpty()) d.setColors(if (colors.size == 1) intArrayOf(colors[0], colors[0]) else colors)
                    val angle = float(res, s, "angle")?.toInt() ?: 0
                    d.setOrientation(
                        when (((angle % 360) + 360) % 360) {
                            45 -> GradientDrawable.Orientation.BL_TR
                            90 -> GradientDrawable.Orientation.BOTTOM_TOP
                            135 -> GradientDrawable.Orientation.BR_TL
                            180 -> GradientDrawable.Orientation.RIGHT_LEFT
                            225 -> GradientDrawable.Orientation.TR_BL
                            270 -> GradientDrawable.Orientation.TOP_BOTTOM
                            315 -> GradientDrawable.Orientation.TL_BR
                            else -> GradientDrawable.Orientation.LEFT_RIGHT
                        }
                    )
                    when (string(res, s, "type") ?: attr(res, s, "type")?.data?.toString()) {
                        "radial", "1" -> d.setGradientType(GradientDrawable.RADIAL_GRADIENT)
                        "sweep", "2" -> d.setGradientType(GradientDrawable.SWEEP_GRADIENT)
                    }
                    dimen(res, s, "gradientRadius")?.let { d.setGradientRadius(it) }
                    val cx = float(res, s, "centerX")
                    val cy = float(res, s, "centerY")
                    if (cx != null || cy != null) d.setGradientCenter(cx ?: 0.5f, cy ?: 0.5f)
                }
                "size" -> d.setSize(dimen(res, s, "width")?.toInt() ?: -1, dimen(res, s, "height")?.toInt() ?: -1)
                "padding" -> d.setPadding(
                    dimen(res, s, "left")?.toInt() ?: 0, dimen(res, s, "top")?.toInt() ?: 0,
                    dimen(res, s, "right")?.toInt() ?: 0, dimen(res, s, "bottom")?.toInt() ?: 0
                )
            }
        }
        return d
    }

    private fun stateSpec(set: AttributeSet): IntArray {
        val states = ArrayList<Int>()
        for (i in 0 until set.attributeCount) {
            val name = set.getAttributeName(i).substringAfter(':')
            val id = stateAttrs[name] ?: continue
            val v = set.getAttributeValue(i)
            val on = v == "true" || v == "-1" || (set is AXmlResourceParser && set.getAttributeDataType(i) == TypedValue.TYPE_INT_BOOLEAN && set.getAttributeData(i) != 0)
            states.add(if (on) id else -id)
        }
        return states.toIntArray()
    }

    private fun inflateSelector(res: Resources, parser: XmlPullParser, theme: Resources.Theme?): Drawable {
        val d = StateListDrawable()
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (e != XmlPullParser.START_TAG || parser.name != "item") continue
            val s = android.util.Xml.asAttributeSet(parser)
            val spec = stateSpec(s)
            var child = drawableRef(res, s, "drawable", theme)
            val itemDepth = parser.depth
            while (true) {
                val ie = parser.next()
                if (ie == XmlPullParser.END_DOCUMENT || (ie == XmlPullParser.END_TAG && parser.depth == itemDepth)) break
                if (ie == XmlPullParser.START_TAG && child == null) child = inflateElement(res, parser, theme)
            }
            d.addState(spec, child)
        }
        return d
    }

    private fun inflateLayers(res: Resources, parser: XmlPullParser, theme: Resources.Theme?, ripple: Boolean = false): Drawable {
        val layers = ArrayList<Pair<Drawable?, IntArray>>()
        val ids = ArrayList<Int>()
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (e != XmlPullParser.START_TAG || parser.name != "item") continue
            val s = android.util.Xml.asAttributeSet(parser)
            val idTv = attr(res, s, "id")
            val isMask = ripple && (idTv?.data == 0x0102002e || s.getAttributeValue(null, "id")?.contains("mask") == true)
            val insets = intArrayOf(
                dimen(res, s, "left")?.toInt() ?: 0, dimen(res, s, "top")?.toInt() ?: 0,
                dimen(res, s, "right")?.toInt() ?: 0, dimen(res, s, "bottom")?.toInt() ?: 0
            )
            var child = drawableRef(res, s, "drawable", theme)
            val itemDepth = parser.depth
            while (true) {
                val ie = parser.next()
                if (ie == XmlPullParser.END_DOCUMENT || (ie == XmlPullParser.END_TAG && parser.depth == itemDepth)) break
                if (ie == XmlPullParser.START_TAG && child == null) child = inflateElement(res, parser, theme)
            }
            if (!isMask) {
                layers.add(child to insets)
                ids.add(if (idTv?.type == TypedValue.TYPE_REFERENCE) idTv.data else -1)
            }
        }
        val ld = LayerDrawable(layers.map { it.first }.toTypedArray())
        layers.forEachIndexed { i, (_, inset) ->
            ld.setLayerInset(i, inset[0], inset[1], inset[2], inset[3])
            ld.setId(i, ids[i])
        }
        return ld
    }

    fun inflateColorStateList(res: Resources, parser: XmlPullParser, theme: Resources.Theme?): ColorStateList? {
        skipToStart(parser)
        if (parser.name != "selector") return null
        val specs = ArrayList<IntArray>()
        val colors = ArrayList<Int>()
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && parser.depth == depth)) break
            if (e != XmlPullParser.START_TAG || parser.name != "item") continue
            val s = android.util.Xml.asAttributeSet(parser)
            var c = color(res, s, "color", theme) ?: continue
            float(res, s, "alpha")?.let { a -> c = (c and 0x00FFFFFF) or ((((c ushr 24) * a).toInt().coerceIn(0, 255)) shl 24) }
            specs.add(stateSpec(s))
            colors.add(c)
        }
        if (colors.isEmpty()) return null
        return ColorStateList(specs.toTypedArray(), colors.toIntArray())
    }
}
