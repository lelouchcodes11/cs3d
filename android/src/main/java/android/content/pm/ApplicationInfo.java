package android.content.pm;

public class ApplicationInfo extends PackageItemInfo {
    public static final int FLAG_DEBUGGABLE = 1 << 1;
    public static final int FLAG_SYSTEM = 1 << 0;
    public static final int FLAG_LARGE_HEAP = 1 << 20;

    public String taskAffinity;
    public String permission;
    public String processName;
    public String className;
    public int descriptionRes;
    public int theme;
    public int flags;
    public String sourceDir;
    public String publicSourceDir;
    public String[] splitSourceDirs;
    public String dataDir;
    public String deviceProtectedDataDir;
    public String nativeLibraryDir;
    public int uid = 10000;
    public int minSdkVersion = 23;
    public int targetSdkVersion = 36;
    public boolean enabled = true;
    public int category = -1;

    public ApplicationInfo() {
    }

    public ApplicationInfo(ApplicationInfo orig) {
        super(orig);
        taskAffinity = orig.taskAffinity;
        permission = orig.permission;
        processName = orig.processName;
        className = orig.className;
        theme = orig.theme;
        flags = orig.flags;
        sourceDir = orig.sourceDir;
        publicSourceDir = orig.publicSourceDir;
        dataDir = orig.dataDir;
        nativeLibraryDir = orig.nativeLibraryDir;
        uid = orig.uid;
        minSdkVersion = orig.minSdkVersion;
        targetSdkVersion = orig.targetSdkVersion;
        enabled = orig.enabled;
    }

    @Override
    public String toString() {
        return "ApplicationInfo{" + packageName + "}";
    }
}
