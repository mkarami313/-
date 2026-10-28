package com.hamraheteyf.app.ui.admin

import androidx.compose.foundation.layout.*
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
import com.hamraheteyf.app.data.model.ReportStatus
import com.hamraheteyf.app.data.model.VerificationStatus
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.EmptyState
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.relativeTimeFa

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("پنل مدیریت", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            InfoBanner("دسترسی مدیر محدود و قابل ممیزی است. مشاهدهٔ مدارک و تصمیم‌ها در «گزارش تغییرات» ثبت می‌شوند.", Modifier.padding(12.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("گزارش‌ها") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("تأیید تخصص") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("تغییرات") })
            }
            when (tab) {
                0 -> {
                    if (reports.isEmpty()) EmptyState("گزارش بازی وجود ندارد.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(reports, key = { it.id }) { report ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("نوع محتوا: ${report.contentType.label}", style = MaterialTheme.typography.titleMedium)
                                    Text("دلیل: ${report.reason.label}", style = MaterialTheme.typography.bodyMedium)
                                    report.note?.let { Text("توضیح: $it", style = MaterialTheme.typography.bodyMedium, color = mutedColor()) }
                                    Spacer(Modifier.height(8.dp))
                                    FlowButtons(
                                        "پنهان‌کردن" to { pending = PendingReason("دلیل پنهان‌کردن") { vm.hideReportedContent(report, it) } },
                                        "حذف محتوا" to { pending = PendingReason("دلیل حذف") { vm.deleteReportedContent(report, it) } },
                                        "رسیدگی شد" to { pending = PendingReason("یادداشت رسیدگی") { vm.resolveReport(report.id, ReportStatus.ACTIONED, it) } },
                                        "رد گزارش" to { pending = PendingReason("دلیل رد") { vm.resolveReport(report.id, ReportStatus.DISMISSED, it) } },
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (requests.isEmpty()) EmptyState("درخواستی وجود ندارد.")
                    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(requests, key = { it.id }) { req ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(req.professionalTitle, style = MaterialTheme.typography.titleMedium)
                                    Text("وضعیت: ${req.status.label}", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                                    Spacer(Modifier.height(8.dp))
                                    FlowButtons(
                                        "مشاهدهٔ مدرک" to { vm.viewCredential(req.id) },
                                        "تأیید" to { pending = PendingReason("دلیل تأیید") { vm.review(req.id, VerificationStatus.VERIFIED, it) } },
                                        "رد" to { pending = PendingReason("دلیل رد") { vm.review(req.id, VerificationStatus.REJECTED, it) } },
                                        "نیاز به اطلاعات" to { pending = PendingReason("توضیح اطلاعات لازم") { vm.review(req.id, VerificationStatus.MORE_INFO_NEEDED, it) } },
                                    )
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
                    Text("شمارهٔ مجوز: ${c.licenseNumber ?: "-"}")
                    Text("توضیح: ${c.credentialNote ?: "-"}")
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
