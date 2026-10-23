package com.lagradost.desktop.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ui.account.AccountViewModel
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.core.AppVms
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.FluentMotion
import com.lagradost.desktop.ui.fluent.Icon
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.Skins
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction

/**
 * "Who's watching?" in Netflix's interface: the whole window goes black and the profiles stand in a row as square pictures with their names under them; a click on one
 * opens the app as that profile. "Manage Profiles" turns the pictures into edit buttons (the app's own profile window opens: name, picture, PIN) and shows Remove
 * under each; "Add Profile" asks for a name first. Everything the profile window of the app does is here too: nothing is left out.
 */
internal fun showProfileGate(forStartup: Boolean) {
    val ctx = DesktopBootstrap.activity
    val vm = AppVms.get<AccountViewModel>()
    Overlays.show(
        Overlays.Dialog(title = null, close = null, fullscreen = true) { dismiss ->
            val c = Fluent.colors
            var accounts by remember { mutableStateOf(runCatching { DataStoreHelper.getAccounts(ctx) }.getOrDefault(emptyList())) }
            var managing by remember { mutableStateOf(false) }
            var adding by remember { mutableStateOf(false) }
            var name by remember { mutableStateOf("") }
            fun refresh() { accounts = runCatching { DataStoreHelper.getAccounts(ctx) }.getOrDefault(accounts) }
            Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                Skins.spec?.let { spec ->
                    FText(spec.wordmark, Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 28.dp), style = Fluent.type.title.copy(fontSize = spec.wordmarkSp.sp, fontWeight = FontWeight.Black, letterSpacing = spec.wordmarkTracking.sp), color = c.accent, maxLines = 1, softWrap = false)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(30.dp)) {
                    FText(if (managing) "Manage Profiles:" else "Who's watching?", style = Fluent.type.title.copy(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.Top) {
                        for (account in accounts) {
                            GateProfile(
                                account, managing,
                                onClick = {
                                    if (managing) showProfileEditor(account) { refresh() }
                                    else { vm.handleAccountSelect(account, ctx, forStartup); dismiss() }
                                },
                                onRemove = if (managing && accounts.size > 1) ({ vm.handleAccountDelete(account, ctx); refresh() }) else null,
                            )
                        }
                        if (!adding) GateAdd(onClick = { adding = true })
                    }
                    if (adding) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextBox(name, { name = it.take(24) }, Modifier.width(300.dp), placeholder = "Profile name", leadingIcon = Icons.Person)
                        Button("Create", {
                            val n = name.trim()
                            if (n.isNotEmpty()) {
                                val key = (accounts.maxOfOrNull { it.keyIndex } ?: 0) + 1
                                val created = DataStoreHelper.Account(keyIndex = key, name = n, defaultImageIndex = key % 8)
                                vm.handleAccountUpdate(created, ctx)
                                refresh()
                                showProfileEditor(created) { refresh() }
                                name = ""
                                adding = false
                            }
                        }, kind = ButtonKind.Accent)
                        Button("Cancel", { adding = false; name = "" }, kind = ButtonKind.Subtle)
                    }
                    Box(Modifier.height(10.dp))
                    GateButton(if (managing) "DONE" else "MANAGE PROFILES", primary = managing) { managing = !managing }
                }
                // opened from the profile menu (not at the start of the app): a way out without choosing
                if (!forStartup) Box(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 28.dp)) {
                    com.lagradost.desktop.ui.fluent.IconButton(Icons.Close, { dismiss() }, tooltip = "Close (Esc)", kind = ButtonKind.Subtle, size = 40.dp, iconSize = 18.dp, tint = Color.White)
                }
            }
        },
    )
}

@Composable
private fun GateProfile(account: DataStoreHelper.Account, managing: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(6.dp)
    val current = account.keyIndex == DataStoreHelper.selectedKeyIndex
    val name by animateColorAsState(if (hovered) Color.White else Color(0xFF808080), FluentMotion.tweenStd(160), label = "gateName")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(150.dp).clip(shape)
                .border(if (hovered) 4.dp else 0.dp, Color.White, shape)
                .fluentClickable(source, true, shape, Role.Button, onClick),
            contentAlignment = Alignment.Center,
        ) {
            ProfileAvatar(account, 150.dp, playing = hovered, shape = shape)
            // managing: the picture dims and carries a pencil
            if (managing) Box(Modifier.size(150.dp).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Edit, size = 40.dp, tint = Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FText(account.name, style = Fluent.type.bodyLarge.copy(fontSize = 19.sp), color = name, maxLines = 1)
            if (account.lockPin != null) Icon(Icons.Lock, size = 13.dp, tint = c.textSecondary)
        }
        if (onRemove != null) Button("Remove", onRemove, kind = ButtonKind.Subtle, height = 28.dp)
        else if (current && !managing) FText("Current", style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
    }
}

@Composable
private fun GateAdd(onClick: () -> Unit) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(6.dp)
    val tint by animateColorAsState(if (hovered) Color.White else Color(0xFF808080), FluentMotion.tweenStd(160), label = "gateAdd")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(150.dp).clip(shape).background(if (hovered) Color(0x22FFFFFF) else Color.Transparent, shape)
                .border(if (hovered) 4.dp else 0.dp, Color.White, shape)
                .fluentClickable(source, true, shape, Role.Button, onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Add, size = 64.dp, tint = tint) }
        FText("Add Profile", style = Fluent.type.bodyLarge.copy(fontSize = 19.sp), color = tint, maxLines = 1)
    }
}

/** The button under the profiles: a framed, letter-spaced caption that fills with white while the pointer is on it */
@Composable
private fun GateButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(2.dp)
    val frame = Color(0xFF808080)
    val fill by animateColorAsState(if (primary) (if (hovered) Color(0xFFD9D9D9) else Color.White) else if (hovered) Color.White else Color.Transparent, FluentMotion.tweenStd(160), label = "gateBtn")
    val text = if (primary || hovered) Color(0xFF141414) else frame
    Box(
        Modifier.clip(shape).background(fill, shape).border(1.dp, if (primary || hovered) fill else frame, shape)
            .fluentClickable(source, true, shape, Role.Button, onClick).padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { FText(label, style = Fluent.type.bodyStrong.copy(fontSize = 17.sp, letterSpacing = 2.sp), color = text, maxLines = 1, softWrap = false) }
}
