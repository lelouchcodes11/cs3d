package com.lagradost.desktop.runtime.ui

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Message
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.lagradost.desktop.runtime.res.FrameworkResources

/**
 * Builds the view hierarchy of an AlertDialog (title, message, list, custom view, buttons) from
 * regular widgets, like com.android.internal.app.AlertController.
 */
class AlertController(private val context: Context, private val dialog: DialogInterface, private val window: Window) {
    class AlertParams(@JvmField val mContext: Context) {
        @JvmField var mTitle: CharSequence? = null
        @JvmField var mCustomTitleView: View? = null
        @JvmField var mMessage: CharSequence? = null
        @JvmField var mIcon: Drawable? = null
        @JvmField var mPositiveButtonText: CharSequence? = null
        @JvmField var mPositiveButtonListener: DialogInterface.OnClickListener? = null
        @JvmField var mNegativeButtonText: CharSequence? = null
        @JvmField var mNegativeButtonListener: DialogInterface.OnClickListener? = null
        @JvmField var mNeutralButtonText: CharSequence? = null
        @JvmField var mNeutralButtonListener: DialogInterface.OnClickListener? = null
        @JvmField var mCancelable = true
        @JvmField var mOnCancelListener: DialogInterface.OnCancelListener? = null
        @JvmField var mOnDismissListener: DialogInterface.OnDismissListener? = null
        @JvmField var mOnKeyListener: DialogInterface.OnKeyListener? = null
        @JvmField var mItems: Array<CharSequence>? = null
        @JvmField var mAdapter: ListAdapter? = null
        @JvmField var mOnClickListener: DialogInterface.OnClickListener? = null
        @JvmField var mView: View? = null
        @JvmField var mViewLayoutResId = 0
        @JvmField var mCheckedItems: BooleanArray? = null
        @JvmField var mIsMultiChoice = false
        @JvmField var mIsSingleChoice = false
        @JvmField var mCheckedItem = -1
        @JvmField var mOnCheckboxClickListener: DialogInterface.OnMultiChoiceClickListener? = null

        fun apply(a: AlertController) {
            if (mCustomTitleView != null) a.setCustomTitle(mCustomTitleView) else {
                if (mTitle != null) a.setTitle(mTitle)
                if (mIcon != null) a.setIcon(mIcon)
            }
            if (mMessage != null) a.setMessage(mMessage)
            if (mPositiveButtonText != null) a.setButton(DialogInterface.BUTTON_POSITIVE, mPositiveButtonText, mPositiveButtonListener, null)
            if (mNegativeButtonText != null) a.setButton(DialogInterface.BUTTON_NEGATIVE, mNegativeButtonText, mNegativeButtonListener, null)
            if (mNeutralButtonText != null) a.setButton(DialogInterface.BUTTON_NEUTRAL, mNeutralButtonText, mNeutralButtonListener, null)
            if (mItems != null || mAdapter != null) a.setList(this)
            if (mView != null) a.setView(mView)
            else if (mViewLayoutResId != 0) a.setView(android.view.LayoutInflater.from(mContext).inflate(mViewLayoutResId, null, false))
        }
    }

    private var title: CharSequence? = null
    private var customTitle: View? = null
    private var message: CharSequence? = null
    private var icon: Drawable? = null
    private var customView: View? = null
    private val buttonTexts = HashMap<Int, CharSequence?>()
    private val buttonListeners = HashMap<Int, DialogInterface.OnClickListener?>()
    private val buttons = HashMap<Int, Button>()
    private var listParams: AlertParams? = null
    var listView: ListView? = null
        private set
    private var installed = false

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    fun setTitle(t: CharSequence?) {
        title = t
        if (installed) titleView?.let { it.setText(t); it.setVisibility(if (t.isNullOrEmpty()) View.GONE else View.VISIBLE) }
    }

    fun setCustomTitle(v: View?) {
        customTitle = v
    }

    fun setMessage(m: CharSequence?) {
        message = m
        if (installed) messageView?.let { it.setText(m); it.setVisibility(if (m.isNullOrEmpty()) View.GONE else View.VISIBLE) }
    }

