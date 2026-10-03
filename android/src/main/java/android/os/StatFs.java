package android.os;

import java.io.File;

/** File system statistics backed by java.io.File space queries */
public class StatFs {
    private File mFile;

    public StatFs(String path) {
        restat(path);
    }

    public void restat(String path) {
        mFile = new File(path);
    }

    public int getBlockSize() {
        return 4096;
    }

    public long getBlockSizeLong() {
        return 4096;
    }

    public int getBlockCount() {
        return (int) getBlockCountLong();
    }

    public long getBlockCountLong() {
        return mFile.getTotalSpace() / 4096;
    }

    public int getFreeBlocks() {
        return (int) getFreeBlocksLong();
    }

    public long getFreeBlocksLong() {
        return mFile.getFreeSpace() / 4096;
    }

    public long getFreeBytes() {
        return mFile.getFreeSpace();
    }

    public int getAvailableBlocks() {
        return (int) getAvailableBlocksLong();
    }

    public long getAvailableBlocksLong() {
        return mFile.getUsableSpace() / 4096;
    }

    public long getAvailableBytes() {
        return mFile.getUsableSpace();
    }

    public long getTotalBytes() {
        return mFile.getTotalSpace();
    }
}
