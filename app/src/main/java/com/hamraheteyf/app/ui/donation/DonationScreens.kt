package com.hamraheteyf.app.ui.donation

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.launch

@Composable
private fun rememberClient(): DonationClient? {
    val api = LocalAppContainer.current.serverApi
    return remember(api) { api?.let { DonationClient(it) } }
}

private const val ALL = "همه دسته‌ها"

// ------------------------------------------------------------------ list

@Composable
fun DonationListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val client = rememberClient()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf(ALL) }
    var categories by remember { mutableStateOf(listOf<String>()) }
    var items by remember { mutableStateOf<List<DonationItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, category, reload, client) {
        if (client == null) { error = "این بخش در حالت آفلاین در دسترس نیست."; return@LaunchedEffect }
        items = null; error = null
        when (val r = client.list(category.takeIf { it != ALL }, mine = tab == 1)) {
            is ApiResult.Ok -> { items = r.data.first; categories = r.data.second }
            is ApiResult.Fail -> error = r.message
        }
    }

    Scaffold(
        topBar = { ScreenHeader("دست‌به‌دست", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Outlined.VolunteerActivism, null) }, text = { Text("اهدا می‌کنم") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("اهدایی‌ها") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("آگهی‌های من") })
            }
            if (tab == 0) {
                FilterDropdown(
                    label = "دسته‌بندی", selected = category, options = listOf(ALL) + categories,
                    onSelected = { i -> category = (listOf(ALL) + categories)[i] },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            val list = items
            when {
                error != null -> ErrorState(error!!, onRetry = { reload++ })
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(if (tab == 0) "هنوز چیزی اهدا نشده است. اولین نفر باشید." else "آگهی‌ای ندارید. لوازمی که لازم ندارید را اهدا کنید.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { items(list, key = { it.id }) { d -> DonationCard(d, client!!) { onOpen(d.id) } } }
            }
        }
    }
}

@Composable
private fun DonationCard(d: DonationItem, client: DonationClient, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp))
            if (d.imageIds.isNotEmpty()) RemoteImage(client.imageUrl(d.imageIds[0]), thumb)
            else Box(thumb.then(Modifier), contentAlignment = Alignment.Center) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.VolunteerActivism, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(d.category, d.city).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = mutedColor(), maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip(conditionLabel(d.condition))
                    if (d.status != "AVAILABLE") CategoryChip(statusLabel(d.status), accent = true)
                    if (d.mine && d.requestCount > 0) CategoryChip("${d.requestCount.toPersianDigits()} درخواست", accent = true)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ detail

