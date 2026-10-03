package androidx.tvprovider.media.tv;

import android.database.Cursor;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

// Java so upstream can static-import fromCursor. Desktop: programs are built but never published.
public final class WatchNextProgram {
    public static final String[] PROJECTION = {"_id", "internal_provider_id", "title"};

    private final Map<String, Object> values;

    private WatchNextProgram(Map<String, Object> values) {
        this.values = values;
    }

    public long getId() {
        Object id = values.get("_id");
        return id instanceof Long ? (Long) id : -1L;
    }

    public String getInternalProviderId() {
        return (String) values.get("internal_provider_id");
    }

    public String getTitle() {
        return (String) values.get("title");
    }

    public static WatchNextProgram fromCursor(Cursor cursor) {
        Builder builder = new Builder();
        int id = cursor.getColumnIndex("_id");
        if (id >= 0) builder.setId(cursor.getLong(id));
        int provider = cursor.getColumnIndex("internal_provider_id");
        if (provider >= 0) builder.setInternalProviderId(cursor.getString(provider));
        int title = cursor.getColumnIndex("title");
        if (title >= 0) builder.setTitle(cursor.getString(title));
        return builder.build();
    }

    public static final class Builder {
        private final Map<String, Object> values = new HashMap<>();

        public Builder() {}

        public Builder(WatchNextProgram other) {
            values.putAll(other.values);
        }

        private Builder put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        public Builder setId(long id) { return put("_id", id); }
        public Builder setTitle(String title) { return put("title", title); }
        public Builder setEpisodeTitle(String episodeTitle) { return put("episode_title", episodeTitle); }
        public Builder setType(int type) { return put("type", type); }
        public Builder setWatchNextType(int watchNextType) { return put("watch_next_type", watchNextType); }
        public Builder setPosterArtUri(Uri posterArtUri) { return put("poster_art_uri", posterArtUri); }
        public Builder setIntentUri(Uri intentUri) { return put("intent_uri", intentUri); }
        public Builder setInternalProviderId(String internalProviderId) { return put("internal_provider_id", internalProviderId); }
        public Builder setLastEngagementTimeUtcMillis(long time) { return put("last_engagement_time_utc_millis", time); }
        public Builder setDurationMillis(int durationMillis) { return put("duration_millis", durationMillis); }
        public Builder setLastPlaybackPositionMillis(int position) { return put("last_playback_position_millis", position); }
        public Builder setEpisodeNumber(int episodeNumber) { return put("episode_display_number", episodeNumber); }
        public Builder setSeasonNumber(int seasonNumber) { return put("season_display_number", seasonNumber); }
        public Builder setDescription(String description) { return put("short_description", description); }

        public WatchNextProgram build() {
            return new WatchNextProgram(new HashMap<>(values));
        }
    }
}
