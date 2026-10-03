package androidx.navigation

import android.content.Context
import android.os.Bundle

open class NavDestination(
    val id: Int,
    val navigatorName: String,
    val className: String?,
    val label: String?,
) {
    var parent: NavDestination? = null
    val actions = LinkedHashMap<Int, NavAction>()
    val arguments = LinkedHashMap<String, NavArgument>()

    companion object {
        /** Imported by upstream as `NavDestination.Companion.hierarchy`. */
        val NavDestination.hierarchy: Sequence<NavDestination>
            get() = generateSequence(this) { it.parent }
    }
}

open class NavGraph(
    id: Int,
    label: String?,
) : NavDestination(id, "navigation", null, label) {
    var startDestinationId: Int = 0
    val nodes = LinkedHashMap<Int, NavDestination>()

    fun findNode(id: Int): NavDestination? = if (id == this.id) this else nodes[id] ?: nodes.values.filterIsInstance<NavGraph>().firstNotNullOfOrNull { it.findNode(id) }

    companion object {
        /** Imported by upstream as `NavGraph.Companion.findStartDestination`. */
        fun NavGraph.findStartDestination(): NavDestination {
            var dest = findNode(startDestinationId)
            while (dest is NavGraph) dest = dest.findNode(dest.startDestinationId)
            return dest ?: throw IllegalStateException("Graph $id has no start destination")
        }
    }
}

class NavAction(
    val id: Int,
    val destinationId: Int,
    val popUpTo: Int,
    val inclusive: Boolean,
    val singleTop: Boolean,
)

class NavArgument(val name: String, val type: String, val defaultValue: String?, val nullable: Boolean)

class NavBackStackEntry(val destination: NavDestination, val arguments: Bundle)

class NavOptions {
    var popUpToId: Int = 0
    var popUpToInclusive: Boolean = false
    var launchSingleTop: Boolean = false
    var enterAnim: Int = 0
    var exitAnim: Int = 0
    var popEnterAnim: Int = 0
    var popExitAnim: Int = 0

    class Builder {
        private val options = NavOptions()
        fun setPopUpTo(destinationId: Int, inclusive: Boolean): Builder = apply {
            options.popUpToId = destinationId
            options.popUpToInclusive = inclusive
        }
        fun setPopUpTo(destinationId: Int, inclusive: Boolean, saveState: Boolean): Builder =
            setPopUpTo(destinationId, inclusive)
        fun setLaunchSingleTop(singleTop: Boolean): Builder = apply { options.launchSingleTop = singleTop }
        fun setEnterAnim(anim: Int): Builder = apply { options.enterAnim = anim }
        fun setExitAnim(anim: Int): Builder = apply { options.exitAnim = anim }
        fun setPopEnterAnim(anim: Int): Builder = apply { options.popEnterAnim = anim }
        fun setPopExitAnim(anim: Int): Builder = apply { options.popExitAnim = anim }
        fun setRestoreState(restoreState: Boolean): Builder = this
        fun build(): NavOptions = options
    }
}

fun navOptions(builder: NavOptions.Builder.() -> Unit): NavOptions = NavOptions.Builder().apply(builder).build()

fun interface OnDestinationChangedListener {
    fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?)
}

open class NavController(val context: Context) {
    var graph: NavGraph? = null
        private set
    private val backStack = ArrayList<NavBackStackEntry>()
    private val listeners = ArrayList<OnDestinationChangedListener>()
    var host: ((NavBackStackEntry) -> Unit)? = null

    /** Desktop diagnostics: the back stack from the root */
    fun backStackSnapshot(): List<NavBackStackEntry> = backStack.toList()
    val currentBackStackEntry: NavBackStackEntry? get() = backStack.lastOrNull()
    val previousBackStackEntry: NavBackStackEntry? get() = backStack.getOrNull(backStack.size - 2)
    val currentDestination: NavDestination? get() = currentBackStackEntry?.destination

    fun setGraph(graphResId: Int) {
        val parser = context.resources.getXml(graphResId)
        try {
            graph = NavInflater.inflate(context, parser)
        } finally {
            parser.close()
        }
        val start = graph?.findNode(graph?.startDestinationId ?: 0) ?: return
        backStack.clear()
        push(start, bundleFor(start, null), fromUser = false)
    }

    fun navigate(resId: Int) = navigate(resId, null, null)

    fun navigate(resId: Int, args: Bundle?) = navigate(resId, args, null)

    fun navigate(resId: Int, args: Bundle?, navOptions: NavOptions?) = navigate(resId, args, navOptions, null)

    fun navigate(resId: Int, args: Bundle?, navOptions: NavOptions?, navigatorExtras: Any?) {
        val graph = graph ?: return
        val action = findAction(graph, resId)
        val options = navOptions ?: action?.let {
            NavOptions().also { o ->
                o.popUpToId = it.popUpTo
                o.popUpToInclusive = it.inclusive
                o.launchSingleTop = it.singleTop
            }
        }
        val destId = action?.destinationId ?: resId
        val dest = graph.findNode(destId) ?: return
        if (options != null && options.popUpToId != 0) popTo(options.popUpToId, options.popUpToInclusive)
        if (options?.launchSingleTop == true && currentDestination?.id == dest.id) {
            currentBackStackEntry?.arguments?.putAll(bundleFor(dest, args))
            dispatch()
            return
        }
        push(dest, bundleFor(dest, args), fromUser = true)
    }

