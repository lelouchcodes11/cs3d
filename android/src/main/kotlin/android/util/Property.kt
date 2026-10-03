package android.util

abstract class Property<T, V>(val type: Class<V>?, val name: String?) {
    abstract fun get(obj: T): V
    open fun set(obj: T, value: V) {
        throw UnsupportedOperationException("Property $name is read-only")
    }
    open fun isReadOnly(): Boolean = false

    companion object {
        @JvmStatic
        fun <T, V> of(hostType: Class<T>, valueType: Class<V>, name: String): Property<T, V> {
            return object : Property<T, V>(valueType, name) {
                override fun get(obj: T): V {
                    val getter = hostType.getMethod("get" + name.replaceFirstChar { it.uppercaseChar() })
                    @Suppress("UNCHECKED_CAST")
                    return getter.invoke(obj) as V
                }

                override fun set(obj: T, value: V) {
                    val setter = hostType.getMethod("set" + name.replaceFirstChar { it.uppercaseChar() }, valueType)
                    setter.invoke(obj, value)
                }
            }
        }
    }
}
