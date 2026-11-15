package androidx.activity.compose

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Android's BackHandler: while [enabled], Back (Escape) calls [onBack] instead of closing the page or dialog. An extension's Compose settings (AnimePahe,
 * AniVortex) use it to step back between their own screens. It goes to the dialog that is up (Dialog.backDispatcherInUse), else the activity.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    val callback = remember { object : OnBackPressedCallback(enabled) { override fun handleOnBackPressed() { current() } } }
    SideEffect { callback.isEnabled = enabled }
    val context = LocalContext.current
    val dispatcher = android.app.Dialog.backDispatcherInUse() ?: activityOf(context)?.onBackPressedDispatcher
    DisposableEffect(dispatcher) {
        dispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}

private fun activityOf(context: Context?): ComponentActivity? {
    var c = context
    while (c != null) {
        if (c is ComponentActivity) return c
        c = (c as? ContextWrapper)?.getBaseContext()
    }
    return null
}
