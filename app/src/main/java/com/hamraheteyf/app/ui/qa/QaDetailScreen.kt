package com.hamraheteyf.app.ui.qa

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.Answer
import com.hamraheteyf.app.data.model.ContentType
import com.hamraheteyf.app.data.model.ReportReason
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaDetailScreen(questionId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: QaDetailViewModel = viewModel(factory = appViewModelFactory {
        QaDetailViewModel(questionId, container.questionRepository, container.userRepository, container.reportRepository, container.authRepository)
    })
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var answerText by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<ContentType, String>?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("پرسش") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "گزینه‌ها") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("گزارش پرسش") }, onClick = { menu = false; reportTarget = ContentType.QUESTION to questionId })
                        if (state.isAuthor) {
                            val q = state.question
                            DropdownMenuItem(
                                text = { Text(if (q?.commentsClosed == true) "بازکردن دیدگاه‌ها" else "بستن دیدگاه‌ها") },
                                onClick = { menu = false; vm.setClosed(!(q?.commentsClosed ?: false)) },
                            )
                            DropdownMenuItem(text = { Text("حذف پرسش") }, onClick = { menu = false; vm.deleteQuestion(onBack) })
                        }
                    }
                },
            )
        },
    ) { padding ->
        val q = state.question
        if (q == null) {
            EmptyState("این پرسش در دسترس نیست.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryChip(q.category.label)
                    Spacer(Modifier.width(8.dp))
                    state.questionAuthor?.badge?.let { AuthorBadgeChip(it) }
                }
            }
            item { Text(q.title, style = MaterialTheme.typography.titleLarge) }
            item { Text(q.body, style = MaterialTheme.typography.bodyLarge) }
            item {
                Text(
                    "${state.questionAuthor?.name ?: ""} · ${relativeTimeFa(q.createdAt)}",
                    style = MaterialTheme.typography.labelMedium, color = mutedColor(),
                )
            }
            item { InfoBanner("پاسخ‌های این بخش جایگزین تشخیص، درمان یا نسخه‌نویسی حرفه‌ای نیستند.") }
            item {
                Text("${q.answerCount.toPersianDigits()} پاسخ", style = MaterialTheme.typography.titleMedium)
            }

            if (state.answers.isEmpty()) {
                item { EmptyState("هنوز پاسخی ثبت نشده است.") }
            }
            items(state.answers, key = { it.first.id }) { (answer, author) ->
                AnswerItem(
                    answer = answer,
                    authorName = author.name,
                    badge = author.badge,
                    liked = state.currentUserId != null && state.currentUserId in answer.helpfulByUserIds,
                    onToggleHelpful = { vm.toggleHelpful(answer.id) },
                    onReport = { reportTarget = ContentType.ANSWER to answer.id },
                )
            }

            item {
                if (q.commentsClosed) {
                    Text("دیدگاه‌ها بسته شده است.", color = mutedColor(), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column {
                        OutlinedTextField(
                            value = answerText, onValueChange = { answerText = it },
                            label = { Text("پاسخ شما") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        BigPrimaryButton(text = "ارسال پاسخ", onClick = {
                            vm.answer(answerText); answerText = ""
                        }, enabled = answerText.isNotBlank())
                    }
                }
            }
        }
    }

    reportTarget?.let { (type, id) ->
        ReportDialog(
            onDismiss = { reportTarget = null },
            onSubmit = { reason: ReportReason, note ->
                vm.report(type, id, reason, note); reportTarget = null
            },
        )
    }
}

@Composable
private fun AnswerItem(
    answer: Answer,
    authorName: String,
    badge: com.hamraheteyf.app.data.model.AuthorBadge?,
    liked: Boolean,
    onToggleHelpful: () -> Unit,
    onReport: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(authorName, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                badge?.let { AuthorBadgeChip(it) }
            }
            Spacer(Modifier.height(6.dp))
            Text(answer.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onToggleHelpful,
                    leadingIcon = { Icon(Icons.Outlined.ThumbUp, null, Modifier.size(16.dp)) },
                    label = { Text("مفید (${answer.helpfulCount.toPersianDigits()})") },
                    colors = if (liked) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else AssistChipDefaults.assistChipColors(),
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReport) {
                    Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("گزارش")
                }
            }
        }
    }
}
