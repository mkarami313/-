package com.hamraheteyf.app.ui.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun RecListBody(st: RecState, emptyText: String, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (Rec) -> Unit) {
    val items = st.items
    when {
        items == null && st.error != null -> ErrorState(st.error, onRetry, modifier)
        items == null -> LoadingState(modifier)
        items.isEmpty() -> EmptyState(emptyText, modifier)
        else -> LazyColumn(
            modifier, contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { items(items, key = { it.id }) { content(it) } }
    }
}

@Composable
fun OwnerActions(onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    Row {
        if (onEdit != null) IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Edit, "ویرایش", modifier = Modifier.size(18.dp)) }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
fun AddFab(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(text) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeChipsEditor(times: String, onChange: (String) -> Unit) {
    val list = times.split(',', '،', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    var picking by remember { mutableStateOf(false) }
    SectionLabel("ساعت‌های یادآوری (هر ساعت یک اعلان روزانه)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.forEach { t ->
            InputChip(
                selected = true, onClick = { onChange(list.filter { it != t }.joinToString(", ")) },
                label = { Text(Jalali.displayTime(t)) },
                trailingIcon = { Icon(Icons.Outlined.Delete, "حذف", Modifier.size(16.dp)) },
            )
        }
        AssistChip(onClick = { picking = true }, label = { Text("افزودن ساعت") }, leadingIcon = { Icon(Icons.Outlined.AccessTime, null, Modifier.size(18.dp)) })
    }
    if (picking) TimePickerDialogFa("08:00", { picking = false }) { t ->
        picking = false
        onChange((list + t).distinct().sorted().joinToString(", "))
    }
}

@Composable
private fun ChildPickerFields(children: List<com.hamraheteyf.app.data.model.ChildProfilePrivate>, v: MutableMap<String, String>) {
    if (children.isEmpty()) {
        OutlinedTextField(v["childName"].orEmpty(), { v["childName"] = it }, label = { Text("نام کودک") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    } else {
        SectionLabel("کودک")
        ChildChipRow(children, v["childId"].orEmpty(), { id -> v["childId"] = id; v["childName"] = if (id.isBlank()) "" else children.nameOf(id) }, allLabel = "بدون کودک خاص")
    }
}

@Composable
private fun DiaryEditor(client: RecClient, children: List<com.hamraheteyf.app.data.model.ChildProfilePrivate>, initial: Rec?, defaultChild: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val d0 = initial?.get("date").orEmpty()
    val defaults = mapOf(
        "childId" to defaultChild, "childName" to if (defaultChild.isBlank()) "" else children.nameOf(defaultChild),
        "date" to if (Jalali.parseIso(d0) != null) d0 else Jalali.todayIso(),
    )
    RecEditorDialog(
        client, "diary", if (initial == null) "یادداشت جدید" else "ویرایش یادداشت", initial, defaults, onDismiss, onDone,
        canSave = { it["note"].orEmpty().isNotBlank() },
        beforeSave = { data -> if (Jalali.parseIso(data["date"].orEmpty()) == null) data["date"] = Jalali.todayIso(); null },
    ) { v, _ ->
        ChildPickerFields(children, v)
        JalaliDateField("تاریخ", v["date"].orEmpty(), { v["date"] = it }, Modifier.fillMaxWidth())
        OutlinedTextField(v["mood"].orEmpty(), { v["mood"] = it }, label = { Text("حال کودک امروز") }, supportingText = { Text("آرام، پرتنش، خوش‌خواب…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(v["note"].orEmpty(), { v["note"] = it }, label = { Text("یادداشت") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MedEditor(client: RecClient, children: List<com.hamraheteyf.app.data.model.ChildProfilePrivate>, initial: Rec?, defaultChild: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    val defaults = mapOf("childId" to defaultChild, "childName" to if (defaultChild.isBlank()) "" else children.nameOf(defaultChild))
    RecEditorDialog(
        client, "med", if (initial == null) "دارو یا یادآور جدید" else "ویرایش", initial, defaults, onDismiss, onDone,
        canSave = { it["name"].orEmpty().isNotBlank() },
    ) { v, _ ->
        ChildPickerFields(children, v)
        OutlinedTextField(v["name"].orEmpty(), { v["name"] = it }, label = { Text("نام دارو یا کار") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(v["dose"].orEmpty(), { v["dose"] = it }, label = { Text("مقدار") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TimeChipsEditor(v["times"].orEmpty()) { v["times"] = it }
        OutlinedTextField(v["notes"].orEmpty(), { v["notes"] = it }, label = { Text("توضیحات") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ChildFileScreen(onBack: () -> Unit) {
    val client = rememberRecClient()
    val ctx = LocalContext.current
    val children = rememberChildren()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    var picked by rememberSaveable { mutableStateOf("") }
    var showShare by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val kind = if (tab == 0) "diary" else "med"
    val st = rememberRecs(client, kind, tick)
    val selected = picked.takeIf { id -> children.any { it.id == id } } ?: if (children.size == 1) children[0].id else ""
    LaunchedEffect(st.items) { if (kind == "med") st.items?.let { Reminders.sync(ctx, it) } }
    val shown = st.items?.let { all -> if (selected.isBlank()) all else all.filter { it["childId"] == selected || it["childId"].isBlank() && it["childName"] == children.nameOf(selected) } }
    val view = RecState(shown, st.error)

    Scaffold(
        topBar = { ScreenHeader("پروندهٔ کودک", onBack = onBack) },
        floatingActionButton = { AddFab(if (tab == 0) "یادداشت جدید" else "دارو یا یادآور") { adding = true } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            InfoBanner("این اطلاعات فقط برای خود شماست و دیگران نمی‌بینند. جایگزین نسخهٔ پزشک نیست.", Modifier.padding(12.dp, 8.dp), dismissKey = "childfile_private")
            if (children.isNotEmpty()) {
                ChildChipRow(children, selected, { picked = it; if (it.isBlank()) showShare = false }, modifier = Modifier.padding(horizontal = 12.dp))
            }
            if (selected.isNotBlank()) {
                TextButton(onClick = { showShare = !showShare }, Modifier.padding(horizontal = 4.dp)) { Text(if (showShare) "بستن اشتراک‌گذاری و گزارش" else "اشتراک‌گذاری و گزارش ${children.nameOf(selected)}") }
                if (showShare) {
                    Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        ShareAndReportSection(selected, children.nameOf(selected))
                    }
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("دفترچهٔ روزانه") })
                Tab(tab == 1, { tab = 1 }, text = { Text("دارو و یادآور") })
            }
            RecListBody(view, if (tab == 0) "هنوز یادداشتی ندارید. رفتار، خواب و پیشرفت کودک را اینجا ثبت کنید." else "دارو یا یادآوری ثبت نشده است.", { tick++ }, Modifier.weight(1f)) { r ->
                val who = r["childId"].takeIf { it.isNotBlank() }?.let { children.nameOf(it) }.orEmpty().ifBlank { r["childName"] }
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (tab == 0) {
                                val date = r["date"].takeIf { it.isNotBlank() }?.let { Jalali.display(it) } ?: relativeTimeFa(r.createdAt)
                                Text(listOf(who, date).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                                if (r["mood"].isNotBlank()) Text("حال: ${r["mood"]}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                                Text(r["note"], style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                            } else {
                                Text(r["name"], style = MaterialTheme.typography.titleSmall)
                                Text(listOf(r["dose"], who).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = mutedColor())
                                if (r["times"].isNotBlank()) CategoryChip("ساعت: ${r["times"].toPersianDigits()}", accent = true)
                                if (r["notes"].isNotBlank()) Text(r["notes"], style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                        OwnerActions({ editing = r }, { deleting = r })
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        val close = { adding = false; editing = null }
        if (tab == 0) DiaryEditor(client, children, editing, selected, close) { close(); tick++ }
        else MedEditor(client, children, editing, selected, close) { close(); tick++ }
    }
    deleting?.let { d ->
        ConfirmDialog("این مورد حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete(kind, d.id); tick++ } }, onDismiss = { deleting = null })
    }
}

@Composable
private fun SlotEditor(client: RecClient, initial: Rec?, onDismiss: () -> Unit, onDone: () -> Unit) {
    val old = initial?.get("when").orEmpty()
    val defaults = mapOf(
        "_date" to (Jalali.findJalaliInText(old) ?: Jalali.todayIso()),
        "_time" to (Jalali.findTimeInText(old) ?: "10:00"),
    )
    RecEditorDialog(
        client, "slot", if (initial == null) "نوبت جدید" else "ویرایش نوبت", initial, defaults, onDismiss, onDone,
        canSave = { Jalali.parseIso(it["_date"].orEmpty()) != null && it["_time"].orEmpty().isNotBlank() },
        beforeSave = { data ->
            val iso = data.remove("_date").orEmpty(); val time = data.remove("_time").orEmpty()
            val g = Jalali.parseIso(iso) ?: return@RecEditorDialog "تاریخ نوبت را انتخاب کنید."
            val at = Jalali.epochMs(iso, time) ?: return@RecEditorDialog "ساعت نوبت نامعتبر است."
            if (at <= System.currentTimeMillis()) return@RecEditorDialog "زمان نوبت باید در آینده باشد."
            val day = Jalali.WEEKDAYS[Jalali.weekdayIndex(g.y, g.m, g.d)]
            data["when"] = "$day ${Jalali.display(iso)} ساعت ${Jalali.displayTime(time)}"
            data["epochMs"] = at.toString()
            null
        },
    ) { v, _ ->
        JalaliDateField("تاریخ", v["_date"].orEmpty(), { v["_date"] = it }, Modifier.fillMaxWidth())
        TimeField("ساعت", v["_time"].orEmpty(), { v["_time"] = it }, Modifier.fillMaxWidth())
        OutlinedTextField(v["duration"].orEmpty(), { v["duration"] = it.filter(Char::isDigit) }, label = { Text("مدت (دقیقه)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(v["place"].orEmpty(), { v["place"] = it }, label = { Text("محل یا شیوه") }, supportingText = { Text("حضوری در …  /  تلفنی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(v["note"].orEmpty(), { v["note"] = it }, label = { Text("توضیحات") }, minLines = 3, modifier = Modifier.fillMaxWidth())
    }
}

private fun apptId(slotId: String) = "slot_$slotId"

@Composable
fun BookingScreen(onBack: () -> Unit, onOpenProfile: ((String) -> Unit)? = null) {
    val client = rememberRecClient()
    val container = com.hamraheteyf.app.di.LocalAppContainer.current
    val pros by remember { container.userRepository.searchProfessionals(com.hamraheteyf.app.data.repository.ProfessionalFilter()) }.collectAsState(emptyList())
    val proTitle = remember(pros) { pros.associate { it.user.id to it.profile.title } }
    val ctx = LocalContext.current
    val me = rememberMe()
    val isPro = me != null && (me.isAdmin || me.accountType != AccountType.FAMILY && me.accountType != AccountType.COMMUNITY)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val titles = listOf("نوبت‌های آزاد", "رزروهای من") + if (isPro) listOf("نوبت‌های من") else emptyList()
    if (tab >= titles.size) tab = 0
    val raw = rememberRecs(client, "slot", tick, mine = tab == 2, booked = tab == 1)
    val st = remember(raw, tab) {
        val now = System.currentTimeMillis()
        RecState(
            raw.items
                ?.filter { tab != 0 || Reminders.slotAt(it).let { t -> t == null || t > now } }
                ?.sortedBy { Reminders.slotAt(it) ?: Long.MAX_VALUE },
            raw.error,
        )
    }
    var cancelling by remember { mutableStateOf<Rec?>(null) }

    // Keep local alarms in step with the booked list (a time change by the professional re-arms; unparseable times are skipped).
    LaunchedEffect(st.items, tab) {
        if (tab == 1) st.items?.forEach { r ->
            Reminders.slotAt(r)?.let { Reminders.scheduleAppointment(ctx, apptId(r.id), "نوبت با ${r.ownerName}: ${r["when"]}", it) }
        }
    }

    fun act(action: String, r: Rec) {
        scope.launch {
            when (val res = client!!.simple(action, r.id)) {
                is ApiResult.Fail -> snack.showSnackbar(res.message)
                is ApiResult.Ok -> when (action) {
                    "slotBook" -> Reminders.slotAt(r)?.let { Reminders.scheduleAppointment(ctx, apptId(r.id), "نوبت با ${r.ownerName}: ${r["when"]}", it) }
                    "slotCancel" -> Reminders.cancel(ctx, apptId(r.id))
                }
            }
            tick++
        }
    }

    Scaffold(
        topBar = { ScreenHeader("نوبت‌دهی", onBack = onBack) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = { if (tab == 2) AddFab("نوبت جدید") { adding = true } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            InfoBanner("رزرو نوبت رایگان است و هیچ پرداختی در برنامه انجام نمی‌شود.", Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp), dismissKey = "booking_free")
            InfoBanner("نوبت‌دهی فقط برای هماهنگی زمان ملاقات است و جایگزین تشخیص یا توصیهٔ پزشکی نیست. در شرایط اورژانسی با ۱۱۵ تماس بگیرید.", Modifier.padding(12.dp, 8.dp), dismissKey = "booking_medical")
            TabRow(selectedTabIndex = tab) { titles.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            RecListBody(st, when (tab) { 0 -> "فعلاً نوبت آزادی ثبت نشده است."; 1 -> "رزروی ندارید."; else -> "هنوز نوبتی ثبت نکرده‌اید." }, { tick++ }, Modifier.weight(1f)) { r ->
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r["when"], style = MaterialTheme.typography.titleSmall)
                            if (tab != 2) {
                                val t = proTitle[r.ownerId]
                                Text(
                                    "با ${r.ownerName}" + (t?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (onOpenProfile != null && r.ownerId.isNotBlank()) MaterialTheme.colorScheme.primary else mutedColor(),
                                    modifier = if (onOpenProfile != null && r.ownerId.isNotBlank()) Modifier.clickable { onOpenProfile(r.ownerId) } else Modifier,
                                )
                            }
                            Text(listOf(r["place"], r["duration"].takeIf { it.isNotBlank() }?.let { "${it.toPersianDigits()} دقیقه" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = mutedColor())
                            if (r["note"].isNotBlank()) Text(r["note"], style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        if (tab == 2) OwnerActions(if (r.status == "OPEN") ({ editing = r }) else null, { deleting = r })
                    }
                    Spacer(Modifier.height(6.dp))
                    when (tab) {
                        0 -> if (r.mine) CategoryChip("نوبت شما") else Button(onClick = { act("slotBook", r) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("رزرو این نوبت") }
                        1 -> OutlinedButton(onClick = { cancelling = r }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("لغو رزرو") }
                        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.status == "BOOKED") {
                                CategoryChip("رزرو: ${r.bookedByName}", accent = true)
                                TextButton(onClick = { cancelling = r }) { Text("لغو رزرو") }
                            } else CategoryChip("آزاد")
                        }
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        val close = { adding = false; editing = null }
        SlotEditor(client, editing, close) { close(); tick++ }
    }
    cancelling?.let { c ->
        ConfirmDialog(
            if (c.mine) "رزرو «${c.bookedByName}» لغو شود؟ نوبت دوباره آزاد می‌شود و به رزروکننده اطلاع داده می‌شود."
            else "رزرو شما برای این نوبت لغو شود؟ نوبت آزاد می‌شود و به ${c.ownerName} اطلاع داده می‌شود.",
            confirm = "لغو رزرو",
            onYes = { cancelling = null; act("slotCancel", c) }, onDismiss = { cancelling = null },
        )
    }
    deleting?.let { d ->
        ConfirmDialog(if (d.status == "BOOKED") "این نوبت رزرو شده است؛ با حذف، به رزروکننده اطلاع داده می‌شود. ادامه می‌دهید؟" else "این نوبت حذف شود؟",
            onYes = { deleting = null; scope.launch { client?.delete("slot", d.id); tick++ } }, onDismiss = { deleting = null })
    }
}
