package io.github.viyh.healthfire.firebase

import android.content.Context
import com.google.firebase.FirebaseApp

/** Initializes the default Firebase app at runtime from an imported config. */
object FirebaseRuntime {

    /** Whether the default Firebase app has been initialized. */
    fun isReady(context: Context): Boolean =
        FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Initializes the default Firebase app from [config]. Idempotent: a no-op
     * if Firebase is already initialized.
     */
    fun initialize(context: Context, config: FirebaseConfig) {
        if (isReady(context)) return
        FirebaseApp.initializeApp(context, config.toFirebaseOptions())
    }
}
