package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.ForumPost
import com.hamraheteyf.app.community.Poll
import com.hamraheteyf.app.community.QuoteRef
import com.hamraheteyf.app.community.TopicFull
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.*
import kotlin.math.roundToInt

/** The opening post. On later pages it starts collapsed to the title so replies come first. */
@Composable
fun OpeningPostCard(
    topic: TopicFull,
    client: CommunityClient?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLike: () -> Unit,
    onQuote: () -> Unit,
    poll: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier) {
        AuthorLine(topic.author, relativeTimeFa(topic.createdAt), onClick = { onOpenProfile(topic.author.id) })
        Spacer(Modifier.height(Dimens.s))
        Text(topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TopicStatusPills(topic)
        if (collapsed) {
            TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("نمایشِ متنِ موضوع") }
            return@AppCard
        }
        Spacer(Modifier.height(Dimens.s))
        SelectionContainer { Text(topic.body, style = MaterialTheme.typography.bodyLarge) }
        if (topic.images.isNotEmpty()) { Spacer(Modifier.height(Dimens.s)); ImageGrid(topic.images, client) }
        if (topic.poll != null) { Spacer(Modifier.height(Dimens.m)); poll() }
        if (topic.editedAt != null) {
            Spacer(Modifier.height(4.dp))
            Text("ویرایش‌شده · ${relativeTimeFa(topic.editedAt)}", style = MaterialTheme.typography.labelSmall, color = muted())
        }
        Spacer(Modifier.height(Dimens.s))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(topic.liked, topic.likeCount, enabled = !topic.mine, onClick = onLike)
            TextButton(onClick = onQuote) {
                Icon(Icons.Outlined.FormatQuote, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("نقلِ قول")
            }
            Spacer(Modifier.weight(1f))
            Counter(Icons.Outlined.ChatBubbleOutline, topic.replyCount, contentDescription = "پاسخ")
            Spacer(Modifier.width(Dimens.m))
            Counter(Icons.Outlined.Visibility, topic.viewCount, contentDescription = "بازدید")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicStatusPills(t: TopicFull) {
    if (!t.pinned && !t.locked && t.expertReplyCount == 0 && t.status == "PUBLISHED") return
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (t.pinned) Pill("سنجاق‌شده", Gold, icon = Icons.Outlined.PushPin)
        if (t.locked) Pill("بسته", muted(), icon = Icons.Outlined.Lock)
        if (t.expertReplyCount > 0) Pill("پاسخِ متخصص", Expert, ExpertContainer, icon = VerifiedIcon)
        if (t.status == "PENDING") Pill("در انتظارِ بررسی", muted())
        if (t.status == "HIDDEN") Pill("پنهان", Danger)
    }
}

@Composable
fun LikeButton(liked: Boolean, count: Int, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, colors = ButtonDefaults.textButtonColors(
        contentColor = if (liked) Like else muted(), disabledContentColor = muted().copy(alpha = 0.6f))) {
        Icon(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, if (liked) "لغوِ پسند" else "پسندیدن", Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (count > 0) count.toPersianDigits() else "پسند")
    }
}

/** One reply. Expert replies carry a blue stripe on the start edge; the accepted one a green pill. */
@Composable
fun PostCard(
    post: ForumPost,
    number: Int,
    client: CommunityClient?,
    canAccept: Boolean,
    onOpenProfile: (String) -> Unit,
    onLike: () -> Unit,
    onQuote: () -> Unit,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stripe = if (post.expert) Expert else if (post.accepted) VerifiedGreen else null
    AppCard(
        modifier.then(if (stripe != null) Modifier.clip(MaterialTheme.shapes.medium).drawBehind {
            val w = 4.dp.toPx()
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
            drawRect(stripe, Offset(x, 0f), Size(w, size.height))
        } else Modifier),
        border = if (post.accepted) VerifiedGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuthorLine(post.author, relativeTimeFa(post.createdAt), Modifier.weight(1f), onClick = { onOpenProfile(post.author.id) })
            Text("#${number.toPersianDigits()}", style = MaterialTheme.typography.labelSmall, color = muted())
        }
        if (post.expert || post.accepted || post.status != "PUBLISHED") {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (post.accepted) Pill("بهترین پاسخ", VerifiedGreen, icon = Icons.Outlined.CheckCircle)
                if (post.expert) Pill("پاسخِ متخصص", Expert, ExpertContainer, icon = VerifiedIcon)
                if (post.status == "PENDING") Pill("در انتظارِ بررسی", muted())
                if (post.status == "HIDDEN") Pill("پنهان‌شده توسطِ ناظر", Danger)
            }
        }
        post.quote?.let { Spacer(Modifier.height(Dimens.s)); QuoteBlock(it) }
        Spacer(Modifier.height(Dimens.s))
        if (post.body.isNotBlank()) SelectionContainer { Text(post.body, style = MaterialTheme.typography.bodyLarge) }
        else Text("این پاسخ در دسترس نیست.", style = MaterialTheme.typography.bodyMedium, color = muted())
        if (post.images.isNotEmpty()) { Spacer(Modifier.height(Dimens.s)); ImageGrid(post.images, client) }
        if (post.editedAt != null) {
            Spacer(Modifier.height(4.dp))
            Text("ویرایش‌شده", style = MaterialTheme.typography.labelSmall, color = muted())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(post.liked, post.likeCount, enabled = !post.mine, onClick = onLike)
            TextButton(onClick = onQuote) {
                Icon(Icons.Outlined.FormatQuote, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("نقلِ قول")
            }
            if (canAccept && !post.mine && post.status == "PUBLISHED") {
                TextButton(onClick = onAccept, colors = ButtonDefaults.textButtonColors(contentColor = VerifiedGreen)) {
                    Icon(if (post.accepted) Icons.Outlined.RemoveDone else Icons.Outlined.Done, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (post.accepted) "برداشتنِ انتخاب" else "بهترین پاسخ")
                }
            }
            Spacer(Modifier.weight(1f))
            PostMenu(post.canEdit, post.canDelete, !post.mine, onEdit, onDelete, onReport)
        }
    }
}

