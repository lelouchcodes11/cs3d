package com.lagradost.desktop.runtime.res;

import java.util.Map;

/** A resolved resource value (after configuration/locale selection). */
public final class ResValue {
    public enum Kind {
        STRING, STRING_ARRAY, INT_ARRAY, PLURALS, COLOR, DIMEN, INTEGER, BOOL, FLOAT, FRACTION, FILE, REFERENCE, ATTRIBUTE, STYLE, ID, NULL
    }

    public final Kind kind;
    public final Object value;
    /** For DIMEN: the TypedValue complex unit; for FILE: unused */
    public final int unit;

    public ResValue(Kind kind, Object value) {
        this(kind, value, 0);
    }

    public ResValue(Kind kind, Object value, int unit) {
        this.kind = kind;
        this.value = value;
        this.unit = unit;
    }

    public static ResValue string(CharSequence s) {
        return new ResValue(Kind.STRING, s);
    }

    public static ResValue color(int argb) {
        return new ResValue(Kind.COLOR, argb);
    }

    public static ResValue file(String path) {
        return new ResValue(Kind.FILE, path);
    }

    public static ResValue reference(int id) {
        return new ResValue(Kind.REFERENCE, id);
    }

    public static ResValue integer(int v) {
        return new ResValue(Kind.INTEGER, v);
    }

    public static ResValue bool(boolean v) {
        return new ResValue(Kind.BOOL, v);
    }

    public static ResValue dimen(float v, int unit) {
        return new ResValue(Kind.DIMEN, v, unit);
    }

    @SuppressWarnings("unchecked")
    public Map<String, CharSequence> plurals() {
        return (Map<String, CharSequence>) value;
    }

    @Override
    public String toString() {
        return kind + ":" + value;
    }
}
