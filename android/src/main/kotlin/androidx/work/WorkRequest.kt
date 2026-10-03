package androidx.work

import java.util.UUID
import java.util.concurrent.TimeUnit

abstract class WorkRequest protected constructor(
    val id: UUID,
    val workerClass: Class<out ListenableWorker>,
    val tags: Set<String>,
    val constraints: Constraints,
    val initialDelayMillis: Long,
    val inputData: Data
) {
    abstract class Builder<B : Builder<B, W>, W : WorkRequest>(
        val workerClass: Class<out ListenableWorker>
    ) {
        protected var id: UUID = UUID.randomUUID()
        protected val tags: MutableSet<String> = mutableSetOf(workerClass.name)
        protected var constraints: Constraints = Constraints.NONE
        protected var initialDelayMillis: Long = 0L
        protected var inputData: Data = Data.EMPTY

        @Suppress("UNCHECKED_CAST")
        fun setId(id: UUID): B = apply { this.id = id } as B
        @Suppress("UNCHECKED_CAST")
        fun addTag(tag: String): B = apply { this.tags.add(tag) } as B
        @Suppress("UNCHECKED_CAST")
        fun setConstraints(constraints: Constraints): B = apply { this.constraints = constraints } as B
        @Suppress("UNCHECKED_CAST")
        fun setInitialDelay(duration: Long, timeUnit: TimeUnit): B = apply {
            this.initialDelayMillis = timeUnit.toMillis(duration)
        } as B
        @Suppress("UNCHECKED_CAST")
        fun setInputData(inputData: Data): B = apply { this.inputData = inputData } as B

        abstract fun build(): W
    }

    companion object {
        const val DEFAULT_BACKOFF_DELAY_MILLIS = 30000L
        const val MAX_BACKOFF_MILLIS = 18000000L
        const val MIN_BACKOFF_MILLIS = 10000L
    }
}

class OneTimeWorkRequest internal constructor(
    id: UUID,
    workerClass: Class<out ListenableWorker>,
    tags: Set<String>,
    constraints: Constraints,
    initialDelayMillis: Long,
    inputData: Data
) : WorkRequest(id, workerClass, tags, constraints, initialDelayMillis, inputData) {

    class Builder(workerClass: Class<out ListenableWorker>) :
        WorkRequest.Builder<Builder, OneTimeWorkRequest>(workerClass) {

        override fun build(): OneTimeWorkRequest {
            return OneTimeWorkRequest(id, workerClass, tags, constraints, initialDelayMillis, inputData)
        }
    }

    companion object {
        @JvmStatic
        fun from(workerClass: Class<out ListenableWorker>): OneTimeWorkRequest =
            Builder(workerClass).build()
    }
}

class PeriodicWorkRequest internal constructor(
    id: UUID,
    workerClass: Class<out ListenableWorker>,
    tags: Set<String>,
    constraints: Constraints,
    initialDelayMillis: Long,
    inputData: Data,
    val repeatIntervalMillis: Long,
    val flexIntervalMillis: Long
) : WorkRequest(id, workerClass, tags, constraints, initialDelayMillis, inputData) {

    class Builder(
        workerClass: Class<out ListenableWorker>,
        val repeatInterval: Long,
        val repeatIntervalTimeUnit: TimeUnit
    ) : WorkRequest.Builder<Builder, PeriodicWorkRequest>(workerClass) {

        private var flexIntervalMillis: Long = repeatIntervalTimeUnit.toMillis(repeatInterval)

        constructor(
            workerClass: Class<out ListenableWorker>,
            repeatInterval: Long,
            repeatIntervalTimeUnit: TimeUnit,
            flexInterval: Long,
            flexIntervalTimeUnit: TimeUnit
        ) : this(workerClass, repeatInterval, repeatIntervalTimeUnit) {
            this.flexIntervalMillis = flexIntervalTimeUnit.toMillis(flexInterval)
        }

        override fun build(): PeriodicWorkRequest {
            val interval = repeatIntervalTimeUnit.toMillis(repeatInterval).coerceAtLeast(MIN_PERIODIC_INTERVAL_MILLIS)
            return PeriodicWorkRequest(
                id, workerClass, tags, constraints, initialDelayMillis, inputData, interval, flexIntervalMillis
            )
        }
    }

    companion object {
        const val MIN_PERIODIC_INTERVAL_MILLIS = 15 * 60 * 1000L // 15 minutes
        const val MIN_PERIODIC_FLEX_MILLIS = 5 * 60 * 1000L
    }
}
