package io.github.viyh.healthfire.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.viyh.healthfire.HealthfireApp

/**
 * Runs one [SyncEngine] sync. WorkManager invokes this on the recurring
 * schedule and for manual "sync now" requests; see [SyncScheduler].
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as HealthfireApp).container

        // The first sync backfills years of history and can run long; promote
        // it to a foreground service so the OS keeps it alive. Routine
        // incremental syncs are short and stay as silent background work.
        if (container.syncStateStore.load().changesToken == null) {
            runCatching {
                setForeground(SyncNotifications.backfillForegroundInfo(applicationContext))
            }
        }

        return when (val result = container.syncEngine.sync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Failure -> if (result.retryable) Result.retry() else Result.failure()
        }
    }
}
