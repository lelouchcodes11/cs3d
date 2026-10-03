package android.text.style;

import android.text.TextPaint;

public abstract class CharacterStyle {
    public abstract void updateDrawState(TextPaint tp);

    public CharacterStyle getUnderlying() {
        return this;
    }

    public static CharacterStyle wrap(CharacterStyle cs) {
        return cs;
    }
}
