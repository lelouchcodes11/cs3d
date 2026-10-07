package com.lagradost.desktop.ui

import android.app.AlertDialog
import android.app.Dialog
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.desktop.runtime.ui.AndroidViewHost
import com.lagradost.desktop.runtime.ui.drawAndroidDrawable

/**
 * An Android dialog window (AlertDialog, custom Dialog, BottomSheetDialog, DialogFragment) inside
 * the main window: dim behind, placement by the window gravity, width/height from the window
 * layout, cancel on outside click and back/escape like on Android.
 */
@Composable
fun AndroidDialogHost(dialog: Dialog) {
    val window = dialog.getWindow()
    val decor = window.getDecorView()
    val isSheet = dialog is BottomSheetDialog
    val isAlert = dialog is AlertDialog || dialog is androidx.appcompat.app.AlertDialog
    val gravity = window.gravityState.let { if (it == 0) (if (isSheet) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER) else it }
    val layoutWidth = window.layoutWidthState
    val layoutHeight = window.layoutHeightState
    val dimBehind = (window.flagsState and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
    val dim = if (dimBehind) window.dimAmountState.coerceIn(0f, 1f) else 0f
    val background = window.backgroundDrawableState

    // Back / Escape go to the dialog first (Dialog.onBackPressed cancels a cancelable dialog)
    DisposableEffect(dialog) {
        val handler = BackHandlers.Handler({ dialog.isShowing() }) { dialog.onBackPressed() }
        BackHandlers.add(handler)
        onDispose { BackHandlers.remove(handler) }
    }

    var shown by remember(dialog) { mutableStateOf(false) }
    LaunchedEffect(dialog) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f)

    // Decor size follows the window layout (MATCH_PARENT fills the available width)
    val fillWidth = layoutWidth == ViewGroup.LayoutParams.MATCH_PARENT || isSheet || isAlert
    val wantW = if (fillWidth) ViewGroup.LayoutParams.MATCH_PARENT else layoutWidth
    val wantH = if (layoutHeight == ViewGroup.LayoutParams.MATCH_PARENT) ViewGroup.LayoutParams.MATCH_PARENT else layoutHeight
    val current = decor.getLayoutParams()
    if (current == null || current.width != wantW || current.height != wantH) {
        SideEffect { decor.setLayoutParams(ViewGroup.LayoutParams(wantW, wantH)) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = dim * appear))
            .pointerInput(dialog) { detectTapGestures { dialog.onTouchOutside() } },
    ) {
        val alignment = alignmentFor(gravity)
        val maxW = maxWidth
        val width = when {
            isSheet -> min(maxW, 640.dp)
            isAlert -> (maxW * 0.45f).coerceIn(min(maxW - 32.dp, 320.dp), min(maxW - 32.dp, 560.dp))
            layoutWidth == ViewGroup.LayoutParams.MATCH_PARENT -> maxW - 32.dp
            else -> null
        }
        val shape = when {
            isSheet -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            else -> RoundedCornerShape(12.dp)
        }
        val defaultBackground = MaterialTheme.colorScheme.surfaceVariant
        var box: Modifier = Modifier.align(alignment)
        if (!isSheet) box = box.padding(16.dp)
        box = if (width != null) box.width(width) else box.widthIn(min = min(maxW - 32.dp, 280.dp), max = min(maxW - 32.dp, 640.dp))
        box = box.heightIn(max = if (layoutHeight == ViewGroup.LayoutParams.MATCH_PARENT) maxHeight else maxHeight - (if (isSheet) 48.dp else 32.dp))
            .alpha(appear)
            .clip(shape)
            .drawBehind {
                if (background == null) drawRect(defaultBackground)
                else drawAndroidDrawable(background, size.width.toInt(), size.height.toInt())
            }
            // clicks inside the dialog must not reach the dim area
            .pointerInput(Unit) { detectTapGestures { } }
        // a fixed width window (bottom sheet, alert, MATCH_PARENT) measures its decor EXACTLY like Android
        // a dialog laid out for a phone screen (no scrolling view of its own, such as the donation dialogs of the Phisher and CNCVerse
        // extensions) can be taller than the window: it scrolls inside it, so its lower buttons stay reachable
        val scrolls = !isSheet && wantH != ViewGroup.LayoutParams.MATCH_PARENT && !hasScrollingView(decor)
        Box(box, propagateMinConstraints = width != null) {
            if (scrolls) {
                Box(Modifier.verticalScroll(rememberScrollState()), propagateMinConstraints = width != null) { AndroidViewHost(decor) }
            } else {
                AndroidViewHost(decor)
            }
        }
    }
}

private fun alignmentFor(gravity: Int): Alignment {
    val h = when (gravity and 0x07) {
        Gravity.LEFT -> -1f
        Gravity.RIGHT -> 1f
        else -> 0f
    }
    val v = when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
        Gravity.TOP -> -1f
        Gravity.BOTTOM -> 1f
        else -> 0f
    }
    return androidx.compose.ui.BiasAlignment(h, v)
}

/** Whether [view] has a view that scrolls by itself (lists, scroll views, web pages): such a dialog keeps the window's height limit */
private fun hasScrollingView(view: android.view.View): Boolean {
    if (view is android.widget.ScrollView || view is android.widget.AbsListView || view is android.webkit.WebView) return true
    val name = view.javaClass.name
    if (name.endsWith("NestedScrollView") || name.endsWith("RecyclerView") || name.endsWith("ViewPager2")) return true
    if (view is ViewGroup) for (i in 0 until view.getChildCount()) if (view.getChildAt(i)?.let(::hasScrollingView) == true) return true
    return false
}
