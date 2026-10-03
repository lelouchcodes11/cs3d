package android.view;

import android.os.Parcel;
import android.os.Parcelable;

public class KeyEvent extends InputEvent implements Parcelable {
    public static final int KEYCODE_UNKNOWN = 0;
    public static final int KEYCODE_SOFT_LEFT = 1;
    public static final int KEYCODE_SOFT_RIGHT = 2;
    public static final int KEYCODE_HOME = 3;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_CALL = 5;
    public static final int KEYCODE_ENDCALL = 6;
    public static final int KEYCODE_0 = 7;
    public static final int KEYCODE_1 = 8;
    public static final int KEYCODE_2 = 9;
    public static final int KEYCODE_3 = 10;
    public static final int KEYCODE_4 = 11;
    public static final int KEYCODE_5 = 12;
    public static final int KEYCODE_6 = 13;
    public static final int KEYCODE_7 = 14;
    public static final int KEYCODE_8 = 15;
    public static final int KEYCODE_9 = 16;
    public static final int KEYCODE_STAR = 17;
    public static final int KEYCODE_POUND = 18;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_VOLUME_UP = 24;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int KEYCODE_POWER = 26;
    public static final int KEYCODE_CAMERA = 27;
    public static final int KEYCODE_CLEAR = 28;
    public static final int KEYCODE_A = 29;
    public static final int KEYCODE_B = 30;
    public static final int KEYCODE_C = 31;
    public static final int KEYCODE_D = 32;
    public static final int KEYCODE_E = 33;
    public static final int KEYCODE_F = 34;
    public static final int KEYCODE_G = 35;
    public static final int KEYCODE_H = 36;
    public static final int KEYCODE_I = 37;
    public static final int KEYCODE_J = 38;
    public static final int KEYCODE_K = 39;
    public static final int KEYCODE_L = 40;
    public static final int KEYCODE_M = 41;
    public static final int KEYCODE_N = 42;
    public static final int KEYCODE_O = 43;
    public static final int KEYCODE_P = 44;
    public static final int KEYCODE_Q = 45;
    public static final int KEYCODE_R = 46;
    public static final int KEYCODE_S = 47;
    public static final int KEYCODE_T = 48;
    public static final int KEYCODE_U = 49;
    public static final int KEYCODE_V = 50;
    public static final int KEYCODE_W = 51;
    public static final int KEYCODE_X = 52;
    public static final int KEYCODE_Y = 53;
    public static final int KEYCODE_Z = 54;
    public static final int KEYCODE_COMMA = 55;
    public static final int KEYCODE_PERIOD = 56;
    public static final int KEYCODE_ALT_LEFT = 57;
    public static final int KEYCODE_ALT_RIGHT = 58;
    public static final int KEYCODE_SHIFT_LEFT = 59;
    public static final int KEYCODE_SHIFT_RIGHT = 60;
    public static final int KEYCODE_TAB = 61;
    public static final int KEYCODE_SPACE = 62;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_DEL = 67;
    public static final int KEYCODE_GRAVE = 68;
    public static final int KEYCODE_MINUS = 69;
    public static final int KEYCODE_EQUALS = 70;
    public static final int KEYCODE_LEFT_BRACKET = 71;
    public static final int KEYCODE_RIGHT_BRACKET = 72;
    public static final int KEYCODE_BACKSLASH = 73;
    public static final int KEYCODE_SEMICOLON = 74;
    public static final int KEYCODE_APOSTROPHE = 75;
    public static final int KEYCODE_SLASH = 76;
    public static final int KEYCODE_AT = 77;
    public static final int KEYCODE_MENU = 82;
    public static final int KEYCODE_SEARCH = 84;
    public static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    public static final int KEYCODE_MEDIA_STOP = 86;
    public static final int KEYCODE_MEDIA_NEXT = 87;
    public static final int KEYCODE_MEDIA_PREVIOUS = 88;
    public static final int KEYCODE_MEDIA_REWIND = 89;
    public static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    public static final int KEYCODE_PAGE_UP = 92;
    public static final int KEYCODE_PAGE_DOWN = 93;
    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_BUTTON_B = 97;
    public static final int KEYCODE_ESCAPE = 111;
    public static final int KEYCODE_FORWARD_DEL = 112;
    public static final int KEYCODE_CTRL_LEFT = 113;
    public static final int KEYCODE_CTRL_RIGHT = 114;
    public static final int KEYCODE_MOVE_HOME = 122;
    public static final int KEYCODE_MOVE_END = 123;
    public static final int KEYCODE_INSERT = 124;
    public static final int KEYCODE_MEDIA_PLAY = 126;
    public static final int KEYCODE_MEDIA_PAUSE = 127;
    public static final int KEYCODE_F1 = 131;
    public static final int KEYCODE_F12 = 142;
    public static final int KEYCODE_NUMPAD_ENTER = 160;
    public static final int KEYCODE_CHANNEL_UP = 166;
    public static final int KEYCODE_CHANNEL_DOWN = 167;
    public static final int KEYCODE_TV = 170;
    public static final int KEYCODE_INFO = 165;
    public static final int KEYCODE_GUIDE = 172;
    public static final int KEYCODE_MEDIA_AUDIO_TRACK = 222;
    public static final int KEYCODE_PROG_RED = 183;
    public static final int KEYCODE_PROG_GREEN = 184;
    public static final int KEYCODE_PROG_YELLOW = 185;
    public static final int KEYCODE_PROG_BLUE = 186;
    public static final int KEYCODE_FORWARD = 125;
    public static final int KEYCODE_MEDIA_SKIP_FORWARD = 272;
    public static final int KEYCODE_MEDIA_SKIP_BACKWARD = 273;
    public static final int KEYCODE_BUTTON_L1 = 102;
    public static final int KEYCODE_BUTTON_R1 = 103;
    public static final int KEYCODE_BUTTON_START = 108;
    public static final int KEYCODE_NUMPAD_0 = 144;
    public static final int KEYCODE_NUMPAD_1 = 145;
    public static final int KEYCODE_NUMPAD_2 = 146;
    public static final int KEYCODE_NUMPAD_3 = 147;
    public static final int KEYCODE_NUMPAD_4 = 148;
    public static final int KEYCODE_NUMPAD_5 = 149;
    public static final int KEYCODE_NUMPAD_7 = 151;
    public static final int KEYCODE_NUMPAD_8 = 152;
    public static final int KEYCODE_NUMPAD_9 = 153;
    public static final int KEYCODE_VOLUME_MUTE = 164;
    public static final int KEYCODE_SETTINGS = 176;
    public static final int KEYCODE_DPAD_UP_LEFT = 268;
    public static final int KEYCODE_DPAD_DOWN_LEFT = 269;
    public static final int KEYCODE_DPAD_UP_RIGHT = 270;
    public static final int KEYCODE_DPAD_DOWN_RIGHT = 271;

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    @Deprecated
    public static final int ACTION_MULTIPLE = 2;

