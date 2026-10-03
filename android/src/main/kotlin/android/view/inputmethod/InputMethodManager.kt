package android.view.inputmethod

import android.os.IBinder
import android.view.View

open class InputMethodManager {
    companion object {
        const val SHOW_IMPLICIT = 0x0001
        const val SHOW_FORCED = 0x0002
        const val HIDE_IMPLICIT_ONLY = 0x0001
        const val HIDE_NOT_ALWAYS = 0x0002
        const val RESULT_UNCHANGED_SHOWN = 0
    }

    /** Desktop has a physical keyboard: showing the soft input just focuses the view */
    open fun showSoftInput(view: View?, flags: Int): Boolean {
        view?.requestFocus()
        return true
    }

    open fun hideSoftInputFromWindow(windowToken: IBinder?, flags: Int): Boolean = true
    open fun toggleSoftInput(showFlags: Int, hideFlags: Int) {}
    open fun isActive(view: View?): Boolean = view?.isFocused() == true
    open fun isActive(): Boolean = true
    open fun isAcceptingText(): Boolean = true
    open fun restartInput(view: View?) {}
}

object EditorInfo {
    const val IME_ACTION_UNSPECIFIED = 0x00000000
    const val IME_ACTION_NONE = 0x00000001
    const val IME_ACTION_GO = 0x00000002
    const val IME_ACTION_SEARCH = 0x00000003
    const val IME_ACTION_SEND = 0x00000004
    const val IME_ACTION_NEXT = 0x00000005
    const val IME_ACTION_DONE = 0x00000006
    const val IME_ACTION_PREVIOUS = 0x00000007
    const val IME_FLAG_NO_FULLSCREEN = 0x2000000
    const val IME_FLAG_NO_EXTRACT_UI = 0x10000000
    const val IME_MASK_ACTION = 0x000000ff
    const val TYPE_NULL = 0
}
