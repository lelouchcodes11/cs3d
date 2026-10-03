package com.lagradost.desktop.runtime.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.res.ResourceSupport

/** Applies XML layout attributes (text or compiled) to views and layout params */
object ViewAttributes {
    private var currentSet: AttributeSet? = null
    private fun res(v: View): Resources = ResourceSupport.resourcesOf(currentSet) ?: v.getContext().resources
    private fun res(c: Context?): Resources = ResourceSupport.resourcesOf(currentSet) ?: (c ?: AndroidRuntime.applicationContext)!!.resources

    private var currentTheme: Resources.Theme? = null

    // ------------------------------------------------------------------ styles (obtainStyledAttributes)

    /** A style item with the resources its value belongs to */
    private class StyleValue(val value: com.lagradost.desktop.runtime.res.ResValue, val res: Resources)

    private var styleKey: String? = null
    private var currentStyle: Map<Int, StyleValue> = emptyMap()
    private var currentAppearance: Map<Int, StyleValue> = emptyMap()

    /** Resources a typed value from a style belongs to (app theme styles used by extension layouts) */
    private val valueRes = java.util.WeakHashMap<TypedValue, Resources>()
    private fun resOf(tv: TypedValue, res: Resources): Resources = valueRes[tv] ?: res

    /** Theme attributes holding the default style of a widget class, most specific first */
    private fun defaultStyleAttrs(v: View): List<String> = when (v) {
        is com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton -> listOf("extendedFloatingActionButtonStyle")
        is com.google.android.material.floatingactionbutton.FloatingActionButton -> listOf("floatingActionButtonStyle")
        is com.google.android.material.button.MaterialButton -> listOf("materialButtonStyle", "buttonStyle", "android:buttonStyle")
        is com.google.android.material.chip.Chip -> listOf("chipStyle")
        is android.widget.EditText -> listOf("editTextStyle", "android:editTextStyle")
        is android.widget.CheckBox -> listOf("checkboxStyle", "android:checkboxStyle")
        is android.widget.RadioButton -> listOf("radioButtonStyle", "android:radioButtonStyle")
        is android.widget.Switch -> listOf("switchStyle", "android:switchStyle")
        is android.widget.ToggleButton -> listOf("android:buttonStyleToggle")
        is android.widget.CheckedTextView -> listOf("checkedTextViewStyle", "android:checkedTextViewStyle")
        is android.widget.Button -> listOf("buttonStyle", "android:buttonStyle")
        is android.widget.TextView -> listOf("android:textViewStyle")
        is android.widget.ImageButton -> listOf("imageButtonStyle", "android:imageButtonStyle")
        is android.widget.SeekBar -> listOf("seekBarStyle", "android:seekBarStyle")
        is android.widget.RatingBar -> listOf("ratingBarStyle", "android:ratingBarStyle")
        is com.google.android.material.progressindicator.CircularProgressIndicator -> listOf("circularProgressIndicatorStyle")
        is com.google.android.material.progressindicator.LinearProgressIndicator -> listOf("linearProgressIndicatorStyle")
        is android.widget.ProgressBar -> listOf("android:progressBarStyle")
        is android.widget.Spinner -> listOf("spinnerStyle", "android:spinnerStyle")
        is com.google.android.material.card.MaterialCardView -> listOf("materialCardViewStyle", "cardViewStyle")
        is androidx.cardview.widget.CardView -> listOf("cardViewStyle")
        is androidx.appcompat.widget.Toolbar -> listOf("toolbarStyle", "android:toolbarStyle")
        is androidx.appcompat.widget.SearchView -> listOf("searchViewStyle")
        is com.google.android.material.textfield.TextInputLayout -> listOf("textInputStyle")
        is com.google.android.material.chip.ChipGroup -> listOf("chipGroupStyle")
        is android.widget.ListView -> listOf("android:listViewStyle")
        is android.widget.GridView -> listOf("android:gridViewStyle")
        is android.widget.ScrollView -> listOf("android:scrollViewStyle")
        is android.widget.HorizontalScrollView -> listOf("android:horizontalScrollViewStyle")
        is com.google.android.material.divider.MaterialDivider -> listOf("materialDividerStyle")
        else -> emptyList()
    }

    private fun attrIdOf(res: Resources, name: String): Int =
        if (name.startsWith("android:")) com.lagradost.desktop.runtime.res.FrameworkResources.attrId(name.removePrefix("android:"))
        else res.getIdentifier(name, "attr", null)

