package com.hamraheteyf.app.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch

private class LogType(val key: String, val emoji: String, val label: String, val color: Color)

private val LogTypes = listOf(
    LogType("meltdown", "😣", "بحران / رفتار", Color(0xFFE57373)),
    LogType("sleep", "😴", "خواب", Color(0xFF7986CB)),
    LogType("seizure", "⚡", "تشنج", Color(0xFFFFB74D)),
    LogType("meal", "🍽️", "غذا", Color(0xFF81C784)),
    LogType("trigger", "🔔", "محرک", Color(0xFFBA68C8)),
    LogType("other", "📝", "سایر", Color(0xFF90A4AE)),
)
private fun typeOf(k: String) = LogTypes.firstOrNull { it.key == k } ?: LogTypes.last()

private val QuickTriggers = listOf("سر و صدا", "شلوغی", "تغییر برنامه", "گرسنگی", "خستگی", "نور زیاد", "درد یا ناراحتی", "صفحهٔ نمایش", "جدایی از والدین", "غذای جدید")

private fun Rec.timeText() = this["time"].takeIf { it.isNotBlank() }?.let { Jalali.displayTime(it) }.orEmpty()
private fun Rec.sortKey() = dayIso() + this["time"].ifBlank { "00:00" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BehaviorLogScreen(onBack: () -> Unit) {
    val client = rememberRecClient()
    val children = rememberChildren()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    val st = rememberRecs(client, "log", tick)
    val visible = remember(st.items, filter) {
        st.items.orEmpty().filter { filter.isBlank() || (if (filter == UNASSIGNED_CHILD) it["childId"].isBlank() else it["childId"] == filter) }.sortedByDescending { it.sortKey() }
    }

    Scaffold(
        topBar = { ScreenHeader("ثبت رفتار و خواب", onBack = onBack) },
        floatingActionButton = { AddFab("ثبت جدید") { adding = true } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            InfoBanner("این ثبت‌ها فقط برای خود شماست. با ثبت منظم، الگوی محرک‌ها و خواب را می‌توانید به درمانگر نشان دهید.", Modifier.padding(12.dp, 8.dp), dismissKey = "behaviorlog_private")
            ChildChipRow(children, filter, { filter = it }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), unassignedLabel = "بدون کودک")
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("ثبت‌ها") })
                Tab(tab == 1, { tab = 1 }, text = { Text("آمار ۳۰ روز") })
            }
            val items = st.items
            when {
                items == null && st.error != null -> ErrorState(st.error, { tick++ })
                items == null -> LoadingState()
                tab == 1 -> LogStats(visible)
                visible.isEmpty() -> EmptyState("هنوز ثبتی ندارید. هر بحران، خواب یا محرکی را با چند لمس اینجا ثبت کنید.")
                else -> {
                    val groups = visible.groupBy { it.dayIso() }
                    LazyColumn(contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { (day, rows) ->
                            item(key = "h_$day") {
                                Text(Jalali.displayLong(day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                            }
                            items(rows, key = { it.id }) { r -> LogRow(r, children.nameOf(r["childId"]), { editing = r }, { deleting = r }) }
                        }
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        LogEditor(client, children, editing, if (editing == null && filter != UNASSIGNED_CHILD) filter else "", { adding = false; editing = null }) { adding = false; editing = null; tick++ }
    }
    deleting?.let { d ->
        ConfirmDialog("این ثبت حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("log", d.id); tick++ } }, onDismiss = { deleting = null })
    }
}

@Composable
private fun LogRow(r: Rec, childName: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val t = typeOf(r["type"])
    CompactCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(listOf(t.label, r.timeText(), childName).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                val extra = buildList {
                    r["intensity"].toIntOrNull()?.let { add((if (t.key == "sleep") "کیفیت " else "شدت ") + it.toPersianDigits() + " از ۵") }
                    r["durationMin"].toIntOrNull()?.takeIf { it > 0 }?.let { add(if (t.key == "sleep") sleepText(it) else "${it.toPersianDigits()} دقیقه") }
                    if (r["trigger"].isNotBlank()) add("محرک: ${r["trigger"]}")
                }
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = mutedColor())
                if (r["notes"].isNotBlank()) Text(r["notes"], style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
            OwnerActions(onEdit, onDelete)
        }
    }
}

private fun sleepText(min: Int): String {
    val h = min / 60; val m = min % 60
    return if (m == 0) "${h.toPersianDigits()} ساعت" else "${h.toPersianDigits()} ساعت و ${m.toPersianDigits()} دقیقه"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogEditor(client: RecClient, children: List<com.hamraheteyf.app.data.model.ChildProfilePrivate>, initial: Rec?, defaultChild: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val defaults = remember {
        mapOf(
            "type" to "meltdown", "intensity" to "3", "date" to Jalali.todayIso(), "time" to Jalali.nowTime(),
            "childId" to (defaultChild.ifBlank { children.singleOrNull()?.id.orEmpty() }),
        )
    }
    RecEditorDialog(client, "log", if (initial == null) "ثبت جدید" else "ویرایش ثبت", initial, defaults, onDismiss, onDone,
        canSave = { it["childId"].orEmpty().isNotBlank() && it["type"].orEmpty().isNotBlank() }) { v, _ ->
        val type = v["type"].orEmpty()
        val isSleep = type == "sleep"
        ChildChipRow(children, v["childId"].orEmpty(), { v["childId"] = it }, allLabel = null)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LogTypes.forEach { t -> FilterChip(type == t.key, { v["type"] = t.key }, label = { Text("${t.emoji} ${t.label}") }) }
        }
        SectionLabel(if (isSleep) "کیفیت خواب (۱ بد … ۵ عالی)" else "شدت (۱ خفیف … ۵ شدید)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n -> FilterChip(v["intensity"] == n.toString(), { v["intensity"] = n.toString() }, label = { Text(n.toPersianDigits()) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JalaliDateField("تاریخ", v["date"].orEmpty(), { v["date"] = it }, Modifier.weight(1f))
            TimeField("ساعت", v["time"].orEmpty(), { v["time"] = it }, Modifier.weight(1f))
        }
        if (isSleep) {
            val hours = v["durationMin"]?.toIntOrNull()?.let { (it / 60.0).toString().removeSuffix(".0") }.orEmpty()
            var txt by remember { mutableStateOf(hours) }
            OutlinedTextField(txt, { txt = it; v["durationMin"] = ((it.replace('٫', '.').replace(',', '.').toDoubleOrNull() ?: 0.0) * 60).toInt().takeIf { m -> m > 0 }?.toString().orEmpty() },
                label = { Text("مدت خواب (ساعت)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        } else {
            OutlinedTextField(v["durationMin"].orEmpty(), { v["durationMin"] = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("مدت (دقیقه)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            SectionLabel("محرک احتمالی")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickTriggers.forEach { q -> FilterChip(v["trigger"] == q, { v["trigger"] = if (v["trigger"] == q) "" else q }, label = { Text(q) }) }
            }
            OutlinedTextField(v["trigger"].orEmpty(), { v["trigger"] = it }, label = { Text("محرک (متن آزاد)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(v["notes"].orEmpty(), { v["notes"] = it }, label = { Text("یادداشت") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogStats(rows: List<Rec>) {
    val today = Jalali.dayNumber(Jalali.todayIso()) ?: return
    val days = 30
    fun idx(r: Rec) = Jalali.dayNumber(r.dayIso())?.let { today - it }?.takeIf { it in 0 until days }
    val recent = rows.mapNotNull { r -> idx(r)?.let { it to r } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recent.isEmpty()) { EmptyState("در ۳۰ روز گذشته ثبتی نیست."); return@Column }
        CompactCard {
            Text("تعداد ثبت‌ها در هر روز", style = MaterialTheme.typography.titleSmall)
            val perDay = Array(days) { IntArray(LogTypes.size) }
            recent.forEach { (i, r) -> perDay[i][LogTypes.indexOfFirst { it.key == r["type"] }.let { k -> if (k < 0) LogTypes.size - 1 else k }]++ }
            val maxTotal = perDay.maxOf { it.sum() }.coerceAtLeast(1)
            val axis = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp)) {
                val slot = size.width / days
                val bw = slot * 0.65f
                drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                for (i in 0 until days) {
                    // oldest day on the right? RTL reading: newest at the right edge.
                    val x = size.width - (i + 1) * slot + (slot - bw) / 2
                    var y = size.height
                    perDay[i].forEachIndexed { k, c ->
                        if (c > 0) {
                            val h = size.height * c / maxTotal * 0.95f
                            drawRect(LogTypes[k].color, Offset(x, y - h), Size(bw, h))
                            y -= h
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("امروز", style = MaterialTheme.typography.labelSmall, color = mutedColor())
                Text("۳۰ روز پیش", style = MaterialTheme.typography.labelSmall, color = mutedColor())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LogTypes.forEach { t -> Text("${t.emoji} ${t.label}", style = MaterialTheme.typography.labelMedium, color = t.color) }
            }
        }
        val behaviour = recent.map { it.second }.filter { it["type"] != "sleep" && it["type"] != "meal" }
        val intens = behaviour.mapNotNull { it["intensity"].toIntOrNull() }
        CompactCard {
            Text("خلاصهٔ ۳۰ روز", style = MaterialTheme.typography.titleSmall)
            Text("تعداد کل ثبت‌ها: ${recent.size.toPersianDigits()}", style = MaterialTheme.typography.bodyMedium)
            if (intens.isNotEmpty()) Text("میانگین شدت رفتارها: ${"%.1f".format(intens.average()).toPersianDigits()} از ۵", style = MaterialTheme.typography.bodyMedium)
            val tops = behaviour.map { it["trigger"].trim() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
            if (tops.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("پرتکرارترین محرک‌ها", style = MaterialTheme.typography.labelLarge)
                tops.forEach { Text("• ${it.key} (${it.value.toPersianDigits()} بار)", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        val sleeps = recent.filter { it.second["type"] == "sleep" && (it.second["durationMin"].toIntOrNull() ?: 0) > 0 }
        if (sleeps.isNotEmpty()) {
            CompactCard {
                Text("روند خواب (ساعت در شب)", style = MaterialTheme.typography.titleSmall)
                val byDay = sleeps.groupBy({ it.first }, { it.second["durationMin"].toInt() / 60f }).mapValues { it.value.average().toFloat() }
                val avg = byDay.values.average()
                Text("میانگین: ${"%.1f".format(avg).toPersianDigits()} ساعت", style = MaterialTheme.typography.bodyMedium)
                val line = MaterialTheme.colorScheme.primary
                val axis = MaterialTheme.colorScheme.outlineVariant
                val maxH = maxOf(12f, byDay.values.max())
                Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
                    drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 2f)
                    val slot = size.width / days
                    val pts = byDay.entries.sortedByDescending { it.key }.map { (i, h) -> Offset(size.width - (i + 0.5f) * slot, size.height - size.height * h / maxH) }
                    if (pts.size > 1) drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, line, style = Stroke(3f))
                    pts.forEach { drawCircle(line, 5f, it) }
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}
