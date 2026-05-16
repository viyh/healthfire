plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Short git SHA (plus "-dirty" when the tree has uncommitted changes),
// resolved once per build. Surfaced in the app and used for the export
// envelope's app_version field, so an exported record traces to an exact build.
fun gitRevision(): String {
    fun git(vararg args: String): String = try {
        ProcessBuilder(listOf("git", "-c", "safe.directory=*") + args.toList())
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) {
        ""
    }
    val sha = git("rev-parse", "--short", "HEAD").ifBlank { return "unknown" }
    val dirty = git("status", "--porcelain").isNotBlank()
    return if (dirty) "$sha-dirty" else sha
}

android {
    namespace = "io.github.viyh.healthfire"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.viyh.healthfire"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GIT_REVISION", "\"${gitRevision()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        // Reuse the conventional Android debug keystore for release builds.
        // Fine for personal sideload sharing: same install UX as debug, no new
        // warnings. The keystore is generated locally / in the build container
        // and is never committed. Do NOT use this key for public distribution.
        create("debugShared") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debugShared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)

    // AndroidX core and lifecycle
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
