package com.lagradost.desktop.ui.shell

import com.lagradost.desktop.ui.fluent.hairBorder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.lagradost.desktop.ui.fluent.Tooltip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
import com.lagradost.desktop.ui.components.UiImageView
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FText
import com.lagradost.desktop.ui.fluent.Fluent
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.Motion
import com.lagradost.desktop.ui.fluent.Overlays
import com.lagradost.desktop.ui.fluent.TextBox
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.rememberInteraction
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How a profile's picture is drawn: a stock or own picture, or one of the drawn characters (ProfileCharacters.kt) */
enum class AvatarStyle(val label: String) { Picture("Picture"), Character("Character") }

/** [palette] is an index of [avatarBackgrounds], [character] the id of an [AvatarCharacter]; both only matter for the Character style */
class ProfileLook(val style: AvatarStyle, val palette: Int, val character: String)

/** The look of each profile (the Android profile record has no room for it): one small preference string per profile */
object ProfileLooks {
    private var version by mutableIntStateOf(0)
    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)
    private fun key(keyIndex: Int) = "desktop_profile_look/$keyIndex"

    /** What is saved for the profile; a profile with no choice yet shows its stock or own picture */
    fun get(account: DataStoreHelper.Account): ProfileLook {
        version
        val raw = runCatching { prefs().getString(key(account.keyIndex), null) }.getOrNull()
        if (raw != null) {
            val p = raw.split('|')
            val style = AvatarStyle.entries.firstOrNull { it.name == p.getOrNull(0) }
            if (style != null) return ProfileLook(style, p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2).orEmpty())
        }
        return ProfileLook(AvatarStyle.Picture, 0, "")
    }

    fun set(keyIndex: Int, look: ProfileLook) {
        runCatching { prefs().edit().putString(key(keyIndex), "${look.style.name}|${look.palette}|${look.character}").apply() }
        version++
    }
}

/**
 * How far a character is into its little action (0 = at rest): a blink, a wiggle of the ears, a glint. It runs for about a second every few
 * seconds, at a time of its own for each picture, and only while the window has focus and motion is on. Between the actions nothing changes,
 * so nothing is redrawn.
 */
@Composable
private fun rememberAvatarAction(animated: Boolean, seed: Int): State<Float> {
    val p = remember { mutableFloatStateOf(0f) }
    val focused = LocalWindowInfo.current.isWindowFocused
    val on = animated && focused && Appearance.motion != Motion.Off && Appearance.animatedProfiles
    LaunchedEffect(on) {
        p.floatValue = 0f
        if (!on) return@LaunchedEffect
        val rnd = java.util.Random(seed * 7919L + System.nanoTime())
        delay(500L + rnd.nextInt(2500))
        while (true) {
            val start = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val k = (androidx.compose.runtime.withFrameNanos { it } - start) / 1e9f / 0.95f
                if (k >= 1f) break
                p.floatValue = k
            }
            p.floatValue = 0f
            delay(2400L + rnd.nextInt(3800))
        }
    }
    return p
}

/** A profile's picture: its own or stock picture, or a character */
@Composable
fun ProfileAvatar(account: DataStoreHelper.Account?, size: Dp, modifier: Modifier = Modifier, look: ProfileLook? = null, animated: Boolean = true) {
    val c = Fluent.colors
    val l = look ?: account?.let { ProfileLooks.get(it) } ?: ProfileLook(AvatarStyle.Character, 0, "smile")
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (l.style == AvatarStyle.Picture && account != null) {
            Box(Modifier.size(size).background(c.control)) { UiImageView(account.image, account.name, Modifier.size(size)) }
        } else {
            val character = avatarCharacter(l.character)
            val action = rememberAvatarAction(animated, account?.keyIndex ?: character.id.hashCode())
            val background = avatarBackgrounds[l.palette.coerceIn(0, avatarBackgrounds.lastIndex)]
            Canvas(Modifier.size(size)) {
                drawRect(background)
                character.draw(this, action.value)
            }
        }
    }
}


// ------------------------------------------------------------------------------------------ editing

/** The values being edited in the profile window */
private class ProfileDraft(val account: DataStoreHelper.Account) {
    private val look = ProfileLooks.get(account)
    var name by mutableStateOf(account.name)
    var style by mutableStateOf(look.style)
    var palette by mutableIntStateOf(look.palette)
    var character by mutableStateOf(look.character)
    var custom by mutableStateOf(account.customImage)
    var index by mutableIntStateOf(account.defaultImageIndex)
    fun asLook() = ProfileLook(style, palette, character)
}

