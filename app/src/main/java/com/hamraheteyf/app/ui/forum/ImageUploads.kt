package com.hamraheteyf.app.ui.forum

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.theme.Dimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One attached image: uploading (id null, no error), uploaded (id), or failed (error). */
data class Attachment(val key: Long, val uri: Uri?, val id: String? = null, val error: String? = null)

/** Picked images uploaded right away, so «ارسال» only sends file ids. */
@Stable
class ImageUploads(private val client: CommunityClient?, private val context: Context, private val scope: CoroutineScope, val max: Int) {
    var items by mutableStateOf<List<Attachment>>(emptyList()); private set
    private var seq = 0L

    val ids get() = items.mapNotNull { it.id }
    val busy get() = items.any { it.id == null && it.error == null }
    val hasFailed get() = items.any { it.error != null }
    val remaining get() = (max - items.size).coerceAtLeast(0)

    fun setExisting(ids: List<String>) { items = ids.map { Attachment(seq++, null, it) } }
    fun clear() { items = emptyList() }
    fun remove(key: Long) { items = items.filterNot { it.key == key } }

    fun add(uris: List<Uri>) {
        val c = client ?: return
        uris.take(remaining).forEach { uri ->
            val a = Attachment(seq++, uri)
            items = items + a
            scope.launch {
                val r = c.uploadImage(context, uri)
                items = items.map {
                    if (it.key != a.key) it
                    else when (r) { is ApiResult.Ok -> it.copy(id = r.data); is ApiResult.Fail -> it.copy(error = r.message) }
                }
            }
        }
    }
}

@Composable
fun rememberImageUploads(client: CommunityClient?, max: Int = 6): ImageUploads {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(client) { ImageUploads(client, ctx, scope, max) }
}

/** Returns a launcher that opens the system photo picker for up to the remaining slots. */
@Composable
fun rememberImagePicker(uploads: ImageUploads): () -> Unit {
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(uploads.max)) { uploads.add(it) }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> uploads.add(listOf(u)) } }
    return {
        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (uploads.remaining > 1) multi.launch(req) else if (uploads.remaining == 1) single.launch(req)
    }
}

/** Thumbnails of the attachments with a remove «×» and a spinner while uploading. */
@Composable
fun AttachmentStrip(uploads: ImageUploads, client: CommunityClient?, modifier: Modifier = Modifier, size: Dp = 64.dp, padding: PaddingValues = PaddingValues(0.dp)) {
    if (uploads.items.isEmpty()) return
    LazyRow(modifier, contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
        items(uploads.items.size, key = { uploads.items[it].key }) { i ->
            val a = uploads.items[i]
            Box(Modifier.size(size)) {
                val shape = RoundedCornerShape(10.dp)
                val boxMod = Modifier.fillMaxSize().clip(shape)
                when {
                    a.id != null && client != null -> RemoteImage(client.fileUrl(a.id), boxMod)
                    a.error != null -> Box(boxMod.background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ErrorOutline, a.error, tint = MaterialTheme.colorScheme.error)
                    }
                    else -> Box(boxMod.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { uploads.remove(a.key) }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Outlined.Close, "حذفِ تصویر", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
