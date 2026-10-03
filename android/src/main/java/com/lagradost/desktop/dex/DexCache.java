package com.lagradost.desktop.dex;

import android.util.Log;

import com.lagradost.desktop.runtime.AndroidRuntime;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Converts dex containers to jars once and caches the result by content hash, so extensions load
 * quickly after the first run.
 */
public final class DexCache {
    private static final String TAG = "DexCache";
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private DexCache() {
    }

    public static File cacheDir() {
        File dir = new File(AndroidRuntime.INSTANCE.getDataDir(), "code_cache/dex-jvm");
        dir.mkdirs();
        return dir;
    }

    /** true if [file] is a zip that already contains JVM classes and no dex */
    static boolean isJvmJar(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            boolean hasClass = false;
            for (java.util.Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                String name = e.nextElement().getName();
                if (name.matches("classes\\d*\\.dex")) return false;
                if (name.endsWith(".class")) hasClass = true;
            }
            return hasClass;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean containsDex(File file) {
        if (file.getName().endsWith(".dex")) return true;
        try (ZipFile zip = new ZipFile(file)) {
            for (java.util.Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                if (e.nextElement().getName().matches("classes\\d*\\.dex")) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /**
     * @return a jar with JVM classes for the dex (or dex container) [file], converting it if needed.
     * Jars without dex are returned as is.
     */
    public static File jarFor(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        if (!file.getName().endsWith(".dex") && isJvmJar(file)) {
            // Already JVM bytecode: use a content addressed copy so the original is never locked
            String hash;
            try {
                hash = sha256(data);
            } catch (Exception e) {
                throw new IOException(e);
            }
            File copy = new File(cacheDir(), hash + ".jar");
            if (!copy.isFile() || copy.length() != data.length) {
                File tmp = new File(copy.getPath() + ".tmp");
                Files.write(tmp.toPath(), data);
                Files.move(tmp.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return copy;
        }
        return jarFor(data, file.getName());
    }

    public static File jarFor(byte[] data, String displayName) throws IOException {
        String hash;
        try {
            hash = sha256(data);
        } catch (Exception e) {
            throw new IOException(e);
        }
        String base = hash + "-v" + DexConverter.VERSION + (DexConverter.FORCE_AGGRESSIVE ? "-agg" : "");
        File out = new File(cacheDir(), base + ".jar");
        Object lock = LOCKS.computeIfAbsent(hash, k -> new Object());
        synchronized (lock) {
            if (out.isFile() && out.length() > 0) return out;
            long start = System.currentTimeMillis();
            DexConverter.Result result = DexConverter.convert(data, out, resourceEntries(data));
            Log.i(TAG, "Converted " + displayName + ": " + result.classCount + " classes in "
                    + (System.currentTimeMillis() - start) + " ms");
            for (String compacted : result.compactedMethods) {
                Log.i(TAG, displayName + ": compacted oversized method " + compacted);
            }
            for (String failed : result.failedMethods) {
                Log.e(TAG, displayName + ": could not translate " + failed);
            }
            for (String error : result.errors) {
                Log.e(TAG, displayName + ": " + error);
            }
            File report = new File(cacheDir(), base + ".log");
            if (!result.isClean() || !result.compactedMethods.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String f : result.compactedMethods) sb.append("COMPACTED ").append(f).append('\n');
                for (String f : result.failedMethods) sb.append("FAILED ").append(f).append('\n');
                for (String f : result.errors) sb.append("ERROR ").append(f).append('\n');
                Files.writeString(report.toPath(), sb.toString());
            }
            return out;
        }
    }

    /** All non dex entries of a zip container (resources visible through the class loader) */
    static java.util.Map<String, byte[]> resourceEntries(byte[] data) {
        java.util.Map<String, byte[]> out = new java.util.LinkedHashMap<>();
        if (data.length < 4 || data[0] != 0x50 || data[1] != 0x4b) return out; // not a zip
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory() || e.getName().matches("classes\\d*\\.dex")) continue;
                out.put(e.getName(), zin.readAllBytes());
            }
        } catch (IOException ex) {
            Log.w(TAG, "Could not read resources of container: " + ex);
        }
        return out;
    }

    /** Reads a zip entry fully, or returns null */
    static byte[] readEntry(File zip, String name) {
        try (ZipFile z = new ZipFile(zip)) {
            ZipEntry e = z.getEntry(name);
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
