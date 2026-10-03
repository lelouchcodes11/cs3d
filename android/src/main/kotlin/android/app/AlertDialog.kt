package android.app

import android.content.Context
import android.content.DialogInterface
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListAdapter
import android.widget.ListView
import com.lagradost.desktop.runtime.ui.AlertController

/**
 * android.app.AlertDialog. The content is built from real widgets (title, message, list, custom
 * view and button bar) by [AlertController], so extensions can look up and customize the buttons,
 * list and views exactly like on Android.
 */
open class AlertDialog : Dialog, DialogInterface {
    companion object {
        const val THEME_DEVICE_DEFAULT_DARK = 4
        const val THEME_DEVICE_DEFAULT_LIGHT = 5
        const val THEME_HOLO_DARK = 2
        const val THEME_HOLO_LIGHT = 3
        const val THEME_TRADITIONAL = 1
    }

    @JvmField
    val mAlert: AlertController

    protected constructor(context: Context) : this(context, 0)
    protected constructor(context: Context, cancelable: Boolean, cancelListener: DialogInterface.OnCancelListener?) : this(context, 0) {
        setCancelable(cancelable)
        setOnCancelListener(cancelListener)
    }

    protected constructor(context: Context, themeResId: Int) : super(context, themeResId) {
        mAlert = AlertController(getContext(), this, getWindow())
    }

