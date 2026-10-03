package android.text;

@Deprecated
public abstract class ClipboardManager {
    public abstract CharSequence getText();

    public abstract void setText(CharSequence text);

    public abstract boolean hasText();
}
