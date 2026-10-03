package android;

public final class Manifest {
    private Manifest() {
    }

    public static final class permission {
        private permission() {
        }

        public static final String INTERNET = "android.permission.INTERNET";
        public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
        public static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
        public static final String MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE";
        public static final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
        public static final String FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE";
        public static final String REQUEST_INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES";
        public static final String READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";
        public static final String READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO";
        public static final String READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES";
        public static final String ACCESS_NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE";
        public static final String WAKE_LOCK = "android.permission.WAKE_LOCK";
    }
}
