package android.util;

import java.util.TreeMap;

public class SparseArray<E> implements Cloneable {
    private TreeMap<Integer, E> mMap = new TreeMap<>();

    public SparseArray() {
    }

    public SparseArray(int initialCapacity) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public SparseArray<E> clone() {
        try {
            SparseArray<E> clone = (SparseArray<E>) super.clone();
            clone.mMap = new TreeMap<>(mMap);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public E get(int key) { return mMap.get(key); }
    public E get(int key, E valueIfKeyNotFound) { E v = mMap.get(key); return v == null ? valueIfKeyNotFound : v; }
    public boolean contains(int key) { return mMap.containsKey(key); }
    public void delete(int key) { mMap.remove(key); }
    public void remove(int key) { mMap.remove(key); }
    public void removeAt(int index) { mMap.remove(keyAt(index)); }
    public void put(int key, E value) { mMap.put(key, value); }
    public void append(int key, E value) { mMap.put(key, value); }
    public int size() { return mMap.size(); }
    public void clear() { mMap.clear(); }

    public int keyAt(int index) {
        int i = 0;
        for (Integer k : mMap.keySet()) {
            if (i++ == index) return k;
        }
        throw new ArrayIndexOutOfBoundsException(index);
    }

    public E valueAt(int index) {
        return mMap.get(keyAt(index));
    }

    public void setValueAt(int index, E value) {
        mMap.put(keyAt(index), value);
    }

    public int indexOfKey(int key) {
        int i = 0;
        for (Integer k : mMap.keySet()) {
            if (k == key) return i;
            i++;
        }
        return -1;
    }

    public int indexOfValue(E value) {
        int i = 0;
        for (E v : mMap.values()) {
            if (v == value) return i;
            i++;
        }
        return -1;
    }

    @Override
    public String toString() {
        return mMap.toString();
    }
}
