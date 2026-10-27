package com.hamraheteyf.app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.data.model.Notification
import com.hamraheteyf.app.data.repository.NotificationRepository
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.EmptyState
import com.hamraheteyf.app.ui.common.mutedColor
import com.hamraheteyf.app.ui.common.relativeTimeFa
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repo: NotificationRepository) : ViewModel() {
    val items: StateFlow<List<Notification>> = repo.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun markRead(id: String) = viewModelScope.launch { repo.markRead(id) }
    fun markAllRead() = viewModelScope.launch { repo.markAllRead() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: NotificationsViewModel = viewModel(factory = appViewModelFactory { NotificationsViewModel(container.notificationRepository) })
    val items by vm.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اعلان‌ها") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = { TextButton(onClick = { vm.markAllRead() }) { Text("خواندن همه") } },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState("اعلانی ندارید.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(items, key = { it.id }) { n ->
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = if (n.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        headlineContent = { Text(n.title) },
                        supportingContent = { Text(n.body, color = mutedColor()) },
                        trailingContent = { Text(relativeTimeFa(n.createdAt), style = MaterialTheme.typography.labelMedium, color = mutedColor()) },
                        modifier = Modifier.clickable { if (!n.read) vm.markRead(n.id) },
                    )
                    HorizontalDivider(color = Color.Transparent)
                }
            }
        }
    }
}
