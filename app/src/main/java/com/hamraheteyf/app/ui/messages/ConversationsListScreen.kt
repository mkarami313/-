package com.hamraheteyf.app.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.Conversation
import com.hamraheteyf.app.data.model.ConversationStatus
import com.hamraheteyf.app.data.repository.AuthRepository
import com.hamraheteyf.app.data.repository.ConversationRepository
import com.hamraheteyf.app.data.repository.UserRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.theme.Dimens
import com.hamraheteyf.app.ui.theme.Danger
import kotlinx.coroutines.flow.*

data class ConversationRow(
    val conversation: Conversation,
    val otherName: String,
    val otherPhoto: String?,
    val unread: Int,
    val muted: Boolean,
    val waitingForMe: Boolean,
)

class ConversationsListViewModel(
    convRepo: ConversationRepository,
    userRepo: UserRepository,
    auth: AuthRepository,
) : ViewModel() {
    val rows: StateFlow<List<ConversationRow>?> = combine(
        convRepo.observeConversations(), userRepo.observeUsers(), auth.currentUserId, userRepo.observeCurrentUser(),
        unreadByConversation(convRepo, auth.currentUserId),
    ) { conversations, users, me, meUser, unread ->
        val blocked = meUser?.blockedUserIds.orEmpty()
        conversations.mapNotNull { c ->
            val otherId = c.participantIds.firstOrNull { it != me }
            if (otherId != null && otherId in blocked) return@mapNotNull null
            val other = users.find { it.id == otherId }
            ConversationRow(
                conversation = c,
                otherName = other?.displayName ?: "کاربر",
                otherPhoto = other?.photoUrl,
                unread = unread[c.id] ?: 0,
                muted = me != null && me in c.mutedByUserIds,
                waitingForMe = c.status == ConversationStatus.REQUESTED && c.initiatorId != me,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(onOpen: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: ConversationsListViewModel = viewModel(factory = appViewModelFactory {
        ConversationsListViewModel(container.conversationRepository, container.userRepository, container.authRepository)
    })
    val rows by vm.rows.collectAsState()
    val totalUnread = rows.orEmpty().count { it.unread > 0 }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    title = "پیام‌ها",
                    subtitle = if (totalUnread > 0) "${totalUnread.toPersianDigits()} گفتگوی خوانده‌نشده" else null,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val list = rows
        when {
            list == null -> LoadingView(Modifier.padding(padding))
            list.isEmpty() -> EmptyView(
                Icons.Outlined.Forum, "هنوز گفتگویی ندارید",
                "از صفحهٔ هر متخصص یا عضو می‌توانید درخواستِ گفتگو بفرستید.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = Dimens.l)) {
                itemsIndexed(list, key = { _, r -> r.conversation.id }) { i, row ->
                    ConversationRowItem(row) { onOpen(row.conversation.id) }
                    if (i < list.lastIndex) InsetDivider(start = Dimens.gutter + 48.dp + Dimens.m)
                }
                item {
                    Text(
                        "پیام‌رسانی برای وضعیتِ اورژانسی یا تشخیصِ پزشکی نیست.",
                        style = MaterialTheme.typography.labelSmall, color = muted(), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.l),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRowItem(row: ConversationRow, onClick: () -> Unit) {
    val c = row.conversation
    val hasUnread = row.unread > 0
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = Dimens.gutter, vertical = Dimens.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(row.otherName, row.otherPhoto, size = 48.dp)
        Spacer(Modifier.width(Dimens.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.otherName, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (row.muted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.NotificationsOff, "بی‌صدا", tint = muted(), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.weight(1f))
                val t = c.updatedAt.takeIf { it > 0 } ?: c.createdAt
                if (t > 0) Text(
                    relativeTimeFa(t), style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary else muted(),
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = when {
                    row.waitingForMe -> "درخواستِ گفتگوی تازه — برای پاسخ بزنید"
                    !c.lastMessagePreview.isNullOrBlank() -> c.lastMessagePreview
                    c.status == ConversationStatus.REQUESTED -> "در انتظارِ پذیرش"
                    else -> "هنوز پیامی نیست"
                }
                Text(
                    preview, style = MaterialTheme.typography.bodyMedium,
                    color = if (hasUnread || row.waitingForMe) MaterialTheme.colorScheme.onSurface else muted(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                StatusPill(c.status)
                if (hasUnread) {
                    Spacer(Modifier.width(6.dp))
                    UnreadCount(row.unread)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ConversationStatus) {
    when (status) {
        ConversationStatus.REQUESTED -> { Spacer(Modifier.width(6.dp)); Pill("درخواست", color = MaterialTheme.colorScheme.secondary) }
        ConversationStatus.ENDED -> { Spacer(Modifier.width(6.dp)); Pill("پایان‌یافته", color = muted(), container = MaterialTheme.colorScheme.surfaceVariant) }
        ConversationStatus.BLOCKED -> { Spacer(Modifier.width(6.dp)); Pill("مسدود", color = Danger) }
        else -> Unit
    }
}

@Composable
internal fun UnreadCount(n: Int) {
    Box(
        Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (n > 99) "۹۹+" else n.toPersianDigits(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}
