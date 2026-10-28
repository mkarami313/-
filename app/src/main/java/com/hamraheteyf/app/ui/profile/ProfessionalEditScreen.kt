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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.InfoBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalEditScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AccountViewModel = viewModel(factory = appViewModelFactory { AccountViewModel(container.authRepository, container.userRepository) })
    val prof by vm.professional.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var title by remember(prof) { mutableStateOf(prof?.title ?: "") }
    var areas by remember(prof) { mutableStateOf(prof?.workAreas?.joinToString("، ") ?: "") }
    var city by remember(prof) { mutableStateOf(prof?.city ?: "") }
    var online by remember(prof) { mutableStateOf(prof?.offersOnline ?: false) }
    var experience by remember(prof) { mutableStateOf(prof?.experience ?: "") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("پروفایل تخصصی", onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            InfoBanner("این اطلاعات عمومی است. مدارک و شمارهٔ مجوز در این بخش ثبت نمی‌شوند؛ آن‌ها فقط در بخش «بررسی تخصص» و برای مدیران ارسال می‌شوند.")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان تخصص") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = areas, onValueChange = { areas = it }, label = { Text("حوزه‌های کاری (با «،» جدا کنید)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("شهر (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = experience, onValueChange = { experience = it }, label = { Text("سابقهٔ کاری (اختیاری)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = online, onCheckedChange = { online = it })
                Spacer(Modifier.width(8.dp))
                Text("امکان پاسخ‌گویی آنلاین دارم")
            }
            Spacer(Modifier.height(20.dp))
            BigPrimaryButton(text = "ذخیره", onClick = {
                val areaList = areas.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() }
                vm.upsertProfessional(title, areaList, city, online, experience)
            })
        }
    }
}
