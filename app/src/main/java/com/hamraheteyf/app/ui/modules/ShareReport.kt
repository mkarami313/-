package com.hamraheteyf.app.ui.modules

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.BuildConfig
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.admin.PlatformCall
import com.hamraheteyf.app.ui.admin.rememberPlatformCall
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

private fun isoDay(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

private fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.lng(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false

/** Server timestamps may be epoch seconds or milliseconds. */
private fun toMillis(v: Long): Long = if (v in 1..99_999_999_999L) v * 1000 else v

private fun daysLeft(expiresAt: Long): Int {
    val ms = toMillis(expiresAt) - System.currentTimeMillis()
    return if (ms <= 0) 0 else ((ms + 86_399_999L) / 86_400_000L).toInt()
}

private fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "کپی شد", Toast.LENGTH_SHORT).show()
}

private fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "اشتراک‌گذاری کد").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Blocking fetch of the printable HTML report. Call from IO. */
private fun fetchReportHtml(token: String?, childId: String, from: String, to: String, shareId: String?): Pair<String?, String?> {
    var c: HttpURLConnection? = null
    return try {
        val q = buildString {
            append("report?childId=").append(URLEncoder.encode(childId, "UTF-8"))
            append("&from=").append(from).append("&to=").append(to)
            if (shareId != null) append("&share=").append(URLEncoder.encode(shareId, "UTF-8"))
        }
        c = (URL(BuildConfig.API_BASE_URL + q).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } to null
        else null to (runCatching { c.errorStream?.bufferedReader()?.use { it.readText() }?.let { Json.parseToJsonElement(it).jsonObject.str("error") } }.getOrNull()
            ?.ifBlank { null } ?: "دریافت گزارش ممکن نشد (${c.responseCode}).")
    } catch (e: Exception) {
        null to "ارتباط با سرور برقرار نشد."
    } finally {
        c?.disconnect()
    }
}

/** Renders [html] in an off-screen WebView and hands it to the system print dialog (Save as PDF). */
private class PdfPrinter(private val ctx: Context) {
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun print(html: String, jobName: String, onFail: () -> Unit) {
        release()
        val w = WebView(ctx)
        web = w
        w.settings.javaScriptEnabled = false
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                try {
                    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    pm.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
                } catch (e: Exception) {
                    onFail()
                }
            }
        }
        w.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    fun release() {
        web?.destroy(); web = null
    }
}

// ---------------------------------------------------------------------------------------------
// PDF export block (own child or shared child)
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportBlock(childId: String, childName: String, shareId: String? = null) {
    val call = rememberPlatformCall()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val printer = remember { PdfPrinter(ctx) }
    DisposableEffect(Unit) { onDispose { printer.release() } }

    val day = 86_400_000L
    var fromMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() - 30 * day) }
    var toMs by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("گزارش PDF", style = MaterialTheme.typography.titleMedium)
    Text("گزارشی قابل چاپ از ثبت‌های بازهٔ انتخابی برای مراجعه به درمانگر.", style = MaterialTheme.typography.bodySmall, color = mutedColor())
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { picking = "from" }, modifier = Modifier.weight(1f)) { Text("از: ${isoDay(fromMs)}") }
        OutlinedButton(onClick = { picking = "to" }, modifier = Modifier.weight(1f)) { Text("تا: ${isoDay(toMs)}") }
    }
    Spacer(Modifier.height(8.dp))
    Button(
        enabled = !busy && call != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        onClick = {
            if (fromMs > toMs) { error = "تاریخ شروع باید پیش از پایان باشد."; return@Button }
            busy = true; error = null
            scope.launch {
                val token = call?.token
                val (html, err) = withContext(Dispatchers.IO) { fetchReportHtml(token, childId, isoDay(fromMs), isoDay(toMs), shareId) }
                busy = false
                if (html == null) error = err
                else printer.print(html, "گزارش ${childName.ifBlank { "کودک" }}") { error = "چاپ/ذخیرهٔ PDF در این دستگاه در دسترس نیست." }
            }
        },
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else { Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("ساخت گزارش و ذخیره به‌صورت PDF") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }

    picking?.let { which ->
        val state = rememberDatePickerState(initialSelectedDateMillis = if (which == "from") fromMs else toMs)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { if (which == "from") fromMs = it else toMs = it }; picking = null }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("انصراف") } },
        ) { DatePicker(state) }
    }
}

// ---------------------------------------------------------------------------------------------
// Owner side: share code + PDF
// ---------------------------------------------------------------------------------------------

private data class OwnShare(val id: String, val code: String, val expiresAt: Long, val revoked: Boolean, val redeemedByName: String)

