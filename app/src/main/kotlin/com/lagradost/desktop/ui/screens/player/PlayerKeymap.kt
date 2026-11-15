package com.lagradost.desktop.ui.screens.player

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * A key combination of the player: Ctrl, Shift and Alt plus one key, or one of those three pressed and let go on its own ([key] null: "Ctrl" alone,
 * which fires when it is let go with no other key pressed in between, so Ctrl + → and the rest still work).
 */
data class KeyCombo(val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false, val key: Long? = null) {
    val modifierOnly: Boolean get() = key == null

    val label: String
        get() = buildList {
            if (ctrl) add("Ctrl")
            if (shift) add("Shift")
            if (alt) add("Alt")
            key?.let { add(keyName(Key(it))) }
        }.joinToString(" + ")

    fun encode(): String = buildString {
        if (ctrl) append('C')
        if (shift) append('S')
        if (alt) append('A')
        append(':')
        if (key != null) append(key)
    }

    companion object {
        fun of(key: Key, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) = KeyCombo(ctrl, shift, alt, key.keyCode)

        val CtrlAlone = KeyCombo(ctrl = true)

        fun decode(s: String): KeyCombo? {
            val i = s.indexOf(':').takeIf { it >= 0 } ?: return null
            val mods = s.substring(0, i)
            val key = s.substring(i + 1).takeIf { it.isNotEmpty() }?.toLongOrNull()
            val c = KeyCombo('C' in mods, 'S' in mods, 'A' in mods, key)
            // a modifier alone is exactly one modifier
            if (key == null && listOf(c.ctrl, c.shift, c.alt).count { it } != 1) return null
            return c
        }

        private val names = mapOf(
            Key.Spacebar to "Space", Key.DirectionLeft to "←", Key.DirectionRight to "→", Key.DirectionUp to "↑", Key.DirectionDown to "↓",
            Key.Comma to ",", Key.Period to ".", Key.MoveHome to "Home", Key.MoveEnd to "End", Key.PageUp to "Page Up", Key.PageDown to "Page Down",
            Key.Enter to "Enter", Key.Backspace to "Backspace", Key.Delete to "Delete", Key.Insert to "Insert", Key.Tab to "Tab", Key.Escape to "Esc",
            Key.Minus to "-", Key.Equals to "=", Key.Slash to "/", Key.Backslash to "\\", Key.Semicolon to ";", Key.Apostrophe to "'",
            Key.LeftBracket to "[", Key.RightBracket to "]", Key.Grave to "`",
            Key.MediaPlayPause to "Play/Pause key", Key.MediaNext to "Next track key", Key.MediaPrevious to "Previous track key",
        )

        fun keyName(k: Key): String = names[k] ?: runCatching {
            val code = (k.keyCode and 0xFFFFFFFFL).toInt()
            java.awt.event.KeyEvent.getKeyText(code).takeIf { it.isNotBlank() && !it.startsWith("Unknown") }
        }.getOrNull() ?: "Key ${k.keyCode}"

        /** The AWT code of a key, whatever side of the keyboard it is on (Ctrl left and right, or a Ctrl with no side, as remote desktops send) */
        private fun awtCode(k: Key): Int = (k.keyCode and 0xFFFFFFFFL).toInt()

        /** Ctrl, Shift, Alt, AltGr and the Windows key */
        fun isModifier(k: Key): Boolean = awtCode(k).let { it == java.awt.event.KeyEvent.VK_CONTROL || it == java.awt.event.KeyEvent.VK_SHIFT || it == java.awt.event.KeyEvent.VK_ALT || it == java.awt.event.KeyEvent.VK_ALT_GRAPH || it == java.awt.event.KeyEvent.VK_META || it == java.awt.event.KeyEvent.VK_WINDOWS }

        /** The combination of a key press (null for a modifier key itself, and for the Windows key: it belongs to Windows) */
        fun of(e: KeyEvent): KeyCombo? {
            if (isModifier(e.key)) return null
            return KeyCombo(e.isCtrlPressed, e.isShiftPressed, e.isAltPressed, e.key.keyCode)
        }

        fun modifierOf(k: Key): KeyCombo? = when (awtCode(k)) {
            java.awt.event.KeyEvent.VK_CONTROL -> KeyCombo(ctrl = true)
            java.awt.event.KeyEvent.VK_SHIFT -> KeyCombo(shift = true)
            java.awt.event.KeyEvent.VK_ALT -> KeyCombo(alt = true)
            else -> null
        }
    }
}

