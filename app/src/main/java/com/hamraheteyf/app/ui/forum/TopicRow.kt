package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.TopicCard
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Expert
import com.hamraheteyf.app.ui.theme.ExpertContainer
import com.hamraheteyf.app.ui.theme.GoldInk

/**
 * The one topic list item of the app: author, title (bold when unread), excerpt, status pills,
 * counters and the time of the last activity; the first image as a thumbnail on the far side.
 */
@Composable
fun TopicRow(t: TopicCard, onClick: () -> Unit, modifier: Modifier = Modifier, place: String? = null, client: CommunityClient? = rememberCommunity()) {
    AppCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(t.author.name, t.author.photoUrl, 22.dp, t.author.anonymous)
                    Spacer(Modifier.width(6.dp))
                    Text(t.author.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    if (t.author.expert) { Spacer(Modifier.width(3.dp)); Icon(VerifiedIcon, "متخصص", tint = Expert, modifier = Modifier.size(13.dp)) }
                    if (place != null) {
                        Text(" · $place", style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    if (t.unread) {
                        Box(Modifier.padding(top = 8.dp, end = 6.dp).size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .semantics { contentDescription = "پاسخِ خوانده‌نشده" })
                    }
                    Text(
                        t.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (t.unread) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (t.excerpt.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(t.excerpt, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TopicPills(t)
            }
            val img = t.images.firstOrNull()
            if (img != null && client != null) {
                Spacer(Modifier.width(Dimens.s))
                RemoteImage(client.fileUrl(img), Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), displaySize = 56.dp)
            }
        }
        Spacer(Modifier.height(Dimens.s))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.m)) {
            Counter(Icons.Outlined.ChatBubbleOutline, t.replyCount, contentDescription = "پاسخ")
            Counter(Icons.Outlined.FavoriteBorder, t.likeCount, contentDescription = "پسند")
            Counter(Icons.Outlined.Visibility, t.viewCount, contentDescription = "بازدید")
            Spacer(Modifier.weight(1f))
            Text(relativeTimeFa(t.lastPostAt.takeIf { it > 0 } ?: t.createdAt), style = MaterialTheme.typography.labelSmall, color = muted())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicPills(t: TopicCard) {
    val pills = buildList<@Composable () -> Unit> {
        if (t.pinned) add { Pill("سنجاق‌شده", GoldInk, icon = Icons.Outlined.PushPin) }
        if (t.locked) add { Pill("بسته", muted(), icon = Icons.Outlined.Lock) }
        if (t.expertReplyCount > 0) add { Pill("پاسخِ متخصص", Expert, ExpertContainer, icon = VerifiedIcon) }
        else if (t.kind == "question" && t.replyCount == 0) add { Pill("در انتظارِ پاسخ", muted()) }
        if (t.hasPoll) add { Pill("نظرسنجی", MaterialTheme.colorScheme.primary, icon = Icons.Outlined.Poll) }
        if (t.bookmarked) add { Pill("نشان‌شده", MaterialTheme.colorScheme.primary, icon = Icons.Outlined.BookmarkBorder) }
    }
    if (pills.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { pills.forEach { it() } }
}
