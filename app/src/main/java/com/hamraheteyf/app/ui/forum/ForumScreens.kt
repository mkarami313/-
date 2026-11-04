package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.TopicSort
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import kotlinx.coroutines.launch

private enum class HubFeed(val label: String) {
    FORUMS("تالارها"), HOT("داغ"), NEW("تازه‌ها"), FOLLOWED("دنبال‌شده"), BOOKMARKED("نشان‌شده"), MINE("گفتگوهای من")
}

/** «انجمن» tab: forum tree + topic feeds (داغ/جدید/دنبال‌شده/نشان‌شده/من) + entry to clinic and groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumHubScreen(
    onOpenForum: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: (String?) -> Unit,
    onOpenClinic: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val client = rememberCommunity()
    val scope = rememberCoroutineScope()
    var feedName by rememberSaveable { mutableStateOf(HubFeed.FORUMS.name) }
    val feedKind = HubFeed.valueOf(feedName)
    val tree = rememberForumTree(client)
    val titles = remember(tree) { ForumTreeCache.titles() }
    val listState = rememberLazyListState()
    var treeRefreshing by remember { mutableStateOf(false) }

    val feed = rememberTopicFeed(client, feedKind) { page ->
        val c = client ?: return@rememberTopicFeed ApiResult.Fail("اتصال به سرور برقرار نیست.")
        when (feedKind) {
            HubFeed.FORUMS -> ApiResult.Ok(com.hamraheteyf.app.community.TopicList())
            HubFeed.NEW -> c.topics(sort = TopicSort.NEW, page = page)
            HubFeed.HOT -> c.topics(sort = TopicSort.HOT, page = page)
            HubFeed.FOLLOWED -> c.topics(followed = true, page = page)
            HubFeed.BOOKMARKED -> c.topics(bookmarked = true, page = page)
            HubFeed.MINE -> c.topics(mine = true, page = page)
        }
    }
    OnResume { if (feedKind != HubFeed.FORUMS) feed.refresh() else scope.launch { client?.let { ForumTreeCache.load(it, force = true) } } }

    Scaffold(
        topBar = {
            AppTopBar("انجمن", actions = {
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "جست‌وجو") }
            })
        },
        floatingActionButton = { ComposeFab("موضوعِ تازه", listState.atTop(), onClick = { onNewTopic(null) }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ChoiceRow(HubFeed.entries, feedKind, { it.label }, {
                feedName = it.name
                scope.launch { listState.scrollToItem(0) }
            }, Modifier.padding(vertical = Dimens.xs))
            PullToRefreshBox(
                isRefreshing = if (feedKind == HubFeed.FORUMS) treeRefreshing else feed.refreshing,
                onRefresh = {
                    if (feedKind == HubFeed.FORUMS) scope.launch {
                        treeRefreshing = true; client?.let { ForumTreeCache.load(it, force = true) }; treeRefreshing = false
                    } else feed.refresh(pull = true)
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = Dimens.xs, bottom = FabClearance)) {
                    if (feedKind == HubFeed.FORUMS) {
                        forumDirectory(tree, onOpenClinic, onOpenGroups, onOpenForum) {
                            scope.launch { client?.let { ForumTreeCache.load(it, force = true) } }
                        }
                    } else {
                        val (icon, title, msg) = when (feedKind) {
                            HubFeed.FOLLOWED -> Triple(Icons.Outlined.NotificationsNone, "هنوز گفتگویی را دنبال نمی‌کنید",
                                "گفتگوهایی که دنبال کنید یا در آن‌ها بنویسید این‌جا می‌آیند و پاسخِ تازه‌شان را خبر می‌دهیم.")
                            HubFeed.BOOKMARKED -> Triple(Icons.Outlined.BookmarkBorder, "نشانی ندارید",
                                "از منوی هر گفتگو «نشان کردن» را بزنید تا بعداً راحت پیدایش کنید.")
                            HubFeed.MINE -> Triple(Icons.Outlined.EditNote, "هنوز موضوعی نساخته‌اید",
                                "پرسش یا تجربه‌تان را با خانواده‌های دیگر در میان بگذارید.")
                            else -> Triple(Icons.Outlined.Forum, "گفتگویی پیدا نشد", "اولین نفری باشید که موضوعی را آغاز می‌کند.")
                        }
                        topicFeedItems(feed, titles, onOpenTopic, icon, title, msg, "موضوعِ تازه", { onNewTopic(null) })
                    }
                }
            }
        }
    }
}

/** Clinic + groups entry cards and the forum tree. */
private fun LazyListScope.forumDirectory(
    tree: List<com.hamraheteyf.app.community.Forum>?,
    onOpenClinic: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenForum: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val pad = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.cardGap / 2)
    item(key = "clinic") { ClinicEntryCard(tree?.firstOrNull { it.isClinic }, onOpenClinic, pad) }
    item(key = "groups") { GroupsEntryCard(onOpenGroups, pad) }
    item(key = "tree-title") { SectionTitle("تالارها", Modifier.padding(horizontal = Dimens.gutter)) }
    when {
        tree == null && ForumTreeCache.error != null -> item(key = "tree-error") { ErrorView(ForumTreeCache.error.orEmpty(), onRetry) }
        tree == null -> item(key = "tree-loading") { LoadingView() }
        else -> {
            val forums = tree.filterNot { it.isClinic }
            if (forums.isEmpty()) item(key = "tree-empty") { EmptyView(Icons.Outlined.Forum, "هنوز تالاری ساخته نشده است") }
            items(forums.size, key = { "f-" + forums[it].id }) { i -> ForumTreeCard(forums[i], onOpenForum, pad) }
        }
    }
}

