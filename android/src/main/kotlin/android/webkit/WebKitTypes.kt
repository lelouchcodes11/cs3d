@file:JvmName("WebKitTypesKt")

package android.webkit

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.KeyEvent
import android.view.View
import java.io.InputStream

fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}

fun interface DownloadListener {
    fun onDownloadStart(url: String?, userAgent: String?, contentDisposition: String?, mimetype: String?, contentLength: Long)
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class JavascriptInterface

interface WebResourceRequest {
    val url: Uri
    val isForMainFrame: Boolean
    val isRedirect: Boolean
    fun hasGesture(): Boolean
    val method: String
    val requestHeaders: Map<String, String>
}

/** Concrete request passed to WebViewClient callbacks */
class WebResourceRequestImpl(
    override val url: Uri,
    override val method: String,
    override val requestHeaders: Map<String, String>,
    override val isForMainFrame: Boolean,
    override val isRedirect: Boolean,
    private val gesture: Boolean,
) : WebResourceRequest {
    override fun hasGesture(): Boolean = gesture
    override fun toString(): String = "$method $url"
}

open class WebResourceResponse {
    private var mMimeType: String?
    private var mEncoding: String?
    private var mStatusCode = 200
    private var mReasonPhrase: String? = "OK"
    private var mResponseHeaders: Map<String, String>? = null
    private var mInputStream: InputStream?

    constructor(mimeType: String?, encoding: String?, data: InputStream?) {
        mMimeType = mimeType
        mEncoding = encoding
        mInputStream = data
    }

    constructor(mimeType: String?, encoding: String?, statusCode: Int, reasonPhrase: String?, responseHeaders: Map<String, String>?, data: InputStream?) {
        mMimeType = mimeType
        mEncoding = encoding
        mInputStream = data
        setStatusCodeAndReasonPhrase(statusCode, reasonPhrase)
        mResponseHeaders = responseHeaders
    }

    open fun setMimeType(mimeType: String?) {
        mMimeType = mimeType
    }

    open fun getMimeType(): String? = mMimeType
    open fun setEncoding(encoding: String?) {
        mEncoding = encoding
    }

    open fun getEncoding(): String? = mEncoding
    open fun setStatusCodeAndReasonPhrase(statusCode: Int, reasonPhrase: String?) {
        mStatusCode = statusCode
        mReasonPhrase = reasonPhrase
    }

    open fun getStatusCode(): Int = mStatusCode
    open fun getReasonPhrase(): String? = mReasonPhrase
    open fun setResponseHeaders(headers: Map<String, String>?) {
        mResponseHeaders = headers
    }

    open fun getResponseHeaders(): Map<String, String>? = mResponseHeaders
    open fun setData(data: InputStream?) {
        mInputStream = data
    }

    open fun getData(): InputStream? = mInputStream
}

abstract class WebResourceError {
    abstract fun getErrorCode(): Int
    abstract fun getDescription(): CharSequence
}

class WebResourceErrorImpl(private val code: Int, private val description: CharSequence) : WebResourceError() {
    override fun getErrorCode(): Int = code
    override fun getDescription(): CharSequence = description
}

open class SslErrorHandler internal constructor(private val onProceed: () -> Unit, private val onCancel: () -> Unit) {
    private var handled = false

    open fun proceed() {
        if (handled) return
        handled = true
        onProceed()
    }

    open fun cancel() {
        if (handled) return
        handled = true
        onCancel()
    }
}

open class HttpAuthHandler internal constructor(private val onProceed: (String?, String?) -> Unit, private val onCancel: () -> Unit) {
    open fun useHttpAuthUsernamePassword(): Boolean = false
    open fun proceed(username: String?, password: String?) = onProceed(username, password)
    open fun cancel() = onCancel()
}

open class ClientCertRequest

open class RenderProcessGoneDetail {
    open fun didCrash(): Boolean = true
    open fun rendererPriorityAtExit(): Int = 0
}

open class ConsoleMessage(
    private val message: String?,
    private val sourceId: String?,
    private val lineNumber: Int,
    private val messageLevel: MessageLevel,
) {
    enum class MessageLevel { TIP, LOG, WARNING, ERROR, DEBUG }

    fun messageLevel(): MessageLevel = messageLevel
    fun message(): String? = message
    fun sourceId(): String? = sourceId
    fun lineNumber(): Int = lineNumber
}

open class JsResult internal constructor(private val onResult: (Boolean, String?) -> Unit) {
    open fun cancel() = onResult(false, null)
    open fun confirm() = onResult(true, null)
    internal fun resolve(confirmed: Boolean, value: String?) = onResult(confirmed, value)
}

open class JsPromptResult internal constructor(onResult: (Boolean, String?) -> Unit) : JsResult(onResult) {
    open fun confirm(result: String?) = resolve(true, result)
}

open class GeolocationPermissions {
    fun interface Callback {
        fun invoke(origin: String?, allow: Boolean, retain: Boolean)
    }

    companion object {
        @JvmStatic
        fun getInstance(): GeolocationPermissions = GeolocationPermissions()
    }

    open fun allow(origin: String?) {}
    open fun clear(origin: String?) {}
    open fun clearAll() {}
}

abstract class PermissionRequest {
    companion object {
        const val RESOURCE_VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"
        const val RESOURCE_AUDIO_CAPTURE = "android.webkit.resource.AUDIO_CAPTURE"
        const val RESOURCE_PROTECTED_MEDIA_ID = "android.webkit.resource.PROTECTED_MEDIA_ID"
        const val RESOURCE_MIDI_SYSEX = "android.webkit.resource.MIDI_SYSEX"
    }

    abstract fun getOrigin(): Uri
    abstract fun getResources(): Array<String>
    abstract fun grant(resources: Array<String>?)
    abstract fun deny()
}

open class WebViewClient {
    companion object {
        const val ERROR_UNKNOWN = -1
        const val ERROR_HOST_LOOKUP = -2
        const val ERROR_UNSUPPORTED_AUTH_SCHEME = -3
        const val ERROR_AUTHENTICATION = -4
        const val ERROR_PROXY_AUTHENTICATION = -5
        const val ERROR_CONNECT = -6
        const val ERROR_IO = -7
        const val ERROR_TIMEOUT = -8
        const val ERROR_REDIRECT_LOOP = -9
        const val ERROR_UNSUPPORTED_SCHEME = -10
        const val ERROR_FAILED_SSL_HANDSHAKE = -11
        const val ERROR_BAD_URL = -12
        const val ERROR_FILE = -13
        const val ERROR_FILE_NOT_FOUND = -14
        const val ERROR_TOO_MANY_REQUESTS = -15
        const val ERROR_UNSAFE_RESOURCE = -16
        const val SAFE_BROWSING_THREAT_UNKNOWN = 0
    }

    @Deprecated("")
    open fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
    open fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        @Suppress("DEPRECATION") if (request != null) shouldOverrideUrlLoading(view, request.url.toString()) else false

    open fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {}
    open fun onPageFinished(view: WebView?, url: String?) {}
    open fun onLoadResource(view: WebView?, url: String?) {}
    open fun onPageCommitVisible(view: WebView?, url: String?) {}

    @Deprecated("")
    open fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? = null
    open fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
        @Suppress("DEPRECATION") if (request != null) shouldInterceptRequest(view, request.url.toString()) else null

    @Deprecated("")
    open fun onTooManyRedirects(view: WebView, cancelMsg: Message?, continueMsg: Message?) {}

    @Deprecated("")
    open fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {}
    open fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            @Suppress("DEPRECATION")
            onReceivedError(view, error.getErrorCode(), error.getDescription().toString(), request.url.toString())
        }
    }

    open fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {}
    open fun onFormResubmission(view: WebView, dontResend: Message?, resend: Message?) {
        dontResend?.sendToTarget()
    }

    open fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {}
    open fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    open fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest?) {}
    open fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String?, realm: String?) {
        handler.cancel()
    }

    open fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean = false
    open fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {}
    open fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {}
    open fun onReceivedLoginRequest(view: WebView, realm: String?, account: String?, args: String?) {}
    open fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = false
    open fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: Any?) {}
}

