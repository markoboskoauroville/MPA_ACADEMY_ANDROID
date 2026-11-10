plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val appVersion = (project.findProperty("appVersion") as String? ?: "1").trim().toInt()
android {
    namespace = "com.mantra.mpaacademy"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mantra.mpaacademy"
        minSdk = 30
        targetSdk = 35
        versionCode = appVersion
        versionName = appVersion.toString()
    }
    System.getenv("DEBUG_KEYSTORE")?.let { ks ->
        signingConfigs.getByName("debug") {
            storeFile = file(ks); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
