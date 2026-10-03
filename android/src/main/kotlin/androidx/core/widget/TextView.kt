package androidx.core.widget

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView

inline fun TextView.doOnTextChanged(
    crossinline action: (text: CharSequence?, start: Int, before: Int, count: Int) -> Unit
): TextWatcher = addTextChangedListener(onTextChanged = action)

inline fun TextView.doAfterTextChanged(
    crossinline action: (text: Editable?) -> Unit
): TextWatcher = addTextChangedListener(afterTextChanged = action)

inline fun TextView.doBeforeTextChanged(
    crossinline action: (text: CharSequence?, start: Int, count: Int, after: Int) -> Unit
): TextWatcher = addTextChangedListener(beforeTextChanged = action)

inline fun TextView.addTextChangedListener(
    crossinline beforeTextChanged: (text: CharSequence?, start: Int, count: Int, after: Int) -> Unit = { _, _, _, _ -> },
    crossinline onTextChanged: (text: CharSequence?, start: Int, before: Int, count: Int) -> Unit = { _, _, _, _ -> },
    crossinline afterTextChanged: (text: Editable?) -> Unit = {}
): TextWatcher {
    val listener = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            beforeTextChanged(s, start, count, after)
        }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged(s, start, before, count)
        }
        override fun afterTextChanged(s: Editable?) {
            afterTextChanged(s)
        }
    }
    addTextChangedListener(listener)
    return listener
}
