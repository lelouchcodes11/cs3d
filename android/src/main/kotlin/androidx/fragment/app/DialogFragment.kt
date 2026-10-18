package androidx.fragment.app

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
        if (skipRepeatedChallenge()) return
        mDismissed = false
        mShownByMe = true
        val ft = manager.beginTransaction()
        ft.setReorderingAllowed(true)
        ft.add(this, tag)
        ft.commit()
    }

    open fun show(transaction: FragmentTransaction, tag: String?): Int {
        if (skipRepeatedChallenge()) return -1
        mDismissed = false
        mShownByMe = true
        transaction.add(this, tag)
        return transaction.commit()
    }

    open fun showNow(manager: FragmentManager, tag: String?) {
        if (skipRepeatedChallenge()) return
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
        challengeEnded()
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
            dialog.isFragmentDialog = true
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
        if (d != null && !mDismissed) {
            d.show()
            if (isChallengeDialog()) watchChallenge()
        }
    }

    internal override fun performStop() {
        super.performStop()
        mDialog?.hide()
    }

    internal override fun performDestroyView() {
        val d = mDialog
        if (d != null) {
            mDismissed = true
            challengeEnded()
            d.setOnDismissListener(null)
            d.dismiss()
            mDialog = null
        }
        super.performDestroyView()
    }

    // ---- Cloudflare challenge sheets of extensions (Zinkmovies and others): a page the user may have to help, that the extension closes itself once it
    // has the cookie. Some never get that cookie (the site moved to a host the extension does not look at), the sheet then stays for two minutes and,
    // closed by hand or timed out, is asked for again by the next request, and again.
    private var mChallengeNoted = false

    private fun isChallengeDialog(): Boolean = javaClass.name.let { it.contains("Cloudflare", ignoreCase = true) || it.contains("CFBypass", ignoreCase = true) || it.contains("CFWebView", ignoreCase = true) }

    private object Challenges {
        @Volatile var open = 0
        @Volatile var endedAt = 0L
        const val QUIET_MS = 120_000L
        val TITLES = listOf("just a moment", "attention required", "checking your browser", "one more step", "ddos-guard", "verify", "security check")
    }

    /** A sheet asked for within two minutes of the last one's end is not shown: the extension is told (its onDismiss) that there are no cookies */
    private fun skipRepeatedChallenge(): Boolean {
        if (!isChallengeDialog()) return false
        val off = !com.lagradost.desktop.runtime.web.CloudflareGate.solversOn || !com.lagradost.desktop.runtime.web.CloudflareGate.inUseOwner(javaClass)
        if (off || Challenges.open == 0 && System.currentTimeMillis() - Challenges.endedAt < Challenges.QUIET_MS) {
            Log.i("DialogFragment", "${javaClass.simpleName}: " + if (off) "the Cloudflare solvers are off or this is not the extension in use, not shown" else "asked again so soon after the last one, not shown")
            mDismissed = true
            onDismiss(object : DialogInterface {
                override fun cancel() {}
                override fun dismiss() {}
            })
            return true
        }
        Challenges.open++
        mChallengeNoted = false
        return false
    }

    private fun challengeEnded() {
        if (!isChallengeDialog() || mChallengeNoted) return
        mChallengeNoted = true
        if (Challenges.open > 0) Challenges.open--
        Challenges.endedAt = System.currentTimeMillis()
    }

    private fun findWebView(v: View?): android.webkit.WebView? {
        if (v is android.webkit.WebView) return v
        if (v is ViewGroup) for (i in 0 until v.getChildCount()) findWebView(v.getChildAt(i))?.let { return it }
        return null
    }

    /** The page is no challenge any more (the extension's own check missed it): the sheet is closed after a few quiet seconds, at the latest this way */
    private fun watchChallenge() {
        val handler = Handler(Looper.getMainLooper())
        val started = System.currentTimeMillis()
        var calm = 0
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (mDismissed || mDialog == null) return
                val title = findWebView(getView())?.getTitle().orEmpty().trim().lowercase()
                val challenge = title.isEmpty() || Challenges.TITLES.any { title.contains(it) }
                calm = if (challenge) 0 else calm + 1
                if (calm >= 3 && System.currentTimeMillis() - started >= 10_000) {
                    Log.i("DialogFragment", "${javaClass.simpleName}: the page is no challenge any more, closing")
                    dismissAllowingStateLoss()
                    return
                }
                handler.postDelayed(this, 2000)
            }
        }, 4000)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (!mShownByMe) mDismissed = false
    }
}
