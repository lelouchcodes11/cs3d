package androidx.appcompat.app

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

open class AppCompatDialogFragment : DialogFragment {
    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AppCompatDialog(requireContext(), getTheme())
}
