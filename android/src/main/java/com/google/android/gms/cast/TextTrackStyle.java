package com.google.android.gms.cast;

public class TextTrackStyle {
    public static final int EDGE_TYPE_UNSPECIFIED = -1;
    public static final int EDGE_TYPE_NONE = 0;
    public static final int EDGE_TYPE_OUTLINE = 1;
    public static final int EDGE_TYPE_DROP_SHADOW = 2;
    public static final int EDGE_TYPE_RAISED = 3;
    public static final int EDGE_TYPE_DEPRESSED = 4;

    public static final int FONT_FAMILY_UNSPECIFIED = -1;
    public static final int FONT_FAMILY_SANS_SERIF = 0;
    public static final int FONT_FAMILY_MONOSPACED_SANS_SERIF = 1;
    public static final int FONT_FAMILY_SERIF = 2;
    public static final int FONT_FAMILY_MONOSPACED_SERIF = 3;
    public static final int FONT_FAMILY_CASUAL = 4;
    public static final int FONT_FAMILY_CURSIVE = 5;
    public static final int FONT_FAMILY_SMALL_CAPITALS = 6;

    public Integer fontGenericFamily;
    public int windowColor;
    public int backgroundColor;
    public int edgeColor;
    public int edgeType;
    public int foregroundColor;
    public float fontScale = 1.0f;

    public Integer getFontGenericFamily() { return fontGenericFamily; }
    public void setFontGenericFamily(Integer f) { this.fontGenericFamily = f; }
    public int getWindowColor() { return windowColor; }
    public void setWindowColor(int c) { this.windowColor = c; }
    public int getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(int c) { this.backgroundColor = c; }
    public int getEdgeColor() { return edgeColor; }
    public void setEdgeColor(int c) { this.edgeColor = c; }
    public int getEdgeType() { return edgeType; }
    public void setEdgeType(int t) { this.edgeType = t; }
    public int getForegroundColor() { return foregroundColor; }
    public void setForegroundColor(int c) { this.foregroundColor = c; }
    public float getFontScale() { return fontScale; }
    public void setFontScale(float s) { this.fontScale = s; }

    public void setFontFamily(String font) {}
}
