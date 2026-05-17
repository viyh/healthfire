package io.github.viyh.healthfire

import android.content.Context
import io.github.viyh.healthfire.firebase.AuthManager
import io.github.viyh.healthfire.firebase.FirebaseConfigStore
import io.github.viyh.healthfire.hc.HealthConnectGateway

/**
 * Manual dependency container holding the app-scoped singletons. Created once
 * by [HealthfireApp]. Deliberately a plain class - no DI framework.
 */
class AppContainer(context: Context) {
    val healthConnectGateway: HealthConnectGateway = HealthConnectGateway(context)
    val firebaseConfigStore: FirebaseConfigStore = FirebaseConfigStore(context)
    val authManager: AuthManager = AuthManager(firebaseConfigStore)
}
