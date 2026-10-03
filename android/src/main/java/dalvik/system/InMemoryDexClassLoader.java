package dalvik.system;

import com.lagradost.desktop.dex.DexCache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.ByteBuffer;

public final class InMemoryDexClassLoader extends BaseDexClassLoader {
    public InMemoryDexClassLoader(ByteBuffer[] dexBuffers, String librarySearchPath, ClassLoader parent) {
        super(convert(dexBuffers), parent);
    }

    public InMemoryDexClassLoader(ByteBuffer[] dexBuffers, ClassLoader parent) {
        this(dexBuffers, null, parent);
    }

    public InMemoryDexClassLoader(ByteBuffer dexBuffer, ClassLoader parent) {
        this(new ByteBuffer[]{dexBuffer}, parent);
    }

    private static URL[] convert(ByteBuffer[] buffers) {
        URL[] urls = new URL[buffers.length];
        for (int i = 0; i < buffers.length; i++) {
            ByteBuffer b = buffers[i].duplicate();
            byte[] data = new byte[b.remaining()];
            b.get(data);
            try {
                urls[i] = toUrl(DexCache.jarFor(data, "in-memory dex #" + i));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return urls;
    }
}
