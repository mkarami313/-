package com.hamraheteyf.app.ui.modules

import android.Manifest
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch
import java.io.File

private val BuiltInAac = listOf(
    "💧" to "آب می‌خواهم", "🍽️" to "غذا می‌خواهم", "🚽" to "دستشویی", "🆘" to "کمک", "✅" to "بله", "❌" to "نه",
    "🎮" to "بازی", "😴" to "خسته‌ام", "❤️" to "دوستت دارم", "🤕" to "درد دارم", "🤗" to "بغل", "🏁" to "تمام شد",
)

private class AacCard(val emoji: String, val text: String, val imageId: String, val audioId: String, val rec: Rec?, val builtin: Boolean)

/** What the editor is editing: an existing record, or a new card (optionally a built-in being customised). */
private class EditTarget(val rec: Rec?, val builtinText: String?, val builtinEmoji: String?)

private fun String.fileId() = substringAfterLast('/')

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AacScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val ctx = LocalContext.current
    val client = rememberRecClient(); val api = container.serverApi
    var tick by remember { mutableIntStateOf(0) }
    val st = rememberRecs(client, "aac", tick)
    val speaker = rememberSpeaker()
    val childrenState by remember { container.userRepository.observeChildren() }.collectAsState(null)
    val children = childrenState.orEmpty()
    val prefs = remember { ctx.getSharedPreferences("hamrah_aac", 0) }
    var profile by rememberSaveable { mutableStateOf(prefs.getString("profile", "") ?: "") }
    LaunchedEffect(childrenState, profile) {
        val loaded = childrenState
        if (loaded != null && profile.isNotBlank() && loaded.none { it.id == profile }) {
            profile = ""; prefs.edit().putString("profile", "").apply()
        }
    }
    var sentence by rememberSaveable(stateSaver = listSaver<List<Pair<String, String?>>, String>(
        save = { l -> l.map { it.first + "\u0000" + (it.second ?: "") } },
        restore = { l -> l.map { v -> v.substringBefore('\u0000') to v.substringAfter('\u0000').ifBlank { null } } },
    )) { mutableStateOf(listOf<Pair<String, String?>>()) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }

    val recs = st.items.orEmpty()
    fun override(key: String) = recs.firstOrNull { it.get("builtin") == key && it.get("childId") == profile }
        ?: recs.firstOrNull { it.get("builtin") == key && it.get("childId").isBlank() }
    val cards = BuiltInAac.map { (e, t) ->
        val o = override(t)
        AacCard(o?.get("emoji")?.ifBlank { null } ?: e, t, o?.get("image").orEmpty(), o?.get("audio").orEmpty(), o, true)
    } + recs.filter { it.get("builtin").isBlank() && (it.get("childId") == profile || it.get("childId").isBlank()) }
        .map { AacCard(it.get("emoji").ifBlank { "💬" }, it.get("text"), it.get("image"), it.get("audio"), it, false) }

    fun targetFor(key: String?): EditTarget? = when {
        key == null -> null
        key == "new" -> EditTarget(null, null, null)
        key.startsWith("rec:") -> recs.firstOrNull { it.id == key.removePrefix("rec:") }?.let { EditTarget(it, null, null) }
        key.startsWith("b:") -> BuiltInAac.firstOrNull { it.second == key.removePrefix("b:") }?.let { EditTarget(override(it.second), it.second, it.first) }
        else -> null
    }
    val edit = targetFor(editKey)
    fun keyOf(card: AacCard) = if (card.builtin) "b:" + card.text else "rec:" + card.rec?.id

    Scaffold(topBar = { ScreenHeader("ارتباط تصویری", onBack = onBack) }, floatingActionButton = { AddFab("کارت جدید") { editKey = "new" } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (children.isNotEmpty()) {
                LazyRow(Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(profile.isBlank(), { profile = ""; prefs.edit().putString("profile", "").apply() }, label = { Text("همه") }) }
                    items(children.size) { i ->
                        val c = children[i]
                        FilterChip(profile == c.id, { profile = c.id; prefs.edit().putString("profile", c.id).apply() }, label = { Text(c.nickname?.takeIf { it.isNotBlank() } ?: "کودک ${(i + 1).toPersianDigits()}") })
                    }
                }
            }
            InfoBanner("این ابزار فقط کمک ارتباطی روزمره است و جایگزین ارزیابی یا برنامهٔ گفتاردرمانگر نیست.", Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp), dismissKey = "aac_medical_notice")
            InfoBanner("روی هر کارت انگشت را نگه دارید تا صدای خودتان را ضبط یا عکس را عوض کنید. با «کارت جدید» کارت دلخواه با عکس و صدا بسازید.", Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp), dismissKey = "aac_hint")
            Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sentence.joinToString(" ") { it.first }.ifBlank { "کارت‌ها را بزنید تا جمله بسازید" }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { if (sentence.isNotEmpty()) speaker.say(sentence) }) { Icon(Icons.Outlined.VolumeUp, "بخوان") }
                    IconButton(onClick = { sentence = emptyList() }) { Icon(Icons.Outlined.Backspace, "پاک کردن") }
                }
            }
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 88.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards, key = { keyOf(it) }) { card ->
                    val label = card.text + if (card.audioId.isNotBlank()) "، با صدای ضبط‌شده" else ""
                    Surface(
                        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = label
                                customActions = listOf(CustomAccessibilityAction("ویرایش کارت") { editKey = keyOf(card); true })
                            }
                            .combinedClickable(
                                onClickLabel = "افزودن به جمله و گفتن",
                                onClick = { sentence = sentence + (card.text to card.audioId.ifBlank { null }); speaker(card.text, card.audioId.ifBlank { null }) },
                                onLongClickLabel = "ویرایش کارت",
                                onLongClick = { editKey = keyOf(card) },
                            ),
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                if (card.imageId.isNotBlank() && api != null) RemoteImage(api.fileUrl(card.imageId), Modifier.fillMaxWidth().height(64.dp).clip(MaterialTheme.shapes.small), ContentScale.Crop)
                                else Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) { Text(card.emoji, fontSize = 34.sp) }
                                Spacer(Modifier.height(2.dp))
                                Text(card.text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                            }
                            if (card.audioId.isNotBlank()) Icon(Icons.Outlined.Mic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp))
                        }
                    }
                }
            }
        }
    }
    edit?.let { t ->
        if (client != null && api != null) AacEditor(client, api, speaker, t, profile, children.isNotEmpty(), { editKey = null }, { editKey = null; tick++ })
    }
}

