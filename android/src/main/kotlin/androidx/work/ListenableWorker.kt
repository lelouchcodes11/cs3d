package androidx.work

import android.app.NotificationManager
import android.content.Context
import java.util.UUID

class WorkerParameters(
    val id: UUID,
    val inputData: Data,
    val tags: Set<String>,
    val runAttemptCount: Int = 0
)

abstract class ListenableWorker(
    val appContext: Context,
    val workerParams: WorkerParameters
) {
    val id: UUID get() = workerParams.id
    val inputData: Data get() = workerParams.inputData
    val tags: Set<String> get() = workerParams.tags
    val runAttemptCount: Int get() = workerParams.runAttemptCount
    var isStopped: Boolean = false
        private set

    open fun onStopped() {}

    internal fun stop() {
        isStopped = true
        onStopped()
    }

    sealed class Result {
        class Success(val outputData: Data = Data.EMPTY) : Result() {
            override fun toString(): String = "Result.Success(data=$outputData)"
        }
        class Failure(val outputData: Data = Data.EMPTY) : Result() {
            override fun toString(): String = "Result.Failure(data=$outputData)"
        }
        class Retry : Result() {
            override fun toString(): String = "Result.Retry"
        }

        companion object {
            @JvmStatic
            fun success(): Result = Success()
            @JvmStatic
            fun success(outputData: Data): Result = Success(outputData)
            @JvmStatic
            fun failure(): Result = Failure()
            @JvmStatic
            fun failure(outputData: Data): Result = Failure(outputData)
            @JvmStatic
            fun retry(): Result = Retry()
        }
    }
}

abstract class Worker(context: Context, workerParams: WorkerParameters) :
    ListenableWorker(context, workerParams) {
    abstract fun doWork(): Result
}

abstract class CoroutineWorker(context: Context, workerParams: WorkerParameters) :
    ListenableWorker(context, workerParams) {
    abstract suspend fun doWork(): Result

    open suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(0, android.app.Notification())

    suspend fun setForeground(foregroundInfo: ForegroundInfo) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(foregroundInfo.notificationId, foregroundInfo.notification)
    }
}
