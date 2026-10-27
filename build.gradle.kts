// Top-level build file. Plugins are declared here (apply false) and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // On the classpath so the app module can conditionally apply it when google-services.json
    // is present. Not applied at the root.
    alias(libs.plugins.google.services) apply false
}
