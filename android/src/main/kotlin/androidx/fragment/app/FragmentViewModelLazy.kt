package androidx.fragment.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras

inline fun <reified VM : ViewModel> Fragment.viewModels(
    noinline ownerProducer: () -> ViewModelStoreOwner = { this },
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> = lazy {
    val owner = ownerProducer()
    val factory = factoryProducer?.invoke() ?: ViewModelProvider.NewInstanceFactory.instance
    ViewModelProvider.create(owner, factory, CreationExtras.Empty)[VM::class]
}

inline fun <reified VM : ViewModel> Fragment.activityViewModels(
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> = viewModels(ownerProducer = { requireActivity() }, factoryProducer = factoryProducer)
