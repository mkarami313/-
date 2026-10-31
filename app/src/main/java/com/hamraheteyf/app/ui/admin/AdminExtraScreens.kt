package com.hamraheteyf.app.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.AccountStatus
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.*
import com.hamraheteyf.app.ui.modules.ConfirmDialog
import com.hamraheteyf.app.ui.navigation.AppFeatures
import com.hamraheteyf.app.ui.navigation.FeatureFlags
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

private val RoleLabels = listOf(
    "FAMILY" to "خانواده", "PROFESSIONAL" to "متخصص سلامت", "EDUCATOR" to "مربی",
    "SOCIAL_WORKER" to "مددکار/مشاور", "COMMUNITY" to "عضو جامعه",
)
private val CoreIds = setOf("home", "profile")

private fun copyText(c: Context, label: String, text: String) {
    (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(c, "کپی شد", Toast.LENGTH_SHORT).show()
}

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.n(k: String) = this[k]?.jsonPrimitive?.longOrNull ?: 0L

/** Modules on/off and per-role restriction. */
@Composable
fun AdminModulesTab(onMessage: (String) -> Unit) {
    val call = rememberPlatformCall()
    val scope = rememberCoroutineScope()
    val disabled by FeatureFlags.disabled.collectAsState()
    var roles by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(call) {
        val r = call?.call("adminStats")
        if (r is ApiResult.Ok) {
            roles = r.data["moduleRoles"]?.jsonObject?.mapValues { e -> e.value.jsonArray.map { it.jsonPrimitive.content } }
        }
    }
    val mods = AppFeatures.all.filter { it.id !in CoreIds }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { InfoBanner("خاموش‌کردن یک ماژول آن را برای همه پنهان می‌کند. «نقش‌ها» دسترسی را به نقش‌های انتخاب‌شده محدود می‌کند (مدیر همیشه دسترسی دارد).", dismissKey = "admin_modules_hint") }
        items(mods, key = { it.id }) { f ->
            val on = f.id !in disabled
            val r = roles?.get(f.id).orEmpty()
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (r.isEmpty()) "نقش‌ها: همه" else "نقش‌ها: " + r.joinToString("، ") { id -> RoleLabels.firstOrNull { it.first == id }?.second ?: id },
                            style = MaterialTheme.typography.bodySmall, color = mutedColor(),
                        )
                    }
                    TextButton(onClick = { editing = f.id to f.title }) { Text("نقش‌ها") }
                    Switch(checked = on, onCheckedChange = { v ->
                        scope.launch {
                            val res = call?.call("setFeatureEnabled", buildJsonObject { put("id", f.id); put("enabled", v) })
                            onMessage(if (res is ApiResult.Fail) res.message else if (v) "«${f.title}» روشن شد." else "«${f.title}» خاموش شد.")
                        }
                    })
                }
            }
        }
    }
    editing?.let { (id, title) ->
        val sel = remember(id) { mutableStateListOf<String>().also { it.addAll(roles?.get(id).orEmpty()) } }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("نقش‌های مجاز «$title»") },
            text = {
                Column {
                    Text("هیچ‌کدام انتخاب نشود یعنی همهٔ نقش‌ها. ذخیره، تنظیم قبلی را جایگزین می‌کند.", style = MaterialTheme.typography.bodySmall)
                    RoleLabels.forEach { (rid, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(rid in sel, { if (it) sel.add(rid) else sel.remove(rid) })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = sel.toList()
                    editing = null
                    scope.launch {
                        val res = call?.call("setModuleRoles", buildJsonObject { put("id", id); put("roles", buildJsonArray { chosen.forEach { add(it) } }) })
                        if (res is ApiResult.Ok) roles = (roles ?: emptyMap()).toMutableMap().also { if (chosen.isEmpty()) it.remove(id) else it[id] = chosen }
                        onMessage(if (res is ApiResult.Fail) res.message else "نقش‌ها ذخیره شد.")
                    }
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("انصراف") } },
        )
    }
}