open class WebChromeClient {
    interface CustomViewCallback {
        fun onCustomViewHidden()
    }

    abstract class FileChooserParams {
        companion object {
            const val MODE_OPEN = 0
            const val MODE_OPEN_MULTIPLE = 1
            const val MODE_SAVE = 3

            @JvmStatic
            fun parseResult(resultCode: Int, data: android.content.Intent?): Array<Uri>? = data?.data?.let { arrayOf(it) }
        }

        abstract fun getMode(): Int
        abstract fun getAcceptTypes(): Array<String>
        abstract fun isCaptureEnabled(): Boolean
        abstract fun getTitle(): CharSequence?
        abstract fun getFilenameHint(): String?
        abstract fun createIntent(): android.content.Intent
    }

    open fun onProgressChanged(view: WebView, newProgress: Int) {}
    open fun onReceivedTitle(view: WebView, title: String?) {}
    open fun onReceivedIcon(view: WebView, icon: Bitmap?) {}
    open fun onReceivedTouchIconUrl(view: WebView, url: String?, precomposed: Boolean) {}
    open fun onShowCustomView(view: View?, callback: CustomViewCallback?) {}
    open fun onHideCustomView() {}
    open fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
    open fun onRequestFocus(view: WebView) {}
    open fun onCloseWindow(window: WebView) {}
    open fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean = false
    open fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean = false
    open fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean = false
    open fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean = false
    open fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {}
    open fun onGeolocationPermissionsHidePrompt() {}
    open fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    open fun onPermissionRequestCanceled(request: PermissionRequest) {}

