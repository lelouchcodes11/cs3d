package com.lagradost.desktop.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.desktop.DesktopBootstrap
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
import com.lagradost.desktop.ui.fluent.Tooltip
import com.lagradost.desktop.ui.fluent.fluentClickable
import com.lagradost.desktop.ui.fluent.hairBorder
import com.lagradost.desktop.ui.fluent.rememberInteraction
import com.lagradost.desktop.ui.fluent.selectedEdge

/** How a profile's picture is drawn: a stock or own picture, an animated character, or a show icon */
enum class AvatarStyle(val label: String) { Picture("Picture"), Character("Character"), Animated("Animated"), Icon("Icon") }

/** [palette] is an index of [avatarBackgrounds], [character] the id of an [AnimatedAvatar] or [ProfileIcon] */
class ProfileLook(val style: AvatarStyle, val palette: Int, val character: String)

/** The look of each profile: one small preference string per profile */
object ProfileLooks {
    private var version by mutableIntStateOf(0)
    private fun prefs() = androidx.preference.PreferenceManager.getDefaultSharedPreferences(com.lagradost.desktop.runtime.AndroidRuntime.context)
    private fun key(keyIndex: Int) = "desktop_profile_look/$keyIndex"
    private const val LEGACY_DEFAULT = "Character|0|smile"

    /** What is saved for the profile; a profile with no choice yet gets a cool show icon by default */
    fun get(account: DataStoreHelper.Account): ProfileLook {
        version
        val raw = runCatching { prefs().getString(key(account.keyIndex), null) }.getOrNull()
        if (raw != null && raw != LEGACY_DEFAULT) {
            val p = raw.split('|')
            val style = AvatarStyle.entries.firstOrNull { it.name == p.getOrNull(0) }
            val charId = p.getOrNull(2).orEmpty()
            if (style == AvatarStyle.Picture) {
                return ProfileLook(style, p.getOrNull(1)?.toIntOrNull() ?: 0, charId)
            }
            if (style == AvatarStyle.Icon || ProfileIconCatalog.find(charId) != null) {
                val icon = ProfileIconCatalog.find(charId) ?: ProfileIconCatalog.defaultFor(account.keyIndex)
                return ProfileLook(AvatarStyle.Icon, 0, icon.id)
            }
            val av = animatedAvatar(charId)
            if (av != null) {
                return ProfileLook(AvatarStyle.Animated, p.getOrNull(1)?.toIntOrNull() ?: av.bg, av.id)
            }
            val cool = ProfileIconCatalog.defaultFor(account.keyIndex)
            return ProfileLook(AvatarStyle.Icon, 0, cool.id)
        }
        // no choice yet: a picture of its own stays; otherwise the profile gets one of The Classics by default (Scarlet Chilleez for keyIndex 0)
        if (account.customImage == null) {
            val cool = ProfileIconCatalog.defaultFor(account.keyIndex)
            return ProfileLook(AvatarStyle.Icon, 0, cool.id)
        }
        return ProfileLook(AvatarStyle.Picture, 0, "")
    }

    fun set(keyIndex: Int, look: ProfileLook) {
        val raw = "${look.style.name}|${look.palette}|${look.character}"
        runCatching { prefs().edit().putString(key(keyIndex), if (raw == LEGACY_DEFAULT) "$raw|chosen" else raw).apply() }
        version++
    }
}

