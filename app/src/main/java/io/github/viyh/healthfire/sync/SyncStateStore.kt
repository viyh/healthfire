package io.github.viyh.healthfire.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * State carried between syncs. A null [changesToken] means the next sync is a
 * full backfill; afterwards the token drives incremental syncs.
 */
@Serializable
data class SyncState(
    val changesToken: String? = null,
    val lastSyncAt: String? = null,
    val backfillComplete: Boolean = false,
)

private val Context.syncDataStore by preferencesDataStore(name = "sync_state")
private val SYNC_STATE = stringPreferencesKey("sync_state")

/** Persists [SyncState] in app-private storage. */
class SyncStateStore(private val context: Context) {

    /** The stored state, or a fresh [SyncState] if nothing has been saved. */
    suspend fun load(): SyncState {
        val stored = context.syncDataStore.data.first()[SYNC_STATE] ?: return SyncState()
        return runCatching { Json.decodeFromString<SyncState>(stored) }.getOrDefault(SyncState())
    }

    suspend fun save(state: SyncState) {
        context.syncDataStore.edit { it[SYNC_STATE] = Json.encodeToString(state) }
    }
}
