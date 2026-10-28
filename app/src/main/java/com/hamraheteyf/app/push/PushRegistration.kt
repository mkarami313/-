package com.hamraheteyf.app.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.hamraheteyf.app.data.repository.server.argsJson
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Registers this device's FCM token with our server so pushes can be routed to it.
 * All calls are best-effort: on a device without Google Play Services the token lookup
 * fails and we simply fall back to the in-app/poll channel.
 */
object PushRegistration {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called after sign-in. Fetches the FCM token and registers it (fire-and-forget). */
    fun register(api: HamrahApi) {
        scope.launch { runCatching { registerNow(api) } }
    }

    /** Called on sign-out (suspending, so the caller can clear the session afterwards). */
    suspend fun unregister(api: HamrahApi) {
        if (api.token == null) return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            api.action("unregisterPush", argsJson("token" to token))
        }
    }

    /** Called by FCM when the token rotates. */
    fun onNewToken(context: Context, token: String) {
        scope.launch {
            runCatching {
                val api = HamrahApi(context)
                if (api.token != null) {
                    api.action("registerPush", argsJson("token" to token, "platform" to "android"))
                }
            }
        }
    }

    private suspend fun registerNow(api: HamrahApi) {
        if (api.token == null) return
        val token = FirebaseMessaging.getInstance().token.await()
        api.action("registerPush", argsJson("token" to token, "platform" to "android"))
    }
}
