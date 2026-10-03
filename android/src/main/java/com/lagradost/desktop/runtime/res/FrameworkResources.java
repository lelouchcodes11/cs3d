package com.lagradost.desktop.runtime.res;

import android.content.res.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The small part of the Android framework resources (android.R, package id 0x01) that apps and
 * extensions commonly reference. Values use the real framework ids so inlined constants resolve.
 */
public final class FrameworkResources implements ResourceTable {

    // android.R.color
    public static final int COLOR_BLACK = 0x0106000c;
    public static final int COLOR_WHITE = 0x0106000b;
    public static final int COLOR_TRANSPARENT = 0x0106000d;
    public static final int COLOR_DARKER_GRAY = 0x01060000;
    public static final int COLOR_HOLO_BLUE_LIGHT = 0x01060012;
    public static final int COLOR_HOLO_RED_LIGHT = 0x01060016;
    public static final int COLOR_HOLO_GREEN_LIGHT = 0x01060014;

    /** android.R.color (framework public.xml ids and values) */
    public static final int[][] ANDROID_COLORS = {
        {0x01060000, 0xffaaaaaa}, // darker_gray
        {0x01060001, 0xffffffff}, // primary_text_dark
        {0x01060002, 0xffffffff}, // primary_text_dark_nodisable
        {0x01060003, 0xff000000}, // primary_text_light
        {0x01060004, 0xff000000}, // primary_text_light_nodisable
        {0x01060005, 0xffbebebe}, // secondary_text_dark
        {0x01060006, 0xffbebebe}, // secondary_text_dark_nodisable
        {0x01060007, 0xff323232}, // secondary_text_light
        {0x01060008, 0xff323232}, // secondary_text_light_nodisable
        {0x01060009, 0xff808080}, // tab_indicator_text
        {0x0106000a, 0xff000000}, // widget_edittext_dark
        {0x0106000b, 0xffffffff}, // white
        {0x0106000c, 0xff000000}, // black
        {0x0106000d, 0x00000000}, // transparent
        {0x0106000e, 0xff000000}, // background_dark
        {0x0106000f, 0xffffffff}, // background_light
        {0x01060010, 0xff808080}, // tertiary_text_dark
        {0x01060011, 0xff808080}, // tertiary_text_light
        {0x01060012, 0xff33b5e5}, // holo_blue_light
        {0x01060013, 0xff0099cc}, // holo_blue_dark
        {0x01060014, 0xff99cc00}, // holo_green_light
        {0x01060015, 0xff669900}, // holo_green_dark
        {0x01060016, 0xffff4444}, // holo_red_light
        {0x01060017, 0xffcc0000}, // holo_red_dark
        {0x01060018, 0xffffbb33}, // holo_orange_light
        {0x01060019, 0xffff8800}, // holo_orange_dark
        {0x0106001a, 0xffaa66cc}, // holo_purple
        {0x0106001b, 0xff00ddff}, // holo_blue_bright
    };
    public static final String[] ANDROID_COLOR_NAMES = {
        "darker_gray",
        "primary_text_dark",
        "primary_text_dark_nodisable",
        "primary_text_light",
        "primary_text_light_nodisable",
        "secondary_text_dark",
        "secondary_text_dark_nodisable",
        "secondary_text_light",
        "secondary_text_light_nodisable",
        "tab_indicator_text",
        "widget_edittext_dark",
        "white",
        "black",
        "transparent",
        "background_dark",
        "background_light",
        "tertiary_text_dark",
        "tertiary_text_light",
        "holo_blue_light",
        "holo_blue_dark",
        "holo_green_light",
        "holo_green_dark",
        "holo_red_light",
        "holo_red_dark",
        "holo_orange_light",
        "holo_orange_dark",
        "holo_purple",
        "holo_blue_bright",
    };

