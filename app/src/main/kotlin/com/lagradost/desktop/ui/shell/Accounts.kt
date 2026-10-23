package com.lagradost.desktop.ui.shell

import com.lagradost.desktop.ui.fluent.selectedEdge
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.hoverEdge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.lagradost.desktop.ui.fluent.TouchLazyRow as LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.getAccounts
import com.lagradost.cloudstream3.utils.DataStoreHelper.getDefaultAccount
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.AppVms
import com.lagradost.desktop.ui.components.UiImageView
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentShapes
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/** Profile picker: choose, edit (name, icon/character), add or remove a profile */
fun showAccountPicker(forStartup: Boolean = false) {
    // Netflix's interface has its own profile screen
    if (com.lagradost.desktop.ui.fluent.Skins.spec != null) { showProfileGate(forStartup); return }
    val ctx = DesktopBootstrap.activity
    val vm = AppVms.get<AccountViewModel>()
    Overlays.show(
        Overlays.Dialog(title = if (forStartup) "Who is watching?" else "Profiles", close = "Close", width = 680.dp) { dismiss ->
            var accounts by remember { mutableStateOf(runCatching { getAccounts(ctx) }.getOrDefault(emptyList())) }
            var adding by remember { mutableStateOf(false) }
            var name by remember { mutableStateOf("") }
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    items(accounts, key = { it.keyIndex }) { account ->
                        ProfileTile(account, account.keyIndex == DataStoreHelper.selectedKeyIndex, onClick = {
                            vm.handleAccountSelect(account, ctx, forStartup)
                            dismiss()
                        }, onEdit = {
                            showProfileEditor(account) { accounts = runCatching { getAccounts(ctx) }.getOrDefault(accounts) }
                        }, onDelete = if (accounts.size > 1) ({
                            vm.handleAccountDelete(account, ctx)
                            accounts = runCatching { getAccounts(ctx) }.getOrDefault(accounts)
                        }) else null)
                    }
                    if (!adding) {
                        item {
                            AddProfileTile(onClick = { adding = true })
                        }
                    }
                }
                if (adding) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Fluent.colors.card, RoundedCornerShape(FluentShapes.card))
                            .hairBorder(Fluent.colors.stroke, RoundedCornerShape(FluentShapes.card)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextBox(name, { name = it.take(24) }, Modifier.weight(1f), placeholder = "Profile name", leadingIcon = Icons.Person)
                        Button("Create", {
                            val n = name.trim()
                            if (n.isNotEmpty()) {
                                val key = (accounts.maxOfOrNull { it.keyIndex } ?: 0) + 1
                                val created = DataStoreHelper.Account(keyIndex = key, name = n, defaultImageIndex = key % 8)
                                vm.handleAccountUpdate(created, ctx)
                                accounts = runCatching { getAccounts(ctx) }.getOrDefault(accounts)
                                showProfileEditor(created) { accounts = runCatching { getAccounts(ctx) }.getOrDefault(accounts) }
                                name = ""
                                adding = false
                            }
                        }, kind = ButtonKind.Accent)
                        Button("Cancel", { adding = false; name = "" }, kind = ButtonKind.Subtle)
                    }
                }
            }
        },
    )
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(
        Modifier.width(136.dp).clip(shape).background(if (hovered) c.cardHover else c.card, shape)
            .hairBorder(if (hovered) c.selectedEdge else c.stroke, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(86.dp).clip(CircleShape).background(if (hovered) c.controlHover else c.control, CircleShape)
                .hairBorder(if (hovered) c.selectedEdge else c.stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Add, size = 32.dp, tint = if (hovered) c.text else c.textSecondary)
        }
        FText("Add Profile", style = Fluent.type.bodyStrong, color = if (hovered) c.text else c.textSecondary, maxLines = 1)
        Box(Modifier.height(26.dp))
    }
}

@Composable
private fun ProfileTile(account: DataStoreHelper.Account, current: Boolean, onClick: () -> Unit, onEdit: () -> Unit, onDelete: (() -> Unit)?) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.card)
    Column(
        Modifier.width(136.dp).clip(shape).background(if (hovered) c.cardHover else c.card, shape)
            .hairBorder(if (current) c.selectedEdge else if (hovered) c.hoverEdge else c.stroke, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(86.dp)) {
            ProfileAvatar(account, 86.dp, playing = hovered)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FText(account.name, style = Fluent.type.bodyStrong, maxLines = 1)
            if (account.lockPin != null) Icon(Icons.Lock, size = 12.dp, tint = c.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button("Edit", onEdit, kind = ButtonKind.Subtle, icon = Icons.Edit, height = 26.dp)
            if (onDelete != null && (hovered || !current)) {
                Button("Remove", onDelete, kind = ButtonKind.Subtle, height = 26.dp)
            }
        }
    }
}
