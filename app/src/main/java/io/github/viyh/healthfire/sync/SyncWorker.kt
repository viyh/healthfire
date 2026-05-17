package io.github.viyh.healthfire.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.viyh.healthfire.HealthfireApp

/**
 * Runs one [SyncEngine] sync. WorkManager invokes this for manual "sync now"
 * requests and, when the user enables it, on the recurring schedule; see
 * [SyncScheduler].
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker: started")
        val container = (applicationContext as HealthfireApp).container

        // The first sync backfills years of history and can run long; promote
        // it to a foreground service so the OS keeps it alive. Routine
        // incremental syncs are short and stay as silent background work.
        if (container.syncStateStore.load().changesToken == null) {
            runCatching {
                setForeground(SyncNotifications.backfillForegroundInfo(applicationContext))
            }.onFailure { Log.w(TAG, "SyncWorker: setForeground failed", it) }
        }

        val result = container.syncEngine.sync()
        Log.i(TAG, "SyncWorker: finished - $result")
        return when (result) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure -> if (result.retryable) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val TAG = "Healthfire"
    }
}
