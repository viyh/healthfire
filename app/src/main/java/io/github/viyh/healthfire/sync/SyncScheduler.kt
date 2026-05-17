package io.github.viyh.healthfire.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules Health Connect sync work: a recurring background sync and an
 * on-demand "sync now". All work is named and unique, so re-scheduling or
 * double-tapping never stacks duplicate runs.
 */
object SyncScheduler {

    private const val PERIODIC_WORK = "healthfire-periodic-sync"
    private const val MANUAL_WORK = "healthfire-manual-sync"
    private const val SYNC_INTERVAL_HOURS = 6L

    /** Recurring sync: unmetered network only, and not while the battery is low. */
    private val periodicConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    /** Manual sync: the user asked for it now, so only require connectivity. */
    private val manualConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Ensures the recurring sync is scheduled. Idempotent across app starts. */
    fun ensurePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_HOURS, TimeUnit.HOURS,
        ).setConstraints(periodicConstraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Queues an immediate sync; a no-op if one is already pending or running. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(manualConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Cancels all scheduled sync work. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK)
            cancelUniqueWork(MANUAL_WORK)
        }
    }
}
