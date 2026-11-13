package com.lagradost.desktop.ui.fluent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Glyphs of the Segoe Fluent Icons font (Segoe MDL2 Assets on Windows 10 has the same code points) */
object Icons {
    /** The glyphs that point left or right and are turned round in a right-to-left language */
    val pointing: Set<String> = setOf(Back, Forward, ChevronLeft, ChevronRight, ChevronLeftSmall, ChevronRightSmall)

    /** Segoe code point -> the Material Icons code point that shows the same thing (the icon font where Segoe is missing, see FluentFonts.icons) */
    private val material: Map<Int, Int> by lazy {
        mapOf(0xE700 to 0xE5D2, 0xE80F to 0xE88A, 0xEA8A to 0xE88A, 0xE721 to 0xE8B6, 0xE8F1 to 0xE04A, 0xE896 to 0xF090, 0xE713 to 0xE8B8, 0xEA86 to 0xE87B, 0xE72B to 0xE5C4, 0xE72A to 0xE5C8, 0xE710 to 0xE145, 0xE711 to 0xE5CD, 0xE712 to 0xE5D3, 0xE768 to 0xE037, 0xE769 to 0xE034, 0xE71A to 0xE047, 0xE893 to 0xE044, 0xE892 to 0xE045, 0xE76B to 0xE5CB, 0xE76C to 0xE5CC, 0xE70D to 0xE5CF, 0xE70E to 0xE5CE, 0xE96E to 0xE5CF, 0xE96D to 0xE5CE, 0xE96F to 0xE5CB, 0xE970 to 0xE5CC, 0xE767 to 0xE050, 0xE74F to 0xE04F, 0xE740 to 0xE5D0, 0xE73F to 0xE5D1, 0xE73E to 0xE5CA, 0xE74D to 0xE872, 0xE72D to 0xE80D, 0xE72C to 0xE5D5, 0xE71C to 0xE152, 0xE70F to 0xE3C9, 0xEB51 to 0xE87E, 0xEB52 to 0xE87D, 0xE71B to 0xE157, 0xE77B to 0xE7FD, 0xE81C to 0xE889, 0xE121 to 0xE8B5, 0xE946 to 0xE88E, 0xE7BA to 0xE002, 0xEA39 to 0xE000, 0xE8B7 to 0xE2C7, 0xE8A7 to 0xE89E, 0xE774 to 0xE80B, 0xE714 to 0xE04B, 0xE895 to 0xE627, 0xEB9E to 0xE020, 0xEB9D to 0xE01F, 0xE7F0 to 0xE01C, 0xE8D2 to 0xE262, 0xE8D6 to 0xE3A1, 0xEC4A to 0xE9E4, 0xE799 to 0xE85B, 0xE787 to 0xE935, 0xE734 to 0xE83A, 0xE735 to 0xE838, 0xE8B1 to 0xE043, 0xE8CB to 0xE164, 0xE8FD to 0xE896, 0xF0E2 to 0xE9B0, 0xE8C8 to 0xE14D, 0xE718 to 0xF10D, 0xE72E to 0xE897, 0xE7B3 to 0xE8F4, 0xED1A to 0xE8F5, 0xE790 to 0xE40A, 0xE771 to 0xE3AE, 0xE775 to 0xE8E2, 0xE77F to 0xE14F, 0xE753 to 0xE2BD, 0xE777 to 0xE864, 0xE943 to 0xE86F, 0xE8AB to 0xE8D4, 0xE7F4 to 0xE333, 0xE8B2 to 0xE02C, 0xE897 to 0xE8FD, 0xE77A to 0xF10D, 0xE922 to 0xE3C6, 0xEE49 to 0xE911, 0xEC15 to 0xE307, 0xEA8F to 0xE7F4, 0xE7ED to 0xE7F5, 0xE9D2 to 0xE26B, 0xE74C to 0xE3F4)
    }