    open fun getButton(whichButton: Int): Button? = mAlert.getButton(whichButton)
    open fun getListView(): ListView? = mAlert.listView

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        mAlert.setTitle(title)
    }

    open fun setCustomTitle(customTitleView: View?) = mAlert.setCustomTitle(customTitleView)
    open fun setMessage(message: CharSequence?) = mAlert.setMessage(message)
    open fun setView(view: View?) = mAlert.setView(view)
    open fun setView(view: View?, viewSpacingLeft: Int, viewSpacingTop: Int, viewSpacingRight: Int, viewSpacingBottom: Int) = mAlert.setView(view)
    open fun setButton(whichButton: Int, text: CharSequence?, listener: DialogInterface.OnClickListener?) = mAlert.setButton(whichButton, text, listener, null)
    open fun setButton(whichButton: Int, text: CharSequence?, msg: android.os.Message?) = mAlert.setButton(whichButton, text, null, msg)
    open fun setIcon(resId: Int) = mAlert.setIcon(if (resId == 0) null else getContext().getDrawable(resId))
    open fun setIcon(icon: Drawable?) = mAlert.setIcon(icon)
    open fun setIconAttribute(attrId: Int) {}
    open fun setInverseBackgroundForced(forceInverseBackground: Boolean) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAlert.installContent()
    }

    open class Builder {
        private val P: AlertController.AlertParams
        private val mTheme: Int

        constructor(context: Context) : this(context, 0)
        constructor(context: Context, themeResId: Int) {
            P = AlertController.AlertParams(context)
            mTheme = themeResId
        }

        open fun getContext(): Context = P.mContext
        @get:JvmName("builderContext")
        val context: Context get() = P.mContext
        open fun setTitle(titleId: Int): Builder = apply { P.mTitle = P.mContext.getText(titleId) }
        open fun setTitle(title: CharSequence?): Builder = apply { P.mTitle = title }
        open fun setCustomTitle(customTitleView: View?): Builder = apply { P.mCustomTitleView = customTitleView }
        open fun setMessage(messageId: Int): Builder = apply { P.mMessage = P.mContext.getText(messageId) }
        open fun setMessage(message: CharSequence?): Builder = apply { P.mMessage = message }
        open fun setIcon(iconId: Int): Builder = apply { P.mIcon = if (iconId == 0) null else P.mContext.getDrawable(iconId) }
        open fun setIcon(icon: Drawable?): Builder = apply { P.mIcon = icon }
        open fun setIconAttribute(attrId: Int): Builder = this
        open fun setPositiveButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = setPositiveButton(P.mContext.getText(textId), listener)
        open fun setPositiveButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = apply {
            P.mPositiveButtonText = text
            P.mPositiveButtonListener = listener
        }

        open fun setNegativeButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = setNegativeButton(P.mContext.getText(textId), listener)
        open fun setNegativeButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = apply {
            P.mNegativeButtonText = text
            P.mNegativeButtonListener = listener
        }

        open fun setNeutralButton(textId: Int, listener: DialogInterface.OnClickListener?): Builder = setNeutralButton(P.mContext.getText(textId), listener)
        open fun setNeutralButton(text: CharSequence?, listener: DialogInterface.OnClickListener?): Builder = apply {
            P.mNeutralButtonText = text
            P.mNeutralButtonListener = listener
        }

        open fun setCancelable(cancelable: Boolean): Builder = apply { P.mCancelable = cancelable }
        open fun setOnCancelListener(onCancelListener: DialogInterface.OnCancelListener?): Builder = apply { P.mOnCancelListener = onCancelListener }
        open fun setOnDismissListener(onDismissListener: DialogInterface.OnDismissListener?): Builder = apply { P.mOnDismissListener = onDismissListener }
        open fun setOnKeyListener(onKeyListener: DialogInterface.OnKeyListener?): Builder = apply { P.mOnKeyListener = onKeyListener }
        open fun setItems(itemsId: Int, listener: DialogInterface.OnClickListener?): Builder = setItems(P.mContext.resources.getTextArray(itemsId), listener)
        open fun setItems(items: Array<out CharSequence>?, listener: DialogInterface.OnClickListener?): Builder = apply {
            P.mItems = items?.map { it }?.toTypedArray()
            P.mOnClickListener = listener
        }

        open fun setAdapter(adapter: ListAdapter?, listener: DialogInterface.OnClickListener?): Builder = apply {
            P.mAdapter = adapter
            P.mOnClickListener = listener
        }

        open fun setCursor(cursor: Cursor?, listener: DialogInterface.OnClickListener?, labelColumn: String?): Builder = this

        open fun setMultiChoiceItems(itemsId: Int, checkedItems: BooleanArray?, listener: DialogInterface.OnMultiChoiceClickListener?): Builder =
            setMultiChoiceItems(P.mContext.resources.getTextArray(itemsId), checkedItems, listener)

        open fun setMultiChoiceItems(items: Array<out CharSequence>?, checkedItems: BooleanArray?, listener: DialogInterface.OnMultiChoiceClickListener?): Builder = apply {
            P.mItems = items?.map { it }?.toTypedArray()
            P.mOnCheckboxClickListener = listener
            P.mCheckedItems = checkedItems
            P.mIsMultiChoice = true
        }

        open fun setSingleChoiceItems(itemsId: Int, checkedItem: Int, listener: DialogInterface.OnClickListener?): Builder =
            setSingleChoiceItems(P.mContext.resources.getTextArray(itemsId), checkedItem, listener)

        open fun setSingleChoiceItems(items: Array<out CharSequence>?, checkedItem: Int, listener: DialogInterface.OnClickListener?): Builder = apply {
            P.mItems = items?.map { it }?.toTypedArray()
            P.mOnClickListener = listener
            P.mCheckedItem = checkedItem
            P.mIsSingleChoice = true
        }

        open fun setSingleChoiceItems(adapter: ListAdapter?, checkedItem: Int, listener: DialogInterface.OnClickListener?): Builder = apply {
            P.mAdapter = adapter
            P.mOnClickListener = listener
            P.mCheckedItem = checkedItem
            P.mIsSingleChoice = true
        }

        open fun setOnItemSelectedListener(listener: android.widget.AdapterView.OnItemSelectedListener?): Builder = this
        open fun setView(layoutResId: Int): Builder = apply {
            P.mView = null
            P.mViewLayoutResId = layoutResId
        }

        open fun setView(view: View?): Builder = apply {
            P.mView = view
            P.mViewLayoutResId = 0
        }

        open fun setInverseBackgroundForced(useInverseBackground: Boolean): Builder = this

        open fun create(): AlertDialog {
            val dialog = AlertDialog(P.mContext, mTheme)
            P.apply(dialog.mAlert)
            dialog.setCancelable(P.mCancelable)
            if (P.mCancelable) dialog.setCanceledOnTouchOutside(true)
            dialog.setOnCancelListener(P.mOnCancelListener)
            dialog.setOnDismissListener(P.mOnDismissListener)
            if (P.mOnKeyListener != null) dialog.setOnKeyListener(P.mOnKeyListener)
            return dialog
        }

        open fun show(): AlertDialog {
            val dialog = create()
            dialog.show()
            return dialog
        }
    }
}
