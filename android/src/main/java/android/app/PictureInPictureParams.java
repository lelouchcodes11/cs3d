package android.app;

/** Desktop has no picture-in-picture. The builder exists so upstream call sites compile. */
public final class PictureInPictureParams {
    private PictureInPictureParams() {
    }

    public static final class Builder {
        public Builder() {
        }

        public PictureInPictureParams build() {
            return new PictureInPictureParams();
        }
    }
}
