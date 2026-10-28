package com.hamraheteyf.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.InfoBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AuthViewModel = viewModel(factory = appViewModelFactory { AuthViewModel(container.authRepository) })
    val state by vm.state.collectAsState()

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var accountType by rememberSaveable { mutableStateOf(AccountType.FAMILY) }
    var accepted by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            com.hamraheteyf.app.ui.common.ScreenHeader("ثبت‌نام", onBack = onBack)
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            InfoBanner("برای شروع فقط چند اطلاعات ساده لازم است. انتخاب نقش «متخصص» به‌خودی‌خود نشان «تخصص تأییدشده» نمی‌دهد؛ تأیید پس از بررسی مدارک توسط مدیر انجام می‌شود.")
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام نمایشی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("ایمیل") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("رمز عبور (حداقل ۶ نویسه)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            Spacer(Modifier.height(16.dp))
            Text("نوع حساب", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            AccountType.entries.forEach { type ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = accountType == type, onClick = { accountType = type }).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = accountType == type, onClick = { accountType = type })
                    Spacer(Modifier.width(4.dp))
                    Text(type.label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "تفاوت حساب‌ها: «خانواده» برای مراقبان کودک است، «عضو جامعه» برای همراهان داوطلب، و «متخصص» برای ارائه‌دهندگان خدمت که می‌توانند درخواست تأیید تخصص بدهند.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Text("قوانین جامعه و سیاست حریم خصوصی را می‌پذیرم.", style = MaterialTheme.typography.bodyMedium)
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            BigPrimaryButton(
                text = if (state.loading) "در حال ایجاد حساب…" else "ایجاد حساب",
                onClick = { vm.register(name, email, password, accountType, accepted) },
                enabled = !state.loading,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
