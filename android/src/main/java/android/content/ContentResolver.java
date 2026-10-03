package android.content;

import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Desktop ContentResolver. Only file:// uris (and content uris registered with
 * {@link #registerContentFile}) are supported since desktop has no content providers.
 */
public class ContentResolver {
    public static final String SCHEME_CONTENT = "content";
    public static final String SCHEME_ANDROID_RESOURCE = "android.resource";
    public static final String SCHEME_FILE = "file";

    private static final java.util.Map<String, File> sContentFiles = new java.util.concurrent.ConcurrentHashMap<>();

    public ContentResolver(Context context) {
    }

    /** Map a content:// uri to a local file, used by the FileProvider equivalent */
    public static void registerContentFile(Uri uri, File file) {
        sContentFiles.put(uri.toString(), file);
    }

    public static File resolveToFile(Uri uri) {
        if (uri == null) return null;
        String scheme = uri.getScheme();
        if (scheme == null || SCHEME_FILE.equals(scheme)) {
            String path = uri.getPath();
            if (path != null && path.length() >= 3 && path.charAt(0) == '/' && Character.isLetter(path.charAt(1)) && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            return path == null ? null : new File(path);
        }
        return sContentFiles.get(uri.toString());
    }

    public final InputStream openInputStream(Uri uri) throws FileNotFoundException {
        File f = resolveToFile(uri);
        if (f == null) {
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                try {
                    return new java.net.URL(uri.toString()).openStream();
                } catch (java.io.IOException e) {
                    throw new FileNotFoundException(e.getMessage());
                }
            }
            throw new FileNotFoundException("No content provider: " + uri);
        }
        return new FileInputStream(f);
    }

    public final OutputStream openOutputStream(Uri uri) throws FileNotFoundException {
        return openOutputStream(uri, "w");
    }

    public final OutputStream openOutputStream(Uri uri, String mode) throws FileNotFoundException {
        File f = resolveToFile(uri);
        if (f == null) throw new FileNotFoundException("No content provider: " + uri);
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        return new FileOutputStream(f, mode != null && mode.contains("a"));
    }

    // desktop: there are no content providers, so every query has no result
    public final android.database.Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    public final int delete(Uri url, String where, String[] selectionArgs) {
        File f = resolveToFile(url);
        return f != null && f.delete() ? 1 : 0;
    }

    public final String getType(Uri url) {
        File f = resolveToFile(url);
        String name = f != null ? f.getName() : url.getLastPathSegment();
        if (name == null) return null;
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        switch (ext) {
            case "mp4": return "video/mp4";
            case "mkv": return "video/x-matroska";
            case "webm": return "video/webm";
            case "m3u8": return "application/x-mpegURL";
            case "ts": return "video/mp2t";
            case "srt": return "application/x-subrip";
            case "vtt": return "text/vtt";
            case "ass":
            case "ssa": return "text/x-ssa";
            case "json": return "application/json";
            case "txt": return "text/plain";
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            default: return null;
        }
    }

    public void takePersistableUriPermission(Uri uri, int modeFlags) {
    }

    public void releasePersistableUriPermission(Uri uri, int modeFlags) {
    }

    public void notifyChange(Uri uri, Object observer) {
    }
}
