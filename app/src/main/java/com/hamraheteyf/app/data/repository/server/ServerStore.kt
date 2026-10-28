package com.hamraheteyf.app.data.repository.server

import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.repository.fake.Db
import com.hamraheteyf.app.data.repository.fake.FakeStore
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.AuthResponse
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val SERVER_NOT_SIGNED_IN = "ابتدا وارد حساب خود شوید."
private const val POLL_INTERVAL_MS = 5000L

/**
 * Keeps a local mirror of the server-scoped snapshot in a [FakeStore] so the existing
 * (reactive, flow-based) read logic in the `fake` package can be reused verbatim for
 * every `observe*`. Mutations never touch the mirror directly — they POST to the API,
 * and the fresh snapshot the server returns replaces the mirror, so the UI updates.
 *
 * A background poller re-reads `/state` every few seconds so changes made by OTHER users
 * (new answers, messages, verification results) also show up without a user action.
 */
class ServerStore(val api: HamrahApi) {

    /** The mirror. Seeded empty; replaced wholesale on every refresh. */
    val mirror = FakeStore(Db(), emptyMap())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null

    val currentUserId: StateFlow<String?> get() = mirror.currentUserId

    /** Called on app start: if a saved session exists, show it optimistically and refresh. */
    fun bootstrap() {
        val uid = api.savedUid
        if (api.token != null && uid != null) {
            mirror.currentUserId.value = uid
            startPolling()
            scope.launch { refresh() }
            com.hamraheteyf.app.push.PushRegistration.register(api)  // refresh push registration
        }
    }

    suspend fun onAuthenticated(resp: AuthResponse) {
        api.token = resp.token
        api.savedUid = resp.user.id
        // Seed the mirror with at least the signed-in user so the UI has something immediately.
        mirror.update { it.copy(users = (it.users.filterNot { u -> u.id == resp.user.id }) + resp.user) }
        mirror.currentUserId.value = resp.user.id
        refresh()
        startPolling()
        com.hamraheteyf.app.push.PushRegistration.register(api)  // register device for push
    }

    suspend fun refresh() {
        when (val r = api.fetchState()) {
            is ApiResult.Ok -> mirror.state.value = r.data
            is ApiResult.Fail -> if (r.message == "UNAUTHORIZED") signOut()
        }
    }

    fun signOut() {
        poller?.cancel(); poller = null
        mirror.currentUserId.value = null
        mirror.state.value = Db()
        // Unregister the device first (needs the still-valid token), then clear the session.
        scope.launch {
            com.hamraheteyf.app.push.PushRegistration.unregister(api)
            api.clearSession()
        }
    }

    private fun startPolling() {
        poller?.cancel()
        poller = scope.launch {
            while (isActive && api.token != null) {
                delay(POLL_INTERVAL_MS)
                refresh()
            }
        }
    }

    /** Runs a server action, applies the returned snapshot, and surfaces its `data` object. */
    suspend fun act(name: String, argsJson: String): DataResult<JsonElement?> =
        when (val r = api.action(name, argsJson)) {
            is ApiResult.Ok -> {
                r.data.state?.let { mirror.state.value = it }
                DataResult.Success(r.data.data)
            }
            is ApiResult.Fail ->
                if (r.message == "UNAUTHORIZED") { signOut(); DataResult.Error(SERVER_NOT_SIGNED_IN) }
                else DataResult.Error(r.message)
        }

    /** Action that only reports success/failure (the common case). */
    suspend fun actUnit(name: String, argsJson: String): DataResult<Unit> =
        when (val r = act(name, argsJson)) {
            is DataResult.Success -> DataResult.Success(Unit)
            is DataResult.Error -> DataResult.Error(r.message)
        }

    companion object {
        fun idOf(data: JsonElement?): String? =
            (data as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull

        fun stringField(data: JsonElement?, key: String): String? =
            (data as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull

        fun objectField(data: JsonElement?, key: String): JsonObject? =
            (data as? JsonObject)?.get(key) as? JsonObject
    }
}

// ------------------------------------------------------------------------- arg builders

/** Builds a compact JSON object string for an action's `args`. */
internal fun argsJson(vararg pairs: Pair<String, Any?>): String =
    buildJsonObject { for ((k, v) in pairs) put(k, toJsonElement(v)) }.toString()

internal fun toJsonElement(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Int -> JsonPrimitive(v)
    is Long -> JsonPrimitive(v)
    is Enum<*> -> JsonPrimitive(v.name)
    is List<*> -> buildJsonArray { v.forEach { add(toJsonElement(it)) } }
    else -> JsonPrimitive(v.toString())
}
