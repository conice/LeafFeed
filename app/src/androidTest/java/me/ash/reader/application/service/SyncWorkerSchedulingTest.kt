package me.ash.reader.application.service

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncWorkerSchedulingTest {
    @Test
    fun oneTimeWorkIsUniquePerAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration =
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(Executor { })
                .setTaskExecutor(SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val workManager = WorkManager.getInstance(context)

        val first =
            SyncWorker.enqueueOneTimeWork(workManager, workDataOf("accountId" to 7))
                .workInfoFlow(workManager)
                .first()
        val duplicate =
            SyncWorker.enqueueOneTimeWork(workManager, workDataOf("accountId" to 7))
                .workInfoFlow(workManager)
                .first()
        val otherAccount =
            SyncWorker.enqueueOneTimeWork(workManager, workDataOf("accountId" to 8))
                .workInfoFlow(workManager)
                .first()

        assertEquals(first.id, duplicate.id)
        assertNotEquals(first.id, otherAccount.id)
    }
}
