package androidx.work

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class WorkManagerImpl(private val context: Context) : WorkManager() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "WorkManager-Scheduler").apply { isDaemon = true }
    }
    private val scheduledTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val workInfos = ConcurrentHashMap<String, MutableLiveData<List<WorkInfo>>>()
    private val workInfoMap = ConcurrentHashMap<UUID, WorkInfo>()
    private val prefs = context.getSharedPreferences("androidx.work.workmanager", Context.MODE_PRIVATE)

    init {
        restorePeriodicSchedules()
    }

    private fun restorePeriodicSchedules() {
        try {
            val all = prefs.all
            for ((key, value) in all) {
                if (key.startsWith("periodic_") && value is String) {
                    val uniqueWorkName = key.removePrefix("periodic_")
                    val parts = value.split(";")
                    if (parts.size >= 3) {
                        val workerClassName = parts[0]
                        val intervalMillis = parts[1].toLongOrNull() ?: continue
                        val lastRunMillis = parts[2].toLongOrNull() ?: 0L
                        val now = System.currentTimeMillis()
                        val delay = max(0L, intervalMillis - (now - lastRunMillis))
                        schedulePeriodic(uniqueWorkName, workerClassName, intervalMillis, delay)
                        println("WorkManager: Restored periodic schedule '$uniqueWorkName' ($workerClassName), next run in ${delay / 1000}s")
                    }
                }
            }
        } catch (e: Throwable) {
            System.err.println("WorkManager: Failed to restore schedules: $e")
        }
    }

    private fun schedulePeriodic(
        uniqueWorkName: String,
        workerClassName: String,
        intervalMillis: Long,
        initialDelayMillis: Long
    ) {
        scheduledTasks.remove(uniqueWorkName)?.cancel(false)
        val future = scheduler.scheduleWithFixedDelay({
            runWorker(uniqueWorkName, workerClassName)
        }, initialDelayMillis, intervalMillis, TimeUnit.MILLISECONDS)
        scheduledTasks[uniqueWorkName] = future
    }

    private fun runWorker(
        uniqueWorkName: String,
        workerClassName: String,
        workId: UUID = UUID.randomUUID(),
        inputData: Data = Data.EMPTY
    ) {
        scope.launch {
            updateWorkInfoState(uniqueWorkName, workId, WorkInfo.State.RUNNING)
            try {
                println("WorkManager: Starting work '$uniqueWorkName' ($workerClassName)")
                val clazz = Class.forName(workerClassName).asSubclass(ListenableWorker::class.java)
                val constructor = clazz.getConstructor(Context::class.java, WorkerParameters::class.java)
                val params = WorkerParameters(
                    id = workId,
                    inputData = inputData,
                    tags = setOf(uniqueWorkName, workerClassName)
                )
                val worker = constructor.newInstance(context, params)
                val result = when (worker) {
                    is CoroutineWorker -> worker.doWork()
                    is Worker -> worker.doWork()
                    else -> ListenableWorker.Result.success()
                }
                println("WorkManager: Finished work '$uniqueWorkName' with result $result")
                val state = when (result) {
                    is ListenableWorker.Result.Success -> WorkInfo.State.SUCCEEDED
                    is ListenableWorker.Result.Failure -> WorkInfo.State.FAILED
                    is ListenableWorker.Result.Retry -> WorkInfo.State.ENQUEUED
                }
                updateWorkInfoState(uniqueWorkName, workId, state)

                // update lastRunMillis in prefs
                val saved = prefs.getString("periodic_$uniqueWorkName", null)
                if (saved != null) {
                    val parts = saved.split(";")
                    if (parts.size >= 2) {
                        prefs.edit().putString("periodic_$uniqueWorkName", "${parts[0]};${parts[1]};${System.currentTimeMillis()}").apply()
                    }
                }
            } catch (e: Throwable) {
                System.err.println("WorkManager: Error executing work '$uniqueWorkName': $e")
                e.printStackTrace()
                updateWorkInfoState(uniqueWorkName, workId, WorkInfo.State.FAILED)
            }
        }
    }

    private fun updateWorkInfoState(uniqueWorkName: String, workId: UUID, state: WorkInfo.State) {
        val info = WorkInfo(workId, state, tags = setOf(uniqueWorkName))
        workInfoMap[workId] = info
        val liveData = workInfos.getOrPut(uniqueWorkName) { MutableLiveData(emptyList()) }
        liveData.postValue(listOf(info))
    }

    override fun enqueue(workRequest: WorkRequest): Operation {
        val name = workRequest.workerClass.simpleName
        updateWorkInfoState(name, workRequest.id, WorkInfo.State.ENQUEUED)
        scheduler.schedule({
            runWorker(name, workRequest.workerClass.name, workRequest.id, workRequest.inputData)
        }, workRequest.initialDelayMillis, TimeUnit.MILLISECONDS)
        return Operation.SUCCESS
    }

    override fun enqueue(workRequests: List<WorkRequest>): Operation {
        for (req in workRequests) enqueue(req)
        return Operation.SUCCESS
    }

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        work: OneTimeWorkRequest
    ): Operation {
        if (existingWorkPolicy == ExistingWorkPolicy.KEEP && scheduledTasks.containsKey(uniqueWorkName)) {
            return Operation.SUCCESS
        }
        scheduledTasks.remove(uniqueWorkName)?.cancel(false)
        updateWorkInfoState(uniqueWorkName, work.id, WorkInfo.State.ENQUEUED)
        val future = scheduler.schedule({
            runWorker(uniqueWorkName, work.workerClass.name, work.id, work.inputData)
        }, work.initialDelayMillis, TimeUnit.MILLISECONDS)
        scheduledTasks[uniqueWorkName] = future
        return Operation.SUCCESS
    }

    override fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        work: List<OneTimeWorkRequest>
    ): Operation {
        work.firstOrNull()?.let { enqueueUniqueWork(uniqueWorkName, existingWorkPolicy, it) }
        return Operation.SUCCESS
    }

    override fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy,
        periodicWork: PeriodicWorkRequest
    ): Operation {
        val workerClassName = periodicWork.workerClass.name
        val intervalMillis = periodicWork.repeatIntervalMillis
        val key = "periodic_$uniqueWorkName"

        if (existingPeriodicWorkPolicy == ExistingPeriodicWorkPolicy.KEEP && prefs.contains(key)) {
            return Operation.SUCCESS
        }

        val now = System.currentTimeMillis()
        prefs.edit().putString(key, "$workerClassName;$intervalMillis;$now").apply()
        updateWorkInfoState(uniqueWorkName, periodicWork.id, WorkInfo.State.ENQUEUED)
        schedulePeriodic(uniqueWorkName, workerClassName, intervalMillis, periodicWork.initialDelayMillis)
        println("WorkManager: Enqueued periodic work '$uniqueWorkName' ($workerClassName) every ${intervalMillis / 1000}s")
        return Operation.SUCCESS
    }

    override fun cancelUniqueWork(uniqueWorkName: String): Operation {
        scheduledTasks.remove(uniqueWorkName)?.cancel(false)
        prefs.edit().remove("periodic_$uniqueWorkName").apply()
        println("WorkManager: Cancelled unique work '$uniqueWorkName'")
        return Operation.SUCCESS
    }

    override fun cancelAllWorkByTag(tag: String): Operation {
        cancelUniqueWork(tag)
        return Operation.SUCCESS
    }

    override fun cancelWorkById(id: UUID): Operation {
        for ((name, liveData) in workInfos) {
            val list = liveData.value ?: continue
            if (list.any { it.id == id }) {
                cancelUniqueWork(name)
            }
        }
        return Operation.SUCCESS
    }

    override fun cancelAllWork(): Operation {
        for (task in scheduledTasks.values) task.cancel(false)
        scheduledTasks.clear()
        prefs.edit().clear().apply()
        return Operation.SUCCESS
    }

    override fun getWorkInfosForUniqueWorkLiveData(uniqueWorkName: String): LiveData<List<WorkInfo>> {
        return workInfos.getOrPut(uniqueWorkName) { MutableLiveData(emptyList()) }
    }

    override fun getWorkInfosByTagLiveData(tag: String): LiveData<List<WorkInfo>> {
        return getWorkInfosForUniqueWorkLiveData(tag)
    }

    override fun getWorkInfoByIdLiveData(id: UUID): LiveData<WorkInfo?> {
        val info = workInfoMap[id] ?: run {
            for (liveData in workInfos.values) {
                val item = liveData.value?.firstOrNull { it.id == id }
                if (item != null) return@run item
            }
            null
        }
        return MutableLiveData(info)
    }
}