/** A profile's picture: its own or stock picture, an animated character, or a show icon */
@Composable
fun ProfileAvatar(
    account: DataStoreHelper.Account?,
    size: Dp,
    modifier: Modifier = Modifier,
    look: ProfileLook? = null,
    animated: Boolean = true,
    playing: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    val c = Fluent.colors
    val l = look ?: account?.let { ProfileLooks.get(it) } ?: ProfileLook(AvatarStyle.Icon, 0, ProfileIconCatalog.defaultFor(0).id)
    Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        when {
            l.style == AvatarStyle.Picture && account != null -> {
                Box(Modifier.size(size).background(c.control)) { UiImageView(account.image, account.name, Modifier.size(size)) }
            }
            l.style == AvatarStyle.Icon || ProfileIconCatalog.find(l.character) != null -> {
                val iconId = l.character.ifBlank { ProfileIconCatalog.defaultFor(account?.keyIndex ?: 0).id }
                val bmp = ProfileIconLoader.load(iconId)
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp,
                        contentDescription = account?.name ?: iconId,
                        modifier = Modifier.size(size),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    val av = animatedAvatar(iconId) ?: defaultAnimatedAvatar(account?.keyIndex ?: 0)
                    AnimatedAvatarView(
                        id = av.id,
                        size = size,
                        background = avatarBackgrounds[l.palette.coerceIn(0, avatarBackgrounds.lastIndex)],
                        animated = animated,
                        playing = playing,
                        seed = account?.keyIndex ?: av.id.hashCode(),
                    )
                }
            }
            else -> {
                val charId = l.character.ifBlank { defaultAnimatedAvatar(account?.keyIndex ?: 0).id }
                val av = animatedAvatar(charId) ?: defaultAnimatedAvatar(account?.keyIndex ?: 0)
                AnimatedAvatarView(
                    id = av.id,
                    size = size,
                    background = avatarBackgrounds[l.palette.coerceIn(0, avatarBackgrounds.lastIndex)],
                    animated = animated,
                    playing = playing,
                    seed = account?.keyIndex ?: av.id.hashCode(),
                )
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
    var character by mutableStateOf(look.character.ifBlank { ProfileIconCatalog.defaultFor(account.keyIndex).id })
    var custom by mutableStateOf(account.customImage)
    var index by mutableIntStateOf(account.defaultImageIndex)
    fun asLook() = ProfileLook(style, palette, character)
}

/** Saves a changed profile without switching to it */
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
    return copyProfilePicture(keyIndex, file)
}

private val PICTURE_TYPES = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

/** The folder for the viewer's own pictures */
private fun picturesFolder(): java.io.File = java.io.File(com.lagradost.desktop.runtime.AndroidRuntime.dataDir, "avatars").also { it.mkdirs() }

private fun myPictures(): List<java.io.File> =
    picturesFolder().listFiles { f -> f.isFile && f.extension.lowercase() in PICTURE_TYPES }?.sortedBy { it.name.lowercase() }.orEmpty()

