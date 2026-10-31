package com.hamraheteyf.app.ui.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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

private val DiaryFields = listOf(
    FieldSpec("childName", "نام کودک"), FieldSpec("date", "تاریخ", hint = "مثلاً ۱۴۰۵/۰۸/۱۰"),
    FieldSpec("mood", "حال کودک امروز", hint = "آرام، پرتنش، خوش‌خواب…"), FieldSpec("note", "یادداشت", long = true),
)
private val MedFields = listOf(
    FieldSpec("name", "نام دارو یا کار"), FieldSpec("dose", "مقدار"),
    FieldSpec("times", "ساعت‌های یادآوری", hint = "مثلاً 08:00, 20:00 — هر ساعت یک اعلان روزانه"),
    FieldSpec("childName", "نام کودک"), FieldSpec("notes", "توضیحات", long = true),
)

@Composable
fun ChildFileScreen(onBack: () -> Unit) {
    val client = rememberRecClient()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    val scope = rememberCoroutineScope()
    val kind = if (tab == 0) "diary" else "med"
    val st = rememberRecs(client, kind, tick)
    LaunchedEffect(st.items) { if (kind == "med") st.items?.let { Reminders.sync(ctx, it) } }

    Scaffold(
        topBar = { ScreenHeader("پروندهٔ کودک", onBack = onBack) },
        floatingActionButton = { AddFab(if (tab == 0) "یادداشت جدید" else "دارو یا یادآور") { adding = true } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            InfoBanner("این اطلاعات فقط برای خود شماست و دیگران نمی‌بینند. جایگزین نسخهٔ پزشک نیست.", Modifier.padding(12.dp, 8.dp), dismissKey = "childfile_private")
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("دفترچهٔ روزانه") })
                Tab(tab == 1, { tab = 1 }, text = { Text("دارو و یادآور") })
            }
            RecListBody(st, if (tab == 0) "هنوز یادداشتی ندارید. رفتار، خواب و پیشرفت کودک را اینجا ثبت کنید." else "دارو یا یادآوری ثبت نشده است.", { tick++ }) { r ->
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (tab == 0) {
                                Text(listOf(r["childName"], r["date"].ifBlank { relativeTimeFa(r.createdAt) }).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                                if (r["mood"].isNotBlank()) Text("حال: ${r["mood"]}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                                Text(r["note"], style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                            } else {
                                Text(r["name"], style = MaterialTheme.typography.titleSmall)
                                Text(listOf(r["dose"], r["childName"]).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = mutedColor())
                                if (r["times"].isNotBlank()) CategoryChip("ساعت: ${r["times"]}", accent = true)
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
        RecFormDialog(client, kind, if (editing == null) "افزودن" else "ویرایش", if (tab == 0) DiaryFields else MedFields, editing,
            onDismiss = { adding = false; editing = null }, onDone = { adding = false; editing = null; tick++ })
    }
    deleting?.let { d ->
        ConfirmDialog("این مورد حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete(kind, d.id); tick++ } }, onDismiss = { deleting = null })
    }
}

private val SlotFields = listOf(
    FieldSpec("when", "زمان نوبت", hint = "مثلاً شنبه ۱۴۰۵/۰۸/۱۵ ساعت ۱۰:۰۰"), FieldSpec("duration", "مدت (دقیقه)", number = true),
    FieldSpec("place", "محل یا شیوه", hint = "حضوری در …  /  تلفنی"), FieldSpec("note", "توضیحات", long = true),
)

@Composable
fun BookingScreen(onBack: () -> Unit) {
    val client = rememberRecClient()
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
    val st = rememberRecs(client, "slot", tick, mine = tab == 2, booked = tab == 1)

    fun act(action: String, id: String) {
        scope.launch {
            when (val r = client!!.simple(action, id)) {
                is ApiResult.Fail -> snack.showSnackbar(r.message)
                is ApiResult.Ok -> {}
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
            InfoBanner("رزرو نوبت رایگان است و هیچ پرداختی در برنامه انجام نمی‌شود.", Modifier.padding(12.dp, 8.dp), dismissKey = "booking_free")
            TabRow(selectedTabIndex = tab) { titles.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
            RecListBody(st, when (tab) { 0 -> "فعلاً نوبت آزادی ثبت نشده است."; 1 -> "رزروی ندارید."; else -> "هنوز نوبتی ثبت نکرده‌اید." }, { tick++ }) { r ->
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r["when"], style = MaterialTheme.typography.titleSmall)
                            if (tab != 2) Text("با ${r.ownerName}", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                            Text(listOf(r["place"], r["duration"].takeIf { it.isNotBlank() }?.let { "${it.toPersianDigits()} دقیقه" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = mutedColor())
                            if (r["note"].isNotBlank()) Text(r["note"], style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        if (tab == 2) OwnerActions(if (r.status == "OPEN") ({ editing = r }) else null, { deleting = r })
                    }
                    Spacer(Modifier.height(6.dp))
                    when (tab) {
                        0 -> Button(onClick = { act("slotBook", r.id) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("رزرو این نوبت") }
                        1 -> OutlinedButton(onClick = { act("slotCancel", r.id) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("لغو رزرو") }
                        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.status == "BOOKED") {
                                CategoryChip("رزرو: ${r.bookedByName}", accent = true)
                                TextButton(onClick = { act("slotCancel", r.id) }) { Text("لغو رزرو") }
                            } else CategoryChip("آزاد")
                        }
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) {
        RecFormDialog(client, "slot", if (editing == null) "نوبت جدید" else "ویرایش نوبت", SlotFields, editing,
            onDismiss = { adding = false; editing = null }, onDone = { adding = false; editing = null; tick++ })
    }
    deleting?.let { d ->
        ConfirmDialog(if (d.status == "BOOKED") "این نوبت رزرو شده است؛ با حذف، به رزروکننده اطلاع داده می‌شود. ادامه می‌دهید؟" else "این نوبت حذف شود؟",
            onYes = { deleting = null; scope.launch { client?.delete("slot", d.id); tick++ } }, onDismiss = { deleting = null })
    }
}
