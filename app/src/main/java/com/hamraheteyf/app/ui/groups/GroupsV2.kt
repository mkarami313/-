package com.hamraheteyf.app.ui.groups

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.GroupItem
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.RemoteImage
import com.hamraheteyf.app.ui.forum.ComposeFab
import com.hamraheteyf.app.ui.forum.ConfirmDialog
import com.hamraheteyf.app.ui.forum.FabClearance
import com.hamraheteyf.app.ui.forum.OnResume
import com.hamraheteyf.app.ui.forum.atTop
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** «کانون‌ها»: my groups / all groups, search and city filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsHubScreen(onBack: (() -> Unit)?, onOpenGroup: (String) -> Unit, onCreate: () -> Unit) {
    val client = rememberCommunity()
    var mine by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<GroupItem>?>(null) }
    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(mine, query, city, reload) {
        if (items == null) error = null  // a new tab/filter shows the spinner, not the previous failure
        if (query.isNotBlank()) delay(350)  // debounce typing
        val c = client ?: run { error = "اتصال به سرور برقرار نیست."; return@LaunchedEffect }
        when (val r = c.groupDirectory(q = query.trim(), city = city, my = mine)) {
            is ApiResult.Ok -> { items = r.data.items; if (r.data.cities.isNotEmpty()) cities = r.data.cities; error = null }
            is ApiResult.Fail -> if (items == null) error = r.message
        }
        refreshing = false
    }
    OnResume { reload++ }

    Scaffold(
        topBar = { AppTopBar("کانون‌ها", onBack = onBack, subtitle = "گروه‌های شهری و موضوعیِ خانواده‌ها") },
        floatingActionButton = { ComposeFab("گروهِ تازه", listState.atTop(), onCreate, Icons.Outlined.GroupAdd) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                query, { query = it.take(60) },
                Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.xs),
                placeholder = { Text("جست‌وجوی گروه…") }, singleLine = true, shape = RoundedCornerShape(50),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "پاک کردن") } },
            )
            TabRow(selectedTabIndex = if (mine) 0 else 1, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(mine, { mine = true; items = null }, text = { Text("گروه‌های من") })
                Tab(!mine, { mine = false; items = null }, text = { Text("همه") })
            }
            if (cities.isNotEmpty()) {
                ChoiceRow(listOf<String?>(null) + cities, city, { it ?: "همهٔ شهرها" }, { city = it; items = null }, Modifier.padding(vertical = Dimens.s))
            }
            PullToRefreshBox(refreshing, { refreshing = true; reload++ }, Modifier.fillMaxSize()) {
                val list = items
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = Dimens.xs, bottom = FabClearance)) {
                    when {
                        list == null && error != null -> item { ErrorView(error.orEmpty(), { error = null; reload++ }) }
                        list == null -> item { LoadingView() }
                        list.isEmpty() -> item {
                            when {
                                query.isNotBlank() || city != null -> EmptyView(Icons.Outlined.SearchOff, "گروهی پیدا نشد",
                                    "عبارت یا شهرِ دیگری را امتحان کنید.", "پاک کردنِ فیلترها", { query = ""; city = null })
                                mine -> EmptyView(Icons.Outlined.Groups, "هنوز عضوِ گروهی نیستید",
                                    "گروه‌های شهر یا موضوعِ مورد علاقه‌تان را پیدا کنید و بپیوندید.", "دیدنِ همهٔ گروه‌ها", { mine = false; items = null })
                                else -> EmptyView(Icons.Outlined.Groups, "هنوز گروهی ساخته نشده", "اولین گروه را شما بسازید.", "گروهِ تازه", onCreate)
                            }
                        }
                        else -> items(list.size, key = { list[it].id }) { i ->
                            GroupRow(list[i], client, { onOpenGroup(list[i].id) },
                                Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.cardGap / 2))
                        }
                    }
                }
            }
        }
    }
}

