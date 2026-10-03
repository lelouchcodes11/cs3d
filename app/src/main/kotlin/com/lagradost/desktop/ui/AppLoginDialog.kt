package com.lagradost.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.syncproviders.AuthLoginResponse
import com.lagradost.cloudstream3.syncproviders.AuthRepo
import com.lagradost.desktop.DesktopPlatform
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.TextBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val loginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Sign in to a service with a user name and password (OpenSubtitles, SubDL, ...): the form of the engine's
 * AddAccountInput layout as a native dialog. The request runs on a worker; the window stays responsive.
 */
fun showFluentAppLogin(api: AuthRepo) {
    val req = api.inAppLoginRequirement ?: return
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var email by mutableStateOf("")
    var server by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    lateinit var dialog: Overlays.Dialog

    fun submit() {
        if (busy) return
        busy = true
        error = null
        loginScope.launch {
            try {
                val ok = api.login(
                    AuthLoginResponse(
                        username = if (req.username) username.trim() else null, password = if (req.password) password else null,
                        email = if (req.email) email.trim() else null, server = if (req.server) server.trim() else null,
                    ),
                )
                if (ok) {
                    Toasts.show("Signed in to ${api.name}", false)
                    Overlays.dismiss(dialog)
                } else error = "Could not sign in to ${api.name}. Check the details and try again."
            } catch (t: Throwable) {
                error = (t as? ErrorLoadingException)?.message ?: "Could not sign in to ${api.name}: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    dialog = Overlays.Dialog(title = "Sign in to ${api.name}", primary = null, close = null, width = 440.dp) { dismiss ->
        val c = Fluent.colors
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (req.username) TextBox(username, { username = it }, Modifier.fillMaxWidth(), placeholder = "User name", leadingIcon = Icons.Person, enabled = !busy, onSubmit = ::submit, height = 36.dp)
            if (req.email) TextBox(email, { email = it }, Modifier.fillMaxWidth(), placeholder = "Email", leadingIcon = Icons.Person, enabled = !busy, onSubmit = ::submit, height = 36.dp)
            if (req.server) TextBox(server, { server = it }, Modifier.fillMaxWidth(), placeholder = "Server", leadingIcon = Icons.Settings, enabled = !busy, onSubmit = ::submit, height = 36.dp)
            if (req.password) TextBox(password, { password = it }, Modifier.fillMaxWidth(), placeholder = "Password", leadingIcon = Icons.Settings, enabled = !busy, onSubmit = ::submit, height = 36.dp, clearable = false, visualTransformation = PasswordVisualTransformation())
            error?.let { FText(it, color = c.critical, style = Fluent.type.caption, maxLines = 4) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button("Sign in", ::submit, Modifier.weight(1f), ButtonKind.Accent, enabled = !busy)
                Button("Cancel", { dismiss() }, Modifier.weight(1f), enabled = !busy)
                if (busy) Box(Modifier.size(24.dp)) { ProgressRing(size = 20.dp, strokeWidth = 2.dp) }
            }
            api.createAccountUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Button("Create an account", { DesktopPlatform.openExternalBrowser(url) }, kind = ButtonKind.Subtle, icon = Icons.Person)
            }
        }
    }
    Overlays.show(dialog)
}
