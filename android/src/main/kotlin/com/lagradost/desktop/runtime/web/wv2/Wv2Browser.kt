package com.lagradost.desktop.runtime.web.wv2

import android.util.Log
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.PointerByReference

/**
 * One WebView2 page (controller + core) in its own window, created on first use. The state and every COM pointer belong to the WebView2
 * thread; other threads go through [withCore], which queues work until the page exists. Subclasses add their event handlers in [setup].
 */
internal abstract class Wv2Browser(private val initialWidth: Int = 1280, private val initialHeight: Int = 720) {
    companion object {
        private const val TAG = "WebView2"
        val IID_CONTROLLER_DONE = Com.iid("6c4819f3-c9b7-4260-8127-c9f5bde7f68c")
        val IID_EXECUTE_SCRIPT_DONE = Com.iid("49511172-cc67-4bca-9923-137112f4c4cc")
        val IID_DEVTOOLS_DONE = Com.iid("5c4889f0-5ef6-4c5a-952c-d8f1b92d0574")
        val IID_ADD_SCRIPT_DONE = Com.iid("b99369f3-9b11-47b5-bc6f-8e7895fcea17")
        val IID_NAVIGATION_STARTING = Com.iid("9adbe429-f36d-432b-9ddc-f8881fbd76e3")
        val IID_CONTENT_LOADING = Com.iid("364471e7-f2be-4910-bdba-d72077d51c4b")
        val IID_SOURCE_CHANGED = Com.iid("3c067f9f-5388-4772-8b48-79f7ef1ab37c")
        val IID_NAVIGATION_COMPLETED = Com.iid("d33a35bf-1c49-4f98-93ab-006e0533fe1c")
        val IID_SCRIPT_DIALOG = Com.iid("ef381bf9-afa8-4e37-91c4-8ac48524bdfb")
        val IID_PROCESS_FAILED = Com.iid("79e0aea4-990b-42d9-aa1d-0fcc2e5bc7f1")
        val IID_NEW_WINDOW = Com.iid("d4c185fe-c81c-4989-97af-2d3fa7ab5651")
        val IID_TITLE_CHANGED = Com.iid("f5f2b923-953e-4042-9f95-f3a118e1afd4")
        val IID_RESOURCE_REQUESTED = Com.iid("ab00b74c-15f1-4646-80e8-e76341d25d71")
        val IID_RESPONSE_RECEIVED = Com.iid("7de9898a-24f5-40c3-a2de-d4f458e69828")
        val IID_DOWNLOAD_STARTING = Com.iid("efedc989-c396-41ca-83f7-07f845a55724")
        val IID_CERTIFICATE_ERROR = Com.iid("969b3a26-d85e-4795-8199-fef57344da22")
        val IID_DEVTOOLS_EVENT = Com.iid("e2fda4be-5456-406c-a261-3d452138362c")
        val IID_CORE2 = Com.iid("9E8F0CF8-E670-4B5E-B2BC-73E061E3184C")
        val IID_CORE4 = Com.iid("20d02d59-6df2-42dc-bd06-f98a694b1302")
        val IID_CORE14 = Com.iid("6daa4f10-4a90-4753-8898-77c5df534165")
        val IID_SETTINGS2 = Com.iid("ee9a0f68-f46c-4e32-ac23-ef8cac224d2a")

        const val CONTEXT_ALL = 0
        const val CONTEXT_DOCUMENT = 1
        const val CONTEXT_IMAGE = 3
        const val ERROR_OPERATION_CANCELED = 14
    }

    protected val debug = System.getProperty("cloudstream.webdebug") == "true"

    enum class State { NONE, CREATING, READY, CLOSED }

    protected val rt = WebView2Runtime
    @Volatile protected var host: WinDef.HWND? = null
    protected var controller: Pointer? = null
    protected var core: Pointer? = null
    protected var core2: Pointer? = null
    @Volatile var state = State.NONE
        protected set
    private val pending = ArrayList<(Pointer) -> Unit>()
    @Volatile var lastActivity = System.currentTimeMillis()

    val isReady: Boolean get() = state == State.READY

    /** Who made this page (the first caller outside the WebView classes), for /webstate and the log */
    val origin: String = Throwable().stackTrace.firstOrNull { e ->
        val c = e.className
        !c.startsWith("com.lagradost.desktop.runtime.web") && !c.startsWith("android.webkit") && !c.startsWith("kotlin") && !c.startsWith("java.")
    }?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" } ?: "?"

