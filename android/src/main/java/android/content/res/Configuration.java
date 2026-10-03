package android.content.res;

import android.os.LocaleList;

import java.util.Locale;

public final class Configuration implements Comparable<Configuration> {
    public static final int ORIENTATION_UNDEFINED = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;

    public static final int UI_MODE_TYPE_MASK = 0x0f;
    public static final int UI_MODE_TYPE_UNDEFINED = 0x00;
    public static final int UI_MODE_TYPE_NORMAL = 0x01;
    public static final int UI_MODE_TYPE_DESK = 0x02;
    public static final int UI_MODE_TYPE_CAR = 0x03;
    public static final int UI_MODE_TYPE_TELEVISION = 0x04;
    public static final int UI_MODE_TYPE_APPLIANCE = 0x05;
    public static final int UI_MODE_TYPE_WATCH = 0x06;
    public static final int UI_MODE_NIGHT_MASK = 0x30;
    public static final int UI_MODE_NIGHT_UNDEFINED = 0x00;
    public static final int UI_MODE_NIGHT_NO = 0x10;
    public static final int UI_MODE_NIGHT_YES = 0x20;

    public static final int SCREENLAYOUT_SIZE_MASK = 0x0f;
    public static final int SCREENLAYOUT_SIZE_XLARGE = 0x04;
    public static final int SCREENLAYOUT_SIZE_LARGE = 0x03;
    public static final int SCREENLAYOUT_SIZE_NORMAL = 0x02;

    public static final int KEYBOARD_NOKEYS = 1;
    public static final int KEYBOARD_QWERTY = 2;
    public static final int TOUCHSCREEN_NOTOUCH = 1;
    public static final int TOUCHSCREEN_FINGER = 3;
    public static final int NAVIGATION_NONAV = 1;
    public static final int NAVIGATION_DPAD = 2;

    private static final Configuration sCurrent = new Configuration();

    static {
        sCurrent.locale = Locale.getDefault();
        sCurrent.orientation = ORIENTATION_LANDSCAPE;
        sCurrent.uiMode = UI_MODE_TYPE_NORMAL | UI_MODE_NIGHT_YES;
        sCurrent.screenLayout = SCREENLAYOUT_SIZE_XLARGE;
        sCurrent.screenWidthDp = 1280;
        sCurrent.screenHeightDp = 720;
        sCurrent.smallestScreenWidthDp = 720;
        sCurrent.densityDpi = 240;
        sCurrent.fontScale = 1f;
        sCurrent.keyboard = KEYBOARD_QWERTY;
        sCurrent.touchscreen = TOUCHSCREEN_FINGER;
        sCurrent.navigation = NAVIGATION_NONAV;
    }

    /** The live configuration of the desktop "device". */
    public static Configuration current() {
        return sCurrent;
    }

    public float fontScale = 1f;
    public int mcc;
    public int mnc;
    @Deprecated
    public Locale locale;
    public int screenLayout;
    public int touchscreen;
    public int keyboard;
    public int keyboardHidden;
    public int hardKeyboardHidden;
    public int navigation;
    public int navigationHidden;
    public int orientation;
    public int uiMode;
    public int screenWidthDp;
    public int screenHeightDp;
    public int smallestScreenWidthDp;
    public int densityDpi;

    public Configuration() {
        locale = Locale.getDefault();
    }

    public Configuration(Configuration o) {
        setTo(o);
    }

    public void setTo(Configuration o) {
        fontScale = o.fontScale;
        mcc = o.mcc;
        mnc = o.mnc;
        locale = o.locale;
        screenLayout = o.screenLayout;
        touchscreen = o.touchscreen;
        keyboard = o.keyboard;
        keyboardHidden = o.keyboardHidden;
        hardKeyboardHidden = o.hardKeyboardHidden;
        navigation = o.navigation;
        navigationHidden = o.navigationHidden;
        orientation = o.orientation;
        uiMode = o.uiMode;
        screenWidthDp = o.screenWidthDp;
        screenHeightDp = o.screenHeightDp;
        smallestScreenWidthDp = o.smallestScreenWidthDp;
        densityDpi = o.densityDpi;
    }

    public void setToDefaults() {
        setTo(sCurrent);
    }

    public LocaleList getLocales() {
        return new LocaleList(locale != null ? locale : Locale.getDefault());
    }

    public void setLocales(LocaleList locales) {
        locale = locales == null || locales.isEmpty() ? Locale.getDefault() : locales.get(0);
    }

    public void setLocale(Locale loc) {
        locale = loc;
    }

    public int getLayoutDirection() {
        return 0;
    }

    public void setLayoutDirection(Locale loc) {
    }

    public boolean isNightModeActive() {
        return (uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES;
    }

    public boolean isLayoutSizeAtLeast(int size) {
        return (screenLayout & SCREENLAYOUT_SIZE_MASK) >= size;
    }

    public int updateFrom(Configuration delta) {
        setTo(delta);
        return 0;
    }

    @Override
    public int compareTo(Configuration that) {
        return Integer.compare(hashCode(), that.hashCode());
    }

    @Override
    public String toString() {
        return "{" + fontScale + " " + locale + " " + screenWidthDp + "x" + screenHeightDp + "dp orientation=" + orientation + " uiMode=" + uiMode + "}";
    }
}
