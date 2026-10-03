package android.os;

import com.lagradost.desktop.runtime.AndroidRuntime;

import java.io.File;

public class Environment {
    public static final String DIRECTORY_MUSIC = "Music";
    public static final String DIRECTORY_PODCASTS = "Podcasts";
    public static final String DIRECTORY_RINGTONES = "Ringtones";
    public static final String DIRECTORY_ALARMS = "Alarms";
    public static final String DIRECTORY_NOTIFICATIONS = "Notifications";
    public static final String DIRECTORY_PICTURES = "Pictures";
    public static final String DIRECTORY_MOVIES = "Movies";
    public static final String DIRECTORY_DOWNLOADS = "Download";
    public static final String DIRECTORY_DCIM = "DCIM";
    public static final String DIRECTORY_DOCUMENTS = "Documents";

    public static final String MEDIA_UNKNOWN = "unknown";
    public static final String MEDIA_REMOVED = "removed";
    public static final String MEDIA_UNMOUNTED = "unmounted";
    public static final String MEDIA_MOUNTED = "mounted";
    public static final String MEDIA_MOUNTED_READ_ONLY = "mounted_ro";

    public static File getExternalStorageDirectory() {
        return AndroidRuntime.INSTANCE.getExternalStorageDir();
    }

    public static File getDataDirectory() {
        return AndroidRuntime.INSTANCE.getDataDir();
    }

    public static File getRootDirectory() {
        return new File(System.getProperty("user.home"));
    }

    public static File getDownloadCacheDirectory() {
        return AndroidRuntime.INSTANCE.cacheDir();
    }

    /**
     * Public directories map to the user's real desktop folders (Downloads, Videos, Music, ...),
     * which is where a desktop user expects downloaded media to end up.
     */
    public static File getExternalStoragePublicDirectory(String type) {
        File home = new File(System.getProperty("user.home"));
        if (type == null) return home;
        switch (type) {
            case DIRECTORY_DOWNLOADS:
                return new File(home, "Downloads");
            case DIRECTORY_MOVIES:
                File videos = new File(home, "Videos");
                return videos.exists() ? videos : new File(home, "Movies");
            case DIRECTORY_MUSIC:
                return new File(home, "Music");
            case DIRECTORY_PICTURES:
                return new File(home, "Pictures");
            case DIRECTORY_DOCUMENTS:
                return new File(home, "Documents");
            default:
                return new File(getExternalStorageDirectory(), type);
        }
    }

    public static String getExternalStorageState() {
        return MEDIA_MOUNTED;
    }

    public static String getExternalStorageState(File path) {
        return MEDIA_MOUNTED;
    }

    public static boolean isExternalStorageEmulated() {
        return true;
    }

    public static boolean isExternalStorageRemovable() {
        return false;
    }

    public static boolean isExternalStorageManager() {
        return true;
    }
}