    // android.R.layout (values of the framework's public.xml; extensions have them inlined)
    public static final int LAYOUT_ACTIVITY_LIST_ITEM = 0x01090000;
    public static final int LAYOUT_SIMPLE_LIST_ITEM_1 = 0x01090003;
    public static final int LAYOUT_SIMPLE_LIST_ITEM_2 = 0x01090004;
    public static final int LAYOUT_SIMPLE_LIST_ITEM_CHECKED = 0x01090005;
    public static final int LAYOUT_SIMPLE_SPINNER_ITEM = 0x01090008;
    public static final int LAYOUT_SIMPLE_SPINNER_DROPDOWN_ITEM = 0x01090009;
    public static final int LAYOUT_SIMPLE_DROPDOWN_ITEM_1LINE = 0x0109000a;
    public static final int LAYOUT_SIMPLE_LIST_ITEM_SINGLE_CHOICE = 0x0109000f;
    public static final int LAYOUT_SIMPLE_LIST_ITEM_MULTIPLE_CHOICE = 0x01090010;
    public static final int LAYOUT_SELECT_DIALOG_ITEM = 0x01090011;
    public static final int LAYOUT_SELECT_DIALOG_SINGLECHOICE = 0x01090012;
    public static final int LAYOUT_SELECT_DIALOG_MULTICHOICE = 0x01090013;
    public static final int LAYOUT_SIMPLE_SELECTABLE_LIST_ITEM = 0x01090015;
    public static final int LAYOUT_SIMPLE_LIST_ITEM_ACTIVATED_1 = 0x01090016;
    public static final int LAYOUT_SIMPLE_LIST_ITEM_ACTIVATED_2 = 0x01090017;

    // android.R.id
    public static final int ID_CONTENT = 0x01020002;
    public static final int ID_TEXT1 = 0x01020014;
    public static final int ID_TEXT2 = 0x01020015;
    public static final int ID_TITLE = 0x01020016;
    public static final int ID_ICON = 0x01020006;
    public static final int ID_LIST = 0x0102000a;
    public static final int ID_BUTTON1 = 0x01020019;
    public static final int ID_BUTTON2 = 0x0102001a;
    public static final int ID_BUTTON3 = 0x0102001b;
    public static final int ID_MESSAGE = 0x0102000b;
    public static final int ID_SUMMARY = 0x01020010;
    public static final int ID_EMPTY = 0x01020004;
    public static final int ID_HOME = 0x0102002c;
    public static final int ID_CHECKBOX = 0x01020001;
    public static final int ID_PROGRESS = 0x0102000d;
    public static final int ID_WIDGET_FRAME = 0x01020018;
    public static final int ID_LIST_CONTAINER = 0x0102003f;

    // android.R.string
    public static final int STRING_OK = 0x0104000a;
    public static final int STRING_CANCEL = 0x01040000;
    public static final int STRING_YES = 0x01040013;
    public static final int STRING_NO = 0x01040009;
    public static final int STRING_COPY = 0x01040001;
    public static final int STRING_PASTE = 0x0104000b;

    // android.R.drawable
    public static final int DRAWABLE_IC_DIALOG_ALERT = 0x01080027;
    public static final int DRAWABLE_IC_MENU_DELETE = 0x0108003c;
    public static final int DRAWABLE_IC_MEDIA_PLAY = 0x01080023;
    public static final int DRAWABLE_IC_MEDIA_PAUSE = 0x01080022;
    public static final int DRAWABLE_IC_DELETE = 0x01080021;

