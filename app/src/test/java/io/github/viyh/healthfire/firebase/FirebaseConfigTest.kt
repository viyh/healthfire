package io.github.viyh.healthfire.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

private const val PACKAGE = "io.github.viyh.healthfire"

private val SAMPLE = """
    {
      "project_info": {
        "project_number": "123456789012",
        "project_id": "healthfire-demo",
        "storage_bucket": "healthfire-demo.firebasestorage.app"
      },
      "client": [
        {
          "client_info": {
            "mobilesdk_app_id": "1:123456789012:android:abc123",
            "android_client_info": { "package_name": "io.github.viyh.healthfire" }
          },
          "oauth_client": [
            { "client_id": "web.apps.googleusercontent.com", "client_type": 3 },
            { "client_id": "android.apps.googleusercontent.com", "client_type": 1 }
          ],
          "api_key": [ { "current_key": "AIzaSyExampleKey" } ],
          "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
        }
      ],
      "configuration_version": "1"
    }
""".trimIndent()

class FirebaseConfigTest {

    @Test
    fun parsesAllFields() {
        val config = FirebaseConfig.parse(SAMPLE, PACKAGE)
        assertEquals("healthfire-demo", config.projectId)
        assertEquals("123456789012", config.projectNumber)
        assertEquals("1:123456789012:android:abc123", config.applicationId)
        assertEquals("AIzaSyExampleKey", config.apiKey)
        assertEquals("healthfire-demo.firebasestorage.app", config.storageBucket)
        assertEquals("web.apps.googleusercontent.com", config.webClientId)
    }

    @Test
    fun unknownPackageThrows() {
        assertThrows(FirebaseConfigException::class.java) {
            FirebaseConfig.parse(SAMPLE, "com.other.app")
        }
    }

    @Test
    fun malformedJsonThrows() {
        assertThrows(FirebaseConfigException::class.java) {
            FirebaseConfig.parse("not json at all", PACKAGE)
        }
    }

    @Test
    fun missingWebClientLeavesWebClientIdNull() {
        val noWebClient = SAMPLE.replace("\"client_type\": 3", "\"client_type\": 1")
        assertNull(FirebaseConfig.parse(noWebClient, PACKAGE).webClientId)
    }
}
