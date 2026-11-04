package com.lagradost.desktop.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.ui.fluent.ButtonBase
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays
import kotlinx.coroutines.launch

/**
 * The profile lock: a 4 digit PIN typed on the keyboard or on the pad. Checking a PIN answers it when it matches; a new PIN is typed twice.
 * Every PIN question of the app comes here (switching to a locked profile, the start, deleting, editing).
 */
object PinLock {
    const val LENGTH = 4

    /**
     * Any profile can have a PIN, the only one too (the owner's wish: the app asks for it at the start). Someone who forgot it is not shut out of the
     * app: the lock screen's Profiles button adds a new profile (the locked one's data stays behind its PIN).
     */
    @Suppress("UNUSED_PARAMETER")
    fun canLock(account: DataStoreHelper.Account): Boolean = true

    /**
     * The open profile has a PIN and nobody typed it in this run: the pages stay hidden behind [LockCover]. Set from the start (the profile the app
     * opens on), cleared when a profile is opened the proper way (its PIN typed, or a profile without one) in AccountViewModel.handleAccountSelect.
     */
    var covered by androidx.compose.runtime.mutableStateOf(
        runCatching {
            DataStoreHelper.getAccounts(DesktopBootstrap.activity).firstOrNull { it.keyIndex == DataStoreHelper.selectedKeyIndex }?.lockPin != null
        }.getOrDefault(false),
    )

    /** The app is never left open on a locked profile nobody unlocked (the start, a cancelled PIN): it moves to a profile without one */
    fun leaveLocked() {
        val ctx = DesktopBootstrap.activity
        val all = runCatching { DataStoreHelper.getAccounts(ctx) }.getOrDefault(emptyList())
        if (all.firstOrNull { it.keyIndex == DataStoreHelper.selectedKeyIndex }?.lockPin == null) return
        val open = all.firstOrNull { it.lockPin == null } ?: return
        com.lagradost.desktop.core.AppVms.get<com.lagradost.cloudstream3.ui.account.AccountViewModel>().handleAccountSelect(open, ctx)
    }

    /**
     * [expected] null: a new PIN (typed twice). Otherwise the PIN to match. [callback] gets the PIN, or null when it was cancelled.
     * [other] adds a button that leaves this profile for another one (the start of the app).
     */
    fun ask(title: String, expected: String?, other: (() -> Unit)? = null, callback: (String?) -> Unit) {
        var answered = false
        fun answer(pin: String?) { if (!answered) { answered = true; callback(pin) } }
        Overlays.show(
            Overlays.Dialog(
                title = title, close = "Cancel", width = 360.dp,
                secondary = if (other != null) "Other profile" else null,
                onSecondary = { if (!answered) { answered = true; other?.invoke() } },
                onClose = { answer(null) },
            ) { dismiss -> PinPad(expected) { pin -> dismiss(); answer(pin) } }
        )
    }
}

@Composable
private fun PinPad(expected: String?, onDone: (String) -> Unit) {
    val c = Fluent.colors
    var typed by remember { mutableStateOf("") }
    // a new PIN: the first entry waits here until the second one matches it
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf(if (expected == null) "Choose a 4 digit PIN" else "Enter the PIN") }
    var wrong by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun fail(text: String) {
        message = text; typed = ""; wrong++
        scope.launch { for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45)) }
    }

    fun complete(pin: String) {
        when {
            expected != null -> if (pin == expected) onDone(pin) else fail("Wrong PIN, try again")
            first == null -> { first = pin; typed = ""; message = "Enter it again" }
            pin == first -> onDone(pin)
            else -> { first = null; fail("The PINs did not match. Choose a PIN") }
        }
    }

    fun press(digit: Char) {
        if (typed.length >= PinLock.LENGTH) return
        typed += digit
        if (typed.length == PinLock.LENGTH) complete(typed)
    }

    fun back() { typed = typed.dropLast(1) }

    Column(
        Modifier.fillMaxWidth().focusRequester(focus).focusable().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val digit = digitOf(e.key)
            when {
                digit != null -> { press(digit); true }
                e.key == Key.Backspace -> { back(); true }
                else -> false
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FText(message, style = Fluent.type.body, color = if (wrong > 0 && typed.isEmpty() && first == null) c.critical else c.textSecondary)
        Row(Modifier.graphicsLayer { translationX = shake.value }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PinLock.LENGTH) { i ->
                Box(Modifier.size(14.dp).background(if (i < typed.length) c.text else c.control, CircleShape))
            }
        }
        // typed on the keyboard (digits or the number pad, Backspace to correct): no on-screen pad (the owner's wish)
        FText("Type the PIN on your keyboard", style = Fluent.type.caption, color = c.textTertiary)
    }
}

private fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/**
 * What shows instead of the pages while the open profile is locked ([PinLock.covered]): the start of the app on a profile with a PIN, also when
 * it is the only profile, and after the PIN pad or the picker was closed without opening a profile. Nothing of the locked profile shows here.
 * Under the dialogs (the PIN pad and the picker are drawn over it).
 */
@Composable
fun LockCover() {
    if (!PinLock.covered) return
    val c = Fluent.colors
    val ctx = DesktopBootstrap.activity
    fun current() = runCatching { DataStoreHelper.getAccounts(ctx).firstOrNull { it.keyIndex == DataStoreHelper.selectedKeyIndex } }.getOrNull()
    Box(
        Modifier.fillMaxSize().background(c.bg)
            // presses and the wheel stop here (only those: a parent that takes every event cancels its own buttons' clicks)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                        if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press || e.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll) e.changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.lagradost.desktop.ui.fluent.Icon(com.lagradost.desktop.ui.fluent.Icons.Lock, size = 36.dp, tint = c.textSecondary)
            FText(current()?.name?.let { "$it is locked" } ?: "This profile is locked", style = Fluent.type.subtitle, color = c.text)
            FText("Enter its PIN, or pick or add another profile", color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.lagradost.desktop.ui.fluent.Button("Enter PIN", {
                    current()?.let { com.lagradost.desktop.core.AppVms.get<com.lagradost.cloudstream3.ui.account.AccountViewModel>().handleAccountSelect(it, ctx, true) }
                }, kind = ButtonKind.Accent, height = 40.dp)
                com.lagradost.desktop.ui.fluent.Button("Profiles", { showAccountPicker(forStartup = true) }, height = 40.dp)
            }
        }
    }
}
