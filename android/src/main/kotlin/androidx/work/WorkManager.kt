package androidx.work

import android.content.Context
import androidx.lifecycle.LiveData
import java.util.UUID

enum class ExistingWorkPolicy {
    REPLACE,
    KEEP,
    APPEND,
    APPEND_OR_REPLACE
}

enum class ExistingPeriodicWorkPolicy {
    REPLACE,
    KEEP,
    UPDATE,
    CANCEL_AND_REENQUEUE
}

enum class NetworkType {
    NOT_REQUIRED,
    CONNECTED,
    UNMETERED,
    NOT_ROAMING,
    METERED,
    TEMPORARILY_UNMETERED
}

interface Operation {
    val result: LiveData<Operation.State>

    sealed class State {
        object IN_PROGRESS : State()
        class SUCCESS : State()
        class FAILURE(val throwable: Throwable) : State()
    }

    companion object {
        val SUCCESS: Operation = object : Operation {
            override val result: LiveData<Operation.State> =
                androidx.lifecycle.MutableLiveData(Operation.State.SUCCESS())
        }
    }
}

class Configuration {
    class Builder {
        fun build(): Configuration = Configuration()
    }
}

abstract class WorkManager {
    abstract fun enqueue(workRequest: WorkRequest): Operation
    abstract fun enqueue(workRequests: List<WorkRequest>): Operation
    abstract fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        work: OneTimeWorkRequest
    ): Operation
    abstract fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        work: List<OneTimeWorkRequest>
    ): Operation
    abstract fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy,
        periodicWork: PeriodicWorkRequest
    ): Operation
    abstract fun cancelUniqueWork(uniqueWorkName: String): Operation
    abstract fun cancelAllWorkByTag(tag: String): Operation
    abstract fun cancelWorkById(id: UUID): Operation
    abstract fun cancelAllWork(): Operation
    abstract fun getWorkInfosForUniqueWorkLiveData(uniqueWorkName: String): LiveData<List<WorkInfo>>
    abstract fun getWorkInfosByTagLiveData(tag: String): LiveData<List<WorkInfo>>
    abstract fun getWorkInfoByIdLiveData(id: UUID): LiveData<WorkInfo?>

    companion object {
        @Volatile
        private var sInstance: WorkManager? = null

        @JvmStatic
        fun getInstance(context: Context): WorkManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: WorkManagerImpl(context.applicationContext ?: context).also { sInstance = it }
            }
        }

        @JvmStatic
        fun initialize(context: Context, configuration: Configuration) {
            // Desktop initialization no-op
        }
    }
}
