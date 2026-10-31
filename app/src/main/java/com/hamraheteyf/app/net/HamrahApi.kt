package com.hamraheteyf.app.net

import android.content.Context
import com.hamraheteyf.app.BuildConfig
import com.hamraheteyf.app.data.model.User
import com.hamraheteyf.app.data.repository.fake.Db
import com.hamraheteyf.app.security.TokenCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Result of one API call: either a payload, or a Persian error message. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>
    data class Fail(val message: String, val network: Boolean = false) : ApiResult<Nothing>
}

/** Sentinel [ApiResult.Fail] message for HTTP 401; UI code must map it to Persian text, never show it raw. */
const val API_UNAUTHORIZED = "UNAUTHORIZED"

/** Result of [HamrahApi.fetchState]. */
sealed interface StateFetch {
    data object Unchanged : StateFetch
    data class Fresh(val db: Db, val etag: String?, val raw: String) : StateFetch
}

/** Parsed login/register response. */
data class AuthResponse(val token: String, val user: User)

/**
 * Thin HTTP client for the self-hosted همراهِ طیف backend. Stdlib [HttpURLConnection] only —
 * no third-party HTTP dependency. All calls are suspend functions on [Dispatchers.IO].
 *
 * The bearer token (and the signed-in uid) are persisted in SharedPreferences so a session
 * survives an app restart.
 */
class HamrahApi(context: Context) {

    private val base = BuildConfig.API_BASE_URL
    private val prefs = context.applicationContext.getSharedPreferences("hamrah_auth", Context.MODE_PRIVATE)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    init { FileUrls.attach(this) }

    /** Called (from any thread) whenever an authenticated call comes back 401. */
    @Volatile var onUnauthorized: (() -> Unit)? = null

    @Volatile private var tokenCache: String? = null
    @Volatile private var tokenLoaded = false
    @Volatile private var etagActionSupported = true

    /** Session token; encrypted at rest. A plain token from older versions is migrated on first read. */
    var token: String?
        get() {
            if (!tokenLoaded) synchronized(this) {
                if (!tokenLoaded) { tokenCache = readToken(); tokenLoaded = true }
            }
            return tokenCache
        }
        set(v) = synchronized(this) {
            tokenCache = v
            tokenLoaded = true
            writeToken(v)
        }

    private fun readToken(): String? {
        prefs.getString(KEY_TOKEN_ENC, null)?.let { return TokenCrypto.decrypt(it) }
        val plain = prefs.getString(KEY_TOKEN, null) ?: return null
        TokenCrypto.encrypt(plain)?.let { enc ->
            if (TokenCrypto.decrypt(enc) == plain) {
                prefs.edit().putString(KEY_TOKEN_ENC, enc).remove(KEY_TOKEN).commit()
            }
        }
        return plain
    }

    private fun writeToken(v: String?) {
        val e = prefs.edit()
        if (v == null) {
            e.remove(KEY_TOKEN).remove(KEY_TOKEN_ENC)
        } else {
            val enc = TokenCrypto.encrypt(v)
            if (enc != null) e.putString(KEY_TOKEN_ENC, enc).remove(KEY_TOKEN)
            else e.putString(KEY_TOKEN, v).remove(KEY_TOKEN_ENC)
        }
        e.commit()
    }

    var savedUid: String?
        get() = prefs.getString("uid", null)
        set(v) = prefs.edit().apply { if (v == null) remove("uid") else putString("uid", v) }.apply()

