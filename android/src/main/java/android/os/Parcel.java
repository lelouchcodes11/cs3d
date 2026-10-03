package android.os;

import java.util.ArrayList;
import java.util.List;

/** Minimal in-memory Parcel: values are written to and read from a list in order. */
public final class Parcel {
    private final List<Object> mValues = new ArrayList<>();
    private int mPos = 0;

    private Parcel() {
    }

    public static Parcel obtain() {
        return new Parcel();
    }

    public void recycle() {
        mValues.clear();
        mPos = 0;
    }

    public int dataSize() { return mValues.size(); }
    public int dataPosition() { return mPos; }
    public void setDataPosition(int pos) { mPos = pos; }

    public void writeInt(int v) { mValues.add(v); }
    public void writeLong(long v) { mValues.add(v); }
    public void writeFloat(float v) { mValues.add(v); }
    public void writeDouble(double v) { mValues.add(v); }
    public void writeString(String v) { mValues.add(v); }
    public void writeByte(byte v) { mValues.add(v); }
    public void writeValue(Object v) { mValues.add(v); }
    public void writeParcelable(Parcelable p, int flags) { mValues.add(p); }
    public void writeBundle(Bundle b) { mValues.add(b); }
    public void writeByteArray(byte[] b) { mValues.add(b); }
    public void writeStringList(List<String> val) { mValues.add(val == null ? null : new ArrayList<>(val)); }

    private Object next() {
        return mPos < mValues.size() ? mValues.get(mPos++) : null;
    }

    public int readInt() { Object o = next(); return o == null ? 0 : (Integer) o; }
    public long readLong() { Object o = next(); return o == null ? 0L : (Long) o; }
    public float readFloat() { Object o = next(); return o == null ? 0f : (Float) o; }
    public double readDouble() { Object o = next(); return o == null ? 0.0 : (Double) o; }
    public String readString() { return (String) next(); }
    public byte readByte() { Object o = next(); return o == null ? 0 : (Byte) o; }
    public Object readValue(ClassLoader loader) { return next(); }
    public Bundle readBundle() { return (Bundle) next(); }
    public Bundle readBundle(ClassLoader loader) { return (Bundle) next(); }
    public byte[] createByteArray() { return (byte[]) next(); }

    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T readParcelable(ClassLoader loader) { return (T) next(); }

    @SuppressWarnings("unchecked")
    public ArrayList<String> createStringArrayList() { return (ArrayList<String>) next(); }
}
