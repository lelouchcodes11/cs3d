package com.google.common.collect

object Comparators {
    @JvmStatic
    fun <T : Comparable<T>> min(a: T, b: T): T = if (a <= b) a else b
}
