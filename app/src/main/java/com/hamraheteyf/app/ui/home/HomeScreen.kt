package com.hamraheteyf.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.community.LearnItem
import com.hamraheteyf.app.community.TopicCard
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.SafetyBanner
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.navigation.AppFeature
import com.hamraheteyf.app.ui.navigation.Routes
import com.hamraheteyf.app.ui.onboarding.OnboardingPrefs
import com.hamraheteyf.app.ui.theme.*

/**
 * Home: one scrolling feed, Ninisite-style. Greeting + one quick action, a single row of round
 * shortcuts, then real content — hot forum topics, expert answers, learning items, fresh topics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tiles: List<AppFeature>,
    onNavigate: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val client = rememberCommunity()
    val vm: HomeViewModel = viewModel(factory = appViewModelFactory { HomeViewModel(client) })
    val state by vm.state.collectAsState()
    // remember: a new Flow on every recomposition restarted both collections each time.
    val user by remember(container) { container.userRepository.observeCurrentUser() }.collectAsState(null)
    val unread by remember(container) { container.notificationRepository.observeUnreadCount() }.collectAsState(0)
    val ctx = LocalContext.current
    val interests = remember(user?.id) { user?.id?.let { OnboardingPrefs.interests(ctx, it) }.orEmpty() }
    val shortcuts = remember(tiles, interests) { buildShortcuts(tiles, interests) }

    // Coming back to Home (from a topic, a tab…) quietly refreshes the feed.
    LaunchedEffect(Unit) { if (state.feed != null) vm.load(user = false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AppTopBar(
                    title = null,
                    actions = {
                        IconButton(onClick = { onNavigate(Routes.SEARCH) }) { Icon(Icons.Outlined.Search, "جست‌وجو") }
                        IconButton(onClick = { onNavigate(Routes.NOTIFICATIONS) }) {
                            BadgedBox(badge = {
                                if (unread > 0) Badge { Text(if (unread > 99) "۹۹+" else unread.toPersianDigits()) }
                            }) { Icon(Icons.Outlined.Notifications, "اعلان‌ها") }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.load(user = true) },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val feed = state.feed
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Dimens.xl)) {
                item(key = "greet") {
                    Greeting(
                        name = user?.displayName,
                        family = user?.accountType == AccountType.FAMILY,
                        onAsk = { onNavigate(Routes.topicNew(forumId = "clinic")) },
                        onNewTopic = { onNavigate(Routes.topicNew()) },
                    )
                }
                if (feed != null && feed.followedUnread > 0) item(key = "followed") {
                    Banner(
                        Icons.Outlined.MarkChatUnread,
                        "${feed.followedUnread.toPersianDigits()} گفتگوی دنبال‌شده پاسخِ تازه دارد",
                        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { onNavigate(Routes.FORUM) }
                }
                if (feed != null && user?.isAdmin == true && feed.pendingReview > 0) item(key = "review") {
                    Banner(
                        Icons.Outlined.RateReview,
                        "${feed.pendingReview.toPersianDigits()} مطلبِ آموزشی منتظرِ بررسی است",
                        MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                    ) { onNavigate(Routes.LEARN_REVIEW) }
                }
                item(key = "shortcuts") { ShortcutRow(shortcuts, onNavigate) }

                when {
                    feed == null && state.loading -> item(key = "skeleton") { FeedSkeleton() }
                    feed == null -> item(key = "error") {
                        ErrorView(state.error ?: "خوراکِ صفحهٔ اصلی بارگیری نشد.", onRetry = { vm.load(user = false) })
                    }
                    feed != null -> {
                        if (state.error != null) item(key = "stale") {
                            Text(
                                state.error ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.xs),
                            )
                        }
                        topicSection("hot", "داغ‌ترینِ انجمن", feed.hot.take(6), { onNavigate(Routes.FORUM) }, onNavigate)
                        if (feed.clinic.isNotEmpty()) item(key = "clinic") {
                            HomeSection("پاسخِ متخصصان", onAll = { onNavigate(Routes.CLINIC) })
                            LazyRow(contentPadding = PaddingValues(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                                items(feed.clinic, key = { it.id }) { t -> ClinicCard(t) { onNavigate(Routes.topic(t.id)) } }
                            }
                        }
                        if (feed.learn.isNotEmpty()) item(key = "learn") {
                            HomeSection("آموزش‌ها", onAll = { onNavigate(Routes.LEARN) })
                            LazyRow(contentPadding = PaddingValues(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                                items(feed.learn, key = { it.id }) { l ->
                                    LearnCard(l, coverUrl = l.coverId?.let { id -> client?.fileUrl(id) }) { onNavigate(Routes.learnItem(l.id)) }
                                }
                            }
                        }
                        topicSection("fresh", "تازه‌ها", feed.fresh.take(8), { onNavigate(Routes.FORUM) }, onNavigate)
                        if (feed.hot.isEmpty() && feed.fresh.isEmpty() && feed.clinic.isEmpty() && feed.learn.isEmpty()) item(key = "empty") {
                            EmptyView(
                                Icons.Outlined.Forum, "هنوز گفتگویی در انجمن نیست",
                                "اولین موضوع را شما شروع کنید؛ پرسش‌ها و تجربه‌ها این‌جا نشان داده می‌شوند.",
                                action = "موضوعِ تازه", onAction = { onNavigate(Routes.topicNew()) },
                            )
                        }
                    }
                }
                item(key = "safety") { SafetyBanner(Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.l)) }
            }
        }
    }
}

// ------------------------------------------------------------------ greeting + banners

@Composable
private fun Greeting(name: String?, family: Boolean, onAsk: () -> Unit, onNewTopic: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .padding(start = Dimens.gutter, end = Dimens.gutter, top = Dimens.m, bottom = Dimens.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (name.isNullOrBlank()) "سلام" else "سلامِ ${name.trim()}",
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (family) "سؤالی دارید؟ متخصصانِ تأییدشده پاسخ می‌دهند." else "امروز تجربه‌ای برای اشتراک دارید؟",
                style = MaterialTheme.typography.labelMedium, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Dimens.s))
        if (family) {
            Button(onClick = onAsk, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.heightIn(min = Dimens.touch)) {
                Icon(Icons.Outlined.MedicalServices, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("پرسش از متخصص")
            }
        } else {
            Button(onClick = onNewTopic, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.heightIn(min = Dimens.touch)) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("موضوعِ تازه")
            }
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, container: Color, content: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = container, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = Dimens.s),
    ) {
        Row(Modifier.heightIn(min = Dimens.touch).padding(horizontal = Dimens.m, vertical = Dimens.s), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Dimens.s))
            Text(text, style = MaterialTheme.typography.labelLarge, color = content, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = content, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HomeSection(title: String, onAll: (() -> Unit)? = null) {
    SectionTitle(title, Modifier.padding(horizontal = Dimens.gutter), action = if (onAll != null) "همه" else null, onAction = onAll)
}

// ------------------------------------------------------------------ shortcuts

private data class Shortcut(val key: String, val title: String, val icon: ImageVector, val color: Color, val route: String)

private val SHORTCUT_COLORS = mapOf(
    "childfile" to Apricot, "booking" to Expert, "tools" to Teal, "courses" to Gold, "places" to VerifiedGreen,
    "directory" to Expert, "donations" to Like, "groups" to TealDark, "messages" to Teal,
)
private val FALLBACK_COLORS = listOf(Teal, Apricot, Expert, Gold, VerifiedGreen, Like)

/** «کلینیک» and «آموزش‌ها» first, then the enabled home modules (the user's interests first). Bottom tabs are skipped. */
private fun buildShortcuts(tiles: List<AppFeature>, interests: Set<String>): List<Shortcut> {
    val fixed = listOf(
        Shortcut("clinic", "کلینیک", Icons.Outlined.MedicalServices, Expert, Routes.CLINIC),
        Shortcut("learn", "آموزش‌ها", Icons.Outlined.PlayCircle, Gold, Routes.LEARN),
    )
    val modules = tiles.filter { it.id != "messages" }
        .sortedWith(compareBy({ if (it.id in interests) 0 else 1 }, { it.order }))
        .mapIndexed { i, f -> Shortcut(f.id, f.title, f.icon, SHORTCUT_COLORS[f.id] ?: FALLBACK_COLORS[i % FALLBACK_COLORS.size], f.entryRoute) }
    return fixed + modules
}

