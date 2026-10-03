package dalvik.system;

import android.util.Log;

import com.lagradost.desktop.dex.DexCache;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Desktop implementation of the ART dex class loaders. Every dex container on the dex path is
 * converted to JVM bytecode (cached), the original files stay on the path so their Java resources
 * (e.g. manifest.json) can be read. Delegation is parent first, like on Android.
 */
public class BaseDexClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final String dexPath;
    private final String librarySearchPath;

    public BaseDexClassLoader(String dexPath, File optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(buildUrls(dexPath), parent);
        this.dexPath = dexPath;
        this.librarySearchPath = librarySearchPath;
    }

    /** Used by InMemoryDexClassLoader */
    BaseDexClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.dexPath = null;
        this.librarySearchPath = null;
    }

    static URL[] buildUrls(String dexPath) {
        List<URL> urls = new ArrayList<>();
        if (dexPath == null) return new URL[0];
        for (String part : dexPath.split(File.pathSeparator.equals(";") ? "[;:](?![\\\\/])" : ":")) {
            if (part.isEmpty()) continue;
            File file = new File(part);
            if (!file.exists()) {
                Log.w("BaseDexClassLoader", "Dex path entry does not exist: " + part);
                continue;
            }
            try {
                // The converted jar also carries the container's resources (manifest.json, ...), so
                // the original file is never held open and can be replaced (plugin updates on Windows)
                urls.add(DexCache.jarFor(file).toURI().toURL());
            } catch (IOException e) {
                Log.e("BaseDexClassLoader", "Could not load " + part + ": " + Log.getStackTraceString(e));
            }
        }
        return urls.toArray(new URL[0]);
    }

    static URL toUrl(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String findLibrary(String name) {
        if (librarySearchPath == null) return null;
        String mapped = System.mapLibraryName(name);
        for (String dir : librarySearchPath.split(File.pathSeparator)) {
            File f = new File(dir, mapped);
            if (f.isFile()) return f.getAbsolutePath();
        }
        return null;
    }

    public void addDexPath(String dexPath) {
        for (URL url : buildUrls(dexPath)) addURL(url);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[DexPathList[" + dexPath + "]]";
    }
}
