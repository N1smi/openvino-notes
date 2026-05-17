package com.itlab.data.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.itlab.data.cloud.AuthManager
import com.itlab.domain.cloud.SyncManager
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncManager: SyncManager,
    private val authManager: AuthManager,
) : CoroutineWorker(context, params) {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val userId =
            inputData.getString("USER_ID")
                ?: authManager.getCurrentUserId()
                ?: run {
                    Timber.e("Sync failed: User is not authorized")
                    return Result.failure()
                }
        return try {
            Timber.d("Starting sync for user: $userId")
            syncManager.sync(userId)

            Timber.d("Sync completed successfully")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Timber.e(e, "Sync retryable error: %s", e.message)
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "Sync fatal error: %s", e.message)
            Result.failure()
        }
    }
}
