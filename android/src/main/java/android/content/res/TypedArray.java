package android.content.res;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import com.lagradost.desktop.runtime.res.ResValue;
import com.lagradost.desktop.runtime.res.ResourceSupport;

public class TypedArray implements AutoCloseable {
    private final Resources mResources;
    private final TypedValue[] mValues;
    /** Indices of the attributes that have a value (getIndexCount / getIndex) */
    private final int[] mDefined;

    TypedArray(Resources res, TypedValue[] values) {
        mResources = res;
        mValues = values;
        int n = 0;
        for (TypedValue v : values) if (v != null) n++;
        mDefined = new int[n];
        int j = 0;
        for (int i = 0; i < values.length; i++) if (values[i] != null) mDefined[j++] = i;
    }

    static TypedArray fromTheme(Resources res, Resources.Theme theme, AttributeSet set, int[] attrs) {
        TypedValue[] values = new TypedValue[attrs == null ? 0 : attrs.length];
        for (int i = 0; i < values.length; i++) {
            TypedValue tv = null;
            if (set != null) tv = ResourceSupport.attributeFromSet(res, set, attrs[i]);
            // enum and flag names compile to their values like aapt does
            if (tv != null && tv.type == TypedValue.TYPE_STRING) {
                Integer e = ResourceSupport.enumOrFlagValue(res, attrs[i], tv.string);
                if (e != null) {
                    tv = new TypedValue();
                    tv.type = TypedValue.TYPE_INT_DEC;
                    tv.data = e;
                }
            }
            if (tv == null) {
                TypedValue t = new TypedValue();
                if (theme != null && theme.resolveAttribute(attrs[i], t, true)) tv = t;
            }
            values[i] = tv;
        }
        return new TypedArray(res, values);
    }

    static TypedArray fromArray(Resources res, ResValue v) {
        TypedValue[] values;
        if (v.kind == ResValue.Kind.STRING_ARRAY) {
            CharSequence[] arr = (CharSequence[]) v.value;
            values = new TypedValue[arr.length];
            for (int i = 0; i < arr.length; i++) {
                values[i] = ResourceSupport.parseTypedValue(res, arr[i] == null ? null : arr[i].toString());
            }
        } else if (v.kind == ResValue.Kind.INT_ARRAY) {
            int[] arr = (int[]) v.value;
            values = new TypedValue[arr.length];
            for (int i = 0; i < arr.length; i++) {
                TypedValue tv = new TypedValue();
                tv.type = TypedValue.TYPE_INT_DEC;
                tv.data = arr[i];
                values[i] = tv;
            }
        } else {
            values = new TypedValue[0];
        }
        return new TypedArray(res, values);
    }

    public int getIndexCount() { return mDefined.length; }
    public int getIndex(int at) { return mDefined[at]; }
    public Resources getResources() { return mResources; }
    public boolean hasValue(int index) { return index < mValues.length && mValues[index] != null && mValues[index].type != TypedValue.TYPE_NULL; }
    public boolean hasValueOrEmpty(int index) { return hasValue(index); }
    public TypedValue peekValue(int index) { return index < mValues.length ? mValues[index] : null; }

    public boolean getValue(int index, TypedValue outValue) {
        TypedValue v = peekValue(index);
        if (v == null) return false;
        outValue.type = v.type;
        outValue.data = v.data;
        outValue.string = v.string;
        outValue.resourceId = v.resourceId;
        return true;
    }

    public int getType(int index) {
        TypedValue v = peekValue(index);
        return v == null ? TypedValue.TYPE_NULL : v.type;
    }

    public CharSequence getText(int index) {
        TypedValue v = peekValue(index);
        if (v == null) return null;
        if (v.type == TypedValue.TYPE_STRING) return v.string;
        if (v.resourceId != 0) {
            try {
                return mResources.getText(v.resourceId);
            } catch (Exception ignored) {
            }
        }
        return v.coerceToString();
    }

    public String getString(int index) {
        CharSequence cs = getText(index);
        return cs == null ? null : cs.toString();
    }

    public String getNonResourceString(int index) {
        return getString(index);
    }

    public boolean getBoolean(int index, boolean defValue) {
        TypedValue v = peekValue(index);
        if (v == null) return defValue;
        if (v.type == TypedValue.TYPE_STRING) return Boolean.parseBoolean(String.valueOf(v.string));
        return v.data != 0;
    }

    public int getInt(int index, int defValue) {
        TypedValue v = peekValue(index);
        if (v == null) return defValue;
        if (v.type == TypedValue.TYPE_STRING) {
            try {
                return Integer.parseInt(String.valueOf(v.string));
            } catch (Exception e) {
                return defValue;
            }
        }
        return v.data;
    }

    public int getInteger(int index, int defValue) {
        return getInt(index, defValue);
    }

    public float getFloat(int index, float defValue) {
        TypedValue v = peekValue(index);
        if (v == null) return defValue;
        if (v.type == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(v.data);
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return v.data;
        return defValue;
    }

    public int getColor(int index, int defValue) {
        TypedValue v = peekValue(index);
        if (v == null) return defValue;
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return v.data;
        if (v.resourceId != 0) {
            try {
                return mResources.getColor(v.resourceId, null);
            } catch (Exception ignored) {
            }
        }
        return defValue;
    }

    public ColorStateList getColorStateList(int index) {
        TypedValue v = peekValue(index);
        if (v == null) return null;
        if (v.resourceId != 0) {
            try {
                return mResources.getColorStateList(v.resourceId, null);
            } catch (Exception ignored) {
            }
        }
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return ColorStateList.valueOf(v.data);
        return null;
    }

    public float getDimension(int index, float defValue) {
        TypedValue v = peekValue(index);
        if (v == null) return defValue;
        if (v.type == TypedValue.TYPE_DIMENSION) return TypedValue.complexToDimension(v.data, mResources.getDisplayMetrics());
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return v.data;
        return defValue;
    }

    public int getDimensionPixelOffset(int index, int defValue) {
        return (int) getDimension(index, defValue);
    }

    public int getDimensionPixelSize(int index, int defValue) {
        float f = getDimension(index, defValue);
        return (int) (f >= 0 ? f + 0.5f : f - 0.5f);
    }

    public int getLayoutDimension(int index, String name) {
        return getDimensionPixelSize(index, 0);
    }

    public int getLayoutDimension(int index, int defValue) {
        TypedValue v = peekValue(index);
        if (v == null) return defValue;
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return v.data;
        return getDimensionPixelSize(index, defValue);
    }

    public float getFraction(int index, int base, int pbase, float defValue) {
        return defValue;
    }

    public int getResourceId(int index, int defValue) {
        TypedValue v = peekValue(index);
        return v == null || v.resourceId == 0 ? defValue : v.resourceId;
    }

    public Drawable getDrawable(int index) {
        TypedValue v = peekValue(index);
        if (v == null) return null;
        if (v.resourceId != 0) {
            try {
                return mResources.getDrawable(v.resourceId, null);
            } catch (Exception ignored) {
            }
        }
        if (v.type >= TypedValue.TYPE_FIRST_COLOR_INT && v.type <= TypedValue.TYPE_LAST_COLOR_INT) return new ColorDrawable(v.data);
        return null;
    }

    public CharSequence[] getTextArray(int index) {
        int id = getResourceId(index, 0);
        return id == 0 ? null : mResources.getTextArray(id);
    }

    public int getChangingConfigurations() {
        return 0;
    }

    public void recycle() {
    }

    @Override
    public void close() {
    }
}