    fun setIcon(d: Drawable?) {
        icon = d
    }

    fun setView(v: View?) {
        customView = v
    }

    fun setButton(which: Int, text: CharSequence?, listener: DialogInterface.OnClickListener?, msg: Message?) {
        buttonTexts[which] = text
        buttonListeners[which] = listener
        buttons[which]?.let {
            it.setText(text)
            it.setVisibility(if (text.isNullOrEmpty()) View.GONE else View.VISIBLE)
        }
    }

    fun setList(p: AlertParams) {
        listParams = p
    }

    /**
     * What a native UI needs to show this dialog itself (title, message, plain item list, buttons); null when
     * the dialog has custom views or adapters, which only the View renderer can show.
     */
    class SimpleModel(
        val title: CharSequence?,
        val message: CharSequence?,
        val items: List<String>?,
        val multi: Boolean,
        val single: Boolean,
        val checked: BooleanArray?,
        val checkedItem: Int,
        val buttons: Map<Int, String>,
    )

    fun simpleModel(): SimpleModel? {
        if (customView != null || customTitle != null) return null
        val p = listParams
        if (p != null && (p.mAdapter != null || p.mItems == null)) return null
        return SimpleModel(
            title, message, p?.mItems?.map { it.toString() }, p?.mIsMultiChoice == true, p?.mIsSingleChoice == true,
            p?.mCheckedItems?.copyOf(), p?.mCheckedItem ?: -1,
            buttonTexts.filterValues { !it.isNullOrEmpty() }.mapValues { it.value.toString() },
        )
    }

    /** A native UI pressed a button: the listener runs, then the dialog closes (like the view button does) */
    fun clickButton(which: Int) {
        buttonListeners[which]?.onClick(dialog, which)
        dialog.dismiss()
    }

    /** A native UI picked a list item */
    fun clickItem(index: Int) {
        val p = listParams ?: return
        p.mOnClickListener?.onClick(dialog, index)
        if (!p.mIsSingleChoice && !p.mIsMultiChoice) dialog.dismiss()
    }

    fun toggleItem(index: Int, checked: Boolean) {
        val p = listParams ?: return
        p.mCheckedItems?.let { if (index < it.size) it[index] = checked }
        p.mOnCheckboxClickListener?.onClick(dialog, index, checked)
    }

    fun cancelDialog() {
        (dialog as? android.app.Dialog)?.cancel() ?: dialog.dismiss()
    }

    /** Buttons exist right after creation like on Android (dialogs are created before show) */
    fun getButton(which: Int): Button? {
        if (!installed) installContent()
        return buttons[which]
    }

    private var titleView: TextView? = null
    private var messageView: TextView? = null