@Composable
private fun PostMenu(canEdit: Boolean, canDelete: Boolean, canReport: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onReport: () -> Unit) {
    if (!canEdit && !canDelete && !canReport) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "گزینه‌ها", tint = muted()) }
        DropdownMenu(open, { open = false }) {
            if (canEdit) DropdownMenuItem({ Text("ویرایش") }, { open = false; onEdit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
            if (canDelete) DropdownMenuItem({ Text("حذف") }, { open = false; onDelete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
            if (canReport) DropdownMenuItem({ Text("گزارش") }, { open = false; onReport() }, leadingIcon = { Icon(Icons.Outlined.Flag, null) })
        }
    }
}

/** Gray quoted block shown above a reply's body. */
@Composable
fun QuoteBlock(q: QuoteRef, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FormatQuote, null, tint = muted(), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(q.authorName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(q.excerpt, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Poll with result bars; vote controls while it is open and the user hasn't voted (or chose «تغییرِ رأی»). */
@Composable
fun PollCard(poll: Poll, busy: Boolean, onVote: (List<Int>) -> Unit) {
    var changing by rememberSaveable { mutableStateOf(false) }
    val voted = poll.myVotes.isNotEmpty()
    val voting = !poll.closed && (!voted || changing)
    var picked by remember(poll.myVotes) { mutableStateOf(poll.myVotes.toSet()) }
    val base = (if (poll.multi) poll.voters else poll.counts.sum()).coerceAtLeast(1)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Poll, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(poll.question.ifBlank { "نظرسنجی" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            if (poll.multi && voting) Text("چند گزینه را می‌توانید انتخاب کنید.", style = MaterialTheme.typography.labelSmall, color = muted())
            Spacer(Modifier.height(Dimens.s))
            poll.options.forEachIndexed { i, opt ->
                if (voting) {
                    val sel = i in picked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp)).clickable {
                            picked = if (poll.multi) (if (sel) picked - i else picked + i) else setOf(i)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (poll.multi) Checkbox(sel, null) else RadioButton(sel, null)
                        Spacer(Modifier.width(8.dp))
                        Text(opt, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val n = poll.counts.getOrElse(i) { 0 }
                    val frac = n.toFloat() / base
                    val mine = i in poll.myVotes
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mine) { Icon(Icons.Outlined.CheckCircle, "رأیِ شما", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)) }
                            Text(opt, style = MaterialTheme.typography.bodyMedium, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Text("${(frac * 100).roundToInt().toPersianDigits()}٪", style = MaterialTheme.typography.labelMedium, color = muted())
                        }
                        Spacer(Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { frac }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.outlineVariant, drawStopIndicator = {},
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val meta = buildList {
                    add("${poll.voters.toPersianDigits()} شرکت‌کننده")
                    if (poll.closed) add("پایان‌یافته")
                    else poll.closesAt?.let { add("پایان: ${remainingFa(it)}") }
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelSmall, color = muted(), modifier = Modifier.weight(1f))
                if (voting) {
                    if (changing) TextButton(onClick = { changing = false; picked = poll.myVotes.toSet() }) { Text("انصراف") }
                    Button(onClick = { onVote(picked.sorted()); changing = false }, enabled = picked.isNotEmpty() && !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("ثبتِ رأی")
                    }
                } else if (!poll.closed && voted) {
                    TextButton(onClick = { changing = true }) { Text("تغییرِ رأی") }
                }
            }
        }
    }
}

private fun remainingFa(at: Long): String {
    val ms = at - System.currentTimeMillis()
    if (ms <= 0) return "به‌زودی"
    val h = ms / 3_600_000
    return if (h < 24) "${h.toInt().coerceAtLeast(1).toPersianDigits()} ساعتِ دیگر" else "${(h / 24).toInt().toPersianDigits()} روزِ دیگر"
}

/** «صفحهٔ x از y» with previous/next and a jump dialog. Pages are 0-based. */
@Composable
fun PagerBar(page: Int, pages: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (pages <= 1) return
    var jump by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "صفحهٔ قبل") }
        Surface(onClick = { jump = true }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
            Text("صفحهٔ ${(page + 1).toPersianDigits()} از ${pages.toPersianDigits()}", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        IconButton(onClick = { onPage(page + 1) }, enabled = page < pages - 1) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "صفحهٔ بعد") }
    }
    if (jump) {
        var text by rememberSaveable { mutableStateOf("") }
        val n = text.asciiDigits().toIntOrNull()
        val ok = n != null && n in 1..pages
        AlertDialog(
            onDismissRequest = { jump = false },
            title = { Text("رفتن به صفحه") },
            text = {
                OutlinedTextField(text, { text = it.take(4) }, singleLine = true,
                    label = { Text("شمارهٔ صفحه (۱ تا ${pages.toPersianDigits()})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            },
            confirmButton = { TextButton(onClick = { jump = false; n?.let { onPage(it - 1) } }, enabled = ok) { Text("برو") } },
            dismissButton = { TextButton(onClick = { jump = false }) { Text("انصراف") } },
        )
    }
}

/** Simple yes/no confirmation. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() },
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