    // android.R.attr (subset used for theme lookups)
    public static final int ATTR_TEXT_COLOR_PRIMARY = 0x01010036;
    public static final int ATTR_TEXT_COLOR_SECONDARY = 0x01010038;
    public static final int ATTR_COLOR_BACKGROUND = 0x01010031;
    public static final int ATTR_COLOR_PRIMARY = 0x01010433;
    public static final int ATTR_COLOR_ACCENT = 0x01010435;
    public static final int ATTR_COLOR_PRIMARY_DARK = 0x01010434;
    public static final int ATTR_SELECTABLE_ITEM_BACKGROUND = 0x0101030e;
    public static final int ATTR_SELECTABLE_ITEM_BACKGROUND_BORDERLESS = 0x0101045c;
    public static final int ATTR_LIST_PREFERRED_ITEM_HEIGHT = 0x0101004d;
    public static final int ATTR_ACTION_BAR_SIZE = 0x010102eb;
    public static final int ATTR_LIST_PREFERRED_ITEM_PADDING_LEFT = 0x010103bb;
    public static final int ATTR_LIST_PREFERRED_ITEM_PADDING_RIGHT = 0x010103bc;
    public static final int ATTR_LIST_PREFERRED_ITEM_PADDING_START = 0x010103bd;
    public static final int ATTR_LIST_PREFERRED_ITEM_PADDING_END = 0x010103be;
    public static final int ATTR_EDIT_TEXT_BACKGROUND = 0x01010352;
    public static final int ATTR_COLUMN_WIDTH = 0x01010117;

    // Drawables the theme attributes above point to (ids not used by the real framework)
    public static final int DRAWABLE_SELECTABLE_ITEM_BACKGROUND = 0x0108ff01;
    public static final int DRAWABLE_SELECTABLE_ITEM_BACKGROUND_BORDERLESS = 0x0108ff02;

    // created after the static tables above, the constructor reads them
    public static final FrameworkResources INSTANCE = new FrameworkResources();

    private final Map<Integer, ResValue> values = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private final Map<String, Integer> ids = new HashMap<>();

    private void put(int id, String type, String name, ResValue v) {
        values.put(id, v);
        names.put(id, "android:" + type + "/" + name);
        ids.put(type + "/" + name, id);
    }

