package com.hamraheteyf.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenQuestion: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onAskQuestion: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenGuides: () -> Unit,
    onOpenDirectory: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(factory = appViewModelFactory {
        HomeViewModel(container.userRepository, container.questionRepository, container.groupRepository, container.guideRepository, container.notificationRepository)
    })
    val user by vm.currentUser.collectAsState()
    val questions by vm.recentQuestions.collectAsState()
    val groups by vm.groups.collectAsState()
    val guides by vm.guides.collectAsState()
    val unread by vm.unreadCount.collectAsState()

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "سلام${user?.let { "، ${it.displayName}" } ?: ""}",
                actions = {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toPersianDigits()) } }) {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "اعلان‌ها", tint = Color.White)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Search entry (navigates to directory for people/specialists search).
            OutlinedCard(onClick = onOpenDirectory, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = mutedColor())
                    Spacer(Modifier.width(8.dp))
                    Text("جست‌وجوی متخصصان و موضوع‌ها", color = mutedColor())
                }
            }

            Spacer(Modifier.height(12.dp))
            SafetyBanner()

            Spacer(Modifier.height(16.dp))
            Row {
                BigPrimaryButton(text = "پرسش بپرس", onClick = onAskQuestion, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onOpenGroups, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("گروه‌ها") }
            }

            SectionHeader("پرسش‌های تازه")
            if (questions.isEmpty()) EmptyState("هنوز پرسشی ثبت نشده است.")
            questions.forEach { (q, author) ->
                QuestionCard(q, author) { onOpenQuestion(q.id) }
                Spacer(Modifier.height(10.dp))
            }

            SectionHeader("گروه‌های فعال")
            if (groups.isEmpty()) EmptyState("گروهی برای نمایش وجود ندارد.")
            groups.take(3).forEach { g ->
                GroupCard(g) { onOpenGroup(g.id) }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("راهنماهای منتخب", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenGuides) { Text("همه") }
            }
            Spacer(Modifier.height(8.dp))
            if (guides.isEmpty()) EmptyState("راهنمایی برای نمایش وجود ندارد.")
            guides.take(2).forEach { g ->
                GuideCard(g) { onOpenGuide(g.id) }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}
