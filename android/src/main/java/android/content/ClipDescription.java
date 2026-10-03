package android.content;

public class ClipDescription {
    public static final String MIMETYPE_TEXT_PLAIN = "text/plain";
    public static final String MIMETYPE_TEXT_HTML = "text/html";
    public static final String MIMETYPE_TEXT_URILIST = "text/uri-list";
    public static final String MIMETYPE_TEXT_INTENT = "text/vnd.android.intent";

    final CharSequence mLabel;
    final String[] mMimeTypes;

    public ClipDescription(CharSequence label, String[] mimeTypes) {
        mLabel = label;
        mMimeTypes = mimeTypes;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public int getMimeTypeCount() {
        return mMimeTypes.length;
    }

    public String getMimeType(int index) {
        return mMimeTypes[index];
    }

    public boolean hasMimeType(String mimeType) {
        for (String m : mMimeTypes) {
            if (m.equals(mimeType) || mimeType.equals("*/*")) return true;
        }
        return false;
    }
}
