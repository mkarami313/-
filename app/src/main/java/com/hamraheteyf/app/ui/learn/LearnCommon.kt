@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.learn

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.LearnCategory
import com.hamraheteyf.app.community.LearnItem
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.Jalali
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.Pill
import com.hamraheteyf.app.ui.kit.iconFor
import com.hamraheteyf.app.ui.kit.muted
import com.hamraheteyf.app.ui.theme.Danger
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Gold
import com.hamraheteyf.app.ui.theme.GoldInk
import com.hamraheteyf.app.ui.theme.VerifiedGreen
import com.hamraheteyf.app.ui.theme.hexColor
import java.util.Calendar

/* Shared pieces of the learning library («آموزش‌ها»). */

/** True when the signed-in user is an admin (from the local user snapshot). */
@Composable
internal fun rememberIsAdmin(): Boolean {
    val c = LocalAppContainer.current
    val me by remember { c.userRepository.observeCurrentUser() }.collectAsState(null)
    return me?.isAdmin == true
}

/** «۱۲:۰۵» / «۱:۰۲:۰۳» from seconds. */
internal fun durationFa(sec: Int?): String? {
    if (sec == null || sec <= 0) return null
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    val t = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    return t.toPersianDigits()
}

/** Jalali date «۱۴۰۵/۰۸/۱۳» from epoch millis. */
internal fun dateFa(ms: Long): String {
    if (ms <= 0) return ""
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return Jalali.format(Jalali.toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)))
}

/** Recent → relative («۳ روز پیش»), older → Jalali date. */
internal fun whenFa(ms: Long): String =
    if (ms <= 0) "" else if (System.currentTimeMillis() - ms < 30L * 86_400_000) relativeTimeFa(ms) else dateFa(ms)

internal fun LearnItem.shownAt(): Long = publishedAt ?: createdAt

/** The aparat video hash from a /v/<hash> or embed URL, else null. */
internal fun aparatHash(url: String?): String? = url?.let {
    Regex("aparat\\.com/(?:v|video/video/embed/videohash)/([A-Za-z0-9]+)").find(it)?.groupValues?.get(1)
}

internal fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

internal fun LearnItem.kindLabel() = if (isVideo) "ویدیو" else "مقاله"

/** Status pill for my submissions / the review queue. */
@Composable
internal fun StatusPill(status: String) {
    when (status) {
        "PENDING" -> Pill("در انتظارِ بررسی", GoldInk, container = Gold.copy(alpha = 0.16f), icon = Icons.Outlined.HourglassEmpty)
        "PUBLISHED" -> Pill("منتشر شد", VerifiedGreen, icon = Icons.Outlined.CheckCircle)
        "REJECTED" -> Pill("رد شد", Danger, icon = Icons.Outlined.HighlightOff)
        "HIDDEN" -> Pill("پنهان", muted(), icon = Icons.Outlined.VisibilityOff)
        else -> Pill(status, muted())
    }
}

/** Cover image (or a tinted placeholder with the category icon); videos get a play badge + duration. */
@Composable
internal fun LearnCover(
    client: CommunityClient?, item: LearnItem, category: LearnCategory?, modifier: Modifier,
    badgeSize: Dp = 40.dp, showDuration: Boolean = true,
) {
    val color = hexColor(category?.color)
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        val cover = item.coverId
        if (cover != null && client != null) RemoteImage(client.fileUrl(cover), Modifier.fillMaxSize())
        else Icon(if (item.isVideo) Icons.Outlined.OndemandVideo else (category?.let { iconFor(it.icon) } ?: Icons.Outlined.Article),
            null, tint = color, modifier = Modifier.size(badgeSize))
        if (item.isVideo) {
            if (cover != null) Box(Modifier.size(badgeSize).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "ویدیو", tint = Color.White, modifier = Modifier.size(badgeSize * 0.65f))
            }
            val d = durationFa(item.durationSec)
            if (showDuration && d != null) Text(d, color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
    }
}

/** Compact list row: 72dp thumb, 2-line title, 1-line summary, category + date. */
@Composable
internal fun LearnRow(
    client: CommunityClient?, item: LearnItem, category: LearnCategory?, onClick: () -> Unit,
    showStatus: Boolean = false, trailing: @Composable (() -> Unit)? = null,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Dimens.gutter, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            LearnCover(client, item, category, Modifier.size(72.dp), badgeSize = 28.dp, showDuration = false)
            Spacer(Modifier.width(Dimens.m))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showStatus) StatusPill(item.status)
                    if (item.isVideo) Pill("ویدیو", MaterialTheme.colorScheme.primary, icon = Icons.Outlined.OndemandVideo)
                    val meta = listOfNotNull(category?.title, whenFa(item.shownAt()).ifBlank { null }).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (showStatus && item.status == "REJECTED" && !item.reviewNote.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("دلیل: ${item.reviewNote}", style = MaterialTheme.typography.labelMedium, color = Danger, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("برای ویرایش و ارسالِ دوباره، مطلب را باز کنید.", style = MaterialTheme.typography.labelSmall, color = muted())
                }
            }
            if (trailing != null) trailing()
        }
    }
}

/** Categories, loaded once per screen (empty on failure — they are decoration, not content). */
@Composable
internal fun rememberLearnCategories(client: CommunityClient?): State<List<LearnCategory>> =
    produceState(emptyList(), client) {
        if (client != null) (client.learnCategories() as? ApiResult.Ok)?.let { value = it.data }
    }