@Composable
private fun AacEditor(client: RecClient, api: com.hamraheteyf.app.net.HamrahApi, speaker: Speaker, target: EditTarget, profile: String, hasProfiles: Boolean, onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val rec = target.rec
    val isBuiltin = target.builtinText != null
    val wasGlobal = rec != null && rec.get("childId").isBlank()
    var text by rememberSaveable { mutableStateOf(rec?.get("text") ?: target.builtinText.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(rec?.get("emoji") ?: target.builtinEmoji.orEmpty()) }
    var imageId by rememberSaveable { mutableStateOf(rec?.get("image").orEmpty()) }
    var audioId by rememberSaveable { mutableStateOf(rec?.get("audio").orEmpty()) }
    // Picked image / recorded clip live in cache files; only their paths survive rotation.
    var newImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var newAudioPath by rememberSaveable { mutableStateOf<String?>(null) }
    val newImage = remember(newImagePath) { newImagePath?.let { File(it) }?.takeIf { it.exists() }?.readBytes() }
    val newAudio = remember(newAudioPath) { newAudioPath?.let { File(it) }?.takeIf { it.exists() } }
    var forAll by rememberSaveable { mutableStateOf(wasGlobal || profile.isBlank()) }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    fun toggleRecord() {
        if (recording) { newAudioPath = recorder.stop()?.path; recording = false; if (newAudioPath == null) error = "صدایی ضبط نشد؛ دوباره امتحان کنید." }
        else if (recorder.start { newAudioPath = recorder.stop()?.path; recording = false }) { recording = true; error = null }
        else error = "ضبط شروع نشد."
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) toggleRecord() else Toast.makeText(ctx, "برای ضبط صدا اجازهٔ میکروفون لازم است.", Toast.LENGTH_LONG).show()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val b = prepareImageForUpload(ctx, uri)
            if (b == null) error = "عکس خوانده نشد." else {
                val f = File(ctx.cacheDir, "aac_pending_${System.currentTimeMillis()}.jpg"); f.writeBytes(b); newImagePath = f.path; error = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (isBuiltin) "شخصی‌سازی «${target.builtinText}»" else if (rec == null) "کارت جدید" else "ویرایش کارت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isBuiltin) OutlinedTextField(text, { text = it.take(80) }, label = { Text("متن کارت (همان که گفته می‌شود)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emoji, { emoji = it.take(8) }, label = { Text("ایموجی (وقتی عکس ندارد)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("عکس", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val bmp = remember(newImage) { newImage?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    val box = Modifier.size(72.dp).clip(MaterialTheme.shapes.small)
                    when {
                        bmp != null -> Image(bmp.asImageBitmap(), null, box, contentScale = ContentScale.Crop)
                        imageId.isNotBlank() -> RemoteImage(api.fileUrl(imageId), box)
                    }
                    OutlinedButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(6.dp)); Text(if (newImage != null || imageId.isNotBlank()) "تغییر عکس" else "انتخاب عکس") }
                    if (newImage != null || imageId.isNotBlank()) TextButton(onClick = { newImagePath = null; imageId = "" }) { Text("حذف") }
                }

                Text("صدا", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (recording || androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) toggleRecord()
                        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                    }, colors = if (recording) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()) {
                        Icon(if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic, null); Spacer(Modifier.width(6.dp))
                        Text(if (recording) "توقف" else if (newAudio != null || audioId.isNotBlank()) "ضبط دوباره" else "ضبط صدا")
                    }
                    if (!recording && (newAudio != null || audioId.isNotBlank())) {
                        IconButton(onClick = { newAudio?.let { speaker.playLocal(it) } ?: speaker(text, audioId) }) { Icon(Icons.Outlined.PlayArrow, "پخش صدای کارت") }
                        TextButton(onClick = { newAudioPath = null; audioId = "" }) { Text("حذف") }
                    }
                }
                Text(if (recording) "در حال ضبط… (حداکثر ۱۵ ثانیه)" else if (newAudio != null) "صدا ضبط شد." else if (audioId.isNotBlank()) "صدای ضبط‌شدهٔ خانواده فعال است." else "بدون ضبط، صدای خودکار فارسی خوانده می‌شود.",
                    style = MaterialTheme.typography.labelMedium, color = mutedColor())

                if (hasProfiles && profile.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(forAll, { forAll = it }); Text("برای همهٔ پروفایل‌ها", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        when {
                            wasGlobal && forAll -> "این کارت الان برای همهٔ پروفایل‌ها یکسان است و تغییر شما روی همه اعمال می‌شود."
                            wasGlobal -> "با برداشتن تیک، یک نسخهٔ جدا فقط برای این پروفایل ساخته می‌شود و کارت مشترک برای بقیه دست‌نخورده می‌ماند."
                            forAll -> "کارت برای همهٔ پروفایل‌ها نمایش داده می‌شود."
                            else -> "کارت فقط برای پروفایل انتخاب‌شده نمایش داده می‌شود."
                        },
                        style = MaterialTheme.typography.labelMedium, color = mutedColor(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && !recording && text.isNotBlank(), onClick = {
                scope.launch {
                    busy = true; error = null
                    var img = imageId; var aud = audioId
                    newImage?.let { b -> when (val u = api.upload(b, "image/jpeg", "aac.jpg")) { is ApiResult.Ok -> img = u.data.fileId(); is ApiResult.Fail -> { error = u.message; busy = false; return@launch } } }
                    newAudio?.let { f -> when (val u = api.upload(f.readBytes(), "audio/mp4", "aac.m4a")) { is ApiResult.Ok -> aud = u.data.fileId(); is ApiResult.Fail -> { error = u.message; busy = false; return@launch } } }
                    val data = mapOf("text" to text.trim(), "emoji" to emoji.trim(), "image" to img, "audio" to aud,
                        "builtin" to (target.builtinText ?: ""), "childId" to if (forAll) "" else profile)
                    val saveId = if (wasGlobal && !forAll) null else rec?.id
                    when (val r = client.save("aac", saveId, data)) { is ApiResult.Ok -> onDone(); is ApiResult.Fail -> error = r.message }
                    busy = false
                }
            }) { Text(if (busy) "…" else "ذخیره") }
        },
        dismissButton = {
            Row {
                if (rec != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text(if (isBuiltin) "بازگشت به پیش‌فرض" else "حذف کارت", color = MaterialTheme.colorScheme.error) }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") }
            }
        },
    )
    if (confirmDelete && rec != null) {
        ConfirmDialog((if (isBuiltin) "عکس و صدای شخصی این کارت پاک شود؟" else "این کارت حذف شود؟") + if (wasGlobal && profile.isNotBlank()) " (این کارت مشترک است و برای همهٔ پروفایل‌ها پاک می‌شود.)" else "", onYes = {
            confirmDelete = false; scope.launch { client.delete("aac", rec.id); onDone() }
        }, onDismiss = { confirmDelete = false })
    }
}
