@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.lagradost.desktop.ui.fluent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** True after the last input was the keyboard: focus rings are only drawn then */
object InputMode {
    var keyboard by mutableStateOf(false)
}

@Composable
fun FText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Fluent.type.body,
    color: Color = Fluent.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
) {
    BasicText(
        text,
        modifier,
        style = style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
    )
}

@Composable
fun FText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = Fluent.type.body,
    color: Color = Fluent.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(text, modifier, style = style.copy(color = color), maxLines = maxLines, overflow = overflow)
}

// ---------------------------------------------------------------------------------------------
// Interaction helpers
// ---------------------------------------------------------------------------------------------

/** Hover/press/focus of one control, plus the keyboard focus ring */
@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/** The Fluent focus visual: a 2 dp ring that is only drawn when the keyboard moved the focus */
@Composable
fun Modifier.focusRing(source: MutableInteractionSource, shape: Shape = RoundedCornerShape(FluentShapes.control)): Modifier {
    val focused by source.collectIsFocusedAsState()
    val ringColor = Fluent.colors.text
    val inner = Fluent.colors.bg
    val show = focused && InputMode.keyboard
    return this.drawWithContent {
        drawContent()
        if (show) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, ringColor, style = Stroke(2.dp.toPx()))
            drawOutline(outline, inner, style = Stroke(1.dp.toPx()))
        }
    }
}

/** A click that also reports hover/press, with the keyboard focus ring */
@Composable
fun Modifier.fluentClickable(
    source: MutableInteractionSource,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(FluentShapes.control),
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = this
    .focusRing(source, shape)
    .hoverable(source, enabled)
    .clickable(source, null, enabled, role = role, onClick = onClick)

/** Right-click handler (reports the pointer position inside the element) */
@Composable
fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier {
    val current by rememberUpdatedState(onClick)
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    val change = event.changes.first()
                    change.consume()
                    current(change.position)
                }
            }
        }
    }
}

/** Double click handler that does not consume the first press (the single click still fires) */
@Composable
fun Modifier.onDoubleClick(onDoubleClick: () -> Unit): Modifier {
    val current by rememberUpdatedState(onDoubleClick)
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            var last = 0L
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Release && event.button == PointerButton.Primary) {
                    val now = System.currentTimeMillis()
                    if (now - last < 350) {
                        last = 0
                        current()
                    } else last = now
                }
            }
        }
    }
}

fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

enum class ButtonKind { Standard, Accent, Subtle }

/** The accent as a fill: one flat colour, lighter under the pointer, darker when pressed (it used to be a gradient into the theme's second colour) */
fun FluentColors.accentBrush(hovered: Boolean = false, pressed: Boolean = false): Brush {
    fun tune(c: Color) = when {
        pressed -> androidx.compose.ui.graphics.lerp(c, Color.Black, 0.18f)
        hovered -> androidx.compose.ui.graphics.lerp(c, Color.White, 0.16f)
        else -> c
    }
    return SolidColor(tune(accent))
}

/** Kept for the callers that asked for a glow under the main button: there is none any more (a coloured shadow was part of the "made by AI" look) */
@Composable
fun Modifier.accentGlow(shape: Shape, strength: Float = 1f): Modifier = this

/** A frosted button on artwork, tinted with the accent (white tinted toward the accent colour) */
fun FluentColors.tintedGlass(hovered: Boolean): Color =
    androidx.compose.ui.graphics.lerp(Color.White, accent, 0.45f).copy(alpha = if (hovered) 0.34f else 0.22f)

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Standard,
    icon: String? = null,
    enabled: Boolean = true,
    height: Dp = 32.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    ButtonBase(onClick, modifier, kind, enabled, height, contentPadding) { color ->
        if (icon != null) {
            Icon(icon, size = 16.dp, tint = color)
            if (text.isNotEmpty()) Box(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) FText(text, style = Fluent.type.body, color = color, maxLines = 1, softWrap = false)
    }
}

