package com.google.android.gms.common.api

interface ResultCallback<R> {
    fun onResult(result: R)
}

class Status(
    val statusCode: Int = 0,
    val statusMessage: String? = null
) {
    val isSuccess: Boolean get() = statusCode == 0
}

abstract class PendingResult<R> {
    abstract fun await(): R
    abstract fun setResultCallback(callback: ResultCallback<R>)
    fun setResultCallback(callback: (R) -> Unit) {
        setResultCallback(object : ResultCallback<R> {
            override fun onResult(result: R) = callback(result)
        })
    }
}
