package com.hamraheteyf.app.ui.auth

import com.hamraheteyf.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Minimal unauthenticated/Bearer JSON POST used by screens that call endpoints outside `/api/action`. */
object AuthHttp {
    data class Reply(val ok: Boolean, val error: String?)

    suspend fun post(path: String, body: String): Reply = withContext(Dispatchers.IO) {
        try {
            val c = URL(BuildConfig.API_BASE_URL + path.trimStart('/')).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            val ok = obj?.get("ok")?.jsonPrimitive?.boolean ?: (code in 200..299)
            Reply(ok, if (ok) null else (obj?.str("error") ?: "خطای ارتباط با سرور ($code)"))
        } catch (e: Exception) {
            Reply(false, "ارتباط با سرور برقرار نشد. اینترنت را بررسی کنید.")
        }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content
}
