package com.hamraheteyf.app.ui.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.User
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.API_UNAUTHORIZED
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.HamrahApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

const val SESSION_EXPIRED_MESSAGE = "نشست شما به پایان رسیده است؛ لطفاً دوباره وارد شوید."

/** A generic server record: a bag of string fields plus a few system fields. */
data class Rec(
    val id: String,
    val f: Map<String, String>,
    val mine: Boolean,
    val ownerName: String,
    val createdAt: Long,
    val status: String,
    val iBooked: Boolean,
    val bookedByName: String,
    val ownerId: String = "",
) {
    operator fun get(k: String): String = f[k].orEmpty()
    /** One entry per non-blank line. */
    fun lines(k: String): List<String> = this[k].lines().map { it.trim() }.filter { it.isNotEmpty() }
}

data class SearchHit(val type: String, val id: String, val title: String, val sub: String)

private val SYSTEM_KEYS = setOf("id", "ownerId", "ownerName", "createdAt", "mine", "status", "iBooked", "bookedByName", "bookedBy")

private fun JsonObject.toRec(): Rec {
    val strings = entries.filter { it.key !in SYSTEM_KEYS && it.value is JsonPrimitive }
        .associate { it.key to it.value.jsonPrimitive.content }
    return Rec(
        id = this["id"]?.jsonPrimitive?.content.orEmpty(),
        f = strings,
        mine = this["mine"]?.jsonPrimitive?.booleanOrNull ?: false,
        ownerName = this["ownerName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        createdAt = this["createdAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        status = this["status"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        iBooked = this["iBooked"]?.jsonPrimitive?.booleanOrNull ?: false,
        bookedByName = this["bookedByName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        ownerId = this["ownerId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

class RecClient(private val api: HamrahApi) {
    private suspend fun call(name: String, args: JsonObject): ApiResult<JsonElement?> =
        when (val r = api.action(name, args.toString())) {
            is ApiResult.Ok -> ApiResult.Ok(r.data.data)
            is ApiResult.Fail ->
                if (r.message == API_UNAUTHORIZED) ApiResult.Fail(SESSION_EXPIRED_MESSAGE) else ApiResult.Fail(r.message, r.network)
        }

    private val bad = ApiResult.Fail("پاسخ سرور نامعتبر است.")

    suspend fun list(kind: String, mine: Boolean = false, booked: Boolean = false): ApiResult<List<Rec>> =
        when (val r = call("recList", buildJsonObject { put("kind", kind); put("mine", mine); put("booked", booked) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching {
                ApiResult.Ok(r.data!!.jsonObject["items"]!!.jsonArray.map { it.jsonObject.toRec() })
            }.getOrElse { bad }
        }

    suspend fun save(kind: String, id: String?, data: Map<String, String>): ApiResult<Unit> =
        when (val r = call("recSave", buildJsonObject {
            put("kind", kind)
            if (id != null) put("id", id)
            put("data", buildJsonObject { data.forEach { (k, v) -> put(k, v) } })
        })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> ApiResult.Ok(Unit)
        }

    suspend fun delete(kind: String, id: String): ApiResult<Unit> =
        when (val r = call("recDelete", buildJsonObject { put("kind", kind); put("id", id) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> ApiResult.Ok(Unit)
        }

    suspend fun simple(action: String, id: String): ApiResult<Unit> =
        when (val r = call(action, buildJsonObject { put("id", id) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> ApiResult.Ok(Unit)
        }

    suspend fun search(q: String): ApiResult<List<SearchHit>> =
        when (val r = call("search", buildJsonObject { put("q", q) })) {
            is ApiResult.Fail -> r
            is ApiResult.Ok -> runCatching {
                ApiResult.Ok(r.data!!.jsonObject["items"]!!.jsonArray.map {
                    val o = it.jsonObject
                    SearchHit(o["type"]!!.jsonPrimitive.content, o["id"]!!.jsonPrimitive.content,
                        o["title"]!!.jsonPrimitive.content, o["sub"]?.jsonPrimitive?.contentOrNull.orEmpty())
                })
            }.getOrElse { bad }
        }
}

@Composable
fun rememberRecClient(): RecClient? {
    val api = LocalAppContainer.current.serverApi
    return remember(api) { api?.let { RecClient(it) } }
}

@Composable
fun rememberMe(): User? {
    val c = LocalAppContainer.current
    val me by remember { c.userRepository.observeCurrentUser() }.collectAsState(null)
    return me
}

class RecState(val items: List<Rec>?, val error: String?)

@Composable
fun rememberRecs(client: RecClient?, kind: String, tick: Int, mine: Boolean = false, booked: Boolean = false): RecState {
    var state by remember(kind, mine, booked) { mutableStateOf(RecState(null, null)) }
    LaunchedEffect(client, kind, mine, booked, tick) {
        if (client == null) { state = RecState(null, "این بخش در حالت آفلاین در دسترس نیست."); return@LaunchedEffect }
        state = when (val r = client.list(kind, mine, booked)) {
            is ApiResult.Ok -> RecState(r.data, null)
            is ApiResult.Fail -> RecState(state.items, r.message)
        }
    }
    return state
}

class FieldSpec(val key: String, val label: String, val long: Boolean = false, val number: Boolean = false, val hint: String? = null)

/** Saves a text-field map through rotation / process death as a flat key,value,key,value list. */
private val StringMapSaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>, String>(
    save = { m -> m.flatMap { listOf(it.key, it.value) } },
    restore = { l -> mutableStateMapOf<String, String>().also { m -> l.chunked(2).forEach { if (it.size == 2) m[it[0]] = it[1] } } },
)

/** Create/edit dialog for one record kind. Calls recSave and reports success through [onDone]. */
@Composable
fun RecFormDialog(
    client: RecClient,
    kind: String,
    title: String,
    fields: List<FieldSpec>,
    initial: Rec?,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    defaults: Map<String, String> = emptyMap(),
) {
    val scope = rememberCoroutineScope()
    val values = rememberSaveable(saver = StringMapSaver) {
        mutableStateMapOf<String, String>().also { m -> fields.forEach { m[it.key] = initial?.get(it.key) ?: defaults[it.key].orEmpty() } }
    }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { f ->
                    OutlinedTextField(
                        value = values[f.key].orEmpty(),
                        onValueChange = { values[f.key] = it },
                        label = { Text(f.label) },
                        supportingText = f.hint?.let { h -> { Text(h) } },
                        singleLine = !f.long,
                        minLines = if (f.long) 3 else 1,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (f.number) KeyboardType.Decimal else KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true; error = null
                    when (val r = client.save(kind, initial?.id, values.toMap())) {
                        is ApiResult.Ok -> onDone()
                        is ApiResult.Fail -> error = r.message
                    }
                    busy = false
                }
            }) { Text(if (busy) "…" else "ذخیره") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun ConfirmDialog(text: String, confirm: String = "حذف", onYes: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** Compact bordered card used by every list in the new modules. */
@Composable
fun CompactCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick, modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), content = content)
    } else Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = colors, border = border) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), content = content)
    }
}
