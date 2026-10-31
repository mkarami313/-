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
    val context = androidx.compose.ui.platform.LocalContext.current

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPro by rememberSaveable { mutableStateOf(false) }
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
            InfoBanner("برای شروع فقط چند اطلاعات ساده لازم است.")
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

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().selectable(selected = isPro, onClick = { isPro = !isPro }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isPro, onCheckedChange = { isPro = it })
                Text("من متخصص هستم (مشاور، درمانگر، مربی…)", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (isPro) "حساب شما ابتدا با نقش خانواده ساخته می‌شود و بلافاصله بعد از ورود، فرم درخواست تأیید تخصص باز می‌شود. نشان «تخصص تأییدشده» فقط پس از بررسی مدارک توسط مدیر داده می‌شود."
                else "همهٔ حساب‌ها با نقش «خانواده» شروع می‌شوند. اگر متخصص هستید، گزینهٔ بالا را بزنید.",
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
                onClick = {
                    com.hamraheteyf.app.ui.onboarding.OnboardingPrefs.setPendingPro(context, isPro)
                    vm.register(name, email, password, AccountType.FAMILY, accepted)
                },
                enabled = !state.loading,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
