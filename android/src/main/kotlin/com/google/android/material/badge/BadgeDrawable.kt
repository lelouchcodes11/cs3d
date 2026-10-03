package com.google.android.material.badge

/** Small badge state used by the navigation bar. Drawing is the number on the item when visible. */
open class BadgeDrawable {
    var isVisible: Boolean = true
    var number: Int = 0
    var maxCharacterCount: Int = 4
    var backgroundColor: Int = 0
    var badgeTextColor: Int = 0xFFFFFFFF.toInt()

    fun clearNumber() {
        number = 0
    }
}