    /** Style id held by a theme attribute */
    private fun themeStyle(theme: Resources.Theme, attrName: String): Int {
        val id = attrIdOf(theme.resources, attrName)
        if (id == 0) return 0
        val out = TypedValue()
        if (!theme.resolveAttribute(id, out, true)) return 0
        return if (out.type == TypedValue.TYPE_REFERENCE) out.data else 0
    }

    private fun styleItems(res: Resources, styleId: Int): Map<Int, StyleValue> {
        if (styleId == 0) return emptyMap()
        val table = res.table ?: return emptyMap()
        return table.getStyle(styleId, res.configuration).mapValues { StyleValue(it.value, res) }
    }

    /** style="@style/..." of an element */
    private fun explicitStyleId(res: Resources, set: AttributeSet): Int {
        val id = set.getStyleAttribute()
        if (id != 0) return id
        for (i in 0 until set.attributeCount) {
            if (set.getAttributeName(i) != "style") continue
            val raw = set.getAttributeValue(i) ?: return 0
            if (raw.startsWith("@android:")) return 0
            return res.getIdentifier(raw.substringAfter('/').replace('.', '_'), "style", null)
        }
        return 0
    }

    /** The default style of [v] from its theme under the explicit style of the element */
    private fun prepareStyle(v: View?, set: AttributeSet, res: Resources, withDefault: Boolean) {
        val key = "${System.identityHashCode(set)}:${set.getPositionDescription()}:${if (v == null) 0 else System.identityHashCode(v)}"
        if (key == styleKey) return
        styleKey = key
        currentAppearance = emptyMap()
        val merged = LinkedHashMap<Int, StyleValue>()
        val theme = v?.getContext()?.theme
        if (withDefault && v != null && theme != null) {
            for (attr in defaultStyleAttrs(v)) {
                val id = themeStyle(theme, attr)
                if (id != 0) {
                    merged.putAll(styleItems(theme.resources, id))
                    break
                }
            }
        }
        merged.putAll(styleItems(res, explicitStyleId(res, set)))
        currentStyle = merged
    }

    private fun fromStyle(res: Resources, name: String, style: Map<Int, StyleValue>): TypedValue? {
        if (style.isEmpty()) return null
        var sv = style[attrIdOf(res, "android:$name")]
        if (sv == null) {
            // app / library attributes of the style's own resources
            for (candidate in style.values.map { it.res }.distinct()) {
                val id = candidate.getIdentifier(name, "attr", null)
                if (id != 0) sv = style[id]
                if (sv != null) break
            }
        }
        val found = sv ?: return null
        val tv = TypedValue()
        ResourceSupport.toTypedValue(found.res, found.value, tv)
        if (found.res !== res) valueRes[tv] = found.res
        return tv
    }

    /**
     * Typed value of an attribute: from the element, else its style, else the widget's default style;
     * ?attr references are resolved through the view's theme (an unresolved theme attribute behaves as
     * if the attribute was not set, like on Android)
     */
    private fun a(res: Resources, set: AttributeSet, name: String): TypedValue? {
        val tv = ResourceSupport.attr(res, set, name) ?: fromStyle(res, name, currentStyle)
            ?: (if (useAppearance) fromStyle(res, name, currentAppearance) else null) ?: return null
        return resolveThemeAttr(tv)
    }

    /** While applying TextView attributes the text appearance is consulted after the element and its style */
    private var useAppearance = false

    private fun resolveThemeAttr(tv: TypedValue): TypedValue? {
        if (tv.type != TypedValue.TYPE_ATTRIBUTE) return tv
        val theme = currentTheme ?: return tv
        var attr = tv.data
        repeat(8) {
            val out = TypedValue()
            if (!theme.resolveAttribute(attr, out, true)) return null
            if (out.type != TypedValue.TYPE_ATTRIBUTE) {
                valueRes[out] = theme.resources
                return out
            }
            attr = out.data
        }
        return null
    }

    /** "@null" (or "@empty") */
    private fun isNull(tv: TypedValue): Boolean =
        tv.type == TypedValue.TYPE_NULL || (tv.type == TypedValue.TYPE_REFERENCE && tv.data == 0) ||
            (tv.type == TypedValue.TYPE_STRING && tv.string?.toString() == "@null")

    private fun dim(res: Resources, set: AttributeSet, name: String): Int? =
        a(res, set, name)?.let { ResourceSupport.dimensionOf(resOf(it, res), it)?.let { f -> if (f >= 0) (f + 0.5f).toInt() else (f - 0.5f).toInt() } }

