package com.hamraheteyf.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamraheteyf.app.ui.onboarding.OnboardingPrefs
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.di.appViewModelFactory
import com.hamraheteyf.app.ui.common.*
import com.hamraheteyf.app.ui.navigation.AppFeature
import com.hamraheteyf.app.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    tiles: List<AppFeature>,
    onNavigate: (String) -> Unit,
    onOpenQuestion: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
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
    val ctx = LocalContext.current
    val interests = remember(user?.id) { user?.id?.let { OnboardingPrefs.interests(ctx, it) }.orEmpty() }
    val grouped = remember(tiles, interests) { groupTiles(tiles, interests) }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "سلام${user?.let { "، ${it.displayName}" } ?: ""}",
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SEARCH) }) {
                        Icon(Icons.Outlined.Search, contentDescription = "جست‌وجو", tint = Color.White)
                    }
                    BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toPersianDigits()) } }) {
                        IconButton(onClick = { onNavigate(Routes.NOTIFICATIONS) }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "اعلان‌ها", tint = Color.White)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = { onNavigate(Routes.SEARCH) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = mutedColor(), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("جست‌وجو در همه‌جا: پرسش، گروه، دوره، مرکز…", style = MaterialTheme.typography.bodyMedium, color = mutedColor())
                }
            }

            ActionTile("پرسش بپرس", Icons.Outlined.Edit, primary = true, onClick = { onNavigate(Routes.QA_CREATE) }, modifier = Modifier.fillMaxWidth())
            grouped.forEach { (title, items) ->
                Text(title, style = MaterialTheme.typography.titleSmall, color = mutedColor(), modifier = Modifier.padding(top = 4.dp))
                items.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { f ->
                            ActionTile(f.title, f.icon, onClick = { onNavigate(f.entryRoute) }, modifier = Modifier.weight(1f))
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SafetyBanner()

            SectionHeader("پرسش‌های تازه", "همه", { onNavigate(Routes.QA) })
            if (questions.isEmpty()) EmptyHint("هنوز پرسشی ثبت نشده است. اولین پرسش را شما بپرسید.")
            questions.take(4).forEach { (q, author) -> QuestionCard(q, author) { onOpenQuestion(q.id) } }

            SectionHeader("گروه‌های فعال", "همه", { onNavigate(Routes.GROUPS) })
            if (groups.isEmpty()) EmptyHint("هنوز گروهی ساخته نشده است.")
            groups.take(3).forEach { g -> GroupCard(g) { onOpenGroup(g.id) } }

            SectionHeader("راهنماهای منتخب", "همه", { onNavigate(Routes.GUIDES) })
            if (guides.isEmpty()) EmptyHint("هنوز راهنمایی منتشر نشده است.")
            guides.take(2).forEach { g -> GuideCard(g) { onOpenGuide(g.id) } }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val CHILD_IDS = setOf("childfile", "booking")
private val LEARN_IDS = setOf("courses", "guides")
private val COMMUNITY_IDS = setOf("qa", "groups", "messages", "donations", "directory", "places")
private val TOOL_IDS = setOf("tools")

/** Splits home tiles into «کودک من / یادگیری / جامعه / ابزارها» (+ «سایر» for modules added later); the user's chosen interests come first. */
private fun groupTiles(tiles: List<AppFeature>, interests: Set<String>): List<Pair<String, List<AppFeature>>> {
    fun sorted(l: List<AppFeature>) = l.sortedWith(compareBy({ if (it.id in interests) 0 else 1 }, { it.order }))
    val known = CHILD_IDS + LEARN_IDS + COMMUNITY_IDS + TOOL_IDS
    val groups = listOf(
        "کودک من" to sorted(tiles.filter { it.id in CHILD_IDS }),
        "یادگیری" to sorted(tiles.filter { it.id in LEARN_IDS }),
        "جامعه" to sorted(tiles.filter { it.id in COMMUNITY_IDS }),
        "ابزارها" to sorted(tiles.filter { it.id in TOOL_IDS }),
        "سایر" to sorted(tiles.filter { it.id !in known }),
    ).filter { it.second.isNotEmpty() }
    return if (interests.isEmpty()) groups else groups.sortedBy { (_, l) -> if (l.any { it.id in interests }) 0 else 1 }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val fg = if (primary) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = bg, modifier = modifier.heightIn(min = 46.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = mutedColor(), modifier = Modifier.padding(14.dp))
    }
}
