package com.hamraheteyf.app.ui.forum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.Forum
import com.hamraheteyf.app.community.TopicCard
import com.hamraheteyf.app.community.TopicList
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Shared plumbing for the forum screens: a paged topic feed, the cached forum tree, an
 * on-resume hook and the collapsing «موضوعِ تازه» button.
 */

/** One paged topic list (page 0..n, infinite scroll, pull-to-refresh, silent refresh on resume). */
@Stable
class TopicFeed(private val scope: CoroutineScope, private val fetch: suspend (Int) -> ApiResult<TopicList>) {
    var items by mutableStateOf<List<TopicCard>>(emptyList()); private set
    var hasMore by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** One-shot message for a failed pull-to-refresh while a list is already shown (see [FeedNotice]). */
    var notice by mutableStateOf<String?>(null); private set
    fun clearNotice() { notice = null }
    /** Index of the last page loaded; part of the footer key so a page of duplicates still advances. */
    var page by mutableStateOf(0); private set
    private var job: Job? = null

    val initialLoading get() = !loaded && error == null

    /**
     * [pull] shows the pull-to-refresh spinner and starts over from page 0. A silent refresh (on
     * resume) re-fetches every page already shown (up to [SILENT_PAGES]) so coming back from a
     * topic doesn't cut the list the user scrolled through down to its first page.
     */
    fun refresh(pull: Boolean = false) {
        job?.cancel()
        if (pull) refreshing = true
        if (!loaded) error = null
        loadingMore = false
        val upTo = if (pull || !loaded) 0 else page.coerceAtMost(SILENT_PAGES - 1)
        job = scope.launch {
            val acc = ArrayList<TopicCard>()
            val seen = HashSet<String>()
            var more = false
            var last = -1
            var fail: String? = null
            for (p in 0..upTo) {
                when (val r = fetch(p)) {
                    is ApiResult.Ok -> {
                        r.data.items.forEach { if (seen.add(it.id)) acc.add(it) }
                        more = r.data.hasMore; last = p
                        if (!more) break
                    }
                    is ApiResult.Fail -> { fail = r.message; break }
                }
            }
            if (last >= 0) { items = acc; hasMore = more; page = last; error = null; loaded = true }
            else if (!loaded) error = fail
            else if (pull) notice = fail ?: "به‌روزرسانی انجام نشد. اتصال اینترنت را بررسی کنید."
            refreshing = false
        }
    }

    private companion object { const val SILENT_PAGES = 4 }

    fun loadMore() {
        if (!loaded || !hasMore || loadingMore || job?.isActive == true) return
        loadingMore = true
        job = scope.launch {
            when (val r = fetch(page + 1)) {
                is ApiResult.Ok -> {
                    val seen = items.mapTo(HashSet()) { it.id }
                    items = items + r.data.items.filter { it.id !in seen }
                    hasMore = r.data.hasMore; page += 1
                }
                is ApiResult.Fail -> Unit  // the footer keeps its «بیشتر» button for a manual retry
            }
            loadingMore = false
        }
    }
}

@Composable
fun rememberTopicFeed(vararg keys: Any?, fetch: suspend (Int) -> ApiResult<TopicList>): TopicFeed {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(fetch)
    val feed = remember(*keys) { TopicFeed(scope) { latest.value(it) } }
    LaunchedEffect(feed) { feed.refresh() }
    return feed
}