@Composable
fun DonationDetailScreen(id: String, onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    val client = rememberClient()
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf<DonationItem?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var askRequest by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(id, reload, client) {
        if (client == null) { error = "این بخش در حالت آفلاین در دسترس نیست."; return@LaunchedEffect }
        when (val r = client.get(id)) {
            is ApiResult.Ok -> item = r.data
            is ApiResult.Fail -> error = r.message
        }
    }

    fun run(block: suspend () -> ApiResult<*>) {
        scope.launch {
            busy = true
            when (val r = block()) {
                is ApiResult.Fail -> message = r.message
                is ApiResult.Ok -> reload++
            }
            busy = false
        }
    }

    fun chat(userId: String) {
        scope.launch {
            when (val r = client!!.openChat(userId)) {
                is ApiResult.Ok -> onOpenConversation(r.data)
                is ApiResult.Fail -> message = r.message
            }
        }
    }

    Scaffold(topBar = { ScreenHeader("جزئیات", onBack = onBack) }) { padding ->
        val d = item
        when {
            error != null -> ErrorState(error!!, modifier = Modifier.padding(padding))
            d == null || client == null -> LoadingState(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                if (d.imageIds.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(d.imageIds) { img ->
                            RemoteImage(client.imageUrl(img), Modifier.height(240.dp).widthIn(min = 200.dp, max = 320.dp).clip(RoundedCornerShape(16.dp)))
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(d.title, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryChip(d.category)
                        CategoryChip(conditionLabel(d.condition))
                        if (d.status != "AVAILABLE") CategoryChip(statusLabel(d.status), accent = true)
                    }
                    Text(
                        listOf("اهداکننده: ${d.ownerName}", d.city).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = mutedColor(),
                    )
                    if (d.description.isNotBlank()) Text(d.description, style = MaterialTheme.typography.bodyLarge)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                    if (!d.mine) {
                        when {
                            d.iAmRecipient -> InfoBanner("اهداکننده درخواست شما را پذیرفت. برای هماهنگی تحویل، پیام بدهید.", dismissKey = null)
                            d.iRequested -> InfoBanner("درخواست شما ثبت شد. منتظر پاسخ اهداکننده باشید.", dismissKey = null)
                        }
                        if (d.status == "AVAILABLE" && !d.iRequested) {
                            BigPrimaryButton("این را می‌خواهم", onClick = { askRequest = true }, enabled = !busy)
                        }
                        OutlinedButton(onClick = { chat(d.ownerId) }, modifier = Modifier.fillMaxWidth()) { Text("پیام به اهداکننده") }
                    } else {
                        OwnerPanel(d, busy, onChat = ::chat,
                            onApprove = { uid -> run { client.simple("donationApprove", d.id, mapOf("userId" to uid)) } },
                            onGiven = { run { client.simple("donationMarkGiven", d.id) } },
                            onReopen = { run { client.simple("donationReopen", d.id) } },
                            onRemove = { confirmRemove = true })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (askRequest) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askRequest = false },
            title = { Text("درخواست این مورد") },
            text = {
                OutlinedTextField(note, { note = it.take(300) }, label = { Text("یادداشت برای اهداکننده (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { askRequest = false; run { client!!.simple("donationRequest", id, mapOf("note" to note)) } }) { Text("ارسال درخواست") }
            },
            dismissButton = { TextButton(onClick = { askRequest = false }) { Text("انصراف") } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("حذف آگهی؟") },
            text = { Text("این آگهی برای همه حذف می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        when (val r = client!!.simple("donationRemove", id)) {
                            is ApiResult.Ok -> onBack()
                            is ApiResult.Fail -> message = r.message
                        }
                    }
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun OwnerPanel(
    d: DonationItem, busy: Boolean,
    onChat: (String) -> Unit, onApprove: (String) -> Unit,
    onGiven: () -> Unit, onReopen: () -> Unit, onRemove: () -> Unit,
) {
    Text("درخواست‌ها (${d.requests.size.toPersianDigits()})", style = MaterialTheme.typography.titleMedium)
    if (d.requests.isEmpty()) Text("هنوز درخواستی نرسیده است.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
    d.requests.forEach { r ->
        val chosen = d.recipientId == r.userId
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.name.ifBlank { "؟" }, size = 32)
                    Spacer(Modifier.width(8.dp))
                    Text(r.name.ifBlank { "کاربر" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (chosen) CategoryChip("انتخاب‌شده")
                }
                if (r.note.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(r.note, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChat(r.userId) }) { Text("پیام") }
                    if (d.status == "AVAILABLE") Button(onClick = { onApprove(r.userId) }, enabled = !busy) { Text("پذیرش") }
                }
            }
        }
    }
    when (d.status) {
        "RESERVED" -> {
            Button(onClick = onGiven, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("تحویل داده شد") }
            OutlinedButton(onClick = onReopen, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("بازگشایی دوباره") }
        }
        "GIVEN" -> OutlinedButton(onClick = onReopen, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("دوباره موجود شد") }
    }
    TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("حذف آگهی", color = MaterialTheme.colorScheme.error) }
}

// ------------------------------------------------------------------ create

@Composable
fun DonationCreateScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val client = rememberClient()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf("GOOD") }
    var categories by remember { mutableStateOf(listOf<String>()) }
    var picked by remember { mutableStateOf(listOf<Uri>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client) {
        if (client != null) (client.list(null, false) as? ApiResult.Ok)?.let {
            categories = it.data.second
            if (category.isBlank()) category = it.data.second.firstOrNull().orEmpty()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        picked = (picked + uris).distinct().take(4)
    }

    Scaffold(topBar = { ScreenHeader("اهدای لوازم", onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoBanner("این بخش کاملاً رایگان است؛ خرید و فروش و دریافت پول ممنوع است. فقط لوازم سالم و ایمن اهدا کنید.", dismissKey = "donation_free")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(picked) { uri ->
                    Box {
                        val bmp = remember(uri) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)!!.use {
                                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })
                                }
                            }.getOrNull()
                        }
                        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        IconButton(onClick = { picked = picked - uri }, modifier = Modifier.size(28.dp).align(Alignment.TopStart)) {
                            Icon(Icons.Outlined.Close, "حذف عکس", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (picked.size < 4) item {
                    Surface(
                        onClick = { picker.launch("image/*") }, shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(88.dp),
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
                            Text("عکس", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            OutlinedTextField(title, { title = it.take(100) }, label = { Text("عنوان (مثلاً: ست مکعب‌های حسی)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FilterDropdown("دسته‌بندی", category, categories, { category = categories[it] }, Modifier.fillMaxWidth())
            FilterDropdown(
                "وضعیت کالا", conditionLabel(condition), DonationConditions.map { it.second },
                { condition = DonationConditions[it].first }, Modifier.fillMaxWidth(),
            )
            OutlinedTextField(city, { city = it.take(60) }, label = { Text("شهر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it.take(1000) }, label = { Text("توضیحات (سن مناسب، ابعاد، نکات)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            BigPrimaryButton(if (busy) "در حال ثبت…" else "ثبت آگهی", enabled = !busy && title.trim().length >= 3 && category.isNotBlank(), onClick = {
                scope.launch {
                    busy = true; error = null
                    val urls = mutableListOf<String>()
                    for ((i, uri) in picked.withIndex()) {
                        val bytes = prepareImageForUpload(context, uri)
                        if (bytes == null) { error = "یکی از عکس‌ها خوانده نشد."; busy = false; return@launch }
                        when (val u = client!!.upload(bytes, "donation_$i.jpg")) {
                            is ApiResult.Ok -> urls += u.data
                            is ApiResult.Fail -> { error = u.message; busy = false; return@launch }
                        }
                    }
                    when (val r = client!!.create(title.trim(), description.trim(), category, condition, city.trim(), urls)) {
                        is ApiResult.Ok -> onCreated(r.data)
                        is ApiResult.Fail -> error = r.message
                    }
                    busy = false
                }
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}
