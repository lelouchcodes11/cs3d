package androidx.core.os

import java.util.Locale

class LocaleListCompat private constructor(private val locales: List<Locale>) {
    companion object {
        @JvmStatic
        fun forLanguageTags(list: String?): LocaleListCompat =
            if (list.isNullOrBlank()) getEmptyLocaleList()
            else LocaleListCompat(list.split(',').filter { it.isNotBlank() }.map { Locale.forLanguageTag(it.trim()) })

        @JvmStatic
        fun getEmptyLocaleList(): LocaleListCompat = LocaleListCompat(emptyList())

        @JvmStatic
        fun getDefault(): LocaleListCompat = LocaleListCompat(listOf(Locale.getDefault()))

        @JvmStatic
        fun getAdjustedDefault(): LocaleListCompat = getDefault()

        @JvmStatic
        fun create(vararg localeList: Locale): LocaleListCompat = LocaleListCompat(localeList.toList())
    }

    fun get(index: Int): Locale? = locales.getOrNull(index)
    fun isEmpty(): Boolean = locales.isEmpty()
    fun size(): Int = locales.size
    fun indexOf(locale: Locale?): Int = locales.indexOf(locale)
    fun toLanguageTags(): String = locales.joinToString(",") { it.toLanguageTag() }
    override fun toString(): String = locales.toString()
}
