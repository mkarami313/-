// Top-level build file. Plugins are declared here (apply false) and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // On the classpath so the app module can apply it when google-services.json is present
    // (for FCM push only — app data lives on our own server, not Firebase).
    alias(libs.plugins.google.services) apply false
}
