package com.lagradost.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import java.awt.EventQueue

/** The snackbar on screen; like Material, a new one replaces the current one */
object Snackbars {
    var current by mutableStateOf<Snackbar?>(null)
        private set

    fun show(bar: Snackbar) {
        EventQueue.invokeLater {
            val previous = current
            current = bar
            if (previous != null && previous !== bar) {
                previous.dispatchDismiss(BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE)
            }
            bar.dispatchShown()
        }
    }

    fun remove(bar: BaseTransientBottomBar<*>) {
        EventQueue.invokeLater {
            if (current === bar) current = null
        }
    }
}

/** Material durations: LENGTH_SHORT 1500 ms, LENGTH_LONG 2750 ms, else the value in ms */
private fun durationMillis(duration: Int): Long? = when (duration) {
    Snackbar.LENGTH_INDEFINITE -> null
    Snackbar.LENGTH_SHORT -> 1500L
    Snackbar.LENGTH_LONG -> 2750L
    else -> duration.toLong()
}

@Composable
fun SnackbarHost() {
    val bar = Snackbars.current
    Box(Modifier.fillMaxSize().padding(bottom = 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = bar != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            if (bar != null) SnackbarContent(bar)
        }
    }
    if (bar != null) {
        LaunchedEffect(bar) {
            val millis = durationMillis(bar.getDuration()) ?: return@LaunchedEffect
            delay(millis)
            bar.dispatchDismiss(BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT)
        }
    }
}

@Composable
private fun SnackbarContent(bar: Snackbar) {
    // SnackbarHelper colors: primaryBlackBackground, textColor, colorPrimary
    val background = bar.backgroundTintOverride?.let { Color(it) } ?: MaterialTheme.colorScheme.inverseSurface
    val textColor = bar.textColorOverride?.let { Color(it) } ?: MaterialTheme.colorScheme.inverseOnSurface
    val actionColor = bar.actionTextColorOverride?.let { Color(it) } ?: MaterialTheme.colorScheme.inversePrimary
    Surface(
        color = background,
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(min = 288.dp, max = 640.dp).padding(horizontal = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                bar.getText()?.toString() ?: "",
                color = textColor,
                maxLines = bar.getTextMaxLines(),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            )
            val action = bar.getActionText()
            if (action != null) {
                TextButton(onClick = { bar.performAction() }) {
                    Text(action.toString(), color = actionColor)
                }
            }
        }
    }
}
