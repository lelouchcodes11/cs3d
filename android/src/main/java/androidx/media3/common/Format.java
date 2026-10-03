package androidx.media3.common;

public class Format {
    public static final int NO_VALUE = -1;

    public final String id;
    public final String label;
    public final String language;
    public final int width;
    public final int height;
    public final String sampleMimeType;
    public final int channelCount;

    public Format() {
        this(null, null, null, NO_VALUE, NO_VALUE, null, NO_VALUE);
    }

    public Format(String id, String label, String language, int width, int height, String sampleMimeType, int channelCount) {
        this.id = id;
        this.label = label;
        this.language = language;
        this.width = width;
        this.height = height;
        this.sampleMimeType = sampleMimeType;
        this.channelCount = channelCount;
    }
}
