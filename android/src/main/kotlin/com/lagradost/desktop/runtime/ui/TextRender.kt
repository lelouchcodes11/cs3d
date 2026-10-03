package com.lagradost.desktop.runtime.ui

import android.text.InputType
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.view.inputmethod.EditorInfo
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import android.widget.ToggleButton
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import com.google.android.material.button.MaterialButton
import kotlin.math.max

// ------------------------------------------------------------------------------------------------
// Drawing (TextView.onDraw)

/** Text color of a TextView (MaterialButton without a color of its own: colorOnPrimary) */
private fun textColorOf(view: TextView): Int =
    if (view is MaterialButton && !view.hasExplicitTextColor() && view.getBackground() == null) ThemeBridge.colorOnPrimary
    else view.getCurrentTextColor()

/** Where the text of a TextView starts (compound padding, vertical gravity offset) */
private fun textOrigin(view: TextView): Offset =
    Offset(view.getCompoundPaddingLeft().toFloat(), (view.getExtendedPaddingTop() + view.getVerticalOffset(false)).toFloat())

/** The Material button shape of a MaterialButton without a background drawable */
private fun DrawScope.drawMaterialButton(view: MaterialButton) {
    if (view.getBackground() != null) return
    val inset = WidgetDefaults.dp(6f).toFloat()
    val enabledColor = view.getBackgroundTintList().colorFor(view, ThemeBridge.colorPrimary)
    val color = if (view.isEnabled()) enabledColor else (ThemeBridge.textColorPrimary and 0x00FFFFFF) or 0x1F000000
    val radius = if (view.getCornerRadius() > 0) view.getCornerRadius().toFloat() else WidgetDefaults.dp(4f).toFloat()
    drawRoundRect(Color(color), topLeft = Offset(0f, inset), size = Size(size.width, max(0f, size.height - 2 * inset)), cornerRadius = CornerRadius(radius, radius))
    val stroke = view.getStrokeWidth().toFloat()
    val strokeColor = view.getStrokeColor()?.colorFor(view, 0) ?: 0
    if (stroke > 0f && (strokeColor ushr 24) != 0) {
        drawRoundRect(
            Color(strokeColor), topLeft = Offset(stroke / 2, inset + stroke / 2),
            size = Size(max(0f, size.width - stroke), max(0f, size.height - 2 * inset - stroke)),
            cornerRadius = CornerRadius(radius, radius), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
    }
}

/** TextView.onDraw: compound drawables, then the text (or hint) clipped to the text columns */
internal fun DrawScope.drawTextView(view: TextView, drawText: Boolean = true) {
    view.renderVersion.intValue
    view.compoundVersion.intValue
    val w = size.width.toInt()
    val h = size.height.toInt()
    val cl = view.getCompoundPaddingLeft()
    val ct = view.getCompoundPaddingTop()
    val cr = view.getCompoundPaddingRight()
    val cb = view.getCompoundPaddingBottom()
    val d = view.getCompoundDrawables()
    val state = view.getDrawableState()
    val vspace = h - cb - ct
    val hspace = w - cr - cl
    val iconShift = (view as? MaterialButton)?.iconOffsetX(w) ?: 0
    for (i in 0 until 4) {
        val drawable = d[i] ?: continue
        val (dw, dh) = view.compoundSize(i).let { it[0] to it[1] }
        drawable.setState(state)
        val (x, y) = when (i) {
            0 -> view.getPaddingLeft() + iconShift to ct + (vspace - dh) / 2
            2 -> w - view.getPaddingRight() - dw + iconShift to ct + (vspace - dh) / 2
            1 -> cl + (hspace - dw) / 2 to view.getPaddingTop()
            else -> cl + (hspace - dw) / 2 to h - view.getPaddingBottom() - dh
        }
        drawAndroidDrawable(drawable, dw, dh, x, y)
    }
    if (!drawText) return
    val hint = view.textState().isEmpty() && view.getHint() != null
    val layout = view.textLayoutForDrawing(hint) ?: return
    val color = if (hint) view.getHintTextColors().colorFor(view, ThemeBridge.textColorSecondary) else textColorOf(view)
    val origin = textOrigin(view)
    clipRect(cl.toFloat(), 0f, (w - cr).toFloat(), h.toFloat()) {
        translate(origin.x, origin.y) { drawText(layout, color = Color(color)) }
    }
}

/** ClickableSpan at a position in the view (LinkMovementMethod) */
private fun clickableSpanAt(view: TextView, pos: Offset): ClickableSpan? {
    val text = view.textState() as? Spanned ?: return null
    val layout = view.textLayoutForDrawing(false) ?: return null
    val origin = textOrigin(view)
    val local = pos - origin
    if (local.y < 0 || local.y > layout.size.height) return null
    val offset = layout.getOffsetForPosition(local)
    val line = layout.getLineForOffset(offset)
    if (local.x < layout.getLineLeft(line) || local.x > layout.getLineRight(line)) return null
    return text.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
}

// ------------------------------------------------------------------------------------------------
// TextView (and Button, ToggleButton)

@Composable
internal fun TextViewNode(view: TextView, modifier: Modifier) {
    val ellipsize = view.getEllipsize()
    val marquee = ellipsize == TextUtils.TruncateAt.MARQUEE && (view.isSelected() || view.isFocused())
    val selectable = view.isTextSelectable()
    val button = if (view is MaterialButton) Modifier.drawBehind { drawMaterialButton(view) } else Modifier
    val raw = view.textState()
    val links = raw is Spanned && raw.getSpans(0, raw.length, ClickableSpan::class.java).isNotEmpty()
    val linkModifier = if (links) Modifier.pointerInput(view) {
        detectTapGestures { pos -> clickableSpanAt(view, pos)?.onClick(view) }
    } else Modifier

    if (!marquee && !selectable) {
        Layout(content = {}, modifier = modifier.then(button).then(linkModifier).drawBehind { drawTextView(view) }) { _, c ->
            layout(c.maxWidth, c.maxHeight) {}
        }
        return
    }
    // Marquee and selectable text are Compose texts inside the text area
    val style = TextLayoutSupport.styleOf(view, textColorOf(view))
    val text = TextLayoutSupport.annotated(view, false)
    PlacedContent(
        modifier.then(button).drawBehind { drawTextView(view, drawText = false) },
        rects = { _, w, h ->
            IntRect(view.getCompoundPaddingLeft(), view.getExtendedPaddingTop(), max(view.getCompoundPaddingLeft(), w - view.getCompoundPaddingRight()), max(view.getExtendedPaddingTop(), h - view.getExtendedPaddingBottom()))
        },
    ) {
        if (marquee) {
            BasicText(text, modifier = Modifier.basicMarquee(), style = style, maxLines = 1, softWrap = false)
        } else {
            SelectionContainer {
                val p = TextLayoutSupport.paramsOf(view)
                BasicText(text, style = style, maxLines = p.maxLines, softWrap = p.softWrap, overflow = p.overflow)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// EditText

@Composable
internal fun EditTextNode(view: EditText, modifier: Modifier) {
    val current = view.textState().toString()
    var selection by remember(view) { mutableStateOf(TextRange(current.length)) }
    var composition by remember(view) { mutableStateOf<TextRange?>(null) }
    val focusRequester = remember(view) { FocusRequester() }

    val requestStart = view.selStart
    val requestEnd = view.selEnd
    LaunchedEffect(requestStart, requestEnd) {
        if (requestStart >= 0) selection = TextRange(requestStart, if (requestEnd >= 0) requestEnd else requestStart)
    }
    val selectAll = view.selectAllRequest.intValue
    LaunchedEffect(selectAll) {
        if (selectAll > 0) selection = TextRange(0, view.textState().length)
    }
    val focusRequest = view.focusRequest.intValue
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) runCatching { focusRequester.requestFocus() }
    }

    val inputType = view.getInputType()
    val cls = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    val multiLineFlag = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
    val singleLine = view.isSingleLine() || (inputType != InputType.TYPE_NULL && !multiLineFlag && cls != 0) || view.getMaxLines() == 1
    val password = view.isPasswordField() ||
        (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
        (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    val keyboardType = when (cls) {
        InputType.TYPE_CLASS_NUMBER -> if (password) KeyboardType.NumberPassword else KeyboardType.Number
        InputType.TYPE_CLASS_PHONE -> KeyboardType.Phone
        else -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_URI -> KeyboardType.Uri
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> KeyboardType.Email
            else -> if (password) KeyboardType.Password else KeyboardType.Text
        }
    }
    val imeOption = view.getImeOptions() and EditorInfo.IME_MASK_ACTION
    val imeAction = when (imeOption) {
        EditorInfo.IME_ACTION_SEARCH -> ImeAction.Search
        EditorInfo.IME_ACTION_GO -> ImeAction.Go
        EditorInfo.IME_ACTION_SEND -> ImeAction.Send
        EditorInfo.IME_ACTION_NEXT -> ImeAction.Next
        EditorInfo.IME_ACTION_DONE -> ImeAction.Done
        else -> if (singleLine) ImeAction.Done else ImeAction.Default
    }
    fun editorAction() {
        view.onEditorAction(if (imeOption == 0 || imeOption == EditorInfo.IME_ACTION_UNSPECIFIED) EditorInfo.IME_ACTION_DONE else imeOption)
    }

    val style = TextLayoutSupport.styleOf(view)
    val hint = view.getHint()
    val hintColor = Color(view.getHintTextColors().colorFor(view, ThemeBridge.textColorSecondary))
    val primary = Color(ThemeBridge.colorPrimary)

    PlacedContent(
        modifier.drawBehind { drawTextView(view, drawText = false) },
        rects = { _, w, h ->
            val top = view.getExtendedPaddingTop() + view.getVerticalOffset(false)
            val lh = view.textLayoutForDrawing(current.isEmpty() && hint != null)?.size?.height ?: (h - top)
            val left = view.getCompoundPaddingLeft()
            IntRect(left, top, max(left, w - view.getCompoundPaddingRight()), top + max(lh, 0).coerceAtMost(max(0, h - top)))
        },
    ) {
        BasicTextField(
            value = TextFieldValue(
                current,
                TextRange(selection.start.coerceIn(0, current.length), selection.end.coerceIn(0, current.length)),
                composition?.takeIf { it.max <= current.length },
            ),
            onValueChange = { new ->
                selection = new.selection
                composition = new.composition
                if (new.text != current) view.onUserTextChanged(new.text)
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { view.dispatchFocusChanged(it.isFocused) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && singleLine && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                        editorAction()
                        true
                    } else if (e.type == KeyEventType.KeyDown || e.type == KeyEventType.KeyUp) {
                        // extension key listeners (setOnKeyListener) see the keys first like on Android
                        view.mOnKeyListener?.let { l ->
                            val code = androidKeyCode(e.key)
                            if (code != android.view.KeyEvent.KEYCODE_UNKNOWN) {
                                val action = if (e.type == KeyEventType.KeyDown) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP
                                l.onKey(view, code, android.view.KeyEvent(action, code))
                            } else false
                        } ?: false
                    } else false
                },
            enabled = view.isEnabled(),
            textStyle = style,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else view.getMaxLines().coerceAtLeast(1),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { editorAction() }),
            cursorBrush = SolidColor(primary),
            decorationBox = { inner ->
                Box {
                    if (current.isEmpty() && hint != null) {
                        BasicText(hint.toString(), style = style.copy(color = hintColor), maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
    }
}

// ------------------------------------------------------------------------------------------------
// CheckBox, RadioButton, Switch (and subclasses)

@Composable
internal fun CompoundButtonNode(view: CompoundButton, modifier: Modifier) {
    if (view is ToggleButton) {
        TextViewNode(view, modifier)
        return
    }
    val checked = view.isChecked()
    val enabled = view.isEnabled()
    val isSwitch = view is Switch
    val primary = Color(view.getButtonTintList().colorFor(view, ThemeBridge.colorPrimary))
    val unchecked = Color(ThemeBridge.textColorSecondary)
    PlacedContent(
        modifier.drawBehind { drawTextView(view) },
        rects = { _, w, h ->
            if (isSwitch) {
                val sw = WidgetDefaults.switchWidth()
                val sh = WidgetDefaults.dp(32f)
                val x = w - view.getPaddingRight() - sw
                val y = view.getPaddingTop() + (h - view.getPaddingTop() - view.getPaddingBottom() - sh) / 2
                IntRect(x, y, x + sw, y + sh)
            } else {
                val s = WidgetDefaults.compoundButtonWidth()
                val y = view.getPaddingTop() + (h - view.getPaddingTop() - view.getPaddingBottom() - s) / 2
                IntRect(view.getPaddingLeft(), y, view.getPaddingLeft() + s, y + s)
            }
        },
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            when {
                isSwitch -> {
                    val sw = view as Switch
                    val thumb = sw.getThumbTintList()?.let { Color(it.colorFor(view, ThemeBridge.colorOnPrimary)) }
                    val track = sw.getTrackTintList()?.let { Color(it.colorFor(view, ThemeBridge.colorPrimary)) }
                    Switch(
                        checked = checked, onCheckedChange = null, enabled = enabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = track ?: primary, checkedThumbColor = thumb ?: Color(ThemeBridge.colorOnPrimary)),
                    )
                }
                view is RadioButton -> RadioButton(
                    selected = checked, onClick = null, enabled = enabled,
                    colors = RadioButtonDefaults.colors(selectedColor = primary, unselectedColor = unchecked),
                )
                else -> Checkbox(
                    checked = checked, onCheckedChange = null, enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = primary, uncheckedColor = unchecked),
                )
            }
        }
    }
}

/** CheckedTextView: the text with a radio button, check box or check mark at the end */
@Composable
internal fun CheckedTextNode(view: CheckedTextView, modifier: Modifier) {
    val type = view.checkMarkType
    if (type == CheckedTextView.CHECK_MARK_NONE) {
        TextViewNode(view, modifier)
        return
    }
    val checked = view.isChecked()
    val primary = Color(ThemeBridge.colorPrimary)
    val unchecked = Color(ThemeBridge.textColorSecondary)
    PlacedContent(
        modifier.drawBehind { drawTextView(view) },
        rects = { _, w, h ->
            val s = WidgetDefaults.compoundButtonWidth()
            val x = w - view.getPaddingRight() - s
            val y = view.getPaddingTop() + (h - view.getPaddingTop() - view.getPaddingBottom() - s) / 2
            IntRect(x, y, x + s, y + s)
        },
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            when (type) {
                CheckedTextView.CHECK_MARK_SINGLE -> RadioButton(
                    selected = checked, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = primary, unselectedColor = unchecked),
                )
                CheckedTextView.CHECK_MARK_MULTIPLE -> Checkbox(
                    checked = checked, onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = primary, uncheckedColor = unchecked),
                )
                else -> if (checked) BasicText("✓", style = TextLayoutSupport.styleOf(view, ThemeBridge.colorPrimary))
            }
        }
    }
}