/** The groups of the shortcuts list, in the order they are shown */
enum class KeyGroup(val title: String) {
    Playback("Playback"),
    Seeking("Seeking"),
    Episodes("Episodes"),
    Audio("Volume and audio"),
    Picture("Subtitles and picture"),
    Window("Window and controls"),
}

/** What a shortcut of the player does; the keys come from [PlayerKeymap] (the defaults here, or the ones chosen in Settings) */
enum class PlayerAction(val id: String, val group: KeyGroup, val label: String, val about: String?, val defaults: List<KeyCombo>) {
    PlayPause("play_pause", KeyGroup.Playback, "Play or pause", "Hold a key without Ctrl, Shift or Alt to play at 2× while it is down", listOf(KeyCombo.of(Key.Spacebar), KeyCombo.of(Key.K))),
    Speed2x("speed_2x", KeyGroup.Playback, "2× speed on or off", "Press once for 2×, again for normal speed", listOf(KeyCombo.CtrlAlone)),
    Slower("speed_down", KeyGroup.Playback, "Slower", "0.25× at a time", listOf(KeyCombo.of(Key.Comma))),
    Faster("speed_up", KeyGroup.Playback, "Faster", "0.25× at a time", listOf(KeyCombo.of(Key.Period))),
    NormalSpeed("speed_normal", KeyGroup.Playback, "Normal speed", null, emptyList()),

    SeekBack("seek_back", KeyGroup.Seeking, "Back 10 s", "Keep it down to go further", listOf(KeyCombo.of(Key.DirectionLeft), KeyCombo.of(Key.J))),
    SeekForward("seek_forward", KeyGroup.Seeking, "Forward 10 s", "Keep it down to go further", listOf(KeyCombo.of(Key.DirectionRight), KeyCombo.of(Key.L))),
    SeekBackLong("seek_back_long", KeyGroup.Seeking, "Back 30 s", null, listOf(KeyCombo.of(Key.DirectionLeft, shift = true))),
    SeekForwardLong("seek_forward_long", KeyGroup.Seeking, "Forward 30 s", null, listOf(KeyCombo.of(Key.DirectionRight, shift = true))),
    ToStart("to_start", KeyGroup.Seeking, "Back to the start", null, listOf(KeyCombo.of(Key.MoveHome))),
    GoLive("go_live", KeyGroup.Seeking, "Live channel: go to the live picture", null, listOf(KeyCombo.of(Key.MoveEnd))),

    NextEpisode("next_episode", KeyGroup.Episodes, "Next episode", null, listOf(KeyCombo.of(Key.DirectionRight, ctrl = true), KeyCombo.of(Key.N))),
    PrevEpisode("prev_episode", KeyGroup.Episodes, "Previous episode", null, listOf(KeyCombo.of(Key.DirectionLeft, ctrl = true), KeyCombo.of(Key.P))),
    EpisodeList("episode_list", KeyGroup.Episodes, "Episode list", null, listOf(KeyCombo.of(Key.E))),

    VolumeUp("volume_up", KeyGroup.Audio, "Volume up", null, listOf(KeyCombo.of(Key.DirectionUp))),
    VolumeDown("volume_down", KeyGroup.Audio, "Volume down", null, listOf(KeyCombo.of(Key.DirectionDown))),
    Mute("mute", KeyGroup.Audio, "Mute", null, listOf(KeyCombo.of(Key.M))),
    NextAudio("next_audio", KeyGroup.Audio, "Next audio track", null, listOf(KeyCombo.of(Key.A))),