@Composable
fun ButtonBase(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Standard,
    enabled: Boolean = true,
    height: Dp = 32.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable RowScope.(Color) -> Unit,
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(FluentShapes.control)
    val fill: Brush = when (kind) {
        ButtonKind.Standard -> SolidColor(when {
            !enabled -> c.controlDisabled
            pressed -> c.controlPressed
            hovered -> c.controlHover
            else -> c.control
        })
        // the main button of a dialog or a page: flat white with dark text (Settings > Look > Play button gives it the accent colour back)
        ButtonKind.Accent -> if (!enabled) SolidColor(c.controlDisabled) else if (Appearance.whitePrimary) SolidColor(c.text.copy(alpha = if (pressed) 0.78f else if (hovered) 0.9f else 1f)) else c.accentBrush(hovered, pressed)
        ButtonKind.Subtle -> SolidColor(when {
            !enabled -> Color.Transparent
            pressed -> c.subtlePressed
            hovered -> c.subtleHover
            else -> Color.Transparent
        })
    }
    val content0 = when {
        !enabled -> c.textDisabled
        kind == ButtonKind.Accent -> if (Appearance.whitePrimary) c.bg else c.onAccent
        pressed && kind != ButtonKind.Accent -> c.textSecondary
        else -> c.text
    }
    val stroke = when (kind) {
        ButtonKind.Standard -> if (hovered && enabled) c.strokeStrong else c.stroke
        ButtonKind.Accent -> if (enabled && !Appearance.whitePrimary) Color.White.copy(alpha = 0.22f) else Color.Transparent
        ButtonKind.Subtle -> Color.Transparent
    }
    Row(
        modifier
            .heightIn(min = height)
            .height(height)
            .clip(shape)
            .background(fill, shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, stroke, shape)
            .fluentClickable(source, enabled, shape, Role.Button, onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { content(content0) }
}

/** Square icon-only button (subtle by default) */
@Composable
fun IconButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    kind: ButtonKind = ButtonKind.Subtle,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val btn = @Composable {
        ButtonBase(onClick, modifier.width(size), kind, enabled, size, PaddingValues(0.dp)) { color ->
            Icon(glyph, size = iconSize, tint = tint ?: color)
        }
    }
    if (tooltip != null) Tooltip(tooltip) { btn() } else btn()
}

@Composable
fun Tooltip(text: String, side: Boolean = false, content: @Composable () -> Unit) {
    val c = Fluent.colors
    TooltipArea(
        tooltip = {
            Box(
                Modifier
                    .background(c.flyout, RoundedCornerShape(FluentShapes.control))
                    .border(androidx.compose.ui.unit.Dp.Hairline, c.strokeStrong.copy(alpha = 0.4f), RoundedCornerShape(FluentShapes.control))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { FText(text, style = Fluent.type.caption, color = c.text, maxLines = 4) }
        },
        delayMillis = 500,
        // side: beside the control, centred on it (the dock's icons: the tip under the pointer covered the next icon)
        tooltipPlacement = if (side) TooltipPlacement.ComponentRect(anchor = Alignment.CenterEnd, alignment = Alignment.CenterEnd, offset = DpOffset(10.dp, 0.dp)) else TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 24.dp)),
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Toggle, check box
// ---------------------------------------------------------------------------------------------

@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val knob by animateDpAsState(
        when {
            pressed -> 17.dp
            hovered -> 14.dp
            else -> 12.dp
        }, tween(83),
    )
    val x by animateDpAsState(if (checked) 40.dp - 4.dp - knob else 4.dp, tween(167))
    val fill by animateColorAsState(
        when {
            !enabled -> if (checked) c.controlDisabled else Color.Transparent
            checked -> if (pressed) c.accentPressed else if (hovered) c.accentHover else c.accent
            else -> if (hovered) c.controlHover else c.control
        }, tween(83),
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(40.dp, 20.dp)
            .background(fill, shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, if (checked) Color.Transparent else if (enabled) c.textSecondary else c.textDisabled, shape)
            .fluentClickable(source, enabled, shape, Role.Switch) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x, (20.dp - 2.dp - knob) / 2)
                .size(knob)
                .background(if (checked) c.onAccent else if (enabled) c.textSecondary else c.textDisabled, CircleShape),
        )
    }
}