    fun popBackStack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.size - 1)
        dispatch()
        return true
    }

    fun popBackStack(destinationId: Int, inclusive: Boolean): Boolean = popTo(destinationId, inclusive).also { if (it) dispatch() }

    fun navigateUp(): Boolean = popBackStack()

    fun addOnDestinationChangedListener(listener: OnDestinationChangedListener) {
        listeners.add(listener)
        currentBackStackEntry?.let { listener.onDestinationChanged(this, it.destination, it.arguments) }
    }

    fun removeOnDestinationChangedListener(listener: OnDestinationChangedListener) {
        listeners.remove(listener)
    }

    private fun push(dest: NavDestination, args: Bundle, fromUser: Boolean) {
        val entry = NavBackStackEntry(dest, args)
        backStack.add(entry)
        dispatch()
    }

    private fun dispatch() {
        val entry = currentBackStackEntry ?: return
        listeners.toList().forEach { it.onDestinationChanged(this, entry.destination, entry.arguments) }
        host?.invoke(entry)
    }

    private fun popTo(destinationId: Int, inclusive: Boolean): Boolean {
        val index = backStack.indexOfLast { it.destination.id == destinationId }
        if (index < 0) return false
        val keep = if (inclusive) index else index + 1
        while (backStack.size > keep) backStack.removeAt(backStack.size - 1)
        return true
    }

    private fun findAction(graph: NavGraph, id: Int): NavAction? {
        graph.actions[id]?.let { return it }
        graph.nodes.values.forEach { node ->
            node.actions[id]?.let { return it }
            if (node is NavGraph) findAction(node, id)?.let { return it }
        }
        return null
    }

    private fun bundleFor(dest: NavDestination, args: Bundle?): Bundle {
        val bundle = Bundle()
        for ((name, arg) in dest.arguments) {
            val value = arg.defaultValue ?: continue
            when (arg.type) {
                "integer" -> bundle.putInt(name, value.toIntOrNull() ?: 0)
                "long" -> bundle.putLong(name, value.removeSuffix("L").toLongOrNull() ?: 0L)
                "boolean" -> bundle.putBoolean(name, value == "true")
                "float" -> bundle.putFloat(name, value.toFloatOrNull() ?: 0f)
                else -> if (value != "@null") bundle.putString(name, value)
            }
        }
        if (args != null) bundle.putAll(args)
        return bundle
    }
}

private object NavInflater {
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private const val APP = "http://schemas.android.com/apk/res-auto"

    fun inflate(context: Context, parser: org.xmlpull.v1.XmlPullParser): NavGraph {
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.START_TAG && event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
        val graph = readDestination(context, parser, parser.name) as NavGraph
        return graph
    }

    private fun readDestination(context: Context, parser: org.xmlpull.v1.XmlPullParser, tag: String): NavDestination {
        val id = res(context, attr(parser, "id"))
        val name = attr(parser, "name")
        val label = text(context, attr(parser, "label"))
        val dest: NavDestination = if (tag == "navigation") NavGraph(id, label) else NavDestination(id, tag, name, label)
        if (dest is NavGraph) {
            dest.startDestinationId = res(context, attr(parser, "startDestination"))
        }
        val depth = parser.depth
        var event = parser.next()
        while (!(event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth) && event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (parser.name) {
                    "argument" -> dest.arguments[attr(parser, "name") ?: ""] = NavArgument(
                        attr(parser, "name") ?: "",
                        attr(parser, "argType") ?: "string",
                        attr(parser, "defaultValue"),
                        attr(parser, "nullable") == "true",
                    )
                    "action" -> {
                        val actionId = res(context, attr(parser, "id"))
                        dest.actions[actionId] = NavAction(
                            actionId,
                            res(context, attr(parser, "destination")),
                            res(context, attr(parser, "popUpTo")),
                            attr(parser, "popUpToInclusive") == "true",
                            attr(parser, "launchSingleTop") == "true",
                        )
                        // Arguments nested inside an action belong to the action, not this destination.
                        skipRest(parser)
                    }
                    "fragment", "activity", "dialog", "navigation" -> {
                        val child = readDestination(context, parser, parser.name)
                        child.parent = dest
                        if (dest is NavGraph) dest.nodes[child.id] = child
                    }
                }
            }
            event = parser.next()
        }
        return dest
    }

    /** Parser is on a start tag. Advance until that element's end tag. */
    private fun skipRest(parser: org.xmlpull.v1.XmlPullParser) {
        val depth = parser.depth
        var event = parser.next()
        while (!(event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth) && event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            event = parser.next()
        }
    }

    private fun attr(parser: org.xmlpull.v1.XmlPullParser, name: String): String? {
        parser.getAttributeValue(ANDROID, name)?.let { return it }
        parser.getAttributeValue(APP, name)?.let { return it }
        for (i in 0 until parser.attributeCount) if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        return null
    }

    private fun res(context: Context, raw: String?): Int {
        if (raw.isNullOrEmpty()) return 0
        if (raw.startsWith("@")) {
            val id = context.resources.getIdentifier(raw.removePrefix("@").removePrefix("+"), null, null)
            if (id != 0) return id
        }
        return raw.toIntOrNull() ?: 0
    }

    private fun text(context: Context, raw: String?): String? {
        if (raw == null) return null
        val id = res(context, raw)
        if (id != 0 && raw.startsWith("@")) {
            return try { context.resources.getString(id) } catch (_: Exception) { raw }
        }
        return raw
    }
}