    NextSubtitle("next_subtitle", KeyGroup.Picture, "Next subtitle track (or off)", null, listOf(KeyCombo.of(Key.S))),
    PictureSize("picture_size", KeyGroup.Picture, "Picture size: fit, fill, zoom", null, listOf(KeyCombo.of(Key.Z))),
    Translate("translate", KeyGroup.Picture, "Translate the writing on the picture", null, listOf(KeyCombo.of(Key.T))),

    Fullscreen("fullscreen", KeyGroup.Window, "Full screen", "F11 and a double click work too", listOf(KeyCombo.of(Key.F))),
    Pip("pip", KeyGroup.Window, "Picture in picture", null, listOf(KeyCombo.of(Key.I))),
    PinControls("pin_controls", KeyGroup.Window, "Keep the controls on screen", "Press again to let them hide", listOf(KeyCombo.of(Key.C))),
    ShowShortcuts("show_shortcuts", KeyGroup.Window, "This list of shortcuts", null, listOf(KeyCombo.of(Key.F1))),
    ;

    /** Seeking keys repeat while they are held: the player takes at most a few jumps a second from them */
    val throttled: Boolean get() = this == SeekBack || this == SeekForward || this == SeekBackLong || this == SeekForwardLong
}

/**
 * The keys of the player: the defaults of [PlayerAction], changed per action in Settings > Keyboard shortcuts (or the player's own Keyboard
 * shortcuts window). A combination does one thing only: the editor refuses one that another action, or the whole app, already uses.
 * Kept as `action=combo|combo` lines in the app's preferences, only for the actions whose keys were changed.
 */
object PlayerKeymap {
    private const val PREF_KEY = "desktop_player_shortcuts"

    /** Changes with every edit (the lists that show the keys read it) */
    val version = mutableIntStateOf(0)

    private fun prefs() = com.lagradost.desktop.runtime.AndroidRuntime.applicationContext?.let { androidx.preference.PreferenceManager.getDefaultSharedPreferences(it) }

    @Volatile private var overrides: Map<PlayerAction, List<KeyCombo>> = load()

    @Volatile private var index: Map<KeyCombo, PlayerAction> = buildIndex()

