package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.lagradost.desktop.runtime.AndroidRuntime

/** Same as the Android Compose LocalContext. The window provides the activity. */
val LocalContext = staticCompositionLocalOf<Context> { AndroidRuntime.context }
