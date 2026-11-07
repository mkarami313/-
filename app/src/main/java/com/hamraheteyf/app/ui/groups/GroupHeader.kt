package com.hamraheteyf.app.ui.groups

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.ui.semantics.Role
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.GroupInfo
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.*

/** Cover, title, description, meta, expandable rules and the membership action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupHeader(
    info: GroupInfo,
    client: CommunityClient?,
    busy: Boolean,
    onJoin: () -> Unit,
    onCancel: () -> Unit,
    onNotify: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    val g = info.group
    val isPrivate = g.visibility == "PRIVATE"
    Column {
        val cover = g.coverId
        if (cover != null && client != null) {
            RemoteImage(client.fileUrl(cover), Modifier.fillMaxWidth().aspectRatio(3f))
        }
        AppCard(Modifier.padding(Dimens.gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cover == null) { GroupThumb(null, client, 52.dp); Spacer(Modifier.width(Dimens.m)) }
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.s), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Counter(Icons.Outlined.People, g.memberCount, contentDescription = "عضو")
                        g.city?.takeIf { it.isNotBlank() }?.let { Pill(it, muted(), icon = Icons.Outlined.Place) }
                        Pill(if (isPrivate) "خصوصی" else "عمومی", muted(), icon = if (isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public)
                        if (g.status == "ARCHIVED") Pill("بسته‌شده", Danger, icon = Icons.Outlined.Archive)
                        when (info.myRole) {
                            "OWNER" -> Pill("سازنده", ApricotInk)
                            "MODERATOR" -> Pill("ناظر", Expert)
                        }
                    }
                }
            }
            if (g.description.isNotBlank()) {
                Spacer(Modifier.height(Dimens.s))
                Text(g.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (g.rules.isNotBlank()) {
                var open by rememberSaveable { mutableStateOf(false) }
                Spacer(Modifier.height(Dimens.s))
                Surface(onClick = { open = !open }, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                    Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Gavel, null, tint = muted(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("قوانینِ گروه", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "بستن" else "نمایش", tint = muted())
                        }
                        if (open) { Spacer(Modifier.height(4.dp)); Text(g.rules, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.m))
            MembershipAction(info, busy, onJoin, onCancel, onNotify, onLeave)
        }
    }
}

@Composable
private fun MembershipAction(info: GroupInfo, busy: Boolean, onJoin: () -> Unit, onCancel: () -> Unit, onNotify: (Boolean) -> Unit, onLeave: () -> Unit) {
    val isPrivate = info.group.visibility == "PRIVATE"
    val archived = info.group.status == "ARCHIVED"
    when (info.myStatus) {
        // Stacked, not side by side: at large font sizes the switch label and the button no longer squeeze each other.
        "ACTIVE" -> Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dimens.touch)
                    .toggleable(info.notify, enabled = !busy, role = Role.Switch, onValueChange = onNotify),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (info.notify) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff, null, tint = muted(), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("اعلانِ گفتگوهای تازه", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(info.notify, null, enabled = !busy)
            }
            OutlinedButton(onClick = onLeave, enabled = !busy, modifier = Modifier.align(Alignment.End).heightIn(min = Dimens.touch)) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("ترکِ گروه")
            }
        }
        "REQUESTED" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("در انتظارِ تأییدِ مدیر", GoldInk, icon = Icons.Outlined.HourglassTop)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onCancel, enabled = !busy) { Text("لغوِ درخواست") }
        }
        "REMOVED" -> Text("مدیرِ گروه شما را از این گروه حذف کرده است و امکانِ پیوستنِ دوباره نیست.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        else -> if (archived) Text("این گروه بسته شده است و عضوِ تازه نمی‌پذیرد.", style = MaterialTheme.typography.bodyMedium, color = muted())
        else Button(onClick = onJoin, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else {
                Icon(if (isPrivate) Icons.Outlined.Lock else Icons.Outlined.GroupAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isPrivate) "درخواستِ عضویت" else "عضویت")
            }
        }
    }
}
