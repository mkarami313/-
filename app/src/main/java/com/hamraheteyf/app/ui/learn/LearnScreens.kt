@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hamraheteyf.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.LearnCategory
import com.hamraheteyf.app.community.LearnItem
import com.hamraheteyf.app.community.LearnList
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Gold
import com.hamraheteyf.app.ui.theme.GoldInk
import com.hamraheteyf.app.ui.theme.hexColor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OFFLINE = "این بخش در حالتِ آفلاین در دسترس نیست."

// =====================================================================================  hub

private class HubData(
    val categories: List<LearnCategory>,
    val featured: List<LearnItem>,
    val videos: List<LearnItem>,
    val articles: List<LearnItem>,
    val pending: Int,
    val pendingMore: Boolean,
)

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

/** «آموزش‌ها» tab: categories, featured, latest videos & articles, «ارسالِ مطلب», admin queue entry. */
@Composable
fun LearnHubScreen(
    onOpenItem: (String) -> Unit,
    onOpenList: (categoryId: String?, mode: String) -> Unit,
    onSubmit: () -> Unit,
    onReview: () -> Unit,
) {
    val client = rememberCommunity()
    val admin = rememberIsAdmin()
    var tick by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 همه، 1 ویدیوها، 2 مقاله‌ها
    // Back from reviewing / submitting: the pending-review count and the latest lists were stale until a restart.
    RefreshOnResume { tick++ }
    val state by produceState<Load<HubData>>(Load.Loading, client, tick, admin) {
        if (client == null) { value = Load.Err(OFFLINE); return@produceState }
        if (value !is Load.Ok) value = Load.Loading
        value = coroutineScope {
            val cats = async { client.learnCategories() }
            val all = async { client.learnList() }
            val vids = async { client.learnList(kind = "video") }
            val arts = async { client.learnList(kind = "article") }
            val rev = if (admin) async { client.learnList(review = true) } else null
            val a = all.await(); val v = vids.await(); val r = arts.await()
            if (a is ApiResult.Fail && v is ApiResult.Fail && r is ApiResult.Fail) Load.Err(a.message)
            else {
                val allItems = (a as? ApiResult.Ok)?.data?.items.orEmpty()
                val featured = allItems.filter { it.featured }.ifEmpty { allItems }.take(6)
                val rv = (rev?.await() as? ApiResult.Ok<LearnList>)?.data
                Load.Ok(HubData(
                    (cats.await() as? ApiResult.Ok)?.data.orEmpty(), featured,
                    (v as? ApiResult.Ok)?.data?.items.orEmpty().take(10),
                    (r as? ApiResult.Ok)?.data?.items.orEmpty().take(8),
                    rv?.items?.size ?: 0, rv?.hasMore == true,
                ))
            }
        }
    }
    Scaffold(
        topBar = {
            AppTopBar("آموزش‌ها", actions = {
                IconButton(onClick = { onOpenList(null, "") }) { Icon(Icons.Outlined.Search, "جست‌وجو در آموزش‌ها") }
                IconButton(onClick = { onOpenList(null, "saved") }) { Icon(Icons.Outlined.BookmarkBorder, "ذخیره‌شده‌ها") }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingView(Modifier.padding(pad))
            is Load.Err -> ErrorView(s.message, { tick++ }, Modifier.padding(pad))
            is Load.Ok -> HubContent(client, s.data, admin, filter, { filter = it }, onOpenItem, onOpenList, onSubmit, onReview, Modifier.padding(pad))
        }
    }
}

@Composable
private fun HubContent(
    client: CommunityClient?, d: HubData, admin: Boolean, filter: Int, onFilter: (Int) -> Unit,
    onOpenItem: (String) -> Unit, onOpenList: (String?, String) -> Unit, onSubmit: () -> Unit, onReview: () -> Unit,
    modifier: Modifier,
) {
    val catById = remember(d.categories) { d.categories.associateBy { it.id } }
    val empty = d.featured.isEmpty() && d.videos.isEmpty() && d.articles.isEmpty()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Dimens.xl)) {
        if (admin && d.pending > 0) item("pending") {
            Surface(onClick = onReview, color = Gold.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(Dimens.gutter, Dimens.s, Dimens.gutter, 0.dp)) {
                Row(Modifier.padding(horizontal = Dimens.m, vertical = 10.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PendingActions, null, tint = Gold)
                    Spacer(Modifier.width(Dimens.s))
                    val n = d.pending.toPersianDigits() + if (d.pendingMore) "+" else ""
                    Text("$n مطلب در انتظارِ بررسی", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("بررسی", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (d.featured.isNotEmpty()) item("featured") { FeaturedPager(client, d.featured, catById, onOpenItem) }
        item("filter") {
            Spacer(Modifier.height(Dimens.s))
            ChoiceRow(listOf(0, 1, 2), filter, { listOf("همه", "ویدیوها", "مقاله‌ها")[it] }, onFilter)
        }
        if (d.categories.isNotEmpty()) item("cats") {
            SectionTitle("دسته‌بندی‌ها", Modifier.padding(horizontal = Dimens.gutter))
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().height(116.dp),
                contentPadding = PaddingValues(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.spacedBy(Dimens.s),
                verticalArrangement = Arrangement.spacedBy(Dimens.s),
            ) {
                items(d.categories, key = { it.id }) { c ->
                    val mode = when (filter) { 1 -> "video"; 2 -> "article"; else -> "" }
                    CategoryChipCard(c) { onOpenList(c.id, mode) }
                }
            }
        }
        if (filter != 2 && d.videos.isNotEmpty()) item("videos") {
            SectionTitle("تازه‌ترین ویدیوها", Modifier.padding(horizontal = Dimens.gutter), "همه") { onOpenList(null, "video") }
            LazyRow(contentPadding = PaddingValues(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(d.videos, key = { it.id }) { v -> VideoCard(client, v, catById[v.categoryId]) { onOpenItem(v.id) } }
            }
        }
        if (filter != 1 && d.articles.isNotEmpty()) {
            item("artsTitle") {
                SectionTitle("تازه‌ترین مقاله‌ها", Modifier.padding(horizontal = Dimens.gutter), "همه") { onOpenList(null, "article") }
            }
            items(d.articles, key = { "a" + it.id }) { a ->
                LearnRow(client, a, catById[a.categoryId], { onOpenItem(a.id) })
                InsetDivider(start = Dimens.gutter + 72.dp + Dimens.m)
            }
        }
        if (empty) item("empty") {
            EmptyView(Icons.Outlined.School, "هنوز مطلبی منتشر نشده است", "اولین کسی باشید که یک مقاله یا ویدیوی مفید می‌فرستد.")
        }
        item("submit") {
            Spacer(Modifier.height(Dimens.l))
            AppCard(Modifier.padding(horizontal = Dimens.gutter), container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.UploadFile, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Dimens.m))
                    Column(Modifier.weight(1f)) {
                        Text("مطلب یا ویدیو دارید؟ برای ما بفرستید", style = MaterialTheme.typography.titleSmall)
                        Text("پس از بررسی در بخشِ آموزش‌ها منتشر می‌شود.", style = MaterialTheme.typography.labelMedium, color = muted())
                    }
                }
                Spacer(Modifier.height(Dimens.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onSubmit, modifier = Modifier.heightIn(min = Dimens.touch)) {
                        Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("ارسالِ مطلب")
                    }
                    Spacer(Modifier.width(Dimens.s))
                    TextButton(onClick = { onOpenList(null, "mine") }, modifier = Modifier.heightIn(min = Dimens.touch)) { Text("مطالبِ من") }
                }
            }
        }
    }
}

@Composable
private fun FeaturedPager(client: CommunityClient?, items: List<LearnItem>, cats: Map<String, LearnCategory>, onOpen: (String) -> Unit) {
    val pager = rememberPagerState { items.size }
    Column(Modifier.padding(top = Dimens.s)) {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = Dimens.gutter), pageSpacing = Dimens.s) { i ->
            val it = items[i]
            Surface(onClick = { onOpen(it.id) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    LearnCover(client, it, cats[it.categoryId], Modifier.fillMaxSize(), badgeSize = 52.dp)
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                        .padding(start = Dimens.m, end = Dimens.m, top = 28.dp, bottom = Dimens.m)) {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (it.featured) Pill("ویژه", Color.White, GoldInk, Icons.Outlined.Star)
                                cats[it.categoryId]?.let { c -> Pill(c.title, Color.White, Color.White.copy(alpha = 0.22f)) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(it.title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (items.size > 1) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val on = pager.currentPage == i
                Box(Modifier.padding(horizontal = 2.dp).size(if (on) 16.dp else 6.dp, 6.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            }
        }
    }
}

@Composable
private fun CategoryChipCard(c: LearnCategory, onClick: () -> Unit) {
    val color = hexColor(c.color)
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.widthIn(min = 150.dp).height(54.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(iconFor(c.icon), color, 34.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 150.dp)) {
                Text(c.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.count.toPersianDigits()} مطلب", style = MaterialTheme.typography.labelSmall, color = muted())
            }
        }
    }
}

@Composable
private fun VideoCard(client: CommunityClient?, v: LearnItem, cat: LearnCategory?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.width(220.dp)) {
        Column {
            LearnCover(client, v, cat, Modifier.fillMaxWidth().aspectRatio(16f / 9f), badgeSize = 36.dp)
            Spacer(Modifier.height(6.dp))
            Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 40.dp))
            Text(listOfNotNull(cat?.title, whenFa(v.shownAt()).ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =====================================================================================  paged list

/** Simple page-by-page loader for learnList-shaped results. */
private class LearnPager {
    val items = mutableStateListOf<LearnItem>()
    var hasMore by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(false)
    private var page = 0
    private var gen = 0

    suspend fun reload(fetch: suspend (Int) -> ApiResult<LearnList>) {
        val g = ++gen
        page = 0; loading = true; error = null
        val r = fetch(0)
        if (g != gen) return
        loading = false
        when (r) {
            is ApiResult.Ok -> { items.clear(); items.addAll(r.data.items); hasMore = r.data.hasMore; loaded = true }
            is ApiResult.Fail -> error = r.message
        }
    }

    suspend fun more(fetch: suspend (Int) -> ApiResult<LearnList>) {
        if (loading || !hasMore) return
        val g = gen
        loading = true
        val r = fetch(page + 1)
        if (g != gen) return
        loading = false
        when (r) {
            is ApiResult.Ok -> { page++; error = null; val ids = items.map { it.id }.toSet(); items.addAll(r.data.items.filter { it.id !in ids }); hasMore = r.data.hasMore }
            // Keep hasMore: the footer's «بیشتر» button is the retry (a single failure used to end the list for good).
            is ApiResult.Fail -> error = r.message
        }
    }

    fun remove(id: String) { items.removeAll { it.id == id } }
}

/** mode: "" published, "video", "article", "saved", "mine". */
@Composable
fun LearnListScreen(categoryId: String?, mode: String, onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val client = rememberCommunity()
    val cats by rememberLearnCategories(client)
    val catById = remember(cats) { cats.associateBy { it.id } }
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(categoryId) }
    var kind by rememberSaveable { mutableStateOf(when (mode) { "video", "article" -> mode; else -> "" }) }
    var tick by remember { mutableIntStateOf(0) }
    val pager = remember { LearnPager() }
    val scope = rememberCoroutineScope()
    val fetch: suspend (Int) -> ApiResult<LearnList> = { p ->
        client?.learnList(categoryId = cat, kind = kind.ifBlank { null }, q = query.trim(), page = p,
            saved = mode == "saved", mine = mode == "mine") ?: ApiResult.Fail(OFFLINE)
    }
    LaunchedEffect(query, cat, kind, tick, client) {
        if (query.isNotBlank()) delay(350)
        pager.reload(fetch)
    }
    // Coming back from an item (edit / unsave) refreshes the list.
    RefreshOnResume { if (pager.loaded) tick++ }

    val title = when (mode) {
        "saved" -> "ذخیره‌شده‌ها"; "mine" -> "مطالبِ من"
        else -> cat?.let { catById[it]?.title } ?: when (kind) { "video" -> "ویدیوها"; "article" -> "مقاله‌ها"; else -> "همهٔ آموزش‌ها" }
    }
    Scaffold(topBar = { AppTopBar(title, onBack = onBack) }) { pad ->
        val listState = rememberLazyListState()
        LazyColumn(Modifier.padding(pad).fillMaxSize(), state = listState) {
            item("search") {
                SearchField(query, { query = it }, "جست‌وجو در عنوان و متن", Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.s))
            }
            item("kinds") {
                ChoiceRow(listOf("", "video", "article"), kind, { when (it) { "video" -> "ویدیوها"; "article" -> "مقاله‌ها"; else -> "همه" } }, { kind = it })
            }
            if (cats.isNotEmpty()) item("cats") {
                Spacer(Modifier.height(6.dp))
                val opts = listOf<LearnCategory?>(null) + cats
                ChoiceRow(opts, opts.firstOrNull { it?.id == cat }, { it?.title ?: "همهٔ دسته‌ها" }, { cat = it?.id })
                Spacer(Modifier.height(6.dp))
            }
            when {
                !pager.loaded && pager.error != null -> item("err") { ErrorView(pager.error.orEmpty(), { tick++ }) }
                !pager.loaded -> item("loading") { LoadingView() }
                pager.items.isEmpty() -> item("empty") {
                    when (mode) {
                        "saved" -> EmptyView(Icons.Outlined.BookmarkBorder, "چیزی ذخیره نکرده‌اید", "با دکمهٔ نشانک در هر مطلب، آن را برای بعد نگه دارید.")
                        "mine" -> EmptyView(Icons.Outlined.UploadFile, "هنوز مطلبی نفرستاده‌اید", "مقاله‌ها و ویدیوهای شما پس از بررسی منتشر می‌شوند.")
                        else -> EmptyView(Icons.Outlined.SearchOff, "مطلبی پیدا نشد", if (query.isNotBlank()) "عبارتِ دیگری را امتحان کنید." else null)
                    }
                }
                else -> {
                    items(pager.items, key = { it.id }) { it ->
                        LearnRow(client, it, catById[it.categoryId], { onOpenItem(it.id) }, showStatus = mode == "mine")
                        InsetDivider(start = Dimens.gutter + 72.dp + Dimens.m)
                    }
                    item("more") {
                        if (pager.error != null && pager.loaded) Text(pager.error.orEmpty(), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(Dimens.gutter))
                        LoadMoreFooter(pager.hasMore, pager.loading) { scope.launch { pager.more(fetch) } }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth().heightIn(min = 48.dp), singleLine = true,
        placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "پاک کردن") } },
        shape = RoundedCornerShape(50), textStyle = MaterialTheme.typography.bodyMedium,
    )
}

/** Runs [onResume] every time the screen comes back to the foreground (not on the first start). */
@Composable
internal fun RefreshOnResume(onResume: () -> Unit) {
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val cb by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        var first = true
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { if (first) first = false else cb() }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

// =====================================================================================  admin review

/** Admin review queue. */
@Composable
fun LearnReviewScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val client = rememberCommunity()
    val cats by rememberLearnCategories(client)
    val catById = remember(cats) { cats.associateBy { it.id } }
    val pager = remember { LearnPager() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tick by remember { mutableIntStateOf(0) }
    var approving by remember { mutableStateOf<LearnItem?>(null) }
    var rejecting by remember { mutableStateOf<LearnItem?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val fetch: suspend (Int) -> ApiResult<LearnList> = { p -> client?.learnList(review = true, page = p) ?: ApiResult.Fail(OFFLINE) }
    LaunchedEffect(tick, client) { pager.reload(fetch) }
    RefreshOnResume { tick++ }

    fun act(item: LearnItem, op: String, note: String = "", featured: Boolean = false) {
        val c = client ?: return
        busy = item.id
        scope.launch {
            val r = c.learnReview(item.id, op, note, featured)
            busy = null
            when (r) {
                is ApiResult.Ok -> { pager.remove(item.id); snackbar.showSnackbar(if (op == "approve") "منتشر شد." else "رد شد و به فرستنده خبر داده شد.") }
                is ApiResult.Fail -> snackbar.showSnackbar(r.message)
            }
        }
    }

    Scaffold(topBar = { AppTopBar("بررسیِ مطالب", onBack = onBack, subtitle = if (pager.loaded) "${pager.items.size.toPersianDigits()}${if (pager.hasMore) "+" else ""} در انتظار" else null) },
        snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            when {
                !pager.loaded && pager.error != null -> item { ErrorView(pager.error.orEmpty(), { tick++ }) }
                !pager.loaded -> item { LoadingView() }
                pager.items.isEmpty() -> item { EmptyView(Icons.Outlined.TaskAlt, "صفِ بررسی خالی است", "مطلبِ تازه‌ای برای بررسی نیست.") }
                else -> {
                    items(pager.items, key = { it.id }) { it ->
                        Column(Modifier.fillMaxWidth()) {
                            AuthorLine(it.author, whenFa(it.createdAt), Modifier.padding(start = Dimens.gutter, end = Dimens.gutter, top = 10.dp), size = 28.dp)
                            LearnRow(client, it, catById[it.categoryId], { onOpenItem(it.id) })
                            Row(Modifier.fillMaxWidth().padding(start = Dimens.gutter, end = Dimens.gutter, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.s), verticalAlignment = Alignment.CenterVertically) {
                                Pill(it.kindLabel(), MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                if (busy == it.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                OutlinedButton(onClick = { rejecting = it }, enabled = busy == null, modifier = Modifier.heightIn(min = Dimens.touch)) { Text("رد") }
                                Button(onClick = { approving = it }, enabled = busy == null, modifier = Modifier.heightIn(min = Dimens.touch)) { Text("تأیید") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    item { LoadMoreFooter(pager.hasMore, pager.loading) { scope.launch { pager.more(fetch) } } }
                }
            }
        }
    }
    approving?.let { a -> ApproveDialog(a.title, onDismiss = { approving = null }) { f -> approving = null; act(a, "approve", featured = f) } }
    rejecting?.let { a -> RejectDialog(a.title, onDismiss = { rejecting = null }) { note -> rejecting = null; act(a, "reject", note = note) } }
}

@Composable
internal fun ApproveDialog(title: String, onDismiss: () -> Unit, onApprove: (featured: Boolean) -> Unit) {
    var featured by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأیید و انتشار") },
        text = {
            Column {
                Text("«$title» در بخشِ آموزش‌ها منتشر شود؟ به فرستنده خبر داده می‌شود.")
                Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touch), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(featured, { featured = it })
                    Text("ویژه (در بالای صفحهٔ آموزش‌ها)")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApprove(featured) }) { Text("انتشار") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
internal fun RejectDialog(title: String, onDismiss: () -> Unit, onReject: (String) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رد کردنِ مطلب") },
        text = {
            Column {
                Text("دلیل را برای فرستندهٔ «$title» بنویسید تا بداند چه چیزی را اصلاح کند.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Dimens.s))
                OutlinedTextField(note, { note = it; err = false }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                    isError = err, supportingText = if (err) { { Text("نوشتنِ دلیل لازم است.") } } else null,
                    placeholder = { Text("مثلاً: منبعِ مطلب را اضافه کنید.") })
            }
        },
        confirmButton = { TextButton(onClick = { if (note.isBlank()) err = true else onReject(note.trim()) }) { Text("رد", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
