package com.lagradost.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.IconButton
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.tabletVerticalScroll as verticalScroll

/**
 * The keyboard shortcuts: the player's, which can be changed ([PlayerKeymap]), in groups, and the fixed ones. The same rows are in
 * Settings > Keyboard shortcuts and in the player's "Keyboard shortcuts" window (F1).
 */
object Shortcuts {
    /** Keys of the player that are always the same */
    val fixed: List<Pair<String, String>> = listOf(
        "Esc" to "Leave full screen or picture in picture, close the episode list, go back",
        "0 – 9" to "Jump to 0 % … 90 % of the video",
        "Mouse wheel" to "Volume",
        "Double click" to "Full screen",
        "Hold click  ·  Hold touch" to "2× for as long as it is held (not on the buttons, not when paused or live)",
        "F11" to "Full screen",
    )

    /** Keys and gestures of the whole app (NativeKeys in NativeApp.kt, the mouse's back button in Main.kt, DropOpen) */
    val everywhere: List<Pair<String, String>> = listOf(
        "Ctrl + K  ·  Ctrl + F  ·  Ctrl + E" to "Search (on Home: in the extension shown there)",
        "Alt + ←  ·  mouse back button" to "Back",
        "Esc" to "Close the dialog or menu that is open, leave full screen",
        "F11" to "Full screen",
        "Tab  ·  arrow keys" to "Move between buttons and posters; Enter opens",
        "Right click" to "More actions on posters and episodes",
        "Drop a file or a link" to "A video plays, subtitle files are added to the video that plays, a CloudStream link opens",
        "Paste a link in search" to "A video link or magnet plays instead of being searched for",
    )

    /** The action whose new key is being recorded, and the last refusal (shown under that action's keys) */
    var recording by mutableStateOf<PlayerAction?>(null)
    var refusal by mutableStateOf<Pair<PlayerAction, String>?>(null)

    fun record(action: PlayerAction) {
        refusal = null
        recording = action
        KeyRecorder.start { combo ->
            java.awt.EventQueue.invokeLater {
                recording = null
                if (combo != null) PlayerKeymap.add(action, combo)?.let { refusal = action to "${combo.label}: $it" }
            }
        }
    }

    fun stopRecording() {
        if (recording != null) { KeyRecorder.cancel(); recording = null }
    }

    /** Search words of an action: its name, group, explanation and keys */
    fun keywords(a: PlayerAction): String = listOfNotNull(a.group.title, a.about, PlayerKeymap.keysOf(a).joinToString(" ") { it.label }, "shortcut hotkey key keyboard").joinToString(" ")

    fun show() {
        Overlays.show(
            Overlays.Dialog(title = "Keyboard shortcuts", close = "Close", width = 680.dp, bodyMax = 560.dp, onClose = { stopRecording() }) {
                Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShortcutsEditor()
                }
            },
        )
    }
}

/** Every group of the player's shortcuts with its keys to change, then the keys that are always the same */
@Composable
fun ShortcutsEditor() {
    val c = Fluent.colors
    PlayerKeymap.version.intValue
    DisposableEffect(Unit) { onDispose { Shortcuts.stopRecording() } }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FText("Click + and press any keys. A combination can do only one thing.", Modifier.weight(1f), style = Fluent.type.caption, color = c.textSecondary)
        Button("Reset all", { Shortcuts.stopRecording(); Shortcuts.refusal = null; PlayerKeymap.resetAll() }, icon = Icons.Refresh, height = 30.dp, enabled = !PlayerKeymap.allDefault)
    }
    for (group in KeyGroup.entries) {
        FText(group.title, style = Fluent.type.bodyStrong, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        for (a in PlayerAction.entries.filter { it.group == group }) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    FText(a.label, style = Fluent.type.body)
                    a.about?.let { FText(it, style = Fluent.type.caption, color = c.textTertiary) }
                }
                KeyBindings(a)
            }
        }
    }
    for ((section, rows) in listOf("Always" to Shortcuts.fixed, "Everywhere in the app" to Shortcuts.everywhere)) {
        FText(section, style = Fluent.type.bodyStrong, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
        for ((keys, what) in rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                FText(what, Modifier.weight(1f).padding(end = 12.dp), color = c.textSecondary, style = Fluent.type.body)
                KeyCap(keys)
            }
        }
    }
}

@Composable
private fun KeyCap(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val c = Fluent.colors
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(if (accent) c.accent else c.control).padding(horizontal = 8.dp, vertical = 3.dp)) {
        FText(text, style = Fluent.type.caption, color = if (accent) c.onAccent else c.text, maxLines = 1, softWrap = false)
    }
}

/**
 * The keys of one action, each with a × to take it away, a + that records a new one (Esc cancels), and a reset when they are not the default
 * ones. A key another action already has is refused, and the row says which action has it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyBindings(a: PlayerAction) {
    val c = Fluent.colors
    PlayerKeymap.version.intValue
    val keys = PlayerKeymap.keysOf(a)
    Column(horizontalAlignment = Alignment.End) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 360.dp)) {
            for (k in keys) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(c.control).clickable { Shortcuts.refusal = null; PlayerKeymap.remove(a, k) }.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FText(if (k.modifierOnly) "${k.label} (alone)" else k.label, style = Fluent.type.caption, maxLines = 1, softWrap = false)
                    com.lagradost.desktop.ui.fluent.Icon(Icons.Close, Modifier.padding(start = 4.dp), size = 10.dp, tint = c.textTertiary)
                }
            }
            if (Shortcuts.recording == a) {
                KeyCap("Press the keys…  (Esc: cancel)", Modifier.clickable { Shortcuts.stopRecording() }, accent = true)
            } else {
                if (keys.isEmpty()) FText("None", style = Fluent.type.caption, color = c.textTertiary, modifier = Modifier.padding(vertical = 3.dp))
                IconButton(Icons.Add, { Shortcuts.record(a) }, tooltip = "Add a key", size = 26.dp, iconSize = 12.dp)
            }
            if (!PlayerKeymap.isDefault(a)) IconButton(Icons.Refresh, { Shortcuts.refusal = null; PlayerKeymap.reset(a) }, tooltip = "Back to the default keys", size = 26.dp, iconSize = 12.dp)
        }
        Shortcuts.refusal?.takeIf { it.first == a }?.let { r ->
            FText(r.second, style = Fluent.type.caption, color = c.critical, modifier = Modifier.padding(top = 2.dp))
            // the reason stays long enough to read, then goes
            androidx.compose.runtime.LaunchedEffect(r) { kotlinx.coroutines.delay(8_000); if (Shortcuts.refusal == r) Shortcuts.refusal = null }
        }
    }
}

