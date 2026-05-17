package io.github.viyh.healthfire.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import io.github.viyh.healthfire.R

/**
 * The notification channel and foreground-service notification used while a
 * sync runs. Only the long initial backfill runs in the foreground; routine
 * incremental syncs are silent background work.
 */
object SyncNotifications {

    private const val CHANNEL_ID = "sync"
    private const val BACKFILL_NOTIFICATION_ID = 1

    /** Registers the sync notification channel. Safe to call repeatedly. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sync_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.sync_channel_description) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** Foreground-service notification shown while the initial backfill runs. */
    fun backfillForegroundInfo(context: Context): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.sync_backfill_title))
            .setContentText(context.getString(R.string.sync_backfill_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            BACKFILL_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}