    private fun load(): Map<PlayerAction, List<KeyCombo>> = runCatching {
        val raw = prefs()?.getString(PREF_KEY, null).orEmpty()
        raw.lines().mapNotNull { line ->
            val id = line.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val action = PlayerAction.entries.firstOrNull { it.id == id } ?: return@mapNotNull null
            action to line.substringAfter('=').split('|').filter { it.isNotBlank() }.mapNotNull(KeyCombo::decode)
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun buildIndex(): Map<KeyCombo, PlayerAction> {
        val map = LinkedHashMap<KeyCombo, PlayerAction>()
        // a combination saved twice (by an older build, or by hand) belongs to the first action that has it
        for (a in PlayerAction.entries) for (c in keysOf(a)) map.putIfAbsent(c, a)
        return map
    }

    private fun save() {
        val text = overrides.entries.joinToString("\n") { (a, list) -> a.id + "=" + list.joinToString("|") { it.encode() } }
        runCatching { prefs()?.edit()?.putString(PREF_KEY, text)?.apply() }
        index = buildIndex()
        version.intValue++
    }

    fun keysOf(action: PlayerAction): List<KeyCombo> = overrides[action] ?: action.defaults

    fun isDefault(action: PlayerAction): Boolean = keysOf(action) == action.defaults

    val allDefault: Boolean get() = PlayerAction.entries.all(::isDefault)

    /** The action a key press does, or null */
    fun actionFor(combo: KeyCombo): PlayerAction? = index[combo]

    /**
     * Keys that belong to the whole app or to Windows and never reach the player's list; the reason is shown when someone tries to use one.
     * Esc and F11 work in the player as they do everywhere (leave full screen, go back; full screen).
     */
    fun reserved(c: KeyCombo): String? {
        val k = c.key?.let(::Key)
        return when {
            k == Key.Escape -> "Esc closes menus, leaves full screen and goes back"
            k == Key.F11 && !c.ctrl && !c.alt && !c.shift -> "F11 is full screen everywhere in the app"
            c.alt && !c.ctrl && !c.shift && k == Key.DirectionLeft -> "Alt + ← goes back everywhere in the app"
            c.alt && k == Key.F4 -> "Alt + F4 closes the window"
            c.alt && k == Key.Tab -> "Alt + Tab belongs to Windows"
            c.ctrl && !c.alt && !c.shift && (k == Key.K || k == Key.E) -> "Ctrl + ${c.key?.let { KeyCombo.keyName(Key(it)) }} opens search everywhere in the app"
            !c.ctrl && !c.alt && !c.shift && k != null && k in digits -> "0 – 9 jump to 0 % … 90 % of the video"
            else -> null
        }
    }

    private val digits = setOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

    /** Who already has [combo] (other than [except]): the action's name or the app's reason; null when it is free */
    fun conflict(combo: KeyCombo, except: PlayerAction? = null): String? {
        reserved(combo)?.let { return it }
        val owner = index[combo] ?: return null
        if (owner == except) return null
        return "Already used by \"${owner.label}\" (${owner.group.title})"
    }

    /** Adds [combo] to [action]; returns why it was refused, or null when it was added */
    fun add(action: PlayerAction, combo: KeyCombo): String? {
        if (combo in keysOf(action)) return null
        conflict(combo, action)?.let { return it }
        overrides = overrides + (action to (keysOf(action) + combo))
        save()
        return null
    }

    fun remove(action: PlayerAction, combo: KeyCombo) {
        overrides = overrides + (action to keysOf(action).filter { it != combo })
        save()
    }

    /**
     * Puts [action]'s own keys back. A default key that another action took meanwhile stays with that action (a combination does one thing),
     * so the reset gives back only the free ones.
     */
    fun reset(action: PlayerAction) {
        val others = PlayerAction.entries.filter { it != action }.flatMap { keysOf(it) }.toSet()
        val keys = action.defaults.filter { it !in others }
        overrides = if (keys == action.defaults) overrides - action else overrides + (action to keys)
        save()
    }

    fun resetAll() {
        overrides = emptyMap()
        save()
    }
}

/**
 * Takes the keyboard while a shortcut is being recorded: [NativeKeys][com.lagradost.desktop.NativeKeys] hands it every key first, so that keys the
 * app uses (Esc, Alt + ←, Ctrl + K) are recorded or refused instead of acting.
 */
object KeyRecorder {
    @Volatile
    var listener: ((KeyEvent) -> Boolean)? = null

    /** A recorder for one press: [done] gets the combination, or null when Esc cancelled it */
    fun start(done: (KeyCombo?) -> Unit) {
        var pendingModifier: KeyCombo? = null
        listener = listener@{ e ->
            when (e.type) {
                KeyEventType.KeyDown -> {
                    val mod = KeyCombo.modifierOf(e.key)
                    if (mod != null || KeyCombo.isModifier(e.key)) {
                        // a modifier: it counts alone only if it is let go before any other key (and only when it is the only one down)
                        val count = listOf(e.isCtrlPressed, e.isShiftPressed, e.isAltPressed).count { it }
                        pendingModifier = if (count <= 1) mod else null
                        return@listener true
                    }
                    pendingModifier = null
                    if (e.key == Key.Escape && !e.isCtrlPressed && !e.isAltPressed && !e.isShiftPressed) { listener = null; done(null); return@listener true }
                    val combo = KeyCombo.of(e) ?: return@listener true
                    listener = null
                    done(combo)
                }
                KeyEventType.KeyUp -> {
                    val mod = KeyCombo.modifierOf(e.key)
                    if (mod != null && mod == pendingModifier) { listener = null; done(mod) }
                    pendingModifier = null
                }
                else -> {}
            }
            true
        }
    }

    fun cancel() { listener = null }
}
