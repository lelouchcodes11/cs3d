package androidx.core.os

import android.content.res.Configuration

object ConfigurationCompat {
    @JvmStatic
    fun getLocales(configuration: Configuration): LocaleListCompat =
        LocaleListCompat.forLanguageTags(configuration.getLocales().toLanguageTags())
}
