package com.hamraheteyf.app.ui.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.ConversationStatus
import com.hamraheteyf.app.data.model.Message
import com.hamraheteyf.app.data.model.ReportReason
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(conversationId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: ConversationViewModel = viewModel(factory = appViewModelFactory {
        ConversationViewModel(conversationId, container.conversationRepository, container.userRepository, container.reportRepository)
    })
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var text by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1) }

    val conv = state.conversation
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.otherName, style = MaterialTheme.typography.titleMedium)
                        state.otherBadge?.let { Text(it.label, style = MaterialTheme.typography.labelMedium, color = mutedColor()) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "گزینه‌ها") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (state.isMuted) "لغو بی‌صدا" else "بی‌صدا کردن") }, onClick = { menu = false; vm.toggleMute() })
                        DropdownMenuItem(text = { Text("گزارش کاربر") }, onClick = { menu = false; showReport = true })
                        DropdownMenuItem(
                            text = { Text(if (state.otherBlocked) "برداشتن مسدودیت" else "مسدودکردن کاربر") },
                            onClick = { menu = false; if (state.otherBlocked) vm.unblockOther() else confirmBlock = true },
                        )
                        DropdownMenuItem(text = { Text("پایان گفتگو") }, onClick = { menu = false; vm.end() })
                    }
                },
            )
        },
    ) { padding ->
        if (conv == null) {
            EmptyState("این گفتگو در دسترس نیست.", Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.otherVerifiedProfessional) {
                InfoBanner("این فرد «متخصص تأییدشده» است. گفتگو جایگزین ویزیت، تشخیص یا درمان حرفه‌ای نیست.", Modifier.padding(12.dp))
            } else {
                InfoBanner("پیام‌رسانی برای وضعیت اورژانسی یا تشخیص پزشکی نیست.", Modifier.padding(12.dp))
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg, isMine = msg.senderId == state.currentUserId)
                }
            }

            when (conv.status) {
                ConversationStatus.REQUESTED -> {
                    if (state.isInitiator) {
                        Text("در انتظار پذیرش طرف مقابل…", Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = mutedColor())
                    } else {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            Button(onClick = { vm.accept() }, modifier = Modifier.weight(1f)) { Text("پذیرش گفتگو") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { vm.decline() }, modifier = Modifier.weight(1f)) { Text("رد") }
                        }
                    }
                }
                ConversationStatus.ACCEPTED -> if (state.otherBlocked) {
                    Text("این کاربر را مسدود کرده‌اید؛ تا برداشتن مسدودیت پیامی ارسال نمی‌شود.", Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = mutedColor())
                } else {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("پیام…") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { if (text.isNotBlank()) { vm.send(text); text = "" } }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "ارسال")
                        }
                    }
                }
                else -> Text(conv.status.label, Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = mutedColor())
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("مسدودکردن ${state.otherName}؟") },
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
private fun MessageBubble(message: Message, isMine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(message.body, style = MaterialTheme.typography.bodyMedium)
                Text(relativeTimeFa(message.createdAt), style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }
        }
    }
}
