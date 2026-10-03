package android.os.ext;

public class SdkExtensions {
    private SdkExtensions() {
    }

    public static int getExtensionVersion(int extension) {
        return 13;
    }

    public static java.util.Map<Integer, Integer> getAllExtensionVersions() {
        return java.util.Collections.emptyMap();
    }
}
