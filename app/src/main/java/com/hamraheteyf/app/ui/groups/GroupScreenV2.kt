package com.hamraheteyf.app.ui.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.CommunityClient
import com.hamraheteyf.app.community.GroupInfo
import com.hamraheteyf.app.community.Person
import com.hamraheteyf.app.community.TopicList
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.ReportDialog
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.forum.*
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import kotlinx.coroutines.launch

private enum class GroupTab(val label: String) { TOPICS("گفتگوها"), MEMBERS("اعضا"), REQUESTS("درخواست‌ها") }

/** A pending confirmation: what to ask and what to run on «بله». */
private class Pending(val title: String, val text: String, val confirm: String, val destructive: Boolean = true, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onNewTopic: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val client = rememberCommunity()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val myId by LocalAppContainer.current.authRepository.currentUserId.collectAsState()

    var info by remember { mutableStateOf<GroupInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tabName by rememberSaveable { mutableStateOf(GroupTab.TOPICS.name) }
    val tab = GroupTab.valueOf(tabName)
    var pending by remember { mutableStateOf<Pending?>(null) }
    var reporting by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    fun toast(m: String) { scope.launch { snack.showSnackbar(m) } }

    fun loadInfo() {
        val c = client ?: run { error = "اتصال به سرور برقرار نیست."; return }
        scope.launch {
            when (val r = c.groupInfo(groupId)) {
                is ApiResult.Ok -> { info = r.data; error = null }
                is ApiResult.Fail -> if (info == null) error = r.message else toast(r.message)
            }
            refreshing = false
        }
    }
    LaunchedEffect(groupId) { loadInfo() }

    val g = info
    val member = g?.myStatus == "ACTIVE"
    val archived = g?.group?.status == "ARCHIVED"
    val canSee = g != null && (g.group.visibility != "PRIVATE" || member || g.canManage)
    // groupInfo.canManage = owner/moderator OR site admin, the same rule approveMember/removeMember enforce.
    val memberManager = g?.canManage == true
    // createTopic refuses an archived group, so don't offer to start a conversation there
    val canPost = member && !archived
    val feed = rememberTopicFeed(client, groupId, canSee) { page ->
        if (!canSee) ApiResult.Ok(TopicList()) else client?.topics(groupId = groupId, page = page) ?: ApiResult.Fail("اتصال به سرور برقرار نیست.")
    }
    OnResume { loadInfo(); if (canSee) feed.refresh() }

    /** Runs a group call with a busy flag, then reloads the header (and topics when access may change). */
    fun act(call: suspend (CommunityClient) -> ApiResult<Unit>, ok: String? = null, after: () -> Unit = {}) {
        val c = client ?: return
        busy = true
        scope.launch {
            when (val r = call(c)) {
                is ApiResult.Ok -> { ok?.let { snack.showSnackbar(it) }; after(); loadInfo() }
                is ApiResult.Fail -> snack.showSnackbar(r.message)
            }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(g?.group?.title ?: "گروه", onBack = onBack, actions = {
                if (g != null) GroupMenu(
                    g, onEdit = { onEdit(groupId) },
                    onArchive = {
                        pending = Pending("بستنِ گروه", "گروه بسته می‌شود و دیگر در فهرست نمی‌آید. مطمئن هستید؟", "بستنِ گروه") {
                            act({ it.groupArchive(groupId) }, after = onBack)
                        }
                    },
                    onReport = { reporting = true },
                )
            })
        },
        floatingActionButton = {
            if (canPost && tab == GroupTab.TOPICS) ComposeFab("گفتگوی تازه", listState.atTop(), { onNewTopic(groupId) })
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        when {
            g == null && error != null -> ErrorView(error.orEmpty(), { error = null; loadInfo() }, Modifier.padding(padding))
            g == null -> LoadingView(Modifier.padding(padding))
            else -> PullToRefreshBox(refreshing, { refreshing = true; loadInfo(); if (canSee) feed.refresh() }, Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = FabClearance)) {
                    item(key = "header") {
                        GroupHeader(
                            g, client, busy,
                            onJoin = { act({ it.join(groupId) }, if (g.group.visibility == "PRIVATE") "درخواستِ شما برای مدیرِ گروه فرستاده شد." else "به گروه پیوستید.") { feed.refresh() } },
                            onCancel = { act({ it.groupCancelRequest(groupId) }, "درخواست لغو شد.") },
                            onNotify = { on -> act({ it.groupNotify(groupId, on) }, if (on) "اعلانِ گفتگوهای تازه روشن شد." else "اعلان‌ها خاموش شد.") },
                            onLeave = {
                                if (g.myRole == "OWNER") toast("سازندهٔ گروه نمی‌تواند آن را ترک کند؛ ابتدا مدیریت را به عضوِ دیگری واگذار کنید.")
                                else pending = Pending("ترکِ گروه", "از «${g.group.title}» خارج می‌شوید.", "ترکِ گروه") {
                                    act({ it.leave(groupId) }, "از گروه خارج شدید.") { feed.refresh() }
                                }
                            },
                        )
                    }
                    if (!canSee) {
                        item(key = "locked") {
                            EmptyView(Icons.Outlined.Lock, "این گروه خصوصی است",
                                if (g.myStatus == "REQUESTED") "پس از تأییدِ مدیر، گفتگوها و اعضا را می‌بینید."
                                else "برای دیدنِ گفتگوها درخواستِ عضویت بدهید.")
                        }
                        return@LazyColumn
                    }
                    val tabs = GroupTab.entries.filter { it != GroupTab.REQUESTS || memberManager }
                    val shown = if (tab in tabs) tab else GroupTab.TOPICS  // e.g. lost the moderator role meanwhile
                    stickyHeader(key = "tabs") {
                        TabRow(selectedTabIndex = tabs.indexOf(shown).coerceAtLeast(0), containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEach { t ->
                                Tab(t == shown, { tabName = t.name }, text = {
                                    val n = when (t) { GroupTab.MEMBERS -> g.members.size; GroupTab.REQUESTS -> g.requests.size; else -> 0 }
                                    Text(if (n > 0) "${t.label} (${n.toPersianDigits()})" else t.label)
                                })
                            }
                        }
                    }
                    when (shown) {
                        GroupTab.TOPICS -> {
                            item(key = "gap") { Spacer(Modifier.height(Dimens.xs)) }
                            topicFeedItems(
                                feed, emptyMap(), onOpenTopic, Icons.Outlined.Forum, "هنوز گفتگویی در این گروه نیست",
                                when { archived -> "این گروه بسته شده است."; member -> "اولین گفتگو را شما آغاز کنید."; else -> "برای گفتگو عضوِ گروه شوید." },
                                if (canPost) "گفتگوی تازه" else null, if (canPost) ({ onNewTopic(groupId) }) else null, showForum = false,
                            )
                        }
                        GroupTab.MEMBERS -> item(key = "members") {
                            MembersCard(g, myId, memberManager, busy, onOpenProfile,
                                onRole = { p, role, label -> act({ it.groupSetRole(groupId, p.id, role) }, label) },
                                onTransfer = { p ->
                                    pending = Pending("واگذاریِ مدیریت", "مدیریتِ گروه به «${p.name}» واگذار می‌شود و شما ناظر می‌شوید.", "واگذاری", destructive = false) {
                                        act({ it.groupSetRole(groupId, p.id, "OWNER") }, "مدیریت واگذار شد.")
                                    }
                                },
                                onRemove = { p ->
                                    pending = Pending("حذف از گروه", "«${p.name}» از گروه حذف می‌شود و نمی‌تواند دوباره بپیوندد.", "حذف") {
                                        act({ it.removeMember(groupId, p.id) }, "عضو حذف شد.")
                                    }
                                },
                            )
                        }
                        GroupTab.REQUESTS -> item(key = "requests") {
                            RequestsCard(g, busy, onOpenProfile,
                                onApprove = { p -> act({ it.approve(groupId, p.id) }, "«${p.name}» عضو شد.") },
                                onReject = { p -> act({ it.removeMember(groupId, p.id) }, "درخواست رد شد.") },
                            )
                        }
                    }
                }
            }
        }
    }

    pending?.let { p -> ConfirmDialog(p.title, p.text, p.confirm, onConfirm = p.run, onDismiss = { pending = null }, destructive = p.destructive) }
    if (reporting) ReportDialog(onDismiss = { reporting = false }, onSubmit = { reason, note ->
        reporting = false
        val c = client
        if (c != null) scope.launch {
            when (val r = c.report("GROUP", groupId, reason.name, note)) {
                is ApiResult.Ok -> snack.showSnackbar("گزارشِ شما ثبت شد.")
                is ApiResult.Fail -> snack.showSnackbar(r.message)
            }
        }
    })
}

