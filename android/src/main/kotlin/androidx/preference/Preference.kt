package androidx.preference

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

open class Preference @JvmOverloads constructor(
    val context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) {
    var key: String? = null
    var title: CharSequence? = null
    var summary: CharSequence? = null
    var icon: Drawable? = null
    var isVisible: Boolean = true
    var isEnabled: Boolean = true
    var isSelectable: Boolean = true
    var order: Int = 0
    var layoutResource: Int = 0
    var widgetLayoutResource: Int = 0
    var preferenceManager: PreferenceManager? = null

    private var onPreferenceChangeListener: OnPreferenceChangeListener? = null
    private var onPreferenceClickListener: OnPreferenceClickListener? = null

    fun getOnPreferenceChangeListener(): OnPreferenceChangeListener? = onPreferenceChangeListener
    fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {
        this.onPreferenceChangeListener = listener
    }

    fun getOnPreferenceClickListener(): OnPreferenceClickListener? = onPreferenceClickListener
    fun setOnPreferenceClickListener(listener: OnPreferenceClickListener?) {
        this.onPreferenceClickListener = listener
    }

    fun getSharedPreferences(): SharedPreferences? =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun callChangeListener(newValue: Any?): Boolean =
        onPreferenceChangeListener?.onPreferenceChange(this, newValue) ?: true

    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }

    fun interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }
}

open class PreferenceGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    private val preferences = mutableListOf<Preference>()

    fun getPreferenceCount(): Int = preferences.size
    fun getPreference(index: Int): Preference = preferences[index]

    fun addPreference(preference: Preference): Boolean {
        preferences.add(preference)
        return true
    }

    fun removePreference(preference: Preference): Boolean = preferences.remove(preference)

    fun removeAll() {
        preferences.clear()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Preference?> findPreference(key: CharSequence): T? {
        val keyStr = key.toString()
        for (pref in preferences) {
            if (pref.key == keyStr) return pref as T
            if (pref is PreferenceGroup) {
                val found: T? = pref.findPreference(key)
                if (found != null) return found
            }
        }
        return null
    }
}

open class PreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes)

open class PreferenceScreen @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : PreferenceGroup(context, attrs, defStyleAttr, defStyleRes)

abstract class TwoStatePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    open var isChecked: Boolean = false
    open var summaryOn: CharSequence? = null
    open var summaryOff: CharSequence? = null
}

open class SwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : TwoStatePreference(context, attrs, defStyleAttr, defStyleRes) {
    open var switchTextOn: CharSequence? = null
    open var switchTextOff: CharSequence? = null
}

open class SwitchPreferenceCompat @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : TwoStatePreference(context, attrs, defStyleAttr, defStyleRes) {
    open var switchTextOn: CharSequence? = null
    open var switchTextOff: CharSequence? = null
}

open class CheckBoxPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : TwoStatePreference(context, attrs, defStyleAttr, defStyleRes)

open class SeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    open var value: Int = 0
    open var min: Int = 0
    open var max: Int = 100
    open var updatesContinuously: Boolean = false
    open var seekBarIncrement: Int = 1
}

abstract class DialogPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    open var dialogTitle: CharSequence? = null
    open var dialogMessage: CharSequence? = null
    open var dialogLayoutResource: Int = 0
    open var positiveButtonText: CharSequence? = null
    open var negativeButtonText: CharSequence? = null
}

open class ListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {
    open var entries: Array<CharSequence>? = null
    open var entryValues: Array<CharSequence>? = null
    open var value: String? = null

    fun setValueIndex(index: Int) {
        value = entryValues?.getOrNull(index)?.toString()
    }

    fun findIndexOfValue(value: String?): Int {
        val vals = entryValues ?: return -1
        return vals.indexOfFirst { it.toString() == value }
    }
}

open class EditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {
    open var text: String? = null
}

abstract class PreferenceFragmentCompat : Fragment() {
    open var preferenceScreen: PreferenceScreen? = null

    abstract fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)

    open fun setPreferencesFromResource(preferencesResId: Int, key: String?) {}
    open fun addPreferencesFromResource(preferencesResId: Int) {}

    @Suppress("UNCHECKED_CAST")
    open fun <T : Preference?> findPreference(key: CharSequence): T? =
        preferenceScreen?.findPreference(key)

    open val listView: RecyclerView?
        get() = getView()?.findViewById(android.R.id.list)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        onCreatePreferences(savedInstanceState, null)
        return null
    }
}
