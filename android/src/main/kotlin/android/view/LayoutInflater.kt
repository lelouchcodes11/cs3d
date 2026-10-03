package android.view

import android.content.Context
import android.content.res.XmlResourceParser
import android.util.AttributeSet
import android.util.Xml
import com.lagradost.desktop.runtime.ui.WidgetFactory
import org.xmlpull.v1.XmlPullParser

/**
 * Inflates layouts from XML (plain text for app resources, compiled AXML for extension resources)
 * into desktop View instances.
 */
open class LayoutInflater protected constructor(private val mContext: Context) {
    interface Factory {
        fun onCreateView(name: String, context: Context, attrs: AttributeSet): View?
    }

    interface Factory2 : Factory {
        fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View?
    }

    fun interface Filter {
        fun onLoadClass(clazz: Class<*>): Boolean
    }

    private var factory: Factory? = null
    private var factory2: Factory2? = null

    /** Set when a fragment inflates its view, so nested `<fragment>` tags use the child FragmentManager. */
    internal var hostFragment: androidx.fragment.app.Fragment? = null

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        @JvmStatic
        fun from(context: Context?): LayoutInflater {
            val ctx = context ?: com.lagradost.desktop.runtime.AndroidRuntime.applicationContext
            ?: throw AssertionError("LayoutInflater not found.")
            return (ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as? LayoutInflater) ?: LayoutInflater(ctx)
        }

