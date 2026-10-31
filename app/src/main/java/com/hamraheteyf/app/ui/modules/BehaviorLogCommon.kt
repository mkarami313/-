package com.hamraheteyf.app.ui.modules

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.ChildProfilePrivate
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.net.HamrahApi
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.prepareImageForUpload
import com.hamraheteyf.app.ui.common.toPersianDigits
import kotlinx.coroutines.launch

/** The signed-in family's children (private profiles). */
@Composable
fun rememberChildren(): List<ChildProfilePrivate> {
    val c = LocalAppContainer.current
    val list by remember { c.userRepository.observeChildren() }.collectAsState(emptyList())
    return list
}

/** Filter value for entries saved before children existed (no childId). */
const val UNASSIGNED_CHILD = "_none"

fun List<ChildProfilePrivate>.nameOf(id: String): String {
    val c = firstOrNull { it.id == id } ?: return ""
    c.nickname?.takeIf { it.isNotBlank() }?.let { return it }
    // Ordinal by creation time (not list order) so «کودک N» stays put when the list is re-sorted.
    val n = sortedWith(compareBy({ it.createdAt }, { it.id })).indexOfFirst { it.id == id } + 1
    return "کودک ${n.toPersianDigits()}"
}

/** Row of child chips. [selected] = "" means "all"/none. [allLabel] null hides the "all" chip. */
@Composable
fun ChildChipRow(children: List<ChildProfilePrivate>, selected: String, onSelect: (String) -> Unit, allLabel: String? = "همه", modifier: Modifier = Modifier, unassignedLabel: String? = null) {
    if (children.isEmpty()) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (allLabel != null) item { FilterChip(selected.isBlank(), { onSelect("") }, label = { Text(allLabel) }) }
        items(children, key = { it.id }) { c -> FilterChip(selected == c.id, { onSelect(c.id) }, label = { Text(children.nameOf(c.id)) }) }
        if (unassignedLabel != null) item { FilterChip(selected == UNASSIGNED_CHILD, { onSelect(UNASSIGNED_CHILD) }, label = { Text(unassignedLabel) }) }
    }
}

/** Uploads JPEG bytes and returns the 32-hex file id. */
suspend fun HamrahApi.uploadImageId(bytes: ByteArray): ApiResult<String> =
    when (val u = upload(bytes, "image/jpeg", "img.jpg")) {
        is ApiResult.Ok -> ApiResult.Ok(u.data.substringAfterLast('/'))
        is ApiResult.Fail -> u
    }

/** Photo chooser row: shows the current/new image, a pick button and a clear button. */
@Composable
fun ImagePickRow(
    api: HamrahApi?, label: String, imageId: String, newBytes: ByteArray?,
    onPicked: (ByteArray) -> Unit, onClear: () -> Unit, onError: (String) -> Unit = {}, size: Dp = 64.dp,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val b = prepareImageForUpload(ctx, uri)
            if (b == null) onError("عکس خوانده نشد.") else onPicked(b)
        }
    }
    val has = newBytes != null || imageId.isNotBlank()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val box = Modifier.size(size).clip(MaterialTheme.shapes.small)
        val bmp = remember(newBytes) { newBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        when {
            bmp != null -> Image(bmp.asImageBitmap(), null, box, contentScale = ContentScale.Crop)
            imageId.isNotBlank() && api != null -> RemoteImage(api.fileUrl(imageId), box)
        }
        OutlinedButton(onClick = { picker.launch("image/*") }) {
            Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text(if (has) "تغییر $label" else "انتخاب $label")
        }
        if (has) TextButton(onClick = onClear) { Text("حذف") }
    }
}

const val NO_IMAGE = "00000000000000000000000000000000"

/** Parallel file-id list stored newline-separated; "none" is written as an all-zero id so server trimming can't shift alignment. */
fun parseImageList(raw: String, size: Int): List<String> {
    val parts = raw.split('\n').map { it.trim() }
    return List(size) { i -> parts.getOrNull(i)?.takeIf { Regex("[0-9a-f]{32}").matches(it) && it != NO_IMAGE }.orEmpty() }
}

fun encodeImageList(ids: List<String>): String =
    if (ids.all { it.isBlank() }) "" else ids.joinToString("\n") { it.ifBlank { NO_IMAGE } }

/**
 * Generic record editor shell: owns values/busy/error and the save call. [content] draws the fields;
 * [beforeSave] may upload files and mutate values (return an error message to abort).
 */
@Composable
fun RecEditorDialog(
    client: RecClient, kind: String, title: String, initial: Rec?,
    defaults: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit, onDone: () -> Unit,
    canSave: (Map<String, String>) -> Boolean = { true },
    beforeSave: suspend (MutableMap<String, String>) -> String? = { null },
    content: @Composable ColumnScope.(MutableMap<String, String>, setError: (String?) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val values = remember {
        mutableStateMapOf<String, String>().also { m ->
            m.putAll(defaults)
            initial?.f?.let { m.putAll(it) }
        }
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content(values, { error = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && canSave(values), onClick = {
                scope.launch {
                    busy = true; error = null
                    val data = values.toMutableMap()
                    val err = beforeSave(data)
                    if (err != null) { error = err; busy = false; return@launch }
                    when (val r = client.save(kind, initial?.id, data)) {
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
fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = mutedColor())

/** Rec -> its calendar day as ISO (own "date" field, else creation time). */
fun Rec.dayIso(): String {
    val d = this["date"]
    if (com.hamraheteyf.app.ui.common.Jalali.parseIso(d) != null) return d.take(10)
    val c = java.util.Calendar.getInstance().apply { timeInMillis = createdAt }
    return "%04d-%02d-%02d".format(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}
