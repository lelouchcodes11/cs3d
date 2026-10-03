package android.text.format;

import android.content.Context;
import java.util.Locale;

public class Formatter {
    private Formatter() {}

    public static String formatFileSize(Context context, long sizeBytes) {
        return formatBytes(sizeBytes, false);
    }

    public static String formatShortFileSize(Context context, long sizeBytes) {
        return formatBytes(sizeBytes, true);
    }

    private static String formatBytes(long bytes, boolean isShort) {
        if (bytes <= 0) return "0 B";
        double unit = 1024.0;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        double value = bytes / Math.pow(unit, exp);
        return String.format(Locale.US, "%.1f %cB", value, pre);
    }
}