        /** Desktop: create an inflater for a context (used by ContextImpl) */
        @JvmStatic
        fun create(context: Context): LayoutInflater = LayoutInflater(context)
    }

    open fun getContext(): Context = mContext
    open fun cloneInContext(newContext: Context): LayoutInflater = LayoutInflater(newContext).also {
        it.factory = factory
        it.factory2 = factory2
        it.hostFragment = hostFragment
    }

    fun getFactory(): Factory? = factory
    fun getFactory2(): Factory2? = factory2
    fun setFactory(factory: Factory?) {
        this.factory = factory
    }

    fun setFactory2(factory: Factory2?) {
        this.factory2 = factory
        this.factory = factory
    }

    open fun getFilter(): Filter? = null
    open fun setFilter(filter: Filter?) {}

    open fun inflate(resource: Int, root: ViewGroup?): View = inflate(resource, root, root != null)

    open fun inflate(resource: Int, root: ViewGroup?, attachToRoot: Boolean): View {
        val builtin = WidgetFactory.inflateFrameworkLayout(mContext, resource)
        if (builtin != null) {
            if (root != null && attachToRoot) {
                root.addView(builtin)
                return root
            }
            return builtin
        }
        val parser = mContext.resources.getLayout(resource)
        try {
            return inflate(parser, root, attachToRoot)
        } finally {
            parser.close()
        }
    }

    open fun inflate(parser: XmlPullParser, root: ViewGroup?): View = inflate(parser, root, root != null)

    open fun inflate(parser: XmlPullParser, root: ViewGroup?, attachToRoot: Boolean): View {
        val attrs = Xml.asAttributeSet(parser)
        var type: Int
        do {
            type = parser.next()
        } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT)
        if (type != XmlPullParser.START_TAG) throw InflateException(parser.positionDescription + ": No start tag found!")

        val name = parser.name
        if (name == "merge") {
            if (root == null || !attachToRoot) throw InflateException("<merge /> can be used only with a valid ViewGroup root and attachToRoot=true")
            rInflate(parser, root, mContext, attrs)
            return root
        }
        val temp = createViewFromTag(root, name, mContext, attrs)
        var params: ViewGroup.LayoutParams? = null
        if (root != null) {
            params = root.generateLayoutParams(attrs)
            if (!attachToRoot) temp.setLayoutParams(params)
        } else {
            params = WidgetFactory.defaultLayoutParams(mContext, attrs)
            temp.setLayoutParams(params)
        }
        if (temp is ViewGroup) rInflate(parser, temp, mContext, attrs)
        else skipChildren(parser)
        temp.finishInflate()
        if (root != null && attachToRoot) {
            root.addView(temp, params)
            return root
        }
        return temp
    }

    private fun skipChildren(parser: XmlPullParser) {
        val depth = parser.depth
        var type: Int
        while (true) {
            type = parser.next()
            if (type == XmlPullParser.END_DOCUMENT) return
            if (type == XmlPullParser.END_TAG && parser.depth <= depth) return
        }
    }

    private fun rInflate(parser: XmlPullParser, parent: ViewGroup, context: Context, attrs: AttributeSet) {
        val depth = parser.depth
        var type: Int
        while (true) {
            type = parser.next()
            if ((type == XmlPullParser.END_TAG && parser.depth <= depth) || type == XmlPullParser.END_DOCUMENT) break
            if (type != XmlPullParser.START_TAG) continue
            when (val name = parser.name) {
                "requestFocus", "tag" -> skipChildren(parser)
                "include" -> {
                    inflateInclude(parser, parent, attrs)
                }
                "fragment" -> inflateFragmentTag(parent, attrs, parser)
                "merge" -> throw InflateException("<merge /> must be the root element")
                else -> {
                    val view = createViewFromTag(parent, name, context, attrs)
                    val params = parent.generateLayoutParams(attrs)
                    if (view is ViewGroup) rInflate(parser, view, context, attrs) else skipChildren(parser)
                    view.finishInflate()
                    parent.addView(view, params)
                }
            }
        }
    }

    protected open fun onCreateView(name: String, attrs: AttributeSet): View =
        WidgetFactory.createView(mContext, name, attrs) ?: throw InflateException("Binary XML: Error inflating class $name")

    protected open fun onCreateView(parent: View?, name: String, attrs: AttributeSet): View = onCreateView(name, attrs)

    open fun createView(name: String, prefix: String?, attrs: AttributeSet?): View =
        WidgetFactory.createView(mContext, if (prefix != null && !name.contains('.')) prefix + name else name, attrs)
            ?: throw InflateException("Binary XML: Error inflating class $name")

    open fun onCreateView(viewContext: Context, parent: View?, name: String, attrs: AttributeSet?): View? =
        WidgetFactory.createView(viewContext, name, attrs)

    private fun inflateInclude(parser: XmlPullParser, parent: ViewGroup, attrs: AttributeSet) {
        val layout = attrs.getAttributeResourceValue(null, "layout", 0)
        if (layout != 0) {
            val included = inflate(layout, parent, false)
            val idAttr = attrs.getAttributeResourceValue(ANDROID_NS, "id", View.NO_ID)
            if (idAttr != View.NO_ID) included.setId(idAttr)
            applyIncludeOverrides(parent, included, attrs)
            parent.addView(included)
        }
        skipChildren(parser)
    }

    private fun applyIncludeOverrides(parent: ViewGroup, included: View, attrs: AttributeSet) {
        when (attrs.getAttributeValue(ANDROID_NS, "visibility")) {
            "gone", "2" -> included.setVisibility(View.GONE)
            "invisible", "1" -> included.setVisibility(View.INVISIBLE)
            "visible", "0" -> included.setVisibility(View.VISIBLE)
        }
        val names = listOf(
            "layout_width", "layout_height", "layout_margin", "layout_marginLeft", "layout_marginTop",
            "layout_marginRight", "layout_marginBottom", "layout_marginStart", "layout_marginEnd",
        )
        if (names.none { attrs.getAttributeValue(ANDROID_NS, it) != null }) return
        val override = parent.generateLayoutParams(attrs)
        val old = included.getLayoutParams()
        if (attrs.getAttributeValue(ANDROID_NS, "layout_width") == null && old != null) override.width = old.width
        if (attrs.getAttributeValue(ANDROID_NS, "layout_height") == null && old != null) override.height = old.height
        included.setLayoutParams(override)
    }

    private fun inflateFragmentTag(parent: ViewGroup, attrs: AttributeSet, parser: XmlPullParser) {
        val name = attrs.getAttributeValue(ANDROID_NS, "name") ?: attrs.getAttributeValue(null, "class")
        var id = attrs.getAttributeResourceValue(ANDROID_NS, "id", View.NO_ID)
        val tag = attrs.getAttributeValue(ANDROID_NS, "tag")
        val holder = android.widget.FrameLayout(mContext)
        if (id == View.NO_ID) id = View.generateViewId()
        holder.setId(id)
        parent.addView(holder, parent.generateLayoutParams(attrs))
        if (!name.isNullOrEmpty()) {
            val fm = hostFragment?.getChildFragmentManager()
                ?: (mContext.findActivity() as? androidx.fragment.app.FragmentActivity)?.getSupportFragmentManager()
            holder.post {
                if (fm == null || fm.findFragmentById(holder.getId()) != null) return@post
                val fragment = try {
                    androidx.fragment.app.Fragment.instantiate(mContext, name)
                } catch (t: Throwable) {
                    android.util.Log.e("LayoutInflater", "Cannot inflate fragment $name", t)
                    return@post
                }
                fm.beginTransaction().add(holder.getId(), fragment, tag).commit()
            }
        }
        skipChildren(parser)
    }

    private fun Context.findActivity(): android.app.Activity? {
        var c: Context? = this
        while (c != null) {
            if (c is android.app.Activity) return c
            c = (c as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }

    private fun createViewFromTag(parent: View?, name0: String, context: Context, attrs: AttributeSet): View {
        val name = if (name0 == "view") attrs.getAttributeValue(null, "class") ?: name0 else name0
        // Activity.onCreateView is the layout factory. MainActivity uses it to create its view models.
        (context as? android.app.Activity)?.onCreateView(name, context, attrs)?.let { return it }
        factory2?.onCreateView(parent, name, context, attrs)?.let { return it }
        factory?.onCreateView(name, context, attrs)?.let { return it }
        return WidgetFactory.createView(context, name, attrs)
            ?: throw InflateException("Binary XML: Error inflating class $name")
    }
}

class InflateException : RuntimeException {
    constructor() : super()
    constructor(detailMessage: String?) : super(detailMessage)
    constructor(detailMessage: String?, throwable: Throwable?) : super(detailMessage, throwable)
    constructor(throwable: Throwable?) : super(throwable)
}
