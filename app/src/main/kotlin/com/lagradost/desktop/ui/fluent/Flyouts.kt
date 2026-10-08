package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues


// ---------------------------------------------------------------------------------------------
// Popup positioning
// ---------------------------------------------------------------------------------------------

/** Below the anchor (or above when there is no room), left aligned or right aligned, kept inside the window */
class BelowAnchor(private val gap: Int = 4, private val alignEnd: Boolean = false) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        var x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        x = x.coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height - 8) y = anchorBounds.top - gap - popupContentSize.height
        return IntOffset(x, y.coerceAtLeast(8))
    }
}

/** At a point inside the anchor (context menus), flipped to stay inside the window */
class AtPoint(private val point: Offset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        var x = anchorBounds.left + point.x.toInt()
        var y = anchorBounds.top + point.y.toInt()
        if (x + popupContentSize.width > windowSize.width - 8) x -= popupContentSize.width
        if (y + popupContentSize.height > windowSize.height - 8) y -= popupContentSize.height
        return IntOffset(x.coerceAtLeast(8), y.coerceAtLeast(8))
    }
}

/** Beside the anchor, vertically centred (side flyouts, tooltips) */
class RightOfAnchor(private val gap: Int = 4) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        var x = anchorBounds.right + gap
        if (x + popupContentSize.width > windowSize.width - 8) x = anchorBounds.left - gap - popupContentSize.width
        val y = (anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2).coerceIn(8, (windowSize.height - popupContentSize.height - 8).coerceAtLeast(8))
        return IntOffset(x.coerceAtLeast(8), y)
    }
}

