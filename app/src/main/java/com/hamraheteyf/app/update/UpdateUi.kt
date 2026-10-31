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
            TextButton(enabled = progress < 0, onClick = {
                if (!AppUpdater.canInstall(ctx)) { error = "اجازهٔ نصب از این برنامه را روشن کنید و دوباره بزنید."; AppUpdater.openInstallPermission(ctx); return@TextButton }
                error = null; progress = 0
                scope.launch {
                    try { AppUpdater.install(ctx, AppUpdater.download(ctx, info) { progress = it }); onDismiss() }
                    catch (e: Exception) { error = e.message ?: "دانلود ناموفق بود."; progress = -1 }
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
