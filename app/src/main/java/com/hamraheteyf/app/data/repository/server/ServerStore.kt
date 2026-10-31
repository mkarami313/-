package com.hamraheteyf.app.data.repository.server

import com.hamraheteyf.app.AppForeground
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.repository.fake.Db
import com.hamraheteyf.app.data.repository.fake.FakeStore
import com.hamraheteyf.app.net.API_UNAUTHORIZED
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.AuthResponse
import com.hamraheteyf.app.net.HamrahApi
import com.hamraheteyf.app.net.StateFetch
import com.hamraheteyf.app.ui.common.ImageDiskCache
import com.hamraheteyf.app.ui.modules.RecClient
import com.hamraheteyf.app.ui.modules.EmergencyCache
import com.hamraheteyf.app.ui.modules.Reminders
import com.hamraheteyf.app.ui.modules.SpeechCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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
import java.io.File

const val SERVER_NOT_SIGNED_IN = "ابتدا وارد حساب خود شوید."
private const val POLL_INTERVAL_MS = 30_000L
private const val MIN_REFRESH_GAP_MS = 2_000L
private const val RECONCILE_GAP_MS = 5 * 60_000L
private const val STATE_CACHE_FILE = "state_cache.json"

/** Per-user prefs wiped on sign-out (progress ticks, AAC boards). Auth/vault/onboarding/dismissed banners are deliberately kept. */
private val USER_PREFS = listOf("hamrah_aac", "hamrah_done", "hamrah_course_done")

/**
 * Keeps a local mirror of the server-scoped snapshot in a [FakeStore] so the existing
 * (reactive, flow-based) read logic in the `fake` package can be reused verbatim for
 * every `observe*`. Mutations never touch the mirror directly — they POST to the API,
 * and the fresh snapshot the server returns replaces the mirror, so the UI updates.
 *
 * While the app is in the foreground a poller re-reads the state every 30 s (conditional on the last
 * etag when the server supports it) and again whenever the app returns to the foreground, so changes
 * made by OTHER users show up. The last good snapshot is persisted so the app opens with data offline.
 */
class ServerStore(val api: HamrahApi, private val context: android.content.Context) {

    /** The mirror. Seeded empty; replaced wholesale on every refresh. */
    val mirror = FakeStore(Db(), emptyMap())

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poller: Job? = null

    private val _offline = MutableStateFlow(false)
    /** True while the last refresh failed on the network (cached data is being shown). */
    val offline: StateFlow<Boolean> get() = _offline

    private val _sessionExpired = MutableStateFlow(false)
    /** True after the server rejected the session (401) until the next successful sign-in. */
    val sessionExpired: StateFlow<Boolean> get() = _sessionExpired

    private val cacheFile get() = File(appContext.filesDir, STATE_CACHE_FILE)

    @Volatile private var etag: String? = null
    @Volatile private var lastRefreshAt = 0L
    /** Bumped on every state write so a slow poll can never overwrite a newer action result. */
    private val version = java.util.concurrent.atomic.AtomicLong(0)

    val currentUserId: StateFlow<String?> get() = mirror.currentUserId

    init {
        // One owner for 401 handling: HamrahApi tells us, we sign out exactly once.
        api.onUnauthorized = { _sessionExpired.value = true; signOut() }
    }

    /** Called on app start: if a saved session exists, show it optimistically (from disk if offline) and refresh. */
    fun bootstrap() {
        (appContext as? android.app.Application)?.let { AppForeground.install(it) }
        com.hamraheteyf.app.ui.navigation.FeatureFlags.load(context)
        scope.launch { SpeechCache.prune(appContext); ImageDiskCache.prune(appContext) }
        val uid = api.savedUid
        if (api.token != null && uid != null) {
            mirror.currentUserId.value = uid
            loadCachedState(uid)
            startPolling()
            Reminders.scheduleAll(appContext)
            scope.launch { Reminders.refreshFromServer(appContext, api) }
            com.hamraheteyf.app.push.PushRegistration.register(api)  // refresh push registration
        }
    }

    suspend fun onAuthenticated(resp: AuthResponse) {
        _sessionExpired.value = false
        _offline.value = false
        api.token = resp.token
        api.savedUid = resp.user.id
        etag = null
        version.incrementAndGet()
        // Seed the mirror with at least the signed-in user so the UI has something immediately.
        mirror.update { it.copy(users = (it.users.filterNot { u -> u.id == resp.user.id }) + resp.user) }
        mirror.currentUserId.value = resp.user.id
        refresh(force = true)
        startPolling()
        Reminders.scheduleAll(appContext)
        scope.launch { Reminders.refreshFromServer(appContext, api) }
        com.hamraheteyf.app.push.PushRegistration.register(api)  // register device for push
    }

    private suspend fun syncFeatures() {
        api.fetchDisabledFeatures()?.let { com.hamraheteyf.app.ui.navigation.FeatureFlags.update(context, it) }
    }

