@file:JvmName("MenuKt")

package android.view

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

interface MenuItem {
    fun interface OnMenuItemClickListener {
        fun onMenuItemClick(item: MenuItem): Boolean
    }

    fun getItemId(): Int
    fun getGroupId(): Int
    fun getOrder(): Int
    fun setTitle(title: CharSequence?): MenuItem
    fun setTitle(title: Int): MenuItem
    fun getTitle(): CharSequence?
    fun setIcon(icon: Drawable?): MenuItem
    fun setIcon(iconRes: Int): MenuItem
    fun getIcon(): Drawable?
    fun setIntent(intent: Intent?): MenuItem
    fun getIntent(): Intent?
    fun setCheckable(checkable: Boolean): MenuItem
    fun isCheckable(): Boolean
    fun setChecked(checked: Boolean): MenuItem
    fun isChecked(): Boolean
    fun setVisible(visible: Boolean): MenuItem
    fun isVisible(): Boolean
    fun setEnabled(enabled: Boolean): MenuItem
    fun isEnabled(): Boolean
    fun hasSubMenu(): Boolean = false
    fun getSubMenu(): SubMenu? = null
    fun setOnMenuItemClickListener(menuItemClickListener: OnMenuItemClickListener?): MenuItem
    fun setShowAsAction(actionEnum: Int) {}
    fun setActionView(view: View?): MenuItem = this
    fun getActionView(): View? = null
    fun setOnActionExpandListener(listener: OnActionExpandListener?): MenuItem = this
    fun expandActionView(): Boolean = false
    fun collapseActionView(): Boolean = false
    fun isActionViewExpanded(): Boolean = false
    fun setShowAsActionFlags(actionEnum: Int): MenuItem = apply { setShowAsAction(actionEnum) }

    interface OnActionExpandListener {
        fun onMenuItemActionExpand(item: MenuItem): Boolean
        fun onMenuItemActionCollapse(item: MenuItem): Boolean
    }

    companion object {
        const val SHOW_AS_ACTION_NEVER = 0
        const val SHOW_AS_ACTION_IF_ROOM = 1
        const val SHOW_AS_ACTION_ALWAYS = 2
        const val SHOW_AS_ACTION_WITH_TEXT = 4
        const val SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW = 8
    }
}

interface Menu {
    companion object {
        const val NONE = 0
        const val FIRST = 1
        const val USER_MASK = 0x0000ffff
        const val CATEGORY_MASK = -0x10000
        const val CATEGORY_SECONDARY = 0x00030000
    }

    fun add(title: CharSequence?): MenuItem
    fun add(titleRes: Int): MenuItem
    fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence?): MenuItem
    fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int): MenuItem
    fun addSubMenu(title: CharSequence?): SubMenu
    fun removeItem(id: Int)
    fun removeGroup(groupId: Int)
    fun clear()
    fun findItem(id: Int): MenuItem?
    fun size(): Int
    fun getItem(index: Int): MenuItem
    fun hasVisibleItems(): Boolean
    fun close() {}
    fun setGroupVisible(group: Int, visible: Boolean)
    fun setGroupEnabled(group: Int, enabled: Boolean)
    fun setGroupCheckable(group: Int, checkable: Boolean, exclusive: Boolean) {}
}

interface SubMenu : Menu {
    fun setHeaderTitle(title: CharSequence?): SubMenu
    fun getItem(): MenuItem
}

