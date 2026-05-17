package io.github.viyh.healthfire.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sync_settings")
private val AUTO_SYNC = booleanPreferencesKey("auto_sync")
private val SYNC_INTERVAL = intPreferencesKey("sync_interval_hours")
private val SYNC_ON_METERED = booleanPreferencesKey("sync_on_metered")

/** Persists the user's sync preferences in app-private storage. */
class SyncSettingsStore(private val context: Context) {

    /** Whether the recurring background sync is enabled. Off until turned on. */
    val autoSyncEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[AUTO_SYNC] ?: false }

    /** How often the recurring background sync runs, in hours. */
    val syncIntervalHours: Flow<Int> =
        context.settingsDataStore.data.map { it[SYNC_INTERVAL] ?: DEFAULT_INTERVAL_HOURS }

    /** Whether background syncs may run over a metered (mobile-data) network. */
    val syncOnMetered: Flow<Boolean> =
        context.settingsDataStore.data.map { it[SYNC_ON_METERED] ?: false }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_SYNC] = enabled }
    }

    suspend fun setSyncIntervalHours(hours: Int) {
        context.settingsDataStore.edit { it[SYNC_INTERVAL] = hours }
    }

    suspend fun setSyncOnMetered(enabled: Boolean) {
        context.settingsDataStore.edit { it[SYNC_ON_METERED] = enabled }
    }

    companion object {
        /** Default background-sync interval, in hours. */
        const val DEFAULT_INTERVAL_HOURS: Int = 6

        /** Background-sync interval choices offered in the UI, in hours. */
        val INTERVAL_CHOICES: List<Int> = listOf(1, 3, 6, 12, 24)
    }
}