    init {
        rt.register(this)
    }

    /** One line for /webstate */
    open fun describe(now: Long): String = "${javaClass.simpleName} $state idle=${(now - lastActivity) / 1000}s by $origin"

    /** Runs [action] with the page's ICoreWebView2 on the WebView2 thread (the page is made first when needed) */
    fun withCore(action: (Pointer) -> Unit) {
        lastActivity = System.currentTimeMillis()
        rt.post {
            when (state) {
                State.READY -> core?.let(action)
                State.CLOSED -> {}
                State.CREATING -> pending.add(action)
                State.NONE -> {
                    pending.add(action)
                    create()
                }
            }
        }
    }

    /** Size of the page's window when it is not on screen */
    protected open fun hiddenSize(): Pair<Int, Int> = initialWidth to initialHeight

    private fun create() {
        state = State.CREATING
        rt.acquire(this)
        rt.withEnvironment { env ->
            if (state == State.CLOSED) return@withEnvironment
            if (env == null) {
                failed("no WebView2 environment")
                return@withEnvironment
            }
            val (w, h) = hiddenSize()
            val window = rt.createHost(this, w, h)
            host = window
            val handler = ComObject.completed(IID_CONTROLLER_DONE) { hr, c ->
                if (!Com.ok(hr) || c == null) {
                    failed("page not created: HRESULT 0x${Integer.toHexString(hr)}")
                    return@completed
                }
                if (state == State.CLOSED) {
                    Com.call(c, 24)
                    return@completed
                }
                controller = Com.addRef(c)
                val cw = Com.getPtr(c, 25)
                core = cw
                core2 = cw?.let { Com.query(it, IID_CORE2) }
                Com.putBool(c, 4, true)
                onHostResized()
                rt.onCoreCreated(core2)
                if (cw != null) {
                    try {
                        setup(cw)
                    } catch (t: Throwable) {
                        Log.e(TAG, "page setup failed: ${Log.getStackTraceString(t)}")
                    }
                }
                state = State.READY
                val actions = pending.toList()
                pending.clear()
                if (cw != null) actions.forEach { a -> runCatching { a(cw) }.onFailure { Log.e(TAG, Log.getStackTraceString(it)) } }
                onReady()
            }
            val hr = Com.call(env, 3, window.pointer, handler.pointer)
            handler.releaseOwn()
            if (hr < 0) failed("page not created: HRESULT 0x${Integer.toHexString(hr)}")
        }
    }

    private fun failed(why: String) {
        Log.e(TAG, why)
        pending.clear()
        releasePointers(close = false)
        if (state != State.CLOSED) state = State.NONE
    }

    /** Event handlers and settings of a new page */
    protected abstract fun setup(core: Pointer)

    /** After [setup] and the queued work */
    protected open fun onReady() {}

    /** WM_SIZE of the page's window: the page fills it */
    open fun onHostResized() {
        val c = controller ?: return
        val window = host ?: return
        val r = WinDef.RECT()
        User32.INSTANCE.GetClientRect(window, r)
        Com.call(c, 6, RectByValue(0, 0, r.right - r.left, r.bottom - r.top))
    }

    protected fun releasePointers(close: Boolean) {
        val c = controller
        controller = null
        if (close && c != null) runCatching { Com.call(c, 24) }
        rt.onCoreRemoved(core2)
        Com.release(core2)
        Com.release(core)
        Com.release(c)
        core2 = null
        core = null
        host?.let { rt.destroyHost(it) }
        host = null
        rt.release(this)
    }

    /** Closes the page for good (any thread) */
    open fun close() {
        rt.post {
            if (state == State.CLOSED) return@post
            state = State.CLOSED
            pending.clear()
            releasePointers(close = true)
            rt.unregister(this)
        }
    }

    /** Closes the page but keeps the object usable: the next [withCore] makes a new one (WebView2 thread) */
    protected fun park() {
        if (state != State.READY) return
        releasePointers(close = true)
        state = State.NONE
    }

    /** The browser process is gone (WebView2 thread) */
    open fun dropAfterCrash() {
        if (state == State.CLOSED) return
        pending.clear()
        rt.onCoreRemoved(core2)
        controller = null
        core2 = null
        core = null
        host?.let { rt.destroyHost(it) }
        host = null
        rt.release(this)
        state = State.NONE
    }