    private fun layoutDim(res: Resources, set: AttributeSet, name: String): Int? {
        val tv = a(res, set, name) ?: return null
        return when {
            tv.type == TypedValue.TYPE_INT_DEC || tv.type == TypedValue.TYPE_INT_HEX -> tv.data
            tv.type == TypedValue.TYPE_STRING && tv.string?.toString() in setOf("match_parent", "fill_parent") -> ViewGroup.LayoutParams.MATCH_PARENT
            tv.type == TypedValue.TYPE_STRING && tv.string?.toString() == "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> ResourceSupport.dimensionOf(resOf(tv, res), tv)?.let { (it + 0.5f).toInt() }
        }
    }

    private fun bool(res: Resources, set: AttributeSet, name: String): Boolean? = a(res, set, name)?.let {
        when (it.type) {
            TypedValue.TYPE_INT_BOOLEAN, TypedValue.TYPE_INT_DEC -> it.data != 0
            TypedValue.TYPE_REFERENCE -> try {
                resOf(it, res).getBoolean(it.data)
            } catch (e: Exception) {
                null
            }
            else -> it.string?.toString()?.toBooleanStrictOrNull()
        }
    }

    private fun int(res: Resources, set: AttributeSet, name: String): Int? = a(res, set, name)?.let {
        when (it.type) {
            TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> it.data
            TypedValue.TYPE_REFERENCE -> try {
                resOf(it, res).getInteger(it.data)
            } catch (e: Exception) {
                null
            }
            else -> it.string?.toString()?.toIntOrNull()
        }
    }

    private fun float(res: Resources, set: AttributeSet, name: String): Float? = a(res, set, name)?.let {
        when (it.type) {
            TypedValue.TYPE_FLOAT -> java.lang.Float.intBitsToFloat(it.data)
            TypedValue.TYPE_INT_DEC -> it.data.toFloat()
            TypedValue.TYPE_DIMENSION -> TypedValue.complexToFloat(it.data)
            else -> it.string?.toString()?.toFloatOrNull()
        }
    }

    private fun text(res: Resources, set: AttributeSet, name: String): CharSequence? = a(res, set, name)?.let {
        when (it.type) {
            TypedValue.TYPE_STRING -> it.string
            TypedValue.TYPE_REFERENCE -> try {
                resOf(it, res).getText(it.data)
            } catch (e: Exception) {
                null
            }
            TypedValue.TYPE_NULL -> null
            else -> it.string ?: it.coerceToString()
        }
    }

    private fun enumValue(res: Resources, set: AttributeSet, name: String): String? = a(res, set, name)?.let {
        when (it.type) {
            TypedValue.TYPE_STRING -> it.string?.toString()
            TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> it.data.toString()
            else -> null
        }
    }

    private fun idOf(res: Resources, set: AttributeSet, name: String): Int? {
        val tv = a(res, set, name) ?: return null
        return when (tv.type) {
            TypedValue.TYPE_REFERENCE -> tv.data
            TypedValue.TYPE_STRING -> tv.string?.toString()?.let { s ->
                if (s.startsWith("@")) IdRegistry.idFor(res, s) else null
            }
            else -> null
        }
    }

    /** Gravity from a string ("center|start") or int flag value */
    fun parseGravity(v: String?): Int? {
        if (v == null) return null
        v.toIntOrNull()?.let { return it }
        v.removePrefix("0x").toIntOrNull(16)?.takeIf { v.startsWith("0x") }?.let { return it }
        var g = 0
        for (part in v.split('|')) {
            g = g or when (part.trim()) {
                "top" -> Gravity.TOP
                "bottom" -> Gravity.BOTTOM
                "left" -> Gravity.LEFT
                "right" -> Gravity.RIGHT
                "start" -> Gravity.START
                "end" -> Gravity.END
                "center" -> Gravity.CENTER
                "center_vertical" -> Gravity.CENTER_VERTICAL
                "center_horizontal" -> Gravity.CENTER_HORIZONTAL
                "fill" -> Gravity.FILL
                "fill_vertical" -> Gravity.FILL_VERTICAL
                "fill_horizontal" -> Gravity.FILL_HORIZONTAL
                else -> 0
            }
        }
        return g
    }

    private fun gravity(res: Resources, set: AttributeSet, name: String): Int? {
        val tv = a(res, set, name) ?: return null
        return if (tv.type == TypedValue.TYPE_INT_DEC || tv.type == TypedValue.TYPE_INT_HEX) tv.data else parseGravity(tv.string?.toString())
    }

    /**
     * Attribute access for widget constructors: the element, its style, then the theme's default
     * widget style. Use it right away (it reads the shared current-style state).
     */
    class Reader internal constructor(private val v: View, private val set: AttributeSet, private val res: Resources) {
        private val theme get() = v.getContext().theme

