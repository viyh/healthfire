package io.github.viyh.healthfire.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sync_settings")
private val AUTO_SYNC = booleanPreferencesKey("auto_sync")

/** Persists the user's sync preferences in app-private storage. */
class SyncSettingsStore(private val context: Context) {

    /** Whether the recurring background sync is enabled. Off until turned on. */
    val autoSyncEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[AUTO_SYNC] ?: false }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_SYNC] = enabled }
    }
}