    fun installContent() {
        if (installed) return
        installed = true
        val root = LinearLayout(context)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setTag("alert_dialog_root")
        root.setLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Title
        val ct = customTitle
        if (ct != null) {
            root.addView(ct)
        } else {
            val titleRow = LinearLayout(context)
            titleRow.setOrientation(LinearLayout.HORIZONTAL)
            titleRow.setGravity(Gravity.CENTER_VERTICAL)
            titleRow.setPadding(dp(24f), dp(22f), dp(24f), dp(8f))
            icon?.let { d ->
                val iv = ImageView(context)
                iv.setImageDrawable(d)
                val lp = LinearLayout.LayoutParams(dp(32f), dp(32f))
                lp.rightMargin = dp(12f)
                titleRow.addView(iv, lp)
            }
            val tv = TextView(context)
            tv.setId(FrameworkResources.ID_TITLE)
            tv.setText(title)
            tv.setTextSize(20f)
            tv.setTypeface(Typeface.DEFAULT_BOLD)
            titleRow.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            titleRow.setVisibility(if (title.isNullOrEmpty() && icon == null) View.GONE else View.VISIBLE)
            titleView = tv
            root.addView(titleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Message
        val scroll = ScrollView(context)
        val msg = TextView(context)
        msg.setId(FrameworkResources.ID_MESSAGE)
        msg.setText(message)
        msg.setTextSize(15f)
        msg.setTextColor(ThemeBridge.textColorSecondary)
        msg.setPadding(dp(24f), dp(4f), dp(24f), dp(12f))
        messageView = msg
        scroll.addView(msg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.setVisibility(if (message.isNullOrEmpty()) View.GONE else View.VISIBLE)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // List
        listParams?.let { p -> root.addView(buildList(p), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }

        // Custom view
        customView?.let { v ->
            (v.getParent() as? ViewGroup)?.removeView(v)
            val frame = FrameLayout(context)
            frame.setId(0x01020018) // android.R.id.custom
            frame.addView(v, v.layoutParamsOrNull() ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        // Buttons: neutral on the left, negative and positive on the right
        val bar = LinearLayout(context)
        bar.setOrientation(LinearLayout.HORIZONTAL)
        bar.setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
        bar.setPadding(dp(12f), dp(4f), dp(12f), dp(12f))
        val order = listOf(DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_POSITIVE)
        val ids = mapOf(
            DialogInterface.BUTTON_POSITIVE to FrameworkResources.ID_BUTTON1,
            DialogInterface.BUTTON_NEGATIVE to FrameworkResources.ID_BUTTON2,
            DialogInterface.BUTTON_NEUTRAL to FrameworkResources.ID_BUTTON3,
        )
        for (which in order) {
            val b = Button(context)
            b.setTag("alert_dialog_button")
            b.setId(ids[which]!!)
            b.setText(buttonTexts[which])
            b.setAllCaps(false)
            b.setTextColor(ThemeBridge.colorPrimary)
            b.setBackground(null)
            b.setVisibility(if (buttonTexts[which].isNullOrEmpty()) View.GONE else View.VISIBLE)
            b.setOnClickListener {
                buttonListeners[which]?.onClick(dialog, which)
                dialog.dismiss()
            }
            buttons[which] = b
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (which == DialogInterface.BUTTON_NEUTRAL) {
                bar.addView(b, lp)
                bar.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            } else bar.addView(b, lp)
        }
        bar.setVisibility(if (buttonTexts.values.all { it.isNullOrEmpty() }) View.GONE else View.VISIBLE)
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        window.setContentView(root)
    }

    private fun buildList(p: AlertParams): ListView {
        val lv = ListView(context)
        val items = p.mItems
        val adapter: ListAdapter = p.mAdapter ?: ArrayAdapter(
            context,
            when {
                p.mIsMultiChoice -> FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_MULTIPLE_CHOICE
                p.mIsSingleChoice -> FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_SINGLE_CHOICE
                else -> FrameworkResources.LAYOUT_SELECT_DIALOG_ITEM
            },
            FrameworkResources.ID_TEXT1,
            (items ?: emptyArray()).toMutableList()
        )
        lv.setAdapter(adapter)
        when {
            p.mIsMultiChoice -> {
                lv.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE)
                p.mCheckedItems?.forEachIndexed { i, c -> if (c) lv.setItemChecked(i, true) }
                // AbsListView.performItemClick already toggled the item, like Android's AlertController
                lv.setOnItemClickListener { _, _, position, _ ->
                    val checked = lv.isItemChecked(position)
                    p.mCheckedItems?.let { if (position < it.size) it[position] = checked }
                    p.mOnCheckboxClickListener?.onClick(dialog, position, checked)
                }
            }
            p.mIsSingleChoice -> {
                lv.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE)
                if (p.mCheckedItem >= 0) lv.setItemChecked(p.mCheckedItem, true)
                lv.setOnItemClickListener { _, _, position, _ ->
                    p.mOnClickListener?.onClick(dialog, position)
                }
            }
            else -> lv.setOnItemClickListener { _, _, position, _ ->
                p.mOnClickListener?.onClick(dialog, position)
                dialog.dismiss()
            }
        }
        listView = lv
        return lv
    }

    @Suppress("unused")
    private fun unused(v: AdapterView<*>, c: CheckedTextView) = Unit
}
