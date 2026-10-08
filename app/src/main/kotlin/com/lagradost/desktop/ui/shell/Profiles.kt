package com.lagradost.desktop.ui.shell

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

/** How a profile's picture is drawn: a stock/own picture, or one of the animated looks */
enum class AvatarStyle(val label: String) { Picture("Picture"), Aurora("Aurora"), Orb("Orb"), Pulse("Pulse"), Wave("Waves"), Stars("Stars") }

class AvatarPalette(val a: Color, val b: Color, val c: Color)

val avatarPalettes = listOf(
    AvatarPalette(Color(0xFF8B7BFF), Color(0xFFFF4FA3), Color(0xFF2A1B6B)), // violet and pink
    AvatarPalette(Color(0xFF22D3EE), Color(0xFF2DD4BF), Color(0xFF0B3A52)), // ocean
    AvatarPalette(Color(0xFFFF7A45), Color(0xFFE11D74), Color(0xFF5A1230)), // sunset
    AvatarPalette(Color(0xFFA3E635), Color(0xFF14B8A6), Color(0xFF0F3B2A)), // matcha
    AvatarPalette(Color(0xFFFF3B5C), Color(0xFFFF8A3D), Color(0xFF4D0B1B)), // crimson
    AvatarPalette(Color(0xFFFFC53D), Color(0xFFEF4444), Color(0xFF4A2A06)), // gold
    AvatarPalette(Color(0xFF88C0D0), Color(0xFFD8E6EE), Color(0xFF243447)), // ice
    AvatarPalette(Color(0xFFFF79C6), Color(0xFFBD93F9), Color(0xFF2B2250)), // candy
)

val avatarEmojis = listOf("", "😀", "😎", "🦊", "🐼", "🐯", "🦁", "🐸", "🐙", "🦄", "🔥", "⭐", "🎬", "🍿", "👾", "🎮", "🌙", "💎")

class ProfileLook(val style: AvatarStyle, val palette: Int, val emoji: String)

/** The animated looks are switched off for now (profiles show their normal picture); the drawing code and the editor part stay for later */
const val ANIMATED_LOOKS = false

/** The look of each profile (the Android profile record has no room for it): one small preference string per profile */
object ProfileLooks {
    private var version by mutableIntStateOf(0)
    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)
    private fun key(keyIndex: Int) = "desktop_profile_look/$keyIndex"

    /** What is saved for the profile; a profile with no choice yet is animated (Aurora in a colour of its own), unless it has its own picture */
    fun get(account: DataStoreHelper.Account): ProfileLook {
        version
        if (!ANIMATED_LOOKS) return ProfileLook(AvatarStyle.Picture, 0, "")
        val raw = runCatching { prefs().getString(key(account.keyIndex), null) }.getOrNull()
        if (raw != null) {
            val p = raw.split('|')
            val style = AvatarStyle.entries.firstOrNull { it.name == p.getOrNull(0) }
            if (style != null) return ProfileLook(style, p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2).orEmpty())
        }
        return if (account.customImage != null) ProfileLook(AvatarStyle.Picture, 0, "") else ProfileLook(AvatarStyle.Aurora, Math.floorMod(account.keyIndex, avatarPalettes.size), "")
    }

    fun set(keyIndex: Int, look: ProfileLook) {
        runCatching { prefs().edit().putString(key(keyIndex), "${look.style.name}|${look.palette}|${look.emoji}").apply() }
        version++
    }
}

/** Seconds since the avatar appeared, ticking about 22 times a second while it is drawn animated (the window has focus, animations are on) */
@Composable
private fun rememberAvatarClock(animated: Boolean): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    val focused = LocalWindowInfo.current.isWindowFocused
    val on = animated && focused && Appearance.motion != Motion.Off && Appearance.animatedProfiles
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        val start = withFrameNanosCompat()
        while (true) {
            val now = withFrameNanosCompat()
            t.floatValue = (now - start) / 1e9f
            delay(45)
        }
    }
    return t
}

private suspend fun withFrameNanosCompat(): Long = androidx.compose.runtime.withFrameNanos { it }

