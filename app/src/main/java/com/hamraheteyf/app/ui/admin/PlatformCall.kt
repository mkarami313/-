package com.hamraheteyf.app.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Thin typed wrapper over [HamrahApi.action] for platform screens (admin, sharing). */
class PlatformCall(private val api: HamrahApi) {
    val token: String? get() = api.token
    fun fileUrl(id: String) = api.fileUrl(id)

    suspend fun call(name: String, args: JsonObject = buildJsonObject { }): ApiResult<JsonObject> =
        when (val r = api.action(name, args.toString())) {
            is ApiResult.Ok -> ApiResult.Ok((r.data.data as? JsonObject) ?: JsonObject(emptyMap()))
            is ApiResult.Fail -> ApiResult.Fail(r.message)
        }
}

@Composable
fun rememberPlatformCall(): PlatformCall? {
    val api = LocalAppContainer.current.serverApi
    return remember(api) { api?.let { PlatformCall(it) } }
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
