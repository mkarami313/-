package com.hamraheteyf.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.common.mutedColor

private const val MIN_PASSWORD = 6

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** [onSubmit] gets (current, new, report) and must call `report(errorOrNull)`; the dialog closes itself on success. */
@Composable
fun ChangePasswordDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String, (String?) -> Unit) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val localError = when {
        new.isNotEmpty() && new.length < MIN_PASSWORD -> "رمز جدید باید حداقل ۶ نویسه باشد."
        repeat.isNotEmpty() && repeat != new -> "تکرار رمز با رمز جدید یکی نیست."
        else -> null
    }
    val canSubmit = !busy && current.isNotEmpty() && new.length >= MIN_PASSWORD && repeat == new
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("تغییر رمز عبور") },
        text = {
            Column {
                PasswordField(current, { current = it; error = null }, "رمز فعلی", !busy)
                Spacer(Modifier.height(8.dp))
                PasswordField(new, { new = it; error = null }, "رمز جدید (حداقل ۶ نویسه)", !busy)
                Spacer(Modifier.height(8.dp))
                PasswordField(repeat, { repeat = it; error = null }, "تکرار رمز جدید", !busy)
                (error ?: localError)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text("پس از تغییر، دستگاه‌های دیگر از حساب شما خارج می‌شوند.", style = MaterialTheme.typography.bodySmall, color = mutedColor())
            }
        },
        confirmButton = {
            TextButton(enabled = canSubmit, onClick = { onSubmit(current, new) { err -> if (err == null) onDismiss() else error = err } }) {
                Text(if (busy) "در حال تغییر…" else "تغییر رمز")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun DeleteAccountDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, (String?) -> Unit) -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("حذف حساب") },
        text = {
            Column {
                Text("با حذف حساب، اطلاعات شخصی شما (پروفایل، اطلاعات کودک، پیام‌ها و عضویت‌ها) حذف می‌شوند و مشارکت‌های عمومی‌تان ناشناس می‌شوند. این عمل قابل بازگشت نیست.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PasswordField(password, { password = it; error = null }, "برای تأیید، رمز عبور را وارد کنید", !busy)
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && password.isNotEmpty(), onClick = { onSubmit(password) { err -> if (err != null) error = err } }) {
                Text(if (busy) "در حال حذف…" else "حذف می‌کنم", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("انصراف") } },
    )
}
