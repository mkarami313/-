package com.hamraheteyf.app.ui.modules

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch
import java.util.Locale

private fun speechChunks(text: String): List<String> {
    val out = mutableListOf<String>(); val cur = StringBuilder()
    for (part in text.split(Regex("(?<=[.!؟?!،\\n])"))) {
        if (cur.length + part.length > 250 && cur.isNotEmpty()) { out += cur.toString().trim(); cur.clear() }
        cur.append(part)
    }
    if (cur.isNotBlank()) out += cur.toString().trim()
    return out.filter { it.isNotBlank() }.flatMap { it.chunked(280) }
}

/**
 * Speaks Persian text. Primary path: server-side voice (Piper), cached on the device, since
 * stock Android TTS usually has no Persian voice. Falls back to the phone's own voice if present.
 */
@Composable
fun rememberSpeaker(): (String) -> Unit {
    val ctx = LocalContext.current
    val api = com.hamraheteyf.app.di.LocalAppContainer.current.serverApi
    val scope = rememberCoroutineScope()
    val job = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val player = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        tts[0] = TextToSpeech(ctx.applicationContext) { st -> if (st == TextToSpeech.SUCCESS) tts[0]?.language = Locale("fa") }
        onDispose { job[0]?.cancel(); player[0]?.release(); tts[0]?.shutdown() }
    }
    suspend fun play(file: java.io.File) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        val mp = android.media.MediaPlayer()
        player[0] = mp
        mp.setDataSource(file.path)
        mp.setOnCompletionListener { mp.release(); if (cont.isActive) cont.resume(Unit) {} }
        mp.setOnErrorListener { _, _, _ -> mp.release(); if (cont.isActive) cont.resume(Unit) {}; true }
        cont.invokeOnCancellation { runCatching { mp.stop(); mp.release() } }
        mp.prepare(); mp.start()
    }
    return { text ->
        job[0]?.cancel(); tts[0]?.stop()
        job[0] = scope.launch {
            val dir = java.io.File(ctx.cacheDir, "tts").apply { mkdirs() }
            var failed = false
            for (chunk in speechChunks(text)) {
                val f = java.io.File(dir, java.security.MessageDigest.getInstance("SHA-1").digest(chunk.toByteArray()).joinToString("") { "%02x".format(it) } + ".wav")
                if (!f.exists()) {
                    val bytes = api?.tts(chunk)
                    if (bytes == null) { failed = true; break }
                    f.writeBytes(bytes)
                }
                play(f)
            }
            if (failed) {
                val t = tts[0]
                if (t != null && t.isLanguageAvailable(Locale("fa")) >= 0) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u")
                else Toast.makeText(ctx, "برای خواندن صوتی، اینترنت لازم است.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private object Done {
    fun key(id: String) = "s_${id}_${System.currentTimeMillis() / 86_400_000L}"
    fun get(c: android.content.Context, k: String) = c.getSharedPreferences("hamrah_done", 0).getStringSet(k, emptySet()).orEmpty()
    fun toggle(c: android.content.Context, k: String, i: String) {
        val s = get(c, k).toMutableSet(); if (!s.add(i)) s.remove(i)
        c.getSharedPreferences("hamrah_done", 0).edit().putStringSet(k, s).apply()
    }
}

@Composable
fun ToolsHubScreen(onBack: () -> Unit, onSchedule: () -> Unit, onStories: () -> Unit, onAac: () -> Unit) {
    Scaffold(topBar = { ScreenHeader("ابزارهای روزانه", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HubTile("برنامهٔ تصویری", "فهرست مراحل روز (مسواک، لباس، مدرسه…) با تیک‌زدن و پیشرفت روزانه", Icons.Outlined.ChecklistRtl, onSchedule)
            HubTile("داستان اجتماعی", "داستان‌های کوتاه برای آماده‌کردن کودک برای موقعیت‌ها (دندان‌پزشک، مهمانی…)", Icons.Outlined.AutoStories, onStories)
            HubTile("ارتباط تصویری", "کارت‌های ارتباطی با صدا؛ بزنید تا گوشی بخواند", Icons.Outlined.RecordVoiceOver, onAac)
        }
    }
}

@Composable
private fun HubTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    CompactCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column { Text(title, style = MaterialTheme.typography.titleSmall); Text(sub, style = MaterialTheme.typography.labelMedium, color = mutedColor()) }
        }
    }
}

private val ScheduleFields = listOf(FieldSpec("title", "عنوان برنامه", hint = "مثلاً روتین صبح"), FieldSpec("steps", "مراحل", long = true, hint = "هر مرحله در یک خط؛ می‌توانید اول خط یک ایموجی بگذارید: 🪥 مسواک زدن"))
private const val SAMPLE_STEPS = "🛏️ بیدار شدن\n🚽 دستشویی\n🪥 مسواک زدن\n👕 لباس پوشیدن\n🍳 صبحانه\n🎒 آماده‌شدن برای مدرسه"

@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val client = rememberRecClient(); val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val st = rememberRecs(client, "schedule", tick)
    Scaffold(topBar = { ScreenHeader("برنامهٔ تصویری", onBack = onBack) }, floatingActionButton = { AddFab("برنامهٔ جدید") { adding = true } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (st.items?.isEmpty() == true && client != null) {
                TextButton(onClick = { scope.launch { client.save("schedule", null, mapOf("title" to "روتین صبح", "steps" to SAMPLE_STEPS)); tick++ } }, Modifier.padding(12.dp)) { Text("افزودن نمونهٔ «روتین صبح»") }
            }
            RecListBody(st, "برنامه‌ای ندارید. مراحل کارهای روزانه را بسازید تا کودک یکی‌یکی تیک بزند.", { tick++ }) { r ->
                val steps = r.lines("steps")
                val dk = Done.key(r.id)
                val done = remember(refresh, r.id) { Done.get(ctx, dk) }
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r["title"], style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${done.size.toPersianDigits()} از ${steps.size.toPersianDigits()}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                        OwnerActions({ editing = r }, { deleting = r })
                    }
                    LinearProgressIndicator(progress = { if (steps.isEmpty()) 0f else done.size.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    steps.forEachIndexed { i, s ->
                        val on = i.toString() in done
                        Row(Modifier.fillMaxWidth().clickable { Done.toggle(ctx, dk, i.toString()); refresh++ }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { Done.toggle(ctx, dk, i.toString()); refresh++ }, modifier = Modifier.size(36.dp))
                            Text(s, style = MaterialTheme.typography.bodyLarge, color = if (on) mutedColor() else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        RecFormDialog(client, "schedule", "برنامه", ScheduleFields, editing, { adding = false; editing = null }, { adding = false; editing = null; tick++ })
    }
    deleting?.let { d -> ConfirmDialog("این برنامه حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("schedule", d.id); tick++ } }, onDismiss = { deleting = null }) }
}

private val StoryFields = listOf(FieldSpec("title", "عنوان داستان"), FieldSpec("pages", "صفحه‌ها", long = true, hint = "صفحه‌ها را با یک خط خالی از هم جدا کنید"))
private const val SAMPLE_STORY = "امروز می‌خواهم به دندان‌پزشک بروم.\n\nدندان‌پزشک به دندان‌های من نگاه می‌کند تا سالم بمانند.\n\nروی صندلی می‌نشینم و دهانم را باز می‌کنم. اگر نخواستم، دستم را بالا می‌برم.\n\nبعد از تمام‌شدن، مامان یا بابا من را بغل می‌کند. من کار خوبی کردم."

@Composable
fun StoriesScreen(onBack: () -> Unit) {
    val client = rememberRecClient(); val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    var reading by remember { mutableStateOf<Rec?>(null) }
    val st = rememberRecs(client, "story", tick)
    val speak = rememberSpeaker()
    Scaffold(topBar = { ScreenHeader("داستان اجتماعی", onBack = onBack) }, floatingActionButton = { AddFab("داستان جدید") { adding = true } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (st.items?.isEmpty() == true && client != null) {
                TextButton(onClick = { scope.launch { client.save("story", null, mapOf("title" to "رفتن به دندان‌پزشک", "pages" to SAMPLE_STORY)); tick++ } }, Modifier.padding(12.dp)) { Text("افزودن نمونهٔ «دندان‌پزشک»") }
            }
            RecListBody(st, "داستانی ندارید. برای آماده‌کردن کودک برای یک موقعیت، داستان کوتاه بسازید.", { tick++ }) { r ->
                CompactCard(onClick = { reading = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r["title"], style = MaterialTheme.typography.titleSmall)
                            Text("${storyPages(r).size.toPersianDigits()} صفحه", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                        }
                        OwnerActions({ editing = r }, { deleting = r })
                    }
                }
            }
        }
    }
    reading?.let { r ->
        val pages = storyPages(r)
        var i by remember(r.id) { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { reading = null },
            title = { Text(r["title"]) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(pages.getOrElse(i) { "" }, fontSize = 22.sp, lineHeight = 36.sp, textAlign = TextAlign.Center, modifier = Modifier.heightIn(min = 160.dp).verticalScroll(rememberScrollState()))
                    Text("${(i + 1).toPersianDigits()} / ${pages.size.toPersianDigits()}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                    TextButton(onClick = { speak(pages.getOrElse(i) { "" }) }) { Icon(Icons.Outlined.VolumeUp, null); Spacer(Modifier.width(6.dp)); Text("بخوان") }
                }
            },
            confirmButton = { TextButton(enabled = i < pages.size - 1, onClick = { i++ }) { Text("بعدی") } },
            dismissButton = { Row { TextButton(enabled = i > 0, onClick = { i-- }) { Text("قبلی") }; TextButton(onClick = { reading = null }) { Text("بستن") } } },
        )
    }
    if ((adding || editing != null) && client != null) {
        RecFormDialog(client, "story", "داستان", StoryFields, editing, { adding = false; editing = null }, { adding = false; editing = null; tick++ })
    }
    deleting?.let { d -> ConfirmDialog("این داستان حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("story", d.id); tick++ } }, onDismiss = { deleting = null }) }
}

private fun storyPages(r: Rec) = r["pages"].split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

private val BuiltInAac = listOf(
    "💧" to "آب می‌خواهم", "🍽️" to "غذا می‌خواهم", "🚽" to "دستشویی", "🆘" to "کمک", "✅" to "بله", "❌" to "نه",
    "🎮" to "بازی", "😴" to "خسته‌ام", "❤️" to "دوستت دارم", "🤕" to "درد دارم", "🤗" to "بغل", "🏁" to "تمام شد",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AacScreen(onBack: () -> Unit) {
    val client = rememberRecClient(); val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    val st = rememberRecs(client, "aac", tick)
    val speak = rememberSpeaker()
    var sentence by remember { mutableStateOf(listOf<String>()) }
    val cards = BuiltInAac.map { Triple(it.first, it.second, null as Rec?) } + (st.items.orEmpty().map { Triple(it["emoji"].ifBlank { "💬" }, it["text"], it as Rec?) })
    Scaffold(topBar = { ScreenHeader("ارتباط تصویری", onBack = onBack) }, floatingActionButton = { AddFab("کارت جدید") { adding = true } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sentence.joinToString(" ").ifBlank { "کارت‌ها را بزنید تا جمله بسازید" }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { if (sentence.isNotEmpty()) speak(sentence.joinToString(" ")) }) { Icon(Icons.Outlined.VolumeUp, "بخوان") }
                    IconButton(onClick = { sentence = emptyList() }) { Icon(Icons.Outlined.Backspace, "پاک کردن") }
                }
            }
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 88.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cards) { (emoji, text, rec) ->
                    Surface(
                        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.aspectRatio(1f).combinedClickable(onClick = { sentence = sentence + text; speak(text) }, onLongClick = { if (rec != null) deleting = rec }),
                    ) {
                        Column(Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(emoji, fontSize = 34.sp)
                            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
    if (adding && client != null) {
        RecFormDialog(client, "aac", "کارت جدید", listOf(FieldSpec("text", "متن (همان که گفته می‌شود)"), FieldSpec("emoji", "ایموجی", hint = "مثلاً 🍎")), null, { adding = false }, { adding = false; tick++ })
    }
    deleting?.let { d -> ConfirmDialog("کارت «${d["text"]}» حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("aac", d.id); tick++ } }, onDismiss = { deleting = null }) }
}
