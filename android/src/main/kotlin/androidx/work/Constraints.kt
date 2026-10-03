package androidx.work

class Constraints(
    val requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED,
    val requiresCharging: Boolean = false,
    val requiresDeviceIdle: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val requiresStorageNotLow: Boolean = false
) {
    class Builder {
        private var requiredNetworkType: NetworkType = NetworkType.NOT_REQUIRED
        private var requiresCharging: Boolean = false
        private var requiresDeviceIdle: Boolean = false
        private var requiresBatteryNotLow: Boolean = false
        private var requiresStorageNotLow: Boolean = false

        fun setRequiredNetworkType(networkType: NetworkType): Builder = apply { this.requiredNetworkType = networkType }
        fun setRequiresCharging(requiresCharging: Boolean): Builder = apply { this.requiresCharging = requiresCharging }
        fun setRequiresDeviceIdle(requiresDeviceIdle: Boolean): Builder = apply { this.requiresDeviceIdle = requiresDeviceIdle }
        fun setRequiresBatteryNotLow(requiresBatteryNotLow: Boolean): Builder = apply { this.requiresBatteryNotLow = requiresBatteryNotLow }
        fun setRequiresStorageNotLow(requiresStorageNotLow: Boolean): Builder = apply { this.requiresStorageNotLow = requiresStorageNotLow }

        fun build(): Constraints = Constraints(
            requiredNetworkType,
            requiresCharging,
            requiresDeviceIdle,
            requiresBatteryNotLow,
            requiresStorageNotLow
        )
    }

    companion object {
        @JvmField
        val NONE = Constraints()
    }
}
