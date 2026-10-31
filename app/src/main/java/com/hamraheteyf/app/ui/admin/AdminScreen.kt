package com.hamraheteyf.app.ui.admin

import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.AccountStatus
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.data.model.ContentType
import com.hamraheteyf.app.data.model.ReportStatus
import com.hamraheteyf.app.data.model.VerificationStatus
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.EmptyState
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.common.toPersianDigits

/** A pending admin action that needs a reason before it runs. */
private data class PendingReason(val title: String, val onConfirm: (String) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AdminViewModel = viewModel(factory = appViewModelFactory { AdminViewModel(container.adminRepository, container.reportRepository) })
    val requests by vm.verificationRequests.collectAsState()
    val reports by vm.reports.collectAsState()
    val actions by vm.actions.collectAsState()
    val message by vm.message.collectAsState()
    val credential by vm.credentialDialog.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var tab by rememberSaveable { mutableStateOf(0) }
    var pending by remember { mutableStateOf<PendingReason?>(null) }
    var verifFilter by rememberSaveable { mutableStateOf(VerificationStatus.PENDING.name) }
    val platform = rememberPlatformCall()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val docScope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("پنل مدیریت", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            InfoBanner("دسترسی مدیر محدود و قابل ممیزی است. مشاهدهٔ مدارک و تصمیم‌ها در «گزارش تغییرات» ثبت می‌شوند.", Modifier.padding(12.dp))
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                listOf("گزارش‌ها", "تأیید تخصص", "کاربران", "ماژول‌ها و نقش‌ها", "آمار", "اعلان همگانی", "تغییرات").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
                }
            }
            when (tab) {
                0 -> {
                    if (reports.isEmpty()) EmptyState("گزارش بازی وجود ندارد.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(reports, key = { it.id }) { report ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("هدف گزارش: ${report.contentType.label}", style = MaterialTheme.typography.titleMedium)
                                    Text("دلیل: ${report.reason.label}", style = MaterialTheme.typography.bodyMedium)
                                    report.note?.let { Text("توضیح: $it", style = MaterialTheme.typography.bodyMedium, color = mutedColor()) }
                                    Spacer(Modifier.height(8.dp))
                                    FlowButtons(
                                        *(if (report.contentType == ContentType.USER) arrayOf<Pair<String, () -> Unit>>(
                                            "محدودکردن کاربر" to { pending = PendingReason("دلیل محدودسازی") { vm.limitUser(report, AccountStatus.LIMITED, it) } },
                                            "مسدودکردن کاربر" to { pending = PendingReason("دلیل مسدودسازی") { vm.limitUser(report, AccountStatus.BLOCKED, it) } },
                                        ) else arrayOf<Pair<String, () -> Unit>>(
                                            "پنهان‌کردن" to { pending = PendingReason("دلیل پنهان‌کردن") { vm.hideReportedContent(report, it) } },
                                            "حذف محتوا" to { pending = PendingReason("دلیل حذف") { vm.deleteReportedContent(report, it) } },
                                        )),
                                        "رسیدگی شد" to { pending = PendingReason("یادداشت رسیدگی") { vm.resolveReport(report.id, ReportStatus.ACTIONED, it) } },
                                        "رد گزارش" to { pending = PendingReason("دلیل رد") { vm.resolveReport(report.id, ReportStatus.DISMISSED, it) } },
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> AdminUsersTab(onMessage = { vm.message.value = it })
                3 -> AdminModulesTab(onMessage = { vm.message.value = it })
                4 -> AdminStatsTab()
                5 -> AdminBroadcastTab(onMessage = { vm.message.value = it })
                1 -> {
                    val shown = requests.filter { verifFilter == "ALL" || it.status.name == verifFilter }.sortedByDescending { it.updatedAt }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            VerificationStatus.PENDING.name to "در انتظار", VerificationStatus.MORE_INFO_NEEDED.name to "نیاز به اطلاعات",
                            VerificationStatus.VERIFIED.name to "تأییدشده", VerificationStatus.REJECTED.name to "ردشده", "ALL" to "همه",
                        ).forEach { (k, label) ->
                            val n = requests.count { k == "ALL" || it.status.name == k }
                            FilterChip(verifFilter == k, { verifFilter = k }, label = { Text("$label (${n.toPersianDigits()})") })
                        }
                    }
                    if (shown.isEmpty()) EmptyState("درخواستی در این وضعیت وجود ندارد.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shown, key = { it.id }) { req ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(req.professionalTitle, style = MaterialTheme.typography.titleMedium)
                                    val rt = req.requestedType?.let { t -> runCatching { AccountType.valueOf(t).label }.getOrNull() }
                                    Text("نقش درخواستی: ${rt ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                                    Text("وضعیت: ${req.status.label} · ${relativeTimeFa(req.updatedAt)}", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                                    if (req.documentRefs.isNotEmpty()) Text("مدارک پیوست: ${req.documentRefs.size.toPersianDigits()} فایل", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                                    req.reviewerNote?.takeIf { it.isNotBlank() }?.let { Text("یادداشت بررسی: $it", style = MaterialTheme.typography.labelMedium, color = mutedColor()) }
                                    Spacer(Modifier.height(8.dp))
                                    FlowButtons(*listOfNotNull<Pair<String, () -> Unit>>(
                                        "مشاهدهٔ مدرک" to { vm.viewCredential(req.id) },
                                        if (req.status != VerificationStatus.VERIFIED) "تأیید" to { pending = PendingReason("دلیل تأیید (برای کاربر ارسال می‌شود)") { vm.review(req.id, VerificationStatus.VERIFIED, it) } } else null,
                                        "رد" to { pending = PendingReason(if (req.status == VerificationStatus.VERIFIED) "دلیل لغو تأیید (نقش به «خانواده» برمی‌گردد)" else "دلیل رد (برای کاربر ارسال می‌شود)") { vm.review(req.id, VerificationStatus.REJECTED, it) } },
                                        if (req.status != VerificationStatus.VERIFIED) "نیاز به اطلاعات" to { pending = PendingReason("توضیح اطلاعات لازم (برای کاربر ارسال می‌شود)") { vm.review(req.id, VerificationStatus.MORE_INFO_NEEDED, it) } } else null,
                                    ).toTypedArray())
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (actions.isEmpty()) EmptyState("گزارشی ثبت نشده است.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(actions, key = { it.id }) { a ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(a.actionType.label, style = MaterialTheme.typography.titleMedium)
                                    Text("دلیل: ${a.reason}", style = MaterialTheme.typography.bodyMedium)
                                    Text(relativeTimeFa(a.createdAt), style = MaterialTheme.typography.labelMedium, color = mutedColor())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pending?.let { p ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(p.title) },
            text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("دلیل تصمیم (الزامی)") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { p.onConfirm(reason); pending = null }, enabled = reason.isNotBlank()) { Text("ثبت") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("انصراف") } },
        )
    }

    credential?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.clearCredentialDialog() },
            title = { Text("مدارک (محرمانه)") },
            text = {
                Column {
                    Text("عنوان: ${c.professionalTitle}")
                    Text("نقش درخواستی: ${c.requestedType?.let { t -> runCatching { AccountType.valueOf(t).label }.getOrNull() } ?: "-"}")
                    Text("شمارهٔ مجوز: ${c.licenseNumber ?: "-"}")
                    Text("توضیح: ${c.credentialNote ?: "-"}")
                    if (c.documentRefs.isEmpty()) Text("فایلی پیوست نشده است.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                    c.documentRefs.forEachIndexed { i, ref ->
                        TextButton(onClick = {
                            docScope.launch {
                                val url = com.hamraheteyf.app.net.FileUrls.resolve(ref.substringAfterLast('/'), force = true)
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }) { Text("باز کردن مدرک ${(i + 1).toPersianDigits()}") }
                    }
                    Text("این مشاهده در گزارش تغییرات ثبت شد.", style = MaterialTheme.typography.labelMedium, color = mutedColor())
                }
            },
            confirmButton = { TextButton(onClick = { vm.clearCredentialDialog() }) { Text("بستن") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowButtons(vararg buttons: Pair<String, () -> Unit>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (label, action) ->
            OutlinedButton(onClick = action) { Text(label) }
        }
    }
}
