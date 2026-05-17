package io.github.viyh.healthfire

import android.content.Context
import io.github.viyh.healthfire.firebase.AuthManager
import io.github.viyh.healthfire.firebase.FirebaseConfigStore
import io.github.viyh.healthfire.firebase.StorageUploader
import io.github.viyh.healthfire.hc.HealthConnectGateway
import io.github.viyh.healthfire.sync.SyncEngine
import io.github.viyh.healthfire.sync.SyncMetricsStore
import io.github.viyh.healthfire.sync.SyncSettingsStore
import io.github.viyh.healthfire.sync.SyncStateStore

/**
 * Manual dependency container holding the app-scoped singletons. Created once
 * by [HealthfireApp]. Deliberately a plain class - no DI framework.
 */
class AppContainer(context: Context) {
    val healthConnectGateway: HealthConnectGateway = HealthConnectGateway(context)
    val firebaseConfigStore: FirebaseConfigStore = FirebaseConfigStore(context)
    val authManager: AuthManager = AuthManager(firebaseConfigStore)
    val storageUploader: StorageUploader = StorageUploader()
    val syncStateStore: SyncStateStore = SyncStateStore(context)
    val syncMetricsStore: SyncMetricsStore = SyncMetricsStore(context)
    val syncSettingsStore: SyncSettingsStore = SyncSettingsStore(context)
    val syncEngine: SyncEngine = SyncEngine(
        gateway = healthConnectGateway,
        authManager = authManager,
        uploader = storageUploader,
        stateStore = syncStateStore,
        metricsStore = syncMetricsStore,
        appVersion = "${BuildConfig.VERSION_NAME}+${BuildConfig.GIT_REVISION}",
    )
}