    /** WebView2 thread, once a minute */
    open fun parkIfForgotten(now: Long) {}

    // ------------------------------------------------------------------ helpers (WebView2 thread)

    protected fun on(target: Pointer, index: Int, iid: com.sun.jna.platform.win32.Guid.GUID, block: (sender: Pointer?, args: Pointer?) -> Unit) {
        val handler = ComObject.event(iid, block)
        Com.addHandler(target, index, handler)
        handler.releaseOwn()
    }

    protected fun navigate(c: Pointer, url: String, headers: Map<String, String>, postData: ByteArray?) {
        val env2 = rt.environment2
        val c2 = core2
        if ((headers.isEmpty() && postData == null) || env2 == null || c2 == null) {
            val hr = Com.call(c, 5, WString(url))
            if (hr < 0 || debug) Log.i(TAG, "Navigate $url: 0x${Integer.toHexString(hr)}")
            return
        }
        val stream = postData?.let { Com.memStream(it) }
        val all = LinkedHashMap(headers)
        if (postData != null && all.keys.none { it.equals("Content-Type", true) }) all["Content-Type"] = "application/x-www-form-urlencoded"
        val headerText = all.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
        val request = Com.getPtr(env2, 8, WString(url), WString(if (postData != null) "POST" else "GET"), stream, WString(headerText))
        Com.release(stream)
        if (request == null) {
            Com.call(c, 5, WString(url))
            return
        }
        Com.call(c2, 63, request)
        Com.release(request)
    }

    /** Runs [script] in the page; [callback] gets the JSON encoded result ("null" for undefined or an error) */
    protected fun execute(c: Pointer, script: String, callback: ((String) -> Unit)?) {
        val handler = ComObject.completed(IID_EXECUTE_SCRIPT_DONE) { hr, result ->
            val json = if (Com.ok(hr)) result?.getWideString(0) else null
            callback?.invoke(json ?: "null")
        }
        if (Com.call(c, 29, WString(script), handler.pointer) < 0) callback?.invoke("null")
        handler.releaseOwn()
    }

    protected fun devTools(c: Pointer, method: String, params: String = "{}", callback: ((String?) -> Unit)? = null) {
        val handler = ComObject.completed(IID_DEVTOOLS_DONE) { hr, result ->
            callback?.invoke(if (Com.ok(hr)) result?.getWideString(0) else null)
        }
        if (Com.call(c, 36, WString(method), WString(params), handler.pointer) < 0) callback?.invoke(null)
        handler.releaseOwn()
    }

    protected fun source(c: Pointer): String? = Com.getString(c, 4)

    /** The request headers of an ICoreWebView2WebResourceRequest */
    protected fun requestHeaders(request: Pointer): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val headers = Com.getPtr(request, 9) ?: return out
        try {
            val it = Com.getPtr(headers, 8) ?: return out
            try {
                while (Com.getBool(it, 4)) {
                    val name = PointerByReference()
                    val value = PointerByReference()
                    if (Com.ok(Com.call(it, 3, name, value))) {
                        val n = name.value?.getWideString(0)
                        val v = value.value?.getWideString(0)
                        name.value?.let { p -> com.sun.jna.platform.win32.Ole32.INSTANCE.CoTaskMemFree(p) }
                        value.value?.let { p -> com.sun.jna.platform.win32.Ole32.INSTANCE.CoTaskMemFree(p) }
                        if (n != null) out[n] = v ?: ""
                    }
                    val more = com.sun.jna.ptr.IntByReference()
                    if (!Com.ok(Com.call(it, 5, more)) || more.value == 0) break
                }
            } finally {
                Com.release(it)
            }
        } finally {
            Com.release(headers)
        }
        return out
    }

    /** Answers a WebResourceRequested event ([args] is ICoreWebView2WebResourceRequestedEventArgs) */
    protected fun respond(args: Pointer, status: Int, reason: String, headers: Map<String, String>, body: ByteArray?) {
        val env = rt.environment ?: return
        val stream = body?.let { Com.memStream(it) }
        val headerText = headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
        val response = Com.getPtr(env, 4, stream, status, WString(reason), WString(headerText))
        Com.release(stream)
        if (response != null) {
            Com.call(args, 5, response)
            Com.release(response)
        }
    }
}
