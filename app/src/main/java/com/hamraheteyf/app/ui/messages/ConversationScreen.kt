package com.hamraheteyf.app.ui.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.ConversationStatus
import com.hamraheteyf.app.data.model.Message
import com.hamraheteyf.app.data.model.ReportReason
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.ReportDialog
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Expert

/**
 * One conversation. Restarting an ended conversation may hand back a new id from the server, so the
 * screen keeps the active id itself and re-keys its ViewModel when it changes.
 */
@Composable
fun ConversationScreen(conversationId: String, onBack: () -> Unit) {
    var activeId by rememberSaveable(conversationId) { mutableStateOf(conversationId) }
    key(activeId) { ConversationContent(activeId, onBack, onSwitch = { activeId = it }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationContent(conversationId: String, onBack: () -> Unit, onSwitch: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: ConversationViewModel = viewModel(key = "conv_$conversationId", factory = appViewModelFactory {
        ConversationViewModel(conversationId, container.conversationRepository, container.userRepository, container.reportRepository)
    })
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val sending by vm.sending.collectAsState()
    val busy by vm.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var text by rememberSaveable(conversationId) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val lastId = state.messages.lastOrNull()?.id
    // Scroll to the newest message and tell the server I've seen it (on open and whenever one arrives).
    // notice + day separators + messages (must mirror the LazyColumn below)
    val lastIndex = state.messages.let { msgs ->
        msgs.size + msgs.indices.count { i -> i == 0 || !sameDay(msgs[i - 1].createdAt, msgs[i].createdAt) }
    }
    LaunchedEffect(lastId, state.conversation != null) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(lastIndex)
        vm.markRead()
    }

    val conv = state.conversation
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AppTopBar(
                    title = state.otherName.ifBlank { "گفتگو" },
                    subtitle = when {
                        conv == null -> null
                        conv.status == ConversationStatus.ACCEPTED -> state.otherBadge?.label
                        else -> conv.status.label
                    },
                    onBack = onBack,
                    actions = {
                        if (state.isMuted) Icon(Icons.Outlined.NotificationsOff, "بی‌صدا", tint = muted(), modifier = Modifier.size(18.dp))
                        if (conv != null) Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "گزینه‌ها") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (state.isMuted) "لغوِ بی‌صدا" else "بی‌صدا کردن") }, onClick = { menu = false; vm.toggleMute() })
                                DropdownMenuItem(text = { Text("گزارشِ کاربر") }, onClick = { menu = false; showReport = true })
                                DropdownMenuItem(
                                    text = { Text(if (state.otherBlocked) "برداشتنِ مسدودیت" else "مسدودکردنِ کاربر") },
                                    onClick = { menu = false; if (state.otherBlocked) vm.unblockOther() else confirmBlock = true },
                                )
                                if (conv.status == ConversationStatus.ACCEPTED || conv.status == ConversationStatus.REQUESTED) {
                                    DropdownMenuItem(
                                        text = { Text("پایانِ گفتگو", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menu = false; confirmEnd = true },
                                    )
                                }
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            if (conv != null) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    when (conv.status) {
                        ConversationStatus.REQUESTED -> if (state.isInitiator) {
                            FooterNote("درخواستِ شما فرستاده شد؛ در انتظارِ پذیرشِ ${state.otherName} هستید.")
                        } else {
                            Column(Modifier.padding(Dimens.gutter)) {
                                Text("${state.otherName} می‌خواهد با شما گفتگو کند.", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(Dimens.s))
                                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                                    Button(onClick = { vm.accept() }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = Dimens.touch)) { Text("پذیرشِ گفتگو") }
                                    OutlinedButton(onClick = { vm.decline() }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = Dimens.touch)) { Text("رد") }
                                }
                            }
                        }
                        ConversationStatus.ACCEPTED -> if (state.otherBlocked) {
                            FooterNote("این کاربر را مسدود کرده‌اید؛ تا برداشتنِ مسدودیت پیامی فرستاده نمی‌شود.")
                        } else {
                            Composer(
                                text = text, onText = { text = it }, sending = sending,
                                // Clear only what was sent: text typed while the send was in flight is kept.
                                onSend = { val sent = text; vm.send(sent) { if (text == sent) text = "" } },
                            )
                        }
                        ConversationStatus.ENDED -> Column(Modifier.padding(Dimens.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("این گفتگو پایان یافته است.", style = MaterialTheme.typography.bodyMedium, color = muted())
                            Spacer(Modifier.height(Dimens.s))
                            Button(
                                onClick = { vm.restart { id -> if (id != conversationId) onSwitch(id) } },
                                enabled = !busy && state.otherUserId != null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touch),
                            ) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("شروعِ دوبارهٔ گفتگو")
                            }
                        }
                        else -> FooterNote(conv.status.label)
                    }
                }
            }
        },
    ) { padding ->
        when {
            !state.loaded -> LoadingView(Modifier.padding(padding))
            conv == null -> EmptyView(Icons.Outlined.Forum, "این گفتگو در دسترس نیست", "ممکن است حذف شده باشد یا به آن دسترسی نداشته باشید.",
                action = "بازگشت", onAction = onBack, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = Dimens.s),
            ) {
                item(key = "notice") {
                    SafetyNote(
                        if (state.otherVerifiedProfessional) "${state.otherName} «متخصصِ تأییدشده» است. گفتگو جایگزینِ ویزیت، تشخیص یا درمانِ حرفه‌ای نیست."
                        else "پیام‌رسانی برای وضعیتِ اورژانسی یا تشخیصِ پزشکی نیست.",
                    )
                }
                if (state.messages.isEmpty()) item(key = "empty") {
                    Text(
                        if (conv.status == ConversationStatus.ACCEPTED) "هنوز پیامی نیست؛ اولین پیام را بفرستید." else "هنوز پیامی رد و بدل نشده است.",
                        style = MaterialTheme.typography.bodyMedium, color = muted(), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.xl),
                    )
                }
                val msgs = state.messages
                msgs.forEachIndexed { i, m ->
                    val prev = msgs.getOrNull(i - 1)
                    if (prev == null || !sameDay(prev.createdAt, m.createdAt)) {
                        item(key = "day_${m.id}") { DaySeparator(dayLabel(m.createdAt)) }
                    }
                    val mine = m.senderId == state.currentUserId
                    val groupedWithPrev = prev != null && prev.senderId == m.senderId && sameDay(prev.createdAt, m.createdAt) &&
                        m.createdAt - prev.createdAt < 5 * 60_000L
                    item(key = m.id) {
                        MessageBubble(m, mine, Modifier.padding(top = if (groupedWithPrev) 2.dp else Dimens.s))
                    }
                }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("پایانِ گفتگو؟") },
            text = { Text("پس از پایان، هیچ‌کدام از شما نمی‌توانید در این گفتگو پیام بفرستید. بعداً می‌توانید دوباره درخواستِ گفتگو بدهید.") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; vm.end() }) { Text("پایان داده شود", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("انصراف") } },
        )
    }
    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("مسدودکردنِ ${state.otherName}؟") },
            text = { Text("این کاربر دیگر نمی‌تواند به شما پیام بدهد و گفتگو برای شما بسته می‌شود. هر زمان می‌توانید مسدودیت را بردارید.") },
            confirmButton = { TextButton(onClick = { confirmBlock = false; vm.blockOther() }) { Text("مسدود شود", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("انصراف") } },
        )
    }
    if (showReport) {
        ReportDialog(onDismiss = { showReport = false }, onSubmit = { reason: ReportReason, note ->
            vm.report(reason, note); showReport = false
        })
    }
}

