package androidx.core.content;

import android.content.Context;
import android.net.Uri;

import java.io.File;

/** Desktop: files are shared as file:// uris */
public class FileProvider {
    public static Uri getUriForFile(Context context, String authority, File file) {
        return Uri.fromFile(file);
    }

    public static Uri getUriForFile(Context context, String authority, File file, String displayName) {
        return Uri.fromFile(file);
    }
}
