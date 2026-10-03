package com.lagradost.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.AppContextUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.ui.components.RemoteImage
import kotlinx.coroutines.delay

@Composable
fun DialogHost() {
    for (request in DesktopDialogs.requests.toList()) {
        when (request) {
            is DesktopDialogs.Request.PinInput -> PinInputDialog(request)
            is DesktopDialogs.Request.Selection -> SelectionDialog(request)
            is DesktopDialogs.Request.TextInput -> TextInputDialog(request)
            is DesktopDialogs.Request.Text -> TextDialog(request)
            is DesktopDialogs.Request.SearchResultPopup -> SearchResultPopupDialog(request)
        }
    }
    for (dialog in DesktopUiHost.androidDialogs.toList()) {
        AndroidDialogHost(dialog)
    }
}

/** Same rules as upstream AccountHelper.showPinInputDialog */
@Composable
private fun PinInputDialog(request: DesktopDialogs.Request.PinInput) {
    val isPinSet = request.currentPin != null
    val isNewPin = request.editAccount && !isPinSet
    val isEditPin = request.editAccount && isPinSet
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(request.errorText) }
    var valid by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val errorIncorrect = stringResource(R.string.pin_error_incorrect)
    val errorLength = stringResource(R.string.pin_error_length)

    fun finish(result: String?) {
        DesktopDialogs.dismiss(request)
        request.callback(result)
    }

    fun onChange(text: String) {
        val filtered = text.filter { it.isDigit() }.take(4)
        pin = filtered
        if (filtered.length == 4) {
            if (isPinSet) {
                if (filtered != request.currentPin) {
                    error = errorIncorrect
                    pin = ""
                    valid = false
                } else {
                    error = null
                    valid = true
                    finish(filtered)
                }
            } else {
                error = null
                valid = true
            }
        } else if (isNewPin) {
            error = errorLength
            valid = false
        }
    }

    val title = if (request.forStartup) {
        val current = DataStoreHelper.accounts.firstOrNull { it.keyIndex == DataStoreHelper.selectedKeyIndex }
        stringResource(R.string.enter_pin_with_name, current?.name ?: "")
    } else stringResource(if (isEditPin) R.string.enter_current_pin else R.string.enter_pin)

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = ::onChange,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) finish(pin) }),
                    modifier = Modifier.focusRequester(focus),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            if (isNewPin) {
                TextButton(onClick = {
                    if (!valid) {
                        // ask again, mentioning the error, like upstream
                        DesktopDialogs.dismiss(request)
                        DesktopDialogs.show(
                            DesktopDialogs.Request.PinInput(null, true, false, error ?: errorLength, request.callback)
                        )
                    } else finish(pin)
                }) { Text(stringResource(R.string.setup_done)) }
            }
        },
        dismissButton = {
            Row {
                if (request.forStartup) {
                    TextButton(onClick = {
                        DesktopDialogs.dismiss(request)
                        val act = DesktopBootstrap.activity
                        DataStoreHelper.setAccount(getDefaultAccount(act))
                        request.callback(null)
                    }) { Text(stringResource(R.string.use_default_account)) }
                }
                TextButton(onClick = { finish(null) }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focus.requestFocus() }
    }
}

/** Upstream SingleSelectionHelper dialogs */
@Composable
private fun SelectionDialog(request: DesktopDialogs.Request.Selection) {
    val selected = remember { mutableStateListOf<Int>().apply { addAll(request.selected.filter { it >= 0 }) } }

    fun close() {
        DesktopDialogs.dismiss(request)
        request.dismissCallback()
    }

    Dialog(onDismissRequest = ::close) {
        Column(
            Modifier.widthIn(min = 300.dp, max = 520.dp).heightIn(max = 640.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 12.dp),
        ) {
            if (request.poster != null) {
                RemoteImage(request.poster, modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp))
            }
            if (request.name.isNotBlank()) {
                Text(
                    request.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                itemsIndexed(request.items) { index, item ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (request.isMultiSelect) {
                                if (selected.contains(index)) selected.remove(index) else selected.add(index)
                            } else {
                                selected.clear()
                                selected.add(index)
                                if (!request.showApply) {
                                    DesktopDialogs.dismiss(request)
                                    request.callback(listOf(index))
                                    request.dismissCallback()
                                }
                            }
                        }.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (request.isMultiSelect) {
                            Checkbox(checked = selected.contains(index), onCheckedChange = null)
                        } else if (!request.instant) {
                            RadioButton(selected = selected.contains(index), onClick = null)
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            item,
                            color = if (selected.contains(index) && !request.instant) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
            if (request.showApply) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::close) { Text(stringResource(R.string.sort_cancel)) }
                    TextButton(onClick = {
                        DesktopDialogs.dismiss(request)
                        request.callback(selected.toList().sorted())
                        request.dismissCallback()
                    }) { Text(stringResource(R.string.sort_apply)) }
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(request: DesktopDialogs.Request.TextInput) {
    var text by remember { mutableStateOf(request.value) }

    fun close() {
        DesktopDialogs.dismiss(request)
        request.dismissCallback()
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(request.name) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                DesktopDialogs.dismiss(request)
                request.callback(text)
                request.dismissCallback()
            }) { Text(stringResource(R.string.sort_apply)) }
        },
        dismissButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.sort_cancel)) } },
    )
}

@Composable
private fun TextDialog(request: DesktopDialogs.Request.Text) {
    fun close() {
        DesktopDialogs.dismiss(request)
        request.dismissCallback()
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(request.title) },
        text = {
            Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(request.text.toString())
            }
        },
        confirmButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun SearchResultPopupDialog(request: DesktopDialogs.Request.SearchResultPopup) {
    val card = request.card
    fun close() = DesktopDialogs.dismiss(request)
    Dialog(onDismissRequest = ::close) {
        Row(
            Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        ) {
            RemoteImage(
                card.posterUrl, card.posterHeaders,
                modifier = Modifier.size(width = 100.dp, height = 150.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(card.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                Text(card.apiName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.size(12.dp))
                TextButton(onClick = {
                    close()
                    loadSearchResult(card)
                }) { Text(stringResource(R.string.home_more_info)) }
            }
        }
    }
}
