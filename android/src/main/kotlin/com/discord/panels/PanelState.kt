package com.discord.panels

/** State of a side panel, as in Discord's OverlappingPanels library */
sealed class PanelState {
    object Opening : PanelState()
    object Opened : PanelState()
    object Closing : PanelState()
    object Closed : PanelState()
}