@Composable
private fun GroupMenu(g: GroupInfo, onEdit: () -> Unit, onArchive: () -> Unit, onReport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "گزینه‌ها") }
        DropdownMenu(open, { open = false }) {
            if (g.canManage) DropdownMenuItem({ Text("ویرایش") }, { open = false; onEdit() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
            if (g.isOwner && g.group.status != "ARCHIVED") DropdownMenuItem({ Text("بستنِ گروه", color = MaterialTheme.colorScheme.error) }, { open = false; onArchive() },
                leadingIcon = { Icon(Icons.Outlined.Archive, null, tint = MaterialTheme.colorScheme.error) })
            if (!g.isOwner) DropdownMenuItem({ Text("گزارشِ گروه") }, { open = false; onReport() }, leadingIcon = { Icon(Icons.Outlined.Flag, null) })
        }
    }
}

@Composable
private fun MembersCard(
    g: GroupInfo, myId: String?, manager: Boolean, busy: Boolean, onOpenProfile: (String) -> Unit,
    onRole: (Person, String, String) -> Unit, onTransfer: (Person) -> Unit, onRemove: (Person) -> Unit,
) {
    AppCard(Modifier.padding(Dimens.gutter), padding = PaddingValues(vertical = Dimens.xs)) {
        if (g.members.isEmpty()) {
            EmptyView(Icons.Outlined.People, "عضوی دیده نمی‌شود"); return@AppCard
        }
        g.members.forEachIndexed { i, p ->
            if (i > 0) InsetDivider(60.dp)
            val self = p.id == myId
            val real = p.id.isNotEmpty()  // deleted accounts come back with an empty id
            val ownerMenu = g.isOwner && manager && real && !self && p.role != "OWNER"
            val modMenu = !g.isOwner && manager && real && !self && p.role == "MEMBER"
            MemberRow(p, onClick = { if (p.id.isNotEmpty()) onOpenProfile(p.id) }) {
                if (ownerMenu || modMenu) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }, enabled = !busy) { Icon(Icons.Outlined.MoreVert, "مدیریتِ ${p.name}", tint = muted()) }
                        DropdownMenu(open, { open = false }) {
                            if (ownerMenu) {
                                if (p.role == "MODERATOR") DropdownMenuItem({ Text("عضوِ عادی") }, { open = false; onRole(p, "MEMBER", "«${p.name}» عضوِ عادی شد.") })
                                else DropdownMenuItem({ Text("ناظر کردن") }, { open = false; onRole(p, "MODERATOR", "«${p.name}» ناظر شد.") })
                                DropdownMenuItem({ Text("واگذاریِ مدیریت") }, { open = false; onTransfer(p) })
                            }
                            DropdownMenuItem({ Text("حذف از گروه", color = MaterialTheme.colorScheme.error) }, { open = false; onRemove(p) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsCard(g: GroupInfo, busy: Boolean, onOpenProfile: (String) -> Unit, onApprove: (Person) -> Unit, onReject: (Person) -> Unit) {
    if (g.requests.isEmpty()) {
        EmptyView(Icons.Outlined.HowToReg, "درخواستی در انتظار نیست", "درخواست‌های عضویت در گروهِ خصوصی این‌جا می‌آیند."); return
    }
    AppCard(Modifier.padding(Dimens.gutter), padding = PaddingValues(vertical = Dimens.xs)) {
        g.requests.forEachIndexed { i, p ->
            if (i > 0) InsetDivider(60.dp)
            MemberRow(p, onClick = { if (p.id.isNotEmpty()) onOpenProfile(p.id) }) {
                TextButton(onClick = { onReject(p) }, enabled = !busy && p.id.isNotEmpty(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("رد") }
                FilledTonalButton(onClick = { onApprove(p) }, enabled = !busy && p.id.isNotEmpty()) { Text("پذیرش") }
            }
        }
    }
}