open class MenuItemImpl(
    private val context: Context,
    private val groupId: Int,
    private val itemId: Int,
    private val order: Int,
    private var title: CharSequence?,
) : MenuItem {
    private var icon: Drawable? = null
    private var intent: Intent? = null
    private var checkable = false
    private var checked = false
    private var visible = true
    private var enabled = true
    private var listener: MenuItem.OnMenuItemClickListener? = null
    internal var owner: MenuImpl? = null
    internal var exclusive = false
    private var showAsAction = MenuItem.SHOW_AS_ACTION_NEVER
    private var actionView: View? = null
    private var subMenu: SubMenu? = null

    override fun getItemId(): Int = itemId
    override fun getGroupId(): Int = groupId
    override fun getOrder(): Int = order
    override fun setTitle(title: CharSequence?): MenuItem = apply { this.title = title; owner?.notifyChanged() }
    override fun setTitle(title: Int): MenuItem = setTitle(context.getText(title))
    override fun getTitle(): CharSequence? = title
    override fun setIcon(icon: Drawable?): MenuItem = apply { this.icon = icon; owner?.notifyChanged() }
    override fun setIcon(iconRes: Int): MenuItem = setIcon(if (iconRes == 0) null else context.getDrawable(iconRes))
    override fun getIcon(): Drawable? = icon
    override fun setIntent(intent: Intent?): MenuItem = apply { this.intent = intent }
    override fun getIntent(): Intent? = intent
    override fun setCheckable(checkable: Boolean): MenuItem = apply { this.checkable = checkable }
    override fun isCheckable(): Boolean = checkable
    override fun setChecked(checked: Boolean): MenuItem = apply {
        this.checked = checked
        if (checked && exclusive) owner?.uncheckOthers(this)
        owner?.notifyChanged()
    }
    override fun setShowAsAction(actionEnum: Int) {
        showAsAction = actionEnum
        owner?.notifyChanged()
    }
    fun getShowAsAction(): Int = showAsAction
    override fun setActionView(view: View?): MenuItem = apply { actionView = view; owner?.notifyChanged() }

    private var expandListener: MenuItem.OnActionExpandListener? = null
    private var expanded = false
    override fun setOnActionExpandListener(listener: MenuItem.OnActionExpandListener?): MenuItem = apply { expandListener = listener }
    override fun isActionViewExpanded(): Boolean = expanded
    override fun expandActionView(): Boolean {
        if (expanded || actionView == null || (showAsAction and MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW) == 0) return false
        if (expandListener?.onMenuItemActionExpand(this) == false) return false
        expanded = true
        owner?.notifyChanged()
        return true
    }

    override fun collapseActionView(): Boolean {
        if (!expanded) return false
        if (expandListener?.onMenuItemActionCollapse(this) == false) return false
        expanded = false
        owner?.notifyChanged()
        return true
    }
    override fun getActionView(): View? = actionView
    fun setActionView(resId: Int): MenuItem =
        setActionView(if (resId == 0) null else LayoutInflater.from(context).inflate(resId, null))
    override fun hasSubMenu(): Boolean = subMenu != null
    override fun getSubMenu(): SubMenu? = subMenu
    internal fun attachSubMenu(sub: SubMenu) {
        subMenu = sub
    }
    override fun isChecked(): Boolean = checked
    override fun setVisible(visible: Boolean): MenuItem = apply { this.visible = visible; owner?.notifyChanged() }
    override fun isVisible(): Boolean = visible
    override fun setEnabled(enabled: Boolean): MenuItem = apply { this.enabled = enabled; owner?.notifyChanged() }
    override fun isEnabled(): Boolean = enabled
    override fun setOnMenuItemClickListener(menuItemClickListener: MenuItem.OnMenuItemClickListener?): MenuItem =
        apply { listener = menuItemClickListener }

    fun invokeAction(): Boolean {
        if (listener?.onMenuItemClick(this) == true) return true
        intent?.let {
            context.startActivity(it)
            return true
        }
        return false
    }
}

open class MenuImpl(private val context: Context) : Menu {
    private val items = ArrayList<MenuItemImpl>()

    /** Desktop: the view presenting this menu (Toolbar) rebuilds when an item changes */
    var onChanged: (() -> Unit)? = null
    fun notifyChanged() {
        onChanged?.invoke()
    }

    fun visibleItems(): List<MenuItemImpl> = items.filter { it.isVisible() }.sortedBy { it.getOrder() }

    override fun add(title: CharSequence?): MenuItem = add(0, 0, 0, title)
    override fun add(titleRes: Int): MenuItem = add(0, 0, 0, context.getText(titleRes))
    override fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence?): MenuItem =
        MenuItemImpl(context, groupId, itemId, order, title).also {
            it.owner = this
            items.add(it)
            notifyChanged()
        }

    internal fun uncheckOthers(selected: MenuItemImpl) {
        items.filter { it !== selected && it.getGroupId() == selected.getGroupId() && it.exclusive }.forEach {
            it.setChecked(false)
        }
    }

    override fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int): MenuItem = add(groupId, itemId, order, context.getText(titleRes))
    override fun addSubMenu(title: CharSequence?): SubMenu {
        val item = add(title) as MenuItemImpl
        val sub = object : MenuImpl(context), SubMenu {
            override fun setHeaderTitle(title: CharSequence?): SubMenu = apply { item.setTitle(title) }
            override fun getItem(): MenuItem = item
        }
        item.attachSubMenu(sub)
        return sub
    }

    override fun setGroupCheckable(group: Int, checkable: Boolean, exclusive: Boolean) {
        items.filter { it.getGroupId() == group }.forEach {
            it.setCheckable(checkable)
            it.exclusive = exclusive
        }
    }

    override fun removeItem(id: Int) {
        items.removeAll { it.getItemId() == id }
        notifyChanged()
    }

    override fun removeGroup(groupId: Int) {
        items.removeAll { it.getGroupId() == groupId }
        notifyChanged()
    }

    override fun clear() {
        items.clear()
        notifyChanged()
    }
    override fun findItem(id: Int): MenuItem? = items.firstOrNull { it.getItemId() == id }
    override fun size(): Int = items.size
    override fun getItem(index: Int): MenuItem = items[index]
    override fun hasVisibleItems(): Boolean = items.any { it.isVisible() }
    override fun setGroupVisible(group: Int, visible: Boolean) {
        items.filter { it.getGroupId() == group }.forEach { it.setVisible(visible) }
    }

    override fun setGroupEnabled(group: Int, enabled: Boolean) {
        items.filter { it.getGroupId() == group }.forEach { it.setEnabled(enabled) }
    }
}

