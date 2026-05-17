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
 * Schedules Health Connect sync work: an on-demand "sync now" and an optional
 * recurring background sync. Nothing is scheduled automatically - the recurring
 * sync runs only after the user turns it on; see [SyncSettingsStore]. Its
 * interval and network policy are user-configurable, so changing either
 * cancels and re-enables.
 */
object SyncScheduler {

    private const val PERIODIC_WORK = "healthfire-periodic-sync"
    private const val MANUAL_WORK = "healthfire-manual-sync"

    /** Manual sync: the user asked for it now, so only require connectivity. */
    private val manualConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Enables the recurring background sync every [intervalHours] hours; the
     * first run is one interval away. With [allowMetered] off the sync waits
     * for unmetered Wi-Fi. Keeps any schedule already in place, so to apply a
     * changed setting the caller disables first (see the view model).
     */
    fun enablePeriodicSync(context: Context, intervalHours: Long, allowMetered: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED,
            )
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(intervalHours, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Disables the recurring background sync. */
    fun disablePeriodicSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
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

    /** Stops the in-progress manual sync. A backfill resumes from its checkpoint. */
    fun stopSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MANUAL_WORK)
    }
}
