package com.itlab.data.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.itlab.domain.cloud.SyncManager
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.IOException

// Используем Robolectric, чтобы предоставить воркеру реальный Context
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {
    private lateinit var context: Context
    private val syncManager = mockk<SyncManager>()
    private val authManager = mockk<AuthManager>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        Timber.plant(
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    // Пакетная заглушка для тестов: логи не нужны
                }
            },
        )
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        clearAllMocks()
    }

    @Test
    fun `doWork should return success when sync is successful`() =
        runBlocking {
            val userId = "user_1"
            every { authManager.getCurrentUserId() } returns userId
            coEvery { syncManager.sync(userId) } just Runs

            // Создаем воркер через официальный билдер
            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(
                        object : androidx.work.WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: androidx.work.WorkerParameters,
                            ) = SyncWorker(appContext, workerParameters, syncManager, authManager)
                        },
                    ).build()

            val result = worker.doWork()

            assertEquals(Result.success(), result)
        }

    @Test
    fun `doWork should return failure when user is not authorized`() =
        runBlocking {
            every { authManager.getCurrentUserId() } returns null

            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(
                        object : androidx.work.WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: androidx.work.WorkerParameters,
                            ) = SyncWorker(appContext, workerParameters, syncManager, authManager)
                        },
                    ).build()

            val result = worker.doWork()

            assertEquals(Result.failure(), result)
        }

    @Test
    fun `doWork should return retry when IOException occurs`() =
        runBlocking {
            val userId = "user_1"
            every { authManager.getCurrentUserId() } returns userId
            coEvery { syncManager.sync(userId) } throws IOException("No network")

            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(
                        object : androidx.work.WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: androidx.work.WorkerParameters,
                            ) = SyncWorker(appContext, workerParameters, syncManager, authManager)
                        },
                    ).build()

            val result = worker.doWork()

            assertEquals(Result.retry(), result)
        }

    @Test
    fun `doWork should return failure on generic exception`() =
        runBlocking {
            val userId = "user_1"
            every { authManager.getCurrentUserId() } returns userId
            coEvery { syncManager.sync(userId) } throws RuntimeException("Fatal")

            val worker =
                TestListenableWorkerBuilder<SyncWorker>(context)
                    .setWorkerFactory(
                        object : androidx.work.WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: androidx.work.WorkerParameters,
                            ) = SyncWorker(appContext, workerParameters, syncManager, authManager)
                        },
                    ).build()

            val result = worker.doWork()

            assertEquals(Result.failure(), result)
        }
}
