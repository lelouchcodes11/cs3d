@file:JvmName("AndroidAlertDialog_androidKt")
@file:Suppress("FunctionName", "unused")

package androidx.compose.material3

import androidx.compose.runtime.Composer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.DialogProperties

/**
 * Android keeps Material 3's AlertDialog in the class AndroidAlertDialog_androidKt; Compose for desktop has the same function in AlertDialog_skikoKt.
 * Extensions built for Android call it by Android's class name: AnimePahe's confirm dialogs (Clear CF cookies) failed with NoClassDefFoundError, and an
 * error inside the window's drawing froze the whole app. This is the compiled form of that call (the Compose compiler's composer and change flags
 * included), handed to the desktop function as it is.
 */
fun `AlertDialog-Oix01E0`(
    onDismissRequest: Function0<Unit>?,
    confirmButton: Function2<Composer, Int, Unit>?,
    modifier: Modifier?,
    dismissButton: Function2<Composer, Int, Unit>?,
    icon: Function2<Composer, Int, Unit>?,
    title: Function2<Composer, Int, Unit>?,
    text: Function2<Composer, Int, Unit>?,
    shape: Shape?,
    containerColor: Long,
    iconContentColor: Long,
    titleContentColor: Long,
    textContentColor: Long,
    tonalElevation: Float,
    properties: DialogProperties?,
    composer: Composer?,
    changed: Int,
    changed1: Int,
    defaults: Int,
) {
    try {
        desktopAlertDialog.invoke(
            null, onDismissRequest, confirmButton, modifier, dismissButton, icon, title, text, shape,
            containerColor, iconContentColor, titleContentColor, textContentColor, tonalElevation, properties, composer, changed, changed1, defaults,
        )
    } catch (e: java.lang.reflect.InvocationTargetException) {
        throw e.targetException
    }
}

private val desktopAlertDialog: java.lang.reflect.Method by lazy {
    Class.forName("androidx.compose.material3.AlertDialog_skikoKt").methods.first { it.name == "AlertDialog-Oix01E0" }
}
