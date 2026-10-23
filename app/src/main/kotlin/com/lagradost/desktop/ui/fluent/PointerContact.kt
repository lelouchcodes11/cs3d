package com.lagradost.desktop.ui.fluent

import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed

/** Real touch events have no mouse-button bit; promoted Windows touches still use the mouse path. */
internal val PointerEvent.primaryContactPressed: Boolean
    get() = buttons.isPrimaryPressed || changes.any { it.type == PointerType.Touch && it.pressed }
