package io.github.viyh.healthfire

import android.app.Application
import android.util.Log
import io.github.viyh.healthfire.firebase.FirebaseRuntime
import io.github.viyh.healthfire.sync.SyncNotifications
import io.github.viyh.healthfire.sync.SyncScheduler
import kotlinx.coroutines.runBlocking

/**
 * Application entry point. Owns the [AppContainer] of app-scoped dependencies,
 * brings up Firebase from a previously imported config, and schedules the
 * recurring background sync.
 */
class HealthfireApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        initializeFirebaseIfConfigured()
        SyncNotifications.ensureChannel(this)
        SyncScheduler.ensurePeriodicSync(this)
    }

    /** Initializes Firebase from a previously imported config, if there is one. */
    private fun initializeFirebaseIfConfigured() {
        val config = runBlocking { container.firebaseConfigStore.load() } ?: return
        runCatching { FirebaseRuntime.initialize(this, config) }
            .onFailure { Log.e("Healthfire", "Firebase initialization failed", it) }
    }
}