@Composable
private fun ShortcutRow(items: List<Shortcut>, onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Dimens.gutter - 4.dp, vertical = Dimens.m),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items, key = { it.key }) { s ->
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { onNavigate(s.route) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(s.color.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = s.color, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ------------------------------------------------------------------ topics

private fun LazyListScope.topicSection(key: String, title: String, topics: List<TopicCard>, onAll: () -> Unit, onNavigate: (String) -> Unit) {
    if (topics.isEmpty()) return
    item(key = "h_$key") { HomeSection(title, onAll) }
    item(key = "c_$key") {
        AppCard(Modifier.padding(horizontal = Dimens.gutter), padding = PaddingValues(0.dp)) {
            topics.forEachIndexed { i, t ->
                TopicRow(t) { onNavigate(Routes.topic(t.id)) }
                if (i < topics.lastIndex) InsetDivider(start = Dimens.cardPad + Dimens.avatar + Dimens.s)
            }
        }
    }
}

@Composable
private fun TopicRow(t: TopicCard, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Dimens.cardPad),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(t.author.name, t.author.photoUrl, Dimens.avatar, t.author.anonymous)
        Spacer(Modifier.width(Dimens.s))
        Column(Modifier.weight(1f)) {
            Text(
                t.title, style = MaterialTheme.typography.titleSmall,
                fontWeight = if (t.unread) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.expertReplyCount > 0) {
                    Pill("پاسخِ متخصص", color = Expert, icon = VerifiedIcon)
                    Spacer(Modifier.width(6.dp))
                }
                val time = (t.lastPostAt.takeIf { it > 0 } ?: t.createdAt).takeIf { it > 0 }?.let { relativeTimeFa(it) }
                Text(
                    listOfNotNull(t.author.name.ifBlank { null }, time).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = muted(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Counter(Icons.Outlined.ChatBubbleOutline, t.replyCount, contentDescription = "پاسخ‌ها")
                Spacer(Modifier.width(Dimens.s))
                Counter(Icons.Outlined.FavoriteBorder, t.likeCount, contentDescription = "پسندها")
            }
        }
    }
}

@Composable
private fun ClinicCard(t: TopicCard, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Expert.copy(alpha = 0.25f)),
        modifier = Modifier.width(264.dp).height(152.dp),
    ) {
        Column(Modifier.padding(Dimens.cardPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(VerifiedIcon, null, tint = Expert, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (t.expertReplyCount > 1) "${t.expertReplyCount.toPersianDigits()} پاسخِ متخصص" else "پاسخِ متخصص",
                    style = MaterialTheme.typography.labelMedium, color = Expert, modifier = Modifier.weight(1f),
                )
                if (t.answered) Pill("پاسخ داده شد", color = VerifiedGreen)
            }
            Spacer(Modifier.height(6.dp))
            Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (t.excerpt.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(t.excerpt, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val time = (t.lastPostAt.takeIf { it > 0 } ?: t.createdAt).takeIf { it > 0 }?.let { relativeTimeFa(it) }
                Text(time.orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted(), modifier = Modifier.weight(1f))
                Counter(Icons.Outlined.ChatBubbleOutline, t.replyCount, contentDescription = "پاسخ‌ها")
            }
        }
    }
}

// ------------------------------------------------------------------ learning

@Composable
private fun LearnCard(l: LearnItem, coverUrl: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(220.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.primaryContainer)) {
                if (coverUrl != null) RemoteImage(coverUrl, Modifier.fillMaxSize())
                else Icon(
                    if (l.isVideo) Icons.Outlined.Movie else Icons.Outlined.Article, null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                )
                if (l.isVideo) {
                    Box(
                        Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, "ویدیو", tint = Color.White, modifier = Modifier.size(26.dp)) }
                    l.durationSec?.takeIf { it > 0 }?.let { d ->
                        Text(
                            durationFa(d), style = MaterialTheme.typography.labelSmall, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = Dimens.m, vertical = Dimens.s)) {
                Text(l.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(if (l.isVideo) "ویدیو" else "مقاله", l.viewCount.takeIf { it > 0 }?.let { "${it.toPersianDigits()} بازدید" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = muted(), maxLines = 1,
                )
            }
        }
    }
}

private fun durationFa(sec: Int): String = "%d:%02d".format(java.util.Locale.US, sec / 60, sec % 60).toPersianDigits()

// ------------------------------------------------------------------ skeleton

@Composable
private fun FeedSkeleton() {
    val c = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        Spacer(Modifier.height(Dimens.l))
        SkeletonBar(0.35f, c, 16.dp)
        Spacer(Modifier.height(Dimens.s))
        AppCard(padding = PaddingValues(0.dp)) {
            repeat(4) { i ->
                Row(Modifier.padding(Dimens.cardPad), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(Dimens.avatar).clip(CircleShape).background(c))
                    Spacer(Modifier.width(Dimens.s))
                    Column(Modifier.weight(1f)) {
                        SkeletonBar(0.9f, c); Spacer(Modifier.height(6.dp)); SkeletonBar(0.6f, c); Spacer(Modifier.height(8.dp)); SkeletonBar(0.4f, c, 10.dp)
                    }
                }
                if (i < 3) InsetDivider(start = Dimens.cardPad + Dimens.avatar + Dimens.s)
            }
        }
        Spacer(Modifier.height(Dimens.l))
        SkeletonBar(0.3f, c, 16.dp)
        Spacer(Modifier.height(Dimens.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
            repeat(2) { Box(Modifier.width(220.dp).height(150.dp).clip(MaterialTheme.shapes.medium).background(c)) }
        }
    }
}

@Composable
private fun SkeletonBar(w: Float, c: Color, h: Dp = 12.dp) {
    Box(Modifier.fillMaxWidth(w).height(h).clip(RoundedCornerShape(6.dp)).background(c))
}