    private FrameworkResources() {
        put(COLOR_BLACK, "color", "black", ResValue.color(0xff000000));
        put(COLOR_WHITE, "color", "white", ResValue.color(0xffffffff));
        put(COLOR_TRANSPARENT, "color", "transparent", ResValue.color(0x00000000));
        put(COLOR_DARKER_GRAY, "color", "darker_gray", ResValue.color(0xffaaaaaa));
        put(COLOR_HOLO_BLUE_LIGHT, "color", "holo_blue_light", ResValue.color(0xff33b5e5));
        put(COLOR_HOLO_RED_LIGHT, "color", "holo_red_light", ResValue.color(0xffff4444));
        put(COLOR_HOLO_GREEN_LIGHT, "color", "holo_green_light", ResValue.color(0xff99cc00));
        for (int i = 0; i < ANDROID_COLORS.length; i++) {
            if (!values.containsKey(ANDROID_COLORS[i][0])) put(ANDROID_COLORS[i][0], "color", ANDROID_COLOR_NAMES[i], ResValue.color(ANDROID_COLORS[i][1]));
        }
        put(STRING_OK, "string", "ok", ResValue.string("OK"));
        put(STRING_CANCEL, "string", "cancel", ResValue.string("Cancel"));
        put(STRING_YES, "string", "yes", ResValue.string("Yes"));
        put(STRING_NO, "string", "no", ResValue.string("No"));
        put(STRING_COPY, "string", "copy", ResValue.string("Copy"));
        put(STRING_PASTE, "string", "paste", ResValue.string("Paste"));
        put(LAYOUT_SIMPLE_LIST_ITEM_1, "layout", "simple_list_item_1", ResValue.file("@android:layout/simple_list_item_1"));
        put(LAYOUT_SIMPLE_LIST_ITEM_2, "layout", "simple_list_item_2", ResValue.file("@android:layout/simple_list_item_2"));
        put(LAYOUT_SIMPLE_SPINNER_ITEM, "layout", "simple_spinner_item", ResValue.file("@android:layout/simple_spinner_item"));
        put(LAYOUT_SIMPLE_SPINNER_DROPDOWN_ITEM, "layout", "simple_spinner_dropdown_item", ResValue.file("@android:layout/simple_spinner_dropdown_item"));
        put(LAYOUT_SIMPLE_LIST_ITEM_CHECKED, "layout", "simple_list_item_checked", ResValue.file("@android:layout/simple_list_item_checked"));
        put(LAYOUT_SIMPLE_LIST_ITEM_SINGLE_CHOICE, "layout", "simple_list_item_single_choice", ResValue.file("@android:layout/simple_list_item_single_choice"));
        put(LAYOUT_SIMPLE_LIST_ITEM_MULTIPLE_CHOICE, "layout", "simple_list_item_multiple_choice", ResValue.file("@android:layout/simple_list_item_multiple_choice"));
        put(LAYOUT_SELECT_DIALOG_ITEM, "layout", "select_dialog_item", ResValue.file("@android:layout/select_dialog_item"));
        for (Object[] idDef : new Object[][]{
                {ID_CONTENT, "content"}, {ID_TEXT1, "text1"}, {ID_TEXT2, "text2"}, {ID_TITLE, "title"},
                {ID_ICON, "icon"}, {ID_LIST, "list"}, {ID_BUTTON1, "button1"}, {ID_BUTTON2, "button2"},
                {ID_BUTTON3, "button3"}, {ID_MESSAGE, "message"}, {ID_SUMMARY, "summary"}, {ID_EMPTY, "empty"},
                {ID_HOME, "home"}, {ID_CHECKBOX, "checkbox"}, {ID_PROGRESS, "progress"},
                {ID_WIDGET_FRAME, "widget_frame"}, {ID_LIST_CONTAINER, "list_container"}}) {
            put((Integer) idDef[0], "id", (String) idDef[1], new ResValue(ResValue.Kind.ID, idDef[0]));
        }
        put(DRAWABLE_IC_DIALOG_ALERT, "drawable", "ic_dialog_alert", ResValue.file("@android:drawable/ic_dialog_alert"));
        put(DRAWABLE_IC_MENU_DELETE, "drawable", "ic_menu_delete", ResValue.file("@android:drawable/ic_menu_delete"));
        put(DRAWABLE_IC_MEDIA_PLAY, "drawable", "ic_media_play", ResValue.file("@android:drawable/ic_media_play"));
        put(DRAWABLE_IC_MEDIA_PAUSE, "drawable", "ic_media_pause", ResValue.file("@android:drawable/ic_media_pause"));
        put(DRAWABLE_IC_DELETE, "drawable", "ic_delete", ResValue.file("@android:drawable/ic_delete"));
        put(DRAWABLE_SELECTABLE_ITEM_BACKGROUND, "drawable", "item_background_material", ResValue.file("@android:drawable/item_background_material"));
        put(DRAWABLE_SELECTABLE_ITEM_BACKGROUND_BORDERLESS, "drawable", "item_background_borderless_material", ResValue.file("@android:drawable/item_background_borderless_material"));
    }

    private static final java.util.Map<String, Integer> ATTR_IDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, String> ATTR_NAMES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ATTR = new java.util.concurrent.atomic.AtomicInteger(0x0101f000);