/** Runs [action] every time the screen comes back to the foreground (not on the first start). */
@Composable
fun OnResume(action: () -> Unit) {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val latest = rememberUpdatedState(action)
    DisposableEffect(owner) {
        var first = true
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { if (first) first = false else latest.value() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/** The forum tree, fetched once per process and shared by every forum screen. */
object ForumTreeCache {
    var tree by mutableStateOf<List<Forum>?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    suspend fun load(client: CommunityClient, force: Boolean = false) {
        if (tree != null && !force) return
        when (val r = client.forumTree()) {
            is ApiResult.Ok -> { tree = r.data; error = null }
            is ApiResult.Fail -> if (tree == null) error = r.message
        }
    }

    fun find(id: String?): Forum? = id?.let { find(tree.orEmpty(), it) }
    private fun find(list: List<Forum>, id: String): Forum? {
        for (f in list) { if (f.id == id) return f; find(f.children, id)?.let { return it } }
        return null
    }

    /** id → title of every forum (for the label on topic rows). */
    fun titles(): Map<String, String> = buildMap { fun add(l: List<Forum>) { l.forEach { put(it.id, it.title); add(it.children) } }; add(tree.orEmpty()) }

    /** True when the forum or its parent is a clinic forum. */
    fun isClinic(id: String?): Boolean {
        val f = find(id) ?: return id == "clinic"
        return f.isClinic || find(f.parentId)?.isClinic == true
    }
}

@Composable
fun rememberForumTree(client: CommunityClient?): List<Forum>? {
    LaunchedEffect(client) { client?.let { ForumTreeCache.load(it) } }
    return ForumTreeCache.tree
}

/**
 * Loading / error / empty / rows / footer for a [TopicFeed] inside a LazyColumn.
 * Rows are individual cards separated by [Dimens.cardGap].
 */
fun LazyListScope.topicFeedItems(
    feed: TopicFeed,
    forumTitles: Map<String, String>,
    onOpen: (String) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String? = null,
    emptyAction: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    showForum: Boolean = true,
) {
    when {
        feed.initialLoading -> item(key = "loading") { LoadingView() }
        feed.error != null -> item(key = "error") { ErrorView(feed.error.orEmpty(), onRetry = { feed.refresh() }) }
        feed.items.isEmpty() -> item(key = "empty") { EmptyView(emptyIcon, emptyTitle, emptyMessage, emptyAction, onEmptyAction) }
        else -> {
            items(feed.items.size, key = { "t-" + feed.items[it].id }) { i ->
                val t = feed.items[i]
                TopicRow(
                    t, onClick = { onOpen(t.id) },
                    place = if (showForum) t.forumId?.let { forumTitles[it] } ?: if (t.groupId != null) (t.groupTitle?.let { "گروهِ $it" } ?: "گفتگوی گروه") else null else null,
                    modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.cardGap / 2),
                )
            }
            item(key = "more-${feed.items.size}-${feed.page}") { LoadMoreFooter(feed.hasMore, feed.loadingMore) { feed.loadMore() } }
        }
    }
}

/** True while the list is at its very top — the FAB shows its label only there. */
@Composable
fun LazyListState.atTop(): Boolean {
    val top by remember(this) { derivedStateOf { firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset < 40 } }
    return top
}

/** «موضوعِ تازه»-style button that collapses to its icon while the list is scrolled. */
@Composable
fun ComposeFab(text: String, expanded: Boolean, onClick: () -> Unit, icon: ImageVector = Icons.Outlined.Edit) {
    ExtendedFloatingActionButton(
        onClick = onClick, expanded = expanded,
        // Screens without the tab bar don't consume the navigation bar: keep the button above it.
        modifier = Modifier.navigationBarsPadding(),
        // The label is read out while it shows; the icon speaks only when collapsed to it.
        icon = { Icon(icon, contentDescription = if (expanded) null else text) },
        text = { Text(text) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

/** Converts Persian/Arabic digits to ASCII so numeric input parses. */
fun String.asciiDigits(): String = map { c ->
    when (c) { in '۰'..'۹' -> '0' + (c - '۰'); in '٠'..'٩' -> '0' + (c - '٠'); else -> c }
}.joinToString("")

/** Bottom spacer so the last row clears the FAB. */
val FabClearance = 88.dp


/** Shows a short toast when a pull-to-refresh fails on a list that is already on screen. */
@Composable
fun FeedNotice(feed: TopicFeed) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(feed.notice) {
        feed.notice?.let { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show(); feed.clearNotice() }
    }
}
