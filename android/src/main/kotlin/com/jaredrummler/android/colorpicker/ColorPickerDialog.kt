package com.jaredrummler.android.colorpicker

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity

class ColorPickerDialog : DialogFragment() {
    class Builder {
        private var dialogId = 0
        private var color = 0
        private var showAlphaSlider = false

        fun setDialogId(dialogId: Int): Builder = apply { this.dialogId = dialogId }
        fun setColor(color: Int): Builder = apply { this.color = color }
        fun setShowAlphaSlider(showAlpha: Boolean): Builder = apply { this.showAlphaSlider = showAlpha }
        fun create(): ColorPickerDialog = ColorPickerDialog()
        fun show(activity: FragmentActivity) {
            create().show(activity.getSupportFragmentManager(), "color_picker_$dialogId")
        }
    }

    companion object {
        fun newBuilder(): Builder = Builder()
    }
}
