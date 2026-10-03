package android;

import com.lagradost.desktop.runtime.res.FrameworkResources;

/**
 * Framework resource ids (public.xml values) for code compiled against this runtime. Extension
 * bytecode has these constants inlined, so they must equal the real Android ids.
 */
public final class R {
    private R() {
    }

    public static final class id {
        public static final int background = 0x01020000;
        public static final int checkbox = FrameworkResources.ID_CHECKBOX;
        public static final int content = FrameworkResources.ID_CONTENT;
        public static final int edit = 0x01020003;
        public static final int empty = FrameworkResources.ID_EMPTY;
        public static final int hint = 0x01020005;
        public static final int icon = FrameworkResources.ID_ICON;
        public static final int icon1 = 0x01020007;
        public static final int icon2 = 0x01020008;
        public static final int input = 0x01020009;
        public static final int list = FrameworkResources.ID_LIST;
        public static final int message = FrameworkResources.ID_MESSAGE;
        public static final int primary = 0x0102000c;
        public static final int progress = FrameworkResources.ID_PROGRESS;
        public static final int selectedIcon = 0x0102000e;
        public static final int secondaryProgress = 0x0102000f;
        public static final int summary = FrameworkResources.ID_SUMMARY;
        public static final int text1 = FrameworkResources.ID_TEXT1;
        public static final int text2 = FrameworkResources.ID_TEXT2;
        public static final int title = FrameworkResources.ID_TITLE;
        public static final int toggle = 0x01020017;
        public static final int widget_frame = 0x01020018;
        public static final int list_container = 0x0102003f;
        public static final int button1 = FrameworkResources.ID_BUTTON1;
        public static final int button2 = FrameworkResources.ID_BUTTON2;
        public static final int button3 = FrameworkResources.ID_BUTTON3;
        public static final int home = FrameworkResources.ID_HOME;
    }

    public static final class layout {
        public static final int activity_list_item = FrameworkResources.LAYOUT_ACTIVITY_LIST_ITEM;
        public static final int simple_list_item_1 = FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_1;
        public static final int simple_list_item_2 = FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_2;
        public static final int simple_list_item_checked = FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_CHECKED;
        public static final int simple_spinner_item = FrameworkResources.LAYOUT_SIMPLE_SPINNER_ITEM;
        public static final int simple_spinner_dropdown_item = FrameworkResources.LAYOUT_SIMPLE_SPINNER_DROPDOWN_ITEM;
        public static final int simple_dropdown_item_1line = FrameworkResources.LAYOUT_SIMPLE_DROPDOWN_ITEM_1LINE;
        public static final int simple_list_item_single_choice = FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_SINGLE_CHOICE;
        public static final int simple_list_item_multiple_choice = FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_MULTIPLE_CHOICE;
        public static final int select_dialog_item = FrameworkResources.LAYOUT_SELECT_DIALOG_ITEM;
        public static final int select_dialog_singlechoice = FrameworkResources.LAYOUT_SELECT_DIALOG_SINGLECHOICE;
        public static final int select_dialog_multichoice = FrameworkResources.LAYOUT_SELECT_DIALOG_MULTICHOICE;
        public static final int simple_selectable_list_item = FrameworkResources.LAYOUT_SIMPLE_SELECTABLE_LIST_ITEM;
        public static final int simple_list_item_activated_1 = FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_ACTIVATED_1;
        public static final int simple_list_item_activated_2 = FrameworkResources.LAYOUT_SIMPLE_LIST_ITEM_ACTIVATED_2;
    }

    public static final class string {
        public static final int cancel = FrameworkResources.STRING_CANCEL;
        public static final int copy = FrameworkResources.STRING_COPY;
        public static final int no = FrameworkResources.STRING_NO;
        public static final int ok = FrameworkResources.STRING_OK;
        public static final int paste = FrameworkResources.STRING_PASTE;
        public static final int yes = FrameworkResources.STRING_YES;
    }

    public static final class color {
        public static final int darker_gray = 0x01060000;
        public static final int primary_text_dark = 0x01060001;
        public static final int primary_text_dark_nodisable = 0x01060002;
        public static final int primary_text_light = 0x01060003;
        public static final int primary_text_light_nodisable = 0x01060004;
        public static final int secondary_text_dark = 0x01060005;
        public static final int secondary_text_dark_nodisable = 0x01060006;
        public static final int secondary_text_light = 0x01060007;
        public static final int secondary_text_light_nodisable = 0x01060008;
        public static final int tab_indicator_text = 0x01060009;
        public static final int widget_edittext_dark = 0x0106000a;
        public static final int white = 0x0106000b;
        public static final int black = 0x0106000c;
        public static final int transparent = 0x0106000d;
        public static final int background_dark = 0x0106000e;
        public static final int background_light = 0x0106000f;
        public static final int tertiary_text_dark = 0x01060010;
        public static final int tertiary_text_light = 0x01060011;
        public static final int holo_blue_light = 0x01060012;
        public static final int holo_blue_dark = 0x01060013;
        public static final int holo_green_light = 0x01060014;
        public static final int holo_green_dark = 0x01060015;
        public static final int holo_red_light = 0x01060016;
        public static final int holo_red_dark = 0x01060017;
        public static final int holo_orange_light = 0x01060018;
        public static final int holo_orange_dark = 0x01060019;
        public static final int holo_purple = 0x0106001a;
        public static final int holo_blue_bright = 0x0106001b;
    }

    public static final class drawable {
        public static final int ic_dialog_alert = FrameworkResources.DRAWABLE_IC_DIALOG_ALERT;
        public static final int ic_menu_delete = FrameworkResources.DRAWABLE_IC_MENU_DELETE;
        public static final int ic_media_play = FrameworkResources.DRAWABLE_IC_MEDIA_PLAY;
        public static final int ic_media_pause = FrameworkResources.DRAWABLE_IC_MEDIA_PAUSE;
        public static final int ic_delete = FrameworkResources.DRAWABLE_IC_DELETE;
    }

    public static final class attr {
        public static final int textColorPrimary = FrameworkResources.ATTR_TEXT_COLOR_PRIMARY;
        public static final int textColorSecondary = FrameworkResources.ATTR_TEXT_COLOR_SECONDARY;
        public static final int colorBackground = FrameworkResources.ATTR_COLOR_BACKGROUND;
        public static final int colorPrimary = FrameworkResources.ATTR_COLOR_PRIMARY;
        public static final int colorAccent = FrameworkResources.ATTR_COLOR_ACCENT;
        public static final int colorPrimaryDark = FrameworkResources.ATTR_COLOR_PRIMARY_DARK;
        public static final int selectableItemBackground = FrameworkResources.ATTR_SELECTABLE_ITEM_BACKGROUND;
        public static final int selectableItemBackgroundBorderless = FrameworkResources.ATTR_SELECTABLE_ITEM_BACKGROUND_BORDERLESS;
        public static final int listPreferredItemHeight = FrameworkResources.ATTR_LIST_PREFERRED_ITEM_HEIGHT;
        public static final int actionBarSize = FrameworkResources.ATTR_ACTION_BAR_SIZE;
        public static final int columnWidth = FrameworkResources.ATTR_COLUMN_WIDTH;
    }
}
