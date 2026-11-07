package com.hamraheteyf.app.ui.forum

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.community.PollDraft
import com.hamraheteyf.app.community.rememberCommunity
import com.hamraheteyf.app.net.ApiResult
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Expert
import com.hamraheteyf.app.ui.theme.ExpertContainer
import kotlinx.coroutines.launch

private const val TITLE_MIN = 4
private const val TITLE_MAX = 140
private const val BODY_MIN = 5
private const val BODY_MAX = 20_000   // server TOPIC_BODY_MAX

/** New topic (in a forum or a group) or edit an existing one. [onDone] gets the topic id. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicComposerScreen(
    forumId: String?,
    groupId: String?,
    editTopicId: String?,
    onBack: () -> Unit,
    onDone: (String) -> Unit,
) {
    val client = rememberCommunity()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val tree = rememberForumTree(client)
    val editing = editTopicId != null

    var chosenForum by rememberSaveable { mutableStateOf(forumId) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var anonymous by rememberSaveable { mutableStateOf(false) }
    var pollOn by rememberSaveable { mutableStateOf(false) }
    var pollQ by rememberSaveable { mutableStateOf("") }
    var pollOptions by rememberSaveable { mutableStateOf(arrayListOf("", "")) }
    var pollMulti by rememberSaveable { mutableStateOf(false) }
    var pollDays by rememberSaveable { mutableIntStateOf(0) }
    val uploads = rememberImageUploads(client)

    var origTitle by rememberSaveable { mutableStateOf("") }
    var origBody by rememberSaveable { mutableStateOf("") }
    var origImages by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var editLoaded by rememberSaveable { mutableStateOf(false) }
    var editGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var groupTitle by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }

    fun loadEdit() {
        val c = client ?: return
        val id = editTopicId ?: return
        loadError = null
        scope.launch {
            when (val r = c.topic(id, 0)) {
                is ApiResult.Ok -> {
                    val t = r.data.topic
                    // Only the first load fills the form: after rotation/process death the restored
                    // fields and images are the user's edits and must not be reset to the server copy.
                    if (!editLoaded) {
                        title = t.title; body = t.body; origTitle = t.title; origBody = t.body
                        chosenForum = t.forumId; editGroup = t.groupId
                        origImages = ArrayList(t.images)
                        uploads.setExisting(t.images)
                        editLoaded = true
                    }
                }
                is ApiResult.Fail -> loadError = r.message
            }
        }
    }
    LaunchedEffect(editTopicId) { if (editing && !editLoaded) loadEdit() }
    // The group's name for the «در گروهِ …» line — also when editing a group topic (editGroup).
    val anyGroup = groupId ?: editGroup
    LaunchedEffect(anyGroup) {
        if (anyGroup != null) (client?.groupInfo(anyGroup) as? ApiResult.Ok)?.let { groupTitle = it.data.group.title }
    }

    val inGroup = groupId != null || editGroup != null
    val forum = remember(tree, chosenForum) { ForumTreeCache.find(chosenForum) }
    val clinicForum = remember(tree, chosenForum) { chosenForum != null && ForumTreeCache.isClinic(chosenForum) }
    val clinic = !inGroup && clinicForum
    val dirty = if (editing) title != origTitle || body != origBody || uploads.ids != origImages || uploads.busy
        else title.isNotBlank() || body.isNotBlank() || uploads.items.isNotEmpty()
    // Clinic questions carry no poll (the switch is hidden there); a poll set up before switching forum must not leak in.
    val withPoll = pollOn && !clinic && !editing

    val titleErr = when {
        title.trim().length < TITLE_MIN -> "عنوان دست‌کم ${TITLE_MIN.toPersianDigits()} نویسه باشد."
        title.trim().length > TITLE_MAX -> "عنوان حداکثر ${TITLE_MAX.toPersianDigits()} نویسه باشد."
        else -> null
    }
    val bodyErr = if (body.trim().length < BODY_MIN) "متن را کامل‌تر بنویسید (دست‌کم ${BODY_MIN.toPersianDigits()} نویسه)." else null

    fun leave() { if (dirty && !publishing) confirmLeave = true else onBack() }
    BackHandler(enabled = dirty && !publishing) { confirmLeave = true }

    fun publish() {
        tried = true
        val c = client ?: return
        val problem = when {
            !editing && !inGroup && chosenForum == null -> "یک تالار برای موضوع انتخاب کنید."
            titleErr != null -> titleErr
            bodyErr != null -> bodyErr
            uploads.busy -> "صبر کنید تا بارگذاریِ تصاویر تمام شود."
            uploads.hasFailed -> "روی تصویرِ ناموفق بزنید تا دوباره فرستاده شود، یا حذفش کنید."
            // the server drops repeated options, so «بله / بله» is one option, not two
            withPoll && pollOptions.map { it.trim() }.filter { it.isNotEmpty() }.distinct().size < 2 -> "نظرسنجی دست‌کم دو گزینهٔ متفاوت لازم دارد."
            else -> null
        }
        if (problem != null) { scope.launch { snack.showSnackbar(problem) }; return }
        publishing = true
        scope.launch {
            val r: ApiResult<String> = if (editTopicId != null) {
                when (val e = c.edit(editTopicId, body.trim(), title.trim(), uploads.ids)) {
                    is ApiResult.Ok -> ApiResult.Ok(editTopicId); is ApiResult.Fail -> e
                }
            } else c.createTopic(
                title.trim(), body.trim(), forumId = if (groupId == null) chosenForum else null, groupId = groupId,
                images = uploads.ids, anonymous = anonymous && groupId == null,
                poll = if (withPoll) PollDraft(pollQ.trim(), pollOptions.map { it.trim() }.filter { it.isNotEmpty() }.distinct(), pollMulti, pollDays) else null,
            )
            when (r) {
                is ApiResult.Ok -> { origTitle = title; origBody = body; uploads.clear(); onDone(r.data) }
                is ApiResult.Fail -> { publishing = false; snack.showSnackbar(r.message) }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                when { editing -> "ویرایشِ موضوع"; clinic -> "پرسش از متخصص"; inGroup -> "گفتگوی تازه"; else -> "موضوعِ تازه" },
                onBack = ::leave,
                actions = {
                    TextButton(onClick = ::publish, enabled = !publishing && (!editing || editLoaded)) {
                        if (publishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (editing) "ذخیره" else "انتشار", style = MaterialTheme.typography.titleSmall)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (editing && !editLoaded) {
            if (loadError != null) ErrorView(loadError.orEmpty(), ::loadEdit, Modifier.padding(padding)) else LoadingView(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter, vertical = Dimens.m),
            verticalArrangement = Arrangement.spacedBy(Dimens.m),
        ) {
            // destination
            when {
                inGroup -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Groups, null, tint = muted(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("در گروهِ «${groupTitle ?: "…"}»", style = MaterialTheme.typography.labelLarge, color = muted())
                }
                editing -> forum?.let {
                    Text("در تالارِ «${it.title}»", style = MaterialTheme.typography.labelLarge, color = muted())
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تالار:", style = MaterialTheme.typography.labelLarge, color = muted())
                    Spacer(Modifier.width(Dimens.s))
                    AssistChip(
                        onClick = {
                            showPicker = true
                            // the tree may have failed to load: tapping is the retry
                            if (tree == null) client?.let { c -> scope.launch { ForumTreeCache.load(c, force = true) } }
                        },
                        label = { Text(forum?.title ?: "انتخابِ تالار") },
                        leadingIcon = { Icon(if (forum != null) iconFor(forum.icon) else Icons.Outlined.Topic, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) },
                        border = AssistChipDefaults.assistChipBorder(true,
                            borderColor = if (tried && chosenForum == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
                    )
                }
            }
            if (clinic) {
                AppCard(container = ExpertContainer.copy(alpha = 0.5f), border = Expert.copy(alpha = 0.25f)) {
                    Row {
                        Icon(VerifiedIcon, null, tint = Expert, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Dimens.s))
                        Text("به پرسش‌های کلینیک فقط متخصصانِ تأییدشده پاسخ می‌دهند. این پاسخ‌ها جایگزینِ ویزیتِ حضوری نیستند؛ " +
                            "سنِ کودک و شرایط را کامل بنویسید.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(
                title, { title = it.take(TITLE_MAX + 20) }, Modifier.fillMaxWidth(),
                label = { Text(if (clinic) "پرسشِ شما" else "عنوان") },
                placeholder = { Text(if (clinic) "پرسش را در یک جمله بنویسید" else "عنوانی روشن و کوتاه") },
                isError = tried && titleErr != null, maxLines = 3,
                supportingText = {
                    Row {
                        Text(if (tried) titleErr.orEmpty() else "", Modifier.weight(1f))
                        Text("${title.trim().length.toPersianDigits()}/${TITLE_MAX.toPersianDigits()}")
                    }
                },
            )
            OutlinedTextField(
                body, { body = it.take(BODY_MAX) }, Modifier.fillMaxWidth().heightIn(min = 160.dp),
                label = { Text(if (clinic) "شرحِ کامل" else "متن") },
                placeholder = { Text(if (clinic) "سنِ کودک، نشانه‌ها، درمان‌های فعلی و پرسشِ دقیقِ خود را بنویسید" else "تجربه، پرسش یا حرفِ خود را بنویسید") },
                isError = tried && bodyErr != null, minLines = 6,
                supportingText = if (tried && bodyErr != null) ({ Text(bodyErr) }) else null,
            )
            // images
            val pick = rememberImagePicker(uploads)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = pick, enabled = uploads.remaining > 0) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("افزودنِ تصویر")
                }
                Spacer(Modifier.width(Dimens.s))
                Text("${uploads.items.size.toPersianDigits()} از ${uploads.max.toPersianDigits()}", style = MaterialTheme.typography.labelMedium, color = muted())
            }
            AttachmentStrip(uploads, client, size = 76.dp)

            if (!editing) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (!inGroup) SwitchRow("ارسال به صورت ناشناس", "نام و تصویرِ شما به دیگران نشان داده نمی‌شود؛ فقط ناظران می‌بینند.", anonymous) { anonymous = it }
                if (!clinic) SwitchRow("افزودنِ نظرسنجی", "از دیگران با چند گزینه نظر بخواهید.", pollOn) { pollOn = it }
                if (pollOn && !clinic) PollEditor(
                    pollQ, { pollQ = it }, pollOptions, { pollOptions = ArrayList(it) }, pollMulti, { pollMulti = it }, pollDays, { pollDays = it },
                )
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }

    if (showPicker && tree != null) ForumPickerSheet(tree, chosenForum, onPick = { chosenForum = it.id; showPicker = false }) { showPicker = false }
    if (confirmLeave) ConfirmDialog(
        "نوشته ذخیره نشده", "اگر خارج شوید، متنی که نوشته‌اید از بین می‌رود.", "خروج",
        onConfirm = onBack, onDismiss = { confirmLeave = false },
    )
}
