package com.hamraheteyf.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide "is any activity visible" signal (no extra dependency). A short debounce keeps
 * rotation and activity hand-offs from looking like a trip to the background.
 */
object AppForeground {
    private const val DEBOUNCE_MS = 700L

    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> get() = _isForeground

    private val backgroundListeners = CopyOnWriteArrayList<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var started = 0
    private var installed = false
    private val goBackground = Runnable {
        if (started == 0 && _isForeground.value) {
            _isForeground.value = false
            backgroundListeners.forEach { runCatching { it() } }
        }
    }

    @Synchronized
    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                handler.removeCallbacks(goBackground)
                _isForeground.value = true
            }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) handler.postDelayed(goBackground, DEBOUNCE_MS)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Registers [cb] to run when the app goes to the background; returns a remover. */
    fun addOnBackground(cb: () -> Unit): () -> Unit {
        backgroundListeners.add(cb)
        return { backgroundListeners.remove(cb) }
    }
}
