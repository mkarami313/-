package com.hamraheteyf.app.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Outcome of the conditional "state" action: nothing new, a fresh snapshot (with its etag), or garbage. */
sealed interface StateReply {
    data object Unchanged : StateReply
    data class Fresh(val state: JsonElement, val etag: String?) : StateReply
    data object Invalid : StateReply
}

object StateReplyParser {
    fun parse(obj: JsonObject?): StateReply {
        if (obj == null) return StateReply.Invalid
        val data = obj["data"] as? JsonObject
        fun flag(k: String) = data?.get(k)?.jsonPrimitive?.booleanOrNull == true
        if (flag("unchanged") || flag("notModified")) return StateReply.Unchanged
        val st = obj["state"] ?: data?.get("state") ?: return StateReply.Invalid
        val etag = data?.get("etag")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: etagOf(st)
        return StateReply.Fresh(st, etag)
    }

    fun etagOf(state: JsonElement?): String? =
        (state as? JsonObject)?.get("etag")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