open class MenuInflater(private val context: Context) {
    open fun inflate(menuRes: Int, menu: Menu) {
        val parser = context.resources.getXml(menuRes)
        try {
            var groupId = 0
            var groupCheckable = 0
            var groupVisible = true
            var groupEnabled = true
            val stack = ArrayList<Menu>()
            stack.add(menu)
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    when (name) {
                        "group" -> {
                            groupId = resId(attr(parser, "id"))
                            groupVisible = bool(attr(parser, "visible"), true)
                            groupEnabled = bool(attr(parser, "enabled"), true)
                            groupCheckable = when (attr(parser, "checkableBehavior")) {
                                "all" -> 1
                                "single" -> 2
                                else -> 0
                            }
                        }
                        "item" -> {
                            val item = stack.last().add(
                                groupId,
                                resId(attr(parser, "id")),
                                intOf(attr(parser, "orderInCategory")),
                                text(attr(parser, "title")),
                            )
                            item.setVisible(bool(attr(parser, "visible"), groupVisible))
                            item.setEnabled(bool(attr(parser, "enabled"), groupEnabled))
                            val checkable = bool(attr(parser, "checkable"), groupCheckable != 0)
                            item.setCheckable(checkable)
                            if (groupCheckable == 2 && item is MenuItemImpl) item.exclusive = true
                            if (bool(attr(parser, "checked"), false)) item.setChecked(true)
                            drawable(attr(parser, "icon"))?.let { item.setIcon(it) }
                            showAsAction(attr(parser, "showAsAction"))?.let { item.setShowAsAction(it) }
                            val actionLayout = resId(attr(parser, "actionLayout"))
                            if (actionLayout != 0 && item is MenuItemImpl) item.setActionView(actionLayout)
                            attr(parser, "actionViewClass")?.let { cls ->
                                try {
                                    item.setActionView(Class.forName(cls).getConstructor(Context::class.java).newInstance(context) as View)
                                } catch (e: Exception) {
                                    android.util.Log.e("MenuInflater", "actionViewClass $cls", e)
                                }
                            }
                        }
                        "menu" -> if (parser.depth > 1 && stack.last().size() > 0) {
                            val header = stack.last().getItem(stack.last().size() - 1)
                            val sub = if (header is MenuItemImpl) {
                                val created = object : MenuImpl(context), SubMenu {
                                    override fun setHeaderTitle(title: CharSequence?): SubMenu = apply { header.setTitle(title) }
                                    override fun getItem(): MenuItem = header
                                }
                                header.attachSubMenu(created)
                                created
                            } else {
                                stack.last().addSubMenu(header.getTitle())
                            }
                            stack.add(sub)
                        }
                    }
                } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    when (name) {
                        "group" -> {
                            groupId = 0
                            groupCheckable = 0
                            groupVisible = true
                            groupEnabled = true
                        }
                        "menu" -> if (stack.size > 1) stack.removeAt(stack.size - 1)
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
    }

    private fun attr(parser: org.xmlpull.v1.XmlPullParser, name: String): String? {
        parser.getAttributeValue(ANDROID_NS, name)?.let { return it }
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun resId(raw: String?): Int {
        if (raw.isNullOrEmpty() || raw[0] != '@') return 0
        return context.resources.getIdentifier(raw.removePrefix("@").removePrefix("+"), null, null)
    }

    private fun text(raw: String?): CharSequence? {
        if (raw == null) return null
        val id = resId(raw)
        if (id != 0) return try {
            context.resources.getText(id)
        } catch (_: Exception) {
            raw
        }
        return raw
    }

    private fun drawable(raw: String?): android.graphics.drawable.Drawable? {
        val id = resId(raw)
        if (id == 0) return null
        return try {
            context.getDrawable(id)
        } catch (_: Exception) {
            null
        }
    }

    private fun bool(raw: String?, default: Boolean): Boolean = when (raw) {
        null -> default
        "true", "1" -> true
        "false", "0" -> false
        else -> default
    }

    private fun intOf(raw: String?): Int = raw?.toIntOrNull() ?: 0

    private fun showAsAction(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        var flags = 0
        for (part in raw.split('|')) {
            flags = flags or when (part.trim()) {
                "never", "0" -> MenuItem.SHOW_AS_ACTION_NEVER
                "ifRoom", "1" -> MenuItem.SHOW_AS_ACTION_IF_ROOM
                "always", "2" -> MenuItem.SHOW_AS_ACTION_ALWAYS
                "withText", "4" -> MenuItem.SHOW_AS_ACTION_WITH_TEXT
                "collapseActionView", "8" -> MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW
                else -> 0
            }
        }
        return flags
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
