package androidx.activity.compose

import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

val LocalOnBackPressedDispatcherOwner: ProvidableCompositionLocal<OnBackPressedDispatcherOwner?> =
    staticCompositionLocalOf { null }