/** Saves a changed profile without switching to it (the engine's own update call selects the profile it saves) */
fun updateProfile(updated: DataStoreHelper.Account) {
    val ctx = DesktopBootstrap.activity
    val list = runCatching { DataStoreHelper.getAccounts(ctx) }.getOrDefault(emptyList()).toMutableList()
    val i = list.indexOfFirst { it.keyIndex == updated.keyIndex }
    if (i >= 0) list[i] = updated else list.add(updated)
    DataStoreHelper.accounts = list.toTypedArray()
    if (updated.keyIndex == DataStoreHelper.selectedKeyIndex) {
        com.lagradost.desktop.core.AppVms.get<com.lagradost.cloudstream3.ui.home.HomeViewModel>().currentAccount.postValue(updated)
    }
}

/** A picture chosen in the Windows file dialog, copied into the data folder for this profile; null when cancelled */
private fun pickProfilePicture(keyIndex: Int): String? {
    val dialog = java.awt.FileDialog(com.lagradost.desktop.ui.DesktopUiHost.window, "Choose a picture", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, n -> n.lowercase().substringAfterLast('.') in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") }
    dialog.isVisible = true
    val file = dialog.file?.let { java.io.File(dialog.directory, it) }?.takeIf { it.isFile } ?: return null
    return runCatching {
        val dir = java.io.File(com.lagradost.desktop.runtime.AndroidRuntime.dataDir, "files").also { it.mkdirs() }
        val copy = java.io.File(dir, "profile-$keyIndex-${System.currentTimeMillis()}." + file.extension.lowercase().ifBlank { "png" })
        file.copyTo(copy, overwrite = true)
        dir.listFiles { f -> f.name.startsWith("profile-$keyIndex-") && f != copy }?.forEach { it.delete() }
        copy.absolutePath
    }.getOrElse { file.absolutePath }
}

/** The "Edit profile" window: name, a stock picture, a character on a colour of your choice, or a picture of your own. Applied with Save. */
fun showProfileEditor(account: DataStoreHelper.Account, onSaved: () -> Unit = {}) {
    val draft = ProfileDraft(account)
    Overlays.show(
        Overlays.Dialog(
            title = "Edit profile", primary = "Save", close = "Cancel", width = 600.dp,
            primaryEnabled = { draft.name.isNotBlank() },
            onPrimary = {
                val updated = account.copy(name = draft.name.trim(), customImage = draft.custom, defaultImageIndex = draft.index)
                updateProfile(updated)
                ProfileLooks.set(account.keyIndex, draft.asLook())
                onSaved()
            },
        ) {
            val c = Fluent.colors
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(account.copy(name = draft.name.ifBlank { account.name }, customImage = draft.custom, defaultImageIndex = draft.index), 96.dp, look = draft.asLook())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextBox(draft.name, { draft.name = it.take(24) }, Modifier.fillMaxWidth(), placeholder = "Profile name", leadingIcon = Icons.Person)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button("Use a picture…", { pickProfilePicture(account.keyIndex)?.let { draft.custom = it; draft.style = AvatarStyle.Picture } }, kind = ButtonKind.Standard, icon = Icons.Folder, height = 32.dp)
                            if (draft.custom != null) Button("Remove picture", { draft.custom = null }, kind = ButtonKind.Subtle, height = 32.dp)
                        }
                    }
                }
                for ((group, list) in avatarCharacters.groupBy { it.group }) {
                FText(group, style = Fluent.type.bodyStrong, color = c.textSecondary)
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (ch in list) {
                        val on = draft.style == AvatarStyle.Character && draft.character == ch.id
                        Tooltip(ch.label) {
                            Box(
                                Modifier.size(52.dp).clip(CircleShape).border(if (on) 2.5.dp else Dp.Hairline, if (on) c.accent else c.stroke, CircleShape)
                                    .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { draft.style = AvatarStyle.Character; draft.character = ch.id; draft.palette = ch.bg },
                            ) { ProfileAvatar(null, 52.dp, look = ProfileLook(AvatarStyle.Character, if (on) draft.palette else ch.bg, ch.id), animated = false) }
                        }
                    }
                }
                }
                if (draft.style == AvatarStyle.Character) {
                    FText("Background", style = Fluent.type.bodyStrong, color = c.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        avatarBackgrounds.forEachIndexed { i, col ->
                            val on = draft.palette == i
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(col, CircleShape)
                                    .border(if (on) 2.5.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape)
                                    .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { draft.palette = i },
                            )
                        }
                    }
                }
                FText("Pictures", style = Fluent.type.bodyStrong, color = c.textSecondary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DataStoreHelper.profileImages.forEachIndexed { i, res ->
                        val on = draft.style == AvatarStyle.Picture && draft.custom == null && draft.index == i
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).border(if (on) 2.5.dp else Dp.Hairline, if (on) c.accent else c.stroke, CircleShape)
                                .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { draft.custom = null; draft.index = i; draft.style = AvatarStyle.Picture },
                        ) { UiImageView(com.lagradost.cloudstream3.utils.UiImage.Drawable(res), "", Modifier.size(52.dp)) }
                    }
                }
            }
        },
    )
}
