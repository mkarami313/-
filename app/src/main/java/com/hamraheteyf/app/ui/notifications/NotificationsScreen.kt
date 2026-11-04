package com.hamraheteyf.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.Notification
import com.hamraheteyf.app.data.model.NotificationType
import com.hamraheteyf.app.data.repository.NotificationRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.relativeTimeFa
import com.hamraheteyf.app.ui.kit.*
import com.hamraheteyf.app.ui.navigation.Routes
import com.hamraheteyf.app.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class NotificationsViewModel(private val repo: NotificationRepository) : ViewModel() {
    val items: StateFlow<List<Notification>?> = repo.observeNotifications()
        .map { l -> l.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun markRead(id: String) = viewModelScope.launch { repo.markRead(id) }
    fun markAllRead() = viewModelScope.launch { repo.markAllRead() }
}

/**
 * Notification inbox, grouped «امروز» / «پیش‌تر». Tapping marks the item read and, when it points
 * somewhere ([Routes.forNotification]), opens that place through [onOpen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val container = LocalAppContainer.current
    val vm: NotificationsViewModel = viewModel(factory = appViewModelFactory { NotificationsViewModel(container.notificationRepository) })
    val items by vm.items.collectAsState()
    val unread = items.orEmpty().count { !it.read }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                AppTopBar(
                    title = "اعلان‌ها", onBack = onBack,
                    actions = {
                        if (unread > 0) TextButton(onClick = { vm.markAllRead() }) {
                            Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("همه خوانده شد")
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        val list = items
        when {
            list == null -> LoadingView(Modifier.padding(padding))
            list.isEmpty() -> EmptyView(Icons.Outlined.NotificationsNone, "اعلانی ندارید", "پاسخ‌ها، پیام‌ها و اطلاعیه‌ها این‌جا نشان داده می‌شوند.", modifier = Modifier.padding(padding))
            else -> {
                val startOfToday = remember {
                    Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                }
                val today = list.filter { it.createdAt >= startOfToday }
                val earlier = list.filter { it.createdAt < startOfToday }
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = Dimens.l)) {
                    listOf("امروز" to today, "پیش‌تر" to earlier).forEach { (title, group) ->
                        if (group.isNotEmpty()) {
                            item(key = "h_$title") {
                                Text(title, style = MaterialTheme.typography.titleSmall, color = muted(),
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = Dimens.gutter, vertical = Dimens.s))
                            }
                            itemsIndexed(group, key = { _, n -> n.id }) { i, n ->
                                NotificationRow(n) {
                                    if (!n.read) vm.markRead(n.id)
                                    Routes.forNotification(n.type.name, n.relatedId)?.let(onOpen)
                                }
                                if (i < group.lastIndex) InsetDivider(start = Dimens.gutter + 40.dp + Dimens.m)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: Notification, onClick: () -> Unit) {
    val (icon, color) = visualFor(n.type)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .heightIn(min = 64.dp)
            .padding(horizontal = Dimens.gutter, vertical = Dimens.m),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(Dimens.m))
        Column(Modifier.weight(1f)) {
            Text(
                n.title, style = MaterialTheme.typography.titleSmall,
                fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (n.body.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = muted(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(n.type.label, relativeTimeFa(n.createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = muted(),
            )
        }
        if (!n.read) {
            Spacer(Modifier.width(Dimens.s))
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

private fun visualFor(type: NotificationType): Pair<ImageVector, Color> = when (type) {
    NotificationType.QUESTION_ANSWERED -> Icons.Outlined.QuestionAnswer to Expert
    NotificationType.NEW_MESSAGE -> Icons.Outlined.Mail to Teal
    NotificationType.GROUP_JOIN_REQUEST -> Icons.Outlined.GroupAdd to Apricot
    NotificationType.GROUP_REPLY -> Icons.Outlined.Groups to Apricot
    NotificationType.VERIFICATION_RESULT -> Icons.Outlined.Verified to VerifiedGreen
    NotificationType.SAFETY_NOTICE -> Icons.Outlined.Shield to Danger
    NotificationType.DONATION -> Icons.Outlined.VolunteerActivism to Like
    NotificationType.BOOKING -> Icons.Outlined.EventAvailable to Gold
}
