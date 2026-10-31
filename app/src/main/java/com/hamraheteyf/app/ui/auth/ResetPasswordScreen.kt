package com.hamraheteyf.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.common.BigPrimaryButton
import com.hamraheteyf.app.ui.common.ScreenHeader
import com.hamraheteyf.app.ui.common.mutedColor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Two-step password reset: email -> emailed 8-digit code + new password. */
@Composable
fun ResetPasswordScreen(onBack: () -> Unit, initialEmail: String = "") {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("فراموشی رمز عبور", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (done) {
                Text("رمز عبور شما تغییر کرد.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("اکنون با رمز تازه وارد شوید.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                BigPrimaryButton("بازگشت به ورود", onClick = onBack)
                return@Column
            }
            if (step == 1) {
                Text("ایمیلی را که با آن ثبت‌نام کرده‌اید وارد کنید.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim() }, label = { Text("ایمیل") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Spacer(Modifier.height(16.dp))
                BigPrimaryButton(
                    if (busy) "در حال ارسال…" else "ارسال کد به ایمیل",
                    enabled = !busy && email.contains("@"),
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            val r = AuthHttp.post("auth/forgot", buildJsonObject { put("email", email) }.toString())
                            busy = false
                            if (r.ok) step = 2 else error = r.error
                        }
                    },
                )
            } else {
                Text(
                    "اگر این ایمیل ثبت شده باشد، کد ۸ رقمی برایتان ارسال می‌شود (ممکن است در پوشهٔ اسپم باشد).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) },
                    label = { Text("کد ۸ رقمی") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("رمز عبور تازه") },
                    supportingText = { Text("دست‌کم ۶ نویسه") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(16.dp))
                BigPrimaryButton(
                    if (busy) "در حال ثبت…" else "تغییر رمز عبور",
                    enabled = !busy && code.length == 8 && pass.length >= 6,
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            val r = AuthHttp.post(
                                "auth/reset",
                                buildJsonObject { put("email", email); put("code", code); put("newPassword", pass) }.toString(),
                            )
                            busy = false
                            if (r.ok) done = true else error = r.error
                        }
                    },
                )
                TextButton(onClick = { step = 1; code = ""; error = null }) { Text("ارسال دوباره یا تغییر ایمیل") }
                Text("کد نرسید؟ از پشتیبانی بخواهید.", style = MaterialTheme.typography.bodySmall, color = mutedColor())
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
