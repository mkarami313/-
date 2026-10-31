package com.hamraheteyf.app.net

import android.content.Context
import com.hamraheteyf.app.BuildConfig
import com.hamraheteyf.app.data.model.User
import com.hamraheteyf.app.data.repository.fake.Db
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
    data class Fail(val message: String) : ApiResult<Nothing>
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

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().apply { if (v == null) remove("token") else putString("token", v) }.apply()

    var savedUid: String?
        get() = prefs.getString("uid", null)
        set(v) = prefs.edit().apply { if (v == null) remove("uid") else putString("uid", v) }.apply()

    fun clearSession() {
        prefs.edit().remove("token").remove("uid").apply()
    }

    // ------------------------------------------------------------------ transport

    private data class Raw(val code: Int, val body: String)

    private fun request(method: String, path: String, bodyJson: String?, auth: Boolean): Raw {
        val url = URL(base + path)
        val c = url.openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("Accept", "application/json")
            if (auth) token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
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
            onFailure = { ApiResult.Fail("خطای ارتباط با سرور. اتصال اینترنت را بررسی کنید.") },
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
            onFailure = { ApiResult.Fail("خطای ارتباط با سرور. اتصال اینترنت را بررسی کنید.") },
        )
    }

    suspend fun reset(bodyJson: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching { request("POST", "auth/reset", bodyJson, auth = false) }
            .fold({ ApiResult.Ok(Unit) }, { ApiResult.Ok(Unit) })
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
        }.getOrElse { ApiResult.Fail("آپلود انجام نشد. اتصال اینترنت را بررسی کنید.") }
    }

    /** Ids of modules the server has switched off. Null when the call fails (keep the last known set). */
    suspend fun fetchDisabledFeatures(): Set<String>? = withContext(Dispatchers.IO) {
        runCatching {
            val obj = parse(request("GET", "features", null, auth = false).body)
            obj?.get("disabled")?.let { el -> json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), el).toSet() }
        }.getOrNull()
    }

    /** Fetches the caller-scoped snapshot. 401 signals an expired/invalid session. */
    suspend fun fetchState(): ApiResult<Db> = withContext(Dispatchers.IO) {
        runCatching { request("GET", "state", null, auth = true) }.fold(
            onSuccess = { raw ->
                val obj = parse(raw.body)
                val stateEl = obj?.get("state")
                when {
                    raw.code == 401 -> ApiResult.Fail("UNAUTHORIZED")
                    raw.code in 200..299 && stateEl != null ->
                        ApiResult.Ok(json.decodeFromJsonElement(Db.serializer(), stateEl))
                    else -> ApiResult.Fail(errorOf(obj))
                }
            },
            onFailure = { ApiResult.Fail("خطای ارتباط با سرور.") },
        )
    }

    /**
     * Posts an action. Returns the (optional) `data` object and the fresh `state` the server
     * returns alongside it, so callers can refresh the mirror without a second round-trip.
     */
    data class ActionResult(val data: JsonElement?, val state: Db?)

    suspend fun action(name: String, argsJson: String): ApiResult<ActionResult> = withContext(Dispatchers.IO) {
        val body = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(name))
            put("args", json.parseToJsonElement(argsJson))
        }.toString()
        runCatching { request("POST", "action", body, auth = true) }.fold(
            onSuccess = { raw ->
                val obj = parse(raw.body)
                val ok = obj?.get("ok")?.jsonPrimitive?.booleanOrNull ?: false
                if (raw.code == 401) return@fold ApiResult.Fail("UNAUTHORIZED")
                if (!ok) return@fold ApiResult.Fail(errorOf(obj))
                val stateEl = obj?.get("state")
                val state = stateEl?.let { runCatching { json.decodeFromJsonElement(Db.serializer(), it) }.getOrNull() }
                ApiResult.Ok(ActionResult(obj?.get("data"), state))
            },
            onFailure = { ApiResult.Fail("خطای ارتباط با سرور.") },
        )
    }
}
