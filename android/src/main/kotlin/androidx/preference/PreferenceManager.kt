package androidx.preference

import android.content.Context
import android.content.SharedPreferences

/** Same file and name as AndroidX: "<package>_preferences" */
class PreferenceManager private constructor() {
    companion object {
        const val KEY_HAS_SET_DEFAULT_VALUES = "_has_set_default_values"

        @JvmStatic
        fun getDefaultSharedPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE)

        @JvmStatic
        fun getDefaultSharedPreferencesName(context: Context): String = context.packageName + "_preferences"

        @JvmStatic
        fun getDefaultSharedPreferencesMode(): Int = Context.MODE_PRIVATE
    }
}
