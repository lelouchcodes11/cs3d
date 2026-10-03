package android.provider;

import android.content.ContentResolver;

import java.util.UUID;

public final class Settings {
    public static final String ACTION_SETTINGS = "android.settings.SETTINGS";
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS";
    public static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION = "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION";
    public static final String ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES";
    public static final String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS = "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";
    public static final String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
    public static final String ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS";
    public static final String ACTION_WIFI_SETTINGS = "android.settings.WIFI_SETTINGS";
    public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";

    private static final String ANDROID_ID;

    static {
        String id;
        try {
            // Stable per machine and user, like ANDROID_ID is stable per device and app signing key
            String seed = java.lang.System.getProperty("user.name", "") + "@" + java.net.InetAddress.getLocalHost().getHostName();
            id = UUID.nameUUIDFromBytes(seed.getBytes()).toString().replace("-", "").substring(0, 16);
        } catch (Exception e) {
            id = "0123456789abcdef";
        }
        ANDROID_ID = id;
    }

    public static final class Secure {
        public static final String ANDROID_ID = "android_id";
        public static final String DEFAULT_INPUT_METHOD = "default_input_method";

        public static String getString(ContentResolver resolver, String name) {
            if (ANDROID_ID.equals(name)) return Settings.ANDROID_ID;
            return null;
        }

        public static int getInt(ContentResolver cr, String name, int def) {
            return def;
        }

        public static int getInt(ContentResolver cr, String name) throws SettingNotFoundException {
            throw new SettingNotFoundException(name);
        }
    }

    public static final class System {
        public static final String SCREEN_BRIGHTNESS = "screen_brightness";
        public static final String SCREEN_BRIGHTNESS_MODE = "screen_brightness_mode";
        public static final int SCREEN_BRIGHTNESS_MODE_MANUAL = 0;
        public static final int SCREEN_BRIGHTNESS_MODE_AUTOMATIC = 1;
        public static final String ACCELEROMETER_ROTATION = "accelerometer_rotation";
        public static final String FONT_SCALE = "font_scale";

        public static String getString(ContentResolver resolver, String name) {
            return null;
        }

        public static int getInt(ContentResolver cr, String name, int def) {
            return def;
        }

        public static int getInt(ContentResolver cr, String name) throws SettingNotFoundException {
            throw new SettingNotFoundException(name);
        }

        public static float getFloat(ContentResolver cr, String name, float def) {
            return def;
        }

        public static boolean putInt(ContentResolver cr, String name, int value) {
            return false;
        }

        public static boolean canWrite(android.content.Context context) {
            return false;
        }
    }

    public static final class Global {
        public static final String DEVICE_NAME = "device_name";
        public static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";

        public static String getString(ContentResolver resolver, String name) {
            if (DEVICE_NAME.equals(name)) return java.lang.System.getProperty("os.name");
            return null;
        }

        public static int getInt(ContentResolver cr, String name, int def) {
            return def;
        }

        public static float getFloat(ContentResolver cr, String name, float def) {
            return def;
        }
    }

    public static class SettingNotFoundException extends Exception {
        public SettingNotFoundException(String msg) {
            super(msg);
        }
    }

    public static boolean canDrawOverlays(android.content.Context context) {
        return true;
    }
}
