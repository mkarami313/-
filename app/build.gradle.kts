plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The real Firebase backend activates automatically when app/google-services.json is present
// (CI writes it from the GOOGLE_SERVICES_JSON secret). Without the file, the app builds in
// DEMO mode with no backend, exactly as before.
val useFirebaseBackend = file("google-services.json").exists()

android {
    namespace = "com.hamraheteyf.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hamraheteyf.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Active backend: FIREBASE when google-services.json is present, else DEMO (in-memory).
        buildConfigField("String", "BACKEND", if (useFirebaseBackend) "\"FIREBASE\"" else "\"DEMO\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Demo build is unsigned; see docs/BUILD_APK.md for signing a real release.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Firebase SDK + coroutines bridge. Always on the classpath so the Firebase backend code
    // compiles; it is only initialized/used at runtime when BACKEND == "FIREBASE".
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
}

// Apply the google-services plugin only when the config file exists, so the DEMO build
// (no google-services.json) keeps working without it.
if (useFirebaseBackend) {
    apply(plugin = "com.google.gms.google-services")
}