        fun has(name: String): Boolean = a(res, set, name) != null
        fun isNull(name: String): Boolean = a(res, set, name)?.let { isNull(it) } ?: false
        fun text(name: String): CharSequence? = text(res, set, name)
        fun bool(name: String): Boolean? = bool(res, set, name)
        fun int(name: String): Int? = int(res, set, name)
        fun dim(name: String): Int? = dim(res, set, name)
        fun float(name: String): Float? = float(res, set, name)
        fun resourceId(name: String): Int = a(res, set, name)?.takeIf { it.type == TypedValue.TYPE_REFERENCE }?.data ?: 0
        fun drawable(name: String): android.graphics.drawable.Drawable? {
            val tv = a(res, set, name) ?: return null
            if (isNull(tv)) return null
            return when (tv.type) {
                TypedValue.TYPE_REFERENCE -> try {
                    resOf(tv, res).getDrawable(tv.data, theme)
                } catch (e: Exception) {
                    null
                }
                else -> ResourceSupport.colorOf(resOf(tv, res), tv, theme)?.let { ColorDrawable(it) }
            }
        }
        fun colorStateList(name: String): android.content.res.ColorStateList? =
            a(res, set, name)?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }
    }

    /** An element without attributes (views created in code still get their default style) */
    @JvmField
    val emptyAttributes: AttributeSet = object : AttributeSet {
        override fun getAttributeCount(): Int = 0
        override fun getAttributeName(index: Int): String = ""
        override fun getAttributeValue(index: Int): String? = null
        override fun getAttributeValue(namespace: String?, name: String?): String? = null
        override fun getPositionDescription(): String = "<code>"
    }

    /** The items of a style resource, like obtainStyledAttributes(null, attrs, 0, styleRes) */
    @JvmStatic
    fun styleReader(v: View, styleRes: Int): Reader {
        currentSet = emptyAttributes
        currentTheme = v.getContext().theme
        val res = res(v)
        styleKey = "style:$styleRes:${System.identityHashCode(v)}"
        currentAppearance = emptyMap()
        currentStyle = styleItems(res, styleRes)
        return Reader(v, emptyAttributes, res)
    }

    @JvmStatic
    fun reader(v: View, set: AttributeSet): Reader {
        currentSet = set
        currentTheme = v.getContext().theme
        val res = res(v)
        prepareStyle(v, set, res, true)
        return Reader(v, set, res)
    }

    @JvmStatic
    fun applyViewAttributes(v: View, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        idOf(res, set, "id")?.let { v.assignId(it) }
        (ResourceSupport.attr(res, set, "background")?.takeIf { isNull(it) } ?: a(res, set, "background"))?.let { tv ->
            // The XML background replaces the widget's default one, and so does its padding
            // (a background without padding leaves the view without the default padding)
            val bg: android.graphics.drawable.Drawable? = when {
                isNull(tv) -> null
                tv.type == TypedValue.TYPE_REFERENCE -> try {
                    resOf(tv, res).getDrawable(tv.data, v.getContext().theme)
                } catch (e: Exception) {
                    v.getBackground()
                }
                else -> ResourceSupport.colorOf(resOf(tv, res), tv, v.getContext().theme)?.let { ColorDrawable(it) } ?: v.getBackground()
            }
            if (bg !== v.getBackground()) {
                v.setBackground(bg)
                if (bg == null || !bg.getPadding(android.graphics.Rect())) v.setPadding(0, 0, 0, 0)
            }
        }
        a(res, set, "backgroundTint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, v.getContext().theme) }?.let { v.setBackgroundTintList(it) }
        val padding = dim(res, set, "padding")
        val ph = dim(res, set, "paddingHorizontal")
        val pv = dim(res, set, "paddingVertical")
        val pl = dim(res, set, "paddingLeft") ?: dim(res, set, "paddingStart") ?: ph ?: padding
        val pt = dim(res, set, "paddingTop") ?: pv ?: padding
        val pr = dim(res, set, "paddingRight") ?: dim(res, set, "paddingEnd") ?: ph ?: padding
        val pb = dim(res, set, "paddingBottom") ?: pv ?: padding
        // sides not given in XML keep the padding provided by the background drawable
        if (pl != null || pt != null || pr != null || pb != null) {
            v.setPadding(pl ?: v.getPaddingLeft(), pt ?: v.getPaddingTop(), pr ?: v.getPaddingRight(), pb ?: v.getPaddingBottom())
        }
        when (enumValue(res, set, "visibility")) {
            "invisible", "1" -> v.setVisibility(View.INVISIBLE)
            "gone", "2" -> v.setVisibility(View.GONE)
        }
        float(res, set, "alpha")?.let { v.setAlpha(it) }
        dim(res, set, "elevation")?.let { v.setElevation(it.toFloat()) }
        dim(res, set, "minWidth")?.let { v.setMinimumWidth(it) }
        dim(res, set, "minHeight")?.let { v.setMinimumHeight(it) }
        bool(res, set, "clickable")?.let { v.setClickable(it) }
        bool(res, set, "focusable")?.let { v.setFocusable(it) }
        bool(res, set, "focusableInTouchMode")?.let { v.setFocusableInTouchMode(it) }
        bool(res, set, "enabled")?.let { v.setEnabled(it) }
        text(res, set, "contentDescription")?.let { v.setContentDescription(it) }
        text(res, set, "tag")?.let { v.setTag(it.toString()) }
        float(res, set, "rotation")?.let { v.setRotation(it) }
        dim(res, set, "translationX")?.let { v.setTranslationX(it.toFloat()) }
        dim(res, set, "translationY")?.let { v.setTranslationY(it.toFloat()) }
        float(res, set, "scaleX")?.let { v.setScaleX(it) }
        float(res, set, "scaleY")?.let { v.setScaleY(it) }
        idOf(res, set, "nextFocusDown")?.let { v.setNextFocusDownId(it) }
        idOf(res, set, "nextFocusUp")?.let { v.setNextFocusUpId(it) }
        idOf(res, set, "nextFocusLeft")?.let { v.setNextFocusLeftId(it) }
        idOf(res, set, "nextFocusRight")?.let { v.setNextFocusRightId(it) }
        // Subclass attributes (LinearLayout, RelativeLayout, ScrollView, TextView ...) are applied by
        // the subclass constructors, after their fields exist, like Android's View constructors do
    }

    @JvmStatic
    fun applyRelativeLayoutAttributes(v: RelativeLayout, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        gravity(res(v), set, "gravity")?.let { v.setGravity(it) }
    }

    @JvmStatic
    fun applyTextViewAttributes(v: TextView, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        val theme = v.getContext().theme
        val appearance = ResourceSupport.attr(res, set, "textAppearance") ?: fromStyle(res, "textAppearance", currentStyle)
        currentAppearance = resolveThemeAttr(appearance ?: TypedValue())?.takeIf { appearance != null && it.type == TypedValue.TYPE_REFERENCE }
            ?.let { styleItems(resOf(it, res), it.data) } ?: emptyMap()
        useAppearance = true
        try {
        text(res, set, "text")?.let { v.setText(it) }
        text(res, set, "hint")?.let { v.setHint(it) }
        a(res, set, "textColor")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }?.let { v.setTextColor(it) }
        a(res, set, "textColorHint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }?.let { v.setHintTextColor(it) }
        a(res, set, "textColorLink")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }?.let { v.setLinkTextColor(it) }
        a(res, set, "textSize")?.let { ResourceSupport.dimensionOf(resOf(it, res), it) }?.let { v.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
        val style = when (enumValue(res, set, "textStyle")) {
            "bold", "1" -> Typeface.BOLD
            "italic", "2" -> Typeface.ITALIC
            "bold|italic", "3" -> Typeface.BOLD_ITALIC
            else -> null
        }
        val family = a(res, set, "fontFamily")?.let { tv ->
            if (tv.type == TypedValue.TYPE_REFERENCE) try {
                resOf(tv, res).getFont(tv.data)
            } catch (e: Exception) {
                null
            } else tv.string?.toString()?.let { Typeface.create(it, style ?: Typeface.NORMAL) }
        }
        if (family != null || style != null) v.setTypeface(family ?: Typeface.defaultFromStyle(style ?: 0), style ?: family?.getStyle() ?: 0)
        gravity(res, set, "gravity")?.let { v.setGravity(it) }
        int(res, set, "maxLines")?.let { v.setMaxLines(it) }
        int(res, set, "minLines")?.let { v.setMinLines(it) }
        int(res, set, "lines")?.let { v.setLines(it) }
        bool(res, set, "singleLine")?.let { v.setSingleLine(it) }
        bool(res, set, "textAllCaps")?.let { v.setAllCaps(it) }
        float(res, set, "letterSpacing")?.let { v.setLetterSpacing(it) }
        val lsExtra = dim(res, set, "lineSpacingExtra")
        val lsMult = float(res, set, "lineSpacingMultiplier")
        if (lsExtra != null || lsMult != null) v.setLineSpacing((lsExtra ?: 0).toFloat(), lsMult ?: 1f)
        when (enumValue(res, set, "ellipsize")) {
            "start", "1" -> v.setEllipsize(TextUtils.TruncateAt.START)
            "middle", "2" -> v.setEllipsize(TextUtils.TruncateAt.MIDDLE)
            "end", "3" -> v.setEllipsize(TextUtils.TruncateAt.END)
            "marquee", "4" -> v.setEllipsize(TextUtils.TruncateAt.MARQUEE)
        }
        a(res, set, "inputType")?.let { tv ->
            val type = if (tv.type == TypedValue.TYPE_INT_DEC || tv.type == TypedValue.TYPE_INT_HEX) tv.data else parseInputType(tv.string?.toString())
            v.setInputType(type)
        }
        int(res, set, "imeOptions")?.let { v.setImeOptions(it) }
        dim(res, set, "maxWidth")?.let { v.setMaxWidth(it) }
        dim(res, set, "maxHeight")?.let { v.setMaxHeight(it) }
        dim(res, set, "drawablePadding")?.let { v.setCompoundDrawablePadding(it) }
        fun draw(n: String) = a(res, set, n)?.takeIf { it.type == TypedValue.TYPE_REFERENCE }?.let {
            try {
                resOf(it, res).getDrawable(it.data, theme)
            } catch (e: Exception) {
                null
            }
        }

        // AppCompatTextView also reads the app: *Compat variants
        val l = draw("drawableStart") ?: draw("drawableStartCompat") ?: draw("drawableLeft") ?: draw("drawableLeftCompat")
        val t = draw("drawableTop") ?: draw("drawableTopCompat")
        val r = draw("drawableEnd") ?: draw("drawableEndCompat") ?: draw("drawableRight") ?: draw("drawableRightCompat")
        val b = draw("drawableBottom") ?: draw("drawableBottomCompat")
        if (l != null || t != null || r != null || b != null) v.setCompoundDrawablesWithIntrinsicBounds(l, t, r, b)
        a(res, set, "drawableTint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }?.let { v.setCompoundDrawableTintList(it) }
        } finally {
            useAppearance = false
        }
    }

    private fun parseInputType(s: String?): Int {
        if (s == null) return InputType.TYPE_CLASS_TEXT
        var t = 0
        for (p in s.split('|')) {
            t = t or when (p.trim()) {
                "text" -> InputType.TYPE_CLASS_TEXT
                "textPassword" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                "textVisiblePassword" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                "textWebPassword" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                "textUri" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                "textEmailAddress" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "textMultiLine" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                "textNoSuggestions" -> InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                "number" -> InputType.TYPE_CLASS_NUMBER
                "numberDecimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                "numberSigned" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                "numberPassword" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                "phone" -> InputType.TYPE_CLASS_PHONE
                "none" -> InputType.TYPE_NULL
                else -> 0
            }
        }
        return t
    }

    @JvmStatic
    fun applyImageViewAttributes(v: ImageView, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        val theme = v.getContext().theme
        val src = a(res, set, "src") ?: a(res, set, "srcCompat")
        src?.let { tv ->
            when (tv.type) {
                TypedValue.TYPE_REFERENCE -> try {
                    v.setImageDrawable(resOf(tv, res).getDrawable(tv.data, theme))
                } catch (e: Exception) {
                }
                else -> ResourceSupport.colorOf(resOf(tv, res), tv, theme)?.let { v.setImageDrawable(ColorDrawable(it)) }
            }
        }
        (a(res, set, "tint") ?: a(res, set, "imageTint"))?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }?.let { v.setImageTintList(it) }
        bool(res, set, "adjustViewBounds")?.let { v.setAdjustViewBounds(it) }
        when (enumValue(res, set, "scaleType")) {
            "matrix", "0" -> v.setScaleType(ImageView.ScaleType.MATRIX)
            "fitXY", "1" -> v.setScaleType(ImageView.ScaleType.FIT_XY)
            "fitStart", "2" -> v.setScaleType(ImageView.ScaleType.FIT_START)
            "fitCenter", "3" -> v.setScaleType(ImageView.ScaleType.FIT_CENTER)
            "fitEnd", "4" -> v.setScaleType(ImageView.ScaleType.FIT_END)
            "center", "5" -> v.setScaleType(ImageView.ScaleType.CENTER)
            "centerCrop", "6" -> v.setScaleType(ImageView.ScaleType.CENTER_CROP)
            "centerInside", "7" -> v.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        }
    }

    @JvmStatic
    fun applyCompoundButtonAttributes(v: CompoundButton, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        bool(res, set, "checked")?.let { v.setChecked(it) }
        a(res, set, "buttonTint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, v.getContext().theme) }?.let { v.setButtonTintList(it) }
    }

    /** Called from Switch's constructor body, after its tint state exists. */
    @JvmStatic
    fun applySwitchAttributes(v: android.widget.Switch, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        a(res, set, "thumbTint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, v.getContext().theme) }?.let { v.setThumbTintList(it) }
        a(res, set, "trackTint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, v.getContext().theme) }?.let { v.setTrackTintList(it) }
    }

    @JvmStatic
    fun applyProgressBarAttributes(v: ProgressBar, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        val style = set.getStyleAttribute().takeIf { it != 0 }
        val styleName = set.getAttributeValue(null, "style") ?: ""
        if (styleName.contains("Horizontal", true) || (style != null && style == 0x01030078)) {
            v.setHorizontalStyle(true)
            v.setIndeterminate(false)
        }
        bool(res, set, "indeterminate")?.let { v.setIndeterminate(it) }
        int(res, set, "max")?.let { v.setMax(it) }
        int(res, set, "progress")?.let { v.setProgress(it) }
        a(res, set, "progressTint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, v.getContext().theme) }?.let { v.setProgressTintList(it) }
        a(res, set, "indeterminateTint")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, v.getContext().theme) }?.let { v.setIndeterminateTintList(it) }
        a(res, set, "progressDrawable")?.takeIf { it.type == TypedValue.TYPE_REFERENCE }?.let {
            runCatching { resOf(it, res).getDrawable(it.data, v.getContext().theme) }.getOrNull()
        }?.let { v.setProgressDrawable(it) }
    }

    @JvmStatic
    fun applyLinearLayoutAttributes(v: LinearLayout, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        when (enumValue(res, set, "orientation")) {
            "vertical", "1" -> v.setOrientation(LinearLayout.VERTICAL)
            "horizontal", "0" -> v.setOrientation(LinearLayout.HORIZONTAL)
        }
        gravity(res, set, "gravity")?.let { v.setGravity(it) }
        float(res, set, "weightSum")?.let { v.setWeightSum(it) }
    }

    @JvmStatic
    fun applyViewGroupAttributes(v: android.view.ViewGroup, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        bool(res, set, "clipToPadding")?.let { v.setClipToPadding(it) }
        bool(res, set, "clipChildren")?.let { v.setClipChildren(it) }
        when (enumValue(res, set, "descendantFocusability")) {
            "beforeDescendants", "0" -> v.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS)
            "afterDescendants", "1" -> v.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS)
            "blocksDescendants", "2" -> v.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS)
        }
    }

    @JvmStatic
    fun applyScrollAttributes(v: View, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        val fill = bool(res, set, "fillViewport") ?: return
        if (v is android.widget.ScrollView) v.setFillViewport(fill)
        if (v is android.widget.HorizontalScrollView) v.setFillViewport(fill)
    }

    @JvmStatic
    fun applyCardAttributes(v: androidx.cardview.widget.CardView, set: AttributeSet) {
        currentSet = set
        currentTheme = v.getContext().theme
        prepareStyle(v, set, res(v), true)
        val res = res(v)
        val theme = v.getContext().theme
        a(res, set, "cardCornerRadius")?.let { ResourceSupport.dimensionOf(resOf(it, res), it) }?.let { v.setRadius(it) }
        a(res, set, "cardElevation")?.let { ResourceSupport.dimensionOf(resOf(it, res), it) }?.let { v.setCardElevation(it) }
        a(res, set, "cardBackgroundColor")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }?.let { v.setCardBackgroundColor(it) }
        if (v is com.google.android.material.card.MaterialCardView) {
            a(res, set, "strokeColor")?.let { ResourceSupport.colorStateListOf(resOf(it, res), it, theme) }?.let { v.setStrokeColor(it) }
            dim(res, set, "strokeWidth")?.let { v.setStrokeWidth(it) }
        }
    }

    // ------------------------------------------------------------------ layout params

    @JvmStatic
    fun applyLayoutParams(lp: ViewGroup.LayoutParams, c: Context?, set: AttributeSet) {
        currentSet = set
        currentTheme = (c ?: AndroidRuntime.applicationContext)?.theme
        prepareStyle(null, set, res(c), false)
        val res = res(c)
        layoutDim(res, set, "layout_width")?.let { lp.width = it }
        layoutDim(res, set, "layout_height")?.let { lp.height = it }
        if (lp is ViewGroup.MarginLayoutParams) {
            val m = dim(res, set, "layout_margin")
            val mh = dim(res, set, "layout_marginHorizontal")
            val mv = dim(res, set, "layout_marginVertical")
            (dim(res, set, "layout_marginLeft") ?: dim(res, set, "layout_marginStart") ?: mh ?: m)?.let { lp.leftMargin = it }
            (dim(res, set, "layout_marginTop") ?: mv ?: m)?.let { lp.topMargin = it }
            (dim(res, set, "layout_marginRight") ?: dim(res, set, "layout_marginEnd") ?: mh ?: m)?.let { lp.rightMargin = it }
            (dim(res, set, "layout_marginBottom") ?: mv ?: m)?.let { lp.bottomMargin = it }
        }
    }

    @JvmStatic
    fun applyLinearLayoutParams(lp: LinearLayout.LayoutParams, c: Context?, set: AttributeSet) {
        currentSet = set
        currentTheme = (c ?: AndroidRuntime.applicationContext)?.theme
        prepareStyle(null, set, res(c), false)
        val res = res(c)
        float(res, set, "layout_weight")?.let { lp.weight = it }
        gravity(res, set, "layout_gravity")?.let { lp.gravity = it }
    }

    @JvmStatic
    fun applyFrameLayoutParams(lp: FrameLayout.LayoutParams, c: Context?, set: AttributeSet) {
        currentSet = set
        currentTheme = (c ?: AndroidRuntime.applicationContext)?.theme
        prepareStyle(null, set, res(c), false)
        val res = res(c)
        gravity(res, set, "layout_gravity")?.let { lp.gravity = it }
    }

    @JvmStatic
    fun applyRelativeLayoutParams(lp: RelativeLayout.LayoutParams, c: Context?, set: AttributeSet) {
        currentSet = set
        currentTheme = (c ?: AndroidRuntime.applicationContext)?.theme
        prepareStyle(null, set, res(c), false)
        val res = res(c)
        val refRules = mapOf(
            "layout_toLeftOf" to RelativeLayout.LEFT_OF, "layout_toRightOf" to RelativeLayout.RIGHT_OF,
            "layout_above" to RelativeLayout.ABOVE, "layout_below" to RelativeLayout.BELOW,
            "layout_alignBaseline" to RelativeLayout.ALIGN_BASELINE, "layout_alignLeft" to RelativeLayout.ALIGN_LEFT,
            "layout_alignTop" to RelativeLayout.ALIGN_TOP, "layout_alignRight" to RelativeLayout.ALIGN_RIGHT,
            "layout_alignBottom" to RelativeLayout.ALIGN_BOTTOM, "layout_toStartOf" to RelativeLayout.START_OF,
            "layout_toEndOf" to RelativeLayout.END_OF, "layout_alignStart" to RelativeLayout.ALIGN_START,
            "layout_alignEnd" to RelativeLayout.ALIGN_END,
        )
        for ((n, verb) in refRules) idOf(res, set, n)?.let { lp.addRule(verb, it) }
        val boolRules = mapOf(
            "layout_alignParentLeft" to RelativeLayout.ALIGN_PARENT_LEFT, "layout_alignParentTop" to RelativeLayout.ALIGN_PARENT_TOP,
            "layout_alignParentRight" to RelativeLayout.ALIGN_PARENT_RIGHT, "layout_alignParentBottom" to RelativeLayout.ALIGN_PARENT_BOTTOM,
            "layout_centerInParent" to RelativeLayout.CENTER_IN_PARENT, "layout_centerHorizontal" to RelativeLayout.CENTER_HORIZONTAL,
            "layout_centerVertical" to RelativeLayout.CENTER_VERTICAL, "layout_alignParentStart" to RelativeLayout.ALIGN_PARENT_START,
            "layout_alignParentEnd" to RelativeLayout.ALIGN_PARENT_END,
        )
        for ((n, verb) in boolRules) if (bool(res, set, n) == true) lp.addRule(verb)
        bool(res, set, "layout_alignWithParentIfMissing")?.let { lp.alignWithParent = it }
    }
}

/** Ids for "@+id/name" references in plain text layouts */
object IdRegistry {
    private val ids = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val next = java.util.concurrent.atomic.AtomicInteger(0x7f0f0000)

    fun idFor(res: Resources, ref: String): Int {
        val body = ref.removePrefix("@").removePrefix("+")
        val framework = body.startsWith("android:")
        val name = body.removePrefix("android:").removePrefix("id/")
        val existing = if (framework) res.getIdentifier("android:id/$name", null, null) else res.getIdentifier(name, "id", null)
        if (existing != 0) return existing
        return ids.getOrPut((if (framework) "android:" else "") + name) { next.getAndIncrement() }
    }
}
