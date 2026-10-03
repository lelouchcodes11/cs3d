package com.lagradost.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Android style toast (layout/toast.xml): rounded dark box at the bottom center */
@Composable
fun ToastHost() {
    val toast = Toasts.current.lastOrNull()
    Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = toast != null, enter = fadeIn(), exit = fadeOut()) {
            if (toast != null) {
                Text(
                    toast.text,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 600.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
    if (toast != null) {
        LaunchedEffect(toast.id) {
            // Toast.LENGTH_LONG = 3.5s, LENGTH_SHORT = 2s
            delay(if (toast.long) 3500 else 2000)
            Toasts.dismiss(toast)
        }
    }
}
