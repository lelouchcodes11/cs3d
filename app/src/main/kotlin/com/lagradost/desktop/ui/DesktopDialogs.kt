package com.lagradost.desktop.ui

import androidx.compose.runtime.mutableStateListOf
import com.lagradost.cloudstream3.SearchResponse

/**
 * Compose dialogs requested by the ported (non UI) code, e.g. the PIN prompt of AccountHelper or
 * the search result popup. The main window renders [requests] in order.
 */
object DesktopDialogs {
    sealed class Request {
        /** Same semantics as upstream AccountHelper.showPinInputDialog */
        class PinInput(
            val currentPin: String?,
            val editAccount: Boolean,
            val forStartup: Boolean,
            val errorText: String?,
            val callback: (String?) -> Unit,
        ) : Request()

        /** Bottom sheet with quick information about a search result (long press in upstream) */
        class SearchResultPopup(val card: SearchResponse, val load: Boolean) : Request()

        /** Upstream SingleSelectionHelper dialogs (single/multi choice, instant pick) */
        class Selection(
            val items: List<String>,
            val selected: List<Int>,
            val name: String,
            val showApply: Boolean,
            val isMultiSelect: Boolean,
            val instant: Boolean,
            val poster: String?,
            val callback: (List<Int>) -> Unit,
            val dismissCallback: () -> Unit,
        ) : Request()

        class TextInput(
            val name: String,
            val value: String,
            val inputType: Int?,
            val dismissCallback: () -> Unit,
            val callback: (String) -> Unit,
        ) : Request()

        class Text(val title: String, val text: CharSequence, val dismissCallback: () -> Unit) : Request()
    }

    val requests = mutableStateListOf<Request>()

    fun show(request: Request) {
        requests.add(request)
    }

    fun dismiss(request: Request) {
        requests.remove(request)
    }
}
