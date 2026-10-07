package com.lagradost.desktop.net

import okhttp3.Request

/**
 * Headers copied from an extension onto an OkHttp request. OkHttp refuses values outside ASCII, and some sources depend on one: IStreamFlare's
 * OK.ru links need its user agent with a Cyrillic "е" in "likе Gecko" (any other one gets HTTP 400). Android's player sends such a value as
 * UTF-8, and so does this (the value used to be dropped, so the request went out without it).
 */
object RawHeaders {
    fun set(builder: Request.Builder, name: String, value: String) {
        try {
            builder.header(name, value)
        } catch (e: IllegalArgumentException) {
            runCatching {
                builder.headers(builder.build().headers.newBuilder().removeAll(name).addUnsafeNonAscii(name, value).build())
            }
        }
    }
}
