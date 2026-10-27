// Top-level build file. Plugins are declared here (apply false) and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Enable only after adding a real google-services.json — see docs/BACKEND_SETUP.md
    // alias(libs.plugins.google.services) apply false
}