@Composable
fun CheckBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier.fluentClickable(source, enabled, shape, Role.Checkbox) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (checked) c.accent else if (hovered) c.controlHover else c.control, shape)
                .border(androidx.compose.ui.unit.Dp.Hairline, if (checked) Color.Transparent else c.textSecondary, shape),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Check, size = 12.dp, tint = c.onAccent) }
        if (label != null) {
            Box(Modifier.width(8.dp))
            FText(label, color = if (enabled) c.text else c.textDisabled)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Text box
// ---------------------------------------------------------------------------------------------

@Composable
fun TextBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null,
    onKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    height: Dp = 32.dp,
    clearable: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    /** rounded ends and an accent ring when focused (search boxes) */
    pill: Boolean = false,
) {
    val c = Fluent.colors
    var focused by remember { mutableStateOf(false) }
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = if (pill) RoundedCornerShape(50) else RoundedCornerShape(FluentShapes.control)
    val fill = when {
        !enabled -> c.controlDisabled
        focused -> if (c.dark) c.flyout else Color.White
        hovered -> c.controlHover
        else -> c.control
    }
    Box(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .background(fill, shape)
            .border(if (pill && focused) 1.5.dp else androidx.compose.ui.unit.Dp.Hairline, if (pill && focused) c.accent else c.stroke, shape)
            .hoverable(source)
            .drawWithContent {
                drawContent()
                if (focused && !pill) {
                    val h = 2.dp.toPx()
                    drawRect(c.accent, Offset(0f, size.height - h), Size(size.width, h))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = if (pill) 16.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(leadingIcon, size = if (pill) 15.dp else 14.dp, tint = if (focused && pill) c.accentText else c.textSecondary)
                Box(Modifier.width(8.dp))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    FText(placeholder, color = c.textTertiary, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = Fluent.type.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.text),
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                        .onFocusChanged {
                            focused = it.isFocused
                            onFocusChange?.invoke(it.isFocused)
                        }
                        .onPreviewKeyEvent { event ->
                            if (onKey != null && onKey(event)) return@onPreviewKeyEvent true
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && onSubmit != null) {
                                onSubmit(); true
                            } else false
                        },
                )
            }
            if (clearable && value.isNotEmpty() && focused) {
                IconButton(Icons.Close, { onValueChange("") }, size = 24.dp, iconSize = 10.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------------------------

@Composable
fun ProgressBar(progress: Float?, modifier: Modifier = Modifier, height: Dp = 3.dp, color: Color = Fluent.colors.accent) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(height)
    if (progress != null) {
        Box(modifier.fillMaxWidth().height(height).clip(shape).background(c.strokeStrong.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(color, shape))
        }
    } else {
        val transition = rememberInfiniteTransition()
        val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart))
        Box(
            modifier.fillMaxWidth().height(height).clip(shape).background(c.strokeStrong.copy(alpha = 0.25f)).drawWithContent {
                val w = size.width
                val start = (t * 1.6f - 0.3f) * w
                drawRoundRect(
                    color, Offset(start.coerceAtLeast(0f), 0f),
                    Size((minOf(start + w * 0.3f, w) - start.coerceAtLeast(0f)).coerceAtLeast(0f), size.height),
                    androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            },
        )
    }
}

@Composable
fun ProgressRing(modifier: Modifier = Modifier, size: Dp = 32.dp, strokeWidth: Dp = 3.dp, color: Color = Fluent.colors.accent) {
    val transition = rememberInfiniteTransition()
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)))
    val sweep by transition.animateFloat(
        20f, 250f,
        infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
    )
    Box(
        modifier.size(size).drawWithContent {
            val sw = strokeWidth.toPx()
            drawArc(
                color, rotation, sweep, false,
                topLeft = Offset(sw / 2, sw / 2), size = Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Slider
// ---------------------------------------------------------------------------------------------

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Float? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    var width by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val span = valueRange.endInclusive - valueRange.start
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    // a white knob that grows a little under the pointer: the same as the player's seek bar (one look for every bar)
    val thumb by animateDpAsState(if (dragging || hovered) 16.dp else 14.dp, tween(83))
    fun setFromX(x: Float) {
        var v = valueRange.start + (x / width).coerceIn(0f, 1f) * span
        if (steps != null && steps > 0f) v = (v / steps).roundToInt() * steps
        onValueChange(v.coerceIn(valueRange.start, valueRange.endInclusive))
    }
    Box(
        modifier
            .height(32.dp)
            .hoverable(source)
            .focusRing(source)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true; setFromX(it.x) },
                    onDragEnd = { dragging = false; onValueChangeFinished?.invoke() },
                    onDragCancel = { dragging = false; onValueChangeFinished?.invoke() },
                ) { change, _ -> change.consume(); setFromX(change.position.x) }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press && e.buttons.isPrimaryPressed && !dragging) {
                            setFromX(e.changes.first().position.x)
                        }
                        if (e.type == PointerEventType.Release && !dragging) onValueChangeFinished?.invoke()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(c.strokeStrong.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(if (enabled) c.accent else c.textDisabled, RoundedCornerShape(2.dp)))
        Box(
            Modifier.fillMaxWidth().height(20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((fraction * (width - 20.dp.toPx())).roundToInt(), 0) }
                    .size(20.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(thumb).background(if (enabled) Color.White else c.textDisabled, CircleShape).border(androidx.compose.ui.unit.Dp.Hairline, if (c.dark) Color(0x33000000) else c.strokeStrong, CircleShape)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small things
// ---------------------------------------------------------------------------------------------

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Fluent.colors.divider))
}

