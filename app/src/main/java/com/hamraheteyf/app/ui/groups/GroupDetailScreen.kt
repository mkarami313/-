package com.hamraheteyf.app.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.ContentType
import com.hamraheteyf.app.data.model.GroupVisibility
import com.hamraheteyf.app.data.model.ReportReason
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(groupId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: GroupDetailViewModel = viewModel(factory = appViewModelFactory {
        GroupDetailViewModel(groupId, container.groupRepository, container.userRepository, container.reportRepository)
    })
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var postText by rememberSaveable { mutableStateOf("") }
    var reportPostId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.group?.title ?: "گروه") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
            )
        },
    ) { padding ->
        val group = state.group
        if (group == null) {
            EmptyState("این گروه خصوصی است یا در دسترس نیست.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(group.description, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                val meta = buildList {
                    add(group.visibility.label); group.category?.let { add(it) }; group.city?.let { add(it) }
                    add("${group.memberCount.toPersianDigits()} عضو")
                }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = mutedColor())
            }
            group.rules?.let {
                item { InfoBanner("قوانین گروه: $it") }
            }

            // Membership action
            item {
                if (!state.isMember) {
                    BigPrimaryButton(
                        text = if (group.visibility == GroupVisibility.PUBLIC) "پیوستن به گروه" else "درخواست عضویت",
                        onClick = { vm.join() },
                    )
                } else {
                    OutlinedButton(onClick = { vm.leave() }, modifier = Modifier.fillMaxWidth()) { Text("خروج از گروه") }
                }
            }

            // Management section
            if (state.canManage && state.pendingRequests.isNotEmpty()) {
                item { Text("درخواست‌های عضویت", style = MaterialTheme.typography.titleMedium) }
                items(state.pendingRequests, key = { it.membership.id }) { req ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(req.displayName, Modifier.weight(1f))
                            TextButton(onClick = { vm.approve(req.membership.userId) }) { Text("تأیید") }
                            TextButton(onClick = { vm.remove(req.membership.userId) }) { Text("رد") }
                        }
                    }
                }
            }

            // Posts
            item { Text("گفت‌وگوها", style = MaterialTheme.typography.titleMedium) }
            if (state.isMember) {
                item {
                    Column {
                        OutlinedTextField(value = postText, onValueChange = { postText = it }, label = { Text("نوشتن پست") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
                        Spacer(Modifier.height(6.dp))
                        BigPrimaryButton(text = "ارسال پست", onClick = { vm.createPost(postText); postText = "" }, enabled = postText.isNotBlank())
                    }
                }
            } else if (group.visibility == GroupVisibility.PRIVATE) {
                item { EmptyState("برای دیدن گفت‌وگوها باید عضو این گروه خصوصی باشید.") }
            }

            if (state.isMember || group.visibility == GroupVisibility.PUBLIC) {
                if (state.posts.isEmpty()) item { EmptyState("هنوز پستی ثبت نشده است.") }
                items(state.posts, key = { it.first.id }) { (post, author) ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(author.name, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(8.dp))
                                author.badge?.let { AuthorBadgeChip(it) }
                                Spacer(Modifier.weight(1f))
                                Text(relativeTimeFa(post.createdAt), style = MaterialTheme.typography.labelMedium, color = mutedColor())
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(post.body, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Row {
                                TextButton(onClick = { reportPostId = post.id }) { Text("گزارش") }
                                if (state.currentUserId == post.authorId || state.canManage) {
                                    TextButton(onClick = { vm.deletePost(post.id) }) { Text("حذف") }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    reportPostId?.let { id ->
        ReportDialog(onDismiss = { reportPostId = null }, onSubmit = { reason: ReportReason, note ->
            vm.reportPost(id, reason, note); reportPostId = null
        })
    }
}
