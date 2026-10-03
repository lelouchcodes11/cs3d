package androidx.work

class Data(val keyValueMap: Map<String, Any?> = emptyMap()) {
    fun getString(key: String): String? = keyValueMap[key] as? String
    fun getInt(key: String, defaultValue: Int): Int = (keyValueMap[key] as? Number)?.toInt() ?: defaultValue
    fun getLong(key: String, defaultValue: Long): Long = (keyValueMap[key] as? Number)?.toLong() ?: defaultValue
    fun getBoolean(key: String, defaultValue: Boolean): Boolean = (keyValueMap[key] as? Boolean) ?: defaultValue
    fun getFloat(key: String, defaultValue: Float): Float = (keyValueMap[key] as? Number)?.toFloat() ?: defaultValue
    fun getDouble(key: String, defaultValue: Double): Double = (keyValueMap[key] as? Number)?.toDouble() ?: defaultValue
    fun getByteArray(key: String): ByteArray? = keyValueMap[key] as? ByteArray
    fun getStringArray(key: String): Array<String>? = keyValueMap[key] as? Array<String>

    class Builder {
        private val map = mutableMapOf<String, Any?>()

        fun putString(key: String, value: String?): Builder = apply { map[key] = value }
        fun putInt(key: String, value: Int): Builder = apply { map[key] = value }
        fun putLong(key: String, value: Long): Builder = apply { map[key] = value }
        fun putBoolean(key: String, value: Boolean): Builder = apply { map[key] = value }
        fun putFloat(key: String, value: Float): Builder = apply { map[key] = value }
        fun putDouble(key: String, value: Double): Builder = apply { map[key] = value }
        fun putByteArray(key: String, value: ByteArray?): Builder = apply { map[key] = value }
        fun putAll(data: Data): Builder = apply { map.putAll(data.keyValueMap) }
        fun putAll(map: Map<String, Any?>): Builder = apply { this.map.putAll(map) }
        fun build(): Data = Data(map.toMap())
    }

    companion object {
        @JvmField
        val EMPTY = Data()
    }
}

fun workDataOf(vararg pairs: Pair<String, Any?>): Data = Data(pairs.toMap())
