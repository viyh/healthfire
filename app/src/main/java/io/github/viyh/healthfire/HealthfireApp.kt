package io.github.viyh.healthfire

import android.app.Application
import android.util.Log
import io.github.viyh.healthfire.firebase.FirebaseRuntime
import io.github.viyh.healthfire.sync.SyncNotifications
import io.github.viyh.healthfire.sync.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application entry point. Owns the [AppContainer] of app-scoped dependencies
 * and brings up Firebase from a previously imported config. Sync is never
 * started from here - it runs only on an explicit request, or on the
 * background schedule once the user turns that on.
 */
class HealthfireApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        initializeFirebaseIfConfigured()
        SyncNotifications.ensureChannel(this)
        reconcileAutoSync()
    }

    /** Initializes Firebase from a previously imported config, if there is one. */
    private fun initializeFirebaseIfConfigured() {
        val config = runBlocking { container.firebaseConfigStore.load() } ?: return
        runCatching { FirebaseRuntime.initialize(this, config) }
            .onFailure { Log.e("Healthfire", "Firebase initialization failed", it) }
    }

    /**
     * Brings WorkManager's recurring sync in line with the saved preference, so
     * a schedule left over from a previous install does not run unbidden.
     */
    private fun reconcileAutoSync() {
        val settings = container.syncSettingsStore
        val enabled = runBlocking { settings.autoSyncEnabled.first() }
        if (enabled) {
            val hours = runBlocking { settings.syncIntervalHours.first() }
            val metered = runBlocking { settings.syncOnMetered.first() }
            SyncScheduler.enablePeriodicSync(this, hours.toLong(), metered)
        } else {
            SyncScheduler.disablePeriodicSync(this)
        }
    }
}
