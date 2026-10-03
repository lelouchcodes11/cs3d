@file:JvmName("MaterialMisc6Kt")

package com.google.android.material.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog

open class MaterialAlertDialogBuilder : AlertDialog.Builder {
    constructor(context: Context) : super(context)
    constructor(context: Context, overrideThemeResId: Int) : super(context, overrideThemeResId)

    open fun setBackground(background: android.graphics.drawable.Drawable?): MaterialAlertDialogBuilder = this
    open fun setBackgroundInsetStart(backgroundInsetStart: Int): MaterialAlertDialogBuilder = this
    open fun setBackgroundInsetEnd(backgroundInsetEnd: Int): MaterialAlertDialogBuilder = this
}
