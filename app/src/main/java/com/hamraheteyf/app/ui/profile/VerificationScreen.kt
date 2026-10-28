package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.InfoBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val verification by vm.verification.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var title by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("بررسی تخصص", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            verification?.let {
                InfoBanner("وضعیت فعلی: ${it.status.label}" + (it.reviewerNote?.let { n -> "\nیادداشت بررسی: $n" } ?: ""))
                Spacer(Modifier.height(16.dp))
            }
            Text(
                "برای دریافت نشان «تخصص تأییدشده»، اطلاعات زیر برای بررسی به مدیران ارسال می‌شود. این اطلاعات در پروفایل عمومی نمایش داده نمی‌شوند. ثبت درخواست به‌معنای تأیید خودکار نیست.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان تخصص") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = license, onValueChange = { license = it }, label = { Text("شمارهٔ مجوز (اختیاری، خصوصی)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("توضیح مدارک (خصوصی)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "بارگذاری فایل مدرک در این نسخه فعال نیست؛ در نسخهٔ متصل به سرویس واقعی، فایل‌ها فقط برای مدیران قابل دسترسی و قابل حذف خواهند بود.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            BigPrimaryButton(text = "ارسال درخواست بررسی", onClick = { vm.submitVerification(title, license, note) })
            Spacer(Modifier.height(24.dp))
        }
    }
}
