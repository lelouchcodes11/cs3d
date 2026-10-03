package com.lagradost.desktop.runtime.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.desktop.runtime.res.FrameworkResources
import java.util.concurrent.ConcurrentHashMap

/** Creates views for layout XML tags */
object WidgetFactory {
    private val constructors = ConcurrentHashMap<String, java.lang.reflect.Constructor<*>?>()

    /** Extra class loaders (extensions) consulted for custom view classes */
    val extraClassLoaders = java.util.concurrent.CopyOnWriteArrayList<ClassLoader>()

    private val aliases = mapOf(
        // Not implemented layouts are approximated with the closest implemented one
        "com.google.android.material.checkbox.MaterialCheckBox" to "android.widget.CheckBox",
        "com.google.android.material.radiobutton.MaterialRadioButton" to "android.widget.RadioButton",
        "com.google.android.material.textview.MaterialTextView" to "android.widget.TextView",
        "androidx.appcompat.widget.AppCompatAutoCompleteTextView" to "android.widget.AutoCompleteTextView",
        "androidx.appcompat.widget.AppCompatSeekBar" to "android.widget.SeekBar",
        "androidx.appcompat.widget.AppCompatToggleButton" to "android.widget.ToggleButton",
        "androidx.core.widget.NestedScrollView" to "androidx.core.widget.NestedScrollView",
        "WebView" to "android.webkit.WebView",
        "View" to "android.view.View",

        "SurfaceView" to "android.view.View",
        "TextureView" to "android.view.View",
        "Space" to "android.widget.Space",
        // Names view binding has to mention before the real widget exists. Inflation stays on the
        // stand-in. Remove the line when that class is implemented.


    )

    private fun classFor(name: String): Class<*>? {
        val candidates = if (name.contains('.')) listOf(aliases[name] ?: name, name)
        else listOf(aliases[name] ?: "android.widget.$name", "android.widget.$name", "android.view.$name", "android.webkit.$name")
        for (c in candidates.distinct()) {
            try {
                return Class.forName(c, true, WidgetFactory::class.java.classLoader)
            } catch (_: Throwable) {
            }
            for (cl in extraClassLoaders) {
                try {
                    return Class.forName(c, true, cl)
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    @JvmStatic
    fun createView(context: Context, name: String, attrs: AttributeSet?): View? {
        val ctor = constructors.getOrPut(name) {
            val cls = classFor(name)
            if (cls == null || !View::class.java.isAssignableFrom(cls)) {
                android.util.Log.w("LayoutInflater", "Unknown view class $name, using FrameLayout")
                FrameLayout::class.java.getConstructor(Context::class.java, AttributeSet::class.java)
            } else try {
                cls.getConstructor(Context::class.java, AttributeSet::class.java)
            } catch (e: NoSuchMethodException) {
                null
            }
        } ?: return null
        return try {
            ctor.newInstance(context, attrs) as View
        } catch (t: java.lang.reflect.InvocationTargetException) {
            throw t.targetException
        }
    }

    @JvmStatic
    fun defaultLayoutParams(context: Context, attrs: AttributeSet?): ViewGroup.LayoutParams =
        ViewGroup.LayoutParams(context, attrs).also {
            if (attrs == null) {
                it.width = ViewGroup.LayoutParams.MATCH_PARENT
                it.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

    private fun dp(context: Context, v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    /** Framework layouts (android.R.layout.*) built in code */
    @JvmStatic
    fun inflateFrameworkLayout(context: Context, resId: Int): View? {
        return when (resId) {
            FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_1, FrameworkResources.LAYOUT_SELECT_DIALOG_ITEM, FrameworkResources.LAYOUT_SIMPLE_SELECTABLE_LIST_ITEM,
            FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_ACTIVATED_1, FrameworkResources.LAYOUT_ACTIVITY_LIST_ITEM -> TextView(context).apply {
                setId(FrameworkResources.ID_TEXT1)
                setTextSize(16f)
                setGravity(Gravity.CENTER_VERTICAL)
                setMinimumHeight(dp(context, 48f))
                setPadding(dp(context, 16f), dp(context, 8f), dp(context, 16f), dp(context, 8f))
                setLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            FrameworkResources.LAYOUT_SIMPLE_SPINNER_ITEM, FrameworkResources.LAYOUT_SIMPLE_DROPDOWN_ITEM_1LINE -> TextView(context).apply {
                setId(FrameworkResources.ID_TEXT1)
                setTextSize(15f)
                setSingleLine(true)
                setEllipsize(android.text.TextUtils.TruncateAt.END)
                setLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            FrameworkResources.LAYOUT_SIMPLE_SPINNER_DROPDOWN_ITEM -> CheckedTextView(context).apply {
                setId(FrameworkResources.ID_TEXT1)
                setTextSize(15f)
                setSingleLine(true)
                setGravity(Gravity.CENTER_VERTICAL)
                setMinimumHeight(dp(context, 44f))
                setPadding(dp(context, 16f), 0, dp(context, 16f), 0)
                setLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_CHECKED, FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_SINGLE_CHOICE,
            FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_MULTIPLE_CHOICE, FrameworkResources.LAYOUT_SELECT_DIALOG_SINGLECHOICE,
            FrameworkResources.LAYOUT_SELECT_DIALOG_MULTICHOICE -> CheckedTextView(context).apply {
                setId(FrameworkResources.ID_TEXT1)
                checkMarkType = when (resId) {
                    FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_SINGLE_CHOICE, FrameworkResources.LAYOUT_SELECT_DIALOG_SINGLECHOICE -> CheckedTextView.CHECK_MARK_SINGLE
                    FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_MULTIPLE_CHOICE, FrameworkResources.LAYOUT_SELECT_DIALOG_MULTICHOICE -> CheckedTextView.CHECK_MARK_MULTIPLE
                    else -> CheckedTextView.CHECK_MARK_CHECK
                }
                setTextSize(16f)
                setGravity(Gravity.CENTER_VERTICAL)
                setMinimumHeight(dp(context, 48f))
                setPadding(dp(context, 16f), 0, dp(context, 16f), 0)
                setLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_2, FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_ACTIVATED_2 -> LinearLayout(context).apply {
                setOrientation(LinearLayout.VERTICAL)
                setPadding(dp(context, 16f), dp(context, 8f), dp(context, 16f), dp(context, 8f))
                addView(TextView(context).apply { setId(FrameworkResources.ID_TEXT1); setTextSize(16f) })
                addView(TextView(context).apply {
                    setId(FrameworkResources.ID_TEXT2)
                    setTextSize(14f)
                    setTextColor(ThemeBridge.textColorSecondary)
                })
                setLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            else -> null
        }
    }
}
