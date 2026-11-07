package com.hamraheteyf.app.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.common.toPersianDigits
import kotlinx.coroutines.launch

/** Dialog that offers [info], downloads it and launches the system installer. */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    // A finished download waiting for the user: Android blocks starting the installer while the app is in the
    // background, so a download that finished after the user switched away used to vanish silently.
    var ready by remember { mutableStateOf<java.io.File?>(null) }
    @Suppress("DEPRECATION")
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    fun tryInstall(f: java.io.File) {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) { ready = f; return }
        runCatching { AppUpdater.install(ctx, f) }
            .onSuccess { onDismiss() }
            .onFailure { ready = f; error = "نصب‌کنندهٔ گوشی باز نشد؛ دوباره «نصب» را بزنید." }
    }
    AlertDialog(
        onDismissRequest = { if (progress < 0) onDismiss() },
        title = { Text("نسخهٔ ${info.versionName.toPersianDigits()} آماده است") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.notes.isNotBlank()) Text(info.notes)
                if (progress >= 0) { LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()); Text("${progress.toPersianDigits()}٪") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val pending = ready
            if (pending != null && progress < 0) TextButton(onClick = { error = null; ready = null; tryInstall(pending) }) { Text("نصب") }
            else TextButton(enabled = progress < 0, onClick = {
                if (!AppUpdater.canInstall(ctx)) {
                    error = if (AppUpdater.openInstallPermission(ctx)) "اجازهٔ نصب از این برنامه را روشن کنید و دوباره بزنید."
                    else "از تنظیماتِ گوشی، «نصب برنامه‌های ناشناس» را برای همراهِ طیف روشن کنید و دوباره بزنید."
                    return@TextButton
                }
                error = null; progress = 0
                scope.launch {
                    try {
                        val f = AppUpdater.download(ctx, info) { progress = it }
                        progress = -1
                        tryInstall(f)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: IllegalStateException) {
                        error = e.message ?: "دانلود ناموفق بود."; progress = -1
                    } catch (e: Exception) {
                        // IOException messages are English internals («timeout», «Unable to resolve host…»).
                        error = "دانلود ناموفق بود. اتصال اینترنت را بررسی کنید و دوباره تلاش کنید."; progress = -1
                    }
                }
            }) { Text("دانلود و نصب") }
        },
        dismissButton = { TextButton(enabled = progress < 0, onClick = onDismiss) { Text("بعداً") } },
    )
}

/** Silent check on app start; shows the dialog only when a newer version exists. */
@Composable
fun UpdateHost() {
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { info = runCatching { AppUpdater.check() }.getOrNull() }
    info?.let { UpdateDialog(it) { info = null } }
}
