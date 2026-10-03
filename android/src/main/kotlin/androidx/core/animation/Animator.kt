package androidx.core.animation

import android.animation.Animator

inline fun Animator.addListener(
    crossinline onEnd: (animator: Animator) -> Unit = {},
    crossinline onStart: (animator: Animator) -> Unit = {},
    crossinline onCancel: (animator: Animator) -> Unit = {},
    crossinline onRepeat: (animator: Animator) -> Unit = {}
): Animator.AnimatorListener {
    val listener = object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) = onStart(animation)
        override fun onAnimationEnd(animation: Animator) = onEnd(animation)
        override fun onAnimationCancel(animation: Animator) = onCancel(animation)
        override fun onAnimationRepeat(animation: Animator) = onRepeat(animation)
    }
    addListener(listener)
    return listener
}

inline fun Animator.doOnEnd(crossinline action: (animator: Animator) -> Unit): Animator.AnimatorListener =
    addListener(onEnd = action)

inline fun Animator.doOnStart(crossinline action: (animator: Animator) -> Unit): Animator.AnimatorListener =
    addListener(onStart = action)

inline fun Animator.doOnCancel(crossinline action: (animator: Animator) -> Unit): Animator.AnimatorListener =
    addListener(onCancel = action)

inline fun Animator.doOnRepeat(crossinline action: (animator: Animator) -> Unit): Animator.AnimatorListener =
    addListener(onRepeat = action)
