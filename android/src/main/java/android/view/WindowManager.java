package android.view;

public interface WindowManager extends ViewManager {
    Display getDefaultDisplay();

    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_DIM_BEHIND = 0x00000002;
        public static final int FLAG_NOT_FOCUSABLE = 0x00000008;
        public static final int FLAG_NOT_TOUCHABLE = 0x00000010;
        public static final int FLAG_NOT_TOUCH_MODAL = 0x00000020;
        public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
        public static final int FLAG_LAYOUT_IN_SCREEN = 0x00000100;
        public static final int FLAG_LAYOUT_NO_LIMITS = 0x00000200;
        public static final int FLAG_FULLSCREEN = 0x00000400;
        public static final int FLAG_FORCE_NOT_FULLSCREEN = 0x00000800;
        public static final int FLAG_SECURE = 0x00002000;
        public static final int FLAG_ALT_FOCUSABLE_IM = 0x00020000;
        public static final int FLAG_WATCH_OUTSIDE_TOUCH = 0x00040000;
        public static final int FLAG_SHOW_WHEN_LOCKED = 0x00080000;
        public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
        public static final int FLAG_TRANSLUCENT_NAVIGATION = 0x08000000;
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
        public static final int SOFT_INPUT_STATE_UNSPECIFIED = 0;
        public static final int SOFT_INPUT_STATE_HIDDEN = 2;
        public static final int SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3;
        public static final int SOFT_INPUT_STATE_VISIBLE = 4;
        public static final int SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5;
        public static final int SOFT_INPUT_ADJUST_UNSPECIFIED = 0x00;
        public static final int SOFT_INPUT_ADJUST_RESIZE = 0x10;
        public static final int SOFT_INPUT_ADJUST_PAN = 0x20;
        public static final int SOFT_INPUT_ADJUST_NOTHING = 0x30;
        public static final int TYPE_APPLICATION = 2;
        public static final int TYPE_APPLICATION_OVERLAY = 2038;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT = 0;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER = 2;
        public static final float BRIGHTNESS_OVERRIDE_NONE = -1.0f;

        public int x = 0;
        public int y = 0;
        public int type = TYPE_APPLICATION;
        public int flags = 0;
        public int gravity = 0;
        public float dimAmount = 0.6f;
        public float screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public float alpha = 1f;
        public int softInputMode = 0;
        public int windowAnimations = 0;
        public int format = 0;
        public int layoutInDisplayCutoutMode = 0;
        public float horizontalMargin = 0f;
        public float verticalMargin = 0f;
        public CharSequence title = null;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(int w, int h, int type, int flags, int format) {
            super(w, h);
            this.type = type;
            this.flags = flags;
            this.format = format;
        }

        public int copyFrom(LayoutParams o) {
            width = o.width;
            height = o.height;
            x = o.x;
            y = o.y;
            flags = o.flags;
            gravity = o.gravity;
            dimAmount = o.dimAmount;
            return 0;
        }

        public void setTitle(CharSequence title) {
            this.title = title;
        }

        public CharSequence getTitle() {
            return title;
        }
    }
}
