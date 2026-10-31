package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.NotificationSettings
import com.hamraheteyf.app.data.model.PrivacySettings
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.mutedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val user by vm.currentUser.collectAsState()
    val blocked by vm.blockedUsers.collectAsState()
    val message by vm.message.collectAsState()
    val exportFile by vm.exportFile.collectAsState()
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val busy by vm.busy.collectAsState()
    val saveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val f = exportFile
        if (uri != null && f != null) {
            val ok = runCatching {
                appCtx.contentResolver.openOutputStream(uri)?.use { it.write(f.content.toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            vm.setMessage(if (ok) "فایل ذخیره شد." else "ذخیرهٔ فایل ناموفق بود.")
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var showDelete by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val privacy = user?.privacy ?: PrivacySettings()
    val notif = user?.notificationSettings ?: NotificationSettings()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("تنظیمات و حریم خصوصی", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("نمایش و حریم خصوصی")
            ToggleRow("نمایش شهر در پروفایل", privacy.showCity) { vm.updatePrivacy(privacy.copy(showCity = it)) }
            ToggleRow("نمایش معرفی در پروفایل", privacy.showBio) { vm.updatePrivacy(privacy.copy(showBio = it)) }
            ToggleRow("دریافت درخواست گفتگو", privacy.allowMessageRequests) { vm.updatePrivacy(privacy.copy(allowMessageRequests = it)) }

            SectionTitle("اعلان‌ها")
            ToggleRow("پاسخ به پرسش", notif.questionAnswered) { vm.updateNotificationSettings(notif.copy(questionAnswered = it)) }
            ToggleRow("پیام جدید", notif.newMessage) { vm.updateNotificationSettings(notif.copy(newMessage = it)) }
            ToggleRow("درخواست عضویت در گروه", notif.groupJoinRequest) { vm.updateNotificationSettings(notif.copy(groupJoinRequest = it)) }
            ToggleRow("پاسخ در گروه", notif.groupReply) { vm.updateNotificationSettings(notif.copy(groupReply = it)) }
            ToggleRow("نتیجهٔ بررسی تخصص", notif.verificationResult) { vm.updateNotificationSettings(notif.copy(verificationResult = it)) }
            ToggleRow("اطلاعیهٔ ایمنی و تغییر قوانین", notif.safetyNotice) { vm.updateNotificationSettings(notif.copy(safetyNotice = it)) }
            Text(
                "در اعلان گوشی، محتوای حساس نمایش داده نمی‌شود و فقط عبارتی مانند «پیام جدید دارید» دیده می‌شود.",
                style = MaterialTheme.typography.labelMedium, color = mutedColor(),
            )

            SectionTitle("کاربران مسدودشده")
            if (blocked.isEmpty()) Text("فهرست خالی است.", color = mutedColor(), style = MaterialTheme.typography.bodyMedium)
            blocked.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.displayName, Modifier.weight(1f))
                    TextButton(onClick = { vm.unblock(b.id) }) { Text("رفع مسدودیت") }
                }
            }

            SectionTitle("به‌روزرسانی برنامه")
            UpdateRow()
            SectionTitle("حساب و داده‌ها")
            OutlinedButton(enabled = !busy, onClick = { vm.exportMyData() }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "در حال آماده‌سازی…" else "دریافت نسخهٔ اطلاعات حساب") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showPassword = true }, modifier = Modifier.fillMaxWidth()) { Text("تغییر رمز عبور") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("خروج از حساب") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("حذف حساب و داده‌های شخصی") }

            SectionTitle("ارتباط با مدیران")
            Text("برای تماس با مدیران از دکمهٔ «گزارش» در هر پرسش، پیام یا پروفایل استفاده کنید؛ گزارش‌ها مستقیم به مدیران می‌رسد.", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDelete) {
        DeleteAccountDialog(busy, onDismiss = { showDelete = false }) { pw, report -> vm.deleteAccount(pw, report) }
    }
    if (showPassword) {
        ChangePasswordDialog(busy, onDismiss = { showPassword = false }) { cur, new, report -> vm.changePassword(cur, new, report) }
    }

    exportFile?.let { f ->
        AlertDialog(
            onDismissRequest = { vm.clearExportFile() },
            title = { Text("نسخهٔ اطلاعات حساب") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(f.summary.ifBlank { "فایل آمادهٔ ذخیره است." }, style = MaterialTheme.typography.bodyMedium) } },
            confirmButton = { TextButton(onClick = { saveLauncher.launch(f.filename) }) { Text("ذخیرهٔ فایل کامل (JSON)") } },
            dismissButton = { TextButton(onClick = { vm.clearExportFile() }) { Text("بستن") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun UpdateRow() {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<com.hamraheteyf.app.update.UpdateInfo?>(null) }
    var status by remember { mutableStateOf("نسخهٔ فعلی: ${com.hamraheteyf.app.BuildConfig.VERSION_NAME}") }
    var busy by remember { mutableStateOf(false) }
    Text(status, style = MaterialTheme.typography.bodyMedium, color = mutedColor())
    Spacer(Modifier.height(6.dp))
    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
        busy = true; status = "در حال بررسی…"
        scope.launch {
            runCatching { com.hamraheteyf.app.update.AppUpdater.check() }.fold(
                { r -> info = r; status = if (r == null) "برنامه به‌روز است (${com.hamraheteyf.app.BuildConfig.VERSION_NAME})" else "نسخهٔ ${r.versionName} موجود است" },
                { status = "بررسی ناموفق بود؛ اینترنت را کنترل کنید." },
            )
            busy = false
        }
    }) { Text("بررسی به‌روزرسانی") }
    info?.let { com.hamraheteyf.app.update.UpdateDialog(it) { info = null } }
}
