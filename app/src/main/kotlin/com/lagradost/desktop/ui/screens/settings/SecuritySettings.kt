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
import com.lagradost.desktop.i18n.Tr
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
    // one switch for both layers: the Windows sandbox (the process the OS locks down) and the Java guard that covers the few extensions that must run in the app
    var on by remember { mutableStateOf(com.lagradost.desktop.isolate.Isolation.enabledSetting() || PluginSandbox.enabledSetting()) }
    card(
        "Sandbox for extensions",
        "Extensions run in a locked-down Windows process. They cannot read your files, start programs or reach your home network.\n" +
            "Applies from the next start.",
        "sandbox appcontainer isolate isolation process windows security malware plugins extensions harm protect virus safe permissions java",
    ) {
        ToggleSwitch(on, {
            on = it
            com.lagradost.desktop.isolate.Isolation.setEnabled(it)
            PluginSandbox.setEnabled(it)
        })
    }
    var fallback by remember { mutableStateOf(com.lagradost.desktop.isolate.Isolation.fallbackAllowed()) }
    card(
        "Run extensions in the app if the sandbox cannot start",
        "Not safe. If the sandbox cannot start on this PC, extensions run in the app with a weaker guard.",
        "sandbox fallback unsafe insecure app run anyway cannot start blocked not loaded",
    ) {
        ToggleSwitch(fallback, {
            fallback = it
            com.lagradost.desktop.isolate.Isolation.setFallbackAllowed(it)
        })
    }
    link("Where extensions run", "Which extensions run in the sandbox and which in the app.", "sandbox isolated app extensions list where status") { showWhereExtensionsRun() }
    link("Blocked by the sandbox", "What the sandbox stopped in this run. Look here if an extension stops working.", "log blocked refused denied extension tried") { showBlocked() }
    link(
        "Trusted extensions",
        "Extensions the sandbox leaves alone. Trust only ones you know.",
        "trust allow exception whitelist extension sandbox",
    ) { showTrusted() }
}

private fun showWhereExtensionsRun() {
    Overlays.show(
        Overlays.Dialog(title = "Where extensions run", close = "Close", width = 560.dp) {
            val c = Fluent.colors
            val providers = com.lagradost.cloudstream3.APIHolder.allProviders.toList().filter { it.sourcePlugin != null }.groupBy { it.sourcePlugin!! }
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FText(
                    com.lagradost.desktop.isolate.IsolatedHost.let {
                        if (it.running()) Tr.t("The Windows sandbox is running.")
                        else it.lastError?.let { e -> Tr.f("The Windows sandbox is not running: {0}", e) } ?: Tr.t("The Windows sandbox is not running (it starts with the first extension).")
                    },
                    color = c.textSecondary, style = Fluent.type.body,
                )
                com.lagradost.desktop.isolate.IsolatedHost.startFailure()?.let { why -> FText(Tr.f("The sandbox could not start: {0}", why), color = c.accent, style = Fluent.type.body) }
                com.lagradost.desktop.isolate.Isolation.blockedNames().takeIf { it.isNotEmpty() }?.let { names -> FText(Tr.f("Not loaded, because the sandbox could not start: {0}", names.joinToString(", ")), color = c.accent, style = Fluent.type.body) }
                if (providers.keys.any { com.lagradost.desktop.isolate.Isolation.reasonInApp(it) != null || providers[it]!!.first() !is com.lagradost.desktop.isolate.RemoteMainAPI }) FText(PluginSandbox.statusLine(), color = c.textSecondary, style = Fluent.type.body)
                for ((path, apis) in providers.entries.sortedBy { it.value.first().name.lowercase() }) {
                    val sandboxed = apis.first() is com.lagradost.desktop.isolate.RemoteMainAPI
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FText(apis.joinToString(", ") { it.name }, style = Fluent.type.bodyStrong, modifier = Modifier.weight(1f))
                        val reason = com.lagradost.desktop.isolate.Isolation.reasonInApp(path)
                        FText(Tr.t(if (sandboxed) "Sandbox" else "App") + (reason?.let { " (${Tr.t(it)})" } ?: ""), color = c.textSecondary, style = Fluent.type.body)
                    }
                }
            }
        },
    )
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
