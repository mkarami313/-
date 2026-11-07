@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.learn

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.hexColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private fun sizeFa(bytes: Long): String =
    if (bytes >= 1024L * 1024) String.format(java.util.Locale.US, "%.1f", bytes / 1048576.0).replace('.', '٫').toPersianDigits() + " مگابایت"
    else (bytes / 1024).toInt().coerceAtLeast(1).toPersianDigits() + " کیلوبایت"

private suspend fun fileMeta(ctx: Context, uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) (c.getString(0) ?: "video.mp4") to (if (c.isNull(1)) -1L else c.getLong(1)) else null
        }
    }.getOrNull() ?: ("video.mp4" to -1L)
}

/** New submission (editId null) or edit/resubmit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnSubmitScreen(editId: String?, onBack: () -> Unit, onDone: () -> Unit) {
    val client = rememberCommunity()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val admin = rememberIsAdmin()
    val cats by rememberLearnCategories(client)

    var kind by rememberSaveable { mutableStateOf("article") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var coverId by rememberSaveable { mutableStateOf<String?>(null) }
    var videoId by rememberSaveable { mutableStateOf<String?>(null) }
    var videoName by rememberSaveable { mutableStateOf("") }
    var videoSize by rememberSaveable { mutableLongStateOf(-1L) }
    var videoUrl by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("upload") } // upload | aparat
    var loaded by rememberSaveable { mutableStateOf(editId == null) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadTick by remember { mutableIntStateOf(0) }

    var coverBusy by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<Int?>(null) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var uploadGen by remember { mutableIntStateOf(0) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    // The file being uploaded is shown from these; videoId/videoName/videoSize keep the CURRENT (e.g. already
    // published) video until the new upload succeeds, so cancelling or a failure no longer wipes it.
    var uploadingName by remember { mutableStateOf("") }
    var uploadingSize by remember { mutableLongStateOf(-1L) }
    var submitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(editId, loadTick, client) {
        if (loaded || editId == null) return@LaunchedEffect
        if (client == null) { loadError = "این بخش در حالتِ آفلاین در دسترس نیست."; return@LaunchedEffect }
        when (val r = client.learnGet(editId)) {
            is ApiResult.Ok -> r.data.let { d ->
                kind = d.kind; categoryId = d.categoryId; title = d.title; summary = d.summary; body = d.body.orEmpty()
                coverId = d.coverId; videoId = d.videoId; videoUrl = d.videoUrl.orEmpty()
                if (d.videoId != null) { videoName = "ویدیوی بارگذاری‌شده"; source = "upload" } else if (!d.videoUrl.isNullOrBlank()) source = "aparat"
                loaded = true; loadError = null
            }
            is ApiResult.Fail -> loadError = r.message
        }
    }

    fun change(f: () -> Unit) { f(); dirty = true; formError = null }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && client != null) {
            coverBusy = true
            scope.launch {
                when (val r = client.uploadImage(ctx, uri)) {
                    is ApiResult.Ok -> change { coverId = r.data }
                    is ApiResult.Fail -> formError = r.message
                }
                coverBusy = false
            }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && client != null) {
            uploadJob?.cancel()
            uploadError = null
            val gen = ++uploadGen
            uploadJob = scope.launch {
                val (name, size) = fileMeta(ctx, uri)
                uploadingName = name; uploadingSize = size
                uploadProgress = 0
                var last = 0
                val r = client.uploadVideo(ctx, uri) { p ->
                    if (p != last) { last = p; scope.launch { if (gen == uploadGen) uploadProgress = p } }
                }
                if (gen != uploadGen) return@launch  // cancelled / replaced: keep the form as it is
                uploadProgress = null
                when (r) {
                    is ApiResult.Ok -> { videoId = r.data; videoName = uploadingName; videoSize = uploadingSize; dirty = true; formError = null }
                    is ApiResult.Fail -> { uploadError = r.message }
                }
                uploadJob = null
            }
        }
    }

    val aparatOk = aparatHash(videoUrl) != null
    val titleErr = title.trim().length !in 4..140
    val catErr = categoryId == null
    val bodyErr = kind == "article" && body.trim().length < 50
    val videoErr = kind == "video" && (if (source == "upload") videoId == null else !aparatOk)

    fun submit() {
        if (submitting) return  // two taps in one frame used to POST the submission twice
        showErrors = true
        val msg = when {
            catErr -> "یک دسته‌بندی انتخاب کنید."
            titleErr -> "عنوان باید بین ۴ تا ۱۴۰ نویسه باشد."
            uploadProgress != null -> "صبر کنید تا بارگذاریِ ویدیو تمام شود."
            videoErr -> if (source == "upload") "یک فایلِ ویدیو (mp4) انتخاب و بارگذاری کنید." else "لینکِ آپارات معتبر نیست؛ مثلاً https://www.aparat.com/v/abc123"
            bodyErr -> "متنِ مطلب دست‌کم ۵۰ نویسه باشد."
            else -> null
        }
        if (msg != null) { formError = msg; return }
        val c = client ?: return
        submitting = true; formError = null
        scope.launch {
            val r = c.learnSubmit(
                editId, kind, categoryId.orEmpty(), title.trim(), summary.trim(), if (kind == "article") body.trim() else "",
                coverId, if (kind == "video" && source == "upload") videoId else null,
                if (kind == "video" && source == "aparat") videoUrl.trim() else null,
            )
            submitting = false
            when (r) {
                is ApiResult.Ok -> { dirty = false; result = r.data.second.ifBlank { "PENDING" } }
                is ApiResult.Fail -> formError = r.message
            }
        }
    }

    fun leave() { if (dirty || uploadProgress != null) confirmLeave = true else onBack() }
    BackHandler(enabled = result == null) { leave() }

    Scaffold(
        topBar = { AppTopBar(if (editId == null) "ارسالِ مطلب" else "ویرایشِ مطلب", onBack = { leave() }) },
        bottomBar = {
            if (loaded) Surface(shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Dimens.gutter, Dimens.s)) {
                    formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp)) }
                    Button(onClick = { submit() }, enabled = !submitting && !coverBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        if (submitting) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }
                        Text(if (admin) "انتشار" else "ارسال برای بررسی")
                    }
                }
            }
        },
    ) { pad ->
        if (!loaded) {
            if (loadError != null) ErrorView(loadError.orEmpty(), { loadError = null; loadTick++ }, Modifier.padding(pad)) else LoadingView(Modifier.padding(pad))
            return@Scaffold
        }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(Dimens.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (admin) "مطالبِ مدیر بی‌درنگ منتشر می‌شوند."
                    else "مطلبِ شما پیش از انتشار توسطِ مدیر بررسی می‌شود و نتیجه را با اعلان خبر می‌دهیم.",
                    style = MaterialTheme.typography.labelMedium, color = muted())
            }

            Label("نوع")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("article" to "مقاله", "video" to "ویدیو").forEachIndexed { i, (k, l) ->
                    SegmentedButton(selected = kind == k, onClick = { change { kind = k } }, shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = { Icon(if (k == "video") Icons.Outlined.OndemandVideo else Icons.Outlined.Article, null, Modifier.size(18.dp)) }) { Text(l) }
                }
            }

            Label("دسته‌بندی")
            if (cats.isEmpty()) Text("در حالِ دریافتِ دسته‌ها…", style = MaterialTheme.typography.labelMedium, color = muted())
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cats, key = { it.id }) { c ->
                    val on = c.id == categoryId
                    FilterChip(selected = on, onClick = { change { categoryId = c.id } }, label = { Text(c.title) },
                        leadingIcon = { Icon(iconFor(c.icon), null, Modifier.size(18.dp), tint = if (on) Color.White else hexColor(c.color)) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White))
                }
            }
            if (showErrors && catErr) ErrText("یک دسته‌بندی انتخاب کنید.")

            Label("عنوان")
            OutlinedTextField(title, { v -> change { title = v.take(140) } }, Modifier.fillMaxWidth(), singleLine = true,
                isError = showErrors && titleErr, placeholder = { Text("مثلاً: چطور برای کودک برنامهٔ خواب بسازیم") },
                supportingText = { Text("${title.trim().length.toPersianDigits()} / ۱۴۰") })

            Label("خلاصه (اختیاری)")
            OutlinedTextField(summary, { v -> change { summary = v.take(600) } }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                placeholder = { Text("یکی دو جمله که در فهرست‌ها دیده می‌شود") })

            if (kind == "video") {
                Label("ویدیو")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("upload" to "بارگذاریِ فایل", "aparat" to "لینکِ آپارات").forEachIndexed { i, (k, l) ->
                        SegmentedButton(selected = source == k, onClick = { change { source = k } }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                Spacer(Modifier.height(Dimens.s))
                if (source == "upload") {
                    AppCard {
                        val p = uploadProgress
                        val shownName = if (p != null) uploadingName else videoName
                        val shownSize = if (p != null) uploadingSize else videoSize
                        if (shownName.isBlank() && p == null) {
                            Text("فایلِ mp4 تا ۲۵۰ مگابایت. بارگذاری با انتخابِ فایل شروع می‌شود.", style = MaterialTheme.typography.bodySmall, color = muted())
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val done = p == null && videoId != null
                                Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Movie, null,
                                    tint = if (done) com.hamraheteyf.app.ui.theme.VerifiedGreen else muted())
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(shownName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOfNotNull(shownSize.takeIf { it > 0 }?.let { sizeFa(it) },
                                        when { p != null -> "در حالِ بارگذاری ${p.toPersianDigits()}٪"; videoId != null -> "بارگذاری شد"; else -> null }).joinToString(" · ")
                                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = muted())
                                }
                                if (p != null) TextButton(onClick = {
                                    uploadGen++; uploadJob?.cancel(); uploadJob = null; uploadProgress = null; uploadingName = ""; uploadingSize = -1
                                }) { Text("لغو") }
                            }
                            if (p != null) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth()) }
                        }
                        uploadError?.let { Spacer(Modifier.height(4.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { pickVideo.launch("video/mp4") }, enabled = uploadProgress == null, modifier = Modifier.heightIn(min = Dimens.touch)) {
                            Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(if (videoId != null || videoName.isNotBlank()) "انتخابِ ویدیوی دیگر" else "انتخابِ ویدیو از گوشی")
                        }
                    }
                    if (showErrors && videoErr && uploadProgress == null) ErrText("یک فایلِ ویدیو انتخاب کنید.")
                } else {
                    OutlinedTextField(videoUrl, { v -> change { videoUrl = v.trim() } }, Modifier.fillMaxWidth(), singleLine = true,
                        isError = (showErrors || videoUrl.isNotBlank()) && !aparatOk,
                        placeholder = { Text("https://www.aparat.com/v/…") },
                        supportingText = { Text(if (videoUrl.isNotBlank() && !aparatOk) "این لینکِ آپارات نیست." else "فقط لینکِ آپارات پذیرفته می‌شود.") })
                }
            } else {
                Label("متنِ مطلب")
                OutlinedTextField(body, { v -> change { body = v.take(40000) } }, Modifier.fillMaxWidth().heightIn(min = 220.dp), minLines = 10,
                    isError = showErrors && bodyErr,
                    placeholder = { Text("متن را بنویسید…") },
                    supportingText = { Text("برای تیتر، خط را با «## » و برای فهرست با «- » شروع کنید. (${body.trim().length.toPersianDigits()} نویسه، دست‌کم ۵۰)") })
            }

            Label(if (kind == "video") "تصویرِ کاور (اختیاری)" else "تصویرِ کاور")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val cid = coverId
                if (cid != null && client != null) {
                    RemoteImage(client.fileUrl(cid), Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(Dimens.s))
                }
                OutlinedButton(onClick = { pickCover.launch("image/*") }, enabled = !coverBusy, modifier = Modifier.heightIn(min = Dimens.touch)) {
                    if (coverBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (cid == null) "انتخابِ تصویر" else "تغییر")
                }
                if (cid != null) TextButton(onClick = { change { coverId = null } }) { Text("حذف") }
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("خروج بدونِ ارسال؟") },
        text = { Text(if (uploadProgress != null) "بارگذاریِ ویدیو هنوز تمام نشده و تغییراتِ شما ذخیره نمی‌شود." else "تغییراتی که داده‌اید ذخیره نمی‌شود.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; uploadJob?.cancel(); onBack() }) { Text("خروج", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("ماندن") } },
    )
    result?.let { st ->
        val published = st == "PUBLISHED"
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(if (published) Icons.Outlined.CheckCircle else Icons.Outlined.HourglassTop, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(if (published) "منتشر شد" else "برای بررسی فرستاده شد") },
            text = { Text(if (published) "مطلب در بخشِ آموزش‌ها منتشر شد." else "سپاس از همراهی‌تان. پس از بررسیِ مدیر، نتیجه را با اعلان خبر می‌دهیم. وضعیت را در «مطالبِ من» هم می‌بینید.") },
            confirmButton = { TextButton(onClick = { result = null; onDone() }) { Text("باشه") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Dimens.l, bottom = 6.dp))
}

@Composable
private fun ErrText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
}
