package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.VerificationStatus
import com.hamraheteyf.app.data.model.canRequestVerification
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.data.model.resolveAuthorBadge
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.AuthorBadgeChip
import com.hamraheteyf.app.ui.common.mutedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTabScreen(
    onEditProfile: () -> Unit,
    onProfessional: () -> Unit,
    onVerification: () -> Unit,
    onChildren: () -> Unit,
    onSettings: () -> Unit,
    onAdmin: () -> Unit,
    onRules: () -> Unit,
    onPrivacy: () -> Unit,
    onSharesReceived: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val user by vm.currentUser.collectAsState()
    val professional by vm.professional.collectAsState()
    val verification by vm.verification.collectAsState()

    Scaffold(topBar = { com.hamraheteyf.app.ui.common.ScreenHeader("پروفایل") }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (user == null) {
                // Signed in but the profile document hasn't loaded/been created yet. Always
                // offer a way out so the session can never get stuck.
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("در حال آماده‌سازی حساب…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "اگر ادامه نیافت، یک‌بار خارج و دوباره وارد شوید.",
                        style = MaterialTheme.typography.bodyMedium, color = mutedColor(),
                    )
                    Spacer(Modifier.height(16.dp))
                    com.hamraheteyf.app.ui.common.BigPrimaryButton(text = "خروج از حساب", onClick = { vm.logout() })
                }
            }
            user?.let { u ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        AuthorBadgeChip(resolveAuthorBadge(u.accountType, professional?.verificationStatus))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(u.accountType.label, style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                    if (u.accountType == AccountType.COMMUNITY) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "به‌عنوان عضو جامعه می‌توانید در پرسش‌وپاسخ و گروه‌ها مشارکت کنید؛ پروندهٔ کودک و ابزارهای خانواده مخصوص حساب خانواده است.",
                            style = MaterialTheme.typography.labelMedium, color = mutedColor(),
                        )
                    }
                    u.bio?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                HorizontalDivider()

                Entry("ویرایش پروفایل", onEditProfile)
                val isPro = u.accountType.canRequestVerification()
                if (isPro || verification != null) Entry("پروفایل تخصصی", onProfessional)
                if (!u.isAdmin) {
                    val vStatus = verification?.status ?: professional?.verificationStatus ?: VerificationStatus.NONE
                    Entry(
                        if (vStatus == VerificationStatus.NONE) "درخواست نشان متخصص (پزشک، مربی، مددکار)" else "بررسی تخصص — ${vStatus.label}",
                        onVerification,
                    )
                }
                if (u.accountType == AccountType.FAMILY) {
                    Entry("اطلاعات کودک (خصوصی)", onChildren)
                }
                if (isPro) Entry("اشتراک‌های دریافتی (پروندهٔ کودکِ دیگران)", onSharesReceived)
                Entry("تنظیمات و حریم خصوصی", onSettings)
                if (u.isAdmin) Entry("پنل مدیریت", onAdmin)
                HorizontalDivider()
                Entry("قوانین جامعه", onRules)
                Entry("سیاست حریم خصوصی", onPrivacy)
                HorizontalDivider()

                TextButton(onClick = { vm.logout() }, modifier = Modifier.padding(16.dp)) { Text("خروج از حساب") }
            }
        }
    }
}

@Composable
private fun Entry(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Icon(Icons.Filled.ChevronLeft, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}
