package com.hamraheteyf.app.ui.modules

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch

private val CourseFields = listOf(
    FieldSpec("title", "عنوان دوره"), FieldSpec("level", "سطح", hint = "مقدماتی / متوسط / پیشرفته"),
    FieldSpec("description", "توضیح", long = true),
    FieldSpec("lessons", "درس‌ها", long = true, hint = "هر درس در یک خط: عنوان | آدرس ویدیو (اختیاری)"),
)

@Composable
fun CoursesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val client = rememberClientOrNull(); val me = rememberMe()
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    val st = rememberRecs(client, "course", tick)
    Scaffold(topBar = { ScreenHeader("دوره‌ها و آموزش", onBack = onBack) },
        floatingActionButton = { if (me?.isAdmin == true) AddFab("دورهٔ جدید") { adding = true } }) { pad ->
        RecListBody(st, "هنوز دوره‌ای منتشر نشده است.", { tick++ }, Modifier.padding(pad)) { r ->
            val n = r.lines("lessons").size
            CompactCard(onClick = { onOpen(r.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.School, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r["title"], style = MaterialTheme.typography.titleSmall)
                        Text(r["description"], style = MaterialTheme.typography.bodySmall, maxLines = 2, color = mutedColor())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (r["level"].isNotBlank()) CategoryChip(r["level"])
                            CategoryChip("${n.toPersianDigits()} درس", accent = true)
                        }
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        RecFormDialog(client, "course", "دوره", CourseFields, editing, { adding = false; editing = null }, { adding = false; editing = null; tick++ })
    }
}

@Composable
private fun rememberClientOrNull() = rememberRecClient()

private fun parseLesson(line: String): Pair<String, String> {
    val i = line.indexOf('|')
    return if (i < 0) line.trim() to "" else line.substring(0, i).trim() to line.substring(i + 1).trim()
}

@Composable
fun CourseDetailScreen(courseId: String, onBack: () -> Unit) {
    val client = rememberRecClient(); val me = rememberMe(); val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val st = rememberRecs(client, "course", tick)
    val course = st.items?.firstOrNull { it.id == courseId }
    val prefs = remember { ctx.getSharedPreferences("hamrah_course_done", 0) }
    Scaffold(topBar = {
        ScreenHeader(course?.get("title") ?: "دوره", onBack = onBack, actions = {
            if (me?.isAdmin == true && course != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "ویرایش") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "حذف") }
            }
        })
    }) { pad ->
        when {
            course == null && st.items == null -> LoadingState(Modifier.padding(pad))
            course == null -> EmptyState("دوره پیدا نشد.", Modifier.padding(pad))
            else -> {
                val lessons = course.lines("lessons").map(::parseLesson)
                val done = remember(refresh) { prefs.getStringSet(courseId, emptySet()).orEmpty() }
                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(course["description"], style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { if (lessons.isEmpty()) 0f else done.size.toFloat() / lessons.size }, modifier = Modifier.fillMaxWidth())
                        Text("${done.size.toPersianDigits()} از ${lessons.size.toPersianDigits()} درس انجام شد", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                    }
                    items(lessons.indices.toList()) { i ->
                        val (title, url) = lessons[i]
                        val on = i.toString() in done
                        CompactCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(on, { val s = done.toMutableSet(); if (!s.add(i.toString())) s.remove(i.toString()); prefs.edit().putStringSet(courseId, s).apply(); refresh++ })
                                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (url.isNotBlank()) TextButton(onClick = {
                                    val direct = Regex("(?i)\\.(mp4|webm|m3u8)(\\?.*)?$").containsMatchIn(url) || url.contains("/api/files/")
                                    if (direct) playing = url else runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }) { Icon(Icons.Outlined.PlayCircle, null); Spacer(Modifier.width(4.dp)); Text("پخش") }
                            }
                        }
                    }
                }
            }
        }
    }
    playing?.let { u ->
        AlertDialog(onDismissRequest = { playing = null }, confirmButton = { TextButton(onClick = { playing = null }) { Text("بستن") } },
            text = {
                val signed by produceState<String?>(null, u) { value = com.hamraheteyf.app.net.FileUrls.resolveUrl(u) }
                signed?.let { src ->
                    AndroidView(factory = { c -> VideoView(c).apply {
                        val mc = android.widget.MediaController(c); mc.setAnchorView(this); setMediaController(mc)
                        setVideoURI(Uri.parse(src)); setOnPreparedListener { start() }
                    } }, modifier = Modifier.fillMaxWidth().height(220.dp))
                }
            })
    }
    if (editing && client != null && course != null) {
        RecFormDialog(client, "course", "ویرایش دوره", CourseFields, course, { editing = false }, { editing = false; tick++ })
    }
    if (deleting && course != null) {
        ConfirmDialog("این دوره حذف شود؟", onYes = { deleting = false; scope.launch { client?.delete("course", course.id); onBack() } }, onDismiss = { deleting = false })
    }
}

