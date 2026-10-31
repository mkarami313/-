package com.hamraheteyf.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.repository.fake.FakeStore
import com.hamraheteyf.app.data.repository.fake.SeedData
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.security.BiometricGate
import com.hamraheteyf.app.security.LocalCredentialVault
import com.hamraheteyf.app.ui.common.BigPrimaryButton

@Composable
fun LoginScreen(onGoToRegister: () -> Unit, onGoToReset: (String) -> Unit = {}, onEmergencyCard: (() -> Unit)? = null) {
    val container = LocalAppContainer.current
    val vault = LocalCredentialVault.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val vm: AuthViewModel = viewModel(
        factory = appViewModelFactory { AuthViewModel(container.authRepository, vault) }
    )
    val state by vm.state.collectAsState()

    // Whether this device can offer fingerprint sign-in at all.
    val biometricPossible = remember { vault.isSupported() && BiometricGate.canAuthenticate(context) && activity != null }
    var hasSaved by remember { mutableStateOf(vault.hasCredentials()) }

    var email by rememberSaveable { mutableStateOf(vault.savedEmail().orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(biometricPossible) }
    var bioError by remember { mutableStateOf<String?>(null) }

    fun signInWithFingerprint() {
        val act = activity ?: return
        BiometricGate.prompt(
            activity = act,
            onSuccess = {
                val creds = vault.load()
                if (creds != null) {
                    email = creds.first
                    vm.login(creds.first, creds.second, remember = true)
                } else {
                    vault.clear(); hasSaved = false
                    bioError = "خواندن مشخصات ذخیره‌شده ممکن نشد؛ لطفاً با رمز وارد شوید."
                }
            },
            onError = { bioError = it },
        )
    }

    // Offer the fingerprint prompt automatically when there is something saved.
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (biometricPossible && hasSaved && !autoPrompted) {
            autoPrompted = true
            signInWithFingerprint()
        }
    }

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

        // "Remember me" — enables fingerprint sign-in next time.
        if (biometricPossible) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = remember, onCheckedChange = { remember = it })
                Text(
                    "ذخیرهٔ ورود و فعال‌سازی ورود با اثر انگشت",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        (bioError ?: state.info)?.let {
            Spacer(Modifier.height(8.dp))
            val color = if (bioError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Text(it, color = color, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        BigPrimaryButton(
            text = if (state.loading) "در حال ورود…" else "ورود",
            onClick = { bioError = null; vm.login(email, password, remember) },
            enabled = !state.loading,
        )

        // Fingerprint sign-in (shown only when credentials are saved on this device).
        if (biometricPossible && hasSaved) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { bioError = null; signInWithFingerprint() },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("ورود با اثر انگشت")
            }
            TextButton(onClick = { vault.clear(); hasSaved = false }) {
                Text("حذف ورود ذخیره‌شده روی این دستگاه")
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { onGoToReset(email) }) { Text("فراموشی رمز") }
        TextButton(onClick = onGoToRegister) { Text("حساب ندارید؟ ثبت‌نام کنید") }
        if (onEmergencyCard != null) TextButton(onClick = onEmergencyCard) { Text("کارت اضطراری") }

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
