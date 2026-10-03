package android.content.res;

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import com.lagradost.desktop.runtime.AndroidRuntime;
import com.lagradost.desktop.runtime.res.FrameworkResources;
import com.lagradost.desktop.runtime.res.ResValue;
import com.lagradost.desktop.runtime.res.ResourceSupport;
import com.lagradost.desktop.runtime.res.ResourceTable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

public class Resources {
    public static final int ID_NULL = 0;

    public static class NotFoundException extends RuntimeException {
        public NotFoundException() {
        }

        public NotFoundException(String name) {
            super(name);
        }

        public NotFoundException(String name, Exception cause) {
            super(name, cause);
        }
    }

    private static Resources sSystem;

    final ResourceTable mTable;
    final AssetManager mAssets;
    final DisplayMetrics mMetrics;
    final Configuration mConfiguration;

    /**
     * Android's public (deprecated) constructor. The AssetManager decides which resource table is
     * used, see {@link AssetManager#addAssetPath}.
     */
    @Deprecated
    public Resources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
        mAssets = assets;
        mTable = assets == null ? null : assets.getTable();
        mMetrics = metrics == null ? AndroidRuntime.INSTANCE.getDisplayMetrics() : metrics;
        mConfiguration = config == null ? Configuration.current() : config;
    }

    public Resources(ResourceTable table) {
        this(new AssetManager(table), AndroidRuntime.INSTANCE.getDisplayMetrics(), Configuration.current());
    }

    public static Resources getSystem() {
        if (sSystem == null) sSystem = new Resources(FrameworkResources.INSTANCE);
        return sSystem;
    }

    public ResourceTable getTable() {
        return mTable;
    }

    /** Resolve an id to a value following references */
    public ResValue resolve(int id) {
        ResValue v = null;
        for (int depth = 0; depth < 20; depth++) {
            if (FrameworkResources.isFramework(id)) {
                v = FrameworkResources.INSTANCE.get(id, mConfiguration);
            } else if (mTable != null) {
                v = mTable.get(id, getConfiguration());
            } else {
                v = null;
            }
            if (v == null && FrameworkResources.isFramework(id)) {
                // A framework resource the runtime doesn't provide: degrade instead of failing the
                // extension (every framework resource exists on a real device)
                v = FrameworkResources.fallback(id);
            }
            if (v == null) {
                throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id));
            }
            if (v.kind == ResValue.Kind.REFERENCE) {
                id = (Integer) v.value;
                continue;
            }
            return v;
        }
        throw new NotFoundException("Reference loop for #0x" + Integer.toHexString(id));
    }

    public ResValue resolveOrNull(int id) {
        try {
            return resolve(id);
        } catch (NotFoundException e) {
            return null;
        }
    }

    public CharSequence getText(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.STRING) return (CharSequence) v.value;
        if (v.value != null) return String.valueOf(v.value);
        throw new NotFoundException("String resource ID #0x" + Integer.toHexString(id));
    }

    public CharSequence getText(int id, CharSequence def) {
        ResValue v = resolveOrNull(id);
        return v != null && v.kind == ResValue.Kind.STRING ? (CharSequence) v.value : def;
    }

    public String getString(int id) throws NotFoundException {
        return getText(id).toString();
    }

    public String getString(int id, Object... formatArgs) throws NotFoundException {
        final String raw = getString(id);
        return String.format(getLocale(), raw, formatArgs);
    }

    public CharSequence getQuantityText(int id, int quantity) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind != ResValue.Kind.PLURALS) throw new NotFoundException("Plurals resource ID #0x" + Integer.toHexString(id));
        Map<String, CharSequence> map = v.plurals();
        String rule = ResourceSupport.pluralRule(getLocale(), quantity);
        CharSequence text = map.get(rule);
        if (text == null && quantity == 0) text = map.get("zero");
        if (text == null && quantity == 1) text = map.get("one");
        if (text == null) text = map.get("other");
        if (text == null && !map.isEmpty()) text = map.values().iterator().next();
        if (text == null) throw new NotFoundException("Plural resource ID #0x" + Integer.toHexString(id));
        return text;
    }

    public String getQuantityString(int id, int quantity) throws NotFoundException {
        return getQuantityText(id, quantity).toString();
    }

    public String getQuantityString(int id, int quantity, Object... formatArgs) throws NotFoundException {
        return String.format(getLocale(), getQuantityText(id, quantity).toString(), formatArgs);
    }

    public CharSequence[] getTextArray(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.STRING_ARRAY) return (CharSequence[]) v.value;
        throw new NotFoundException("Array resource ID #0x" + Integer.toHexString(id));
    }

    public String[] getStringArray(int id) throws NotFoundException {
        CharSequence[] arr = getTextArray(id);
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i] == null ? null : arr[i].toString();
        return out;
    }

    public int[] getIntArray(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.INT_ARRAY) return (int[]) v.value;
        if (v.kind == ResValue.Kind.STRING_ARRAY) {
            CharSequence[] arr = (CharSequence[]) v.value;
            int[] out = new int[arr.length];
            for (int i = 0; i < arr.length; i++) {
                try {
                    out[i] = Integer.parseInt(arr[i].toString().trim());
                } catch (Exception ignored) {
                }
            }
            return out;
        }
        throw new NotFoundException("Int array resource ID #0x" + Integer.toHexString(id));
    }

    public TypedArray obtainTypedArray(int id) throws NotFoundException {
        ResValue v = resolve(id);
        return TypedArray.fromArray(this, v);
    }

    public float getDimension(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.DIMEN) {
            return TypedValue.applyDimension(v.unit, (Float) v.value, getDisplayMetrics());
        }
        if (v.kind == ResValue.Kind.INTEGER) return (Integer) v.value;
        if (v.kind == ResValue.Kind.FLOAT) return (Float) v.value;
        throw new NotFoundException("Dimension resource ID #0x" + Integer.toHexString(id));
    }

    public int getDimensionPixelOffset(int id) throws NotFoundException {
        return (int) getDimension(id);
    }

    public int getDimensionPixelSize(int id) throws NotFoundException {
        final float f = getDimension(id);
        final int res = (int) ((f >= 0) ? (f + 0.5f) : (f - 0.5f));
        if (res != 0) return res;
        if (f == 0) return 0;
        return f > 0 ? 1 : -1;
    }

    public float getFraction(int id, int base, int pbase) {
        ResValue v = resolve(id);
        if (v.value instanceof Float) return (Float) v.value * base;
        return 0f;
    }

    public int getInteger(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.INTEGER || v.kind == ResValue.Kind.COLOR) return (Integer) v.value;
        if (v.kind == ResValue.Kind.STRING) return Integer.parseInt(v.value.toString().trim());
        throw new NotFoundException("Integer resource ID #0x" + Integer.toHexString(id));
    }

    public float getFloat(int id) {
        ResValue v = resolve(id);
        if (v.value instanceof Number) return ((Number) v.value).floatValue();
        return Float.parseFloat(v.value.toString());
    }

    public boolean getBoolean(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.BOOL) return (Boolean) v.value;
        if (v.kind == ResValue.Kind.INTEGER) return ((Integer) v.value) != 0;
        if (v.kind == ResValue.Kind.STRING) return Boolean.parseBoolean(v.value.toString());
        throw new NotFoundException("Boolean resource ID #0x" + Integer.toHexString(id));
    }

    @Deprecated
    public int getColor(int id) throws NotFoundException {
        return getColor(id, null);
    }

    public int getColor(int id, Theme theme) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.COLOR || v.kind == ResValue.Kind.INTEGER) return (Integer) v.value;
        if (v.kind == ResValue.Kind.FILE) return getColorStateList(id, theme).getDefaultColor();
        if (v.kind == ResValue.Kind.ATTRIBUTE && theme != null) {
            TypedValue tv = new TypedValue();
            if (theme.resolveAttribute((Integer) v.value, tv, true)) return tv.data;
        }
        throw new NotFoundException("Color resource ID #0x" + Integer.toHexString(id));
    }

    @Deprecated
    public ColorStateList getColorStateList(int id) throws NotFoundException {
        return getColorStateList(id, null);
    }

    public ColorStateList getColorStateList(int id, Theme theme) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.COLOR || v.kind == ResValue.Kind.INTEGER) return ColorStateList.valueOf((Integer) v.value);
        if (v.kind == ResValue.Kind.FILE) {
            ColorStateList csl = ResourceSupport.loadColorStateList(this, (String) v.value, theme);
            if (csl != null) return csl;
        }
        throw new NotFoundException("ColorStateList resource ID #0x" + Integer.toHexString(id));
    }

    @Deprecated
    public Drawable getDrawable(int id) throws NotFoundException {
        return getDrawable(id, null);
    }

    public Drawable getDrawable(int id, Theme theme) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.COLOR) return new ColorDrawable((Integer) v.value);
        if (v.kind == ResValue.Kind.FILE) {
            Drawable d = ResourceSupport.loadDrawable(this, id, (String) v.value, theme);
            if (d != null) return d;
        }
        throw new NotFoundException("Drawable resource ID #0x" + Integer.toHexString(id));
    }

    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return getDrawable(id, null);
    }

    public Drawable getDrawableForDensity(int id, int density, Theme theme) {
        return getDrawable(id, theme);
    }

    public Typeface getFont(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind == ResValue.Kind.FILE) {
            Typeface tf = ResourceSupport.loadFont(this, (String) v.value);
            if (tf != null) return tf;
        }
        return Typeface.DEFAULT;
    }

    public XmlResourceParser getLayout(int id) throws NotFoundException {
        return loadXmlResourceParser(id, "layout");
    }

    public XmlResourceParser getAnimation(int id) throws NotFoundException {
        return loadXmlResourceParser(id, "anim");
    }

    public XmlResourceParser getXml(int id) throws NotFoundException {
        return loadXmlResourceParser(id, "xml");
    }

    XmlResourceParser loadXmlResourceParser(int id, String type) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind != ResValue.Kind.FILE) {
            throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type " + type + " is not an xml file");
        }
        try {
            return ResourceSupport.openXmlParser(this, (String) v.value);
        } catch (IOException e) {
            throw new NotFoundException("File " + v.value + " from xml type " + type + " resource ID #0x" + Integer.toHexString(id), e);
        }
    }

    public InputStream openRawResource(int id) throws NotFoundException {
        ResValue v = resolve(id);
        if (v.kind != ResValue.Kind.FILE) throw new NotFoundException("Resource ID #0x" + Integer.toHexString(id));
        try {
            return mTable.openFile((String) v.value);
        } catch (IOException e) {
            throw new NotFoundException("File " + v.value, e);
        }
    }

    public InputStream openRawResource(int id, TypedValue value) throws NotFoundException {
        return openRawResource(id);
    }

    public void getValue(int id, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        ResValue v = resolve(id);
        ResourceSupport.toTypedValue(this, v, outValue);
        outValue.resourceId = id;
    }

    public void getValue(String name, TypedValue outValue, boolean resolveRefs) throws NotFoundException {
        int id = getIdentifier(name, "string", null);
        if (id == 0) throw new NotFoundException("String resource name " + name);
        getValue(id, outValue, resolveRefs);
    }

    public int getIdentifier(String name, String defType, String defPackage) {
        if (name == null) return 0;
        String type = defType;
        String entry = name;
        String pkg = defPackage;
        int colon = entry.indexOf(':');
        if (colon >= 0) {
            pkg = entry.substring(0, colon);
            entry = entry.substring(colon + 1);
        }
        if (entry.startsWith("@")) entry = entry.substring(1);
        int slash = entry.indexOf('/');
        if (slash >= 0) {
            type = entry.substring(0, slash);
            entry = entry.substring(slash + 1);
        }
        if (type == null) return 0;
        if ("android".equals(pkg)) return FrameworkResources.INSTANCE.getIdentifier(type, entry);
        if (mTable == null) return 0;
        return mTable.getIdentifier(type, entry);
    }

    public String getResourceName(int resid) throws NotFoundException {
        String name = FrameworkResources.isFramework(resid)
                ? FrameworkResources.INSTANCE.getResourceName(resid)
                : (mTable == null ? null : mTable.getResourceName(resid));
        if (name == null && FrameworkResources.attrName(resid) != null) name = "android:attr/" + FrameworkResources.attrName(resid);
        if (name == null) throw new NotFoundException("Unable to find resource ID #0x" + Integer.toHexString(resid));
        return name;
    }

    public String getResourcePackageName(int resid) throws NotFoundException {
        String n = getResourceName(resid);
        return n.substring(0, n.indexOf(':'));
    }

    public String getResourceTypeName(int resid) throws NotFoundException {
        String n = getResourceName(resid);
        return n.substring(n.indexOf(':') + 1, n.indexOf('/'));
    }

    public String getResourceEntryName(int resid) throws NotFoundException {
        String n = getResourceName(resid);
        return n.substring(n.indexOf('/') + 1);
    }

    public DisplayMetrics getDisplayMetrics() {
        return mMetrics;
    }

    public Configuration getConfiguration() {
        return mConfiguration;
    }

    Locale getLocale() {
        Locale l = mConfiguration.locale;
        return l != null ? l : Locale.getDefault();
    }

    @Deprecated
    public void updateConfiguration(Configuration config, DisplayMetrics metrics) {
        if (config != null) mConfiguration.setTo(config);
        if (metrics != null) mMetrics.setTo(metrics);
    }

    public final AssetManager getAssets() {
        return mAssets;
    }

    public final Theme newTheme() {
        return new Theme(this);
    }

    public final void flushLayoutCache() {
    }

    public final class Theme {
        final Resources mResources;
        final java.util.Map<Integer, TypedValue> mAttrs = new java.util.HashMap<>();

        Theme(Resources res) {
            mResources = res;
            ResourceSupport.applyDefaultTheme(this);
        }

        public void applyStyle(int resId, boolean force) {
            ResourceSupport.applyStyle(this, resId, force);
        }

        public void setTo(Theme other) {
            mAttrs.clear();
            mAttrs.putAll(other.mAttrs);
        }

        public Resources getResources() {
            return mResources;
        }

        /** Desktop extension: define the value of a theme attribute */
        public void putAttribute(int attr, TypedValue value) {
            mAttrs.put(attr, value);
        }

        public boolean resolveAttribute(int resid, TypedValue outValue, boolean resolveRefs) {
            TypedValue v = mAttrs.get(resid);
            if (v == null) return false;
            outValue.type = v.type;
            outValue.data = v.data;
            outValue.string = v.string;
            outValue.resourceId = v.resourceId;
            outValue.assetCookie = v.assetCookie;
            outValue.density = v.density;
            return true;
        }

        public TypedArray obtainStyledAttributes(int[] attrs) {
            return TypedArray.fromTheme(mResources, this, null, attrs);
        }

        public TypedArray obtainStyledAttributes(int resId, int[] attrs) throws NotFoundException {
            return TypedArray.fromTheme(mResources, this, null, attrs);
        }

        public TypedArray obtainStyledAttributes(android.util.AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
            return TypedArray.fromTheme(mResources, this, set, attrs);
        }

        public Drawable getDrawable(int id) throws NotFoundException {
            return mResources.getDrawable(id, this);
        }

        public int getChangingConfigurations() {
            return 0;
        }

        public void rebase() {
        }
    }
}
