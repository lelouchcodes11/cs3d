package android.content.res;

import com.lagradost.desktop.runtime.res.ResourceSupport;
import com.lagradost.desktop.runtime.res.ResourceTable;

import java.io.IOException;
import java.io.InputStream;

public final class AssetManager implements AutoCloseable {
    public static final int ACCESS_UNKNOWN = 0;
    public static final int ACCESS_RANDOM = 1;
    public static final int ACCESS_STREAMING = 2;
    public static final int ACCESS_BUFFER = 3;

    private ResourceTable mTable;

    /** Hidden public constructor on Android, used by apps through reflection */
    public AssetManager() {
    }

    public AssetManager(ResourceTable table) {
        mTable = table;
    }

    public ResourceTable getTable() {
        return mTable;
    }

    /**
     * Hidden Android API (called through reflection by plugin loaders). Loads the resources of an
     * apk/cs3 file. Returns a non-zero cookie on success.
     */
    public int addAssetPath(String path) {
        try {
            mTable = ResourceSupport.loadApkResources(path);
            return mTable == null ? 0 : 1;
        } catch (Exception e) {
            android.util.Log.e("AssetManager", "Failed to load resources from " + path + ": " + android.util.Log.getStackTraceString(e));
            return 0;
        }
    }

    public InputStream open(String fileName) throws IOException {
        return open(fileName, ACCESS_STREAMING);
    }

    public InputStream open(String fileName, int accessMode) throws IOException {
        if (mTable == null) throw new java.io.FileNotFoundException(fileName);
        return mTable.openAsset(fileName);
    }

    public String[] list(String path) throws IOException {
        if (mTable == null) return new String[0];
        return mTable.listAssets(path);
    }

    public String[] getLocales() {
        return new String[]{java.util.Locale.getDefault().toLanguageTag()};
    }

    @Override
    public void close() {
    }
}
