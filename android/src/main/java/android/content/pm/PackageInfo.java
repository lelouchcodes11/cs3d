package android.content.pm;

public class PackageInfo {
    public String packageName;
    public String[] splitNames;
    @Deprecated
    public int versionCode;
    public int versionCodeMajor;
    public String versionName;
    public int baseRevisionCode;
    public String sharedUserId;
    public ApplicationInfo applicationInfo;
    public long firstInstallTime;
    public long lastUpdateTime;
    public String[] requestedPermissions;

    public PackageInfo() {
    }

    public long getLongVersionCode() {
        return (((long) versionCodeMajor) << 32) | (versionCode & 0xffffffffL);
    }

    public void setLongVersionCode(long longVersionCode) {
        versionCodeMajor = (int) (longVersionCode >> 32);
        versionCode = (int) longVersionCode;
    }

    @Override
    public String toString() {
        return "PackageInfo{" + packageName + " " + versionName + "}";
    }
}
