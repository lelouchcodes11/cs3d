package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A complete interface in the manner of a streaming service (the Netflix theme of Settings > Appearance > Themes): not only its colours but how the app is
 * laid out: the navigation (links along the top), the billboard at the top of Home, how a row is titled and how its cards look and answer to the pointer,
 * the profile screen, the shape of buttons and cards everywhere. Only the layout ideas are taken, no names, logos or marks of the service. The colours come
 * with the theme ([ThemePreset.skin] names the skin that goes with a theme); every feature of the app stays where it was reachable.
 */
enum class Skin { None, Netflix }

class SkinSpec(
    /** the app's name set as the service sets its own: the mark of the top bar */
    val wordmark: String,
    val wordmarkSp: Int,
    val wordmarkTracking: Float,
    /** how the navigation pages are called (id -> name; the ids are those of AppShell's nav items) */
    val labels: Map<String, String>,
    /** the pages shown as text links in the top bar, in order; the others are icons at the right */
    val links: List<String>,
    /** radius of cards (dp); controls and menus derive from it as everywhere */
    val cardRadius: Int,
    /** radius of buttons, chips and boxes */
    val buttonRadius: Dp,
    val play: String,
    val more: String,
    val rowTitleSp: Int,
    val seeAll: String,
)

object Skins {
    private val netflix = SkinSpec(
        wordmark = "CLOUDSTREAM", wordmarkSp = 22, wordmarkTracking = 0.6f,
        labels = mapOf("home" to "Home", "search" to "Search", "library" to "My List", "schedule" to "New & Popular", "downloads" to "Downloads", "settings" to "Settings"),
        links = listOf("home", "search", "schedule", "library", "downloads"),
        cardRadius = 6, buttonRadius = 4.dp,
        play = "Play", more = "More Info", rowTitleSp = 20, seeAll = "Explore All",
    )

    /**
     * True while the player page is up: the player keeps the Default theme's colours and shapes whatever the theme is (the Netflix player is left for later).
     * Set by the player page; the controls window of the native player reads it as well.
     */
    var inPlayer by mutableStateOf(false)

    /** The theme in use, as the colours are made from it: the Default theme's palette (Settings > Colors) in the player, else the chosen theme */
    val preset: ThemePreset
        get() = if (inPlayer) Themes.byId(Appearance.palette) else Themes.byId(Appearance.theme)

    /** The skin of the theme in use, unless "Full interface" is off in Settings (then only the colours are used) or the player is up. Reads state: call it where the UI is composed. */
    val active: Skin
        get() = if (!Appearance.skinLayout) Skin.None else preset.skin

    fun specOf(skin: Skin): SkinSpec? = when (skin) {
        Skin.None -> null
        Skin.Netflix -> netflix
    }

    val spec: SkinSpec? get() = specOf(active)

    /** The title of a main page as the interface in use calls it ("Library" is "My List" on Netflix's) */
    fun pageTitle(title: String): String {
        val spec = spec ?: return title
        return when (title) {
            "Library" -> spec.labels["library"]
            "Schedule" -> spec.labels["schedule"]
            "Downloads" -> spec.labels["downloads"]
            else -> null
        } ?: title
    }

    private val fullPill: Shape = RoundedCornerShape(percent = 50)
    private val rectangles = HashMap<Dp, Shape>()

    /** Shape of buttons, chips and text boxes: fully round, or a small radius for the service whose buttons are rectangles */
    val pill: Shape
        get() {
            val r = spec?.buttonRadius ?: return fullPill
            return rectangles.getOrPut(r) { RoundedCornerShape(r) }
        }
}
