package androidx.core.util

import android.util.SparseBooleanArray

inline fun SparseBooleanArray.forEach(action: (key: Int, value: Boolean) -> Unit) {
    for (index in 0 until size()) {
        action(keyAt(index), valueAt(index))
    }
}