private val PlaceFields = listOf(
    FieldSpec("name", "نام مرکز"), FieldSpec("type", "نوع", hint = "کلینیک، مدرسه، گفتاردرمانی…"), FieldSpec("city", "شهر"),
    FieldSpec("address", "نشانی", long = true), FieldSpec("phone", "تلفن"),
    FieldSpec("lat", "عرض جغرافیایی (اختیاری)", number = true), FieldSpec("lng", "طول جغرافیایی (اختیاری)", number = true), FieldSpec("note", "توضیحات", long = true),
)
private val EventFields = listOf(
    FieldSpec("title", "عنوان رویداد"), FieldSpec("date", "تاریخ و ساعت"), FieldSpec("city", "شهر"),
    FieldSpec("address", "نشانی", long = true), FieldSpec("description", "توضیح", long = true), FieldSpec("link", "پیوند ثبت‌نام یا اطلاعات"),
)

@Composable
fun PlacesScreen(onBack: () -> Unit) {
    val client = rememberRecClient(); val me = rememberMe(); val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var city by rememberSaveable { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    val kind = if (tab == 0) "place" else "event"
    val st = rememberRecs(client, kind, tick)
    val admin = me?.isAdmin == true
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val shown = st.items?.filter { city.isBlank() || it["city"].contains(city.trim()) }
    Scaffold(topBar = { ScreenHeader("مراکز و رویدادها", onBack = onBack) },
        floatingActionButton = { if (admin) AddFab(if (tab == 0) "مرکز جدید" else "رویداد جدید") { adding = true } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("مراکز") }); Tab(tab == 1, { tab = 1 }, text = { Text("رویدادها") })
            }
            OutlinedTextField(city, { city = it }, label = { Text("فیلتر شهر") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp))
            RecListBody(RecState(shown, st.error), if (st.items?.isNotEmpty() == true) "موردی برای این شهر نیست." else "هنوز موردی ثبت نشده است.", { tick++ }) { r ->
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (tab == 0) r["name"] else r["title"], style = MaterialTheme.typography.titleSmall)
                            Text(listOf(if (tab == 0) r["type"] else r["date"], r["city"]).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = mutedColor())
                            if (r["address"].isNotBlank()) Text(r["address"], style = MaterialTheme.typography.bodySmall)
                            val desc = if (tab == 0) r["note"] else r["description"]
                            if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 3, color = mutedColor())
                        }
                        if (admin) OwnerActions({ editing = r }, { deleting = r })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (r["phone"].isNotBlank()) TextButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r["phone"]}"))) }) { Icon(Icons.Outlined.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("تماس") }
                        val q = if (r["lat"].isNotBlank() && r["lng"].isNotBlank()) "${r["lat"]},${r["lng"]}" else r["address"].ifBlank { "" }
                        if (q.isNotBlank()) TextButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)))) }) { Icon(Icons.Outlined.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("مسیریابی") }
                        if (r["link"].startsWith("http")) TextButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(r["link"]))) }) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("اطلاعات بیشتر") }
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        RecFormDialog(client, kind, "ثبت", if (tab == 0) PlaceFields else EventFields, editing, { adding = false; editing = null }, { adding = false; editing = null; tick++ })
    }
    deleting?.let { d -> ConfirmDialog("حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete(kind, d.id); tick++ } }, onDismiss = { deleting = null }) }
}
