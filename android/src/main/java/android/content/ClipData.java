package android.content;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class ClipData implements Parcelable {
    public static class Item {
        final CharSequence mText;
        final String mHtmlText;
        final Intent mIntent;
        final Uri mUri;

        public Item(CharSequence text) {
            this(text, null, null, null);
        }

        public Item(CharSequence text, String htmlText) {
            this(text, htmlText, null, null);
        }

        public Item(Intent intent) {
            this(null, null, intent, null);
        }

        public Item(Uri uri) {
            this(null, null, null, uri);
        }

        public Item(CharSequence text, Intent intent, Uri uri) {
            this(text, null, intent, uri);
        }

        public Item(CharSequence text, String htmlText, Intent intent, Uri uri) {
            mText = text;
            mHtmlText = htmlText;
            mIntent = intent;
            mUri = uri;
        }

        public CharSequence getText() { return mText; }
        public String getHtmlText() { return mHtmlText; }
        public Intent getIntent() { return mIntent; }
        public Uri getUri() { return mUri; }

        public CharSequence coerceToText(Context context) {
            if (mText != null) return mText;
            if (mUri != null) return mUri.toString();
            if (mIntent != null) return mIntent.toUri(0);
            return "";
        }

        @Override
        public String toString() {
            return "ClipData.Item { " + (mText != null ? "T:" + mText : mUri != null ? "U:" + mUri : "") + " }";
        }
    }

    final ClipDescription mClipDescription;
    final ArrayList<Item> mItems = new ArrayList<>();

    public ClipData(CharSequence label, String[] mimeTypes, Item item) {
        mClipDescription = new ClipDescription(label, mimeTypes);
        mItems.add(item);
    }

    public ClipData(ClipDescription description, Item item) {
        mClipDescription = description;
        mItems.add(item);
    }

    public ClipData(ClipData other) {
        mClipDescription = other.mClipDescription;
        mItems.addAll(other.mItems);
    }

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        return new ClipData(label, new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, new Item(text));
    }

    public static ClipData newHtmlText(CharSequence label, CharSequence text, String htmlText) {
        return new ClipData(label, new String[]{ClipDescription.MIMETYPE_TEXT_HTML}, new Item(text, htmlText));
    }

    public static ClipData newIntent(CharSequence label, Intent intent) {
        return new ClipData(label, new String[]{ClipDescription.MIMETYPE_TEXT_INTENT}, new Item(intent));
    }

    public static ClipData newRawUri(CharSequence label, Uri uri) {
        return new ClipData(label, new String[]{ClipDescription.MIMETYPE_TEXT_URILIST}, new Item(uri));
    }

    public static ClipData newUri(ContentResolver resolver, CharSequence label, Uri uri) {
        return newRawUri(label, uri);
    }

    public ClipDescription getDescription() {
        return mClipDescription;
    }

    public void addItem(Item item) {
        mItems.add(item);
    }

    public int getItemCount() {
        return mItems.size();
    }

    public Item getItemAt(int index) {
        return mItems.get(index);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }
}