/** A profile's picture: its own or stock picture, or an animated look (aurora ring, lava-lamp orb, pulse rings, waves, twinkling stars) */
@Composable
fun ProfileAvatar(account: DataStoreHelper.Account?, size: Dp, modifier: Modifier = Modifier, look: ProfileLook? = null, animated: Boolean = true) {
    val c = Fluent.colors
    val l = look ?: account?.let { ProfileLooks.get(it) } ?: ProfileLook(AvatarStyle.Aurora, 0, "")
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (l.style == AvatarStyle.Picture && account != null) {
            Box(Modifier.size(size).background(c.control)) { UiImageView(account.image, account.name, Modifier.size(size)) }
        } else {
            val time = rememberAvatarClock(animated)
            val palette = avatarPalettes[l.palette.coerceIn(0, avatarPalettes.lastIndex)]
            val seed = account?.keyIndex ?: 0
            Canvas(Modifier.size(size)) { drawAvatar(if (l.style == AvatarStyle.Picture) AvatarStyle.Aurora else l.style, palette, time.value, seed) }
            val text = l.emoji.ifBlank { account?.name?.trim()?.firstOrNull()?.uppercase() ?: "?" }
            FText(
                text,
                style = Fluent.type.bodyStrong.copy(fontSize = (size.value * (if (l.emoji.isBlank()) 0.44f else 0.46f)).sp, lineHeight = (size.value * 0.6f).sp, fontWeight = FontWeight.Black, shadow = Shadow(Color(0x66000000), Offset(0f, 1.5f), 5f)),
                color = Color.White, maxLines = 1, softWrap = false,
            )
        }
    }
}

private fun DrawScope.drawAvatar(style: AvatarStyle, p: AvatarPalette, t: Float, seed: Int) {
    val s = size.minDimension
    val center = Offset(s / 2f, s / 2f)
    when (style) {
        AvatarStyle.Aurora -> {
            drawRect(Brush.radialGradient(listOf(p.c.copy(alpha = 0.55f).compositeOver(Color.Black), p.c), center = center, radius = s * 0.7f))
            // a ring of the palette that turns, and an inner glow that breathes
            val ring = s * 0.12f
            rotate(t * 70f, center) {
                drawCircle(Brush.sweepGradient(listOf(p.a, p.b, p.a.copy(alpha = 0.15f), p.b, p.a), center), radius = s / 2f - ring / 2f, center = center, style = Stroke(ring))
            }
            val glow = 0.30f + 0.12f * sin(t * 1.7f)
            drawCircle(Brush.radialGradient(listOf(p.a.copy(alpha = glow), Color.Transparent), center = center, radius = s * 0.4f), radius = s * 0.4f, center = center)
        }
        AvatarStyle.Orb -> {
            drawRect(p.c)
            // three soft blobs that drift around like a lava lamp
            for (k in 0 until 3) {
                val ph = k * 2.1f
                val x = s * (0.5f + 0.26f * cos(t * (0.55f + 0.17f * k) + ph))
                val y = s * (0.5f + 0.26f * sin(t * (0.70f - 0.11f * k) + ph * 1.3f))
                val col = when (k) { 0 -> p.a; 1 -> p.b; else -> p.a.copy(alpha = 0.8f) }
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.95f), Color.Transparent), center = Offset(x, y), radius = s * 0.52f), radius = s * 0.52f, center = Offset(x, y))
            }
        }
        AvatarStyle.Pulse -> {
            drawRect(Brush.linearGradient(listOf(p.a, p.b), start = Offset.Zero, end = Offset(s, s)))
            // rings that grow from the middle and fade
            for (k in 0 until 3) {
                val phase = (t * 0.45f + k / 3f) % 1f
                drawCircle(Color.White.copy(alpha = (1f - phase) * 0.55f), radius = s * (0.12f + phase * 0.42f), center = center, style = Stroke(s * 0.035f))
            }
        }
        AvatarStyle.Wave -> {
            drawRect(Brush.verticalGradient(listOf(p.c, p.c.copy(alpha = 0.7f).compositeOver(p.a.copy(alpha = 0.25f)))))
            for (k in 0 until 2) {
                val path = Path()
                val base = s * (0.58f + 0.13f * k)
                val amp = s * (0.07f - 0.02f * k)
                path.moveTo(0f, s)
                var x = 0f
                while (x <= s + 1f) {
                    path.lineTo(x, base + amp * sin(x / s * 2f * PI.toFloat() * (1.3f + 0.4f * k) + t * (1.6f + 0.7f * k) + k * 1.7f))
                    x += s / 24f
                }
                path.lineTo(s, s)
                path.close()
                drawPath(path, (if (k == 0) p.a else p.b).copy(alpha = 0.78f))
            }
        }
        AvatarStyle.Stars -> {
            drawRect(Brush.linearGradient(listOf(p.c, p.a.copy(alpha = 0.85f).compositeOver(p.c)), start = Offset(0f, 0f), end = Offset(s, s)))
            rotate(t * 6f, center) {
                for (i in 0 until 11) {
                    val a = (i * 2.399f + seed * 0.7f)
                    val r = s * (0.12f + 0.34f * (((i * 37 + seed * 13) % 100) / 100f))
                    val tw = 0.25f + 0.75f * abs(sin(t * (0.9f + i * 0.31f) + i))
                    val pos = Offset(center.x + r * cos(a), center.y + r * sin(a))
                    drawCircle(Color.White.copy(alpha = tw), radius = s * (0.016f + 0.018f * ((i * 7) % 3)), center = pos)
                    if (i % 4 == 0) drawCircle(p.b.copy(alpha = tw * 0.35f), radius = s * 0.05f, center = pos)
                }
            }
        }
        AvatarStyle.Picture -> {}
    }
}


