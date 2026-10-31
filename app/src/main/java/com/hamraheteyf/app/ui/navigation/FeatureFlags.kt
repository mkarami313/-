package com.hamraheteyf.app.ui.navigation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Modules the server has switched off. Persisted so the last known state applies offline. */
object FeatureFlags {
    private val _disabled = MutableStateFlow<Set<String>>(emptySet())
    val disabled: StateFlow<Set<String>> get() = _disabled

    fun load(context: Context) {
        val saved = context.getSharedPreferences("hamrah_features", Context.MODE_PRIVATE).getStringSet("disabled", emptySet())
        _disabled.value = saved?.toSet().orEmpty()
    }

    fun update(context: Context, ids: Set<String>) {
        if (ids == _disabled.value) return
        _disabled.value = ids
        context.getSharedPreferences("hamrah_features", Context.MODE_PRIVATE).edit().putStringSet("disabled", ids).apply()
    }
}
