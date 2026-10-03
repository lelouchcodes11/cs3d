package android.os;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Bundle implements Cloneable, Parcelable {
    public static final Bundle EMPTY = new Bundle();

    private final Map<String, Object> mMap;
    private ClassLoader mClassLoader;

    public Bundle() {
        mMap = new LinkedHashMap<>();
    }

    public Bundle(int capacity) {
        mMap = new LinkedHashMap<>(Math.max(capacity, 4));
    }

    public Bundle(ClassLoader loader) {
        this();
        mClassLoader = loader;
    }

    public Bundle(Bundle b) {
        mMap = new LinkedHashMap<>(b.mMap);
        mClassLoader = b.mClassLoader;
    }

    @Override
    public Object clone() {
        return new Bundle(this);
    }

    public Bundle deepCopy() {
        return new Bundle(this);
    }

    public void setClassLoader(ClassLoader loader) {
        mClassLoader = loader;
    }

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    public int size() { return mMap.size(); }
    public boolean isEmpty() { return mMap.isEmpty(); }
    public void clear() { mMap.clear(); }
    public boolean containsKey(String key) { return mMap.containsKey(key); }
    public Object get(String key) { return mMap.get(key); }
    public void remove(String key) { mMap.remove(key); }
    public void putAll(Bundle bundle) { mMap.putAll(bundle.mMap); }
    public Set<String> keySet() { return mMap.keySet(); }

    public void putBoolean(String key, boolean value) { mMap.put(key, value); }
    public void putByte(String key, byte value) { mMap.put(key, value); }
    public void putChar(String key, char value) { mMap.put(key, value); }
    public void putShort(String key, short value) { mMap.put(key, value); }
    public void putInt(String key, int value) { mMap.put(key, value); }
    public void putLong(String key, long value) { mMap.put(key, value); }
    public void putFloat(String key, float value) { mMap.put(key, value); }
    public void putDouble(String key, double value) { mMap.put(key, value); }
    public void putString(String key, String value) { mMap.put(key, value); }
    public void putCharSequence(String key, CharSequence value) { mMap.put(key, value); }
    public void putParcelable(String key, Parcelable value) { mMap.put(key, value); }
    public void putParcelableArray(String key, Parcelable[] value) { mMap.put(key, value); }
    public void putParcelableArrayList(String key, ArrayList<? extends Parcelable> value) { mMap.put(key, value); }
    public void putIntegerArrayList(String key, ArrayList<Integer> value) { mMap.put(key, value); }
    public void putStringArrayList(String key, ArrayList<String> value) { mMap.put(key, value); }
    public void putSerializable(String key, Serializable value) { mMap.put(key, value); }
    public void putBooleanArray(String key, boolean[] value) { mMap.put(key, value); }
    public void putByteArray(String key, byte[] value) { mMap.put(key, value); }
    public void putIntArray(String key, int[] value) { mMap.put(key, value); }
    public void putLongArray(String key, long[] value) { mMap.put(key, value); }
    public void putFloatArray(String key, float[] value) { mMap.put(key, value); }
    public void putDoubleArray(String key, double[] value) { mMap.put(key, value); }
    public void putStringArray(String key, String[] value) { mMap.put(key, value); }
    public void putBundle(String key, Bundle value) { mMap.put(key, value); }

    private <T> T typed(String key, Class<T> c, T def) {
        Object o = mMap.get(key);
        if (o == null) return def;
        try {
            return c.cast(o);
        } catch (ClassCastException e) {
            return def;
        }
    }

    public boolean getBoolean(String key) { return typed(key, Boolean.class, false); }
    public boolean getBoolean(String key, boolean defaultValue) { return typed(key, Boolean.class, defaultValue); }
    public byte getByte(String key) { return typed(key, Byte.class, (byte) 0); }
    public Byte getByte(String key, byte defaultValue) { return typed(key, Byte.class, defaultValue); }
    public char getChar(String key) { return typed(key, Character.class, (char) 0); }
    public char getChar(String key, char defaultValue) { return typed(key, Character.class, defaultValue); }
    public short getShort(String key) { return typed(key, Short.class, (short) 0); }
    public short getShort(String key, short defaultValue) { return typed(key, Short.class, defaultValue); }
    public int getInt(String key) { return typed(key, Integer.class, 0); }
    public int getInt(String key, int defaultValue) { return typed(key, Integer.class, defaultValue); }
    public long getLong(String key) { return typed(key, Long.class, 0L); }
    public long getLong(String key, long defaultValue) { return typed(key, Long.class, defaultValue); }
    public float getFloat(String key) { return typed(key, Float.class, 0f); }
    public float getFloat(String key, float defaultValue) { return typed(key, Float.class, defaultValue); }
    public double getDouble(String key) { return typed(key, Double.class, 0.0); }
    public double getDouble(String key, double defaultValue) { return typed(key, Double.class, defaultValue); }

    public String getString(String key) {
        Object o = mMap.get(key);
        return o instanceof String ? (String) o : null;
    }

    public String getString(String key, String defaultValue) {
        String s = getString(key);
        return s == null ? defaultValue : s;
    }

    public CharSequence getCharSequence(String key) { return typed(key, CharSequence.class, null); }
    public CharSequence getCharSequence(String key, CharSequence defaultValue) { return typed(key, CharSequence.class, defaultValue); }
    public Bundle getBundle(String key) { return typed(key, Bundle.class, null); }

    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T getParcelable(String key) { return (T) mMap.get(key); }

    public <T> T getParcelable(String key, Class<T> clazz) { return typed(key, clazz, null); }
    public Parcelable[] getParcelableArray(String key) { return typed(key, Parcelable[].class, null); }

    @SuppressWarnings("unchecked")
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(String key) { return (ArrayList<T>) mMap.get(key); }

    public Serializable getSerializable(String key) { return typed(key, Serializable.class, null); }
    public <T extends Serializable> T getSerializable(String key, Class<T> clazz) { return typed(key, clazz, null); }

    @SuppressWarnings("unchecked")
    public ArrayList<Integer> getIntegerArrayList(String key) { return (ArrayList<Integer>) mMap.get(key); }

    @SuppressWarnings("unchecked")
    public ArrayList<String> getStringArrayList(String key) { return (ArrayList<String>) mMap.get(key); }

    public boolean[] getBooleanArray(String key) { return typed(key, boolean[].class, null); }
    public byte[] getByteArray(String key) { return typed(key, byte[].class, null); }
    public int[] getIntArray(String key) { return typed(key, int[].class, null); }
    public long[] getLongArray(String key) { return typed(key, long[].class, null); }
    public float[] getFloatArray(String key) { return typed(key, float[].class, null); }
    public double[] getDoubleArray(String key) { return typed(key, double[].class, null); }
    public String[] getStringArray(String key) { return typed(key, String[].class, null); }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public String toString() {
        return "Bundle" + mMap;
    }
}
