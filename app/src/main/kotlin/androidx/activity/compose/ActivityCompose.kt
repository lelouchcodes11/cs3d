@file:JvmName("ActivityComposeDesktopKt")

package androidx.activity.compose

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.desktop.ui.BackHandlers

val LocalActivity = compositionLocalOf<Activity?> { null }

/** Activity result launcher for compose, dispatched by the desktop [ActivityResultRegistry] */
@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ActivityResultLauncher<I> {
    val currentOnResult = rememberUpdatedState(onResult)
    val activity = LocalActivity.current
    return remember(contract) {
        val registry = (activity as? androidx.activity.result.ActivityResultRegistryOwner)?.activityResultRegistry
            ?: ActivityResultRegistry { activity }
        registry.register("compose_" + System.identityHashCode(contract), contract) { currentOnResult.value(it) }
    }
}

/** Back press handling, same semantics as androidx.activity.compose.BackHandler */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack = rememberUpdatedState(onBack)
    val currentEnabled = rememberUpdatedState(enabled)
    DisposableEffect(Unit) {
        val handler = BackHandlers.Handler({ currentEnabled.value }) { currentOnBack.value() }
        BackHandlers.add(handler)
        onDispose { BackHandlers.remove(handler) }
    }
}

@Suppress("unused")
private val unusedOptions = ActivityOptionsCompat::class.java