/** Small pill label (genre, quality, type) */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val c = Fluent.colors
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .background(if (accent) c.accent else c.control, shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, if (accent) Color.Transparent else c.stroke, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) { FText(text, style = Fluent.type.caption, color = if (accent) c.onAccent else c.textSecondary, maxLines = 1) }
}

/** Stremio logo icon: purple rounded disc with white forward-pointing play triangle */
@Composable
fun StremioLogo(size: Dp = 14.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFA055F5), Color(0xFF6B26A6))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.55f)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.1f)
                lineTo(w * 0.9f, h * 0.5f)
                lineTo(w * 0.2f, h * 0.9f)
                close()
            }
            drawPath(path, color = Color.White)
        }
    }
}

/** Purple Stremio badge with logo for Stremio add-on providers */
@Composable
fun StremioBadge(modifier: Modifier = Modifier, showText: Boolean = true) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .clip(shape)
            .background(Color(0xFF8E4EC6).copy(alpha = 0.22f), shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, Color(0xFF8E4EC6).copy(alpha = 0.55f), shape)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StremioLogo(size = 12.dp)
        if (showText) {
            FText("Stremio", style = Fluent.type.caption.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color(0xFFD2A8FF), maxLines = 1)
        }
    }
}

/** Filter chip / segmented toggle button */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null) {
    val c = Fluent.colors
    val source = rememberInteraction()
    val hovered by source.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(32.dp)
            .clip(shape)
            // chosen = a flat light fill with dark text (the same as the main button); the others are quiet outlined buttons
            .background(if (selected) SolidColor(c.text.copy(alpha = if (hovered) 0.9f else 1f)) else SolidColor(if (hovered) c.controlHover else Color.Transparent), shape)
            .border(androidx.compose.ui.unit.Dp.Hairline, if (selected) Color.Transparent else if (hovered) c.strokeStrong else c.stroke, shape)
            .fluentClickable(source, true, shape, Role.Tab, onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) c.bg else c.textSecondary.let { if (hovered) c.text else it }
        if (icon != null) {
            Icon(icon, size = 14.dp, tint = fg)
            Box(Modifier.width(6.dp))
        }
        FText(text, style = if (selected) Fluent.type.bodyStrong else Fluent.type.body, color = fg, maxLines = 1, softWrap = false)
    }
}

/** Scrim-gradient helper used on media surfaces */
fun verticalScrim(from: Color, to: Color): Brush = Brush.verticalGradient(listOf(from, to))

@Composable
fun BoxScope.Fill(color: Color) {
    Box(Modifier.matchParentSize().background(color))
}
