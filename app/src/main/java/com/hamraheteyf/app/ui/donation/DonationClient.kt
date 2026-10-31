package com.hamraheteyf.app.ui.donation

import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
data class DonationRequestItem(val userId: String, val name: String = "", val note: String = "", val createdAt: Long = 0)

@Serializable
data class DonationItem(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val condition: String = "GOOD",
    val city: String = "",
    val imageIds: List<String> = emptyList(),
    val status: String = "AVAILABLE",
    val ownerId: String = "",
    val ownerName: String = "",
    val createdAt: Long = 0,
    val mine: Boolean = false,
    val requestCount: Int = 0,
    val iRequested: Boolean = false,
    val iAmRecipient: Boolean = false,
    val requests: List<DonationRequestItem> = emptyList(),
    val recipientId: String? = null,
)

val DonationConditions = listOf("NEW" to "نو", "GOOD" to "سالم و تمیز", "USED" to "کارکرده")
fun conditionLabel(key: String) = DonationConditions.firstOrNull { it.first == key }?.second ?: key
fun statusLabel(key: String) = when (key) {
    "RESERVED" -> "کنار گذاشته شد"
    "GIVEN" -> "اهدا شد"
    else -> "موجود"
}

/** Talks to the «دست‌به‌دست» actions on the server. Self-contained: no shared mirror needed. */
class DonationClient(private val api: HamrahApi) {
    private suspend fun call(name: String, args: JsonObject): ApiResult<JsonElement?> =
        when (val r = api.action(name, args.toString())) {
            is ApiResult.Ok -> ApiResult.Ok(r.data.data)
            is ApiResult.Fail -> ApiResult.Fail(r.message)
        }

    fun imageUrl(id: String) = api.fileUrl(id)

    suspend fun list(category: String?, mine: Boolean): ApiResult<Pair<List<DonationItem>, List<String>>> =
        when (val r = call("donationList", buildJsonObject { if (category != null) put("category", category); put("mine", mine) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching {
                val o = r.data!!.jsonObject
                val items = api.json.decodeFromJsonElement(ListSerializer(DonationItem.serializer()), o["items"]!!)
                val cats = api.json.decodeFromJsonElement(ListSerializer(String.serializer()), o["categories"]!!)
                ApiResult.Ok(items to cats)
            }.getOrElse { ApiResult.Fail("پاسخ سرور نامعتبر است.") }
        }

    suspend fun get(id: String): ApiResult<DonationItem> =
        when (val r = call("donationGet", buildJsonObject { put("id", id) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching {
                ApiResult.Ok(api.json.decodeFromJsonElement(DonationItem.serializer(), r.data!!.jsonObject["item"]!!))
            }.getOrElse { ApiResult.Fail("پاسخ سرور نامعتبر است.") }
        }

    suspend fun create(title: String, description: String, category: String, condition: String, city: String, imageUrls: List<String>): ApiResult<String> =
        when (val r = call("donationCreate", buildJsonObject {
            put("title", title); put("description", description); put("category", category)
            put("condition", condition); put("city", city)
            put("imageUrls", JsonArray(imageUrls.map { JsonPrimitive(it) }))
        })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching { ApiResult.Ok(r.data!!.jsonObject["id"]!!.let { (it as JsonPrimitive).content }) }
                .getOrElse { ApiResult.Fail("پاسخ سرور نامعتبر است.") }
        }

    suspend fun simple(name: String, id: String, extra: Map<String, String> = emptyMap()): ApiResult<Unit> =
        when (val r = call(name, buildJsonObject { put("id", id); extra.forEach { (k, v) -> put(k, v) } })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> ApiResult.Ok(Unit)
        }

    suspend fun openChat(otherUserId: String): ApiResult<String> =
        when (val r = call("requestConversation", buildJsonObject { put("recipientUserId", otherUserId) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching { ApiResult.Ok((r.data!!.jsonObject["id"] as JsonPrimitive).content) }
                .getOrElse { ApiResult.Fail("پاسخ سرور نامعتبر است.") }
        }

    suspend fun upload(bytes: ByteArray, name: String): ApiResult<String> = api.upload(bytes, "image/jpeg", name)
}
