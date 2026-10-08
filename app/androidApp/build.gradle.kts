import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
// Guest mode builds without Firebase configuration; account UI reports setup is missing.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // System biometric / device-credential prompt for the optional app unlock.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(libs.firebase.firestore)
    implementation(libs.sqldelight.android)
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation("androidx.window:window:1.5.1")

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
}

android {
    namespace = "org.example.stocksteps"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.stocksteps"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        // Backend URL is configuration, not code: -PstockstepsBackendUrl=... (or gradle.properties).
        // Debug defaults to the local server via `adb reverse tcp:8080 tcp:8080`; release has no default.
        val backendUrl = providers.gradleProperty("stockstepsBackendUrl")
        // Local mock backend (`./gradlew :server:runMock`, then `adb reverse tcp:8081 tcp:8081`).
        val mockBackendUrl = providers.gradleProperty("stockstepsMockBackendUrl")
        debug {
            buildConfigField("boolean", "FIREBASE_EMULATORS", providers.gradleProperty("firebaseEmulators").orElse("false").get())
            buildConfigField("String", "BACKEND_URL", "\"${backendUrl.orElse("http://127.0.0.1:8080").get()}\"")
            buildConfigField("String", "MOCK_BACKEND_URL", "\"${mockBackendUrl.orElse("http://127.0.0.1:8081").get()}\"")
        }
        release {
            buildConfigField("boolean", "FIREBASE_EMULATORS", "false")
            buildConfigField("String", "BACKEND_URL", "\"${backendUrl.orElse("").get()}\"")
            // No mock backend in release builds: the Development setting is hidden and data is always real.
            buildConfigField("String", "MOCK_BACKEND_URL", "\"\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}