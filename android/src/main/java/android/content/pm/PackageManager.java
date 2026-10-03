package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import com.lagradost.desktop.runtime.AndroidRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Desktop package manager. Only the application itself is "installed"; queries for other packages
 * (external players, torrent clients...) are answered by the UI host, which may map well known
 * Android package names to installed desktop programs.
 */
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public static final int GET_ACTIVITIES = 0x00000001;
    public static final int GET_META_DATA = 0x00000080;
    public static final int GET_SIGNATURES = 0x00000040;
    public static final int MATCH_DEFAULT_ONLY = 0x00010000;
    public static final int MATCH_ALL = 0x00020000;
    public static final int COMPONENT_ENABLED_STATE_DEFAULT = 0;
    public static final int COMPONENT_ENABLED_STATE_ENABLED = 1;
    public static final int COMPONENT_ENABLED_STATE_DISABLED = 2;
    public static final int DONT_KILL_APP = 0x00000001;
    public static final String FEATURE_LEANBACK = "android.software.leanback";
    public static final String FEATURE_TELEVISION = "android.hardware.type.television";
    public static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    public static final String FEATURE_PICTURE_IN_PICTURE = "android.software.picture_in_picture";

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() {
        }

        public NameNotFoundException(String name) {
            super(name);
        }
    }

    public static final class PackageInfoFlags {
        final long value;

        private PackageInfoFlags(long value) {
            this.value = value;
        }

        public static PackageInfoFlags of(long value) {
            return new PackageInfoFlags(value);
        }

        public long getValue() {
            return value;
        }
    }

    /** Package names considered installed, besides our own. Filled by the application. */
    public static final List<String> sInstalledPackages = Collections.synchronizedList(new ArrayList<>());

    /** Application version, set by the application at startup */
    public static volatile String sVersionName = "1.0.0";
    public static volatile long sVersionCode = 1;

    public PackageManager() {
    }

    private boolean isInstalled(String packageName) {
        return AndroidRuntime.PACKAGE_NAME.equals(packageName) || sInstalledPackages.contains(packageName);
    }

    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        if (!isInstalled(packageName)) throw new NameNotFoundException(packageName);
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        if (AndroidRuntime.PACKAGE_NAME.equals(packageName)) {
            info.versionName = sVersionName;
            info.setLongVersionCode(sVersionCode);
            info.applicationInfo = getApplicationInfo(packageName, flags);
        } else {
            info.versionName = "1.0";
            info.versionCode = 1;
        }
        return info;
    }

    public PackageInfo getPackageInfo(String packageName, PackageInfoFlags flags) throws NameNotFoundException {
        return getPackageInfo(packageName, (int) flags.getValue());
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        if (!isInstalled(packageName)) throw new NameNotFoundException(packageName);
        if (AndroidRuntime.PACKAGE_NAME.equals(packageName)) {
            android.content.Context ctx = AndroidRuntime.INSTANCE.getApplicationContext();
            if (ctx != null) return ctx.getApplicationInfo();
        }
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        return info;
    }

    public Intent getLaunchIntentForPackage(String packageName) {
        if (!isInstalled(packageName)) return null;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(packageName);
        intent.setComponent(new ComponentName(packageName, packageName + ".MainActivity"));
        return intent;
    }

    public Intent getLeanbackLaunchIntentForPackage(String packageName) {
        return null;
    }

    public List<PackageInfo> getInstalledPackages(int flags) {
        List<PackageInfo> list = new ArrayList<>();
        try {
            list.add(getPackageInfo(AndroidRuntime.PACKAGE_NAME, flags));
            for (String p : sInstalledPackages) list.add(getPackageInfo(p, flags));
        } catch (NameNotFoundException ignored) {
        }
        return list;
    }

    public List<ApplicationInfo> getInstalledApplications(int flags) {
        List<ApplicationInfo> list = new ArrayList<>();
        for (PackageInfo p : getInstalledPackages(flags)) {
            if (p.applicationInfo != null) list.add(p.applicationInfo);
        }
        return list;
    }

    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        return new ArrayList<>();
    }

    public ResolveInfo resolveActivity(Intent intent, int flags) {
        return null;
    }

    public boolean hasSystemFeature(String name) {
        return false;
    }

    public boolean hasSystemFeature(String name, int version) {
        return false;
    }

    public int checkPermission(String permName, String packageName) {
        return PERMISSION_GRANTED;
    }

    public void setComponentEnabledSetting(ComponentName componentName, int newState, int flags) {
    }

    public int getComponentEnabledSetting(ComponentName componentName) {
        return COMPONENT_ENABLED_STATE_DEFAULT;
    }

    public CharSequence getApplicationLabel(ApplicationInfo info) {
        return info.loadLabel(this);
    }

    public String getInstallerPackageName(String packageName) {
        return null;
    }

    public boolean canRequestPackageInstalls() {
        return false;
    }

    public PackageInstaller getPackageInstaller() {
        return new PackageInstaller();
    }
}
