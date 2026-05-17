package io.github.viyh.healthfire.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in via Credential Manager into Firebase Auth. The resulting
 * Firebase uid is the `person_uid` stamped on every exported record.
 */
class AuthManager(private val configStore: FirebaseConfigStore) {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** The signed-in user's Firebase uid, or null if not signed in. */
    val uid: String?
        get() = runCatching { auth.currentUser?.uid }.getOrNull()

    /** Whether a user is currently signed in. */
    val isSignedIn: Boolean get() = uid != null

    /**
     * Runs the Google sign-in flow and authenticates with Firebase. Returns the
     * signed-in user's uid, or a failure with a message suitable for the UI.
     */
    suspend fun signIn(activity: Activity): Result<String> = runCatching {
        val webClientId = configStore.load()?.webClientId
            ?: error(
                "No Google web client ID. Import a google-services.json from a " +
                    "Firebase project with Google sign-in enabled.",
            )
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build(),
            )
            .build()
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Unexpected credential type: ${credential.type}")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(firebaseCredential).await()
        result.user?.uid ?: error("Firebase sign-in returned no user.")
    }

    /** Signs out of Firebase and clears the Credential Manager state. */
    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
