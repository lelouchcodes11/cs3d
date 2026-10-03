package android.content;

import com.lagradost.desktop.runtime.AndroidRuntime;

/** Backed by the system clipboard through the UI host. */
public class ClipboardManager extends android.text.ClipboardManager {
    public interface OnPrimaryClipChangedListener {
        void onPrimaryClipChanged();
    }

    public ClipboardManager() {
    }

    public void setPrimaryClip(ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(null);
        AndroidRuntime.INSTANCE.getHost().setClipboard(text == null ? "" : text.toString());
    }

    public void clearPrimaryClip() {
        AndroidRuntime.INSTANCE.getHost().setClipboard("");
    }

    public ClipData getPrimaryClip() {
        String text = AndroidRuntime.INSTANCE.getHost().getClipboard();
        if (text == null) return null;
        return ClipData.newPlainText("", text);
    }

    public ClipDescription getPrimaryClipDescription() {
        ClipData clip = getPrimaryClip();
        return clip == null ? null : clip.getDescription();
    }

    public boolean hasPrimaryClip() {
        return AndroidRuntime.INSTANCE.getHost().getClipboard() != null;
    }

    public void addPrimaryClipChangedListener(OnPrimaryClipChangedListener what) {
    }

    public void removePrimaryClipChangedListener(OnPrimaryClipChangedListener what) {
    }

    @Override
    @Deprecated
    public CharSequence getText() {
        return AndroidRuntime.INSTANCE.getHost().getClipboard();
    }

    @Override
    @Deprecated
    public void setText(CharSequence text) {
        AndroidRuntime.INSTANCE.getHost().setClipboard(text == null ? "" : text.toString());
    }

    @Override
    @Deprecated
    public boolean hasText() {
        return hasPrimaryClip();
    }
}
