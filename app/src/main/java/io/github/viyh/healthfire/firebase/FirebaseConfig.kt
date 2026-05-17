package io.github.viyh.healthfire.firebase

import com.google.firebase.FirebaseOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Raised when an imported google-services.json cannot be used. */
class FirebaseConfigException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * The part of a Firebase google-services.json that healthfire needs to start
 * Firebase at runtime. Imported on first run and never baked into the build,
 * so one APK works against anyone's own Firebase project.
 */
@Serializable
data class FirebaseConfig(
    val projectId: String,
    val projectNumber: String,
    val applicationId: String,
    val apiKey: String,
    val storageBucket: String,
    val webClientId: String?,
) {
    /** Options for `FirebaseApp.initializeApp`. */
    fun toFirebaseOptions(): FirebaseOptions =
        FirebaseOptions.Builder()
            .setApplicationId(applicationId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .setGcmSenderId(projectNumber)
            .setStorageBucket(storageBucket)
            .build()

    companion object {
        /**
         * Extracts the config for the Android client matching [packageName].
         *
         * @throws FirebaseConfigException if the file is malformed or contains
         *   no Android client for [packageName].
         */
        fun parse(googleServicesJson: String, packageName: String): FirebaseConfig = try {
            val root = Json.parseToJsonElement(googleServicesJson).jsonObject
            val projectInfo = root.child("project_info")
            val client = root.children("client")
                .firstOrNull { it.androidPackageName() == packageName }
                ?: throw FirebaseConfigException(
                    "google-services.json has no Android app for '$packageName'. Add an " +
                        "Android app with that package name to your Firebase project.",
                )
            FirebaseConfig(
                projectId = projectInfo.requireString("project_id"),
                projectNumber = projectInfo.requireString("project_number"),
                applicationId = client.child("client_info").requireString("mobilesdk_app_id"),
                apiKey = client.children("api_key").firstOrNull()?.optString("current_key")
                    ?: throw FirebaseConfigException("google-services.json has no api_key."),
                storageBucket = projectInfo.requireString("storage_bucket"),
                webClientId = client.webOauthClientId(),
            )
        } catch (e: FirebaseConfigException) {
            throw e
        } catch (e: Exception) {
            throw FirebaseConfigException("Could not read google-services.json.", e)
        }
    }
}

private fun JsonObject.androidPackageName(): String? {
    val clientInfo = this["client_info"] as? JsonObject ?: return null
    val androidInfo = clientInfo["android_client_info"] as? JsonObject ?: return null
    return androidInfo.optString("package_name")
}

private fun JsonObject.webOauthClientId(): String? =
    (this["oauth_client"] as? JsonArray)
        ?.map { it.jsonObject }
        ?.firstOrNull { it["client_type"]?.jsonPrimitive?.intOrNull == 3 }
        ?.optString("client_id")

private fun JsonObject.child(key: String): JsonObject =
    this[key]?.jsonObject
        ?: throw FirebaseConfigException("google-services.json is missing '$key'.")

private fun JsonObject.children(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.map { it.jsonObject } ?: emptyList()

private fun JsonObject.requireString(key: String): String =
    optString(key) ?: throw FirebaseConfigException("google-services.json is missing '$key'.")

private fun JsonObject.optString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull
