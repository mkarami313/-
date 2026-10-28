package com.hamraheteyf.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.repository.fake.FakeStore
import com.hamraheteyf.app.data.repository.fake.SeedData
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.BigPrimaryButton

@Composable
fun LoginScreen(onGoToRegister: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AuthViewModel = viewModel(factory = appViewModelFactory { AuthViewModel(container.authRepository) })
    val state by vm.state.collectAsState()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("همراهِ طیف", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "فضایی محترمانه و رایگان برای خانواده‌ها و متخصصان",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("ایمیل") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("رمز عبور") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.info?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        BigPrimaryButton(
            text = if (state.loading) "در حال ورود…" else "ورود",
            onClick = { vm.login(email, password) },
            enabled = !state.loading,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.resetPassword(email) }) { Text("رمز عبور را فراموش کرده‌ام") }
        TextButton(onClick = onGoToRegister) { Text("حساب ندارید؟ ثبت‌نام کنید") }

        // Demo-only helper so reviewers can sign in without a backend (hidden in SERVER builds).
        if (com.hamraheteyf.app.BuildConfig.BACKEND == "DEMO") {
            Spacer(Modifier.height(24.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp)) {
                    Text("حالت نمایشی", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "برای آزمایش می‌توانید با ایمیل ${SeedData.EMAIL_FAMILY1} (خانواده) یا ${SeedData.EMAIL_ADMIN} (مدیر) و رمز ${FakeStore.DEMO_PASSWORD} وارد شوید.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
