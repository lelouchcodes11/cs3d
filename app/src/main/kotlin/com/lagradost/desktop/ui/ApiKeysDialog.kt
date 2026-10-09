package com.lagradost.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.syncproviders.ApiKeys
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private class KeySection(val name: String, val keys: List<ApiKeys.Key>, val steps: String, val redirect: String, val page: String)

private val sections = listOf(
    KeySection(
        "AniList", listOf(ApiKeys.Key.ANILIST_ID),
        "Create a client (any name), set its Redirect URL to the address below and paste its ID, a number.",
        com.lagradost.desktop.net.OAuthCallback.redirectUrl("anilistlogin"), "https://anilist.co/settings/developer",
    ),
    KeySection(
        "MyAnimeList", listOf(ApiKeys.Key.MAL_ID, ApiKeys.Key.MAL_SECRET),
        "Create ID: App Type \"other\", Redirect URL as below, the rest as you like. Paste the Client ID. The Client Secret is only for an App Type \"web\" client (\"Client authentication failed\" at sign-in means it is needed).",
        com.lagradost.desktop.net.OAuthCallback.redirectUrl("mallogin"), "https://myanimelist.net/apiconfig",
    ),
    KeySection(
        "Simkl", listOf(ApiKeys.Key.SIMKL_ID, ApiKeys.Key.SIMKL_SECRET),
        "New app, Redirect URI as below. Paste the Client ID (the PIN sign-in needs only that) and the Client Secret (browser sign-in).",
        com.lagradost.desktop.net.OAuthCallback.redirectUrl("simkl"), "https://simkl.com/settings/developer/",
    ),
)

/**
 * The OAuth clients of AniList, MyAnimeList and Simkl. This build has none of its own, so each user registers a free client once
 * (a minute per service) and enters it here; sign-in and syncing use it from then on. [focus] puts that service first.
 */
fun showApiKeysDialog(focus: String? = null) {
    val ordered = sections.sortedBy { if (it.name.equals(focus, ignoreCase = true)) 0 else 1 }
    val values = ApiKeys.Key.entries.associateWith { mutableStateOf(ApiKeys.saved(it).orEmpty()) }

    Overlays.show(
        Overlays.Dialog(
            title = "Sign-in keys",
            primary = "Save",
            close = "Cancel",
            width = 600.dp,
            onPrimary = {
                values.forEach { (key, state) -> ApiKeys.set(key, state.value) }
                Toasts.show("Sign-in keys saved", false)
            },
        ) {
            val c = Fluent.colors
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                FText(
                    "AniList, MyAnimeList and Simkl need an API client of your own: this app has none built in (that is why their sign-in page said \"Client authentication failed\"). Register one on each service you use with the redirect URL shown (the browser comes back to this app on that address), paste its keys here, then sign in from Accounts.",
                    color = c.textSecondary, maxLines = 6,
                )
                for (section in ordered) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FText(section.name, style = Fluent.type.bodyStrong)
                        FText(section.steps, style = Fluent.type.caption, color = c.textSecondary, maxLines = 4)
                        FText("Redirect URL: ${section.redirect}", style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
                        for (key in section.keys) {
                            val state = values.getValue(key)
                            TextBox(
                                state.value, { state.value = it }, Modifier.fillMaxWidth(),
                                placeholder = key.label + if (ApiKeys.hasBuiltIn(key)) " (one is built in)" else "",
                                leadingIcon = if (key.secret) Icons.Lock else Icons.Link, height = 36.dp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                            Button("Open developer page", { DesktopPlatform.openExternalBrowser(section.page) }, kind = ButtonKind.Subtle, icon = Icons.OpenInNewWindow, height = 32.dp)
                            Button("Copy redirect", {
                                runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(section.redirect), null) }
                                Toasts.show("Redirect copied", false)
                            }, kind = ButtonKind.Subtle, icon = Icons.Copy, height = 32.dp)
                        }
                    }
                }
            }
        },
    )
}