/** Forum-topics screen (one forum and its sub-forums). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumTopicsScreen(
    forumId: String,
    onBack: () -> Unit,
    onOpenForum: (String) -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: (String) -> Unit,
) {
    val client = rememberCommunity()
    val tree = rememberForumTree(client)
    val forum = remember(tree, forumId) { ForumTreeCache.find(forumId) }
    val titles = remember(tree) { ForumTreeCache.titles() }
    val clinic = remember(tree, forumId) { ForumTreeCache.isClinic(forumId) }
    var sortName by rememberSaveable { mutableStateOf(TopicSort.ACTIVE.name) }
    val sort = TopicSort.valueOf(sortName)
    val listState = rememberLazyListState()
    val feed = rememberTopicFeed(client, forumId, sort) { page ->
        client?.topics(forumId = forumId, sort = sort, page = page) ?: ApiResult.Fail("اتصال به سرور برقرار نیست.")
    }
    OnResume { feed.refresh() }
    val label = if (clinic) "پرسش از متخصص" else "موضوعِ تازه"

    Scaffold(
        topBar = { AppTopBar(forum?.title ?: "تالار", onBack = onBack, subtitle = forum?.description?.takeIf { it.isNotBlank() }) },
        floatingActionButton = { ComposeFab(label, listState.atTop(), onClick = { onNewTopic(forumId) }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        PullToRefreshBox(feed.refreshing, { feed.refresh(pull = true) }, Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = FabClearance)) {
                val kids = forum?.children.orEmpty()
                if (kids.isNotEmpty()) item(key = "kids") {
                    SubForumChips(kids, null, onSelect = { it?.let(onOpenForum) }, Modifier.padding(top = Dimens.s))
                }
                item(key = "sort") {
                    ChoiceRow(TopicSort.entries, sort, { it.label }, { sortName = it.name }, Modifier.padding(vertical = Dimens.s))
                }
                topicFeedItems(
                    feed, titles, onOpenTopic,
                    if (clinic) Icons.Outlined.MedicalServices else Icons.Outlined.Forum,
                    if (sort == TopicSort.UNANSWERED) "همهٔ موضوع‌ها پاسخ گرفته‌اند" else "هنوز موضوعی این‌جا نیست",
                    "اولین موضوع را شما آغاز کنید.", label, { onNewTopic(forumId) },
                    showForum = kids.isNotEmpty(),
                )
            }
        }
    }
}

/** «کلینیک»: questions to verified professionals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicScreen(onBack: () -> Unit, onOpenTopic: (String) -> Unit, onAsk: (String?) -> Unit) {
    val client = rememberCommunity()
    val tree = rememberForumTree(client)
    val clinic = remember(tree) { tree?.firstOrNull { it.isClinic } }
    val titles = remember(tree) { ForumTreeCache.titles() }
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var sortName by rememberSaveable { mutableStateOf(TopicSort.ACTIVE.name) }
    val sort = TopicSort.valueOf(sortName)
    val listState = rememberLazyListState()
    val feed = rememberTopicFeed(client, sub, sort) { page ->
        val c = client ?: return@rememberTopicFeed ApiResult.Fail("اتصال به سرور برقرار نیست.")
        if (sub != null) c.topics(forumId = sub, sort = sort, page = page) else c.topics(clinic = true, sort = sort, page = page)
    }
    OnResume { feed.refresh() }

    Scaffold(
        topBar = { AppTopBar("کلینیک", onBack = onBack, subtitle = "پرسش از متخصص") },
        floatingActionButton = {
            ComposeFab("پرسش از متخصص", listState.atTop(), onClick = { onAsk(sub ?: clinic?.id) }, icon = Icons.Outlined.QuestionAnswer)
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        PullToRefreshBox(feed.refreshing, { feed.refresh(pull = true) }, Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = FabClearance)) {
                item(key = "intro") {
                    AppCard(Modifier.padding(Dimens.gutter), container = com.hamraheteyf.app.ui.theme.ExpertContainer.copy(alpha = 0.5f)) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(VerifiedIcon, null, tint = com.hamraheteyf.app.ui.theme.Expert, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(Dimens.s))
                            Text("به پرسش‌های کلینیک فقط متخصصانِ تأییدشده پاسخ می‌دهند؛ جایگزینِ ویزیتِ حضوری نیست.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val kids = clinic?.children.orEmpty()
                if (kids.isNotEmpty()) item(key = "kids") { SubForumChips(kids, sub, onSelect = { sub = it }, allLabel = "همهٔ تخصص‌ها") }
                item(key = "sort") {
                    ChoiceRow(TopicSort.entries, sort, { it.label }, { sortName = it.name }, Modifier.padding(vertical = Dimens.s))
                }
                topicFeedItems(
                    feed, titles, onOpenTopic, Icons.Outlined.MedicalServices, "هنوز پرسشی این‌جا نیست",
                    "پرسشِ خود را بنویسید تا متخصصان پاسخ دهند.", "پرسش از متخصص", { onAsk(sub ?: clinic?.id) },
                    showForum = sub == null,
                )
            }
        }
    }
}