@Composable
fun FlyoutSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.overlay)
    Box(
        modifier
            .background(c.flyout, shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, if (c.dark) Color(0x66757575) else Color(0x33000000), shape)
            .clip(shape),
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Menu
// ---------------------------------------------------------------------------------------------

sealed interface MenuEntry
class MenuItem(
    val text: String,
    val icon: String? = null,
    val shortcut: String? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
) : MenuEntry
object MenuSeparator : MenuEntry

@Composable
fun MenuFlyout(
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    position: PopupPositionProvider = BelowAnchor(),
) {
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        FlyoutSurface(Modifier.widthIn(min = 160.dp, max = 360.dp)) {
            Column(Modifier.padding(vertical = 4.dp).width(IntrinsicSize.Max)) {
                for (entry in entries) {
                    when (entry) {
                        MenuSeparator -> Box(Modifier.padding(vertical = 4.dp)) { Divider() }
                        is MenuItem -> MenuRow(entry) { onDismiss(); entry.onClick() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, onClick: () -> Unit) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            .height(32.dp)
            .clip(shape)
            .background(if (hovered && item.enabled) c.subtleHover else Color.Transparent, shape)
            .fluentClickable(source, item.enabled, shape, Role.Button, onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (item.checked) Icon(Icons.Check, size = 12.dp, tint = c.text)
            else if (item.icon != null) Icon(item.icon, size = 16.dp, tint = if (item.destructive) c.critical else c.text)
        }
        Box(Modifier.width(12.dp))
        FText(
            item.text, Modifier.weight(1f),
            color = when {
                !item.enabled -> c.textDisabled
                item.destructive -> c.critical
                else -> c.text
            },
            maxLines = 1,
        )
        if (item.shortcut != null) {
            Box(Modifier.width(24.dp))
            FText(item.shortcut, style = Fluent.type.caption, color = c.textTertiary, maxLines = 1)
        }
    }
}

/** Right click opens [entries] at the pointer */
@Composable
fun ContextMenuArea(entries: () -> List<MenuEntry>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var point by remember { mutableStateOf<Offset?>(null) }
    Box(modifier.onSecondaryClick { point = it }) {
        content()
        point?.let { p ->
            val list = entries()
            if (list.isNotEmpty()) MenuFlyout(list, { point = null }, AtPoint(p)) else point = null
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ComboBox
// ---------------------------------------------------------------------------------------------

@Composable
fun <T> ComboBox(
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    icon: String? = null,
    minWidth: Dp = 120.dp,
    height: Dp = 32.dp,
) {
    val c = Fluent.colors
    var open by remember { mutableStateOf(false) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    var anchorWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { anchorWidth = it.width }) {
        Row(
            Modifier
                .defaultMinSize(minWidth = minWidth)
                .height(height)
                .clip(shape)
                .background(if (!enabled) c.controlDisabled else if (hovered) c.controlHover else c.control, shape)
                .border(androidx.compose.ui.unit.Dp.Hairline, c.stroke, shape)
                .fluentClickable(source, enabled, shape, Role.DropdownList) { open = !open }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, size = 14.dp, tint = c.textSecondary)
                Box(Modifier.width(8.dp))
            }
            val text = selected?.let(label) ?: placeholder
            FText(text, Modifier.weight(1f, fill = false), color = if (selected == null) c.textTertiary else c.text, maxLines = 1)
            Box(Modifier.width(12.dp))
            Icon(Icons.ChevronDownSmall, size = 12.dp, tint = c.textSecondary)
        }
        if (open) {
            Popup(popupPositionProvider = BelowAnchor(2), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
                DropdownList(
                    items, selected, label,
                    width = with(density) { anchorWidth.toDp() },
                    onPick = { open = false; onSelect(it) },
                )
            }
        }
    }
}

@Composable
private fun <T> DropdownList(items: List<T>, selected: T?, label: (T) -> String, width: Dp, onPick: (T) -> Unit) {
    val c = Fluent.colors
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    var index by remember { mutableStateOf(items.indexOf(selected).coerceAtLeast(0)) }
    val short = items.size <= 40
    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (!short && index > 0) list.scrollToItem((index - 2).coerceAtLeast(0))
    }
    @Composable
    fun Row(i: Int, item: T) {
        val isSel = item == selected
        val source = rememberInteraction()
        val hovered by source.collectIsHoveredAsState()
        val shape = RoundedCornerShape(4.dp)
        Box(
            Modifier
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .fillMaxWidth()
                .height(32.dp)
                .clip(shape)
                .background(
                    when {
                        i == index -> c.subtleHover
                        isSel -> c.subtlePressed
                        hovered -> c.subtleHover
                        else -> Color.Transparent
                    }, shape,
                )
                .fluentClickable(source, true, shape, Role.DropdownList) { onPick(item) }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isSel) Box(Modifier.align(Alignment.CenterStart).size(3.dp, 16.dp).background(c.accent, RoundedCornerShape(2.dp)))
            FText(label(item), Modifier.padding(start = 4.dp), maxLines = 1, softWrap = false)
        }
    }
    FlyoutSurface(
        Modifier
            .widthIn(min = if (short) width else maxOf(width, 260.dp), max = 480.dp)
            .heightIn(max = 360.dp)
            .focusRequester(focus)
            .focusTarget()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { index = (index + 1).coerceAtMost(items.lastIndex); true }
                    Key.DirectionUp -> { index = (index - 1).coerceAtLeast(0); true }
                    Key.Enter, Key.NumPadEnter -> { items.getOrNull(index)?.let(onPick); true }
                    else -> false
                }
            },
    ) {
        if (short) {
            // fits its content like a Windows ComboBox
            val scroll = androidx.compose.foundation.rememberScrollState()
            Column(Modifier.width(IntrinsicSize.Max).verticalScroll(scroll).padding(vertical = 4.dp)) {
                items.forEachIndexed { i, item -> Row(i, item) }
            }
        } else {
            LaunchedEffect(index) { runCatching { list.animateScrollToItem((index - 3).coerceAtLeast(0)) } }
            LazyColumn(state = list, contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(items) { i, item -> Row(i, item) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ContentDialog
// ---------------------------------------------------------------------------------------------

object Overlays {
    class Dialog(
        val title: String?,
        val primary: String? = null,
        val secondary: String? = null,
        val close: String? = "Close",
        val primaryEnabled: () -> Boolean = { true },
        val onPrimary: () -> Unit = {},
        val onSecondary: () -> Unit = {},
        val onClose: () -> Unit = {},
        val width: Dp = 440.dp,
        /** A click on the dimmed area around the card closes it (menus that apply each choice at once) */
        val dismissOnOutside: Boolean = false,
        val body: @Composable (dismiss: () -> Unit) -> Unit,
    )

    val dialogs = mutableStateListOf<Dialog>()

    fun show(dialog: Dialog) {
        java.awt.EventQueue.invokeLater { dialogs.add(dialog) }
    }

    fun dismiss(dialog: Dialog) {
        java.awt.EventQueue.invokeLater { dialogs.remove(dialog) }
    }

    /** Simple message or confirmation */
    fun message(
        title: String,
        text: String,
        primary: String? = null,
        onPrimary: () -> Unit = {},
        close: String? = if (primary == null) "OK" else "Cancel",
    ) = show(Dialog(title, primary = primary, onPrimary = onPrimary, close = close) { FText(text, color = Fluent.colors.textSecondary) })

    /** Esc: closes the top dialog; returns whether one was open */
    fun onEscape(): Boolean {
        val top = dialogs.lastOrNull() ?: return false
        dialogs.remove(top)
        top.onClose()
        return true
    }
}

@Composable
fun DialogLayer() {
    val top = Overlays.dialogs.lastOrNull()
    val c = Fluent.colors
    AnimatedVisibility(top != null, enter = fadeIn(FluentMotion.tweenIn(200)), exit = fadeOut(FluentMotion.tweenOut(120))) {
        // keep the last dialog drawn while the exit animation runs
        val shown = remember(top) { top }
        // the card rises and grows into place
        val appear = remember(shown) { androidx.compose.animation.core.Animatable(0f) }
        androidx.compose.runtime.LaunchedEffect(shown) { appear.animateTo(1f, FluentMotion.tweenIn(180)) }
        Box(
            Modifier.fillMaxSize().background(c.scrim.copy(alpha = (c.scrim.alpha * 1.4f).coerceAtMost(0.75f))).pointerInput(shown) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        // the card consumes its own clicks: a press that reaches the scrim is outside it
                        val outside = e.type == androidx.compose.ui.input.pointer.PointerEventType.Press && e.changes.none { it.isConsumed }
                        e.changes.forEach { it.consume() }
                        if (outside && shown?.dismissOnOutside == true) { Overlays.dismiss(shown); shown.onClose() }
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            if (shown != null) Box(Modifier.graphicsLayer { val a = appear.value; alpha = a; val s = 0.97f + 0.03f * a; scaleX = s; scaleY = s }.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }) { ContentDialogCard(shown) }
        }
    }
}

@Composable
private fun ContentDialogCard(d: Overlays.Dialog) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(FluentShapes.overlay)
    val dismiss = { Overlays.dismiss(d) }
    Column(
        Modifier
            .padding(24.dp)
            .width(d.width)
            .shadow(48.dp, shape, clip = false)
            .background(c.flyout, shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, if (c.dark) Color(0x66757575) else Color(0x33000000), shape)
            .clip(shape),
    ) {
        Column(Modifier.padding(24.dp)) {
            if (d.title != null) {
                FText(d.title, style = Fluent.type.subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 3)
                Box(Modifier.height(14.dp))
            }
            Box(Modifier.heightIn(max = 520.dp)) { d.body(dismiss) }
        }
        if (d.primary != null || d.secondary != null || d.close != null) {
            Row(
                Modifier.fillMaxWidth().background(c.bgPane.copy(alpha = if (c.dark) 0.6f else 0.8f)).padding(24.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (d.primary != null) Button(d.primary, { d.onPrimary(); dismiss() }, Modifier.weight(1f), ButtonKind.Accent, enabled = d.primaryEnabled())
                if (d.secondary != null) Button(d.secondary, { d.onSecondary(); dismiss() }, Modifier.weight(1f))
                if (d.close != null) Button(d.close, { d.onClose(); dismiss() }, Modifier.weight(1f))
            }
        }
    }
}
