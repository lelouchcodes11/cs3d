package androidx.work

import android.content.Context
import com.lagradost.desktop.runtime.AndroidRuntime
import com.lagradost.desktop.runtime.ContextImpl
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestSyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        executedLatch.countDown()
        return Result.success(workDataOf("result_key" to "success_value"))
    }

    companion object {
        val executedLatch = CountDownLatch(1)
    }
}

class WorkManagerTest {
    @BeforeTest
    fun setUp() {
        val tempDir = Files.createTempDirectory("wm-test").toFile()
        AndroidRuntime.init(tempDir)
    }

    @Test
    fun testWorkManagerOneTimeAndPeriodic() {
        val context = ContextImpl.create()
        val wm = WorkManager.getInstance(context)

        // 1. Test OneTimeWorkRequest
        val oneTime = OneTimeWorkRequest.Builder(TestSyncWorker::class.java)
            .setInputData(workDataOf("input_key" to "input_value"))
            .build()

        wm.enqueue(oneTime)
        assertTrue(TestSyncWorker.executedLatch.await(5, TimeUnit.SECONDS), "Worker did not execute in time")

        // 2. Test PeriodicWorkRequest enqueueUniquePeriodicWork
        val periodic = PeriodicWorkRequest.Builder(TestSyncWorker::class.java, 6, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(
            "unique_test_work",
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        // 3. Verify persistence in SharedPreferences
        val prefs = context.getSharedPreferences("androidx.work.workmanager", Context.MODE_PRIVATE)
        val record = prefs.getString("periodic_unique_test_work", null)
        assertNotNull(record, "Periodic work record should be persisted in SharedPreferences")
        assertTrue(record.contains("TestSyncWorker"), "Periodic record should contain worker class name")

        // 4. Verify WorkInfo state
        val workInfo = wm.getWorkInfoByIdLiveData(periodic.id).value
        assertNotNull(workInfo)
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
    }
}
