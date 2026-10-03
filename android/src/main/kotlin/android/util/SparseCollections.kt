package android.util

/** android.util.SparseBooleanArray: int keys kept sorted */
open class SparseBooleanArray @JvmOverloads constructor(initialCapacity: Int = 10) : Cloneable {
    private val map = java.util.TreeMap<Int, Boolean>()

    open operator fun get(key: Int): Boolean = map[key] ?: false
    open fun get(key: Int, valueIfKeyNotFound: Boolean): Boolean = map[key] ?: valueIfKeyNotFound
    open fun put(key: Int, value: Boolean) {
        map[key] = value
    }

    open fun append(key: Int, value: Boolean) = put(key, value)
    open fun delete(key: Int) {
        map.remove(key)
    }

    open fun removeAt(index: Int) {
        map.remove(keyAt(index))
    }

    open fun size(): Int = map.size
    open fun keyAt(index: Int): Int = map.keys.elementAt(index)
    open fun valueAt(index: Int): Boolean = map.values.elementAt(index)
    open fun setValueAt(index: Int, value: Boolean) {
        map[keyAt(index)] = value
    }

    open fun indexOfKey(key: Int): Int = map.keys.indexOf(key)
    open fun indexOfValue(value: Boolean): Int = map.values.indexOf(value)
    open fun clear() = map.clear()
    public override fun clone(): SparseBooleanArray = SparseBooleanArray().also { it.map.putAll(map) }
    override fun toString(): String = map.toString()
}

/** android.util.SparseIntArray */
open class SparseIntArray @JvmOverloads constructor(initialCapacity: Int = 10) : Cloneable {
    private val map = java.util.TreeMap<Int, Int>()

    open fun get(key: Int): Int = map[key] ?: 0
    open fun get(key: Int, valueIfKeyNotFound: Int): Int = map[key] ?: valueIfKeyNotFound
    open fun put(key: Int, value: Int) {
        map[key] = value
    }

    open fun append(key: Int, value: Int) = put(key, value)
    open fun delete(key: Int) {
        map.remove(key)
    }

    open fun removeAt(index: Int) {
        map.remove(keyAt(index))
    }

    open fun size(): Int = map.size
    open fun keyAt(index: Int): Int = map.keys.elementAt(index)
    open fun valueAt(index: Int): Int = map.values.elementAt(index)
    open fun indexOfKey(key: Int): Int = map.keys.indexOf(key)
    open fun indexOfValue(value: Int): Int = map.values.indexOf(value)
    open fun clear() = map.clear()
    public override fun clone(): SparseIntArray = SparseIntArray().also { it.map.putAll(map) }
    override fun toString(): String = map.toString()
}

/** android.util.SparseLongArray */
open class SparseLongArray @JvmOverloads constructor(initialCapacity: Int = 10) : Cloneable {
    private val map = java.util.TreeMap<Int, Long>()

    open fun get(key: Int): Long = map[key] ?: 0L
    open fun get(key: Int, valueIfKeyNotFound: Long): Long = map[key] ?: valueIfKeyNotFound
    open fun put(key: Int, value: Long) {
        map[key] = value
    }

    open fun append(key: Int, value: Long) = put(key, value)
    open fun delete(key: Int) {
        map.remove(key)
    }

    open fun size(): Int = map.size
    open fun keyAt(index: Int): Int = map.keys.elementAt(index)
    open fun valueAt(index: Int): Long = map.values.elementAt(index)
    open fun indexOfKey(key: Int): Int = map.keys.indexOf(key)
    open fun clear() = map.clear()
    public override fun clone(): SparseLongArray = SparseLongArray().also { it.map.putAll(map) }
}

/** android.util.LongSparseArray */
open class LongSparseArray<E> @JvmOverloads constructor(initialCapacity: Int = 10) : Cloneable {
    private val map = java.util.TreeMap<Long, E>()

    open fun get(key: Long): E? = map[key]
    open fun get(key: Long, valueIfKeyNotFound: E): E = map[key] ?: valueIfKeyNotFound
    open fun put(key: Long, value: E) {
        map[key] = value
    }

    open fun append(key: Long, value: E) = put(key, value)
    open fun delete(key: Long) {
        map.remove(key)
    }

    open fun remove(key: Long) = delete(key)
    open fun removeAt(index: Int) {
        map.remove(keyAt(index))
    }

    open fun size(): Int = map.size
    open fun keyAt(index: Int): Long = map.keys.elementAt(index)
    open fun valueAt(index: Int): E = map.values.elementAt(index)
    open fun setValueAt(index: Int, value: E) {
        map[keyAt(index)] = value
    }

    open fun indexOfKey(key: Long): Int = map.keys.indexOf(key)
    open fun indexOfValue(value: E): Int = map.values.indexOf(value)
    open fun clear() = map.clear()
    public override fun clone(): LongSparseArray<E> = LongSparseArray<E>().also { it.map.putAll(map) }
}
