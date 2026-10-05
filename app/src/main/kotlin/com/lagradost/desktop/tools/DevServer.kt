package com.lagradost.desktop.tools

import com.lagradost.desktop.runtime.LogBuffer
import kotlinx.coroutines.async
import okhttp3.Request
import com.lagradost.desktop.ui.BackHandlers
import com.lagradost.desktop.ui.DesktopUiHost

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Component
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Development only (started with -Dcloudstream.devport=<port>, bound to 127.0.0.1): lets tooling
 * look at and drive the real window without bringing it to the front, to test the UI end to end.
 * Coordinates are window content pixels (the screenshot's pixels).
 *
 * GET /screenshot                                   PNG of the window content (PrintWindow)
 * GET /click?x=&y=[&button=right][&count=2]         GET /move?x=&y=
 * GET /scroll?x=&y=&amount=  (positive = down)      GET /drag?x1=&y1=&x2=&y2=
 * GET /key?name=ESCAPE|ENTER|TAB|BACK_SPACE|DOWN|...[&ctrl=1&shift=1&alt=1]
 * GET /type?text=...                                GET /navigate?id=navigation_home
 * GET /back   GET /state   GET /log?lines=200
 */
object DevServer {
    fun start(port: Int) {
        val server = try {
            HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        } catch (e: java.io.IOException) {
            System.err.println("Dev server not started, port $port: $e")
            return
        }
        server.createContext("/") { ex ->
            try {
                handle(ex)
            } catch (t: Throwable) {
                respond(ex, 500, "text/plain", t.stackTraceToString().toByteArray())
            }
        }
        server.executor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "dev-server").also { it.isDaemon = true } }
        server.start()
        println("Dev server on http://127.0.0.1:$port")
    }

    private fun findWebView(view: android.view.View): android.webkit.WebView? {
        if (view is android.webkit.WebView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.getChildCount()) findWebView(view.getChildAt(i) ?: continue)?.let { return it }
        }
        return null
    }

    private fun query(ex: HttpExchange): Map<String, String> =
        (ex.requestURI.rawQuery ?: "").split('&').filter { it.contains('=') }.associate {
            val (k, v) = it.split('=', limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    private fun respond(ex: HttpExchange, code: Int, type: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    /** MainActivity's NavController (nav_host_fragment), if MainActivity is created */
    private fun mainNavController(): androidx.navigation.NavController? {
        val act = com.lagradost.desktop.DesktopBootstrap.activityOrNull() ?: return null
        val host = act.getSupportFragmentManager().findFragmentById(com.lagradost.cloudstream3.R.id.nav_host_fragment)
        return (host as? androidx.navigation.fragment.NavHostFragment)?.navController
    }

    private fun ok(ex: HttpExchange, text: String = "ok") = respond(ex, 200, "text/plain; charset=utf-8", text.toByteArray())

    private fun window(): Frame = DesktopUiHost.window ?: error("no window")

    private fun content(): Component = (window() as? JFrame)?.contentPane ?: window()

    /** Window pixels per AWT user space unit */
    private fun scale(): Double = window().graphicsConfiguration.defaultTransform.scaleX

    private fun onEdt(block: () -> Unit) = EventQueue.invokeAndWait(block)

    // ---------------------------------------------------------------------------------------------
    // Capture: PrintWindow renders the window even when other windows cover it

    private fun capture(): BufferedImage {
        val frame = window()
        val hwnd = WinDef.HWND(Native.getComponentPointer(frame))
        val rect = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hwnd, rect)
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        val hdcWindow = User32.INSTANCE.GetDC(hwnd)
        val hdcMem = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow)
        val bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, w, h)
        val old = GDI32.INSTANCE.SelectObject(hdcMem, bitmap)
        try {
            User32.INSTANCE.PrintWindow(hwnd, hdcMem, 2) // PW_RENDERFULLCONTENT
            val bmi = WinGDI.BITMAPINFO()
            bmi.bmiHeader.biWidth = w
            bmi.bmiHeader.biHeight = -h
            bmi.bmiHeader.biPlanes = 1
            bmi.bmiHeader.biBitCount = 32
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB
            val buffer = Memory(w.toLong() * h * 4)
            GDI32.INSTANCE.GetDIBits(hdcWindow, bitmap, 0, h, buffer, bmi, WinGDI.DIB_RGB_COLORS)
            val full = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            full.setRGB(0, 0, w, h, buffer.getIntArray(0, w * h), 0, w)
            // crop to the content pane
            val s = scale()
            val origin = SwingUtilities.convertPoint(content(), 0, 0, frame)
            val cx = (origin.x * s).toInt().coerceIn(0, w - 1)
            val cy = (origin.y * s).toInt().coerceIn(0, h - 1)
            val cw = (content().width * s).toInt().coerceAtMost(w - cx)
            val ch = (content().height * s).toInt().coerceAtMost(h - cy)
            return full.getSubimage(cx, cy, cw, ch)
        } finally {
            GDI32.INSTANCE.SelectObject(hdcMem, old)
            GDI32.INSTANCE.DeleteObject(bitmap)
            GDI32.INSTANCE.DeleteDC(hdcMem)
            User32.INSTANCE.ReleaseDC(hwnd, hdcWindow)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Input: AWT events dispatched to the component under the point (no focus stealing)

    private fun target(x: Int, y: Int): Pair<Component, Point> {
        val s = scale()
        val ux = (x / s).toInt()
        val uy = (y / s).toInt()
        val root = content()
        val comp = SwingUtilities.getDeepestComponentAt(root, ux, uy) ?: root
        return comp to SwingUtilities.convertPoint(root, ux, uy, comp)
    }

    private fun mouse(comp: Component, id: Int, p: Point, modifiers: Int, clickCount: Int, button: Int) {
        comp.dispatchEvent(MouseEvent(comp, id, System.currentTimeMillis(), modifiers, p.x, p.y, clickCount, false, button))
    }

    private fun click(x: Int, y: Int, right: Boolean, count: Int) = onEdt {
        val (comp, p) = target(x, y)
        val button = if (right) MouseEvent.BUTTON3 else MouseEvent.BUTTON1
        val mask = if (right) InputEvent.BUTTON3_DOWN_MASK else InputEvent.BUTTON1_DOWN_MASK
        mouse(comp, MouseEvent.MOUSE_MOVED, p, 0, 0, MouseEvent.NOBUTTON)
        for (i in 1..count) {
            mouse(comp, MouseEvent.MOUSE_PRESSED, p, mask, i, button)
            mouse(comp, MouseEvent.MOUSE_RELEASED, p, 0, i, button)
            mouse(comp, MouseEvent.MOUSE_CLICKED, p, 0, i, button)
        }
    }

    private fun keyTarget(): Component {
        val w = window()
        return w.mostRecentFocusOwner ?: target(content().width / 2, content().height / 2).first
    }

    private fun key(code: Int, modifiers: Int, char: Char = KeyEvent.CHAR_UNDEFINED) = onEdt {
        val comp = keyTarget()
        val now = System.currentTimeMillis()
        comp.dispatchEvent(KeyEvent(comp, KeyEvent.KEY_PRESSED, now, modifiers, code, char))
        if (char != KeyEvent.CHAR_UNDEFINED) comp.dispatchEvent(KeyEvent(comp, KeyEvent.KEY_TYPED, now, modifiers, KeyEvent.VK_UNDEFINED, char))
        comp.dispatchEvent(KeyEvent(comp, KeyEvent.KEY_RELEASED, now, modifiers, code, char))
    }

    private fun dim(v: Int) = when (v) {
        -1 -> "MATCH"
        -2 -> "WRAP"
        else -> v.toString()
    }

    private fun dumpView(v: android.view.View, depth: Int, sb: StringBuilder) {
        val ctx = v.getContext()
        val idName = if (v.getId() != android.view.View.NO_ID) runCatching { ctx.resources.getResourceEntryName(v.getId()) }.getOrDefault("0x" + Integer.toHexString(v.getId())) else ""
        sb.append("  ".repeat(depth)).append(v.javaClass.simpleName)
        if (idName.isNotEmpty()) sb.append(" #").append(idName)
        val lp = v.getLayoutParams()
        if (lp != null) {
            sb.append(" lp=").append(dim(lp.width)).append('x').append(dim(lp.height))
            (lp as? android.view.ViewGroup.MarginLayoutParams)?.let {
                if (it.leftMargin or it.topMargin or it.rightMargin or it.bottomMargin != 0) sb.append(" m=").append(it.leftMargin).append(',').append(it.topMargin).append(',').append(it.rightMargin).append(',').append(it.bottomMargin)
            }
            (lp as? android.widget.LinearLayout.LayoutParams)?.let { if (it.weight != 0f) sb.append(" weight=").append(it.weight); if (it.gravity != -1) sb.append(" lg=0x").append(Integer.toHexString(it.gravity)) }
        }
        if (v.getPaddingLeft() or v.getPaddingTop() or v.getPaddingRight() or v.getPaddingBottom() != 0) {
            sb.append(" p=").append(v.getPaddingLeft()).append(',').append(v.getPaddingTop()).append(',').append(v.getPaddingRight()).append(',').append(v.getPaddingBottom())
        }
        if (v.getVisibility() != android.view.View.VISIBLE) sb.append(" vis=").append(v.getVisibility())
        sb.append(" @").append(v.getLeft()).append(',').append(v.getTop()).append(' ').append(v.getWidth()).append('x').append(v.getHeight())
        if (v is android.widget.LinearLayout) sb.append(if (v.getOrientation() == 1) " vertical" else " horizontal").append(" g=0x").append(Integer.toHexString(v.getGravity()))
        if (v is androidx.recyclerview.widget.RecyclerView) sb.append(" items=").append(v.getAdapter()?.getItemCount() ?: -1).append(" children=").append(v.getChildCount()).append(" lm=").append(v.getLayoutManager()?.javaClass?.simpleName).append(" adapter=").append(v.getAdapter()?.javaClass?.simpleName)
        if (v is android.widget.AdapterView<*>) sb.append(" items=").append(v.getAdapter()?.getCount() ?: -1)
        if (v is android.widget.TextView) {
            sb.append(" text=\"").append(v.getText().toString().take(40)).append('"')
            v.getHint()?.let { sb.append(" hint=\"").append(it).append('"') }
            sb.append(" size=").append(v.getTextSize()).append(" color=#").append(Integer.toHexString(v.getCurrentTextColor()))
            sb.append(" caps=").append(v.isAllCaps()).append(" gravity=0x").append(Integer.toHexString(v.getGravity()))
        }
        v.getBackground()?.let { bg ->
            sb.append(" bg=").append(bg.javaClass.simpleName)
            (bg as? android.graphics.drawable.ColorDrawable)?.let { sb.append("#").append(Integer.toHexString(it.getColor())) }
            (bg as? android.graphics.drawable.GradientDrawable)?.getColor()?.let { sb.append("#").append(Integer.toHexString(it.defaultColor)) }
        }
        v.getBackgroundTintList()?.let { sb.append(" tint=#").append(Integer.toHexString(it.defaultColor)) }
        sb.append('\n')
        if (v is android.view.ViewGroup) for (c in v.children.toList()) dumpView(c, depth + 1, sb)
    }

    private fun handle(ex: HttpExchange) {
        val q = query(ex)
        when (ex.requestURI.path) {
            "/screenshot" -> {
                val out = ByteArrayOutputStream()
                ImageIO.write(capture(), "png", out)
                respond(ex, 200, "image/png", out.toByteArray())
            }
            "/click" -> {
                click(q["x"]!!.toInt(), q["y"]!!.toInt(), q["button"] == "right", q["count"]?.toInt() ?: 1)
                ok(ex)
            }
            "/move" -> {
                onEdt {
                    val (comp, p) = target(q["x"]!!.toInt(), q["y"]!!.toInt())
                    mouse(comp, MouseEvent.MOUSE_MOVED, p, 0, 0, MouseEvent.NOBUTTON)
                }
                ok(ex)
            }
            "/scroll" -> {
                onEdt {
                    val (comp, p) = target(q["x"]!!.toInt(), q["y"]!!.toInt())
                    val amount = q["amount"]?.toInt() ?: 3
                    comp.dispatchEvent(
                        MouseWheelEvent(comp, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, p.x, p.y, 0, false,
                            MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, amount)
                    )
                }
                ok(ex)
            }
            "/drag" -> {
                onEdt {
                    val (comp, p1) = target(q["x1"]!!.toInt(), q["y1"]!!.toInt())
                    val (_, p2raw) = target(q["x2"]!!.toInt(), q["y2"]!!.toInt())
                    val p2 = SwingUtilities.convertPoint(content(), (q["x2"]!!.toInt() / scale()).toInt(), (q["y2"]!!.toInt() / scale()).toInt(), comp)
                    mouse(comp, MouseEvent.MOUSE_PRESSED, p1, InputEvent.BUTTON1_DOWN_MASK, 1, MouseEvent.BUTTON1)
                    for (i in 1..10) {
                        val p = Point(p1.x + (p2.x - p1.x) * i / 10, p1.y + (p2.y - p1.y) * i / 10)
                        mouse(comp, MouseEvent.MOUSE_DRAGGED, p, InputEvent.BUTTON1_DOWN_MASK, 0, MouseEvent.NOBUTTON)
                    }
                    mouse(comp, MouseEvent.MOUSE_RELEASED, p2, 0, 1, MouseEvent.BUTTON1)
                    @Suppress("UNUSED_VARIABLE") val unused = p2raw
                }
                ok(ex)
            }
            "/key" -> {
                val code = KeyEvent::class.java.getField("VK_" + q["name"]!!.uppercase()).getInt(null)
                var modifiers = 0
                if (q["ctrl"] == "1") modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
                if (q["shift"] == "1") modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK
                if (q["alt"] == "1") modifiers = modifiers or InputEvent.ALT_DOWN_MASK
                val char = when (code) {
                    KeyEvent.VK_ENTER -> '\n'
                    KeyEvent.VK_BACK_SPACE -> '\b'
                    KeyEvent.VK_TAB -> '\t'
                    else -> KeyEvent.CHAR_UNDEFINED
                }
                key(code, modifiers, char)
                ok(ex)
            }
            "/type" -> {
                for (c in q["text"] ?: "") key(KeyEvent.getExtendedKeyCodeForChar(c.code), 0, c)
                ok(ex)
            }
            "/hit" -> {
                // dev: what the integrated title bar reports for a window pixel (screenshot pixels), e.g. /hit?x=1800&y=20
                val scale = DesktopUiHost.window?.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
                val width = ((DesktopUiHost.window?.width ?: 0) * scale).toInt()
                val names = mapOf(1 to "CLIENT", 2 to "CAPTION", 8 to "MINBUTTON", 9 to "MAXBUTTON", 20 to "CLOSE", 12 to "TOP", 13 to "TOPLEFT", 14 to "TOPRIGHT")
                val code = com.lagradost.desktop.platform.WinChrome.classify(q["x"]!!.toInt(), q["y"]!!.toInt(), width, scale, com.lagradost.desktop.platform.WinChrome.maximized)
                ok(ex, "$code ${names[code] ?: ""} enabled=${com.lagradost.desktop.platform.WinChrome.enabled} maximized=${com.lagradost.desktop.platform.WinChrome.maximized} width=$width scale=$scale")
            }
            "/placement" -> {
                // dev: /placement?v=floating|maximized
                val st = DesktopUiHost.windowState
                onEdt { st?.placement = if (q["v"] == "maximized") androidx.compose.ui.window.WindowPlacement.Maximized else androidx.compose.ui.window.WindowPlacement.Floating }
                ok(ex)
            }
            "/resize" -> {
                // dev: window size in dp, e.g. /resize?w=900&h=600
                val w = q["w"]!!.toInt()
                val h = q["h"]!!.toInt()
                onEdt { DesktopUiHost.window?.setSize(w, h) }
                ok(ex)
            }
            "/nav" -> {
                // native UI: route=home|search|library|downloads|extensions|settings|back, q= for search
                val nav = com.lagradost.desktop.core.Navigator
                when (q["route"] ?: "home") {
                    "home" -> nav.goTab(com.lagradost.desktop.core.Tab.Home)
                    "search" -> nav.search(q["q"], q["only"])
                    "library" -> nav.goTab(com.lagradost.desktop.core.Tab.Library)
                    "downloads" -> nav.goTab(com.lagradost.desktop.core.Tab.Downloads)
                    "extensions" -> nav.goTab(com.lagradost.desktop.core.Tab.Extensions)
                    "settings" -> nav.go(com.lagradost.desktop.core.Route.Settings(q["q"]))
                    "back" -> nav.back()
                    "icons" -> nav.go(com.lagradost.desktop.core.Route.Icons(q["q"] ?: "E700"))
                }
                ok(ex)
            }
            "/navigate" -> {
                val id = com.lagradost.cloudstream3.R.id::class.java.getField(q["id"]!!).getInt(null)
                onEdt { mainNavController()?.navigate(id) }
                ok(ex)
            }
            "/back" -> {
                var handled = false
                onEdt { handled = BackHandlers.dispatch() }
                ok(ex, handled.toString())
            }
            "/layout" -> {
                // inflates an app layout (R.layout.<name>) into a dialog to check how it renders.
                // show=0 inflates only, so a sweep of every layout does not stack dialogs.
                // url= loads that address into the first WebView (dialog scale checks).
                val name = q["name"] ?: error("give name=<layout>")
                val show = q["show"] != "0"
                val page = q["url"]
                var result = "ok"
                onEdt {
                    try {
                        val act = com.lagradost.desktop.DesktopBootstrap.activity
                        val id = act.resources.getIdentifier(name, "layout", null)
                        if (id == 0) {
                            result = "no layout $name"
                        } else {
                            val view = android.view.LayoutInflater.from(act).inflate(id, null, false)
                            if (page != null) findWebView(view)?.loadUrl(page)
                            if (show) {
                                val d = android.app.Dialog(act)
                                d.setContentView(view)
                                d.show()
                            }
                        }
                    } catch (t: Throwable) {
                        result = "EX " + t.javaClass.name + ": " + (t.message ?: "")
                        android.util.Log.e("DevServer", "layout $name", t)
                    }
                }
                ok(ex, result)
            }
            "/handlers" -> {
                // which uncaught exception handlers are installed (diagnostics)
                val sb = StringBuilder()
                sb.append("default=").append(Thread.getDefaultUncaughtExceptionHandler()?.javaClass?.name).append('\n')
                onEdt { sb.append("edt=").append(Thread.currentThread().uncaughtExceptionHandler?.javaClass?.name).append('\n') }
                sb.append("err=").append(System.err.javaClass.name).append('\n')
                ok(ex, sb.toString())
            }
            "/state" -> {
                val sb = StringBuilder()
                onEdt {
                    val ctx = com.lagradost.desktop.runtime.AndroidRuntime.context
                    sb.append("native=").append(com.lagradost.desktop.core.Navigator.stack.joinToString(" > ") { it.route.toString().take(80) }).append('\n')
                    sb.append("fluentDialogs=").append(com.lagradost.desktop.ui.fluent.Overlays.dialogs.size).append('\n')
                    sb.append("activities=").append(com.lagradost.desktop.ActivityStack.activities.joinToString { it.javaClass.simpleName }).append('\n')
                    for (e in mainNavController()?.backStackSnapshot().orEmpty()) {
                        val id = e.destination.id
                        sb.append(runCatching { ctx.resources.getResourceEntryName(id) }.getOrDefault(id.toString())).append('\n')
                    }
                    sb.append("dialogs=").append(DesktopUiHost.androidDialogs.size).append('\n')
                    sb.append("layoutReports=").append(com.lagradost.desktop.runtime.ui.LayoutReporter.reports)
                        .append(" flushed=").append(com.lagradost.desktop.runtime.ui.LayoutReporter.flushed).append('\n')
                    sb.append("scale=").append(scale()).append(" size=").append(content().size).append('\n')
                }
                ok(ex, sb.toString())
            }
            "/plugins" -> {
                val sb = StringBuilder()
                for ((path, p) in com.lagradost.cloudstream3.plugins.PluginManager.plugins.toMap()) {
                    val settings = (p as? com.lagradost.cloudstream3.plugins.Plugin)?.openSettings != null
                    sb.append(p.javaClass.name).append(" settings=").append(settings).append(' ').append(path.substringAfterLast('\\').substringAfterLast('/')).append('\n')
                }
                ok(ex, sb.toString())
            }
            "/plugin-settings" -> {
                val name = q["name"]!!.lowercase()
                val plugin = com.lagradost.cloudstream3.plugins.PluginManager.plugins.toMap().entries.firstOrNull { (path, p) ->
                    p.javaClass.name.lowercase().contains(name) || path.lowercase().contains(name)
                }?.value as? com.lagradost.cloudstream3.plugins.Plugin ?: error("no plugin $name")
                val open = plugin.openSettings ?: error("${plugin.javaClass.name} has no settings")
                onEdt { open(com.lagradost.desktop.DesktopBootstrap.activity) }
                ok(ex)
            }
            "/viewtree" -> {
                val sb = StringBuilder()
                onEdt {
                    if (q["root"] == "activity") {
                        dumpView(com.lagradost.desktop.DesktopBootstrap.activity.getWindow().getDecorView(), 0, sb)
                        return@onEdt
                    }
                    val dialogs = DesktopUiHost.androidDialogs.toList()
                    val index = q["dialog"]?.toInt() ?: (dialogs.size - 1)
                    val dialog = dialogs.getOrNull(index)
                    val w = dialog?.getWindow()
                    if (w != null) {
                        sb.append("window gravity=").append(w.gravityState).append(" w=").append(w.layoutWidthState)
                            .append(" h=").append(w.layoutHeightState).append(" dialog=").append(dialog.javaClass.name).append('\n')
                        dumpView(w.getDecorView(), 0, sb)
                    } else sb.append("no dialog\n")
                }
                ok(ex, sb.toString())
            }
            "/plugin-res" -> {
                // dumps an XML resource (layout/drawable/...) of a plugin: ?name=jellyfin&type=drawable&res=button_red
                val name = q["name"]!!.lowercase()
                val plugin = com.lagradost.cloudstream3.plugins.PluginManager.plugins.toMap().entries.firstOrNull { (path, p) ->
                    p.javaClass.name.lowercase().contains(name) || path.lowercase().contains(name)
                }?.value as? com.lagradost.cloudstream3.plugins.Plugin ?: error("no plugin $name")
                val res = plugin.resources ?: error("plugin has no resources")
                val type = q["type"] ?: "drawable"
                val sb = StringBuilder()
                val resName = q["res"]
                if (resName == null) {
                    // list the resources of that type
                    for (id in 0x7f000000..0x7fffffff step 1) break
                    sb.append("give res=<name>")
                } else {
                    val pkg = res.javaClass.getMethod("getResourcePackageName", Int::class.javaPrimitiveType)
                    var id = res.getIdentifier(resName, type, null)
                    if (id == 0) id = res.getIdentifier(resName, type, "com.lagradost.cloudstream3")
                    if (id == 0) error("not found $type/$resName")
                    val parser = res.getXml(id)
                    var depth = 0
                    var event = parser.getEventType()
                    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                sb.append("  ".repeat(depth)).append('<').append(parser.getName())
                                for (i in 0 until parser.getAttributeCount()) {
                                    sb.append(' ').append(parser.getAttributeName(i)).append("=\"").append(parser.getAttributeValue(i)).append('"')
                                }
                                sb.append(">\n")
                                depth++
                            }
                            org.xmlpull.v1.XmlPullParser.END_TAG -> depth--
                        }
                        event = parser.next()
                    }
                    @Suppress("UNUSED_VARIABLE") val unused = pkg
                }
                ok(ex, sb.toString())
            }
            "/find" -> {
                // window pixel center of Android views whose text, hint or content description contains ?text=
                val needle = q["text"]!!.lowercase()
                val sb = StringBuilder()
                onEdt {
                    for ((root, pos) in com.lagradost.desktop.runtime.ui.HostPositions.positions.toMap()) {
                        fun walk(v: android.view.View, x: Int, y: Int) {
                            if (v.getVisibility() != android.view.View.VISIBLE) return
                            val label = when (v) {
                                is android.widget.TextView -> v.getText().toString() + " " + (v.getHint() ?: "")
                                else -> ""
                            } + " " + (v.getContentDescription() ?: "")
                            if (label.lowercase().contains(needle)) {
                                sb.append(v.javaClass.simpleName).append(" \"").append(label.trim().take(40)).append("\" x=")
                                    .append((pos.x + x + v.getWidth() / 2).toInt()).append(" y=").append((pos.y + y + v.getHeight() / 2).toInt()).append('\n')
                            }
                            if (v is android.view.ViewGroup) for (c in v.children.toList()) {
                                val sx = if (v is android.widget.ScrollView) v.getScrollY() else 0
                                walk(c, x + c.getLeft(), y + c.getTop() - sx)
                            }
                        }
                        walk(root, 0, 0)
                    }
                }
                ok(ex, sb.toString())
            }
            "/addsub" -> {
                // dev: add an external subtitle file to the playing video and select it
                val p = com.lagradost.desktop.player.MpvPlayer.active ?: return ok(ex, "no player")
                val file = java.io.File(q["file"]!!)
                val sd = com.lagradost.cloudstream3.ui.player.SubtitleData(q["name"] ?: "Test", "", file.toURI().toString(), com.lagradost.cloudstream3.ui.player.SubtitleOrigin.DOWNLOADED_FILE, "application/x-subrip", emptyMap(), q["lang"])
                p.setActiveSubtitles(setOf(sd))
                p.setPreferredSubtitles(sd)
                ok(ex)
            }
            "/mpv" -> {
                // dev: read (and set) an mpv property of the playing video: /mpv?name=sub-use-margins&set=no
                val p = com.lagradost.desktop.player.MpvPlayer.active ?: return ok(ex, "no player")
                val name = q["name"]!!
                q["set"]?.let { p.setMpvProperty(name, it) }
                ok(ex, "$name=${p.getMpvPropertyString(name)}")
            }
            "/mem" -> {
                // dev: where the memory of this process is: heap, other JVM memory, threads, classes, direct buffers, native libraries, and what Windows reports as the working set
                val mx = java.lang.management.ManagementFactory.getMemoryMXBean()
                val rt = Runtime.getRuntime()
                val cl = java.lang.management.ManagementFactory.getClassLoadingMXBean()
                val th = java.lang.management.ManagementFactory.getThreadMXBean()
                val direct = java.lang.management.ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean::class.java).joinToString(", ") { it.name + " " + it.memoryUsed / 1_048_576 + " MB" }
                val mb = { v: Long -> (v / 1_048_576).toString() + " MB" }
                ok(ex, "heap used " + mb(mx.heapMemoryUsage.used) + " / committed " + mb(mx.heapMemoryUsage.committed) + " / max " + mb(mx.heapMemoryUsage.max) + "\n" +
                    "non-heap used " + mb(mx.nonHeapMemoryUsage.used) + " / committed " + mb(mx.nonHeapMemoryUsage.committed) + "\n" +
                    "classes " + cl.loadedClassCount + ", threads " + th.threadCount + "\n" + "buffers: " + direct)
            }
            "/framestats" -> ok(ex, com.lagradost.desktop.tools.FrameStats.report(q["s"]?.toInt() ?: 10) + (q["worst"]?.toIntOrNull()?.let { "\n" + com.lagradost.desktop.tools.FrameStats.worst(q["s"]?.toInt() ?: 10, it) } ?: ""))
            "/videostats" -> {
                // dev: how the video frames keep up (render thread) and what mpv says about timing
                val p = com.lagradost.desktop.player.MpvPlayer.active ?: return ok(ex, "no player")
                val surface = com.lagradost.desktop.ui.screens.player.PlayerSession.active?.surface
                val props = listOf("time-pos", "speed", "avsync", "total-avsync-change", "frame-drop-count", "decoder-frame-drop-count", "vo-delayed-frame-count", "mistimed-frame-count", "container-fps", "estimated-vf-fps", "video-params/w", "video-params/h", "hwdec-current", "paused-for-cache", "demuxer-cache-duration")
                ok(ex, "surface: ${surface?.stats?.last}\npresent: ${surface?.present?.last}\nsmooth: ${surface?.smoothLast} useful=${surface?.blendUseful}\n" + props.joinToString("\n") { "$it=${p.getMpvPropertyString(it)}" })
            }
            "/mpvsample" -> {
                // dev: hitches in the picture and in the audio timeline: /mpvsample?s=30 samples the frame counter, time-pos and audio-pts every 5 ms and
                // lists every gap of 70+ ms between frames and every jump of the audio clock against the wall clock
                val p = com.lagradost.desktop.player.MpvPlayer.active ?: return ok(ex, "no player")
                val seconds = q["s"]?.toInt() ?: 30
                val sb = StringBuilder()
                val t0 = System.nanoTime()
                var lastFrame = -1L; var lastFrameAt = 0.0
                var lastAudio = Double.NaN; var lastAudioAt = 0.0
                var frames = 0; var gaps = 0; var jumps = 0; var maxGap = 0.0
                while ((System.nanoTime() - t0) / 1e9 < seconds) {
                    val now = (System.nanoTime() - t0) / 1e9
                    val frame = p.getMpvPropertyString("estimated-frame-number")?.toLongOrNull()
                    if (frame != null && frame != lastFrame) {
                        if (lastFrame >= 0) { frames++; val gap = (now - lastFrameAt) * 1000; if (gap > maxGap) maxGap = gap; if (gap >= 70) { gaps++; sb.append("frame gap %.0f ms at %.2f s (frame %d)\n".format(gap, now, frame)) } }
                        lastFrame = frame; lastFrameAt = now
                    }
                    val audio = p.getMpvPropertyString("audio-pts")?.toDoubleOrNull()
                    if (audio != null && audio != lastAudio) {
                        if (!lastAudio.isNaN()) {
                            val drift = (audio - lastAudio) - (now - lastAudioAt)
                            if (kotlin.math.abs(drift) > 0.08) { jumps++; sb.append("audio clock jump %+.0f ms at %.2f s\n".format(drift * 1000, now)) }
                        }
                        lastAudio = audio; lastAudioAt = now
                    }
                    Thread.sleep(5)
                }
                ok(ex, sb.toString() + "frames=$frames gaps(70+ms)=$gaps maxGap=%.0f ms audioJumps=$jumps".format(maxGap))
            }
            "/osdl" -> {
                // dev: the OpenSubtitles download request in several variants (which one is answered with the link and which with a block page)
                val s = com.lagradost.desktop.ui.screens.player.PlayerSession.active ?: return ok(ex, "no player page")
                val repo = com.lagradost.cloudstream3.ui.player.GeneratorPlayer.subsProviders.firstOrNull { it.idPrefix == "opensubtitles" } ?: return ok(ex, "no provider")
                val auth = repo.authData() ?: return ok(ex, "not signed in")
                val sb = StringBuilder()
                kotlinx.coroutines.runBlocking {
                    val entity = s.searchSubtitles(q["q"] ?: s.defaultSubtitleQuery, "en").firstOrNull { it.idPrefix == "opensubtitles" } ?: return@runBlocking
                    val id = entity.data
                    val key = com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi.API_KEY
                    val host = com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi.HOST
                    val bearer = "Bearer ${auth.token.accessToken}"
                    val browser = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    suspend fun run(name: String, ua: String, json: Boolean, accept: String?) {
                        val headers = buildMap { put("user-agent", ua); put("Api-Key", key); put("Authorization", bearer); put("Content-Type", "application/json"); accept?.let { put("Accept", it) } }
                        val r = runCatching {
                            if (json) com.lagradost.cloudstream3.app.post("$host/download", headers = headers, json = mapOf("file_id" to id.toInt()))
                            else com.lagradost.cloudstream3.app.post("$host/download", headers = headers, data = mapOf("file_id" to id))
                        }
                        sb.append(name).append(": ").append(r.fold({ x -> "${x.code} " + x.text.take(40).replace("\n", " ") }, { e -> "error ${e.message}" })).append('\n')
                        val link = r.getOrNull()?.text?.let { Regex("\"link\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                        if (link != null) {
                            val f = runCatching { com.lagradost.cloudstream3.app.get(link, headers = mapOf("user-agent" to ua)) }
                            sb.append("    file: ").append(f.fold({ x -> "${x.code} ${x.okhttpResponse.header("Content-Type")} " + x.text.take(50).replace("\n", " ") }, { e -> "error ${e.message}" })).append('\n')
                        }
                    }
                    run("form, Cloudstream UA, Accept */*", "Cloudstream3 v0.2", false, "*/*")
                    run("json, Cloudstream UA, Accept */*", "Cloudstream3 v0.2", true, "*/*")
                    run("json, Cloudstream UA, no Accept", "Cloudstream3 v0.2", true, null)
                    run("json, browser UA, Accept json", browser, true, "application/json")
                    run("form, browser UA", browser, false, null)
                }
                ok(ex, sb.toString())
            }
            "/dns" -> {
                // dev: how this process resolves a name: the JVM, and the HTTP client's Dns.SYSTEM / default proxy selector
                val host = q["host"]!!
                val a = runCatching { java.net.InetAddress.getAllByName(host).joinToString { it.hostAddress } }.getOrElse { "FAILED $it" }
                val b = runCatching { okhttp3.Dns.SYSTEM.lookup(host).joinToString { it.hostAddress } }.getOrElse { "FAILED $it" }
                val c = runCatching { java.net.ProxySelector.getDefault()?.javaClass?.name + " -> " + java.net.ProxySelector.getDefault()?.select(java.net.URI("https://$host/")).toString() }.getOrElse { "FAILED $it" }
                ok(ex, "InetAddress: $a\nDns.SYSTEM: $b\nProxySelector: $c\n")
            }
            "/suburl" -> {
                // dev: an online subtitle by link, the way an extension or a subtitle site delivers it: /suburl?url=http://...&name=Test
                val s = com.lagradost.desktop.ui.screens.player.PlayerSession.active ?: return ok(ex, "no player page")
                val url = q["url"]!!
                onEdt {
                    s.addAndSelectSubtitles(com.lagradost.cloudstream3.ui.player.SubtitleData(q["name"] ?: "Online test", "", url, com.lagradost.cloudstream3.ui.player.SubtitleOrigin.URL, "application/x-subrip", emptyMap(), q["lang"]))
                }
                ok(ex)
            }
            "/subdeliver" -> {
                // dev: subtitles delivered by an "extension" while playing, left to the automatic choice: /subdeliver?urls=http://a.srt|http://b.srt&names=English 0|English 1&lang=en
                val s = com.lagradost.desktop.ui.screens.player.PlayerSession.active ?: return ok(ex, "no player page")
                val names = q["names"]?.split('|') ?: emptyList()
                val subs = q["urls"]!!.split('|').mapIndexed { i, url ->
                    com.lagradost.cloudstream3.ui.player.SubtitleData(names.getOrNull(i) ?: "English $i", "", url, com.lagradost.cloudstream3.ui.player.SubtitleOrigin.URL, "application/x-subrip", emptyMap(), q["lang"] ?: "en")
                }
                onEdt { s.debugDeliverSubtitles(*subs.toTypedArray()) }
                ok(ex)
            }
            "/subfile" -> {
                // dev: the "Add subtitle file" path without the native dialog: /subfile?file=C:/path/x.srt
                val s = com.lagradost.desktop.ui.screens.player.PlayerSession.active ?: return ok(ex, "no player page")
                onEdt { s.addSubtitleFile(java.io.File(q["file"]!!)) }
                ok(ex)
            }
            "/ossub" -> {
                // dev: online subtitle search + apply of the n-th result in the playing video: /ossub?q=<title>&lang=en&n=0
                val s = com.lagradost.desktop.ui.screens.player.PlayerSession.active ?: return ok(ex, "no player page")
                // &block=1 behaves as if www.opensubtitles.com were blocked (the older interface answers search and download)
                if (q["block"] != null) com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesLegacy.markComBlocked()
                val out = kotlinx.coroutines.runBlocking {
                    val list = s.searchSubtitles(q["q"] ?: s.defaultSubtitleQuery, q["lang"] ?: "en").filter { q["provider"] == null || it.idPrefix == q["provider"] }
                    val n = q["n"]?.toInt() ?: 0
                    "results=${list.size} first=${list.take(3).map { it.name }}\n" + (list.getOrNull(n)?.let { "apply[$n]: " + s.applyOnlineSubtitle(it) } ?: "no result $n")
                }
                ok(ex, out)
            }
            "/vlctest" -> {
                // dev: hand a link to VLC the way the VLC action does: /vlctest?url=https://...mp4 (VLC opens; it quits itself after 4 s)
                val link = kotlinx.coroutines.runBlocking { com.lagradost.cloudstream3.utils.newExtractorLink("test", "test", q["url"]!!) {} }
                val outcome = com.lagradost.desktop.player.ExternalPlayers.openInVlc(link, "CloudStream test", emptyList(), null, listOf("--play-and-exit", "--run-time=4", "--no-audio"))
                ok(ex, "ok=${outcome.ok} ${outcome.message ?: ""} vlc=${com.lagradost.desktop.player.ExternalPlayers.vlcPath()}")
            }
            "/windows" -> {
                // dev: the AWT windows of the app (class, title, shown, bounds in screen px)
                ok(ex, java.awt.Window.getWindows().joinToString("\n") { w -> "${w.javaClass.simpleName} '${(w as? java.awt.Dialog)?.title ?: (w as? java.awt.Frame)?.title ?: ""}' shown=${w.isShowing} ${w.bounds.x},${w.bounds.y} ${w.bounds.width}x${w.bounds.height}" })
            }
            "/playurl" -> {
                // dev: play a plain link in the player to look at its controls: /playurl?url=https://...mp4&name=Title
                val link = kotlinx.coroutines.runBlocking { com.lagradost.cloudstream3.utils.newExtractorLink(q["name"] ?: "Test source", q["name"] ?: "Test source", q["url"]!!) { quality = 1080 } }
                onEdt {
                    com.lagradost.desktop.core.Navigator.go(com.lagradost.desktop.core.Route.Player(com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator(listOf(link), emptyList()), 0, null))
                }
                ok(ex)
            }
            "/playurls" -> {
                // dev: several plain links as the sources of one video, best first: /playurls?urls=http://a.mp4%7Chttp://b.mp4&names=One%7CTwo
                val urls = q["urls"]!!.split('|')
                val names = q["names"]?.split('|') ?: emptyList()
                val links = kotlinx.coroutines.runBlocking { urls.mapIndexed { i, u -> com.lagradost.cloudstream3.utils.newExtractorLink(names.getOrNull(i) ?: "Source $i", names.getOrNull(i) ?: "Source $i", u) { quality = 1000 - i * 100 } } }
                onEdt { com.lagradost.desktop.core.Navigator.go(com.lagradost.desktop.core.Route.Player(com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator(links, emptyList()), 0, null)) }
                ok(ex)
            }
            "/pick" -> {
                // dev: choose the n-th source (0 = first in the list) as the Sources dialog does: /pick?n=1
                val s = com.lagradost.desktop.ui.screens.player.PlayerSession.active ?: return ok(ex, "no player page")
                val list = s.sources()
                val n = q["n"]!!.toInt()
                onEdt { s.selectSource(list[n].link) }
                ok(ex, "picked ${list[n].name} of ${list.joinToString { it.name + if (it.usable) "" else "(failed)" }}")
            }
            "/pref" -> {
                // dev: set a string preference: /pref?key=player_default_key&value=...
                val act = com.lagradost.desktop.DesktopBootstrap.activity
                if (q["value"] != null) androidx.preference.PreferenceManager.getDefaultSharedPreferences(act).edit().putString(q["key"]!!, q["value"]!!).apply()
                val now = androidx.preference.PreferenceManager.getDefaultSharedPreferences(act).getString(q["key"]!!, null)
                ok(ex, "value=$now action=${com.lagradost.cloudstream3.ui.result.EpisodeAdapter.getPlayerAction(act)} ids=${com.lagradost.cloudstream3.actions.VideoClickActionHolder.allVideoClickActions.map { it.uniqueId() }}")
            }
            "/updatecheck" -> {
                // dev: Settings > About > Check now: /updatecheck ; /updatecheck?reset=1 forgets the last check and the skipped version first
                if (q["reset"] != null) androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.DesktopBootstrap.activity).edit()
                    .remove("desktop_update_checked_at").remove("desktop_update_skipped").apply()
                com.lagradost.desktop.update.UpdateCheck.checkNow()
                ok(ex)
            }
            "/trychannel" -> {
                // dev: play a channel of a provider the way the UI does (search, load, links, player), muted, and answer with the verdict:
                // /trychannel?provider=1&q=ETV+Plus[&i=0][&wait=30]   (one at a time, call /back between channels)
                val name = q["provider"]!!
                val api = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull { it.name == name } ?: return ok(ex, "NO PROVIDER $name")
                val hits = kotlinx.coroutines.runBlocking { api.search(q["q"]!!) } ?: emptyList()
                val hit = hits.getOrNull(q["i"]?.toIntOrNull() ?: 0) ?: return ok(ex, "NO RESULT for ${q["q"]} (${hits.size} results)")
                val loaded = kotlinx.coroutines.runBlocking { api.load(hit.url) } ?: return ok(ex, "LOAD FAILED ${hit.name}")
                val data = (loaded as? com.lagradost.cloudstream3.LiveStreamLoadResponse)?.dataUrl ?: return ok(ex, "NOT A LIVE ITEM ${loaded.javaClass.simpleName}")
                val links = ArrayList<com.lagradost.cloudstream3.utils.ExtractorLink>()
                kotlinx.coroutines.runBlocking { api.loadLinks(data, false, {}, { links.add(it) }) }
                if (links.isEmpty()) return ok(ex, "NO LINKS ${hit.name}")
                val before = com.lagradost.desktop.ui.screens.player.PlayerSession.active
                onEdt { com.lagradost.desktop.core.Navigator.go(com.lagradost.desktop.core.Route.Player(com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator(links, emptyList()), 0, null)) }
                val deadline = System.currentTimeMillis() + (q["wait"]?.toLongOrNull() ?: 30) * 1000
                var verdict = "TIMEOUT"
                var line = ""
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(500)
                    com.lagradost.desktop.player.MpvPlayer.active?.runCatching { setMpvProperty("mute", "yes") }
                    val session = com.lagradost.desktop.ui.screens.player.PlayerSession.active?.takeIf { it !== before } ?: continue
                    line = session.debugLine()
                    // the mpv part of the line can still be the previous channel's: a loaded file whose position moves is a playing one
                    val pos = Regex("""pos=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                    if (line.contains("fileLoaded=true") && line.contains("firstFrame=true") && pos > 1500) { verdict = "PLAYING"; break }
                    if (!line.contains("failure=null")) { verdict = "FAILED"; break }
                }
                val l = links.first()
                ok(ex, "$verdict | ${hit.name} | ${l.type} drm=${l.javaClass.simpleName} ${l.url.substringBefore('?').take(90)} | " + line.replace('\n', ' ').take(160))
            }
            "/cfkiller" -> {
                // dev: one request through the app's built-in Cloudflare handler (the one many extensions use as `interceptor = CloudflareKiller()`: a
                // hidden browser, no dialog, no click): /cfkiller?url=https://animepahe.pw/api?m=search&q=naruto ; prints the status, the time and the start of the body
                val url = q["url"]!!
                val started = System.currentTimeMillis()
                val out = kotlinx.coroutines.runBlocking {
                    runCatching {
                        val r = com.lagradost.cloudstream3.app.get(url, interceptor = com.lagradost.cloudstream3.network.CloudflareKiller(), timeout = 120)
                        "HTTP ${r.code} ${r.okhttpResponse.header("content-type")}\n${r.text.take(200)}"
                    }.getOrElse { "FAILED ${it.javaClass.simpleName}: ${it.message}" }
                }
                ok(ex, "${(System.currentTimeMillis() - started) / 1000.0} s\n$out")
            }
            "/toast" -> {
                // dev: show an in-app toast: /toast?text=hello
                com.lagradost.desktop.ui.Toasts.show(q["text"] ?: "toast", q["long"] == "1")
                ok(ex)
            }
            "/backup" -> {
                // dev: Settings > Back up data without the dialog: /backup writes where the button does
                com.lagradost.cloudstream3.utils.BackupUtils.backup(com.lagradost.desktop.DesktopBootstrap.activity)
                ok(ex)
            }
            "/restore" -> {
                // dev: Settings > Restore data without the file dialog: /restore?file=C:/path/CS3_Backup_x.txt
                com.lagradost.cloudstream3.utils.BackupUtils.restoreFromUri(com.lagradost.desktop.DesktopBootstrap.activity, android.net.Uri.fromFile(java.io.File(q["file"]!!)))
                ok(ex)
            }
            "/maximize" -> {
                // dev: /maximize?on=0 restores the window, on=1 maximizes it
                com.lagradost.desktop.platform.WinChrome.toggleMaximized(q["on"] != "0")
                ok(ex)
            }
            "/pointer" -> {
                // dev: pretend the pointer is at window pixel (x, y) for the auto-hiding window bar: /pointer?x=900&y=2 ; /pointer?off=1 goes back to the real one
                com.lagradost.desktop.platform.WinChrome.debugPointer = if (q["off"] != null) null else intArrayOf(q["x"]!!.toInt(), q["y"]!!.toInt())
                ok(ex)
            }
            "/link" -> {
                // dev: a deep link as if Windows had started the app with it (OAuth redirects): /link?u=cloudstreamapp://anilistlogin%23access_token=x
                val u = q["u"]!!
                onEdt { com.lagradost.desktop.NativeLinks.open(u) }
                ok(ex)
            }
            "/cookietest" -> {
                // dev: what a plugin login sees: a WebView page sets a cookie, onPageFinished reads it with CookieManager.getCookie (UI thread)
                val url = q["url"] ?: "https://httpbin.org/cookies/set?ui=test${System.currentTimeMillis() % 100000}"
                val result = java.util.concurrent.CompletableFuture<String>()
                onEdt {
                    val wv = android.webkit.WebView(com.lagradost.desktop.DesktopBootstrap.activity)
                    wv.getSettings().setJavaScriptEnabled(true)
                    wv.setWebViewClient(object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            val c = android.webkit.CookieManager.getInstance().getCookie(url)
                            val jar = com.lagradost.desktop.runtime.web.JcefRuntime.allCookies().joinToString { it.domain + "|" + it.name + "|" + it.path }
                            result.complete("page=$url cookie=$c jar=[$jar] ready=${com.lagradost.desktop.runtime.web.JcefRuntime.isReady}")
                            view?.destroy()
                        }
                    })
                    wv.loadUrl(url)
                }
                ok(ex, runCatching { result.get(40, java.util.concurrent.TimeUnit.SECONDS) }.getOrElse { "timeout: ${it.message}" })
            }
            "/look" -> {
                // dev: change the appearance without saving it: /look?nav=Top&radius=20&backdrop=Solid&player=Classic&style=Labels
                val a = com.lagradost.desktop.ui.fluent.Appearance
                onEdt {
                    q["nav"]?.let { v -> a.navPosition = com.lagradost.desktop.ui.fluent.NavPosition.valueOf(v) }
                    q["style"]?.let { v -> a.navStyle = com.lagradost.desktop.ui.fluent.NavStyle.valueOf(v) }
                    q["dockhide"]?.let { v -> a.dockAutoHide = v == "true" }
                    q["radius"]?.let { v -> a.cornerRadius = v.toInt() }
                    q["backdrop"]?.let { v -> a.backdrop = com.lagradost.desktop.ui.fluent.Backdrop.valueOf(v) }
                    q["player"]?.let { v -> a.playerStyle = com.lagradost.desktop.ui.fluent.PlayerStyle.valueOf(v) }
                    q["scale"]?.let { v -> a.uiScale = v.toFloat() }
                    q["poster"]?.let { v -> a.posterSize = com.lagradost.desktop.ui.fluent.PosterSize.valueOf(v) }
                    q["smooth"]?.let { v -> a.smoothMotion = v == "true" }
                    q["native"]?.let { v -> a.nativePlayer = v == "true" }
                    q["anime4k"]?.let { v -> a.anime4k = v == "true" }
                    q["blend"]?.let { v -> com.lagradost.desktop.ui.screens.player.blendDebug = v }
                }
                ok(ex, "nav=${a.navPosition} style=${a.navStyle} radius=${a.cornerRadius} backdrop=${a.backdrop} player=${a.playerStyle} scale=${a.uiScale}")
            }
            "/playlive" -> {
                // dev: open a live channel in the player: /playlive?provider=livxow&link=WILLOW (first main-page item with such a link)
                val providerName = q["provider"]!!.lowercase()
                val wanted = q["link"]!!
                val api = com.lagradost.cloudstream3.APIHolder.apis.firstOrNull { it.name.lowercase().contains(providerName) } ?: return ok(ex, "no provider")
                val found = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    for (page in api.mainPage.take(3)) {
                        val home = runCatching { api.getMainPage(1, com.lagradost.cloudstream3.MainPageRequest(page.name, page.data, page.horizontalImages)) }.getOrNull() ?: continue
                        for (item in home.items.flatMap { it.list }.take(20)) {
                            val data = when (val r = runCatching { api.load(item.url) }.getOrNull()) {
                                is com.lagradost.cloudstream3.LiveStreamLoadResponse -> r.dataUrl
                                is com.lagradost.cloudstream3.MovieLoadResponse -> r.dataUrl
                                else -> null
                            } ?: continue
                            val links = java.util.Collections.synchronizedList(ArrayList<com.lagradost.cloudstream3.utils.ExtractorLink>())
                            kotlinx.coroutines.withTimeoutOrNull(40_000) { runCatching { api.loadLinks(data, false, {}, { links.add(it) }) } }
                            links.firstOrNull { it.name.contains(wanted, true) }?.let { return@runBlocking item.name to it }
                        }
                    }
                    null
                } ?: return ok(ex, "no such link")
                onEdt {
                    com.lagradost.desktop.core.Navigator.go(com.lagradost.desktop.core.Route.Player(com.lagradost.cloudstream3.ui.player.ExtractorLinkGenerator(listOf(found.second), emptyList()), 0, null))
                }
                ok(ex, "playing ${found.first} / ${found.second.name}")
            }
            "/provider" -> {
                // dev: select the Home provider by name, optionally search in it only: /provider?name=Kisskh&q=squid
                val name = q["name"]!!
                onEdt {
                    com.lagradost.desktop.core.AppVms.get<com.lagradost.cloudstream3.ui.home.HomeViewModel>().loadAndCancel(name, forceReload = true, fromUI = true)
                    com.lagradost.desktop.core.Navigator.goTab(com.lagradost.desktop.core.Tab.Home)
                    q["q"]?.let { com.lagradost.desktop.core.Navigator.search(it, only = name) }
                }
                ok(ex)
            }
            "/tracks" -> {
                // dev: what mpv has (track list, selected tracks)
                val p = com.lagradost.desktop.player.MpvPlayer.active
                val sb = StringBuilder()
                if (p == null) sb.append("no player\n") else {
                    sb.append("sid=").append(p.getMpvPropertyString("sid")).append(" aid=").append(p.getMpvPropertyString("aid")).append(" vid=").append(p.getMpvPropertyString("vid")).append(" pos=").append(p.getMpvPropertyString("time-pos")).append(" cachedAhead=").append(p.getMpvPropertyString("demuxer-cache-duration")).append("\n")
                    p.tracks().forEach { sb.append(it.type).append(" #").append(it.id).append(" sel=").append(it.selected).append(" ext=").append(it.external).append(" lang=").append(it.lang).append(" title=").append(it.title).append(" codec=").append(it.codec).append(" file=").append(it.externalFile?.take(80)).append("\n") }
                }
                ok(ex, sb.toString())
            }
            "/restart" -> {
                // dev: what extensions do for "restart the app" (launcher intent, then kill the process)
                ok(ex)
                Thread {
                    val ctx = com.lagradost.desktop.runtime.AndroidRuntime.context
                    val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    ctx.startActivity(android.content.Intent.makeRestartActivityTask(launch?.component))
                    Runtime.getRuntime().exit(0)
                }.start()
            }
            "/quit" -> {
                ok(ex)
                EventQueue.invokeLater {
                    val w = window()
                    w.dispatchEvent(java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING))
                }
            }
            "/player" -> {
                // dev: the player page state in one line (loading text, status, source, mpv flags)
                if (q["fail"] != null) com.lagradost.desktop.ui.screens.player.PlayerSession.active?.debugFail()
                if (q["end"] != null) com.lagradost.desktop.ui.screens.player.PlayerSession.active?.debugEnd()
                ok(ex, com.lagradost.desktop.ui.screens.player.PlayerSession.active?.debugLine() ?: "no player page")
            }
            "/tlsinfo" -> {
                // dev: which TLS stack and HTTP version the app's client and a plain OkHttp client get
                val sb = StringBuilder()
                sb.appendLine("providers: " + java.security.Security.getProviders().take(4).joinToString { it.name })
                sb.appendLine("okhttp platform: " + okhttp3.internal.platform.Platform.get().javaClass.name)
                val base = com.lagradost.cloudstream3.app.baseClient
                sb.appendLine("baseClient protocols=${base.protocols} sslSocketFactory=${base.sslSocketFactory.javaClass.name} dns=${base.dns.javaClass.simpleName}")
                val url = q["url"] ?: "https://www.google.com/"
                fun probe(label: String, c: okhttp3.OkHttpClient) {
                    val r = runCatching { c.newCall(Request.Builder().url(url).build()).execute().use { "${it.protocol} ${it.code}" } }.getOrElse { "FAIL $it" }
                    sb.appendLine("$label: $r")
                }
                probe("baseClient", base)
                probe("plain OkHttpClient", okhttp3.OkHttpClient())
                probe("base without custom ssl", base.newBuilder().sslSocketFactory(
                    javax.net.ssl.SSLContext.getDefault().socketFactory,
                    (javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).also { it.init(null as java.security.KeyStore?) }.trustManagers[0] as javax.net.ssl.X509TrustManager),
                ).build())
                ok(ex, sb.toString())
            }
            "/notify" -> {
                // dev: posts a notification the way an extension or a download does (tray balloon, always silent)
                val n = android.app.Notification().also { it.title = q["title"] ?: "CloudStream"; it.text = q["text"] ?: "Test notification" }
                com.lagradost.desktop.runtime.Notifications.post(null, 4242, n)
                ok(ex)
            }
            "/httpprobe" -> {
                // dev: ONE request through the app's own HTTP client, nothing secret in it: /httpprobe?url=https://api.subdl.com/login&post=1 (empty JSON body)
                // [&os=1 adds the headers OpenSubtitles wants]; prints the status, protocol, content type and the start of the body, or the exception
                val url = q["url"]!!
                var extra = if (q["os"] != null) com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi.headers else emptyMap()
                // [&cookiekey=ANIMEPAHE_CF_COOKIES&uakey=ANIMEPAHE_CF_USER_AGENT]: the cookie and the user agent a plugin saved (read here, never printed)
                q["cookiekey"]?.let { k -> com.lagradost.cloudstream3.CloudStreamApp.getKey<String>(k)?.let { extra = extra + ("Cookie" to it) } }
                q["uakey"]?.let { k -> com.lagradost.cloudstream3.CloudStreamApp.getKey<String>(k)?.let { extra = extra + ("User-Agent" to it) } }
                q["referer"]?.let { extra = extra + ("Referer" to it) }
                val out = kotlinx.coroutines.runBlocking {
                    runCatching {
                        val r = if (q["post"] != null) com.lagradost.cloudstream3.app.post(url, json = emptyMap<String, String>(), headers = extra, timeout = 20)
                        else com.lagradost.cloudstream3.app.get(url, headers = extra, timeout = 20)
                        "HTTP ${r.code} ${r.okhttpResponse.protocol} ${r.okhttpResponse.header("content-type")}\n${r.text.take(300)}"
                    }.getOrElse { "FAILED ${it.javaClass.simpleName}: ${it.message}" }
                }
                ok(ex, out)
            }
            "/netcheck" -> {
                // dev: the app's own HTTP client asks url=<a,b> n times each (4 at a time, 12 s limit); prints ok/failed counts and the error kinds
                val urls = (q["url"] ?: "https://api.themoviedb.org/3/").split(",")
                val n = q["n"]?.toInt() ?: 20
                val gate = kotlinx.coroutines.sync.Semaphore(4)
                val out = kotlinx.coroutines.runBlocking {
                    urls.map { u ->
                        this.async(kotlinx.coroutines.Dispatchers.IO) {
                            val results = (1..n).map {
                                this.async {
                                    gate.acquire()
                                    try {
                                        val t0 = System.currentTimeMillis()
                                        val r = runCatching { kotlinx.coroutines.withTimeout(12_000) { com.lagradost.cloudstream3.app.get(u, timeout = 12) } }
                                        val ms = System.currentTimeMillis() - t0
                                        r.fold({ "ok ${it.code} ${it.okhttpResponse.protocol} ${ms}ms" }, { e -> "FAIL ${e.javaClass.simpleName}: ${e.message?.take(70)} ${ms}ms" })
                                    } finally {
                                        gate.release()
                                    }
                                }
                            }.map { it.await() }
                            val good = results.count { it.startsWith("ok") }
                            val protocols = results.filter { it.startsWith("ok") }.groupingBy { it.split(" ").getOrElse(2) { "?" } }.eachCount()
                            "$u  ok=$good/$n  protocols=$protocols\n" + results.filter { !it.startsWith("ok") }.groupingBy { r -> r.replace(Regex("\\d+ms"), "").trim() }.eachCount().entries.joinToString("\n") { "    ${it.value}x ${it.key}" }
                        }
                    }.map { it.await() }
                }
                ok(ex, out.joinToString("\n"))
            }
            "/searchreport" -> {
                // dev: every installed extension searched with q=<text> (like the Search page, 4 at a time), one line each: result count or the error
                val text = q["q"] ?: "inception"
                val limit = q["timeout"]?.toLong() ?: 30_000L
                val gate = kotlinx.coroutines.sync.Semaphore(q["parallel"]?.toInt() ?: 4)
                val lines = kotlinx.coroutines.runBlocking {
                    val repos = com.lagradost.cloudstream3.APIHolder.apis.map { com.lagradost.cloudstream3.ui.APIRepository(it) }
                    repos.map { r ->
                        this.async(kotlinx.coroutines.Dispatchers.IO) {
                            gate.acquire()
                            try {
                                val t0 = System.currentTimeMillis()
                                val res = kotlinx.coroutines.withTimeoutOrNull(limit) { r.search(text, 1) }
                                val ms = System.currentTimeMillis() - t0
                                val verdict = when (res) {
                                    null -> "TIMEOUT"
                                    is com.lagradost.cloudstream3.mvvm.Resource.Success -> if (res.value.items.isEmpty()) "EMPTY" else "OK ${res.value.items.size}"
                                    is com.lagradost.cloudstream3.mvvm.Resource.Failure -> "FAIL ${res.errorString.replace('\n', ' ').take(160)}"
                                    else -> "?"
                                }
                                "%-26s %6d ms  %s".format(r.name, ms, verdict)
                            } finally {
                                gate.release()
                            }
                        }
                    }.map { it.await() }
                }
                ok(ex, lines.joinToString("\n"))
            }
            "/log" -> {
                val lines = q["lines"]?.toInt() ?: 200
                ok(ex, LogBuffer.snapshot().takeLast(lines).joinToString("\n"))
            }
            else -> respond(ex, 404, "text/plain", "unknown".toByteArray())
        }
    }
}