    /** [glyph] as Material Icons draws it; a glyph without a counterpart is left out rather than shown as an empty box */
    fun portable(glyph: String): String {
        val c = glyph.firstOrNull()?.code ?: return glyph
        return material[c]?.let { String(Character.toChars(it)) } ?: if (c in 0xE000..0xF8FF) "" else glyph
    }

    const val Menu = ""
    const val Home = ""
    const val HomeSolid = ""
    const val Search = ""
    const val Library = ""
    const val Download = ""
    const val Settings = ""
    const val Extensions = ""
    const val Back = ""
    const val Forward = ""
    const val Add = ""
    const val Close = ""
    const val More = ""
    const val Play = ""
    const val Pause = ""
    const val Stop = ""
    const val Next = ""
    const val Previous = ""
    const val ChevronLeft = ""
    const val ChevronRight = ""
    const val ChevronDown = ""
    const val ChevronUp = ""
    const val ChevronDownSmall = ""
    const val ChevronUpSmall = ""
    const val ChevronLeftSmall = ""
    const val ChevronRightSmall = ""
    const val Volume = ""
    const val Mute = ""
    const val Fullscreen = ""
    const val ExitFullscreen = ""
    const val Check = ""
    const val Delete = ""
    const val Share = ""
    const val Refresh = ""
    const val Filter = ""
    const val Edit = ""
    const val Favorite = "\uEB51"
    const val FavoriteFilled = "\uEB52"
    const val Link = ""
    const val Person = ""
    const val History = ""
    const val Clock = ""
    const val Info = ""
    const val Warning = ""
    const val Error = ""
    const val Folder = ""
    const val OpenInNewWindow = ""
    const val Globe = ""
    const val Video = ""
    const val Sync = ""
    const val Bookmark = "\uE8F1"
    const val BookmarkFilled = "\uE73E"
    const val Rewind = "\uEB9E"
    const val FastForward = "\uEB9D"
    const val Subtitles = "\uE7F0"
    const val Font = "\uE8D2"
    const val Audio = "\uE8D6"
    const val Speed = "\uEC4A"
    const val Aspect = ""
    const val Calendar = ""
    const val Star = ""
    const val StarFilled = ""
    const val Shuffle = ""
    const val Sort = ""
    const val List = ""
    const val Grid = ""
    const val Copy = ""
    const val Pin = ""
    const val Lock = ""
    const val Eye = ""
    const val Hide = ""
    const val Theme = ""
    const val Personalize = ""
    const val Language = ""
    const val Paste = ""
    const val Cloud = ""
    const val Backup = ""
    const val Update = ""
    const val Code = ""
    const val Account = ""
    const val Switch = ""
    const val Plugin = ""
    const val Tv = ""
    const val Movie = ""
    const val Anime = ""
    const val Music = ""
    const val Help = ""
    const val Cancel = ""
    const val Accept = ""
    const val Pause2 = ""
    const val Unpin = ""
    const val Skip = ""
    const val Maximize = ""
    const val Pip = ""
    const val BackToWindow = ""
    const val Cast =""
    const val Notification = ""
    const val Bell = ""
    const val Chart = ""
    const val Thumbnail = ""
}

/** One glyph of [FluentFonts.icons] centred in a square of [size] */
@Composable
fun Icon(
    glyph: String,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = Fluent.colors.text,
) {
    // an arrow that points the way of reading (back, forward, the chevrons of a list row) points the other way in a right-to-left language
    val mirror = glyph in Icons.pointing && androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Box(if (mirror) modifier.size(size).graphicsLayer { scaleX = -1f } else modifier.size(size), contentAlignment = Alignment.Center) {
        BasicText(
            if (FluentFonts.segoeIcons) glyph else Icons.portable(glyph),
            style = TextStyle(
                fontFamily = FluentFonts.icons,
                fontSize = size.value.sp,
                lineHeight = size.value.sp,
                color = tint,
            ),
            softWrap = false,
        )
    }
}
