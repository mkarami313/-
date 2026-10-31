import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is read from a gitignored keystore.properties at the repo root (never
// committed). Without it, the release build is left unsigned (e.g. on CI without the key).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// FCM push is enabled only when google-services.json is present (gitignored). Without it the
// app still builds and runs; only push is unavailable. App DATA always lives on our server.
val useFirebasePush = file("google-services.json").exists()

android {
    namespace = "com.hamraheteyf.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hamraheteyf.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "0.11.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Active backend:
        //   SERVER — the self-hosted API on tizbin (real users, persistent data).
        //   DEMO   — in-memory fake store, no network (used by unit tests / offline preview).
        buildConfigField("String", "BACKEND", "\"SERVER\"")
        buildConfigField("String", "API_BASE_URL", "\"https://proj.tizbin.org/hamrah/api/\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
    // Do not embed the Google Play dependency-metadata blob in the artifact.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Strip build/VCS metadata so the shipped APK carries no git revision,
            // AGP version, or per-library version markers.
            excludes += "/META-INF/version-control-info.textproto"
            excludes += "/META-INF/com/android/build/gradle/app-metadata.properties"
            excludes += "/META-INF/*.version"
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
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)  // override biometric's old transitive fragment (ActivityResult needs >=1.3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Firebase Cloud Messaging — push transport only (no Firebase data/auth).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Apply the google-services plugin only when the config file exists (see useFirebasePush),
// so a build without google-services.json still succeeds (push simply disabled).
if (useFirebasePush) {
    apply(plugin = "com.google.gms.google-services")
}
