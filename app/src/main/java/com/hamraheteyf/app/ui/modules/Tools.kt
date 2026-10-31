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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.hamraheteyf.app.di.LocalAppContainer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch
import java.util.Locale

private object Done {
    fun key(id: String) = "s_${id}_${System.currentTimeMillis() / 86_400_000L}"
    fun get(c: android.content.Context, k: String) = c.getSharedPreferences("hamrah_done", 0).getStringSet(k, emptySet()).orEmpty()
    fun toggle(c: android.content.Context, k: String, i: String) {
        val s = get(c, k).toMutableSet(); if (!s.add(i)) s.remove(i)
        c.getSharedPreferences("hamrah_done", 0).edit().putStringSet(k, s).apply()
    }
}

@Composable
fun ToolsHubScreen(
    onBack: () -> Unit, onSchedule: () -> Unit, onStories: () -> Unit, onAac: () -> Unit,
    onLog: () -> Unit = {}, onEmergency: () -> Unit = {}, onReward: () -> Unit = {}, onFirstThen: () -> Unit = {}, onTimer: () -> Unit = {},
) {
    Scaffold(topBar = { ScreenHeader("ابزارهای روزانه", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HubTile("ثبت رفتار و خواب", "ثبت سریع رفتار، خواب و خلق‌وخو با آمار ۳۰ روزه", Icons.Outlined.Insights, onLog)
            HubTile("کارت اضطراری کودک", "اطلاعات تماس و نکات مهم برای گم‌شدن یا شرایط اضطراری", Icons.Outlined.Emergency, onEmergency)
            HubTile("برنامهٔ تصویری", "فهرست مراحل روز (مسواک، لباس، مدرسه…) با تیک‌زدن و پیشرفت روزانه", Icons.Outlined.ChecklistRtl, onSchedule)
            HubTile("داستان اجتماعی", "داستان‌های کوتاه برای آماده‌کردن کودک برای موقعیت‌ها (دندان‌پزشک، مهمانی…)", Icons.Outlined.AutoStories, onStories)
            HubTile("ارتباط تصویری", "کارت‌های ارتباطی با صدا؛ بزنید تا گوشی بخواند", Icons.Outlined.RecordVoiceOver, onAac)
            HubTile("اول-بعد", "تابلوی «اول این، بعد آن» برای کمک به انتقال بین فعالیت‌ها", Icons.Outlined.SwapHoriz, onFirstThen)
            HubTile("نمودار پاداش", "ستاره‌های روزانه تا رسیدن به هدف و پاداش", Icons.Outlined.Star, onReward)
            HubTile("تایمر تصویری", "زمان باقی‌مانده را به شکل دایرهٔ رنگی نشان می‌دهد", Icons.Outlined.Timer, onTimer)
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
    val api = LocalAppContainer.current.serverApi
    Scaffold(topBar = { ScreenHeader("برنامهٔ تصویری", onBack = onBack) }, floatingActionButton = { AddFab("برنامهٔ جدید") { adding = true } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (st.items?.isEmpty() == true && client != null) {
                TextButton(onClick = { scope.launch { client.save("schedule", null, mapOf("title" to "روتین صبح", "steps" to SAMPLE_STEPS)); tick++ } }, Modifier.padding(12.dp)) { Text("افزودن نمونهٔ «روتین صبح»") }
            }
            RecListBody(st, "برنامه‌ای ندارید. مراحل کارهای روزانه را بسازید تا کودک یکی‌یکی تیک بزند.", { tick++ }) { r ->
                val steps = r.lines("steps")
                val stepImgs = parseImageList(r["stepImages"], steps.size)
                val dk = Done.key(r.id)
                val done = remember(refresh, r.id) { Done.get(ctx, dk) }
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r["cover"].isNotBlank() && api != null) { RemoteImage(api.fileUrl(r["cover"]), Modifier.size(44.dp).clip(MaterialTheme.shapes.small)); Spacer(Modifier.width(10.dp)) }
                        Text(r["title"], style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${done.size.toPersianDigits()} از ${steps.size.toPersianDigits()}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                        OwnerActions({ editing = r }, { deleting = r })
                    }
                    LinearProgressIndicator(progress = { if (steps.isEmpty()) 0f else done.size.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    steps.forEachIndexed { i, s ->
                        val on = i.toString() in done
                        Row(Modifier.fillMaxWidth().clickable { Done.toggle(ctx, dk, i.toString()); refresh++ }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { Done.toggle(ctx, dk, i.toString()); refresh++ }, modifier = Modifier.size(36.dp))
                            if (stepImgs[i].isNotBlank() && api != null) { RemoteImage(api.fileUrl(stepImgs[i]), Modifier.size(44.dp).clip(MaterialTheme.shapes.small)); Spacer(Modifier.width(8.dp)) }
                            Text(s, style = MaterialTheme.typography.bodyLarge, color = if (on) mutedColor() else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        MediaItemsEditor(client, "schedule", if (editing == null) "برنامهٔ جدید" else "ویرایش برنامه", editing, "title", "عنوان برنامه", "steps", "stepImages", "مرحله", "مراحل (به ترتیب)", false, { adding = false; editing = null }, { adding = false; editing = null; tick++ })
    }
    deleting?.let { d -> ConfirmDialog("این برنامه حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("schedule", d.id); tick++ } }, onDismiss = { deleting = null }) }
}

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
    val api = LocalAppContainer.current.serverApi
    val speak = rememberSpeaker()
    Scaffold(topBar = { ScreenHeader("داستان اجتماعی", onBack = onBack) }, floatingActionButton = { AddFab("داستان جدید") { adding = true } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (st.items?.isEmpty() == true && client != null) {
                TextButton(onClick = { scope.launch { client.save("story", null, mapOf("title" to "رفتن به دندان‌پزشک", "pages" to SAMPLE_STORY)); tick++ } }, Modifier.padding(12.dp)) { Text("افزودن نمونهٔ «دندان‌پزشک»") }
            }
            RecListBody(st, "داستانی ندارید. برای آماده‌کردن کودک برای یک موقعیت، داستان کوتاه بسازید.", { tick++ }) { r ->
                CompactCard(onClick = { reading = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r["cover"].isNotBlank() && api != null) RemoteImage(api.fileUrl(r["cover"]), Modifier.size(44.dp).clip(MaterialTheme.shapes.small))
                        else Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
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
        val pageImgs = parseImageList(r["pageImages"], pages.size)
        var i by remember(r.id) { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { reading = null },
            title = { Text(r["title"]) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val img = pageImgs.getOrElse(i) { "" }.ifBlank { if (i == 0) r["cover"] else "" }
                    if (img.isNotBlank() && api != null) {
                        RemoteImage(api.fileUrl(img), Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium))
                        Spacer(Modifier.height(8.dp))
                    }
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
        MediaItemsEditor(client, "story", if (editing == null) "داستان جدید" else "ویرایش داستان", editing, "title", "عنوان داستان", "pages", "pageImages", "صفحه", "صفحه‌ها (به ترتیب)", true, { adding = false; editing = null }, { adding = false; editing = null; tick++ })
    }
    deleting?.let { d -> ConfirmDialog("این داستان حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("story", d.id); tick++ } }, onDismiss = { deleting = null }) }
}

private fun storyPages(r: Rec) = r["pages"].split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

private class ItemDraft(text: String, imageId: String) {
    var text by mutableStateOf(text)
    var imageId by mutableStateOf(imageId)
    var bytes by mutableStateOf<ByteArray?>(null)
}

/** Editor for a titled record with a cover photo and an ordered list of items (steps/pages), each with an optional photo. */
@Composable
private fun MediaItemsEditor(
    client: RecClient, kind: String, dialogTitle: String, initial: Rec?,
    titleKey: String, titleLabel: String, textKey: String, imagesKey: String, itemWord: String, listLabel: String, paged: Boolean,
    onDismiss: () -> Unit, onDone: () -> Unit,
) {
    val api = LocalAppContainer.current.serverApi
    val drafts = remember {
        val texts = if (initial == null) emptyList() else if (paged) storyPages(initial) else initial.lines(textKey)
        val ids = parseImageList(initial?.get(imagesKey).orEmpty(), texts.size)
        mutableStateListOf<ItemDraft>().also { l -> texts.forEachIndexed { i, t -> l.add(ItemDraft(t, ids[i])) }; if (l.isEmpty()) l.add(ItemDraft("", "")) }
    }
    var coverBytes by remember { mutableStateOf<ByteArray?>(null) }
    RecEditorDialog(
        client, kind, dialogTitle, initial, emptyMap(), onDismiss, onDone,
        canSave = { it[titleKey].orEmpty().isNotBlank() && drafts.any { d -> d.text.isNotBlank() } },
        beforeSave = { data ->
            if (api == null) return@RecEditorDialog "اتصال به سرور برقرار نیست."
            coverBytes?.let { b ->
                when (val u = api.uploadImageId(b)) {
                    is ApiResult.Ok -> data["cover"] = u.data
                    is ApiResult.Fail -> return@RecEditorDialog u.message
                }
            }
            val kept = drafts.filter { it.text.isNotBlank() }
            for (d in kept) {
                d.bytes?.let { b ->
                    when (val u = api.uploadImageId(b)) {
                        is ApiResult.Ok -> { d.imageId = u.data; d.bytes = null }
                        is ApiResult.Fail -> return@RecEditorDialog u.message
                    }
                }
            }
            data[textKey] = kept.joinToString(if (paged) "\n\n" else "\n") { it.text.trim().replace(Regex("\\n\\s*\\n"), "\n") }
            data[imagesKey] = encodeImageList(kept.map { it.imageId })
            null
        },
    ) { v, setError ->
        OutlinedTextField(v[titleKey].orEmpty(), { v[titleKey] = it }, label = { Text(titleLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ImagePickRow(api, "تصویر جلد", v["cover"].orEmpty(), coverBytes, { coverBytes = it }, { coverBytes = null; v["cover"] = "" }, setError)
        SectionLabel(listLabel)
        drafts.forEachIndexed { i, d ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        d.text, { d.text = it }, label = { Text("$itemWord ${(i + 1).toPersianDigits()}") },
                        singleLine = !paged, minLines = if (paged) 2 else 1, modifier = Modifier.weight(1f),
                    )
                    if (drafts.size > 1) IconButton(onClick = { drafts.removeAt(i) }) { Icon(Icons.Outlined.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                }
                ImagePickRow(api, "تصویر", d.imageId, d.bytes, { d.bytes = it }, { d.bytes = null; d.imageId = "" }, setError, size = 48.dp)
            }
        }
        TextButton(onClick = { drafts.add(ItemDraft("", "")) }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("افزودن $itemWord") }
    }
}
