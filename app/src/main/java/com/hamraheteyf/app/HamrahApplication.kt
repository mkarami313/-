package com.hamraheteyf.app

import android.app.Application
import com.hamraheteyf.app.di.AppContainer
import com.hamraheteyf.app.di.createAppContainer

/** Holds the process-wide [AppContainer] so repositories (and the demo store) survive config changes. */
class HamrahApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createAppContainer(this)
    }
}
