package com.hamraheteyf.app.di

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Provides the [AppContainer] to the Compose tree. Set once in MainActivity. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided. Wrap content in CompositionLocalProvider(LocalAppContainer provides ...).")
}

/**
 * Minimal factory helper so a composable can build a ViewModel with explicit constructor
 * arguments (e.g. a navigation id) without annotation-processing frameworks.
 */
@Suppress("UNCHECKED_CAST")
fun <VM : ViewModel> appViewModelFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
