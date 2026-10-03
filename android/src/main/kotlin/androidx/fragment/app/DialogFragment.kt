package androidx.fragment.app

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window

open class DialogFragment : Fragment, DialogInterface.OnCancelListener, DialogInterface.OnDismissListener {
    companion object {
        const val STYLE_NORMAL = 0
        const val STYLE_NO_TITLE = 1
        const val STYLE_NO_FRAME = 2
        const val STYLE_NO_INPUT = 3
    }

    private var mStyle = STYLE_NORMAL
    private var mTheme = 0
    private var mCancelable = true
    private var mShowsDialog = true
    private var mDialog: Dialog? = null
    private var mDismissed = false
    private var mShownByMe = false
    private var mContentLayoutId = 0

    constructor() : super()
    constructor(contentLayoutId: Int) : super() {
        mContentLayoutId = contentLayoutId
    }

    open fun show(manager: FragmentManager, tag: String?) {
        mDismissed = false
        mShownByMe = true
        val ft = manager.beginTransaction()
        ft.setReorderingAllowed(true)
        ft.add(this, tag)
        ft.commit()
    }

    open fun show(transaction: FragmentTransaction, tag: String?): Int {
        mDismissed = false
        mShownByMe = true
        transaction.add(this, tag)
        return transaction.commit()
    }

    open fun showNow(manager: FragmentManager, tag: String?) {
        mDismissed = false
        mShownByMe = true
        val ft = manager.beginTransaction()
        ft.add(this, tag)
        ft.commitNow()
    }

    open fun dismiss() = dismissInternal(false, false)
    open fun dismissAllowingStateLoss() = dismissInternal(true, false)
    open fun dismissNow() = dismissInternal(false, true)

    private fun dismissInternal(allowStateLoss: Boolean, immediate: Boolean) {
        if (mDismissed) return
        mDismissed = true
        mDialog?.setOnDismissListener(null)
        mDialog?.dismiss()
        onDismiss(mDialog ?: return removeSelf(immediate))
        removeSelf(immediate)
    }

    private fun removeSelf(immediate: Boolean) {
        val fm = getFragmentManager() ?: return
        val ft = fm.beginTransaction().remove(this)
        if (immediate) ft.commitNow() else ft.commitAllowingStateLoss()
    }

    open fun getDialog(): Dialog? = mDialog
    fun requireDialog(): Dialog = mDialog ?: throw IllegalStateException("DialogFragment $this does not have a Dialog.")
    open fun getTheme(): Int = mTheme
    open fun setStyle(style: Int, theme: Int) {
        mStyle = style
        if (theme != 0) mTheme = theme
    }

    open fun isCancelable(): Boolean = mCancelable
    open fun setCancelable(cancelable: Boolean) {
        mCancelable = cancelable
        mDialog?.setCancelable(cancelable)
    }

    open fun getShowsDialog(): Boolean = mShowsDialog
    open fun setShowsDialog(showsDialog: Boolean) {
        mShowsDialog = showsDialog
    }

    open fun onCreateDialog(savedInstanceState: Bundle?): Dialog = Dialog(requireContext(), getTheme())

    open fun setupDialog(dialog: Dialog, style: Int) {
        when (style) {
            STYLE_NO_INPUT, STYLE_NO_FRAME, STYLE_NO_TITLE -> dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    override fun onCancel(dialog: DialogInterface) {}
    override fun onDismiss(dialog: DialogInterface) {
        if (!mDismissed) {
            mDismissed = true
            removeSelf(false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        if (mContentLayoutId != 0) inflater.inflate(mContentLayoutId, container, false) else null

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val d = mDialog ?: return inflater
        return inflater.cloneInContext(d.getContext())
    }

    internal override fun performCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (mShowsDialog && mDialog == null) {
            val dialog = onCreateDialog(savedInstanceState)
            mDialog = dialog
            setupDialog(dialog, mStyle)
            dialog.setCancelable(mCancelable)
            dialog.setOnCancelListener(this)
            dialog.setOnDismissListener(this)
        }
        val view = super.performCreateView(onGetLayoutInflater(savedInstanceState), container, savedInstanceState)
        val d = mDialog
        if (d != null && view != null && view.getParent() == null) d.setContentView(view)
        return view
    }

    internal override fun performStart() {
        super.performStart()
        val d = mDialog
        if (d != null && !mDismissed) d.show()
    }

    internal override fun performStop() {
        super.performStop()
        mDialog?.hide()
    }

    internal override fun performDestroyView() {
        val d = mDialog
        if (d != null) {
            mDismissed = true
            d.setOnDismissListener(null)
            d.dismiss()
            mDialog = null
        }
        super.performDestroyView()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (!mShownByMe) mDismissed = false
    }
}