@Composable
fun ShareAndReportSection(childId: String, childName: String) {
    val call = rememberPlatformCall()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by rememberSaveable { mutableIntStateOf(7) }
    var shares by remember { mutableStateOf<List<OwnShare>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf<OwnShare?>(null) }
    var fresh by rememberSaveable { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(call, childId, tick) {
        val r = call?.call("shareList") ?: return@LaunchedEffect
        when (r) {
            is ApiResult.Ok -> shares = (r.data["shares"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { it.str("childId") == childId }
                .map { OwnShare(it.str("id"), it.str("code"), it.lng("expiresAt"), it.bool("revoked"), it.str("redeemedByName")) }
            is ApiResult.Fail -> { if (shares == null) shares = emptyList(); error = r.message }
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("اشتراک‌گذاری با درمانگر یا مربی", style = MaterialTheme.typography.titleMedium)
        Text(
            "یک کد موقت بسازید و به فرد مورد اعتماد بدهید؛ او فقط خواندنیِ خلاصهٔ پروندهٔ ${childName.ifBlank { "کودک" }} را می‌بیند. هر زمان می‌توانید دسترسی را قطع کنید.",
            style = MaterialTheme.typography.bodySmall, color = mutedColor(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { d ->
                FilterChip(selected = days == d, onClick = { days = d }, label = { Text("${d.toPersianDigits()} روز") })
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !busy && call != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            onClick = {
                busy = true; error = null
                scope.launch {
                    when (val r = call?.call("shareCreate", buildJsonObject { put("childId", childId); put("days", days) })) {
                        is ApiResult.Ok -> { fresh = r.data.str("code"); tick++ }
                        is ApiResult.Fail -> error = r.message
                        null -> error = "اتصال به سرور برقرار نیست."
                    }
                    busy = false
                }
            },
        ) { Text("ساخت کد اشتراک") }

        fresh?.takeIf { it.isNotBlank() }?.let { code ->
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("کد اشتراک", style = MaterialTheme.typography.labelMedium)
                    Text(code, style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { copyText(ctx, "share", code) }) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("کپی") }
                        OutlinedButton(onClick = {
                            shareText(ctx, "کد دسترسی به پروندهٔ کودک در «همراهِ طیف»: $code\nدر برنامه: پروفایل ← اشتراک‌های دریافتی ← وارد کردن کد")
                        }) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text("ارسال") }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }

        val active = shares.orEmpty().filter { !it.revoked && toMillis(it.expiresAt) > System.currentTimeMillis() }
        if (active.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("اشتراک‌های فعال", style = MaterialTheme.typography.titleSmall)
            active.forEach { s ->
                CompactCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.code, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${daysLeft(s.expiresAt).toPersianDigits()} روز مانده" + if (s.redeemedByName.isNotBlank()) " · استفاده‌شده توسط ${s.redeemedByName}" else " · هنوز استفاده نشده",
                                style = MaterialTheme.typography.bodySmall, color = mutedColor(),
                            )
                        }
                        IconButton(onClick = { copyText(ctx, "share", s.code) }) { Icon(Icons.Outlined.ContentCopy, "کپی کد") }
                        TextButton(onClick = { revoking = s }) { Text("قطع دسترسی") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PdfExportBlock(childId, childName)
    }

    revoking?.let { s ->
        ConfirmDialog("دسترسی این کد قطع شود؟ هر کسی که آن را وارد کرده دیگر پرونده را نمی‌بیند.", "قطع دسترسی", onYes = {
            revoking = null
            scope.launch {
                when (val r = call?.call("shareRevoke", buildJsonObject { put("id", s.id) })) {
                    is ApiResult.Fail -> error = r.message
                    else -> tick++
                }
            }
        }, onDismiss = { revoking = null })
    }
}

// ---------------------------------------------------------------------------------------------
// Receiver side
// ---------------------------------------------------------------------------------------------

private data class ReceivedShare(val id: String, val childId: String, val childName: String, val ownerName: String, val expiresAt: Long)

@Composable
fun SharesReceivedScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val call = rememberPlatformCall()
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ReceivedShare>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(call, tick) {
        when (val r = call?.call("shareList") ?: return@LaunchedEffect) {
            is ApiResult.Ok -> items = (r.data["received"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .map { ReceivedShare(it.str("id"), it.str("childId"), it.str("childName"), it.str("ownerName"), it.lng("expiresAt")) }
            is ApiResult.Fail -> { if (items == null) items = emptyList(); error = r.message }
        }
    }

    Scaffold(topBar = { ScreenHeader("اشتراک‌های دریافتی", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoBanner("اگر خانواده‌ای کد اشتراک پروندهٔ کودکش را به شما داده، آن را اینجا وارد کنید. دسترسی فقط خواندنی و موقت است.", dismissKey = "shares_received_info")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it.trim() }, singleLine = true,
                label = { Text("کد اشتراک") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Spacer(Modifier.height(8.dp))
            BigPrimaryButton("ثبت کد", enabled = !busy && code.isNotBlank() && call != null, onClick = {
                busy = true; error = null
                scope.launch {
                    when (val r = call?.call("shareRedeem", buildJsonObject { put("code", code) })) {
                        is ApiResult.Ok -> { code = ""; tick++; r.data.str("id").takeIf { it.isNotBlank() }?.let(onOpen) }
                        is ApiResult.Fail -> error = r.message
                        null -> error = "اتصال به سرور برقرار نیست."
                    }
                    busy = false
                }
            })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("پرونده‌های اشتراکی", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            when {
                items == null -> LoadingState()
                items!!.isEmpty() -> EmptyState("هنوز پرونده‌ای با شما به اشتراک گذاشته نشده است.")
                else -> items!!.forEach { s ->
                    val left = daysLeft(s.expiresAt)
                    CompactCard(onClick = if (left > 0) ({ onOpen(s.id) }) else null) {
                        Text(s.childName.ifBlank { "کودک" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (s.ownerName.isNotBlank()) "از طرف ${s.ownerName} · " else "") +
                                if (left > 0) "${left.toPersianDigits()} روز مانده" else "منقضی شده",
                            style = MaterialTheme.typography.bodySmall, color = mutedColor(),
                        )
                    }
                }
            }
        }
    }
}

private val SKIP_KEYS = setOf("id", "ownerId", "childId", "childName", "createdAt", "mine", "status", "ownerName")
private val LABELS = mapOf(
    "date" to "تاریخ", "time" to "ساعت", "mood" to "حال‌وهوا", "note" to "یادداشت", "notes" to "توضیحات", "name" to "نام",
    "dose" to "دوز", "times" to "زمان‌ها", "title" to "عنوان", "text" to "متن", "type" to "نوع", "trigger" to "محرک",
    "behavior" to "رفتار", "severity" to "شدت", "duration" to "مدت", "intensity" to "شدت",
)

@Composable
private fun JsonSection(title: String, rows: List<JsonObject>) {
    if (rows.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Text("$title (${rows.size.toPersianDigits()})", style = MaterialTheme.typography.titleMedium)
    rows.take(100).forEach { o ->
        CompactCard {
            o.entries.filter { it.key !in SKIP_KEYS && it.value is JsonPrimitive && it.value.jsonPrimitive.content.isNotBlank() }.forEach { (k, v) ->
                val label = LABELS[k]
                Text(if (label != null) "$label: ${v.jsonPrimitive.content}" else v.jsonPrimitive.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SharedChildViewerScreen(shareId: String, onBack: () -> Unit) {
    val call = rememberPlatformCall()
    var data by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var listedChildId by remember { mutableStateOf("") }

    LaunchedEffect(call, shareId) {
        val r = call?.call("shareList")
        if (r is ApiResult.Ok) listedChildId = (r.data["received"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("id") == shareId }?.str("childId").orEmpty()
    }
    LaunchedEffect(call, shareId, tick) {
        error = null
        when (val r = call?.call("shareView", buildJsonObject { put("id", shareId) }) ?: return@LaunchedEffect) {
            is ApiResult.Ok -> data = r.data
            is ApiResult.Fail -> error = r.message
        }
    }
    val child = data?.get("child") as? JsonObject
    val childId = listedChildId.ifBlank { child?.str("id").orEmpty() }.ifBlank { data?.str("childId").orEmpty() }

    Scaffold(topBar = { ScreenHeader(child?.str("name")?.ifBlank { null } ?: "پروندهٔ اشتراکی", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            when {
                error != null -> ErrorState(error!!, onRetry = { tick++ })
                data == null -> LoadingState()
                else -> {
                    val d = data!!
                    InfoBanner("نمای فقط‌خواندنی و موقت. این اطلاعات خصوصی است؛ آن را منتشر یا بازارسال نکنید.", dismissKey = "share_view_privacy")
                    Spacer(Modifier.height(8.dp))
                    child?.let { c ->
                        CompactCard {
                            Text(c.str("name"), style = MaterialTheme.typography.titleMedium)
                            c.str("ageRange").takeIf { it.isNotBlank() }?.let { Text("بازهٔ سنی: $it", style = MaterialTheme.typography.bodyMedium) }
                            (c["interests"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotEmpty() }
                                ?.let { Text("علاقه‌ها: ${it.joinToString("، ")}", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    (d["emergency"] as? JsonObject)?.let { e ->
                        JsonSection("کارت اضطراری", listOf(e))
                    }
                    fun rows(k: String) = (d[k] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    JsonSection("ثبت‌های رفتاری", rows("logs"))
                    JsonSection("یادداشت‌های روزانه", rows("diary"))
                    JsonSection("داروها", rows("med"))
                    if (childId.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        PdfExportBlock(childId, child?.str("name").orEmpty(), shareId)
                    }
                }
            }
        }
    }
}
