package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.sandbox.PluginSandbox
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ToggleSwitch
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll
import java.text.SimpleDateFormat
import java.util.Date

/** Settings > General > Security: the extension sandbox. Each line of a description is a text of its own, so it can be translated. */
@Composable
fun GroupBuilder.securityItems() {
    var on by remember { mutableStateOf(PluginSandbox.enabledSetting()) }
    card(
        "Extension sandbox",
        "Extensions are programs that anyone can publish. The sandbox stops them from running programs, reading your documents and browser data, changing files outside this app's data folder, ending the app, switching the sandbox off or calling Windows' own system functions.\n" +
            PluginSandbox.statusLine(),
        "sandbox security malware plugins extensions harm protect virus safe permissions",
    ) {
        ToggleSwitch(on, { on = it; PluginSandbox.setEnabled(it) })
    }
    link("Blocked by the sandbox", "Actions of extensions that were stopped in this run (or would have been, in audit mode). If an extension stops working, this list says why.", "log blocked refused denied extension tried") { showBlocked() }
    link(
        "Trusted extensions",
        "The sandbox leaves these extensions alone. Trust one from the blocked list only if you know it and need what the sandbox stopped.",
        "trust allow exception whitelist extension sandbox",
    ) { showTrusted() }
}

private fun showBlocked() {
    Overlays.show(
        Overlays.Dialog(title = "Blocked by the sandbox", close = "Close", width = 560.dp) {
            val c = Fluent.colors
            var changed by remember { mutableIntStateOf(0) }
            changed // read: the buttons follow a trust
            val events = PluginSandbox.events().asReversed()
            val format = SimpleDateFormat("HH:mm:ss")
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (events.isEmpty()) FText("Nothing was blocked in this run.", color = c.textSecondary, style = Fluent.type.body)
                for (name in events.map { it.plugin }.distinct().filter { it != "an extension" }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FText(name, style = Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
                        if (PluginSandbox.isTrusted(name)) FText("Trusted", color = c.textSecondary, style = Fluent.type.body)
                        else Button("Trust", {
                            PluginSandbox.trust(name)
                            com.lagradost.desktop.ui.Toasts.show(com.lagradost.desktop.i18n.Tr.f("{0} is trusted now.", name), false)
                            changed++
                        }, kind = ButtonKind.Standard)
                    }
                }
                for (e in events) {
                    FText("${format.format(Date(e.time))}  ·  ${e.plugin}", style = Fluent.type.bodyStrong)
                    // "Stopped: end the app": the label and the action are translated one by one
                    FText((if (e.blocked) "Stopped: " else "Would be stopped: ") + e.what, color = c.textSecondary, style = Fluent.type.body)
                }
            }
        },
    )
}

private fun showTrusted() {
    Overlays.show(
        Overlays.Dialog(title = "Trusted extensions", close = "Close", width = 480.dp) {
            val c = Fluent.colors
            var names by remember { mutableStateOf(PluginSandbox.trustedList()) }
            Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (names.isEmpty()) FText("No extension is trusted.", color = c.textSecondary, style = Fluent.type.body)
                for (name in names) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FText(name, style = Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
                        Button("Stop trusting", {
                            PluginSandbox.untrust(name)
                            com.lagradost.desktop.ui.Toasts.show(com.lagradost.desktop.i18n.Tr.f("{0} is not trusted any more.", name), false)
                            names = PluginSandbox.trustedList()
                        }, kind = ButtonKind.Standard)
                    }
                }
            }
        },
    )
}
