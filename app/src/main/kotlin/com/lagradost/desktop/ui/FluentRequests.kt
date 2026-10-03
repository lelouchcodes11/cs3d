package com.lagradost.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.Navigator
import com.lagradost.desktop.ui.components.RemoteImage
import com.lagradost.desktop.ui.fluent.CheckBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/**
 * The engine asks for simple dialogs through [DesktopDialogs] (selection lists, text input, messages,
 * PIN). In the native UI each one is shown as a Fluent ContentDialog.
 */
@Composable
fun FluentRequestDialogs() {
    val pending = DesktopDialogs.requests.toList()
    for (request in pending) {
        LaunchedEffect(request) {
            DesktopDialogs.dismiss(request)
            when (request) {
                is DesktopDialogs.Request.Text -> showText(request)
                is DesktopDialogs.Request.TextInput -> showTextInput(request)
                is DesktopDialogs.Request.Selection -> showSelection(request)
                is DesktopDialogs.Request.PinInput -> showPin(request)
                // quick-info sheet of the phone UI: the native pages open the title instead
                is DesktopDialogs.Request.SearchResultPopup -> Navigator.openDetails(request.card)
            }
        }
    }
}

private fun label(res: Int) = DesktopBootstrap.activity.getString(res)

private fun showText(r: DesktopDialogs.Request.Text) {
    Overlays.show(
        Overlays.Dialog(title = r.title, close = label(R.string.ok), width = 560.dp, onClose = r.dismissCallback) {
            Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { FText(r.text.toString(), color = Fluent.colors.textSecondary) }
        },
    )
}

private fun showTextInput(r: DesktopDialogs.Request.TextInput) {
    var text by mutableStateOf(r.value)
    var applied = false
    Overlays.show(
        Overlays.Dialog(
            title = r.name, primary = label(R.string.sort_apply), close = label(R.string.sort_cancel), width = 460.dp,
            onPrimary = { applied = true; r.callback(text); r.dismissCallback() },
            onClose = { if (!applied) r.dismissCallback() },
        ) { TextBox(text, { text = it }, Modifier.fillMaxWidth(), focusRequester = remember { FocusRequester() }) },
    )
}

private fun showSelection(r: DesktopDialogs.Request.Selection) {
    val selected = mutableStateListOf<Int>().apply { addAll(r.selected.filter { it >= 0 }) }
    var done = false
    val instant = !r.showApply && !r.isMultiSelect
    Overlays.show(
        Overlays.Dialog(
            title = r.name.ifBlank { null },
            primary = if (r.showApply || r.isMultiSelect) label(R.string.sort_apply) else null,
            close = label(R.string.sort_cancel), width = 480.dp,
            onPrimary = { done = true; r.callback(selected.toList().sorted()); r.dismissCallback() },
            onClose = { if (!done) r.dismissCallback() },
        ) { dismiss ->
            val c = Fluent.colors
            Column {
                if (r.poster != null) {
                    Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(6.dp))) { RemoteImage(r.poster, null, null, Modifier.fillMaxWidth(), ContentScale.Crop) }
                    Box(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(r.items) { index, item ->
                        val source = rememberInteraction()
                        val hovered by source.collectIsHoveredAsState()
                        val shape = RoundedCornerShape(4.dp)
                        val on = index in selected
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(if ((on && !instant) || hovered) c.subtleHover else Color.Transparent, shape)
                                .fluentClickable(source, true, shape, Role.Button) {
                                    if (r.isMultiSelect) {
                                        if (on) selected.remove(index) else selected.add(index)
                                    } else if (instant) {
                                        done = true; r.callback(listOf(index)); r.dismissCallback(); dismiss()
                                    } else {
                                        selected.clear(); selected.add(index)
                                    }
                                }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (r.isMultiSelect) CheckBox(on, { if (it) selected.add(index) else selected.remove(index) })
                            else if (!instant) Box(Modifier.size(16.dp).border(androidx.compose.ui.unit.Dp.Hairline, if (on) c.accent else c.textSecondary, CircleShape), contentAlignment = Alignment.Center) {
                                if (on) Box(Modifier.size(8.dp).background(c.accent, CircleShape))
                            }
                            FText(item, Modifier.weight(1f), color = if (on && !instant) c.accentText else c.text, maxLines = 2)
                        }
                    }
                }
            }
        },
    )
}

/** Same rules as upstream AccountHelper.showPinInputDialog */
private fun showPin(r: DesktopDialogs.Request.PinInput) {
    val ctx = DesktopBootstrap.activity
    val isPinSet = r.currentPin != null
    val isNewPin = r.editAccount && !isPinSet
    val isEditPin = r.editAccount && isPinSet
    var pin by mutableStateOf("")
    var error by mutableStateOf(r.errorText)
    var valid = false
    var finished = false
    val errorIncorrect = ctx.getString(R.string.pin_error_incorrect)
    val errorLength = ctx.getString(R.string.pin_error_length)
    lateinit var dialog: Overlays.Dialog
    fun finish(result: String?) {
        if (finished) return
        finished = true
        Overlays.dismiss(dialog)
        r.callback(result)
    }
    fun onChange(text: String) {
        val filtered = text.filter { it.isDigit() }.take(4)
        pin = filtered
        if (filtered.length == 4) {
            if (isPinSet) {
                if (filtered != r.currentPin) { error = errorIncorrect; pin = ""; valid = false }
                else { error = null; valid = true; finish(filtered) }
            } else { error = null; valid = true }
        } else if (isNewPin) { error = errorLength; valid = false }
    }
    val title = if (r.forStartup) {
        val current = DataStoreHelper.accounts.firstOrNull { it.keyIndex == DataStoreHelper.selectedKeyIndex }
        ctx.getString(R.string.enter_pin_with_name, current?.name ?: "")
    } else ctx.getString(if (isEditPin) R.string.enter_current_pin else R.string.enter_pin)
    dialog = Overlays.Dialog(
        title = title, close = label(R.string.cancel), width = 400.dp,
        primary = if (isNewPin) label(R.string.setup_done) else null,
        secondary = if (r.forStartup) ctx.getString(R.string.use_default_account) else null,
        onSecondary = { finished = true; DataStoreHelper.setAccount(getDefaultAccount(ctx)); r.callback(null) },
        onPrimary = {
            if (!valid) {
                finished = true
                DesktopDialogs.show(DesktopDialogs.Request.PinInput(null, true, false, error ?: errorLength, r.callback))
            } else finish(pin)
        },
        onClose = { if (!finished) { finished = true; r.callback(null) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextBox(
                pin, ::onChange, Modifier.fillMaxWidth(), placeholder = "••••", leadingIcon = Icons.Lock,
                visualTransformation = PasswordVisualTransformation(), focusRequester = remember { FocusRequester() },
                onSubmit = { if (valid) finish(pin) },
            )
            error?.let { FText(it, color = Fluent.colors.critical, style = Fluent.type.caption) }
        }
    }
    Overlays.show(dialog)
}
