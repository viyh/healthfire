package io.github.viyh.healthfire.firebase

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "healthfire")
private val FIREBASE_CONFIG = stringPreferencesKey("firebase_config")

/**
 * Persists the imported [FirebaseConfig] in app-private storage. The config is
 * the user's own Firebase project details and stays on the device.
 */
class FirebaseConfigStore(private val context: Context) {

    /** Stores [config], replacing any previous one. */
    suspend fun save(config: FirebaseConfig) {
        context.dataStore.edit { it[FIREBASE_CONFIG] = Json.encodeToString(config) }
    }

    /** The stored config, or null if none has been imported yet. */
    suspend fun load(): FirebaseConfig? {
        val stored = context.dataStore.data.first()[FIREBASE_CONFIG] ?: return null
        return runCatching { Json.decodeFromString<FirebaseConfig>(stored) }.getOrNull()
    }

    /** Removes the stored config, e.g. when resetting first-run setup. */
    suspend fun clear() {
        context.dataStore.edit { it.remove(FIREBASE_CONFIG) }
    }
}
