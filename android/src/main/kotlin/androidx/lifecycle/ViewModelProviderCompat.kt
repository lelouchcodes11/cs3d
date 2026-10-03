package androidx.lifecycle

import androidx.lifecycle.viewmodel.CreationExtras

/**
 * Lifecycle 2.11 removed `ViewModelProvider(ViewModelStoreOwner)` and `get(Class)`.
 * Upstream still calls both. These are the old entry points.
 */
fun ViewModelProvider(owner: ViewModelStoreOwner): ViewModelProvider =
    ViewModelProvider.create(
        owner,
        ViewModelProvider.NewInstanceFactory.instance,
        CreationExtras.Empty,
    )

operator fun <T : ViewModel> ViewModelProvider.get(modelClass: Class<T>): T = get(modelClass.kotlin)
