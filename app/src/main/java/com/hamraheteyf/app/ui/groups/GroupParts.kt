package com.hamraheteyf.app.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.GroupItem
import com.hamraheteyf.app.community.Person
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.*

/** Cover thumbnail, or a tinted group icon when the group has none. */
@Composable
fun GroupThumb(coverId: String?, client: CommunityClient?, size: androidx.compose.ui.unit.Dp = 48.dp) {
    if (coverId != null && client != null) RemoteImage(client.fileUrl(coverId), Modifier.size(size).clip(RoundedCornerShape(12.dp)), displaySize = size)
    else IconBadge(Icons.Outlined.Groups, Apricot, size)
}

/** One row of the group directory. */
@Composable
fun GroupRow(g: GroupItem, client: CommunityClient?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupThumb(g.coverId, client)
            Spacer(Modifier.width(Dimens.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (g.isPrivate) { Spacer(Modifier.width(6.dp)); Pill("خصوصی", muted(), icon = Icons.Outlined.Lock) }
                }
                if (g.description.isNotBlank()) {
                    Text(g.description, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                    Counter(Icons.Outlined.People, g.memberCount, contentDescription = "عضو")
                    g.city?.takeIf { it.isNotBlank() }?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Place, null, tint = muted(), modifier = Modifier.size(14.dp))
                            Text(it, style = MaterialTheme.typography.labelMedium, color = muted())
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    when {
                        g.isMember -> Pill(if (g.myRole == "OWNER") "سازنده" else "عضو", MaterialTheme.colorScheme.primary, icon = Icons.Outlined.Check)
                        g.isPending -> Pill("در انتظارِ تأیید", GoldInk, icon = Icons.Outlined.HourglassTop)
                    }
                }
            }
        }
    }
}

/** Role label for a member, or null for plain members. */
fun roleLabel(role: String?): String? = when (role) { "OWNER" -> "سازنده"; "MODERATOR" -> "ناظر"; else -> null }

/** Member row with role pill and an optional trailing slot (menu / approve buttons). */
@Composable
fun MemberRow(p: Person, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Dimens.cardPad), verticalAlignment = Alignment.CenterVertically) {
        AuthorLine(p, p.since?.let { "عضویت: ${com.hamraheteyf.app.ui.common.relativeTimeFa(it)}" }, Modifier.weight(1f), onClick = onClick)
        roleLabel(p.role)?.let { Pill(it, if (p.role == "OWNER") ApricotInk else Expert) }
        trailing()
    }
}

/** Card section header with an item count. */
@Composable
fun CountHeader(title: String, count: Int) {
    Text("$title (${count.toPersianDigits()})", style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = Dimens.cardPad, vertical = Dimens.s))
}
