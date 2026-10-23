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
            glyph,
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
