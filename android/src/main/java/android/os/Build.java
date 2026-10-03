package android.os;

public class Build {
    public static final String UNKNOWN = "unknown";
    public static final String ID = "UQ1A.240205.004";
    public static final String DISPLAY = "CloudStream Desktop";
    public static final String PRODUCT = "desktop";
    public static final String DEVICE = "desktop";
    public static final String BOARD = "desktop";
    public static final String MANUFACTURER = System.getProperty("os.name", "Desktop");
    public static final String BRAND = "desktop";
    public static final String MODEL = System.getProperty("os.name", "Desktop") + " " + System.getProperty("os.arch", "");
    public static final String BOOTLOADER = UNKNOWN;
    public static final String HARDWARE = System.getProperty("os.arch", UNKNOWN);
    public static final String[] SUPPORTED_ABIS = new String[]{"x86_64"};
    public static final String[] SUPPORTED_32_BIT_ABIS = new String[]{};
    public static final String[] SUPPORTED_64_BIT_ABIS = new String[]{"x86_64"};
    @Deprecated
    public static final String CPU_ABI = "x86_64";
    @Deprecated
    public static final String CPU_ABI2 = "";
    public static final String TYPE = "user";
    public static final String TAGS = "release-keys";
    public static final String FINGERPRINT = "desktop/desktop/desktop:14/UQ1A.240205.004/1:user/release-keys";
    public static final long TIME = 0;
    public static final String USER = "cloudstream";
    public static final String HOST = "desktop";

    public static String getSerial() {
        return UNKNOWN;
    }

    public static String getRadioVersion() {
        return null;
    }

    public static class VERSION {
        public static final String INCREMENTAL = "1";
        public static final String RELEASE = "14";
        public static final String RELEASE_OR_CODENAME = "14";
        public static final String BASE_OS = "";
        public static final String SECURITY_PATCH = "2024-02-05";
        @Deprecated
        public static final String SDK = "34";
        public static final int SDK_INT = 34;
        public static final int PREVIEW_SDK_INT = 0;
        public static final String CODENAME = "REL";
    }

    public static class VERSION_CODES {
        public static final int CUR_DEVELOPMENT = 10000;
        public static final int BASE = 1;
        public static final int BASE_1_1 = 2;
        public static final int CUPCAKE = 3;
        public static final int DONUT = 4;
        public static final int ECLAIR = 5;
        public static final int ECLAIR_0_1 = 6;
        public static final int ECLAIR_MR1 = 7;
        public static final int FROYO = 8;
        public static final int GINGERBREAD = 9;
        public static final int GINGERBREAD_MR1 = 10;
        public static final int HONEYCOMB = 11;
        public static final int HONEYCOMB_MR1 = 12;
        public static final int HONEYCOMB_MR2 = 13;
        public static final int ICE_CREAM_SANDWICH = 14;
        public static final int ICE_CREAM_SANDWICH_MR1 = 15;
        public static final int JELLY_BEAN = 16;
        public static final int JELLY_BEAN_MR1 = 17;
        public static final int JELLY_BEAN_MR2 = 18;
        public static final int KITKAT = 19;
        public static final int KITKAT_WATCH = 20;
        public static final int LOLLIPOP = 21;
        public static final int LOLLIPOP_MR1 = 22;
        public static final int M = 23;
        public static final int N = 24;
        public static final int N_MR1 = 25;
        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int S_V2 = 32;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
        public static final int VANILLA_ICE_CREAM = 35;
        public static final int BAKLAVA = 36;
    }
}
