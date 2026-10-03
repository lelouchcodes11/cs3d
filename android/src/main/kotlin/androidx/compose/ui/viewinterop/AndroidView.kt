package androidx.compose.ui.viewinterop

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lagradost.desktop.runtime.ui.AndroidViewHost

@Composable
fun <T : View> AndroidView(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {}
) {
    val context = LocalContext.current
    val view = remember { factory(context) }
    update(view)
    AndroidViewHost(view, modifier)
}