    @Deprecated("")
    open fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {}
    open fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        @Suppress("DEPRECATION")
        onConsoleMessage(consoleMessage.message(), consoleMessage.lineNumber(), consoleMessage.sourceId())
        return false
    }

    open fun getDefaultVideoPoster(): Bitmap? = null
    open fun getVideoLoadingProgressView(): View? = null
    open fun getVisitedHistory(callback: ValueCallback<Array<String>>?) {}
    open fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean = false
}

object URLUtil {
    @JvmStatic
    fun guessUrl(inUrl: String?): String? {
        if (inUrl.isNullOrBlank()) return inUrl
        val u = inUrl.trim()
        if (u.contains("://") || u.startsWith("about:") || u.startsWith("javascript:") || u.startsWith("data:")) return u
        return "http://$u"
    }

    @JvmStatic
    fun composeSearchUrl(inQuery: String?, template: String?, queryPlaceHolder: String?): String? {
        if (inQuery == null || template == null || queryPlaceHolder == null) return null
        return template.replace(queryPlaceHolder, Uri.encode(inQuery))
    }

    @JvmStatic
    fun isAssetUrl(url: String?): Boolean = url?.startsWith("file:///android_asset/") == true

    @JvmStatic
    fun isResourceUrl(url: String?): Boolean = url?.startsWith("file:///android_res/") == true

    @JvmStatic
    fun isFileUrl(url: String?): Boolean = url?.startsWith("file://") == true && !isAssetUrl(url) && !isResourceUrl(url)

    @JvmStatic
    fun isAboutUrl(url: String?): Boolean = url?.startsWith("about:") == true

    @JvmStatic
    fun isDataUrl(url: String?): Boolean = url?.startsWith("data:") == true

    @JvmStatic
    fun isJavaScriptUrl(url: String?): Boolean = url?.startsWith("javascript:") == true

    @JvmStatic
    fun isHttpUrl(url: String?): Boolean = url != null && url.length > 6 && url.substring(0, 7).equals("http://", ignoreCase = true)

    @JvmStatic
    fun isHttpsUrl(url: String?): Boolean = url != null && url.length > 7 && url.substring(0, 8).equals("https://", ignoreCase = true)

    @JvmStatic
    fun isNetworkUrl(url: String?): Boolean = !url.isNullOrEmpty() && (isHttpUrl(url) || isHttpsUrl(url))

    @JvmStatic
    fun isContentUrl(url: String?): Boolean = url?.startsWith("content:") == true

