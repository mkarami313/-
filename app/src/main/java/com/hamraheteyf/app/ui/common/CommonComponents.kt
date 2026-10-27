package com.hamraheteyf.app.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.AuthorBadge
import com.hamraheteyf.app.data.model.ReportReason
import com.hamraheteyf.app.ui.theme.VerifiedGreen

/** A colored chip showing the author's role/verification badge. */
@Composable
fun AuthorBadgeChip(badge: AuthorBadge, modifier: Modifier = Modifier) {
    val verified = badge == AuthorBadge.PROFESSIONAL_VERIFIED
    val container = when (badge) {
        AuthorBadge.PROFESSIONAL_VERIFIED -> VerifiedGreen.copy(alpha = 0.14f)
        AuthorBadge.PROFESSIONAL_UNVERIFIED -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = if (verified) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = "نشان: ${badge.label}" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            if (verified) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = VerifiedGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(badge.label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        }
    }
}

/** A soft informational banner (e.g. the medical disclaimer). */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** A prominent safety banner used where the "not a substitute for emergency care" notice matters. */
@Composable
fun SafetyBanner(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "این برنامه جایگزین پزشک، درمانگر، تشخیص حرفه‌ای یا خدمات اورژانسی نیست. در شرایط اضطراری با خدمات فوریت تماس بگیرید.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("تلاش دوباره") }
        }
    }
}

/** A large, high-contrast primary action button for low-tech-literacy users. */
@Composable
fun BigPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/**
 * Report dialog shared across content types. Lets the user pick a [ReportReason] and add an
 * optional note.
 */
@Composable
fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String) -> Unit,
) {
    var reason by remember { mutableStateOf(ReportReason.DANGEROUS_ADVICE) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش محتوا") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("دلیل گزارش را انتخاب کنید:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                ReportReason.entries.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == r, onClick = { reason = r }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Spacer(Modifier.width(4.dp))
                        Text(r.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("توضیح اختیاری") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(reason, note) }) { Text("ارسال گزارش") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** Small helper to tint text in a muted color. */
@Composable
fun mutedColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant
