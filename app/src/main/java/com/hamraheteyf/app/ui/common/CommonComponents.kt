package com.hamraheteyf.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.data.model.AuthorBadge
import com.hamraheteyf.app.data.model.ReportReason
import com.hamraheteyf.app.ui.theme.VerifiedGreen

/**
 * Remembers which notices/warnings the user has closed, per device. A notice with a
 * [dismissKey] gets a close (×) button; once closed it stays hidden across sessions.
 */
object DismissedNotices {
    private const val PREFS = "hamrah_notices"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isDismissed(c: Context, key: String): Boolean = prefs(c).getBoolean(key, false)
    fun dismiss(c: Context, key: String) = prefs(c).edit().putBoolean(key, true).apply()
}

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

/**
 * A soft informational banner (e.g. the privacy notice). Dismissible by default: the user
 * can close it and it stays hidden (keyed by [dismissKey]). Pass dismissKey = null to pin it.
 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    dismissKey: String? = "info_${text.hashCode()}",
) {
    DismissibleBanner(
        modifier = modifier,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = Icons.Outlined.Info,
        text = text,
        dismissKey = dismissKey,
    )
}

/** A prominent safety banner ("not a substitute for emergency care"). Dismissible by default. */
@Composable
fun SafetyBanner(
    modifier: Modifier = Modifier,
    dismissKey: String? = "safety_notice",
) {
    DismissibleBanner(
        modifier = modifier,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = Icons.Outlined.Warning,
        text = "این برنامه جایگزین پزشک، درمانگر، تشخیص حرفه‌ای یا خدمات اورژانسی نیست. در شرایط اضطراری با خدمات فوریت تماس بگیرید.",
        dismissKey = dismissKey,
    )
}

/** Shared body for the dismissible notice banners. */
@Composable
private fun DismissibleBanner(
    modifier: Modifier,
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    dismissKey: String?,
) {
    val context = LocalContext.current
    var visible by remember(dismissKey) {
        mutableStateOf(dismissKey == null || !DismissedNotices.isDismissed(context, dismissKey))
    }
    if (!visible) return
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.padding(vertical = 12.dp).size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            if (dismissKey != null) {
                IconButton(
                    onClick = { DismissedNotices.dismiss(context, dismissKey); visible = false },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "بستن اعلان", tint = content, modifier = Modifier.size(18.dp))
                }
            } else Spacer(Modifier.width(12.dp))
        }
    }
}

/** Banner whose dismissal lasts only until [resetKey] changes (e.g. one offline episode). */
@Composable
private fun EphemeralBanner(
    modifier: Modifier,
    resetKey: Any,
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    var closed by rememberSaveable(resetKey) { mutableStateOf(false) }
    if (closed) return
    Surface(color = container, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel, color = content) }
            IconButton(onClick = { closed = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "بستن اعلان", tint = content, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** "No connection — showing saved data" notice; hidden when [offline] is false. */
@Composable
fun OfflineBanner(offline: Boolean, modifier: Modifier = Modifier) {
    if (!offline) return
    EphemeralBanner(
        modifier, resetKey = "offline",
        container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer,
        icon = Icons.Outlined.CloudOff,
        text = "آفلاین هستید؛ آخرین اطلاعات ذخیره‌شده نمایش داده می‌شود.",
    )
}

/** Shown on the login screen after the server rejected the saved session. */
@Composable
fun SessionExpiredBanner(modifier: Modifier = Modifier) {
    EphemeralBanner(
        modifier, resetKey = "expired",
        container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer,
        icon = Icons.Outlined.Info,
        text = "نشست شما منقضی شده است؛ لطفاً دوباره وارد شوید.",
    )
}

/** Warns when reminders exist but system notifications are switched off, with a shortcut to the settings page. */
@Composable
fun NotificationsOffBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var blocked by remember { mutableStateOf(com.hamraheteyf.app.ui.modules.Reminders.notificationsBlocked(ctx)) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) blocked = com.hamraheteyf.app.ui.modules.Reminders.notificationsBlocked(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    if (!blocked) return
    EphemeralBanner(
        modifier, resetKey = "notif",
        container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = Icons.Outlined.Warning,
        text = "اعلان‌ها خاموش است و یادآورها نمایش داده نمی‌شوند.",
        actionLabel = "روشن کردن",
        onAction = {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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

/**
 * A compact dropdown filter (anchored read-only field + menu). Used in place of overflowing
 * horizontal chip rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(index); expanded = false },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
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
    var reason by rememberSaveable { mutableStateOf(ReportReason.DANGEROUS_ADVICE) }
    var note by rememberSaveable { mutableStateOf("") }
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
