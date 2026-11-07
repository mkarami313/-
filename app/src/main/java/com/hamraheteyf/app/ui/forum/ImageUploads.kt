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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
        if (client == null) return
        uris.distinct().take(remaining).forEach { uri ->
            val a = Attachment(seq++, uri)
            items = items + a
            upload(a)
        }
    }

    /** Tries a failed image again (it keeps its place in the strip). */
    fun retry(key: Long) {
        val a = items.firstOrNull { it.key == key && it.error != null && it.uri != null } ?: return
        val fresh = a.copy(error = null)
        items = items.map { if (it.key == key) fresh else it }
        upload(fresh)
    }

    private fun upload(a: Attachment) {
        val c = client ?: return
        val uri = a.uri ?: return
        scope.launch {
            val r = c.uploadImage(context, uri)
            items = items.map {
                if (it.key != a.key) it
                else when (r) { is ApiResult.Ok -> it.copy(id = r.data); is ApiResult.Fail -> it.copy(error = r.message) }
            }
        }
    }
}

/**
 * Survives rotation and process death with the images that finished uploading (their file ids); an upload
 * still running at that moment is dropped, as its coroutine is gone with the screen.
 */
@Composable
fun rememberImageUploads(client: CommunityClient?, max: Int = 6): ImageUploads {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return rememberSaveable(client, saver = Saver(
        save = { ArrayList(it.ids) },
        restore = { ids -> ImageUploads(client, ctx, scope, max).apply { setExisting(ids) } },
    )) { ImageUploads(client, ctx, scope, max) }
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
                    a.id != null && client != null -> RemoteImage(client.fileUrl(a.id), boxMod, displaySize = size)
                    a.error != null -> Box(
                        boxMod.background(MaterialTheme.colorScheme.errorContainer)
                            .clickable(onClickLabel = "تلاشِ دوباره") { uploads.retry(a.key) }
                            .semantics { contentDescription = "${a.error} برای تلاشِ دوباره بزنید." },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Refresh, null, tint = MaterialTheme.colorScheme.error)
                    }
                    else -> Box(boxMod.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                // 22dp visual badge inside a 36dp touch area in the corner (the thumbnail itself is only 56–76dp).
                Box(
                    Modifier.align(Alignment.TopEnd).size(36.dp)
                        .clickable(role = Role.Button, onClickLabel = "حذفِ تصویر") { uploads.remove(a.key) }
                        .semantics { contentDescription = "حذفِ تصویر" },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Box(
                        Modifier.padding(2.dp).size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
