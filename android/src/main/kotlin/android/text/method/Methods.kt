@file:JvmName("MethodsKt")

package android.text.method

import android.view.View

interface MovementMethod
interface TransformationMethod {
    fun getTransformation(source: CharSequence?, view: View?): CharSequence?
    fun onFocusChanged(view: View?, sourceText: CharSequence?, focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {}
}

interface KeyListener {
    fun getInputType(): Int
}

open class BaseMovementMethod : MovementMethod
open class ScrollingMovementMethod : BaseMovementMethod() {
    companion object {
        @JvmStatic
        fun getInstance(): MovementMethod = ScrollingMovementMethod()
    }
}

open class LinkMovementMethod : ScrollingMovementMethod() {
    companion object {
        private val instance = LinkMovementMethod()

        @JvmStatic
        fun getInstance(): MovementMethod = instance
    }
}

open class ArrowKeyMovementMethod : BaseMovementMethod() {
    companion object {
        @JvmStatic
        fun getInstance(): MovementMethod = ArrowKeyMovementMethod()
    }
}

open class PasswordTransformationMethod : TransformationMethod {
    override fun getTransformation(source: CharSequence?, view: View?): CharSequence? = source?.let { "•".repeat(it.length) }

    companion object {
        private val instance = PasswordTransformationMethod()

        @JvmStatic
        fun getInstance(): PasswordTransformationMethod = instance
    }
}

open class HideReturnsTransformationMethod : TransformationMethod {
    override fun getTransformation(source: CharSequence?, view: View?): CharSequence? = source

    companion object {
        private val instance = HideReturnsTransformationMethod()

        @JvmStatic
        fun getInstance(): HideReturnsTransformationMethod = instance
    }
}

open class SingleLineTransformationMethod : TransformationMethod {
    override fun getTransformation(source: CharSequence?, view: View?): CharSequence? = source?.toString()?.replace('\n', ' ')

    companion object {
        @JvmStatic
        fun getInstance(): SingleLineTransformationMethod = SingleLineTransformationMethod()
    }
}

open class DigitsKeyListener : KeyListener {
    override fun getInputType(): Int = android.text.InputType.TYPE_CLASS_NUMBER

    companion object {
        @JvmStatic
        fun getInstance(): DigitsKeyListener = DigitsKeyListener()

        @JvmStatic
        fun getInstance(accepted: String?): DigitsKeyListener = DigitsKeyListener()

        @JvmStatic
        fun getInstance(sign: Boolean, decimal: Boolean): DigitsKeyListener = DigitsKeyListener()
    }
}
