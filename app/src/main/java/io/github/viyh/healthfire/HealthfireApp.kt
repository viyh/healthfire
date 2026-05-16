package io.github.viyh.healthfire

import android.app.Application

/**
 * Application entry point. Owns the [AppContainer] of app-scoped dependencies.
 * Runtime Firebase initialization and WorkManager setup are wired in by later
 * milestones.
 */
class HealthfireApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
