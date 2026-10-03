package com.lagradost.cloudstream3.mvvm

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.doOnAttach
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.viewbinding.ViewBinding
import com.lagradost.cloudstream3.ui.BaseFragment

/** NOTE: Only one observer at a time per value */
fun <T> ComponentActivity.observe(liveData: LiveData<T>, action: (T) -> Unit) {
    observeNullable(liveData) { t -> t?.run(action) }
}

/** NOTE: Only one observer at a time per value */
fun <T> ComponentActivity.observeNullable(liveData: LiveData<T>, action: (T?) -> Unit) {
    liveData.removeObservers(this)
    liveData.observe(this, action)
}

/** NOTE: Only one observer at a time per value */
fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (T) -> Unit) {
    liveData.removeObservers(this)
    liveData.observe(this) { t -> t?.run(action) }
}

/** NOTE: Only one observer at a time per value */
fun <T, V : ViewBinding> BaseFragment<V>.observe(liveData: LiveData<T>, action: (T) -> Unit) {
    observeNullable(liveData) { t -> t?.run(action) }
}

/**
 * Attaches an observable to the root binding, instead of the fragment. This is more efficient as
 * it will not call observe if the view is in the background.
 *
 * NOTE: Only one observer at a time per value
 * */
fun <T, V : ViewBinding> BaseFragment<V>.observeNullable(
    liveData: LiveData<T>, action: (T?) -> Unit
) {
    val root = this.binding?.root
    if (root == null) {
        liveData.removeObservers(this)
        liveData.observe(this, action)
    } else {
        root.doOnAttach { view ->
            val owner: LifecycleOwner = view.findViewTreeLifecycleOwner() ?: this@observeNullable
            liveData.removeObservers(owner)
            liveData.observe(owner, action)
        }
    }
}

/** NOTE: Only one observer at a time per value */
fun <T> View.observe(liveData: LiveData<T>, action: (T) -> Unit) {
    observeNullable(liveData) { t -> t?.run(action) }
}

/** NOTE: Only one observer at a time per value */
fun <T> View.observeNullable(liveData: LiveData<T>, action: (T?) -> Unit) {
    doOnAttach { view ->
        val owner: LifecycleOwner? = view.findViewTreeLifecycleOwner()
        if (owner != null) {
            liveData.removeObservers(owner)
            liveData.observe(owner, action)
        }
    }
}

/**
 * Desktop compose bridge for the upstream view models: the latest value of a [LiveData] as compose
 * state, observed for as long as the composable is in the composition.
 */
@Composable
fun <T> LiveData<T>.observeAsState(): State<T?> = observeAsState(value)

@Composable
fun <R, T : R> LiveData<T>.observeAsState(initial: R): State<R> {
    val state = remember(this) { mutableStateOf(if (isInitialized) value as R else initial) }
    DisposableEffect(this) {
        val observer = Observer<T> { state.value = it }
        observeForever(observer)
        onDispose { removeObserver(observer) }
    }
    return state
}
