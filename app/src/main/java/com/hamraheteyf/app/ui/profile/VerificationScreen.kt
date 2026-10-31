package com.hamraheteyf.app.ui.profile

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.data.model.VerificationStatus
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.prepareImageForUpload
import com.hamraheteyf.app.ui.common.toPersianDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RequestableTypes = listOf(AccountType.PROFESSIONAL, AccountType.EDUCATOR, AccountType.SOCIAL_WORKER)
private const val MAX_DOCS = 5
private const val MAX_PDF_BYTES = 7_000_000

/** A credential document: either already stored on the server (id) or picked locally (uri). */
private data class Doc(val id: String?, val uri: Uri?, val name: String)

private fun displayName(ctx: android.content.Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: "فایل"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val verification by vm.verification.collectAsState()
    val me by vm.currentUser.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    val status = verification?.status ?: VerificationStatus.NONE
    val locked = status == VerificationStatus.VERIFIED
    val myType = me?.accountType
    val initialType = remember(verification?.id, myType) {
        runCatching { AccountType.valueOf(verification?.requestedType.orEmpty()) }.getOrNull()?.takeIf { it in RequestableTypes }
            ?: myType?.takeIf { it in RequestableTypes } ?: AccountType.PROFESSIONAL
    }

    var type by rememberSaveable(verification?.id) { mutableStateOf(initialType.name) }
    var title by rememberSaveable(verification?.id) { mutableStateOf(verification?.professionalTitle.orEmpty()) }
    var license by rememberSaveable(verification?.id) { mutableStateOf(verification?.licenseNumber.orEmpty()) }
    var note by rememberSaveable(verification?.id) { mutableStateOf(verification?.credentialNote.orEmpty()) }
    val docs = remember(verification?.id) {
        mutableStateListOf<Doc>().also { l -> verification?.documentRefs?.forEachIndexed { i, r -> l.add(Doc(r.substringAfterLast('/'), null, "مدرک ارسال‌شدهٔ ${(i + 1).toPersianDigits()}")) } }
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val room = MAX_DOCS - docs.size
        uris.take(room).forEach { u -> docs.add(Doc(null, u, displayName(ctx, u))) }
        if (uris.size > room) error = "حداکثر ${MAX_DOCS.toPersianDigits()} فایل قابل ارسال است."
    }

    fun submit() {
        val api = container.serverApi
        scope.launch {
            busy = true; error = null
            val ids = mutableListOf<String>()
            for (d in docs) {
                if (d.id != null) { ids += d.id; continue }
                val uri = d.uri ?: continue
                val isPdf = ctx.contentResolver.getType(uri) == "application/pdf"
                val bytes = if (isPdf) withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
                else prepareImageForUpload(ctx, uri)
                if (bytes == null) { error = "«${d.name}» خوانده نشد."; busy = false; return@launch }
                if (isPdf && bytes.size > MAX_PDF_BYTES) { error = "«${d.name}» بیشتر از ۷ مگابایت است."; busy = false; return@launch }
                if (api == null) { error = "بارگذاری مدرک در حالت آفلاین ممکن نیست."; busy = false; return@launch }
                when (val u = api.upload(bytes, if (isPdf) "application/pdf" else "image/jpeg", d.name)) {
                    is ApiResult.Ok -> ids += u.data.substringAfterLast('/')
                    is ApiResult.Fail -> { error = u.message; busy = false; return@launch }
                }
            }
            vm.submitVerification(title.trim(), license.trim(), note.trim(), runCatching { AccountType.valueOf(type) }.getOrNull(), ids)
            busy = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { com.hamraheteyf.app.ui.common.ScreenHeader("بررسی تخصص", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            val v = verification
            val typeText = v?.requestedType?.let { t -> runCatching { AccountType.valueOf(t).label }.getOrNull() }
            InfoBanner(
                if (v == null) "هنوز درخواستی ثبت نکرده‌اید."
                else "وضعیت درخواست: ${v.status.label}" + (typeText?.let { "\nنقش درخواستی: $it" } ?: "") +
                    (v.reviewerNote?.takeIf { it.isNotBlank() }?.let { n -> "\nیادداشت بررسی: $n" } ?: ""),
                dismissKey = null,
            )
            Spacer(Modifier.height(12.dp))
            when (status) {
                VerificationStatus.VERIFIED -> Text("تخصص شما تأیید شده و نشان «تخصص تأییدشده» در پروفایل شما دیده می‌شود. نیازی به درخواست مجدد نیست؛ برای تغییر نقش با پشتیبانی تماس بگیرید.", style = MaterialTheme.typography.bodyMedium)
                VerificationStatus.PENDING -> Text("درخواست شما در صف بررسی است. اگر اطلاعات یا مدارک را کامل‌تر کردید، می‌توانید دوباره ارسال کنید؛ درخواست قبلی جایگزین می‌شود.", style = MaterialTheme.typography.bodyMedium)
                VerificationStatus.MORE_INFO_NEEDED -> Text("مدیر اطلاعات بیشتری می‌خواهد. توضیح بالا را بخوانید، فرم را تکمیل و دوباره ارسال کنید.", style = MaterialTheme.typography.bodyMedium)
                VerificationStatus.REJECTED -> Text("درخواست قبلی رد شد. با توجه به یادداشت بالا می‌توانید اطلاعات را اصلاح و دوباره ارسال کنید.", style = MaterialTheme.typography.bodyMedium)
                VerificationStatus.NONE -> Text(
                    "برای دریافت نشان «تخصص تأییدشده»، اطلاعات زیر برای بررسی به مدیران ارسال می‌شود. این اطلاعات در پروفایل عمومی نمایش داده نمی‌شوند. ثبت درخواست به‌معنای تأیید خودکار نیست.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!locked) {
                Spacer(Modifier.height(16.dp))
                Text("نقشی که برای آن درخواست می‌دهید", style = MaterialTheme.typography.titleSmall)
                RequestableTypes.forEach { t ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(type == t.name, { type = t.name })
                        Text(t.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text("عنوان تخصص") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = license, onValueChange = { license = it.take(60) }, label = { Text("شمارهٔ مجوز (اختیاری، خصوصی)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = note, onValueChange = { note = it.take(1000) }, label = { Text("توضیح مدارک (خصوصی)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
                Spacer(Modifier.height(12.dp))
                Text("مدارک (تصویر یا PDF، حداکثر ${MAX_DOCS.toPersianDigits()} فایل)", style = MaterialTheme.typography.titleSmall)
                Text("فقط مدیران می‌توانند مدارک را ببینند و هر مشاهده در گزارش تغییرات ثبت می‌شود.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                docs.forEachIndexed { i, d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AttachFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        IconButton(onClick = { docs.removeAt(i) }, enabled = !busy) { Icon(Icons.Filled.Close, contentDescription = "حذف فایل ${d.name}") }
                    }
                }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                    enabled = !busy && docs.size < MAX_DOCS,
                ) { Text("افزودن مدرک") }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(20.dp))
                val canSend = !busy && title.trim().length >= 2
                BigPrimaryButton(
                    text = when {
                        busy -> "در حال ارسال…"
                        status == VerificationStatus.NONE -> "ارسال درخواست بررسی"
                        else -> "ارسال مجدد درخواست"
                    },
                    enabled = canSend, onClick = { submit() },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
