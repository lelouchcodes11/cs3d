package android.util

/**
 * Android's LruCache: keeps at most [maxSize] (in the units of [sizeOf], entries by default), dropping the least recently used. Some extensions
 * keep their lookups in one (it was missing: NoClassDefFoundError when they loaded or first used it).
 */
open class LruCache<K : Any, V : Any>(maxSize: Int) {
    private val map = LinkedHashMap<K, V>(0, 0.75f, true)
    private var size = 0
    private var maxSize = maxSize
    private var putCount = 0
    private var createCount = 0
    private var evictionCount = 0
    private var hitCount = 0
    private var missCount = 0

    init {
        require(maxSize > 0) { "maxSize <= 0" }
    }

    open fun resize(maxSize: Int) {
        require(maxSize > 0) { "maxSize <= 0" }
        synchronized(this) { this.maxSize = maxSize }
        trimToSize(maxSize)
    }

    operator fun get(key: K): V? {
        synchronized(this) {
            map[key]?.let { hitCount++; return it }
            missCount++
        }
        val created = create(key) ?: return null
        val previous: V?
        synchronized(this) {
            createCount++
            previous = map.put(key, created)
            if (previous != null) map[key] = previous else size += safeSizeOf(key, created)
        }
        if (previous != null) {
            entryRemoved(false, key, created, previous)
            return previous
        }
        trimToSize(maxSize)
        return created
    }

    fun put(key: K, value: V): V? {
        val previous: V?
        synchronized(this) {
            putCount++
            size += safeSizeOf(key, value)
            previous = map.put(key, value)
            if (previous != null) size -= safeSizeOf(key, previous)
        }
        if (previous != null) entryRemoved(false, key, previous, value)
        trimToSize(maxSize)
        return previous
    }

    open fun trimToSize(maxSize: Int) {
        while (true) {
            val key: K
            val value: V
            synchronized(this) {
                if (size <= maxSize || map.isEmpty()) return
                val eldest = map.entries.iterator().next()
                key = eldest.key
                value = eldest.value
                map.remove(key)
                size -= safeSizeOf(key, value)
                evictionCount++
            }
            entryRemoved(true, key, value, null)
        }
    }

    fun remove(key: K): V? {
        val previous: V?
        synchronized(this) {
            previous = map.remove(key)
            if (previous != null) size -= safeSizeOf(key, previous)
        }
        if (previous != null) entryRemoved(false, key, previous, null)
        return previous
    }

    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}

    protected open fun create(key: K): V? = null

    private fun safeSizeOf(key: K, value: V): Int {
        val result = sizeOf(key, value)
        check(result >= 0) { "Negative size: $key=$value" }
        return result
    }

    protected open fun sizeOf(key: K, value: V): Int = 1

    fun evictAll() = trimToSize(-1)

    @Synchronized fun size(): Int = size
    @Synchronized fun maxSize(): Int = maxSize
    @Synchronized fun hitCount(): Int = hitCount
    @Synchronized fun missCount(): Int = missCount
    @Synchronized fun createCount(): Int = createCount
    @Synchronized fun putCount(): Int = putCount
    @Synchronized fun evictionCount(): Int = evictionCount
    @Synchronized fun snapshot(): MutableMap<K, V> = LinkedHashMap(map)

    @Synchronized override fun toString(): String {
        val accesses = hitCount + missCount
        val hitPercent = if (accesses != 0) 100 * hitCount / accesses else 0
        return "LruCache[maxSize=$maxSize,hits=$hitCount,misses=$missCount,hitRate=$hitPercent%]"
    }
}
