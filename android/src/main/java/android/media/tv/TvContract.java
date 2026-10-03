package android.media.tv;

// Java so upstream can static-import the nested constants
public final class TvContract {
    private TvContract() {}

    public static final String AUTHORITY = "android.media.tv";

    public static final class Channels {
        private Channels() {}

        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        public static final String COLUMN_DISPLAY_NAME = "display_name";
    }
}