/** Create (groupId null) or edit a group. [onSaved] gets the group id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(groupId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val client = rememberCommunity()
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val editing = groupId != null

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var rules by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var coverId by rememberSaveable { mutableStateOf<String?>(null) }
    // Not editable here, but groupUpdate clears whatever isn't sent — carry them through.
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var ageBand by rememberSaveable { mutableStateOf<String?>(null) }
    var loaded by rememberSaveable { mutableStateOf(!editing) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    fun load() {
        val c = client ?: return
        val id = groupId ?: return
        loadError = null
        scope.launch {
            when (val r = c.groupInfo(id)) {
                is ApiResult.Ok -> r.data.group.let {
                    title = it.title; description = it.description; rules = it.rules; city = it.city.orEmpty()
                    isPrivate = it.visibility == "PRIVATE"; coverId = it.coverId
                    category = it.category; ageBand = it.ageBand; loaded = true
                }
                is ApiResult.Fail -> loadError = r.message
            }
        }
    }
    LaunchedEffect(groupId) { if (editing && !loaded) load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val c = client
        if (uri != null && c != null) {
            uploading = true
            scope.launch {
                when (val r = c.uploadImage(ctx, uri)) {
                    is ApiResult.Ok -> { coverId = r.data; dirty = true }
                    is ApiResult.Fail -> snack.showSnackbar(r.message)
                }
                uploading = false
            }
        }
    }

    fun leave() { if (dirty && !saving) confirmLeave = true else onBack() }
    BackHandler(enabled = dirty && !saving) { confirmLeave = true }

    fun save() {
        val c = client ?: return
        val problem = when {
            title.trim().length < 3 -> "نامِ گروه دست‌کم ۳ نویسه باشد."
            uploading -> "صبر کنید تا بارگذاریِ تصویر تمام شود."
            else -> null
        }
        if (problem != null) { scope.launch { snack.showSnackbar(problem) }; return }
        saving = true
        scope.launch {
            val vis = if (isPrivate) "PRIVATE" else "PUBLIC"
            val r: ApiResult<String> = if (groupId != null) {
                when (val u = c.groupUpdate(groupId, title.trim(), description.trim(), rules.trim(), city.trim(), vis, coverId, category, ageBand)) {
                    is ApiResult.Ok -> ApiResult.Ok(groupId); is ApiResult.Fail -> u
                }
            } else c.createGroup(title.trim(), description.trim(), vis, city.trim(), rules.trim())
            when (r) {
                is ApiResult.Ok -> { dirty = false; onSaved(r.data) }
                is ApiResult.Fail -> { saving = false; snack.showSnackbar(r.message) }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(if (editing) "ویرایشِ گروه" else "گروهِ تازه", onBack = ::leave, actions = {
                TextButton(onClick = ::save, enabled = loaded && !saving) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (editing) "ذخیره" else "ساختن", style = MaterialTheme.typography.titleSmall)
                }
            })
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (!loaded) {
            if (loadError != null) ErrorView(loadError.orEmpty(), ::load, Modifier.padding(padding)) else LoadingView(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter, vertical = Dimens.m),
            verticalArrangement = Arrangement.spacedBy(Dimens.m),
        ) {
            if (editing) {
                Text("تصویرِ کاور", style = MaterialTheme.typography.labelLarge, color = muted())
                Box(Modifier.fillMaxWidth().aspectRatio(3f).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                    val cov = coverId
                    if (cov != null && client != null) RemoteImage(client.fileUrl(cov), Modifier.fillMaxSize())
                    else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    if (uploading) CircularProgressIndicator()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !uploading) {
                        Icon(Icons.Outlined.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (coverId == null) "انتخابِ کاور" else "تغییرِ کاور")
                    }
                    if (coverId != null) TextButton(onClick = { coverId = null; dirty = true }) { Text("حذفِ کاور") }
                }
            }
            OutlinedTextField(title, { title = it.take(80); dirty = true }, Modifier.fillMaxWidth(), label = { Text("نامِ گروه") }, singleLine = true,
                placeholder = { Text("مثلاً «خانواده‌های اوتیسم در شیراز»") })
            OutlinedTextField(description, { description = it.take(1000); dirty = true }, Modifier.fillMaxWidth(), label = { Text("درباره‌ی گروه") }, minLines = 3)
            OutlinedTextField(city, { city = it.take(40); dirty = true }, Modifier.fillMaxWidth(), label = { Text("شهر (اختیاری)") }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Place, null) })
            OutlinedTextField(rules, { rules = it.take(2000); dirty = true }, Modifier.fillMaxWidth(), label = { Text("قوانینِ گروه (اختیاری)") }, minLines = 3,
                placeholder = { Text("مثلاً: احترام، بدونِ تبلیغ، حفظِ حریمِ کودکان") })
            Text("نوعِ گروه", style = MaterialTheme.typography.labelLarge, color = muted())
            VisibilityOption("عمومی", "همه گفتگوها را می‌بینند و هر کسی می‌تواند فوراً عضو شود.", !isPrivate) { isPrivate = false; dirty = true }
            VisibilityOption("خصوصی", "فقط اعضا گفتگوها را می‌بینند؛ عضویت با تأییدِ مدیر است.", isPrivate) { isPrivate = true; dirty = true }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
    if (confirmLeave) ConfirmDialog("تغییرات ذخیره نشده", "اگر خارج شوید، تغییرات از بین می‌رود.", "خروج", onConfirm = onBack, onDismiss = { confirmLeave = false })
}

@Composable
private fun VisibilityOption(title: String, note: String, selected: Boolean, onClick: () -> Unit) {
    AppCard(
        onClick = onClick,
        border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, null)
            Spacer(Modifier.width(Dimens.s))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(note, style = MaterialTheme.typography.bodySmall, color = muted())
            }
        }
    }
}
