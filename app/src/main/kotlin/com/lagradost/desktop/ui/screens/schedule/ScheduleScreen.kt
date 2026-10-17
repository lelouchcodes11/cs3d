package com.lagradost.desktop.ui.screens.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.notify.UpdateNotices
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.FluentScrollbar
import com.lagradost.desktop.ui.fluent.Icons
import com.lagradost.desktop.ui.fluent.PageHeader
import com.lagradost.desktop.ui.fluent.PillTabs
import com.lagradost.desktop.ui.fluent.ProgressRing
import com.lagradost.desktop.ui.fluent.smoothWheel
import com.lagradost.desktop.ui.shell.TopBarHeight

private const val TIMETABLE = 0
private const val SOON = 1
private const val NOTICES = 2

/**
 * Three tabs, one file each: the anime timetable (seven days, hour by hour; TimetableTab), what is coming out in cinemas and on streaming
 * services as a slider and shelves (ComingSoonTab), and what came out for the titles you follow, the bell on a title page (NoticesTab).
 */
@Composable
fun ScheduleScreen() {
    LaunchedEffect(Unit) { UpdateNotices.start() }
    // new episodes of what you follow come first when there are any you have not seen, else the timetable
    var tab by remember { mutableStateOf(if (UpdateNotices.unread > 0) NOTICES else TIMETABLE) }
    val state = rememberLazyListState()
    val tt = rememberTimetable()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        FluentScrollbar(state, TopBarHeight)
        LazyColumn(
            state = state, modifier = Modifier.fillMaxSize().smoothWheel(state),
            contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = TopBarHeight + 20.dp, bottom = 40.dp + com.lagradost.desktop.ui.shell.LocalDockInset.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "head") {
                PageHeader(
                    "Schedule",
                    subtitle = when (tab) {
                        TIMETABLE -> "Anime airing, day by day, in your time"
                        SOON -> "Films in cinemas and on streaming, new series and anime, in the next three months"
                        else -> {
                            val n = UpdateNotices.unread
                            if (UpdateNotices.items.isEmpty()) "New episodes of the titles you follow show up here" else if (n > 0) "$n new" else "You are up to date"
                        }
                    },
                    trailing = {
                        if (tab == NOTICES) {
                            if (UpdateNotices.checking) ProgressRing(size = 20.dp, strokeWidth = 2.dp)
                            else Button("Check now", { UpdateNotices.checkNow() }, kind = ButtonKind.Standard, icon = Icons.Refresh, height = 34.dp)
                            if (UpdateNotices.unread > 0) Button("Mark all read", { UpdateNotices.markAllRead() }, kind = ButtonKind.Standard, icon = Icons.Check, height = 34.dp)
                        }
                    },
                )
            }
            item(key = "tabs") {
                Row(Modifier.padding(bottom = 10.dp)) {
                    PillTabs(listOf("Anime timetable", "Movies & series", "Notifications"), tab, { tab = it }, counts = listOf(null, null, UpdateNotices.unread))
                }
            }
            when (tab) {
                TIMETABLE -> timetable(tt, maxWidth - 72.dp, state)
                SOON -> comingSoon(maxWidth - 72.dp)
                else -> notifications()
            }
        }
    }
}
