package com.hamraheteyf.app.ui.forum

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.ForumPost
import com.hamraheteyf.app.community.QuoteRef
import com.hamraheteyf.app.community.TopicPage
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.ReportDialog
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import kotlinx.coroutines.launch

/** Replies per page — mirrors the server's PAGE_POSTS (only used to number replies). */
private const val PAGE_POSTS = 30
/** Server POST_BODY_MAX. */
private const val REPLY_MAX = 10_000

private val QuoteSaver = listSaver<QuoteRef?, String>(
    save = { q -> if (q == null) emptyList() else listOf(q.id, q.authorName, q.excerpt) },
    restore = { l -> if (l.size == 3) QuoteRef(l[0], l[1], l[2]) else null },
)

private enum class Scroll { NONE, TOP, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    topicId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenForum: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val client = rememberCommunity()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var data by remember { mutableStateOf<TopicPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(-1) }   // -1 = last page
    var scrollTo by remember { mutableStateOf(Scroll.NONE) }
    var scrollTick by remember { mutableIntStateOf(0) }
    var openingExpanded by rememberSaveable { mutableStateOf(false) }

    var text by rememberSaveable(topicId) { mutableStateOf("") }
    // Saved with the draft text: a rotation must not turn «پاسخ به …» into a plain reply.
    var quote by rememberSaveable(topicId, stateSaver = QuoteSaver) { mutableStateOf<QuoteRef?>(null) }
    var sending by remember { mutableStateOf(false) }
    var voting by remember { mutableStateOf(false) }
    val uploads = rememberImageUploads(client)

    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editPost by remember { mutableStateOf<ForumPost?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    // A half-written reply (or attached images) is not thrown away by an accidental back.
    val hasDraft = text.isNotBlank() || uploads.items.isNotEmpty()
    fun leave() { if (hasDraft && !sending) confirmLeave = true else onBack() }
    BackHandler(enabled = hasDraft && !sending) { confirmLeave = true }

    fun toast(msg: String) { scope.launch { snack.showSnackbar(msg) } }

    // One load at a time: a resume-refresh finishing after a page jump must not overwrite it.
    var loadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Toggles (like/bookmark/follow) already on their way; a second tap would un-toggle on the server.
    val inFlight = remember { mutableSetOf<String>() }
    var savingEdit by remember { mutableStateOf(false) }

    fun load(p: Int?, scroll: Scroll = Scroll.NONE) {
        val c = client ?: run { error = "اتصال به سرور برقرار نیست."; return }
        loading = true
        loadJob?.cancel()
        loadJob = scope.launch {
            when (val r = c.topic(topicId, p)) {
                is ApiResult.Ok -> {
                    data = r.data; error = null; page = r.data.page
                    if (scroll != Scroll.NONE) { scrollTo = scroll; scrollTick++ }
                }
                is ApiResult.Fail -> if (data == null) error = r.message else toast(r.message)
            }
            loading = false
        }
    }

    LaunchedEffect(topicId) { if (data == null) load(page.takeIf { it >= 0 }) }
    OnResume { if (data != null) load(page) }
    LaunchedEffect(scrollTick) {
        when (scrollTo) {
            Scroll.TOP -> listState.scrollToItem(0)
            Scroll.END -> data?.let { d ->
                // pager-top? + opening + replies title + (posts or «no replies») + pager-bottom?
                val count = 2 + d.posts.size.coerceAtLeast(1) + if (d.pages > 1) 2 else 0
                listState.animateScrollToItem(count - 1)
            }
            Scroll.NONE -> Unit
        }
    }

    fun <T> act(call: suspend (CommunityClient) -> ApiResult<T>, ok: (T) -> Unit) {
        val c = client ?: return
        scope.launch { when (val r = call(c)) { is ApiResult.Ok -> ok(r.data); is ApiResult.Fail -> toast(r.message) } }
    }

    /** [act] for a server-side toggle: ignored while the same toggle is still in flight. */
    fun <T> toggle(key: String, call: suspend (CommunityClient) -> ApiResult<T>, ok: (T) -> Unit) {
        val c = client ?: return
        if (!inFlight.add(key)) return
        scope.launch {
            try { when (val r = call(c)) { is ApiResult.Ok -> ok(r.data); is ApiResult.Fail -> toast(r.message) } }
            finally { inFlight.remove(key) }
        }
    }

    val d = data
    val t = d?.topic
    val clinic = d?.forum?.kind == "clinic"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val place = d?.group?.title ?: d?.forum?.title
                    Column(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = d?.group != null || d?.forum != null) {
                            d?.group?.let { onOpenGroup(it.id) } ?: d?.forum?.let { onOpenForum(it.id) }
                        },
                    ) {
                        Text(if (clinic) "پرسش از متخصص" else if (d?.group != null) "گفتگوی گروه" else "گفتگو",
                            style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        if (place != null) Text(place, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت") } },
                actions = {
                    if (t != null) TopicMenu(
                        t, onFollow = { toggle("follow", { it.follow(t.id) }) { f -> data = data?.let { it.copy(topic = it.topic.copy(followed = f)) }; toast(if (f) "پاسخ‌های تازه را خبر می‌دهیم." else "دنبال کردن لغو شد.") } },
                        onBookmark = { toggle("bookmark", { it.bookmark(t.id) }) { b -> data = data?.let { it.copy(topic = it.topic.copy(bookmarked = b)) }; toast(if (b) "نشان شد." else "نشان برداشته شد.") } },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, t.title).putExtra(Intent.EXTRA_TEXT, "${t.title}\n\n${t.body}\n\n— همراهِ طیف")
                            ctx.startActivity(Intent.createChooser(send, "اشتراک‌گذاری"))
                        },
                        onReport = { reportTarget = "TOPIC" to t.id },
                        onEdit = { onEdit(t.id) },
                        onDelete = { deleteId = t.id },
                        onModerate = { op -> act({ it.moderate(t.id, op) }) { load(page) } },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (d != null) ReplyComposer(
                canReply = d.canReply, hint = d.replyHint, text = text, onText = { text = it.take(REPLY_MAX) }, quote = quote, onClearQuote = { quote = null },
                uploads = uploads, client = client, sending = sending,
                onSend = {
                    val c = client ?: return@ReplyComposer
                    if (uploads.hasFailed) { toast("روی تصویرِ ناموفق بزنید تا دوباره فرستاده شود، یا حذفش کنید."); return@ReplyComposer }
                    sending = true
                    scope.launch {
                        when (val r = c.reply(topicId, text.trim(), quote?.id, uploads.ids)) {
                            is ApiResult.Ok -> { text = ""; quote = null; uploads.clear(); load((r.data - 1).coerceAtLeast(0), Scroll.END) }
                            is ApiResult.Fail -> snack.showSnackbar(r.message)  // text stays for another try
                        }
                        sending = false
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            d == null && error != null -> ErrorView(error.orEmpty(), { error = null; load(page.takeIf { it >= 0 }) }, Modifier.padding(padding))
            d == null || t == null -> LoadingView(Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(), state = listState,
                contentPadding = PaddingValues(vertical = Dimens.s),
            ) {
                val goPage: (Int) -> Unit = { p -> load(p, Scroll.TOP) }
                if (d.pages > 1) item(key = "pager-top") { PagerBar(d.page, d.pages, goPage) }
                item(key = "opening") {
                    OpeningPostCard(
                        t, client, collapsed = d.page > 0 && !openingExpanded, onToggle = { openingExpanded = true }, canQuote = d.canReply,
                        onOpenProfile = onOpenProfile,
                        onLike = { toggle("like:" + t.id, { it.like(t.id) }) { (liked, n) -> data = data?.let { it.copy(topic = it.topic.copy(liked = liked, likeCount = n)) } } },
                        onQuote = { quote = QuoteRef(t.id, t.author.name, t.body.take(160)) },
                        poll = {
                            t.poll?.let { poll ->
                                PollCard(poll, voting, locked = t.locked || t.status != "PUBLISHED") { opts ->
                                    voting = true
                                    scope.launch {
                                        when (val r = client?.vote(t.id, opts)) {
                                            is ApiResult.Ok -> data = data?.let { it.copy(topic = it.topic.copy(poll = r.data)) }
                                            is ApiResult.Fail -> toast(r.message)
                                            null -> Unit
                                        }
                                        voting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = Dimens.gutter),
                    )
                }
                item(key = "replies-title") {
                    SectionTitle(
                        if (d.total > 0) "پاسخ‌ها (${d.total.toPersianDigits()})" else "پاسخ‌ها",
                        Modifier.padding(horizontal = Dimens.gutter),
                    )
                }
                if (d.posts.isEmpty()) item(key = "no-replies") {
                    Text(
                        if (d.canReply) "هنوز پاسخی نیامده؛ اولین نفری باشید که پاسخ می‌دهد." else "هنوز پاسخی نیامده است.",
                        style = MaterialTheme.typography.bodyMedium, color = muted(),
                        modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.s),
                    )
                }
                items(d.posts.size, key = { "p-" + d.posts[it].id }) { i ->
                    val p = d.posts[i]
                    PostCard(
                        p, d.page * PAGE_POSTS + i + 1, client, t.canAccept, canQuote = d.canReply,
                        onOpenProfile = onOpenProfile,
                        onLike = {
                            toggle("like:" + p.id, { it.like(p.id) }) { (liked, n) ->
                                data = data?.let { cur -> cur.copy(posts = cur.posts.map { if (it.id == p.id) it.copy(liked = liked, likeCount = n) else it }) }
                            }
                        },
                        onQuote = { quote = QuoteRef(p.id, p.author.name, p.body.take(160)) },
                        onAccept = { toggle("accept", { it.moderate(t.id, "accept", postId = p.id) }) { load(d.page) } },
                        onEdit = { editPost = p },
                        onDelete = { deleteId = p.id },
                        onReport = { reportTarget = "POST" to p.id },
                        modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.cardGap / 2),
                    )
                }
                if (d.pages > 1) item(key = "pager-bottom") { PagerBar(d.page, d.pages, goPage, Modifier.padding(top = Dimens.s)) }
            }
        }
    }

    reportTarget?.let { (type, id) ->
        ReportDialog(onDismiss = { reportTarget = null }, onSubmit = { reason, note ->
            reportTarget = null
            act({ it.report(type, id, reason.name, note) }) { toast("گزارشِ شما ثبت شد؛ ناظران بررسی می‌کنند.") }
        })
    }
    deleteId?.let { id ->
        val isTopic = id == topicId
        ConfirmDialog(
            if (isTopic) "حذفِ گفتگو" else "حذفِ پاسخ",
            if (isTopic) "این گفتگو و همهٔ پاسخ‌هایش از دیدِ دیگران پنهان می‌شود. مطمئن هستید؟" else "این پاسخ حذف می‌شود. مطمئن هستید؟",
            "حذف",
            onConfirm = { act({ it.delete(id) }) { if (isTopic) onBack() else load(page) } },
            onDismiss = { deleteId = null },
        )
    }
    if (confirmLeave) ConfirmDialog(
        "پاسخ ارسال نشده", "اگر خارج شوید، پاسخی که نوشته‌اید از بین می‌رود.", "خروج",
        onConfirm = onBack, onDismiss = { confirmLeave = false },
    )
    editPost?.let { p -> EditPostDialog(p, saving = savingEdit, onDismiss = { editPost = null }) { body ->
        val c = client ?: return@EditPostDialog
        savingEdit = true
        scope.launch {
            when (val r = c.edit(p.id, body)) {
                is ApiResult.Ok -> { editPost = null; load(page) }
                is ApiResult.Fail -> toast(r.message)  // the dialog stays open with the text
            }
            savingEdit = false
        }
    } }
}

@Composable
private fun TopicMenu(
    t: com.hamraheteyf.app.community.TopicFull,
    onFollow: () -> Unit, onBookmark: () -> Unit, onShare: () -> Unit, onReport: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onModerate: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = onBookmark) {
        Icon(if (t.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, if (t.bookmarked) "برداشتنِ نشان" else "نشان کردن")
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "گزینه‌ها") }
        DropdownMenu(open, { open = false }) {
            fun go(f: () -> Unit) = { open = false; f() }
            DropdownMenuItem({ Text(if (t.followed) "لغوِ دنبال کردن" else "دنبال کردن") }, go(onFollow),
                leadingIcon = { Icon(if (t.followed) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive, null) })
            DropdownMenuItem({ Text(if (t.bookmarked) "برداشتنِ نشان" else "نشان کردن") }, go(onBookmark),
                leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) })
            DropdownMenuItem({ Text("اشتراک‌گذاریِ متن") }, go(onShare), leadingIcon = { Icon(Icons.Outlined.Share, null) })
            if (t.canEdit) DropdownMenuItem({ Text("ویرایش") }, go(onEdit), leadingIcon = { Icon(Icons.Outlined.Edit, null) })
            if (t.canModerate) {
                DropdownMenuItem({ Text(if (t.pinned) "برداشتنِ سنجاق" else "سنجاق کردن") }, go { onModerate(if (t.pinned) "unpin" else "pin") },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) })
                DropdownMenuItem({ Text(if (t.locked) "بازکردنِ گفتگو" else "بستنِ گفتگو") }, go { onModerate(if (t.locked) "unlock" else "lock") },
                    leadingIcon = { Icon(if (t.locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) })
            }
            if (t.canDelete) DropdownMenuItem({ Text("حذف", color = MaterialTheme.colorScheme.error) }, go(onDelete),
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) })
            if (!t.mine) DropdownMenuItem({ Text("گزارش") }, go(onReport), leadingIcon = { Icon(Icons.Outlined.Flag, null) })
        }
    }
}

@Composable
private fun EditPostDialog(post: ForumPost, saving: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var body by rememberSaveable(post.id) { mutableStateOf(post.body) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایشِ پاسخ") },
        text = { OutlinedTextField(body, { body = it.take(10_000) }, Modifier.fillMaxWidth().heightIn(min = 120.dp), maxLines = 10, enabled = !saving) },
        confirmButton = {
            TextButton(onClick = { onSave(body.trim()) }, enabled = !saving && body.isNotBlank() && body.trim() != post.body) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("ذخیره")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
