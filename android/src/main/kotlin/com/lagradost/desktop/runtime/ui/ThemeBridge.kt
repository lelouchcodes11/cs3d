package com.lagradost.desktop.runtime.ui

import android.content.res.Resources
import android.util.TypedValue
import com.lagradost.desktop.runtime.res.FrameworkResources
import com.lagradost.desktop.runtime.res.ResourceSupport

/**
 * Colors of the current desktop theme, used as defaults for Android widgets and to answer theme
 * attribute queries (?attr/colorPrimary, ?android:attr/textColorPrimary ...). Updated by the app.
 */
object ThemeBridge {
    @Volatile var colorPrimary: Int = 0xFF3D50FA.toInt()
    @Volatile var colorOnPrimary: Int = 0xFFFFFFFF.toInt()
    @Volatile var background: Int = 0xFF111111.toInt()
    @Volatile var surface: Int = 0xFF1C1C1E.toInt()
    @Volatile var surfaceVariant: Int = 0xFF2B2B2E.toInt()
    @Volatile var textColorPrimary: Int = 0xFFE9EAEE.toInt()
    @Volatile var textColorSecondary: Int = 0xFF9BA0A6.toInt()
    @Volatile var isDark: Boolean = true

    /** App defined theme attributes (R.attr ids of the application) */
    val appAttributes = java.util.concurrent.ConcurrentHashMap<Int, () -> Int>()

    private fun color(v: Int): TypedValue = TypedValue().also {
        it.type = TypedValue.TYPE_INT_COLOR_ARGB8
        it.data = v
    }

    /** A dimension in dp, encoded like a compiled resource (TypedValue complex, radix 23p0) */
    private fun dp(v: Int): TypedValue = TypedValue().also {
        it.type = TypedValue.TYPE_DIMENSION
        it.data = (v shl 8) or TypedValue.COMPLEX_UNIT_DIP
    }

    private fun ref(id: Int): TypedValue = TypedValue().also {
        it.type = TypedValue.TYPE_REFERENCE
        it.data = id
        it.resourceId = id
    }

    fun populate(theme: Resources.Theme) {
        theme.putAttribute(FrameworkResources.ATTR_TEXT_COLOR_PRIMARY, color(textColorPrimary))
        theme.putAttribute(FrameworkResources.ATTR_TEXT_COLOR_SECONDARY, color(textColorSecondary))
        theme.putAttribute(FrameworkResources.ATTR_COLOR_BACKGROUND, color(background))
        theme.putAttribute(FrameworkResources.ATTR_COLOR_PRIMARY, color(colorPrimary))
        theme.putAttribute(FrameworkResources.ATTR_COLOR_ACCENT, color(colorPrimary))
        theme.putAttribute(FrameworkResources.ATTR_COLOR_PRIMARY_DARK, color(background))
        putNamed(theme, "colorPrimary", color(colorPrimary))
        putNamed(theme, "colorOnPrimary", color(colorOnPrimary))
        putNamed(theme, "colorSurface", color(surface))
        putNamed(theme, "colorOnSurface", color(textColorPrimary))
        putNamed(theme, "colorSecondary", color(colorPrimary))
        // Material theme metrics used by layouts through ?android:attr/...
        theme.putAttribute(FrameworkResources.ATTR_LIST_PREFERRED_ITEM_PADDING_LEFT, dp(16))
        theme.putAttribute(FrameworkResources.ATTR_LIST_PREFERRED_ITEM_PADDING_RIGHT, dp(16))
        theme.putAttribute(FrameworkResources.ATTR_LIST_PREFERRED_ITEM_PADDING_START, dp(16))
        theme.putAttribute(FrameworkResources.ATTR_LIST_PREFERRED_ITEM_PADDING_END, dp(16))
        theme.putAttribute(FrameworkResources.ATTR_LIST_PREFERRED_ITEM_HEIGHT, dp(64))
        theme.putAttribute(FrameworkResources.ATTR_ACTION_BAR_SIZE, dp(56))
        // android:Theme.Material metrics that only the framework theme defines (styles applied later override them)
        for ((name, value) in listOf(
            "listPreferredItemHeightSmall" to dp(48), "listPreferredItemHeightLarge" to dp(80), "dialogPreferredPadding" to dp(24),
            "listPreferredItemPaddingLeft" to dp(16), "listPreferredItemPaddingRight" to dp(16),
            "listPreferredItemPaddingStart" to dp(16), "listPreferredItemPaddingEnd" to dp(16),
            "listPreferredItemHeight" to dp(64), "actionBarSize" to dp(56),
        )) {
            theme.putAttribute(FrameworkResources.attrId(name), value)
            putNamed(theme, name, value)
        }
        theme.putAttribute(FrameworkResources.ATTR_SELECTABLE_ITEM_BACKGROUND, ref(FrameworkResources.DRAWABLE_SELECTABLE_ITEM_BACKGROUND))
        theme.putAttribute(FrameworkResources.ATTR_SELECTABLE_ITEM_BACKGROUND_BORDERLESS, ref(FrameworkResources.DRAWABLE_SELECTABLE_ITEM_BACKGROUND_BORDERLESS))
        for ((attr, provider) in appAttributes) theme.putAttribute(attr, color(provider()))
    }

    private fun putNamed(theme: Resources.Theme, name: String, value: TypedValue) {
        val id = ResourceSupport.attrId(theme.resources, name)
        if (id != 0) theme.putAttribute(id, value)
    }
}