    static {
        String[][] known = {
            {"textColorPrimary", "01010036"}, {"textColorSecondary", "01010038"}, {"colorBackground", "01010031"},
            {"colorPrimary", "01010433"}, {"colorAccent", "01010435"}, {"colorPrimaryDark", "01010434"},
            {"selectableItemBackground", "0101030e"}, {"selectableItemBackgroundBorderless", "0101045c"},
            {"listPreferredItemHeight", "0101004d"}, {"actionBarSize", "010102eb"},
            {"listPreferredItemPaddingLeft", "010103bb"}, {"listPreferredItemPaddingRight", "010103bc"},
            {"listPreferredItemPaddingStart", "010103bd"}, {"listPreferredItemPaddingEnd", "010103be"},
            {"editTextBackground", "01010352"}, {"windowBackground", "01010054"}, {"textSize", "01010095"},
            {"textStyle", "01010097"}, {"textColor", "01010098"}, {"textColorHint", "0101009a"},
            {"textAppearance", "01010034"}, {"layout_width", "010100f4"}, {"layout_height", "010100f5"},
            {"padding", "010100d5"}, {"paddingLeft", "010100d6"}, {"paddingTop", "010100d7"},
            {"paddingRight", "010100d8"}, {"paddingBottom", "010100d9"}, {"background", "010100d4"},
            {"minWidth", "0101013f"}, {"minHeight", "01010140"}, {"gravity", "010100af"},
            {"fontFamily", "010103ac"}, {"textAllCaps", "0101038c"}, {"letterSpacing", "010104b6"},
            {"id", "010100d0"}, {"text", "0101014f"}, {"hint", "01010150"}, {"src", "01010119"},
            {"orientation", "010100c4"}, {"visibility", "010100dc"}, {"alpha", "0101031f"},
            {"elevation", "01010440"}, {"tint", "01010121"}, {"checked", "01010106"},
            {"maxLines", "01010153"}, {"lines", "01010154"}, {"singleLine", "0101015d"}, {"ellipsize", "010100ab"},
            {"layout_margin", "010100f6"}, {"layout_gravity", "010100b3"}, {"layout_weight", "01010181"},
            {"paddingStart", "010103b3"}, {"paddingEnd", "010103b4"}, {"textColorLink", "0101009b"},
            {"drawablePadding", "01010171"}, {"insetLeft", "010101b7"}, {"insetRight", "010101b8"},
            {"insetTop", "010101b9"}, {"insetBottom", "010101ba"}, {"foreground", "01010109"},
            {"clickable", "010100e5"}, {"focusable", "010100da"}, {"scaleType", "0101011d"},
            {"columnWidth", "01010117"},
        };
        for (String[] k : known) {
            int id = (int) Long.parseLong(k[1], 16);
            ATTR_IDS.put(k[0], id);
            ATTR_NAMES.put(id, k[0]);
        }
    }

    /** Id of android.R.attr.[name]: the public id when known, else a stable id for this process */
    public static int attrId(String name) {
        Integer id = ATTR_IDS.get(name);
        if (id != null) return id;
        synchronized (ATTR_IDS) {
            id = ATTR_IDS.get(name);
            if (id != null) return id;
            int n = NEXT_ATTR.getAndIncrement();
            ATTR_IDS.put(name, n);
            ATTR_NAMES.put(n, name);
            return n;
        }
    }

    /** Name of a framework attribute id handed out by [attrId] (null when unknown) */
    public static String attrName(int id) {
        return ATTR_NAMES.get(id);
    }

    public static boolean isFramework(int id) {
        return (id >>> 24) == 0x01;
    }

    private static final java.util.Set<Integer> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Neutral value for a framework resource the runtime doesn't define, by resource type */
    public static ResValue fallback(int id) {
        int type = (id >>> 16) & 0xff;
        ResValue v;
        switch (type) {
            case 0x04: v = ResValue.string(""); break;                  // string
            case 0x05: v = ResValue.dimen(0f, 0); break;                // dimen
            case 0x06: v = ResValue.color(0x00000000); break;           // color
            case 0x08: v = ResValue.file("@android:drawable/unknown"); break; // drawable
            case 0x0e: v = ResValue.integer(0); break;                  // integer
            case 0x11: v = ResValue.bool(false); break;                 // bool
            default: return null;
        }
        if (warned.add(id)) android.util.Log.w("Resources", "Framework resource #0x" + Integer.toHexString(id) + " is not provided, using a neutral value");
        return v;
    }

    @Override
    public ResValue get(int id, Configuration config) {
        return values.get(id);
    }

    @Override
    public int getIdentifier(String type, String name) {
        Integer id = ids.get(type + "/" + name);
        return id == null ? 0 : id;
    }

    @Override
    public String getResourceName(int id) {
        return names.get(id);
    }

    @Override
    public String getPackageName() {
        return "android";
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        throw new java.io.FileNotFoundException(path);
    }

    @Override
    public boolean isBinaryXml(String path) {
        return false;
    }
}
