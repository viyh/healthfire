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
 * State carried between syncs. A null [changesToken] means the backfill has
 * not finished yet; once set, the token drives incremental syncs.
 *
 * While a backfill runs, [backfillToken] holds the changes token taken at its
 * start and [backfillDoneTypes] lists the record types already exported, so an
 * interrupted backfill resumes instead of restarting. Both reset, and
 * [changesToken] is set, only when the backfill completes.
 */
@Serializable
data class SyncState(
    val changesToken: String? = null,
    val lastSyncAt: String? = null,
    val backfillComplete: Boolean = false,
    val backfillToken: String? = null,
    val backfillDoneTypes: List<String> = emptyList(),
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

    /** Clears all sync state so the next sync is a fresh full backfill. */
    suspend fun clear() {
        context.syncDataStore.edit { it.remove(SYNC_STATE) }
    }
}
