package androidx.core.text

import android.text.Spanned
import android.text.SpannedString

inline fun CharSequence.toSpanned(): Spanned = SpannedString(this)