@Composable
private fun Composer(text: String, onText: (String) -> Unit, sending: Boolean, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.s, vertical = Dimens.s), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = text, onValueChange = onText,
            placeholder = { Text("پیام بنویسید…") },
            maxLines = 5,
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Dimens.s))
        FilledIconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !sending,
            modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
        ) {
            if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.AutoMirrored.Filled.Send, "ارسال")
        }
    }
}

@Composable
private fun FooterNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = muted(), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.l))
}

@Composable
private fun SafetyNote(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.xs).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = Dimens.m, vertical = Dimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, null, tint = Expert, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Dimens.s))
        Text(text, style = MaterialTheme.typography.labelMedium, color = muted())
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(top = Dimens.m, bottom = Dimens.xs), contentAlignment = Alignment.Center) {
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = muted(),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Mine sits on the far (end) side in the primary container; theirs on the start side, white with a hairline. */
@Composable
private fun MessageBubble(message: Message, isMine: Boolean, modifier: Modifier = Modifier) {
    val shape = if (isMine) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            shape = shape,
            border = if (isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(start = Dimens.m, end = Dimens.m, top = Dimens.s, bottom = 6.dp)) {
                Text(message.body, style = MaterialTheme.typography.bodyLarge)
                Text(
                    clockFa(message.createdAt), style = MaterialTheme.typography.labelSmall, color = muted(),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
