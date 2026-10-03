package com.lagradost.desktop.ui

import android.content.DialogInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.runtime.ui.AlertController
import com.lagradost.desktop.ui.fluent.CheckBox
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/**
 * Android `AlertDialog`s built by engine code (confirmations, simple choices) shown as Fluent
 * ContentDialogs. Dialogs with custom views or adapters (extension screens, bottom sheets) are left to
 * the View renderer.
 */
object FluentAlerts {
    private val shown = HashMap<android.app.Dialog, Overlays.Dialog>()

    private fun controllerOf(dialog: android.app.Dialog): AlertController? =
        (dialog as? androidx.appcompat.app.AlertDialog)?.mAlert ?: (dialog as? android.app.AlertDialog)?.mAlert

    /** True when the dialog is now shown natively (call on the UI thread) */
    fun show(dialog: android.app.Dialog): Boolean {
        val controller = controllerOf(dialog) ?: return false
        val model = controller.simpleModel() ?: return false
        if (shown.containsKey(dialog)) return true
        val positive = model.buttons[DialogInterface.BUTTON_POSITIVE]
        val negative = model.buttons[DialogInterface.BUTTON_NEGATIVE]
        val neutral = model.buttons[DialogInterface.BUTTON_NEUTRAL]
        val hasItems = model.items != null
        val plainList = hasItems && !model.single && !model.multi
        val title = model.title?.toString()?.takeIf { it.isNotBlank() }
        val checked = model.checked?.toList().orEmpty()
        val d = Overlays.Dialog(
            title = title,
            primary = positive,
            secondary = neutral,
            close = negative ?: if (positive == null && neutral == null) (if (plainList) "Cancel" else "OK") else null,
            width = if (hasItems) 480.dp else 460.dp,
            onPrimary = { controller.clickButton(DialogInterface.BUTTON_POSITIVE) },
            onSecondary = { controller.clickButton(DialogInterface.BUTTON_NEUTRAL) },
            onClose = {
                shown.remove(dialog)
                if (negative != null) controller.clickButton(DialogInterface.BUTTON_NEGATIVE)
                else if (positive == null && neutral == null && !plainList) controller.clickButton(DialogInterface.BUTTON_POSITIVE)
                else controller.cancelDialog()
            },
        ) { dismiss ->
            AlertBody(model.message?.toString(), model.items, model.multi, model.single, checked, model.checkedItem, controller, dismiss)
        }
        shown[dialog] = d
        Overlays.show(d)
        return true
    }

    fun dismiss(dialog: android.app.Dialog) {
        val d = shown.remove(dialog) ?: return
        Overlays.dismiss(d)
    }
}

@Composable
private fun AlertBody(
    message: String?,
    items: List<String>?,
    multi: Boolean,
    single: Boolean,
    checkedInitial: List<Boolean>,
    checkedItem: Int,
    controller: AlertController,
    dismiss: () -> Unit,
) {
    val c = Fluent.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!message.isNullOrBlank()) {
            Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { FText(message, color = c.textSecondary) }
        }
        if (items != null) {
            val checked = remember { mutableStateListOf<Boolean>().apply { addAll(checkedInitial.ifEmpty { items.map { false } }) } }
            var selected by remember { mutableStateOf(checkedItem) }
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                itemsIndexed(items) { index, text ->
                    val source = rememberInteraction()
                    val hovered by source.collectIsHoveredAsState()
                    val shape = RoundedCornerShape(4.dp)
                    val on = if (multi) checked.getOrElse(index) { false } else selected == index
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(if (hovered) c.subtleHover else Color.Transparent, shape)
                            .fluentClickable(source, true, shape, Role.Button) {
                                when {
                                    multi -> {
                                        val next = !on
                                        if (index < checked.size) checked[index] = next
                                        controller.toggleItem(index, next)
                                    }
                                    single -> { selected = index; controller.clickItem(index) }
                                    else -> { controller.clickItem(index); dismiss() }
                                }
                            }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (multi) CheckBox(on, { next -> if (index < checked.size) checked[index] = next; controller.toggleItem(index, next) })
                        else if (single) Box(Modifier.size(16.dp).border(androidx.compose.ui.unit.Dp.Hairline, if (on) c.accent else c.textSecondary, CircleShape), contentAlignment = Alignment.Center) {
                            if (on) Box(Modifier.size(8.dp).background(c.accent, CircleShape))
                        }
                        FText(text, Modifier.weight(1f), maxLines = 2)
                    }
                }
            }
        }
    }
}
