package com.hamraheteyf.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hamraheteyf.app.di.LocalAppContainer
import com.hamraheteyf.app.push.PendingDeepLink
import com.hamraheteyf.app.ui.auth.LoginScreen
import com.hamraheteyf.app.ui.auth.RegisterScreen
import com.hamraheteyf.app.ui.auth.ResetPasswordScreen
import com.hamraheteyf.app.ui.common.toPersianDigits
import com.hamraheteyf.app.ui.messages.rememberUnreadConversationCount
import com.hamraheteyf.app.ui.onboarding.OnboardingPrefs
import com.hamraheteyf.app.ui.modules.EmergencyCardPublicScreen
import com.hamraheteyf.app.ui.navigation.AppFeatures
import com.hamraheteyf.app.ui.navigation.FeatureFlags
import com.hamraheteyf.app.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val container = LocalAppContainer.current
    val userId by container.authRepository.currentUserId.collectAsState()
    if (userId == null) AuthNavHost() else { MainNavHost(); com.hamraheteyf.app.update.UpdateHost() }
}

@Composable
private fun AuthNavHost() {
    val nav = rememberNavController()
    var resetEmail by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    NavHost(nav, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onGoToRegister = { nav.navigate(Routes.REGISTER) },
                onGoToReset = { email -> resetEmail = email; nav.navigate(Routes.RESET) },
                onEmergencyCard = { nav.navigate(Routes.EMERGENCY_CARD_PUBLIC) },
            )
        }
        composable(Routes.EMERGENCY_CARD_PUBLIC) { EmergencyCardPublicScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.RESET) { ResetPasswordScreen(onBack = { nav.popBackStack() }, initialEmail = resetEmail) }
        composable(Routes.REGISTER) { RegisterScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val disabled by FeatureFlags.disabled.collectAsState()
    val tabs = AppFeatures.enabled(disabled).filter { it.bottomTab }
    val ctx = LocalContext.current
    val uid by LocalAppContainer.current.authRepository.currentUserId.collectAsState()
    val start = remember(uid) { if (uid != null && !OnboardingPrefs.isDone(ctx, uid!!)) Routes.ONBOARDING else Routes.HOME }
    val unreadConversations by rememberUnreadConversationCount()

    // «من متخصص هستم» chosen at sign-up / onboarding: continue straight into verification once on Home.
    LaunchedEffect(currentRoute) {
        if (currentRoute == Routes.HOME && OnboardingPrefs.takePendingPro(ctx)) nav.navigate(Routes.VERIFICATION)
    }

    // A tapped system notification: open its target once the graph is up (and onboarding is done).
    val deepLink by PendingDeepLink.target.collectAsState()
    val notificationRepo = LocalAppContainer.current.notificationRepository
    val deepLinkScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(deepLink, currentRoute) {
        val t = deepLink ?: return@LaunchedEffect
        if (currentRoute == null || currentRoute == Routes.ONBOARDING) return@LaunchedEffect
        PendingDeepLink.consume()
        // Opening a notification from the tray reads it: without this it stayed unread in the inbox (and the badge).
        t.notifId?.takeIf { it.isNotBlank() }?.let { id -> deepLinkScope.launch { runCatching { notificationRepo.markRead(id) } } }
        val route = Routes.forNotification(t.type, t.relatedId) ?: Routes.NOTIFICATIONS
        runCatching { nav.navigate(route) { launchSingleTop = true } }
    }

    Scaffold(
        // Screens own their insets (their top bars pad the status bar); the shell only reserves the tab bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in tabs.map { it.entryRoute }) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        tabs.forEach { f ->
                            val selected = currentRoute == f.entryRoute
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(f.entryRoute) {
                                        // HOME is the root of the tab stack. After onboarding the graph's start
                                        // destination (ONBOARDING) is no longer on the back stack, so popping up to
                                        // it was a no-op and every tab tap piled another screen onto the stack.
                                        if (runCatching { nav.getBackStackEntry(Routes.HOME) }.isSuccess) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                        } else {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val icon = tabIcon(f.id, selected) ?: f.icon
                                    if (f.id == "messages" && unreadConversations > 0) {
                                        BadgedBox(badge = {
                                            Badge { Text(if (unreadConversations > 99) "۹۹+" else unreadConversations.toPersianDigits()) }
                                        }) { Icon(icon, contentDescription = f.title) }
                                    } else {
                                        Icon(icon, contentDescription = f.title)
                                    }
                                },
                                label = {
                                    Text(
                                        f.title, maxLines = 1, softWrap = false,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            AppFeatures.all.forEach { f -> f.routes(this, nav) }
        }
    }
}

/** Filled icon for the selected tab, outlined otherwise; null → the feature's own icon. */
private fun tabIcon(id: String, selected: Boolean): ImageVector? = when (id) {
    "home" -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    "qa" -> if (selected) Icons.Filled.Forum else Icons.Outlined.Forum
    "guides" -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle
    "messages" -> if (selected) Icons.Filled.Mail else Icons.Outlined.Mail
    "profile" -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    else -> null
}
