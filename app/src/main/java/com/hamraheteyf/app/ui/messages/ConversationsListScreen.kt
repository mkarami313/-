package com.hamraheteyf.app.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.Conversation
import com.hamraheteyf.app.data.repository.AuthRepository
import com.hamraheteyf.app.data.repository.ConversationRepository
import com.hamraheteyf.app.data.repository.UserRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.EmptyState
import com.hamraheteyf.app.ui.common.InfoBanner
import com.hamraheteyf.app.ui.common.mutedColor
import kotlinx.coroutines.flow.*

data class ConversationRow(val conversation: Conversation, val otherName: String)

class ConversationsListViewModel(
    convRepo: ConversationRepository,
    userRepo: UserRepository,
    auth: AuthRepository,
) : ViewModel() {
    val rows: StateFlow<List<ConversationRow>> = combine(
        convRepo.observeConversations(), userRepo.observeUsers(), auth.currentUserId,
    ) { conversations, users, me ->
        conversations.map { c ->
            val otherId = c.participantIds.firstOrNull { it != me }
            ConversationRow(c, users.find { it.id == otherId }?.displayName ?: "کاربر")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(onOpen: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: ConversationsListViewModel = viewModel(factory = appViewModelFactory {
        ConversationsListViewModel(container.conversationRepository, container.userRepository, container.authRepository)
    })
    val rows by vm.rows.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("پیام‌ها") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            InfoBanner("پیام‌رسانی برای وضعیت اورژانسی یا تشخیص پزشکی نیست.", Modifier.padding(16.dp))
            if (rows.isEmpty()) {
                EmptyState("هنوز گفتگویی ندارید. می‌توانید از صفحهٔ متخصصان درخواست گفتگو بفرستید.")
            } else {
                LazyColumn {
                    items(rows, key = { it.conversation.id }) { row ->
                        ListItem(
                            headlineContent = { Text(row.otherName) },
                            supportingContent = {
                                Text(
                                    row.conversation.lastMessagePreview ?: row.conversation.status.label,
                                    color = mutedColor(), maxLines = 1,
                                )
                            },
                            trailingContent = { Text(row.conversation.status.label, style = MaterialTheme.typography.labelMedium, color = mutedColor()) },
                            modifier = Modifier.clickable { onOpen(row.conversation.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
