package com.google.android.gms.cast;

public class MediaStatus {
    public static final int REPEAT_MODE_REPEAT_OFF = 0;
    public static final int REPEAT_MODE_REPEAT_ALL = 1;
    public static final int REPEAT_MODE_REPEAT_SINGLE = 2;
    public static final int REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE = 3;

    private long[] activeTrackIds = null;

    public long[] getActiveTrackIds() {
        return activeTrackIds;
    }
}