@Composable
fun AdminStatsTab() {
    val call = rememberPlatformCall()
    var tick by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(call, tick) {
        err = null
        when (val r = call?.call("adminStats")) {
            is ApiResult.Ok -> data = r.data
            is ApiResult.Fail -> err = r.message
            null -> err = "در حالت آفلاین در دسترس نیست."
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        err?.let { ErrorState(it, onRetry = { tick++ }) }
        val d = data
        if (d == null && err == null) LoadingState()
        if (d != null) {
            StatRow("کاربران", d.n("users")); StatRow("فعال در ۷ روز اخیر", d.n("activeLast7d"))
            StatRow("گزارش‌های باز", d.n("openReports")); StatRow("درخواست‌های تأیید تخصص", d.n("pendingVerifications"))
            d["byType"]?.jsonObject?.let { bt ->
                Text("به تفکیک نقش", style = MaterialTheme.typography.titleSmall)
                bt.forEach { (k, v) -> StatRow(RoleLabels.firstOrNull { it.first == k }?.second ?: k, v.jsonPrimitive.longOrNull ?: 0) }
            }
            d["content"]?.jsonObject?.let { c ->
                Text("محتوا", style = MaterialTheme.typography.titleSmall)
                val names = mapOf("questions" to "پرسش‌ها", "answers" to "پاسخ‌ها", "posts" to "پست‌های گروه", "guides" to "راهنماها", "donations" to "آگهی‌های دست‌به‌دست")
                c.forEach { (k, v) -> StatRow(names[k] ?: k, v.jsonPrimitive.longOrNull ?: 0) }
            }
            OutlinedButton(onClick = { tick++ }) { Text("تازه‌سازی") }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Long) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value.toInt().toPersianDigits(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AdminUsersTab(onMessage: (String) -> Unit) {
    val call = rememberPlatformCall()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf<List<JsonObject>?>(null) }
    var total by remember { mutableLongStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var statusFor by remember { mutableStateOf<Pair<JsonObject, AccountStatus>?>(null) }
    var statusReason by remember { mutableStateOf("") }
    var resetFor by remember { mutableStateOf<JsonObject?>(null) }
    var resetCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(call, query, tick) {
        err = null
        when (val r = call?.call("adminListUsers", buildJsonObject { put("q", query); put("limit", 50); put("offset", 0) })) {
            is ApiResult.Ok -> { users = r.data["users"]?.jsonArray?.map { it.jsonObject }.orEmpty(); total = r.data.n("total") }
            is ApiResult.Fail -> err = r.message
            null -> err = "در حالت آفلاین در دسترس نیست."
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            q, { q = it }, label = { Text("جستجوی نام یا ایمیل") }, singleLine = true,
            trailingIcon = { TextButton(onClick = { query = q.trim() }) { Text("جستجو") } },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        err?.let { ErrorState(it, onRetry = { tick++ }) }
        val list = users
        if (list == null && err == null) LoadingState()
        if (list != null) LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("${total.toInt().toPersianDigits()} کاربر", style = MaterialTheme.typography.labelMedium, color = mutedColor()) }
            if (list.isEmpty()) item { EmptyState("کاربری پیدا نشد.") }
            items(list, key = { it.s("id") }) { u ->
                val st = runCatching { AccountStatus.valueOf(u.s("status")) }.getOrDefault(AccountStatus.ACTIVE)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(u.s("name").ifBlank { "بدون نام" }, style = MaterialTheme.typography.titleSmall)
                        Text(u.s("email"), style = MaterialTheme.typography.bodySmall, color = mutedColor())
                        Text("وضعیت: ${st.label}", style = MaterialTheme.typography.labelMedium)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (st != AccountStatus.ACTIVE) OutlinedButton(onClick = { statusFor = u to AccountStatus.ACTIVE }) { Text("فعال‌سازی") }
                            if (st != AccountStatus.LIMITED) OutlinedButton(onClick = { statusFor = u to AccountStatus.LIMITED }) { Text("محدودسازی") }
                            if (st != AccountStatus.BLOCKED) OutlinedButton(onClick = { statusFor = u to AccountStatus.BLOCKED }) { Text("مسدودسازی") }
                            OutlinedButton(onClick = { resetFor = u }) { Text("صدور کد بازیابی") }
                        }
                    }
                }
            }
        }
    }
    statusFor?.let { (u, target) ->
        AlertDialog(
            onDismissRequest = { statusFor = null; statusReason = "" },
            title = { Text("«${target.label}» برای ${u.s("name")}؟") },
            text = { OutlinedTextField(statusReason, { statusReason = it }, label = { Text("دلیل تصمیم (الزامی)") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = statusReason.isNotBlank(), onClick = {
                    val reason = statusReason; statusFor = null; statusReason = ""
                    scope.launch {
                        val r = call?.call("setAccountStatus", buildJsonObject { put("userId", u.s("id")); put("status", target.name); put("reason", reason) })
                        onMessage(if (r is ApiResult.Fail) r.message else "وضعیت حساب ثبت شد.")
                        tick++
                    }
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { statusFor = null; statusReason = "" }) { Text("انصراف") } },
        )
    }
    resetFor?.let { u ->
        ConfirmDialog("برای ${u.s("email")} کد بازیابی رمز صادر شود؟ (۸ رقم، ۳۰ دقیقه، یک‌بار مصرف؛ در گزارش تغییرات ثبت می‌شود)", confirm = "صدور کد", onYes = {
            resetFor = null
            scope.launch {
                when (val r = call?.call("adminIssueReset", buildJsonObject { put("email", u.s("email")) })) {
                    is ApiResult.Ok -> resetCode = r.data.s("code")
                    is ApiResult.Fail -> onMessage(r.message)
                    null -> {}
                }
            }
        }, onDismiss = { resetFor = null })
    }
    resetCode?.let { code ->
        AlertDialog(
            onDismissRequest = { resetCode = null },
            title = { Text("کد بازیابی") },
            text = {
                Column {
                    Text(code, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("این کد را فقط به صاحب حساب بدهید. ۳۰ دقیقه معتبر است.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { copyText(ctx, "reset", code) }) { Text("کپی") } },
            dismissButton = { TextButton(onClick = { resetCode = null }) { Text("بستن") } },
        )
    }
}

@Composable
fun AdminBroadcastTab(onMessage: (String) -> Unit) {
    val call = rememberPlatformCall()
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBanner("این پیام به همهٔ کاربران اعلان فوری می‌فرستد و قابل بازگشت نیست؛ فقط برای خبرهای مهم استفاده کنید.", dismissKey = "admin_broadcast_hint")
        OutlinedTextField(title, { title = it.take(60) }, label = { Text("عنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it.take(300) }, label = { Text("متن پیام") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        BigPrimaryButton(if (busy) "در حال ارسال…" else "ارسال به همه", enabled = !busy && title.isNotBlank() && body.isNotBlank(), onClick = { confirm = true })
    }
    if (confirm) ConfirmDialog("این اعلان برای همهٔ کاربران ارسال شود؟", confirm = "ارسال", onYes = {
        confirm = false; busy = true
        scope.launch {
            val r = call?.call("adminBroadcast", buildJsonObject { put("title", title.trim()); put("body", body.trim()) })
            busy = false
            if (r is ApiResult.Ok) { onMessage("برای ${r.data.n("sent").toInt().toPersianDigits()} دستگاه ارسال شد."); title = ""; body = "" }
            else onMessage((r as? ApiResult.Fail)?.message ?: "در حالت آفلاین در دسترس نیست.")
        }
    }, onDismiss = { confirm = false })
}
