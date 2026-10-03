package android.content;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.concurrent.Executor;

public abstract class Context {
    public static final int MODE_PRIVATE = 0x0000;
    @Deprecated
    public static final int MODE_WORLD_READABLE = 0x0001;
    @Deprecated
    public static final int MODE_WORLD_WRITEABLE = 0x0002;
    public static final int MODE_APPEND = 0x8000;
    public static final int MODE_MULTI_PROCESS = 0x0004;

    public static final int BIND_AUTO_CREATE = 0x0001;
    public static final int RECEIVER_EXPORTED = 0x2;
    public static final int RECEIVER_NOT_EXPORTED = 0x4;

    public static final String ACTIVITY_SERVICE = "activity";
    public static final String ALARM_SERVICE = "alarm";
    public static final String AUDIO_SERVICE = "audio";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String DOWNLOAD_SERVICE = "download";
    public static final String INPUT_METHOD_SERVICE = "input_method";
    public static final String KEYGUARD_SERVICE = "keyguard";
    public static final String LAYOUT_INFLATER_SERVICE = "layout_inflater";
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String POWER_SERVICE = "power";
    public static final String STORAGE_SERVICE = "storage";
    public static final String TELEPHONY_SERVICE = "phone";
    public static final String UI_MODE_SERVICE = "uimode";
    public static final String VIBRATOR_SERVICE = "vibrator";
    public static final String WIFI_SERVICE = "wifi";
    public static final String WINDOW_SERVICE = "window";
    public static final String NSD_SERVICE = "servicediscovery";
    public static final String APP_OPS_SERVICE = "appops";
    public static final String DISPLAY_SERVICE = "display";
    public static final String JOB_SCHEDULER_SERVICE = "jobscheduler";
    public static final String MEDIA_SESSION_SERVICE = "media_session";
    public static final String DEVICE_POLICY_SERVICE = "device_policy";
    public static final String USER_SERVICE = "user";

    public abstract AssetManager getAssets();

    public abstract Resources getResources();

    public abstract PackageManager getPackageManager();

    public abstract ContentResolver getContentResolver();

    public abstract Looper getMainLooper();

    public Executor getMainExecutor() {
        Handler h = new Handler(getMainLooper());
        return h::post;
    }

    public abstract Context getApplicationContext();

    public abstract void setTheme(int resid);

    public abstract Resources.Theme getTheme();

    public final TypedArray obtainStyledAttributes(int[] attrs) {
        return getTheme().obtainStyledAttributes(attrs);
    }

    public final TypedArray obtainStyledAttributes(int resid, int[] attrs) {
        return getTheme().obtainStyledAttributes(resid, attrs);
    }

    public final TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs) {
        return getTheme().obtainStyledAttributes(set, attrs, 0, 0);
    }

    public final TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
        return getTheme().obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes);
    }

    public abstract ClassLoader getClassLoader();

    public abstract String getPackageName();

    public String getOpPackageName() {
        return getPackageName();
    }

    public String getAttributionTag() {
        return null;
    }

    public abstract ApplicationInfo getApplicationInfo();

    public abstract String getPackageResourcePath();

    public abstract String getPackageCodePath();

    public abstract SharedPreferences getSharedPreferences(String name, int mode);

    public abstract boolean deleteSharedPreferences(String name);

    public abstract FileInputStream openFileInput(String name) throws FileNotFoundException;

    public abstract FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException;

    public abstract boolean deleteFile(String name);

    public abstract File getFileStreamPath(String name);

    public abstract File getDataDir();

    public abstract File getFilesDir();

    public abstract File getNoBackupFilesDir();

    public abstract File getExternalFilesDir(String type);

    public abstract File[] getExternalFilesDirs(String type);

    public abstract File getObbDir();

    public abstract File getCacheDir();

    public abstract File getCodeCacheDir();

    public abstract File getExternalCacheDir();

    public abstract File[] getExternalCacheDirs();

    public abstract File[] getExternalMediaDirs();

    public abstract String[] fileList();

    public abstract File getDir(String name, int mode);

    public abstract File getDatabasePath(String name);

    public abstract String[] databaseList();

    public abstract boolean deleteDatabase(String name);

    public abstract void startActivity(Intent intent);

    public abstract void startActivity(Intent intent, Bundle options);

    public abstract void sendBroadcast(Intent intent);

    public abstract Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter);

    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return registerReceiver(receiver, filter);
    }

    public abstract void unregisterReceiver(BroadcastReceiver receiver);

    public abstract ComponentName startService(Intent service);

    public ComponentName startForegroundService(Intent service) {
        return startService(service);
    }

    public abstract boolean stopService(Intent service);

    public abstract Object getSystemService(String name);

    @SuppressWarnings("unchecked")
    public final <T> T getSystemService(Class<T> serviceClass) {
        String name = getSystemServiceName(serviceClass);
        return name == null ? null : (T) getSystemService(name);
    }

    public abstract String getSystemServiceName(Class<?> serviceClass);

    public abstract int checkPermission(String permission, int pid, int uid);

    public abstract int checkCallingOrSelfPermission(String permission);

    public abstract int checkSelfPermission(String permission);

    public abstract Context createConfigurationContext(Configuration overrideConfiguration);

    public abstract Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException;

    public boolean isRestricted() {
        return false;
    }

    public boolean isDeviceProtectedStorage() {
        return false;
    }

    public final CharSequence getText(int resId) {
        return getResources().getText(resId);
    }

    public final String getString(int resId) {
        return getResources().getString(resId);
    }

    public final String getString(int resId, Object... formatArgs) {
        return getResources().getString(resId, formatArgs);
    }

    public final int getColor(int id) {
        return getResources().getColor(id, getTheme());
    }

    public final Drawable getDrawable(int id) {
        return getResources().getDrawable(id, getTheme());
    }

    public final ColorStateList getColorStateList(int id) {
        return getResources().getColorStateList(id, getTheme());
    }
}
