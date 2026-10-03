package com.jaredrummler.android.colorpicker

interface ColorPickerDialogListener {
    fun onColorSelected(dialogId: Int, color: Int)
    fun onDialogDismissed(dialogId: Int)
}