    @JvmStatic
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return isAssetUrl(url) || isResourceUrl(url) || isFileUrl(url) || isAboutUrl(url) || isHttpUrl(url) ||
                isHttpsUrl(url) || isJavaScriptUrl(url) || isContentUrl(url)
    }

    @JvmStatic
    fun stripAnchor(url: String?): String? {
        if (url == null) return null
        val anchorIndex = url.indexOf('#')
        return if (anchorIndex != -1) url.substring(0, anchorIndex) else url
    }

    @JvmStatic
    fun guessFileName(url: String?, contentDisposition: String?, mimeType: String?): String {
        var filename: String? = null
        if (contentDisposition != null) {
            val m = Regex("filename\\*?=\"?(?:UTF-8'')?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(contentDisposition)
            filename = m?.groupValues?.getOrNull(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        }
        if (filename == null && url != null) {
            var decoded = Uri.decode(url.substringBefore('?').substringBefore('#'))
            if (decoded.endsWith("/")) decoded = decoded.dropLast(1)
            filename = decoded.substringAfterLast('/').ifEmpty { null }
        }
        if (filename == null) filename = "downloadfile"
        if (!filename.contains('.')) {
            val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            filename += if (ext != null) ".$ext" else if (mimeType?.startsWith("text/") == true) ".txt" else ".bin"
        }
        return filename
    }
}

open class MimeTypeMap private constructor() {
    companion object {
        private val sMimeTypeMap = MimeTypeMap()
        private val extToMime = mapOf(
            "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
            "avi" to "video/x-msvideo", "mov" to "video/quicktime", "ts" to "video/mp2t", "m3u8" to "application/x-mpegurl",
            "mpd" to "application/dash+xml", "flv" to "video/x-flv", "3gp" to "video/3gpp", "wmv" to "video/x-ms-wmv",
            "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac", "ogg" to "audio/ogg",
            "opus" to "audio/opus", "wav" to "audio/x-wav", "srt" to "application/x-subrip", "vtt" to "text/vtt",
            "ass" to "text/x-ssa", "ssa" to "text/x-ssa", "ttml" to "application/ttml+xml", "json" to "application/json",
            "txt" to "text/plain", "html" to "text/html", "htm" to "text/html", "css" to "text/css", "js" to "text/javascript",
            "xml" to "text/xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
            "webp" to "image/webp", "svg" to "image/svg+xml", "ico" to "image/x-icon", "zip" to "application/zip",
            "apk" to "application/vnd.android.package-archive", "pdf" to "application/pdf", "torrent" to "application/x-bittorrent",
            "cs3" to "application/zip", "jar" to "application/java-archive"
        )
        private val mimeToExt: Map<String, String> = extToMime.entries.groupBy({ it.value }, { it.key }).mapValues { it.value.first() }

        @JvmStatic
        fun getSingleton(): MimeTypeMap = sMimeTypeMap

        @JvmStatic
        fun getFileExtensionFromUrl(url: String?): String {
            if (url.isNullOrEmpty()) return ""
            var u: String = url
            val hash = u.indexOf('#')
            if (hash > 0) u = u.substring(0, hash)
            val query = u.indexOf('?')
            if (query > 0) u = u.substring(0, query)
            val filename = u.substringAfterLast('/')
            if (filename.isNotEmpty() && Regex("[a-zA-Z_0-9.\\-()%]+").matches(filename)) {
                val dot = filename.lastIndexOf('.')
                if (dot >= 0) return filename.substring(dot + 1)
            }
            return ""
        }
    }

    open fun hasMimeType(mimeType: String?): Boolean = mimeType != null && mimeToExt.containsKey(mimeType.lowercase())
    open fun getMimeTypeFromExtension(extension: String?): String? = extension?.let { extToMime[it.lowercase()] }
    open fun hasExtension(extension: String?): Boolean = extension != null && extToMime.containsKey(extension.lowercase())
    open fun getExtensionFromMimeType(mimeType: String?): String? = mimeType?.let { mimeToExt[it.lowercase()] }
}