/** Pictures chosen in the Windows file dialog copied into [picturesFolder] */
private fun addMyPictures(): Int {
    val dialog = java.awt.FileDialog(com.lagradost.desktop.ui.DesktopUiHost.window, "Choose pictures", java.awt.FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.setFilenameFilter { _, n -> n.lowercase().substringAfterLast('.') in PICTURE_TYPES }
    dialog.isVisible = true
    var added = 0
    for (file in dialog.files.orEmpty()) {
        if (!file.isFile || file.extension.lowercase() !in PICTURE_TYPES) continue
        val target = java.io.File(picturesFolder(), file.name)
        if (target.exists()) continue
        runCatching { file.copyTo(target); added++ }
    }
    return added
}

/** [file] copied into the data folder as the picture of this profile */
private fun copyProfilePicture(keyIndex: Int, file: java.io.File): String {
    return runCatching {
        val dir = java.io.File(com.lagradost.desktop.runtime.AndroidRuntime.dataDir, "files").also { it.mkdirs() }
        val copy = java.io.File(dir, "profile-$keyIndex-${System.currentTimeMillis()}." + file.extension.lowercase().ifBlank { "png" })
        file.copyTo(copy, overwrite = true)
        dir.listFiles { f -> f.name.startsWith("profile-$keyIndex-") && f != copy }?.forEach { it.delete() }
        copy.absolutePath
    }.getOrElse { file.absolutePath }
}

/** The "Edit profile" window: name, show icon, animated character, or custom picture. */
fun showProfileEditor(account: DataStoreHelper.Account, onSaved: () -> Unit = {}) {
    val draft = ProfileDraft(account)
    Overlays.show(
        Overlays.Dialog(
            title = "Edit profile", primary = "Save", close = "Cancel", width = 740.dp,
            primaryEnabled = { draft.name.isNotBlank() },
            onPrimary = {
                val updated = account.copy(name = draft.name.trim(), customImage = draft.custom, defaultImageIndex = draft.index)
                updateProfile(updated)
                ProfileLooks.set(account.keyIndex, draft.asLook())
                onSaved()
            },
        ) {
            val c = Fluent.colors
            var searchQuery by remember { mutableStateOf("") }
            var selectedCategory by remember { mutableStateOf("🔥 Popular") }

            val categories = remember {
                listOf("🔥 Popular", "All Shows (${ProfileIconCatalog.all.size})") +
                    ProfileIconCatalog.categories +
                    listOf("🎬 Animated Clips", "🖼️ Stock Pictures", "📁 My Pictures")
            }

            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Top Header Card with Preview Avatar & Name
                Row(
                    Modifier.fillMaxWidth().background(c.card, RoundedCornerShape(FluentShapes.card))
                        .hairBorder(c.stroke, RoundedCornerShape(FluentShapes.card))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(88.dp).clip(CircleShape).border(2.5.dp, c.selectedEdge, CircleShape)) {
                        ProfileAvatar(
                            account.copy(name = draft.name.ifBlank { account.name }, customImage = draft.custom, defaultImageIndex = draft.index),
                            88.dp,
                            look = draft.asLook(),
                            playing = true,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextBox(draft.name, { draft.name = it.take(24) }, Modifier.fillMaxWidth(), placeholder = "Profile name", leadingIcon = Icons.Person)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val selectedInfo = when {
                                draft.custom != null -> "Custom uploaded picture"
                                draft.style == AvatarStyle.Icon -> ProfileIconCatalog.find(draft.character)?.let { "${it.name} • ${it.category}" } ?: "Show Icon"
                                draft.style == AvatarStyle.Animated -> animatedAvatar(draft.character)?.let { "${it.label} • Animated" } ?: "Animated"
                                else -> "Stock Picture #${draft.index + 1}"
                            }
                            FText(selectedInfo, style = Fluent.type.caption, color = c.textSecondary, maxLines = 1)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button("Upload…", { pickProfilePicture(account.keyIndex)?.let { draft.custom = it; draft.style = AvatarStyle.Picture } }, kind = ButtonKind.Subtle, icon = Icons.Folder, height = 28.dp)
                                if (draft.custom != null || draft.style != AvatarStyle.Icon || draft.character != ProfileIconCatalog.defaultFor(account.keyIndex).id) {
                                    Button("Reset", {
                                        val def = ProfileIconCatalog.defaultFor(account.keyIndex)
                                        draft.style = AvatarStyle.Icon
                                        draft.character = def.id
                                        draft.custom = null
                                    }, kind = ButtonKind.Subtle, height = 28.dp)
                                }
                            }
                        }
                    }
                }

                // Search Bar
                TextBox(
                    searchQuery,
                    { searchQuery = it },
                    Modifier.fillMaxWidth(),
                    placeholder = "Search icons or characters (e.g. Luffy, Jinx, Eleven)...",
                    leadingIcon = Icons.Search,
                )

                // Category Chips Row (hidden when searching)
                if (searchQuery.isBlank()) {
                    // a mouse cannot scroll a plain row sideways: buttons at both ends move it, and it can be held and dragged
                    com.lagradost.desktop.ui.fluent.ArrowScrollRow {
                        for (cat in categories) {
                            val active = selectedCategory == cat
                            val interaction = rememberInteraction()
                            Box(
                                Modifier.clip(FluentShapes.pill)
                                    .background(if (active) c.controlHover else c.control, FluentShapes.pill)
                                    .hairBorder(if (active) c.selectedEdge else c.stroke, FluentShapes.pill)
                                    .fluentClickable(interaction, true, FluentShapes.pill, Role.Tab) { selectedCategory = cat }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                FText(
                                    cat,
                                    style = Fluent.type.bodyStrong,
                                    color = if (active) c.text else c.textSecondary,
                                )
                            }
                        }
                    }
                }

                // Content Section based on Search Query or Selected Category
                if (searchQuery.isNotBlank()) {
                    val q = searchQuery.trim().lowercase()
                    val matchingIcons = ProfileIconCatalog.all.filter { it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
                    val matchingAnimated = animatedAvatars.filter { it.label.lowercase().contains(q) || it.group.lowercase().contains(q) }

                    FText("Found ${matchingIcons.size + matchingAnimated.size} results for \"$searchQuery\"", style = Fluent.type.bodyStrong, color = c.textSecondary)

                    if (matchingIcons.isNotEmpty()) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (icon in matchingIcons) {
                                val on = draft.style == AvatarStyle.Icon && draft.custom == null && draft.character == icon.id
                                Tooltip("${icon.name} (${icon.category})") {
                                    Box(
                                        Modifier.size(58.dp).clip(CircleShape)
                                            .border(if (on) 3.dp else Dp.Hairline, if (on) c.selectedEdge else c.stroke, CircleShape)
                                            .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) {
                                                draft.style = AvatarStyle.Icon
                                                draft.character = icon.id
                                                draft.custom = null
                                            },
                                    ) {
                                        ProfileAvatar(null, 58.dp, look = ProfileLook(AvatarStyle.Icon, 0, icon.id))
                                    }
                                }
                            }
                        }
                    }

                    if (matchingAnimated.isNotEmpty()) {
                        FText("Animated Matches", style = Fluent.type.bodyStrong, color = c.textSecondary)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (av in matchingAnimated) {
                                val on = draft.style == AvatarStyle.Animated && draft.custom == null && draft.character == av.id
                                val source = rememberInteraction()
                                val hovered by source.collectIsHoveredAsState()
                                Tooltip("${av.label} (${av.group})") {
                                    Box(
                                        Modifier.size(58.dp).clip(CircleShape)
                                            .border(if (on) 3.dp else Dp.Hairline, if (on) c.selectedEdge else c.stroke, CircleShape)
                                            .fluentClickable(source, true, CircleShape, Role.RadioButton) {
                                                draft.style = AvatarStyle.Animated
                                                draft.character = av.id
                                                draft.palette = av.bg
                                                draft.custom = null
                                            },
                                    ) {
                                        ProfileAvatar(null, 58.dp, look = ProfileLook(AvatarStyle.Animated, if (on) draft.palette else av.bg, av.id), animated = false, playing = hovered)
                                    }
                                }
                            }
                        }
                    }

                    if (matchingIcons.isEmpty() && matchingAnimated.isEmpty()) {
                        FText("No icons match your search.", style = Fluent.type.body, color = c.textSecondary)
                    }
                } else {
                    when (selectedCategory) {
                        "🔥 Popular" -> {
                            FText("Handpicked Cool & Popular Characters", style = Fluent.type.bodyStrong, color = c.textSecondary)
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                for (icon in ProfileIconCatalog.coolDefaults) {
                                    val on = draft.style == AvatarStyle.Icon && draft.custom == null && draft.character == icon.id
                                    Tooltip("${icon.name} (${icon.category})") {
                                        Box(
                                            Modifier.size(62.dp).clip(CircleShape)
                                                .border(if (on) 3.dp else Dp.Hairline, if (on) c.selectedEdge else c.stroke, CircleShape)
                                                .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) {
                                                    draft.style = AvatarStyle.Icon
                                                    draft.character = icon.id
                                                    draft.custom = null
                                                },
                                        ) {
                                            ProfileAvatar(null, 62.dp, look = ProfileLook(AvatarStyle.Icon, 0, icon.id))
                                        }
                                    }
                                }
                            }
                        }

                        "🎬 Animated Clips" -> {
                            for (group in animatedAvatarGroups) {
                                FText(group, style = Fluent.type.bodyStrong, color = c.textSecondary)
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (av in animatedAvatars.filter { it.group == group }) {
                                        val on = draft.style == AvatarStyle.Animated && draft.custom == null && draft.character == av.id
                                        val source = rememberInteraction()
                                        val hovered by source.collectIsHoveredAsState()
                                        Tooltip(av.label) {
                                            Box(
                                                Modifier.size(58.dp).clip(CircleShape)
                                                    .border(if (on) 3.dp else Dp.Hairline, if (on) c.selectedEdge else c.stroke, CircleShape)
                                                    .fluentClickable(source, true, CircleShape, Role.RadioButton) {
                                                        draft.style = AvatarStyle.Animated
                                                        draft.character = av.id
                                                        draft.palette = av.bg
                                                        draft.custom = null
                                                    },
                                            ) {
                                                ProfileAvatar(null, 58.dp, look = ProfileLook(AvatarStyle.Animated, if (on) draft.palette else av.bg, av.id), animated = false, playing = hovered)
                                            }
                                        }
                                    }
                                }
                            }
                            if (draft.style == AvatarStyle.Animated) {
                                FText("Character Background Color", style = Fluent.type.bodyStrong, color = c.textSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    avatarBackgrounds.forEachIndexed { i, col ->
                                        val on = draft.palette == i
                                        Box(
                                            Modifier.size(32.dp).clip(CircleShape).background(col, CircleShape)
                                                .border(if (on) 2.5.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape)
                                                .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { draft.palette = i },
                                        )
                                    }
                                }
                            }
                        }

                        "🖼️ Stock Pictures" -> {
                            FText("CloudStream Classic Avatars", style = Fluent.type.bodyStrong, color = c.textSecondary)
                            com.lagradost.desktop.ui.fluent.ArrowScrollRow(spacing = 12.dp) {
                                DataStoreHelper.profileImages.forEachIndexed { i, res ->
                                    val on = draft.style == AvatarStyle.Picture && draft.custom == null && draft.index == i
                                    Box(
                                        Modifier.size(58.dp).clip(CircleShape).border(if (on) 3.dp else Dp.Hairline, if (on) c.selectedEdge else c.stroke, CircleShape)
                                            .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) { draft.custom = null; draft.index = i; draft.style = AvatarStyle.Picture },
                                    ) { UiImageView(com.lagradost.cloudstream3.utils.UiImage.Drawable(res), "", Modifier.size(58.dp)) }
                                }
                            }
                        }

                        "📁 My Pictures" -> {
                            var picturesVersion by remember { mutableIntStateOf(0) }
                            val mine = remember(picturesVersion) { myPictures() }
                            var pickedMine by remember { mutableStateOf<String?>(null) }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FText("Custom Images Folder", style = Fluent.type.bodyStrong, color = c.textSecondary)
                                Button("Add pictures…", { if (addMyPictures() > 0) picturesVersion++ }, kind = ButtonKind.Subtle, icon = Icons.Add, height = 28.dp)
                                Button("Open folder", { com.lagradost.desktop.DesktopPlatform.openFile(picturesFolder()) }, kind = ButtonKind.Subtle, icon = Icons.Folder, height = 28.dp)
                            }
                            if (mine.isEmpty()) {
                                FText("Add pictures (PNG, JPG, WebP, GIF), or put them in the avatars folder: they will appear here.", style = Fluent.type.caption, color = c.textSecondary)
                            } else {
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (f in mine) {
                                        val on = draft.style == AvatarStyle.Picture && draft.custom != null && pickedMine == f.name
                                        Tooltip(f.nameWithoutExtension) {
                                            Box(
                                                Modifier.size(58.dp).clip(CircleShape).border(if (on) 3.dp else Dp.Hairline, if (on) c.selectedEdge else c.stroke, CircleShape)
                                                    .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) {
                                                        draft.custom = copyProfilePicture(account.keyIndex, f)
                                                        draft.style = AvatarStyle.Picture
                                                        pickedMine = f.name
                                                    },
                                            ) { UiImageView(com.lagradost.cloudstream3.utils.UiImage.Image(f.absolutePath), f.nameWithoutExtension, Modifier.size(58.dp)) }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // Specific Category or All Shows
                            val list = if (selectedCategory.startsWith("All Shows")) ProfileIconCatalog.all
                            else ProfileIconCatalog.all.filter { it.category == selectedCategory }

                            FText("${selectedCategory.replace(Regex(""" \(\d+\)"""), "")} (${list.size} icons)", style = Fluent.type.bodyStrong, color = c.textSecondary)
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (icon in list) {
                                    val on = draft.style == AvatarStyle.Icon && draft.custom == null && draft.character == icon.id
                                    Tooltip("${icon.name} (${icon.category})") {
                                        Box(
                                            Modifier.size(58.dp).clip(CircleShape)
                                                .border(if (on) 3.dp else Dp.Hairline, if (on) c.selectedEdge else c.stroke, CircleShape)
                                                .fluentClickable(rememberInteraction(), true, CircleShape, Role.RadioButton) {
                                                    draft.style = AvatarStyle.Icon
                                                    draft.character = icon.id
                                                    draft.custom = null
                                                },
                                        ) {
                                            ProfileAvatar(null, 58.dp, look = ProfileLook(AvatarStyle.Icon, 0, icon.id))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
