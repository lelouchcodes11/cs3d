package androidx.core.content;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import java.io.File;
import java.util.concurrent.Executor;

public class ContextCompat {
    public static final int RECEIVER_VISIBLE_TO_INSTANT_APPS = 0x1;
    public static final int RECEIVER_EXPORTED = 0x2;
    public static final int RECEIVER_NOT_EXPORTED = 0x4;

    protected ContextCompat() {
    }

    public static int getColor(Context context, int id) {
        return context.getResources().getColor(id, context.getTheme());
    }

    public static Drawable getDrawable(Context context, int id) {
        return context.getResources().getDrawable(id, context.getTheme());
    }

    public static ColorStateList getColorStateList(Context context, int id) {
        return context.getResources().getColorStateList(id, context.getTheme());
    }

    public static <T> T getSystemService(Context context, Class<T> serviceClass) {
        return context.getSystemService(serviceClass);
    }

    public static String getSystemServiceName(Context context, Class<?> serviceClass) {
        return context.getSystemServiceName(serviceClass);
    }

    public static int checkSelfPermission(Context context, String permission) {
        return PackageManager.PERMISSION_GRANTED;
    }

    public static void startForegroundService(Context context, Intent intent) {
        context.startService(intent);
    }

    public static boolean startActivities(Context context, Intent[] intents) {
        for (Intent i : intents) context.startActivity(i);
        return true;
    }

    public static Executor getMainExecutor(Context context) {
        Handler handler = new Handler(context.getMainLooper());
        return handler::post;
    }

    public static File getDataDir(Context context) {
        return context.getDataDir();
    }

    public static File getNoBackupFilesDir(Context context) {
        return context.getNoBackupFilesDir();
    }

    public static File getCodeCacheDir(Context context) {
        return context.getCodeCacheDir();
    }

    public static File[] getExternalFilesDirs(Context context, String type) {
        return context.getExternalFilesDirs(type);
    }

    public static File[] getExternalCacheDirs(Context context) {
        return context.getExternalCacheDirs();
    }

    public static Intent registerReceiver(Context context, BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return context.registerReceiver(receiver, filter);
    }

    public static String getString(Context context, int resId) {
        return context.getString(resId);
    }

    public static Context getAttributionContext(Context context) {
        return context;
    }
}