    public static final int META_ALT_ON = 0x02;
    public static final int META_SHIFT_ON = 0x1;
    public static final int META_CTRL_ON = 0x1000;
    public static final int META_META_ON = 0x10000;

    public static final int FLAG_SOFT_KEYBOARD = 0x2;
    public static final int FLAG_KEEP_TOUCH_MODE = 0x4;
    public static final int FLAG_FROM_SYSTEM = 0x8;

    private final long mDownTime;
    private final long mEventTime;
    private final int mAction;
    private final int mKeyCode;
    private final int mRepeatCount;
    private final int mMetaState;
    private final int mDeviceId;
    private final int mScanCode;
    private final int mFlags;

    public KeyEvent(int action, int code) {
        this(0, 0, action, code, 0, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat) {
        this(downTime, eventTime, action, code, repeat, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState) {
        this(downTime, eventTime, action, code, repeat, metaState, -1, 0, 0);
    }

    public KeyEvent(long downTime, long eventTime, int action, int code, int repeat, int metaState, int deviceId, int scancode, int flags) {
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = code;
        mRepeatCount = repeat;
        mMetaState = metaState;
        mDeviceId = deviceId;
        mScanCode = scancode;
        mFlags = flags;
    }

    public KeyEvent(KeyEvent origEvent) {
        this(origEvent.mDownTime, origEvent.mEventTime, origEvent.mAction, origEvent.mKeyCode, origEvent.mRepeatCount,
                origEvent.mMetaState, origEvent.mDeviceId, origEvent.mScanCode, origEvent.mFlags);
    }

    public final int getAction() { return mAction; }
    public final int getKeyCode() { return mKeyCode; }
    public final int getRepeatCount() { return mRepeatCount; }
    public final int getMetaState() { return mMetaState; }
    public final int getModifiers() { return mMetaState; }
    public final int getFlags() { return mFlags; }
    public final int getScanCode() { return mScanCode; }
    public final long getDownTime() { return mDownTime; }
    @Override public final long getEventTime() { return mEventTime; }
    @Override public final int getDeviceId() { return mDeviceId; }
    public final boolean isShiftPressed() { return (mMetaState & META_SHIFT_ON) != 0; }
    public final boolean isAltPressed() { return (mMetaState & META_ALT_ON) != 0; }
    public final boolean isCtrlPressed() { return (mMetaState & META_CTRL_ON) != 0; }
    public final boolean isMetaPressed() { return (mMetaState & META_META_ON) != 0; }
    public final boolean isLongPress() { return false; }
    public final boolean isCanceled() { return false; }
    public final boolean isSystem() { return false; }

    public int getUnicodeChar() {
        if (mKeyCode >= KEYCODE_A && mKeyCode <= KEYCODE_Z) return (isShiftPressed() ? 'A' : 'a') + (mKeyCode - KEYCODE_A);
        if (mKeyCode >= KEYCODE_0 && mKeyCode <= KEYCODE_9) return '0' + (mKeyCode - KEYCODE_0);
        if (mKeyCode == KEYCODE_SPACE) return ' ';
        return 0;
    }

    public static boolean isConfirmKey(int keyCode) {
        return keyCode == KEYCODE_DPAD_CENTER || keyCode == KEYCODE_ENTER || keyCode == KEYCODE_SPACE || keyCode == KEYCODE_NUMPAD_ENTER;
    }

    public static boolean isMediaSessionKey(int keyCode) {
        return keyCode == KEYCODE_MEDIA_PLAY || keyCode == KEYCODE_MEDIA_PAUSE || keyCode == KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KEYCODE_MEDIA_STOP || keyCode == KEYCODE_MEDIA_NEXT || keyCode == KEYCODE_MEDIA_PREVIOUS;
    }

    public static String keyCodeToString(int keyCode) {
        return "KEYCODE_" + keyCode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
    }

    @Override
    public String toString() {
        return "KeyEvent { action=" + mAction + ", keyCode=" + mKeyCode + ", repeatCount=" + mRepeatCount + " }";
    }
}
