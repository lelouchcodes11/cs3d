package androidx.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

inline fun <reified VM : ViewModel> ComponentActivity.viewModels(
    noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
): Lazy<VM> = lazy {
    val factory = factoryProducer?.invoke() ?: ViewModelProvider.NewInstanceFactory.instance
    ViewModelProvider.create(this, factory, CreationExtras.Empty)[VM::class]
}