    fun clearSession() {
        synchronized(this) {
            tokenCache = null
            tokenLoaded = true
            prefs.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_ENC).remove("uid").commit()
        }
    }

    // ------------------------------------------------------------------ transport

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_ENC = "token_enc"
        const val NET_ERR = "خطای ارتباط با سرور. اتصال اینترنت را بررسی کنید."
    }

    private class RawBytes(val code: Int, val bytes: ByteArray?)

    private fun requestBytes(method: String, path: String, bodyJson: String?, readTimeoutMs: Int): RawBytes {
        val c = URL(base + path).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = readTimeoutMs
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (bodyJson != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            RawBytes(code, if (code == 200) c.inputStream.use { it.readBytes() } else null)
        } finally {
            c.disconnect()
        }
    }

    private data class Raw(val code: Int, val body: String)

    private fun request(method: String, path: String, bodyJson: String?, auth: Boolean, tokenOverride: String? = null): Raw {
        val url = URL(base + path)
        val c = url.openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("Accept", "application/json")
            if (auth) (tokenOverride ?: token)?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (bodyJson != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val text = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
            Raw(code, text)
        } finally {
            c.disconnect()
        }
    }

    private fun parse(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()

    private fun errorOf(obj: JsonObject?): String =
        obj?.get("error")?.jsonPrimitive?.contentOrNull ?: "خطای ارتباط با سرور. اتصال اینترنت را بررسی کنید."

    // ------------------------------------------------------------------ endpoints

    suspend fun register(bodyJson: String): ApiResult<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching { request("POST", "auth/register", bodyJson, auth = false) }.fold(
            onSuccess = { raw ->
                val obj = parse(raw.body)
                val token = obj?.get("token")?.jsonPrimitive?.contentOrNull
                val userEl = obj?.get("user")
                if (raw.code in 200..299 && token != null && userEl != null) {
                    ApiResult.Ok(AuthResponse(token, json.decodeFromJsonElement(User.serializer(), userEl)))
                } else ApiResult.Fail(errorOf(obj))
            },
            onFailure = { ApiResult.Fail(NET_ERR, network = true) },
        )
    }

    suspend fun login(bodyJson: String): ApiResult<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching { request("POST", "auth/login", bodyJson, auth = false) }.fold(
            onSuccess = { raw ->
                val obj = parse(raw.body)
                val token = obj?.get("token")?.jsonPrimitive?.contentOrNull
                val userEl = obj?.get("user")
                if (raw.code in 200..299 && token != null && userEl != null) {
                    ApiResult.Ok(AuthResponse(token, json.decodeFromJsonElement(User.serializer(), userEl)))
                } else ApiResult.Fail(errorOf(obj))
            },
            onFailure = { ApiResult.Fail(NET_ERR, network = true) },
        )
    }

    suspend fun reset(bodyJson: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { request("POST", "auth/reset", bodyJson, auth = false) }
            .fold({ ApiResult.Ok(Unit) }, { ApiResult.Ok(Unit) })
    }

    /** Signed URL when one is cached (refreshed in the background), else the legacy bare URL. See [FileUrls]. */
    fun fileUrl(id: String): String = FileUrls.url(id)

    /** Persian speech (WAV bytes) synthesized on the server; null when unavailable. POST JSON, GET for old servers. */
    suspend fun tts(text: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val body = kotlinx.serialization.json.buildJsonObject {
                put("text", kotlinx.serialization.json.JsonPrimitive(text))
            }.toString()
            val r = requestBytes("POST", "tts", body, 40000)
            when (r.code) {
                200 -> r.bytes
                404, 405, 501 -> requestBytes("GET", "tts?text=" + java.net.URLEncoder.encode(text, "UTF-8"), null, 40000).bytes
                else -> null
            }
        }.getOrNull()
    }

    /** Uploads one file (image/audio/video/PDF, up to 8 MB). Returns the absolute URL of the stored file. */
    suspend fun upload(bytes: ByteArray, mime: String, name: String): ApiResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(base + "upload").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.doOutput = true
                c.setFixedLengthStreamingMode(bytes.size)
                c.setRequestProperty("Content-Type", mime)
                c.setRequestProperty("X-Filename", java.net.URLEncoder.encode(name, "UTF-8"))
                token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                c.outputStream.use { it.write(bytes) }
                val stream = if (c.responseCode in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
                val obj = parse(stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText))
                val rel = obj?.get("file")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                if (c.responseCode in 200..299 && rel != null) ApiResult.Ok(base + rel) else ApiResult.Fail(errorOf(obj))
            } finally {
                c.disconnect()
            }
        }.getOrElse { ApiResult.Fail("آپلود انجام نشد. اتصال اینترنت را بررسی کنید.", network = true) }
    }

    /** Ids of modules the server has switched off. Null when the call fails (keep the last known set). */
    suspend fun fetchDisabledFeatures(): Set<String>? = withContext(Dispatchers.IO) {
        runCatching {
            val obj = parse(request("GET", "features", null, auth = true).body)
            obj?.get("disabled")?.let { el -> json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), el).toSet() }
        }.getOrNull()
    }

    /**
     * Fetches the caller-scoped snapshot. With an [etag] it asks the server first whether anything
     * changed (action "state"); servers without that action (404) are remembered and served by plain GET.
     * 401 → Fail([API_UNAUTHORIZED]); transport errors → Fail(network = true).
     */
    suspend fun fetchState(etag: String? = null): ApiResult<StateFetch> = withContext(Dispatchers.IO) {
        val sent = token
        if (etagActionSupported) {
            val body = kotlinx.serialization.json.buildJsonObject {
                put("name", kotlinx.serialization.json.JsonPrimitive("state"))
                put("args", kotlinx.serialization.json.buildJsonObject { if (etag != null) put("etag", kotlinx.serialization.json.JsonPrimitive(etag)) })
            }.toString()
            val raw = runCatching { request("POST", "action", body, auth = true) }.getOrElse {
                return@withContext ApiResult.Fail(NET_ERR, network = true)
            }
            when {
                raw.code == 401 -> return@withContext unauthorized(sent)
                raw.code == 404 || raw.code == 405 -> etagActionSupported = false
                raw.code in 200..299 -> {
                    val obj = parse(raw.body)
                    when (val reply = StateReplyParser.parse(obj)) {
                        StateReply.Unchanged -> return@withContext ApiResult.Ok(StateFetch.Unchanged)
                        is StateReply.Fresh -> return@withContext decodeState(reply.state, reply.etag)
                        StateReply.Invalid -> return@withContext ApiResult.Fail(errorOf(obj))
                    }
                }
                else -> return@withContext ApiResult.Fail(errorOf(parse(raw.body)))
            }
        }
        runCatching { request("GET", "state", null, auth = true) }.fold(
            onSuccess = { raw ->
                val obj = parse(raw.body)
                val stateEl = obj?.get("state")
                when {
                    raw.code == 401 -> unauthorized(sent)
                    raw.code in 200..299 && stateEl != null -> decodeState(stateEl, StateReplyParser.etagOf(stateEl))
                    else -> ApiResult.Fail(errorOf(obj))
                }
            },
            onFailure = { ApiResult.Fail(NET_ERR, network = true) },
        )
    }

    private fun decodeState(el: JsonElement, etag: String?): ApiResult<StateFetch> =
        runCatching { StateFetch.Fresh(json.decodeFromJsonElement(Db.serializer(), el), etag, el.toString()) }
            .fold({ ApiResult.Ok(it) }, { ApiResult.Fail("پاسخ سرور نامعتبر است.") })

    /** Only the session that actually sent the rejected token is torn down (not a newer login). */
    private fun unauthorized(sent: String?): ApiResult.Fail {
        if (sent != null && sent == token) onUnauthorized?.invoke()
        return ApiResult.Fail(API_UNAUTHORIZED)
    }

    /**
     * Posts an action. Returns the (optional) `data` object and the fresh `state` the server
     * returns alongside it, so callers can refresh the mirror without a second round-trip.
     */
    data class ActionResult(val data: JsonElement?, val state: Db?, val etag: String? = null, val stateRaw: String? = null)

    /** [tokenOverride] lets sign-out send one last call (push unregister) with the already-cleared session's token. */
    suspend fun action(name: String, argsJson: String, tokenOverride: String? = null): ApiResult<ActionResult> = withContext(Dispatchers.IO) {
        val sent = if (tokenOverride != null) null else token
        val body = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(name))
            put("args", json.parseToJsonElement(argsJson))
        }.toString()
        runCatching { request("POST", "action", body, auth = true, tokenOverride = tokenOverride) }.fold(
            onSuccess = { raw ->
                val obj = parse(raw.body)
                val ok = obj?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
                if (raw.code == 401) return@fold unauthorized(sent)
                if (!ok) return@fold ApiResult.Fail(errorOf(obj))
                val stateEl = obj?.get("state")
                val state = stateEl?.let { runCatching { json.decodeFromJsonElement(Db.serializer(), it) }.getOrNull() }
                ApiResult.Ok(ActionResult(obj?.get("data"), state, StateReplyParser.etagOf(stateEl), if (state != null) stateEl.toString() else null))
            },
            onFailure = { ApiResult.Fail("خطای ارتباط با سرور.", network = true) },
        )
    }
}
