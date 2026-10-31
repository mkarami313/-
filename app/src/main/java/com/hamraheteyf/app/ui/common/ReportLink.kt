package com.hamraheteyf.app.ui.common

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.admin.rememberPlatformCall
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Report dialog state + submission for a content type the typed repositories do not cover (e.g. "DONATION"). */
@Composable
fun rememberReporter(type: String, id: String): Pair<() -> Unit, @Composable () -> Unit> {
    val call = rememberPlatformCall()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val dialog: @Composable () -> Unit = {
        if (open) ReportDialog(onDismiss = { open = false }, onSubmit = { reason, note ->
            open = false
            scope.launch {
                val r = call?.call("createReport", buildJsonObject {
                    put("contentType", type); put("contentId", id); put("reason", reason.name); put("note", note)
                })
                Toast.makeText(ctx, if (r is ApiResult.Fail) r.message else "گزارش شما ثبت شد.", Toast.LENGTH_SHORT).show()
            }
        })
    }
    return Pair({ open = true }, dialog)
}

/** A small «گزارش» text button with its dialog attached. */
@Composable
fun ReportLink(type: String, id: String, label: String = "گزارش", modifier: Modifier = Modifier) {
    val (open, dialog) = rememberReporter(type, id)
    TextButton(onClick = open, modifier = modifier) {
        Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
    dialog()
}
