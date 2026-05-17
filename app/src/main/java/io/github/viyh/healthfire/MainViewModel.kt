package io.github.viyh.healthfire

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.viyh.healthfire.firebase.FirebaseConfig
import io.github.viyh.healthfire.firebase.FirebaseRuntime
import io.github.viyh.healthfire.hc.HcAvailability
import io.github.viyh.healthfire.hc.RecordTypes
import io.github.viyh.healthfire.sync.MetricsLog
import io.github.viyh.healthfire.sync.SyncProgress
import io.github.viyh.healthfire.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for [MainActivity]. The same state drives both the first-run setup
 * flow and the home screen; [setupComplete] decides which is shown.
 */
data class MainUiState(
    val loading: Boolean = true,
    val availability: HcAvailability = HcAvailability.NOT_SUPPORTED,
    val configImported: Boolean = false,
    val signedIn: Boolean = false,
    val accountEmail: String? = null,
    val grantedTypeCount: Int = 0,
    val knownTypeCount: Int = RecordTypes.ALL.size,
    val historyGranted: Boolean = false,
    val backgroundGranted: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val lastSyncAt: String? = null,
    val backfillComplete: Boolean = false,
    val metrics: MetricsLog = MetricsLog(),
    val syncProgress: SyncProgress = SyncProgress.Idle,
    val autoSyncEnabled: Boolean = false,
) {
    val healthConnectReady: Boolean get() = availability == HcAvailability.AVAILABLE
    val permissionsGranted: Boolean get() = grantedTypeCount > 0
    val setupComplete: Boolean
        get() = healthConnectReady && configImported && signedIn && permissionsGranted
}

/**
 * Drives [MainActivity]: derives setup progress and home-screen status from the
 * app's stores and Health Connect, and runs the first-run setup actions.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HealthfireApp
    private val container get() = app.container

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** The Health Connect permission set to hand to the permission request. */
    val permissionsToRequest: Set<String> = container.healthConnectGateway.readPermissions

    init {
        refresh()
        observeSyncProgress()
        observeAutoSync()
    }

    /** Mirrors the sync engine's live progress into the UI state. */
    private fun observeSyncProgress() {
        viewModelScope.launch {
            var wasRunning = false
            var typesDone = 0
            container.syncEngine.progress.collect { progress ->
                _uiState.update { it.copy(syncProgress = progress) }
                val running = progress is SyncProgress.Running
                // When a sync finishes, pull the fresh sync state and metrics.
                if (wasRunning && !running) refresh()
                // Each finished record type writes its metrics; reload as it goes.
                val done = (progress as? SyncProgress.Running)?.recordTypesDone ?: 0
                if (done > typesDone) refreshMetrics()
                typesDone = done
                wasRunning = running
            }
        }
    }

    /** Reloads just the export metrics, e.g. as a backfill finishes each type. */
    private fun refreshMetrics() {
        viewModelScope.launch {
            val metrics = runCatching { container.syncMetricsStore.load() }
                .getOrDefault(MetricsLog())
            _uiState.update { it.copy(metrics = metrics) }
        }
    }

    /** Mirrors the saved background-sync preference into the UI state. */
    private fun observeAutoSync() {
        viewModelScope.launch {
            container.syncSettingsStore.autoSyncEnabled.collect { enabled ->
                _uiState.update { it.copy(autoSyncEnabled = enabled) }
            }
        }
    }

    /** Re-derives the whole UI state. Safe to call on every resume. */
    fun refresh() {
        viewModelScope.launch {
            val gateway = container.healthConnectGateway
            val availability = gateway.availability()

            val config = runCatching { container.firebaseConfigStore.load() }.getOrNull()
            if (config != null && !FirebaseRuntime.isReady(app)) {
                runCatching { FirebaseRuntime.initialize(app, config) }
                    .onFailure { Log.e(TAG, "Firebase initialization failed", it) }
            }
            val configImported = config != null && FirebaseRuntime.isReady(app)
            val signedIn = configImported && container.authManager.isSignedIn

            val granted = if (availability == HcAvailability.AVAILABLE) {
                runCatching { gateway.grantedPermissions() }.getOrDefault(emptySet())
            } else {
                emptySet()
            }
            val syncState = runCatching { container.syncStateStore.load() }.getOrNull()
            val metrics = runCatching { container.syncMetricsStore.load() }
                .getOrDefault(MetricsLog())

            _uiState.update {
                it.copy(
                    loading = false,
                    availability = availability,
                    configImported = configImported,
                    signedIn = signedIn,
                    accountEmail = if (configImported) container.authManager.email else null,
                    grantedTypeCount = RecordTypes.ALL.count { type ->
                        HealthPermission.getReadPermission(type) in granted
                    },
                    historyGranted =
                        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted,
                    backgroundGranted =
                        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted,
                    lastSyncAt = syncState?.lastSyncAt,
                    backfillComplete = syncState?.backfillComplete ?: false,
                    metrics = metrics,
                )
            }
        }
    }

    /** Imports a google-services.json from [uri] and brings up Firebase. */
    fun importConfig(uri: Uri?) {
        if (uri == null || _uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            val error = runCatching {
                val json = app.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().decodeToString()
                } ?: error("Could not open the selected file.")
                val config = FirebaseConfig.parse(json, app.packageName)
                container.firebaseConfigStore.save(config)
                FirebaseRuntime.initialize(app, config)
            }.exceptionOrNull()
            _uiState.update { it.copy(busy = false, message = error?.message) }
            refresh()
        }
    }

    /** Runs Google sign-in; [activity] is required by Credential Manager. */
    fun signIn(activity: Activity) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            val error = container.authManager.signIn(activity).exceptionOrNull()
            _uiState.update { it.copy(busy = false, message = error?.message) }
            refresh()
        }
    }

    /** Signs out of Firebase Auth. */
    fun signOut() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            container.authManager.signOut(app)
            _uiState.update { it.copy(busy = false) }
            refresh()
        }
    }

    /** Queues an immediate sync through WorkManager. */
    fun syncNow() {
        if (_uiState.value.syncProgress is SyncProgress.Running) return
        SyncScheduler.syncNow(app)
        // Optimistic running state so the UI reacts on tap; the worker's real
        // progress replaces it once the sync starts a moment later.
        _uiState.update {
            it.copy(syncProgress = SyncProgress.Running(backfill = false), message = null)
        }
    }

    /** Stops the sync in progress. A backfill resumes from its checkpoint. */
    fun stopSync() {
        SyncScheduler.stopSync(app)
        _uiState.update { it.copy(syncProgress = SyncProgress.Idle) }
    }

    /** Turns the recurring background sync on or off. */
    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            container.syncSettingsStore.setAutoSyncEnabled(enabled)
            if (enabled) {
                SyncScheduler.enablePeriodicSync(app)
            } else {
                SyncScheduler.disablePeriodicSync(app)
            }
        }
    }

    /** Clears the sync checkpoint so the next sync re-exports the full history. */
    fun startOver() {
        if (_uiState.value.syncProgress is SyncProgress.Running) return
        viewModelScope.launch {
            container.syncStateStore.clear()
            refresh()
        }
    }

    /** Clears the transient status message. */
    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private companion object {
        const val TAG = "Healthfire"
    }
}