// ------------------------------------------------------------------------------------------ editing

/** The values being edited in the profile window */
private class ProfileDraft(val account: DataStoreHelper.Account) {
    private val look = ProfileLooks.get(account)
    var name by mutableStateOf(account.name)
    var style by mutableStateOf(look.style)
    var palette by mutableIntStateOf(look.palette)
    var emoji by mutableStateOf(look.emoji)
    var custom by mutableStateOf(account.customImage)
    var index by mutableIntStateOf(account.defaultImageIndex)
    fun asLook() = ProfileLook(style, palette, emoji)
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

/** The "Edit profile" window: name, animated look (style, colours, emoji) or a picture of your own. Applied with Save. */
fun showProfileEditor(account: DataStoreHelper.Account, onSaved: () -> Unit = {}) {
    val draft = ProfileDraft(account)
    Overlays.show(
        Overlays.Dialog(
            title = "Edit profile", primary = "Save", close = "Cancel", width = 600.dp,
            primaryEnabled = { draft.name.isNotBlank() },
            onPrimary = {
                val updated = account.copy(name = draft.name.trim(), customImage = draft.custom, defaultImageIndex = draft.index)
                updateProfile(updated)
                if (ANIMATED_LOOKS) ProfileLooks.set(account.keyIndex, draft.asLook())
                onSaved()
            },
        ) {
            val c = Fluent.colors
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(account.copy(name = draft.name.ifBlank { account.name }, customImage = draft.custom, defaultImageIndex = draft.index), 96.dp, look = draft.asLook())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextBox(draft.name, { draft.name = it.take(24) }, Modifier.fillMaxWidth(), placeholder = "Profile name", leadingIcon = Icons.Person)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button("Use a picture…", { pickProfilePicture(account.keyIndex)?.let { draft.custom = it; draft.style = AvatarStyle.Picture } }, kind = ButtonKind.Standard, icon = Icons.Folder, height = 32.dp)
                            if (draft.custom != null) Button("Remove picture", { draft.custom = null; if (draft.style == AvatarStyle.Picture && ANIMATED_LOOKS) draft.style = AvatarStyle.Aurora }, kind = ButtonKind.Subtle, height = 32.dp)
                        }
                    }
                }
                FText("Pictures", style = Fluent.type.bodyStrong, color = c.textSecondary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DataStoreHelper.profileImages.forEachIndexed { i, res ->
                        val on = draft.custom == null && draft.index == i
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).border(if (on) 2.5.dp else Dp.Hairline, if (on) c.accent else c.stroke, CircleShape)
                                .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { draft.custom = null; draft.index = i; draft.style = AvatarStyle.Picture },
                        ) { UiImageView(com.lagradost.cloudstream3.utils.UiImage.Drawable(res), "", Modifier.size(52.dp)) }
                    }
                }
                if (ANIMATED_LOOKS) {
                FText("Look", style = Fluent.type.bodyStrong, color = c.textSecondary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (s in AvatarStyle.entries) {
                        if (s == AvatarStyle.Picture && draft.custom == null) continue
                        val selected = draft.style == s
                        val tile = RoundedCornerShape(14.dp)
                        Column(
                            Modifier.width(76.dp).clip(tile).fluentClickable(rememberInteraction(), true, tile, Role.RadioButton) { draft.style = s }
                                .border(if (selected) 2.dp else Dp.Hairline, if (selected) c.accent else c.stroke, tile).padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ProfileAvatar(account.copy(customImage = draft.custom), 48.dp, look = ProfileLook(s, draft.palette, draft.emoji))
                            FText(s.label, style = Fluent.type.caption, color = if (selected) c.text else c.textSecondary, maxLines = 1)
                        }
                    }
                }
                if (draft.style != AvatarStyle.Picture) {
                    FText("Colours", style = Fluent.type.bodyStrong, color = c.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        avatarPalettes.forEachIndexed { i, p ->
                            val on = draft.palette == i
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(p.a, p.b)), CircleShape)
                                    .border(if (on) 2.5.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape)
                                    .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { draft.palette = i },
                            )
                        }
                    }
                    FText("Symbol", style = Fluent.type.bodyStrong, color = c.textSecondary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (e in avatarEmojis) {
                            val on = draft.emoji == e
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                Modifier.size(36.dp).clip(shape).background(if (on) c.accent.copy(alpha = 0.28f) else c.card, shape).border(Dp.Hairline, if (on) c.accent else c.stroke, shape)
                                    .fluentClickable(rememberInteraction(), true, shape, Role.RadioButton) { draft.emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { FText(if (e.isEmpty()) "Aa" else e, style = Fluent.type.body, maxLines = 1, softWrap = false) }
                        }
                    }
                }
                }
            }
        },
    )
}
