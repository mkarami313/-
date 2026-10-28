package com.hamraheteyf.app

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.hamraheteyf.app.di.AppContainer
import com.hamraheteyf.app.di.createAppContainer

/** Holds the process-wide [AppContainer] so repositories (and the demo store) survive config changes. */
class HamrahApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.BACKEND == "FIREBASE") {
            // Force long polling for Firestore. Firestore's default gRPC/WebChannel transport is
            // often blocked or unstable behind VPNs, proxies and restrictive networks, which makes
            // writes never reach the server (while Auth over plain HTTPS still works). Long polling
            // uses ordinary HTTPS requests and is far more reliable on such networks. Must be set
            // before any Firestore operation.
            runCatching {
                FirebaseFirestore.getInstance().firestoreSettings =
                    FirebaseFirestoreSettings.Builder()
                        .setExperimentalForceLongPolling(true)
                        .build()
            }
        }

        container = createAppContainer()
    }
}