    /**
     * Pulls the latest snapshot. A call within [MIN_REFRESH_GAP_MS] of the previous one is skipped unless
     * [force]. Network failure keeps the current (cached) data and flips [offline]; 401 is handled by
     * [HamrahApi.onUnauthorized] → [signOut].
     */
    suspend fun refresh(force: Boolean = true) {
        if (api.token == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshAt < MIN_REFRESH_GAP_MS) return
        lastRefreshAt = now
        val startedAt = version.get()
        val sentToken = api.token
        syncFeatures()
        when (val r = api.fetchState(etag)) {
            is ApiResult.Ok -> {
                _offline.value = false
                when (val f = r.data) {
                    StateFetch.Unchanged -> Unit
                    is StateFetch.Fresh -> synchronized(this) {
                        if (api.token == sentToken && version.get() == startedAt) applyState(f.db, f.etag, f.raw)
                    }
                }
            }
            is ApiResult.Fail -> {
                if (r.network) _offline.value = true
                // 401 already routed through api.onUnauthorized.
            }
        }
    }

    @Synchronized
    private fun applyState(db: Db, newEtag: String?, raw: String?) {
        mirror.state.value = db
        etag = newEtag
        version.incrementAndGet()
        val uid = mirror.currentUserId.value
        if (uid != null && raw != null) persistState(uid, newEtag, raw)
    }

    // ------------------------------------------------------------------ disk cache

    private fun persistState(uid: String, etag: String?, raw: String) {
        runCatching {
            val text = buildJsonObject {
                put("uid", JsonPrimitive(uid))
                if (etag != null) put("etag", JsonPrimitive(etag))
                put("state", api.json.parseToJsonElement(raw))
            }.toString()
            val tmp = File(cacheFile.parentFile, "$STATE_CACHE_FILE.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); tmp.renameTo(cacheFile) }
        }
    }

    private fun loadCachedState(uid: String) {
        runCatching {
            val f = cacheFile
            if (!f.exists()) return
            val obj = api.json.parseToJsonElement(f.readText()).jsonObject
            if (obj["uid"]?.jsonPrimitive?.contentOrNull != uid) { f.delete(); return }
            val st = obj["state"] ?: return
            mirror.state.value = api.json.decodeFromJsonElement(Db.serializer(), st)
            // Keep the etag so the first poll can be a cheap "unchanged" check.
            etag = obj["etag"]?.jsonPrimitive?.contentOrNull
        }.onFailure { runCatching { cacheFile.delete() } }
    }

    // ------------------------------------------------------------------ sign-out

    /**
     * Ends the session. The token is cleared synchronously FIRST (so nothing can keep using it), then
     * everything user-specific on the device is wiped, then a best-effort, time-boxed push
     * unregistration is sent with the old token. The biometric credential vault is intentionally untouched.
     */
    @Synchronized
    fun signOut() {
        val oldToken = api.token
        api.clearSession()
        poller?.cancel(); poller = null
        etag = null
        version.incrementAndGet()
        mirror.currentUserId.value = null
        mirror.state.value = Db()
        _offline.value = false
        runCatching { cacheFile.delete() }
        runCatching { Reminders.clearAll(appContext) }
        runCatching { SpeechCache.clear(appContext) }
        runCatching { ImageDiskCache.clear(appContext) }
        runCatching { com.hamraheteyf.app.net.FileUrls.clear() }
        runCatching { EmergencyCache.onSignOut(appContext) }
        runCatching { com.hamraheteyf.app.ui.navigation.FeatureFlags.update(appContext, emptySet()) }
        runCatching {
            USER_PREFS.forEach { appContext.getSharedPreferences(it, android.content.Context.MODE_PRIVATE).edit().clear().apply() }
            appContext.cacheDir.listFiles { f -> f.name.startsWith("aac_pending_") }?.forEach { it.delete() }
        }
        if (oldToken != null) {
            scope.launch {
                withTimeoutOrNull(8_000) {
                    runCatching {
                        val push = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                        api.action("unregisterPush", argsJson("token" to push), tokenOverride = oldToken)
                    }
                }
            }
        }
    }

    private fun startPolling() {
        poller?.cancel()
        poller = scope.launch {
            AppForeground.isForeground.collectLatest { fg ->
                if (!fg) return@collectLatest
                var lastReconcile = 0L
                while (isActive && api.token != null) {
                    refresh(force = false)
                    val t = System.currentTimeMillis()
                    if (t - lastReconcile >= RECONCILE_GAP_MS) {
                        lastReconcile = t
                        runCatching { Reminders.reconcileAppointments(appContext, RecClient(api)) }
                        if (api.token == null) runCatching { Reminders.clearAll(appContext) }  // signed out mid-flight
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    /** Runs a server action, applies the returned snapshot, and surfaces its `data` object. */
    suspend fun act(name: String, argsJson: String): DataResult<JsonElement?> =
        when (val r = api.action(name, argsJson)) {
            is ApiResult.Ok -> {
                synchronized(this) {
                    r.data.state?.let { applyState(it, r.data.etag, r.data.stateRaw) }
                }
                _offline.value = false
                DataResult.Success(r.data.data)
            }
            is ApiResult.Fail -> {
                if (r.network) _offline.value = true
                // 401 was already turned into a sign-out by api.onUnauthorized.
                if (r.message == API_UNAUTHORIZED) DataResult.Error(SERVER_NOT_SIGNED_IN)
                else DataResult.Error(r.message)
            }
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
