package com.hamraheteyf.app.ui.modules

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hamraheteyf.app.R
import com.hamraheteyf.app.data.model.ChildProfilePrivate
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.ui.common.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

private val EmergencyKeys = listOf("childId", "name", "diagnosis", "sensory", "triggers", "calming", "medical", "contact1Name", "contact1Phone", "contact2Name", "contact2Phone", "photo")

/**
 * Local, login-free copy of the emergency cards (plain JSON in prefs `hamrah_emergency`, photos in filesDir).
 * It exists so the card can be shown from the login screen when the family opted in.
 */
object EmergencyCache {
    private const val PREFS = "hamrah_emergency"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun photoFile(c: Context, id: String) = File(c.filesDir, "emergency_$id.jpg")

    fun showOnLogin(c: Context) = prefs(c).getBoolean("showOnLogin", false)
    fun setShowOnLogin(c: Context, on: Boolean) = prefs(c).edit().putBoolean("showOnLogin", on).apply()

    fun save(c: Context, recs: List<Rec>) {
        val arr = JSONArray()
        recs.forEach { r -> arr.put(JSONObject().apply { put("id", r.id); EmergencyKeys.forEach { put(it, r[it]) } }) }
        prefs(c).edit().putString("records", arr.toString()).apply()
        val keep = recs.map { "emergency_${it.id}.jpg" }.toSet()
        c.filesDir.listFiles { f -> f.name.startsWith("emergency_") }?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    fun load(c: Context): List<Map<String, String>> = runCatching {
        val arr = JSONArray(prefs(c).getString("records", "[]"))
        List(arr.length()) { i -> val o = arr.getJSONObject(i); (EmergencyKeys + "id").associateWith { o.optString(it, "") } }
    }.getOrDefault(emptyList())

    fun photo(c: Context, id: String): Bitmap? = photoFile(c, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Best-effort download of each card's photo so it also shows offline. */
    suspend fun cachePhotos(c: Context, api: com.hamraheteyf.app.net.HamrahApi, recs: List<Rec>) = withContext(Dispatchers.IO) {
        recs.forEach { r ->
            val f = photoFile(c, r.id)
            if (r["photo"].isBlank()) { f.delete(); return@forEach }
            runCatching {
                val c = URL(api.fileUrl(r["photo"])).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 20_000
                try { if (c.responseCode == 200) f.writeBytes(c.inputStream.use { it.readBytes() }) } finally { c.disconnect() }
            }
        }
    }

    /** Call from sign-out: wipes the cache unless the family opted in to showing the card before login. */
    fun onSignOut(c: Context) {
        if (showOnLogin(c)) return
        prefs(c).edit().remove("records").apply()
        c.filesDir.listFiles { f -> f.name.startsWith("emergency_") }?.forEach { it.delete() }
    }
}

private fun dial(ctx: Context, phone: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private val Yellow = Color(0xFFFFE600)

/** Full-screen, high-contrast emergency card (also used by the login-screen entry). */
@Composable
fun EmergencyCardView(f: Map<String, String>, photo: Bitmap?, photoId: String, onBack: () -> Unit, onShare: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val api = LocalAppContainer.current.serverApi
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().background(Color.Black).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("بستن", color = Yellow, fontSize = 18.sp) }
            Spacer(Modifier.weight(1f))
            if (onShare != null) TextButton(onClick = onShare) { Icon(Icons.Outlined.Share, null, tint = Yellow); Spacer(Modifier.width(6.dp)); Text("اشتراک به‌صورت تصویر", color = Yellow, fontSize = 16.sp) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("کارت اضطراری کودک", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.background(Yellow).fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center)
            val photoMod = Modifier.size(180.dp).clip(MaterialTheme.shapes.medium)
            when {
                photo != null -> Image(photo.asImageBitmap(), null, photoMod, contentScale = ContentScale.Crop)
                photoId.isNotBlank() && api != null -> RemoteImage(api.fileUrl(photoId), photoMod)
            }
            Text(f["name"].orEmpty(), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center)
            listOf("تشخیص" to "diagnosis", "حساسیت‌های حسی" to "sensory", "محرک‌ها" to "triggers", "چه چیزی آرامش می‌کند" to "calming", "نکات پزشکی (دارو، آلرژی)" to "medical").forEach { (label, key) ->
                val v = f[key].orEmpty()
                if (v.isNotBlank()) Column(Modifier.fillMaxWidth().background(Color(0xFFF2F2F2)).padding(12.dp)) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF444444))
                    Text(v, fontSize = 24.sp, lineHeight = 36.sp, color = Color.Black)
                }
            }
            listOf("contact1Name" to "contact1Phone", "contact2Name" to "contact2Phone").forEach { (nk, pk) ->
                val phone = f[pk].orEmpty()
                if (phone.isNotBlank()) Button(
                    onClick = { dial(ctx, phone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Yellow),
                ) {
                    Icon(Icons.Outlined.Call, null, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp))
                    Column { Text(f[nk].orEmpty().ifBlank { "تماس" }, fontSize = 20.sp); Text(phone.toPersianDigits(), fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Login-screen entry: reads ONLY the local cache (no network, no login). */
@Composable
fun EmergencyCardPublicScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cards = remember { EmergencyCache.load(ctx) }
    var sel by remember { mutableIntStateOf(0) }
    if (cards.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("کارت اضطراری ذخیره‌ای روی این گوشی نیست.", fontSize = 20.sp, color = Color.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("بازگشت") }
        }
        return
    }
    val card = cards[sel.coerceIn(0, cards.lastIndex)]
    Box(Modifier.fillMaxSize()) {
        EmergencyCardView(card, remember(card["id"]) { EmergencyCache.photo(ctx, card["id"].orEmpty()) }, "", onBack)
        if (cards.size > 1) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEachIndexed { i, c -> Button(onClick = { sel = i }, colors = ButtonDefaults.buttonColors(containerColor = if (i == sel) Yellow else Color.DarkGray, contentColor = if (i == sel) Color.Black else Color.White)) { Text(c["name"].orEmpty().ifBlank { "کارت ${(i + 1).toPersianDigits()}" }) } }
        }
    }
}

private fun renderCardBitmap(ctx: Context, f: Map<String, String>, photo: Bitmap?): Bitmap {
    val w = 1080; val pad = 48
    val face = runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.vazirmatn_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    fun tp(size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }
    fun layout(text: String, p: TextPaint) = StaticLayout.Builder.obtain(text, 0, text.length, p, w - 2 * pad).setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(android.text.TextDirectionHeuristics.RTL).setLineSpacing(0f, 1.2f).build()
    val blocks = mutableListOf<Pair<StaticLayout, Int>>() // layout, background color (0 = none)
    blocks += layout("کارت اضطراری کودک", tp(46f, AColor.BLACK)) to 0xFFFFE600.toInt()
    blocks += layout(f["name"].orEmpty(), tp(84f, AColor.BLACK)) to 0
    listOf("تشخیص" to "diagnosis", "حساسیت‌های حسی" to "sensory", "محرک‌ها" to "triggers", "آرام‌کننده‌ها" to "calming", "نکات پزشکی" to "medical").forEach { (l, k) ->
        val v = f[k].orEmpty()
        if (v.isNotBlank()) { blocks += layout(l, tp(32f, 0xFF444444.toInt())) to 0xFFF2F2F2.toInt(); blocks += layout(v, tp(48f, AColor.BLACK)) to 0xFFF2F2F2.toInt() }
    }
    listOf("contact1Name" to "contact1Phone", "contact2Name" to "contact2Phone").forEach { (n, p) ->
        if (f[p].orEmpty().isNotBlank()) blocks += layout("${f[n].orEmpty().ifBlank { "تماس" }}: ${f[p]}".toPersianDigits(), tp(52f, AColor.WHITE)) to AColor.BLACK
    }
    val photoSize = if (photo != null) 360 else 0
    val gap = 18
    val height = pad + (if (photo != null) photoSize + gap else 0) + blocks.sumOf { it.first.height + gap } + pad
    val bmp = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    c.drawColor(AColor.WHITE)
    var y = pad.toFloat()
    // title bar first, then the photo
    val bg = Paint()
    fun drawBlock(b: Pair<StaticLayout, Int>) {
        if (b.second != 0) { bg.color = b.second; c.drawRect(0f, y - 6, w.toFloat(), y + b.first.height + 6, bg) }
        c.save(); c.translate(pad.toFloat(), y); b.first.draw(c); c.restore()
        y += b.first.height + gap
    }
    drawBlock(blocks[0])
    if (photo != null) {
        val side = minOf(photo.width, photo.height)
        val src = android.graphics.Rect((photo.width - side) / 2, (photo.height - side) / 2, (photo.width + side) / 2, (photo.height + side) / 2)
        val left = (w - photoSize) / 2
        c.drawBitmap(photo, src, android.graphics.Rect(left, y.toInt(), left + photoSize, y.toInt() + photoSize), null)
        y += photoSize + gap
    }
    blocks.drop(1).forEach { drawBlock(it) }
    return bmp
}

private fun shareCard(ctx: Context, f: Map<String, String>, photo: Bitmap?) {
    runCatching {
        val bmp = renderCardBitmap(ctx, f, photo)
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "emergency_card.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        ctx.startActivity(Intent.createChooser(send, "اشتراک کارت اضطراری").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { android.widget.Toast.makeText(ctx, "ساخت تصویر ممکن نشد.", android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable
fun EmergencyScreen(onBack: () -> Unit) {
    val client = rememberRecClient()
    val ctx = LocalContext.current
    val api = LocalAppContainer.current.serverApi
    val children = rememberChildren()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Rec?>(null) }
    var showing by remember { mutableStateOf<Rec?>(null) }
    var optIn by remember { mutableStateOf(EmergencyCache.showOnLogin(ctx)) }
    val st = rememberRecs(client, "emergency", tick)
    LaunchedEffect(st.items) {
        st.items?.let { EmergencyCache.save(ctx, it); if (api != null) EmergencyCache.cachePhotos(ctx, api, it) }
    }

    showing?.let { r ->
        val photo = remember(r.id, r["photo"]) { EmergencyCache.photo(ctx, r.id) }
        androidx.compose.ui.window.Dialog(onDismissRequest = { showing = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) { EmergencyCardView(r.f, photo, r["photo"], { showing = null }, { shareCard(ctx, r.f, photo) }) }
        }
    }

    Scaffold(
        topBar = { ScreenHeader("کارت اضطراری", onBack = onBack) },
        floatingActionButton = { AddFab("کارت جدید") { adding = true } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            InfoBanner("اگر کودک گم شد یا در جمع دچار بحران شد، این کارت را نشان دهید یا تصویرش را بفرستید. همهٔ اطلاعات فقط برای خود شماست.", Modifier.padding(12.dp, 8.dp), dismissKey = "emergency_info")
            CompactCard(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("نمایش کارت اضطراری در صفحهٔ ورود", style = MaterialTheme.typography.titleSmall)
                        Text("اگر روشن باشد، کارت بدون ورود به حساب هم در همین گوشی دیده می‌شود. هر کسی که گوشی را بردارد آن را می‌بیند.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                    }
                    Switch(optIn, { optIn = it; EmergencyCache.setShowOnLogin(ctx, it) })
                }
            }
            Spacer(Modifier.height(6.dp))
            RecListBody(st, "کارتی ندارید. اطلاعات مهم کودک را برای شرایط اضطراری بنویسید.", { tick++ }, Modifier.weight(1f)) { r ->
                CompactCard(onClick = { showing = r }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r["name"], style = MaterialTheme.typography.titleSmall)
                            val sub = listOf(children.nameOf(r["childId"]), r["diagnosis"]).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium, color = mutedColor(), maxLines = 1)
                            Text("برای نمایش تمام‌صفحه بزنید", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        OwnerActions({ editing = r }, { deleting = r })
                    }
                }
            }
        }
    }
    if ((adding || editing != null) && client != null) EmergencyEditor(client, api, children, editing, { adding = false; editing = null }) { adding = false; editing = null; tick++ }
    deleting?.let { d -> ConfirmDialog("این کارت حذف شود؟", onYes = { deleting = null; scope.launch { client?.delete("emergency", d.id); tick++ } }, onDismiss = { deleting = null }) }
}

@Composable
private fun EmergencyEditor(client: RecClient, api: com.hamraheteyf.app.net.HamrahApi?, children: List<ChildProfilePrivate>, initial: Rec?, onDismiss: () -> Unit, onDone: () -> Unit) {
    var newPhoto by remember { mutableStateOf<ByteArray?>(null) }
    RecEditorDialog(client, "emergency", if (initial == null) "کارت اضطراری جدید" else "ویرایش کارت", initial, emptyMap(), onDismiss, onDone,
        canSave = { it["childId"].orEmpty().isNotBlank() && it["name"].orEmpty().isNotBlank() },
        beforeSave = { data ->
            val b = newPhoto
            if (b != null && api != null) when (val u = api.uploadImageId(b)) {
                is com.hamraheteyf.app.net.ApiResult.Ok -> data["photo"] = u.data
                is com.hamraheteyf.app.net.ApiResult.Fail -> return@RecEditorDialog u.message
            }
            null
        }) { v, setError ->
        ChildChipRow(children, v["childId"].orEmpty(), { id -> v["childId"] = id; if (v["name"].isNullOrBlank()) v["name"] = children.nameOf(id) }, allLabel = null)
        fun tf(key: String, label: String, long: Boolean = false, phone: Boolean = false) = key to (label to (long to phone))
        listOf(tf("name", "نام کودک *"), tf("diagnosis", "تشخیص / وضعیت"), tf("sensory", "حساسیت‌های حسی", long = true), tf("triggers", "محرک‌ها", long = true),
            tf("calming", "چه چیزی آرامش می‌کند", long = true), tf("medical", "نکات پزشکی (دارو، آلرژی)", long = true),
            tf("contact1Name", "تماس اول: نام"), tf("contact1Phone", "تماس اول: شماره", phone = true),
            tf("contact2Name", "تماس دوم: نام"), tf("contact2Phone", "تماس دوم: شماره", phone = true),
        ).forEach { (k, rest) ->
            val (label, flags) = rest
            OutlinedTextField(v[k].orEmpty(), { v[k] = it }, label = { Text(label) }, singleLine = !flags.first, minLines = if (flags.first) 2 else 1, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (flags.second) androidx.compose.ui.text.input.KeyboardType.Phone else androidx.compose.ui.text.input.KeyboardType.Text))
        }
        ImagePickRow(api, "عکس کودک", v["photo"].orEmpty(), newPhoto, { newPhoto = it }, { newPhoto = null; v["photo"] = "" }, { setError(it) }, size = 72.dp)
    }
}
