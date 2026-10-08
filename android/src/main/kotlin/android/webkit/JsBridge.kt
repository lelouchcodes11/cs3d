package android.webkit

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * addJavascriptInterface for both engines: a script made at document creation defines window[name] with the @JavascriptInterface methods;
 * each call is a synchronous XMLHttpRequest to [ORIGIN]/<browser>/<name>/<method> that the engine answers from Java ([invoke]).
 */
internal object JsBridge {
    private const val TAG = "WebView"
    const val ORIGIN = "https://cloudstream-bridge.invalid"

    private fun exposedMethods(obj: Any): List<String> =
        obj.javaClass.methods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }.map { it.name }.distinct()

    /** The document-creation script for [interfaces] of the browser [browserId], null when there are none */
    fun script(browserId: Any, interfaces: Map<String, Any>): String? {
        if (interfaces.isEmpty()) return null
        val sb = StringBuilder("(function(){function __csCall(n,m,a){var x=new XMLHttpRequest();")
        sb.append("x.open('POST','$ORIGIN/$browserId/'+encodeURIComponent(n)+'/'+encodeURIComponent(m),false);")
        sb.append("x.send(JSON.stringify(Array.prototype.slice.call(a)));var r=JSON.parse(x.responseText||'{}');")
        sb.append("if(r.e)throw new Error(r.e);return r.v;}")
        for ((name, obj) in interfaces) {
            sb.append("window[").append(JSONObject.quote(name)).append("]={")
            sb.append(exposedMethods(obj).joinToString(",") { m -> "${JSONObject.quote(m)}:function(){return __csCall(${JSONObject.quote(name)},${JSONObject.quote(m)},arguments);}" })
            sb.append("};")
        }
        sb.append("})();")
        return sb.toString()
    }

    /** (interface name, method) of a bridge URL */
    fun target(url: String): Pair<String, String> {
        val path = url.removePrefix(ORIGIN).trim('/').split('/')
        return java.net.URLDecoder.decode(path.getOrNull(1) ?: "", "UTF-8") to java.net.URLDecoder.decode(path.getOrNull(2) ?: "", "UTF-8")
    }

    /** Invokes a @JavascriptInterface method, returns the JSON answer for the page */
    fun invoke(interfaces: Map<String, Any>, name: String, method: String, argsJson: String): String {
        val obj = interfaces[name] ?: return JSONObject().put("e", "No interface $name").toString()
        return try {
            val args = JSONArray(argsJson.ifBlank { "[]" })
            val candidates = obj.javaClass.methods.filter {
                it.name == method && it.isAnnotationPresent(JavascriptInterface::class.java) && it.parameterCount == args.length()
            }
            val m = candidates.firstOrNull() ?: return JSONObject().put("e", "Method not found").toString()
            // extensions often declare the bridge as a private (package-private) class: Android's WebView calls it anyway
            m.trySetAccessible()
            val params = m.parameterTypes.mapIndexed { i, t -> convertArg(args.opt(i), t) }.toTypedArray()
            val result = m.invoke(obj, *params)
            JSONObject().put("v", if (result == null || m.returnType == Void.TYPE) JSONObject.NULL else result).toString()
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            Log.e(TAG, "JavascriptInterface $name.$method threw: ${Log.getStackTraceString(cause)}")
            JSONObject().put("e", cause.toString()).toString()
        }
    }

    private fun convertArg(v: Any?, t: Class<*>): Any? {
        if (v == null || v == JSONObject.NULL) {
            return when (t) {
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Double.TYPE -> 0.0
                java.lang.Float.TYPE -> 0f
                java.lang.Boolean.TYPE -> false
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Character.TYPE -> 0.toChar()
                String::class.java -> if (v == JSONObject.NULL) null else "undefined"
                else -> null
            }
        }
        return when (t) {
            String::class.java -> if (v is String) v else v.toString()
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> (v as? Number)?.toInt() ?: v.toString().toIntOrNull() ?: 0
            java.lang.Long.TYPE, java.lang.Long::class.java -> (v as? Number)?.toLong() ?: v.toString().toLongOrNull() ?: 0L
            java.lang.Double.TYPE, java.lang.Double::class.java -> (v as? Number)?.toDouble() ?: v.toString().toDoubleOrNull() ?: 0.0
            java.lang.Float.TYPE, java.lang.Float::class.java -> (v as? Number)?.toFloat() ?: v.toString().toFloatOrNull() ?: 0f
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> (v as? Boolean) ?: v.toString().toBoolean()
            else -> v
        }
    }
}
